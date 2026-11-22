package com.yunfie.illustia.data.pixiv

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class SuggestionStoreTest :
    FunSpec({
        test("only the latest autocomplete response is published") {
            val firstRequestStarted = CompletableDeferred<Unit>()
            val releaseFirstRequest = CompletableDeferred<Unit>()
            val store =
                SuggestionStore { query ->
                    when (query) {
                        "old" -> {
                            firstRequestStarted.complete(Unit)
                            releaseFirstRequest.await()
                            listOf("old result")
                        }

                        else -> {
                            listOf("new result")
                        }
                    }
                }

            coroutineScope {
                val oldRequest = async { store.fetch("old") }
                firstRequestStarted.await()
                store.fetch("new")
                releaseFirstRequest.complete(Unit)
                oldRequest.await()
            }

            store.autoWords.value shouldBe listOf("new result")
        }

        test("blank queries clear suggestions without a request") {
            var requestCount = 0
            val store =
                SuggestionStore {
                    requestCount += 1
                    listOf("result")
                }

            store.fetch("query")
            store.fetch("   ")

            requestCount shouldBe 1
            store.autoWords.value shouldBe emptyList()
        }

        test("a debounced query invalidates previous responses immediately") {
            val oldRequestStarted = CompletableDeferred<Unit>()
            val releaseOldRequest = CompletableDeferred<Unit>()
            val store =
                SuggestionStore { query ->
                    if (query == "old") {
                        oldRequestStarted.complete(Unit)
                        releaseOldRequest.await()
                        listOf("old result")
                    } else {
                        listOf("new result")
                    }
                }

            coroutineScope {
                val oldRequest = async(start = CoroutineStart.UNDISPATCHED) { store.fetch("old") }
                oldRequestStarted.await()
                val latestRequest =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        store.fetch("new", debounceMillis = 200)
                    }
                releaseOldRequest.complete(Unit)

                oldRequest.await()
                store.autoWords.value shouldBe emptyList()
                latestRequest.await()
            }

            store.autoWords.value shouldBe listOf("new result")
        }

        test("switching to a new query clears the previous suggestions while it loads") {
            val firstQueryDone = CompletableDeferred<Unit>()
            val releaseSecond = CompletableDeferred<Unit>()
            val store =
                SuggestionStore { query ->
                    if (query == "first") {
                        listOf("first result")
                    } else {
                        firstQueryDone.complete(Unit)
                        releaseSecond.await()
                        listOf("second result")
                    }
                }

            coroutineScope {
                store.fetch("first")
                store.autoWords.value shouldBe listOf("first result")

                val secondRequest = async(start = CoroutineStart.UNDISPATCHED) { store.fetch("second") }
                firstQueryDone.await()
                // The old query's suggestions must not linger while the new one is in flight.
                store.autoWords.value shouldBe emptyList()

                releaseSecond.complete(Unit)
                secondRequest.await()
            }

            store.autoWords.value shouldBe listOf("second result")
        }
    })
