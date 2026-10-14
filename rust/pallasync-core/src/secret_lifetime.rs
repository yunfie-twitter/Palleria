//! Monotonic expiry must destroy secret sessions even when no API is called.
use crate::client_error::{ClientError, Result};
use std::{
    sync::{
        Once,
        atomic::{AtomicU64, Ordering},
    },
    time::Duration,
};
static START: Once = Once::new();
// Odd: foreground. Every cancellation/foreground transition invalidates old leases.
static LIFETIME: AtomicU64 = AtomicU64::new(1);
#[derive(Clone, Copy)]
pub(crate) struct Lease(u64);
impl Lease {
    pub(crate) fn live(self) -> bool {
        self.0 % 2 == 1 && LIFETIME.load(Ordering::SeqCst) == self.0
    }
    pub(crate) fn check(self) -> Result {
        if self.live() {
            Ok(())
        } else {
            Err(ClientError("C_RECOVERY_SESSION_EXPIRED"))
        }
    }
}
pub(crate) fn lease() -> Result<Lease> {
    let lease = Lease(LIFETIME.load(Ordering::SeqCst));
    lease.check()?;
    Ok(lease)
}
pub fn foreground(value: bool) {
    let _ = LIFETIME.fetch_update(Ordering::SeqCst, Ordering::SeqCst, |state| {
        if (state % 2 == 1) == value {
            None
        } else {
            state.checked_add(1)
        }
    });
    if !value {
        crate::snapshot::abort_all_verifications();
    }
    start();
}
pub fn invalidate() {
    let _ = LIFETIME.fetch_update(Ordering::SeqCst, Ordering::SeqCst, |state| {
        state.checked_add(2)
    });
    crate::snapshot::abort_all_verifications();
    start();
}
pub(crate) fn start() {
    START.call_once(|| {
        std::thread::spawn(|| {
            loop {
                std::thread::sleep(Duration::from_millis(250));
                crate::root_session::expire();
                crate::peer_enrollment::expire();
                for handle in crate::local_transfer::expire() {
                    crate::recovery_flow::discard_holder(handle);
                }
                crate::recovery_flow::expire_collectors();
                crate::device_session::expire();
                crate::epoch_session::expire();
                crate::snapshot::expire_verifications();
            }
        });
    });
}
