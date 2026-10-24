use pallasync_core::snapshot::StateStream;
use serde_json::{Value, json};
const COMPACTION: &str = "55555555-5555-4555-8555-555555555555";
fn key() -> [u8; 32] {
    hex::decode("d96ff76d5c3ad5df7429153b52126e43a818c223f53fa3f52d7ce102026c0974")
        .unwrap()
        .try_into()
        .unwrap()
}
fn header() -> Value {
    json!({"chain_id":"ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc","covered_log_hash":"0zeJRLV2xNFxHkbAzYH-TSb1kOcmVyl9r4kiBsQLCHQ","covered_through_seq":0,"epoch_hash":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","generation":0,"kind":"header","lamport_max":0,"log_id":"cccccccc-cccc-4ccc-8ccc-cccccccccccc"})
}
fn part(index: u32, count: u32, entries: &[Value]) -> Value {
    json!({"type":"PALLASYNC-SNAPSHOT-v3","collection":"pallasync.snapshot/3","compaction_id":COMPACTION,"part_index":index,"part_count":count,"entries":entries})
}
#[test]
fn c10_exact_state_array_and_constant_time_commitment_check() {
    let header = header();
    let p = part(0, 1, std::slice::from_ref(&header));
    let mut stream = StateStream::new(&key(), &header, 1).unwrap();
    stream.part(&p, COMPACTION).unwrap();
    assert_eq!(
        stream.commitment().unwrap(),
        "cEWZYwyPF0UbkBBP8J9BJnU69OPDBciCrjHKk2gt1bU"
    );
    let mut stream = StateStream::new(&key(), &header, 1).unwrap();
    stream.part(&p, COMPACTION).unwrap();
    stream
        .finish("cEWZYwyPF0UbkBBP8J9BJnU69OPDBciCrjHKk2gt1bU")
        .unwrap();
    let mut stream = StateStream::new(&key(), &header, 1).unwrap();
    stream.part(&p, COMPACTION).unwrap();
    assert!(
        stream
            .finish("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
            .is_err()
    );
}
#[test]
fn every_state_kind_has_one_identity_order_independent_of_part_boundaries() {
    let mut head = header();
    head["lamport_max"] = json!(8);
    let winner = json!([
        5,
        0,
        "11111111-1111-4111-8111-111111111111",
        "22222222-2222-4222-8222-222222222222",
        0
    ]);
    let deletion = json!([
        6,
        1,
        "11111111-1111-4111-8111-111111111111",
        "22222222-2222-4222-8222-222222222222",
        0
    ]);
    let values = vec![
        head.clone(),
        json!({"kind":"entity","collection":"palleria.search_history/3","entity_id":"猫","key":winner,"operation":"upsert","body":{"query":"猫"},"context":{}}),
        json!({"kind":"clear","collection":"palleria.search_history/3","scope":"all","key":deletion}),
        json!({"kind":"retention","collection":"palleria.search_history/3","scope":"all","key":winner,"limit":null,"stop_on_safety_limit":false}),
        json!({"kind":"author","generation":0,"device_id":"11111111-1111-4111-8111-111111111111","device_seq":5,"record_hash":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"}),
        json!({"kind":"author_gap","generation":0,"device_id":"11111111-1111-4111-8111-111111111111","first":2,"last":3}),
        json!({"kind":"projection","collection":"palleria.search_history/3","scope":"all","limit":1000000,"eviction_floor":{"key":winner,"entity_id":"猫"}}),
    ];
    let mut one = StateStream::new(&key(), &head, 1).unwrap();
    one.part(&part(0, 1, &values), COMPACTION).unwrap();
    let digest = one.commitment().unwrap();
    for boundary in 1..values.len() {
        let mut stream = StateStream::new(&key(), &head, 2).unwrap();
        stream
            .part(&part(0, 2, &values[..boundary]), COMPACTION)
            .unwrap();
        stream
            .part(&part(1, 2, &values[boundary..]), COMPACTION)
            .unwrap();
        stream.finish(&digest).unwrap();
    }
    let mut stream = StateStream::new(&key(), &head, 2).unwrap();
    stream.part(&part(0, 2, &values[..3]), COMPACTION).unwrap();
    assert!(stream.finish(&digest).is_err());
    let mut stream = StateStream::new(&key(), &head, 1).unwrap();
    let mut repeated = values.clone();
    repeated.insert(2, values[1].clone());
    assert!(stream.part(&part(0, 1, &repeated), COMPACTION).is_err());
    assert!(stream.part(&part(0, 1, &values), COMPACTION).is_err()); // Failed staging cannot be reused.
    assert!(stream.commitment().is_err());
}

#[test]
fn native_verifier_session_is_one_shot_and_failed_parts_destroy_it() {
    use pallasync_core::snapshot::{
        abort_verification, begin_verification, finish_commitment, finish_verification,
        verification_part,
    };

    let head = header();
    let encoded = part(0, 1, std::slice::from_ref(&head));
    let handle = begin_verification(&key(), &head, COMPACTION, 1).unwrap();
    verification_part(handle, &encoded).unwrap();
    finish_verification(handle, "cEWZYwyPF0UbkBBP8J9BJnU69OPDBciCrjHKk2gt1bU").unwrap();
    assert!(finish_verification(handle, "cEWZYwyPF0UbkBBP8J9BJnU69OPDBciCrjHKk2gt1bU").is_err());

    let producer = begin_verification(&key(), &head, COMPACTION, 1).unwrap();
    verification_part(producer, &encoded).unwrap();
    assert_eq!(
        finish_commitment(producer).unwrap(),
        "cEWZYwyPF0UbkBBP8J9BJnU69OPDBciCrjHKk2gt1bU"
    );
    assert!(finish_commitment(producer).is_err());

    let failed = begin_verification(&key(), &head, COMPACTION, 1).unwrap();
    let wrong = part(1, 1, std::slice::from_ref(&head));
    assert!(verification_part(failed, &wrong).is_err());
    assert!(verification_part(failed, &encoded).is_err());

    let cancelled = begin_verification(&key(), &head, COMPACTION, 1).unwrap();
    abort_verification(cancelled);
    assert!(verification_part(cancelled, &encoded).is_err());
}
