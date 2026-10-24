//! §5.6 adapter: fixed x=1..n, lowest-t reconstruction, extra-piece consistency.
use crate::{
    client_error::{ClientError, Result},
    vendor::sss::hazmat,
};
use std::collections::BTreeMap;
use subtle::ConstantTimeEq;
use zeroize::{Zeroize, ZeroizeOnDrop, Zeroizing};

#[derive(Clone, Zeroize, ZeroizeOnDrop)]
pub struct Piece {
    pub index: u8,
    pub bytes: [u8; 32],
}

pub fn parameters(t: u16, n: u16) -> Result {
    if t >= 2 && t < n && n <= 255 {
        Ok(())
    } else {
        Err(ClientError("C_RECOVERY_PARAMETER_INVALID"))
    }
}
pub fn split(rms: &[u8; 32], t: u16, n: u16) -> Result<Vec<Piece>> {
    parameters(t, n)?;
    let raw = Zeroizing::new(
        hazmat::create_keyshares(rms, n as u8, t as u8)
            .map_err(|_| ClientError("C_RECOVERY_PARAMETER_INVALID"))?,
    );
    Ok(raw
        .iter()
        .map(|p| Piece {
            index: p[0],
            bytes: p[1..].try_into().expect("upstream's fixed 32-byte profile"),
        })
        .collect())
}
pub fn reconstruct(pieces: &[Piece], t: u16, n: u16) -> Result<Zeroizing<[u8; 32]>> {
    reconstruct_refs(pieces.iter(), t, n)
}
pub(crate) fn reconstruct_locked(
    pieces: &[crate::locked_memory::Locked<Piece>],
    t: u16,
    n: u16,
) -> Result<crate::locked_memory::Locked<[u8; 32]>> {
    let rms = reconstruct_refs(pieces.iter().map(|piece| &**piece), t, n)?;
    crate::locked_memory::Locked::new(*rms)
}
fn reconstruct_refs<'a>(
    pieces: impl IntoIterator<Item = &'a Piece>,
    t: u16,
    n: u16,
) -> Result<Zeroizing<[u8; 32]>> {
    parameters(t, n)?;
    let mut distinct = BTreeMap::new();
    for p in pieces {
        if p.index == 0 || u16::from(p.index) > n {
            return Err(ClientError("C_RECOVERY_SHARE_INVALID"));
        }
        if let Some(previous) = distinct.insert(p.index, &p.bytes)
            && !bool::from(previous.ct_eq(&p.bytes))
        {
            return Err(ClientError("C_RECOVERY_SHARE_ALREADY_USED"));
        }
    }
    if distinct.len() < usize::from(t) {
        return Err(ClientError("C_RECOVERY_THRESHOLD_NOT_MET"));
    }
    let mut raw = Zeroizing::new(Vec::new());
    for (&index, bytes) in distinct.iter().take(usize::from(t)) {
        let mut p = vec![index];
        p.extend_from_slice(*bytes);
        raw.push(p);
    }
    let base = Zeroizing::new(
        hazmat::combine_keyshares(&raw).map_err(|_| ClientError("C_RECOVERY_SHARE_INVALID"))?,
    );
    for (&index, bytes) in distinct.iter().skip(usize::from(t)) {
        let mut extra = vec![index];
        extra.extend_from_slice(*bytes);
        raw.push(extra);
        let check = Zeroizing::new(
            hazmat::combine_keyshares(&raw).map_err(|_| ClientError("C_RECOVERY_SHARE_INVALID"))?,
        );
        raw.last_mut().expect("just appended").zeroize();
        raw.pop();
        if !bool::from(base.as_slice().ct_eq(check.as_slice())) {
            return Err(ClientError("C_RECOVERY_SHARE_INVALID"));
        }
    }
    let mut rms = Zeroizing::new([0; 32]);
    rms.copy_from_slice(&base);
    Ok(rms)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn c8_every_three_of_five_subset_and_inconsistent_extra() {
        let vectors = [
            "606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f",
            "5b5c55524740494e63646d6a7f7871762b2c25223730393e13141d1a0f080106",
            "3b3c35322720292e03040d0a1f1811164b4c45425750595e73747d7a6f686166",
            "ecf9c6d3b8ad928744516e7b10053a2fa7b28d98f3e6d9cc0f1a25305b4e7164",
            "8c99a6b3d8cdf2e724310e1b70655a4fc7d2edf89386b9ac6f7a45503b2e1104",
        ];
        let mut pieces: Vec<_> = vectors
            .iter()
            .enumerate()
            .map(|(i, p)| Piece {
                index: i as u8 + 1,
                bytes: hex::decode(p).unwrap().try_into().unwrap(),
            })
            .collect();
        let expected = std::array::from_fn::<u8, 32, _>(|i| i as u8);
        for a in 0..5 {
            for b in a + 1..5 {
                for c in b + 1..5 {
                    assert_eq!(
                        *reconstruct(
                            &[pieces[c].clone(), pieces[a].clone(), pieces[b].clone()],
                            3,
                            5
                        )
                        .unwrap(),
                        expected
                    );
                }
            }
        }
        assert_eq!(*reconstruct(&pieces, 3, 5).unwrap(), expected);
        pieces[4].bytes[0] ^= 1;
        assert_eq!(
            reconstruct(&pieces, 3, 5).err().unwrap().0,
            "C_RECOVERY_SHARE_INVALID"
        );
    }
    #[test]
    fn invalid_profiles_and_duplicate_capacity() {
        for (t, n) in [(1, 3), (2, 2), (3, 2), (2, 256)] {
            assert!(parameters(t, n).is_err());
        }
        let rms = [42; 32];
        let p = split(&rms, 2, 3).unwrap();
        assert_eq!(
            reconstruct(&[p[0].clone(), p[0].clone()], 2, 3)
                .err()
                .unwrap()
                .0,
            "C_RECOVERY_THRESHOLD_NOT_MET"
        );
        assert_eq!(*reconstruct(&p, 2, 3).unwrap(), rms);
    }

    #[test]
    fn compatible_profile_matrix_and_u8_boundaries_reconstruct() {
        let profiles = (3..=16)
            .flat_map(|n| (2..n).map(move |t| (t, n)))
            .chain([(2, 255), (254, 255)]);
        for (t, n) in profiles {
            let rms =
                std::array::from_fn(|i| (i as u8).wrapping_add(n as u8).wrapping_add(t as u8));
            let pieces = split(&rms, t, n).unwrap();
            assert_eq!(pieces.len(), usize::from(n), "profile {t}-of-{n}");

            let threshold = usize::from(t);
            for basis in [
                pieces[..threshold].to_vec(),
                pieces[pieces.len() - threshold..].to_vec(),
            ] {
                assert_eq!(
                    *reconstruct(&basis, t, n).unwrap(),
                    rms,
                    "profile {t}-of-{n}"
                );
            }
            assert_eq!(
                *reconstruct(&pieces, t, n).unwrap(),
                rms,
                "profile {t}-of-{n} full set"
            );
        }
    }
}
