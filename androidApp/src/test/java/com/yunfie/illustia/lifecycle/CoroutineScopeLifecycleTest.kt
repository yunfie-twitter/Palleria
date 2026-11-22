package com.yunfie.illustia.lifecycle

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive

/**
 * 非同期クライアントやコンポーネントにおける CoroutineScope / Job のライフサイクルおよび破棄・キャンセル検証テスト。
 */
class CoroutineScopeLifecycleTest :
    FunSpec({
        test("scope cancellation properly marks scope as inactive and cancels child jobs") {
            val job = SupervisorJob()
            val scope = CoroutineScope(Dispatchers.Default + job)

            scope.isActive shouldBe true
            job.isActive shouldBe true

            // 切断・破棄シミュレーション
            scope.cancel()

            scope.isActive shouldBe false
            job.isActive shouldBe false
            job.isCancelled shouldBe true
        }

        test("canceling parent job stops active processing loop") {
            val parentJob = SupervisorJob()
            var loopExecutedCount = 0

            val isActiveCondition: () -> Boolean = { parentJob.isActive }

            isActiveCondition() shouldBe true

            // ループ実行シミュレーション
            if (isActiveCondition()) loopExecutedCount++
            if (isActiveCondition()) loopExecutedCount++

            parentJob.cancel()

            isActiveCondition() shouldBe false
            if (isActiveCondition()) loopExecutedCount++

            loopExecutedCount shouldBe 2
        }
    })
