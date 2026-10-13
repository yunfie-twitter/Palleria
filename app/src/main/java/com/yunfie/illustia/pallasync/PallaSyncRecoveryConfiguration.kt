package com.yunfie.illustia.pallasync

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/** §§5.6/18.6: user-selected public storage domains; no share is allocated by enrollment. */
internal data class PallaSyncRecoveryConfiguration(
    val threshold: Int,
    val holders: List<Holder>,
    val independentHoldersConfirmed: Boolean,
    val deviceOnlyRiskConfirmed: Boolean,
) {
    data class Holder(
        val instanceId: String,
        val deviceId: String?,
    )

    fun holdersJson(): String {
        check(threshold in 2 until holders.size && holders.size <= MAX_HOLDERS) { "C_RECOVERY_PARAMETER_INVALID" }
        check(independentHoldersConfirmed) { "C_RECOVERY_AUTH_REQUIRED" }
        check(holders.any { it.deviceId == null } || deviceOnlyRiskConfirmed) { "C_RECOVERY_AUTH_REQUIRED" }
        val instances = mutableSetOf<String>()
        return JsonArray(
            holders.map { holder ->
                check(uuid4(holder.instanceId) && (holder.deviceId == null || uuid4(holder.deviceId))) {
                    "C_RECOVERY_PARAMETER_INVALID"
                }
                check(instances.add(holder.instanceId)) { "C_RECOVERY_SHARE_ALREADY_USED" }
                buildJsonObject {
                    put("holder_kind", if (holder.deviceId == null) "offline" else "device")
                    put("holder_device_id", holder.deviceId?.let(::JsonPrimitive) ?: JsonNull)
                    put("holder_instance_id", holder.instanceId)
                }
            },
        ).toString()
    }

    companion object {
        const val MAX_HOLDERS = 255
        private const val UUID_VERSION = 4
        private const val UUID_VARIANT = 2

        private fun uuid4(value: String): Boolean =
            runCatching { UUID.fromString(value) }.getOrNull()?.let {
                it.version() == UUID_VERSION && it.variant() == UUID_VARIANT && it.toString() == value
            } == true
    }
}
