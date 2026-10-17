use pallasync_core::{
    local_transfer as transfer,
    recovery::{self, RecoveryInput},
    recovery_flow::{self as flow, CollectionMode},
    root_session::{self, Operation},
    strict::verify::object_hash,
};
use serde_json::{Value, json};
use zeroize::Zeroizing;
const MNEMONIC: &str = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art";
fn pair(
    manifest: &Value,
    ceremony: &str,
    left_role: &str,
    right_role: &str,
    purpose: &str,
) -> (u64, u64) {
    let session = uuid::Uuid::new_v4().to_string();
    let admin = manifest["admin_public_key"].as_str().unwrap();
    let (a, ad) = transfer::begin(
        manifest,
        admin,
        ceremony,
        &session,
        "initiator",
        left_role,
        purpose,
    )
    .unwrap();
    let (b, bd) = transfer::begin(
        manifest,
        admin,
        ceremony,
        &session,
        "responder",
        right_role,
        purpose,
    )
    .unwrap();
    let sas = transfer::bind_peer(a, &bd).unwrap();
    assert_eq!(sas, transfer::bind_peer(b, &ad).unwrap());
    transfer::confirm(a, &sas, true).unwrap();
    transfer::confirm(b, &sas, true).unwrap();
    (a, b)
}
fn bytes(value: &Value) -> Vec<u8> {
    recovery::canonical(value).unwrap()
}

