package com.yunfie.illustia

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** Owned by the main-thread ViewModel; each visit has a distinct cancellation/generation token. */
internal class UserProfileRequests(
    private val parentScope: CoroutineScope,
) {
    class Session(
        val userId: Long,
        val accountKey: String,
        val scope: CoroutineScope,
    )

    private var active: Session? = null

    fun open(
        userId: Long,
        accountKey: String,
    ): Session {
        close()
        return Session(
            userId,
            accountKey,
            CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job])),
        ).also { active = it }
    }

    fun current(): Session? = active

    fun isCurrent(session: Session): Boolean = active === session

    fun close() {
        val previous = active
        active = null
        previous?.scope?.cancel()
    }
}
