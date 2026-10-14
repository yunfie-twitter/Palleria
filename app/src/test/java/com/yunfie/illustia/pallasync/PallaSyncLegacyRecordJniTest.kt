package com.yunfie.illustia.pallasync

import io.kotest.matchers.shouldBe
import org.junit.Test

class PallaSyncLegacyRecordJniTest {
    @Test
    fun `v21 record signature uses legacy context and omits relay assigned sequences`() {
        val publicKey = "Zs1gi5KLiOUODv6qM_rxxDzv4HKUsLh-n-CrpqPPdjM"
        val record =
            """{"action":"upsert","chain_id":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","collection_name":"palleria.favorite_tag/2","created_at_ms":1700000000000,"device_id":"01890f3e-7c00-7000-8000-000000000002","encrypted_payload":"BwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwc","epoch":0,"lamport":1,"payload_nonce":"CAgICAgICAgICAgICAgICAgICAgICAgI","protocol_version":"2.1","record_id":"01890f3e-7c00-7000-8000-000000000001","signature":"b_ipAK6qA2e26iF8FVDBA5KzlsKXmD8tboi3d9DgDov9ed_h5Bnj-YNsl7z-HqsDEEc0fwqLrCVWJTeQYvsiBA"}"""

        PallaSyncCore.verifyLegacySyncRecordV21(record, publicKey) shouldBe true
        PallaSyncCore.verifyLegacySyncRecordV21(record.dropLast(1) + ",\"relay_seq\":19}", publicKey) shouldBe true
        PallaSyncCore.verifyLegacySyncRecordV21(record.replace("\"lamport\":1", "\"lamport\":2"), publicKey) shouldBe false
        PallaSyncCore.verifyLegacySyncRecordV21(record.dropLast(1) + ",\"unknown\":true}", publicKey) shouldBe false
    }
}
