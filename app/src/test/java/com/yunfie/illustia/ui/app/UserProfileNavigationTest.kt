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

        test("reopening the same artist from an artwork needs its own navigation signal") {
            // プロフィール → 作品 → プロフィールの順に戻ると、showUserPage は true、
            // userPageDismissed は false のまま残る。この状態で作品ページから同じ
            // ユーザーを開くと、状態を見ている Effect のキーが 3 つとも変化しないため
            // 発火しない。openUserPage は毎回 userNavigationRequests を emit するので、
            // その要求を購読していること自体が再オープンの成立条件になる。
            val profile = AppRoute.UserProfile(42L)
            val stack = mutableListOf<AppRoute>(AppRoute.Main, profile, AppRoute.Detail(10L))

            // 作品ページが前面なので、このルート経由の読み込みは走らない。
            shouldLoadUserProfile(profile, stack.last(), 42L) shouldBe false

            // 前面でプロフィールに戻ったときだけ、既存の入口が読み込みを補完する。
            shouldLoadUserProfile(profile, profile, 42L) shouldBe false
            shouldLoadUserProfile(profile, profile, null) shouldBe true
        }
    })
