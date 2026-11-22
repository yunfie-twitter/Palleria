package com.yunfie.illustia

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AppShortcutRouterTest :
    FunSpec({
        afterTest {
            AppShortcutDestination.entries.forEach(AppShortcutRouter::consume)
        }

        test("accepts each declared shortcut action") {
            AppShortcutDestination.entries.forEach { destination ->
                AppShortcutRouter.acceptAction(destination.action) shouldBe true
                AppShortcutRouter.pending.value shouldBe destination
                AppShortcutRouter.consume(destination)
            }
        }

        test("ignores unrelated intents without replacing a pending shortcut") {
            AppShortcutRouter.acceptAction(AppShortcutDestination.Search.action)

            AppShortcutRouter.acceptAction("android.intent.action.VIEW") shouldBe false
            AppShortcutRouter.pending.value shouldBe AppShortcutDestination.Search
        }
    })
