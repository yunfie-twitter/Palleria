use pallasync_core::{
    recovery,
    root_session::{self, Operation},
    strict::verify::object_hash,
};
use serde_json::json;
const MNEMONIC: &str = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art";

#[test]
fn interrupted_prepared_set_can_be_retired_by_fresh_scoped_root_but_never_reactivated() {
    let root = root_session::begin(
        MNEMONIC.as_bytes(),
        b"",
        Operation::RecoveryCreate,
        None,
        None,
    )
    .unwrap();
    let holders: Vec<_> = (0..3).map(|_| json!({"holder_kind":"offline","holder_device_id":null,"holder_instance_id":uuid::Uuid::new_v4().to_string()})).collect();
    let prepared =
        root_session::create_set(root, Operation::RecoveryCreate, 0, 2, &holders, 1).unwrap();
    let manifest = &prepared["manifest"];
    let statuses = prepared["status_chain"].as_array().unwrap();
    let head = object_hash(&statuses[0]);
    assert_eq!(
        root_session::advance_recovery_status(
            root,
            Operation::RecoveryCreate,
            manifest,
            statuses,
            &head,
            "retired",
            None,
            2
        )
        .unwrap_err()
        .0,
        "C_RECOVERY_AUTH_REQUIRED"
    );
    root_session::end(root);
    let root = root_session::begin(
        MNEMONIC.as_bytes(),
        b"",
        Operation::RecoveryDisable,
        None,
        None,
    )
    .unwrap();
    assert_eq!(
        root_session::advance_recovery_status(
            root,
            Operation::RecoveryDisable,
            manifest,
            statuses,
            &head,
            "active",
            None,
            2
        )
        .unwrap_err()
        .0,
        "C_RECOVERY_PARAMETER_INVALID"
    );
    assert_eq!(
        root_session::advance_recovery_status(
            root,
            Operation::RecoveryDisable,
            manifest,
            statuses,
            &head,
            "retired",
            Some(&uuid::Uuid::new_v4().to_string()),
            2
        )
        .unwrap_err()
        .0,
        "C_RECOVERY_ROTATION_INCOMPLETE"
    );
    let retired = root_session::advance_recovery_status(
        root,
        Operation::RecoveryDisable,
        manifest,
        statuses,
        &head,
        "retired",
        None,
        2,
    )
    .unwrap();
    let retired = retired.as_array().unwrap();
    recovery::validate_status_chain(retired, manifest, Some(&head)).unwrap();
    assert_eq!(retired[1]["previous_status_hash"], head);
    assert_eq!(
        root_session::advance_recovery_status(
            root,
            Operation::RecoveryDisable,
            manifest,
            retired,
            &head,
            "compromised",
            None,
            3
        )
        .unwrap_err()
        .0,
        "C_RECOVERY_SHARE_INVALID"
    );
    let compromised = root_session::advance_recovery_status(
        root,
        Operation::RecoveryDisable,
        manifest,
        retired,
        &object_hash(&retired[1]),
        "compromised",
        None,
        3,
    )
    .unwrap();
    let compromised = compromised.as_array().unwrap();
    recovery::validate_status_chain(compromised, manifest, Some(&object_hash(&retired[1])))
        .unwrap();
    assert_eq!(
        root_session::advance_recovery_status(
            root,
            Operation::RecoveryDisable,
            manifest,
            compromised,
            &object_hash(&compromised[2]),
            "retired",
            None,
            4
        )
        .unwrap_err()
        .0,
        "C_RECOVERY_SHARE_INVALID"
    );
    let mut forged = manifest.clone();
    forged["threshold"] = json!(3);
    assert!(
        root_session::advance_recovery_status(
            root,
            Operation::RecoveryDisable,
            &forged,
            statuses,
            &head,
            "retired",
            None,
            2
        )
        .is_err()
    );
    root_session::end(root);
}
