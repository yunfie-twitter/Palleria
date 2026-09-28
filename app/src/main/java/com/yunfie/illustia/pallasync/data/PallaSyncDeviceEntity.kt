package com.yunfie.illustia.pallasync.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pallasync_devices",
    indices = [Index("chainId")],
)
data class PallaSyncDeviceEntity(
    @PrimaryKey val deviceId: String,
    val chainId: String,
    val deviceName: String,
    val publicKey: String,
    val joinedAtMs: Long,
)