#[test]
fn denied_or_mismatched_confirmation_permanently_closes_the_secret_pair() {
    let manifest = recovery::parse(include_bytes!("fixtures/recovery-manifest-c8.json")).unwrap();
    let admin = manifest["admin_public_key"].as_str().unwrap();
    for denied in [true, false] {
        let session = uuid::Uuid::new_v4().to_string();
        let ceremony = uuid::Uuid::new_v4().to_string();
        let (a, ad) = transfer::begin(
            &manifest,
            admin,
            &ceremony,
            &session,
            "initiator",
            "dealer",
            "create",
        )
        .unwrap();
        let (b, bd) = transfer::begin(
            &manifest,
            admin,
            &ceremony,
            &session,
            "responder",
            "holder",
            "create",
        )
        .unwrap();
        let sas = transfer::bind_peer(a, &bd).unwrap();
        assert_eq!(sas, transfer::bind_peer(b, &ad).unwrap());
        let displayed = if denied { sas.as_str() } else { "mismatched" };
        assert_eq!(
            transfer::confirm(a, displayed, !denied).err().unwrap().0,
            if denied {
                "C_RECOVERY_AUTH_REQUIRED"
            } else {
                "C_RECOVERY_SAS_MISMATCH"
            }
        );
        assert_eq!(
            transfer::confirm(a, &sas, true).err().unwrap().0,
            "C_RECOVERY_SESSION_EXPIRED"
        );
        transfer::end(b);
    }
}
#[test]
fn stored_readback_threshold_test_activation_and_explicit_recovery() {
    let root = root_session::begin(
        MNEMONIC.as_bytes(),
        b"",
        Operation::RecoveryCreate,
        None,
        None,
    )
    .unwrap();
    let holder_ids = [
        uuid::Uuid::new_v4().to_string(),
        uuid::Uuid::new_v4().to_string(),
        uuid::Uuid::new_v4().to_string(),
    ];
    let device_ids = [
        uuid::Uuid::new_v4().to_string(),
        uuid::Uuid::new_v4().to_string(),
    ];
    let selected = vec![
        json!({"holder_kind":"device","holder_device_id":device_ids[0],"holder_instance_id":holder_ids[0]}),
        json!({"holder_kind":"device","holder_device_id":device_ids[1],"holder_instance_id":holder_ids[1]}),
        json!({"holder_kind":"offline","holder_device_id":null,"holder_instance_id":holder_ids[2]}),
    ];
    let set =
        root_session::create_set(root, Operation::RecoveryCreate, 0, 2, &selected, 1).unwrap();
    let manifest = &set["manifest"];
    let admin = manifest["admin_public_key"].as_str().unwrap();
    let prepared = set["status_chain"].as_array().unwrap();
    assert_eq!(
        flow::begin_collection(
            manifest,
            prepared,
            admin,
            None,
            &uuid::Uuid::new_v4().to_string(),
            CollectionMode::Recover {
                operation: Operation::Enrollment,
                emergency_new_root: false
            },
            true,
            false
        )
        .err()
        .unwrap()
        .0,
        "C_RECOVERY_ROTATION_INCOMPLETE"
    );
    let ceremony = uuid::Uuid::new_v4().to_string();
    let mut stored = Vec::new();
    let mut keys = Vec::new();
    for index in 1..=3 {
        let slot = (index - 1) as usize;
        let role = if index == 3 { "reader" } else { "holder" };
        let protection = if index == 3 {
            "passphrase-wrapped"
        } else {
            "os-keystore"
        };
        let (dealer, holder) = pair(manifest, &ceremony, "dealer", role, "create");
        let first = flow::distribute(root, Operation::RecoveryCreate, dealer, index).unwrap();
        assert_eq!(first, transfer::retry_packet(dealer, 1).unwrap());
        let device = if index == 3 {
            None
        } else {
            Some(device_ids[slot].as_str())
        };
        assert_eq!(
            flow::receive_holder(
                holder,
                &bytes(&first),
                &holder_ids[slot],
                device,
                protection,
                false,
                false,
                false,
                2
            )
            .err()
            .unwrap()
            .0,
            "C_RECOVERY_STORAGE_UNSAFE"
        );
        let proposal = flow::receive_holder(
            holder,
            &bytes(&first),
            &holder_ids[slot],
            device,
            protection,
            true,
            false,
            false,
            2,
        )
        .unwrap();
        assert_eq!(
            flow::receive_holder(
                holder,
                &bytes(&first),
                &holder_ids[slot],
                device,
                protection,
                true,
                false,
                false,
                2
            )
            .unwrap(),
            proposal
        );
        let signed =
            flow::receive_dealer(root, Operation::RecoveryCreate, dealer, &bytes(&proposal))
                .unwrap();
        // A lost reply must return the original packet, without a second signature or sequence.
        assert_eq!(
            signed,
            flow::receive_dealer(root, Operation::RecoveryCreate, dealer, &bytes(&proposal))
                .unwrap()
        );
        assert_eq!(signed, transfer::retry_packet(dealer, 2).unwrap());
        assert_eq!(
            flow::receive_holder(
                holder,
                &bytes(&signed),
                &holder_ids[slot],
                device,
                protection,
                true,
                false,
                false,
                2
            )
            .unwrap()["ready_to_persist"],
            true
        );
        let key = flow::holder_wrap_key(holder).unwrap();
        let package = flow::holder_package(
            holder,
            prepared,
            if index == 3 {
                Some(b"separate offline credential")
            } else {
                None
            },
        )
        .unwrap();
        if index == 3 {
            assert!(recovery::unwrap_offline_key(&package, b"wrong").is_err());
            assert_eq!(
                *recovery::unwrap_offline_key(&package, b"separate offline credential").unwrap(),
                *key
            );
        }
        assert!(flow::persisted(holder, &package, &[0; 32]).is_err());
        let ack = flow::persisted(holder, &package, &key).unwrap();
        flow::receive_dealer(root, Operation::RecoveryCreate, dealer, &bytes(&ack)).unwrap();
        assert!(flow::holder_wrap_key(holder).is_err());
        stored.push(package);
        keys.push(key);
        flow::end(dealer);
        flow::end(holder);
    }
    assert!(root_session::activate_set(root, Operation::RecoveryCreate, 3).is_err());
    let test_ceremony = uuid::Uuid::new_v4().to_string();
    let collector = flow::begin_collection(
        manifest,
        prepared,
        admin,
        Some(&object_hash(&prepared[0])),
        &test_ceremony,
        CollectionMode::SetupTest {
            root,
            operation: Operation::RecoveryCreate,
        },
        true,
        false,
    )
    .unwrap();
    for index in 0..2 {
        let (holder, collect) = pair(manifest, &test_ceremony, "holder", "collector", "create");
        let packet = flow::release_piece(holder, &stored[index], &keys[index], true, true).unwrap();
        let accepted = flow::receive_collector(collector, collect, &bytes(&packet)).unwrap();
        assert_eq!(accepted["distinct_shares"], index + 1);
        assert_eq!(
            flow::receive_collector(collector, collect, &bytes(&packet)).unwrap(),
            accepted
        );
        flow::end(holder);
        flow::end(collect);
    }
    assert_eq!(
        flow::finish_collection(collector, true).unwrap(),
        json!({"threshold_test_passed":true})
    );
    let active = root_session::activate_set(root, Operation::RecoveryCreate, 3).unwrap();
    for package in &mut stored {
        package["status_chain"] = active.clone();
    }
    root_session::end(root);
    let restore_ceremony = uuid::Uuid::new_v4().to_string();
    let active_chain = active.as_array().unwrap();
    let collector = flow::begin_collection(
        manifest,
        active_chain,
        admin,
        Some(&object_hash(&active_chain[1])),
        &restore_ceremony,
        CollectionMode::Recover {
            operation: Operation::Enrollment,
            emergency_new_root: false,
        },
        true,
        false,
    )
    .unwrap();
    let own_key = Zeroizing::new(*keys[0]);
    flow::collect_own_piece(collector, &stored[0], &own_key, &holder_ids[0], true, true).unwrap();
    let (reader, collect) = pair(
        manifest,
        &restore_ceremony,
        "reader",
        "collector",
        "recover",
    );
    assert!(flow::release_piece(reader, &stored[2], &keys[2], false, true).is_err());
    let packet = flow::release_piece(reader, &stored[2], &keys[2], true, true).unwrap();
    flow::receive_collector(collector, collect, &bytes(&packet)).unwrap();
    let result = flow::finish_collection(collector, true).unwrap();
    assert_eq!(
        result["identity"]["chain_id"],
        RecoveryInput::from_user(MNEMONIC, "")
            .unwrap()
            .roots()
            .unwrap()
            .chain_id
    );
    let recovered = result["root_session"].as_u64().unwrap();
    flow::cancel_set(manifest["recovery_set_id"].as_str().unwrap()).unwrap();
    assert!(root_session::identity(recovered).is_err());
    root_session::end(recovered);
    assert!(root_session::identity(recovered).is_err());
    flow::end(reader);
    flow::end(collect);
    assert_eq!(
        flow::begin_collection(
            manifest,
            active_chain,
            admin,
            None,
            &restore_ceremony,
            CollectionMode::Recover {
                operation: Operation::Enrollment,
                emergency_new_root: false
            },
            true,
            true
        )
        .err()
        .unwrap()
        .0,
        "C_RECOVERY_SHARE_COMPROMISED"
    );
}
