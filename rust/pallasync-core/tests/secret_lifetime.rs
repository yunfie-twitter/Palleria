use pallasync_core::{
    root_session::{self, Operation},
    secret_lifetime,
};
const PHRASE: &[u8] = b"abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art";

#[test]
fn cancellation_and_background_do_not_revive_old_handles() {
    let first = root_session::begin(PHRASE, b"", Operation::Enrollment, None, None).unwrap();
    secret_lifetime::foreground(false);
    assert!(root_session::identity(first).is_err());
    assert!(root_session::begin(PHRASE, b"", Operation::Enrollment, None, None).is_err());
    secret_lifetime::foreground(true);
    assert!(root_session::identity(first).is_err());
    let fresh = root_session::begin(PHRASE, b"", Operation::Enrollment, None, None).unwrap();
    assert!(root_session::identity(fresh).is_ok());
    secret_lifetime::invalidate();
    assert!(root_session::touch(fresh).is_err());
    assert!(root_session::identity(fresh).is_err());
    let next = root_session::begin(PHRASE, b"", Operation::Enrollment, None, None).unwrap();
    assert!(root_session::identity(next).is_ok());
    root_session::end(next);
    assert!(root_session::identity(next).is_err());
}
