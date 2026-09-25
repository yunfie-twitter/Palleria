package com.yunfie.illustia.ui.app

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class UserProfileNavigationTest :
    FunSpec({
        test("popped profile does not reopen when its data is released") {
            val profile = AppRoute.UserProfile(42L)
            val stack = mutableListOf<AppRoute>(AppRoute.Main, AppRoute.Detail(10L), profile)

            stack.removeAt(stack.lastIndex)
            shouldLoadUserProfile(profile, stack.last(), null) shouldBe false

            stack.removeAt(stack.lastIndex)
            shouldLoadUserProfile(profile, stack.last(), null) shouldBe false
        }

        test("active profile loads only when the requested user is not already loading") {
            val profile = AppRoute.UserProfile(42L)
            shouldLoadUserProfile(profile, profile, null) shouldBe true
            shouldLoadUserProfile(profile, profile, 7L) shouldBe true
            shouldLoadUserProfile(profile, profile, 42L) shouldBe false
        }
    })
