package com.yunfie.illustia

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class PermissionRationaleTest :
    FunSpec({
        test("default ui state has no pending permission rationale") {
            val state = IllustiaUiState()
            state.pendingPermissionRationale.shouldBeNull()
        }

        test("setting notification rationale transitions correctly") {
            val state = IllustiaUiState(pendingPermissionRationale = PermissionRationaleType.Notification)
            state.pendingPermissionRationale shouldBe PermissionRationaleType.Notification
        }

        test("setting storage rationale transitions correctly") {
            val state = IllustiaUiState(pendingPermissionRationale = PermissionRationaleType.Storage)
            state.pendingPermissionRationale shouldBe PermissionRationaleType.Storage
        }

        test("dismissing rationale clears the pending permission") {
            val state = IllustiaUiState(pendingPermissionRationale = PermissionRationaleType.Notification)
            val dismissed = state.copy(pendingPermissionRationale = null)
            dismissed.pendingPermissionRationale.shouldBeNull()
        }
    })
