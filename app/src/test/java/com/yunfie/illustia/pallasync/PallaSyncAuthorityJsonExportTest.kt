package com.yunfie.illustia.pallasync

import org.junit.Assert.assertEquals
import org.junit.Test

class PallaSyncAuthorityJsonExportTest {
    @Test
    fun onlyExportsAuthorityWithMatchingTrustedHash() {
        val authority = "{\"chain_id\":\"public-chain\"}"
        assertEquals(authority, authorityJsonForVerifiedExport(authority, "hash", "hash"))
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsAuthorityWhenTrustedHashIsMissingOrDoesNotMatch() {
        authorityJsonForVerifiedExport("{}", "calculated", null)
    }
}
