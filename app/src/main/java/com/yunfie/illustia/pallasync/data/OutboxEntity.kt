package com.yunfie.illustia.pallasync.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pallasync_outbox",
    indices = [
        Index(value = ["status"]),
        Index(value = ["chain_id", "status"]),
        Index(value = ["chain_id", "record_id"], unique = true),
    ],
)
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "chain_id") val chainId: String,
    @ColumnInfo(name = "device_seq") val deviceSeq: Long,
    @ColumnInfo(name = "status") val status: String, // queued, pending_initial_merge, uploading, accepted, rejected
    @ColumnInfo(name = "event_json") val eventJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "record_id") val recordId: String? = null,
    @ColumnInfo(name = "object_hash") val objectHash: String? = null,
    @ColumnInfo(name = "receipt_json") val receiptJson: String? = null,
)
