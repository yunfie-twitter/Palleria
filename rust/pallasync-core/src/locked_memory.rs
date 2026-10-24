//! §7.11.3. Dedicated anonymous pages avoid overlapping heap-page locks.
//! Locking is best effort; temporary stack/KDF/managed copies and OS dumps are separate limits.
use crate::client_error::{ClientError, Result};
use std::{
    mem,
    ops::{Deref, DerefMut},
    ptr::{self, NonNull},
};
use zeroize::Zeroize;

pub(crate) struct Locked<T: Zeroize> {
    pointer: NonNull<T>,
    bytes: usize,
}

/// Dynamic UTF-8/binary inputs live in their own mapping, rather than a Vec's unlocked heap.
pub(crate) struct LockedBytes {
    pointer: NonNull<u8>,
    bytes: usize,
}
// SAFETY: the mapping has one owner; only immutable slice access is exposed.
unsafe impl Send for LockedBytes {}
// SAFETY: immutable borrows cannot mutate or release its owned mapping.
unsafe impl Sync for LockedBytes {}
impl LockedBytes {
    pub(crate) fn new(source: &[u8]) -> Result<Self> {
        if source.is_empty() {
            return Ok(Self {
                pointer: NonNull::dangling(),
                bytes: 0,
            });
        }
        let pointer = allocate(source.len())
            .ok_or(ClientError("C_LOCAL_LIMIT"))?
            .cast::<u8>();
        // SAFETY: fresh uniquely owned mapping, sized to source, with no overlap.
        unsafe {
            ptr::copy_nonoverlapping(source.as_ptr(), pointer.as_ptr(), source.len());
        }
        Ok(Self {
            pointer,
            bytes: source.len(),
        })
    }
    pub(crate) fn as_slice(&self) -> &[u8] {
        // SAFETY: the live mapping contains bytes initialized in new; the empty dangling case is valid.
        unsafe { std::slice::from_raw_parts(self.pointer.as_ptr(), self.bytes) }
    }
}
impl Drop for LockedBytes {
    fn drop(&mut self) {
        if self.bytes != 0 {
            // SAFETY: Drop has exclusive mapping ownership. Wipe its contents before unmapping.
            unsafe {
                std::slice::from_raw_parts_mut(self.pointer.as_ptr(), self.bytes).zeroize();
                release(self.pointer.cast(), self.bytes);
            }
        }
    }
}
// SAFETY: the mapping owns a single T. There are no shared/mutable aliases except Deref borrows.
unsafe impl<T: Zeroize + Send> Send for Locked<T> {}
// SAFETY: read access has the same synchronization requirements as T itself.
unsafe impl<T: Zeroize + Sync> Sync for Locked<T> {}

impl<T: Zeroize> Locked<T> {
    pub(crate) fn new(mut value: T) -> Result<Self> {
        let bytes = mem::size_of::<T>();
        // OS mappings are page aligned. These internal types have no over-page alignment.
        if bytes == 0 || mem::align_of::<T>() > 4096 {
            value.zeroize();
            return Err(ClientError("C_LOCAL_LIMIT"));
        }
        let Some(pointer) = allocate(bytes).map(NonNull::cast::<T>) else {
            value.zeroize();
            return Err(ClientError("C_LOCAL_LIMIT"));
        };
        // SAFETY: allocation is uniquely owned, large/aligned enough, and has no initialized T.
        unsafe {
            pointer.as_ptr().write(value);
        }
        Ok(Self { pointer, bytes })
    }
}
impl<T: Zeroize> Deref for Locked<T> {
    type Target = T;
    fn deref(&self) -> &T {
        // SAFETY: T was initialized in new and remains live until Drop; borrow forbids mutation.
        unsafe { self.pointer.as_ref() }
    }
}
impl<T: Zeroize> DerefMut for Locked<T> {
    fn deref_mut(&mut self) -> &mut T {
        // SAFETY: exclusive owner borrow is the only access to this initialized T.
        unsafe { self.pointer.as_mut() }
    }
}
impl<T: Zeroize> Drop for Locked<T> {
    fn drop(&mut self) {
        // SAFETY: T is still live. Wipe before its destructors/unmapping release locked pages.
        unsafe {
            self.pointer.as_mut().zeroize();
            ptr::drop_in_place(self.pointer.as_ptr());
            release(self.pointer.cast(), self.bytes);
        }
    }
}

#[cfg(unix)]
fn allocate(bytes: usize) -> Option<NonNull<std::ffi::c_void>> {
    // SAFETY: private anonymous read/write mapping, no file/offset or existing address.
    unsafe {
        let p = libc::mmap(
            ptr::null_mut(),
            bytes,
            libc::PROT_READ | libc::PROT_WRITE,
            libc::MAP_PRIVATE | libc::MAP_ANONYMOUS,
            -1,
            0,
        );
        if p == libc::MAP_FAILED {
            return None;
        }
        let mapped = NonNull::new(p)?;
        // Best effort: OS resource limits may refuse locking. Never claim guaranteed lock.
        let _ = libc::mlock(p, bytes);
        #[cfg(any(target_os = "linux", target_os = "android"))]
        let _ = libc::madvise(p, bytes, libc::MADV_DONTDUMP);
        Some(mapped)
    }
}
#[cfg(unix)]
unsafe fn release(p: NonNull<std::ffi::c_void>, bytes: usize) {
    // SAFETY: exclusive original mapping, already wiped/dropped. Unmapping also removes its lock.
    unsafe {
        let _ = libc::munmap(p.as_ptr(), bytes);
    }
}

#[cfg(windows)]
fn allocate(bytes: usize) -> Option<NonNull<std::ffi::c_void>> {
    use windows_sys::Win32::System::Memory::*;
    // SAFETY: dedicated committed anonymous region with no prescribed address.
    unsafe {
        let p = NonNull::new(VirtualAlloc(
            ptr::null(),
            bytes,
            MEM_COMMIT | MEM_RESERVE,
            PAGE_READWRITE,
        ))?;
        let _ = VirtualLock(p.as_ptr(), bytes);
        Some(p)
    }
}
#[cfg(windows)]
unsafe fn release(p: NonNull<std::ffi::c_void>, _bytes: usize) {
    use windows_sys::Win32::System::Memory::{MEM_RELEASE, VirtualFree};
    // SAFETY: original base, zero size for MEM_RELEASE. Contents/destructors were wiped first.
    unsafe {
        let _ = VirtualFree(p.as_ptr(), 0, MEM_RELEASE);
    }
}

#[cfg(not(any(unix, windows)))]
fn allocate(_bytes: usize) -> Option<NonNull<std::ffi::c_void>> {
    None
}
#[cfg(not(any(unix, windows)))]
unsafe fn release(_p: NonNull<std::ffi::c_void>, _bytes: usize) {}
