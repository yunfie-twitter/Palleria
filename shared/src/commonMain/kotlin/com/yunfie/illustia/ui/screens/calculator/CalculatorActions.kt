package com.yunfie.illustia.ui.screens.calculator

import com.yunfie.illustia.CalculatorUiState
import kotlinx.coroutines.flow.StateFlow

/**
 * 電卓／プライバシーモード解除画面が ViewModel に要求する操作。
 *
 * 画面から `IllustiaViewModel` への直接依存を断ち、共有 UI 側から使えるようにするための
 * 最小契約。実装は androidApp の ViewModel が担う。
 */
interface CalculatorActions {
    val calculatorState: StateFlow<CalculatorUiState>

    suspend fun verifyAndUnlockPrivacy(code: String): Boolean

    fun confirmPrivacyUnlock()

    fun appendToCalculatorBuffer(char: Char)

    fun clearCalculatorBuffer()

    fun deleteLastCalculatorBuffer()

    fun evaluateCalculatorExpression()
}
