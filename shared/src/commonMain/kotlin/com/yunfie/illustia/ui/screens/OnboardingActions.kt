package com.yunfie.illustia.ui.screens

/** オンボーディング画面が ViewModel に要求する操作。 */
interface OnboardingActions {
    fun completeOnboardingSetup()

    fun updateAppLanguage(value: String)

    fun updatePixivImageProxyBaseUrl(url: String)

    fun updatePixivNetworkMode(mode: String)
}
