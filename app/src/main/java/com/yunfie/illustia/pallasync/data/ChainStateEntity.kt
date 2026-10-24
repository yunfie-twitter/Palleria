package com.yunfie.illustia.pallasync.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pallasync_chain_state")
data class ChainStateEntity(
    @PrimaryKey val chainId: String,
    @ColumnInfo(name = "lamport") val lamport: Long,
    @ColumnInfo(name = "key_epoch") val keyEpoch: Long,
    @ColumnInfo(name = "chain_vector_json") val chainVectorJson: String, // Serialized Map<String, Long>
    @ColumnInfo(name = "latest_control_seq") val latestControlSeq: Long = 0,
    @ColumnInfo(name = "prev_control_hash") val prevControlHash: String = "",
    @ColumnInfo(name = "last_relay_seq", defaultValue = "0") val lastRelaySeq: Long = 0,
    @ColumnInfo(name = "initial_pull_completed", defaultValue = "1") val initialPullCompleted: Boolean = true,
    @ColumnInfo(name = "generation", defaultValue = "0") val generation: Long = 0,
    @ColumnInfo(name = "author_seq", defaultValue = "0") val authorSeq: Long = 0,
    @ColumnInfo(name = "author_tail_hash") val authorTailHash: String? = null,
    @ColumnInfo(name = "log_id") val logId: String? = null,
    @ColumnInfo(name = "parameters_hash") val parametersHash: String? = null,
    @ColumnInfo(name = "epoch_hash") val epochHash: String? = null,
    @ColumnInfo(name = "downloaded_seq", defaultValue = "0") val downloadedSeq: Long = 0,
    @ColumnInfo(name = "verified_seq", defaultValue = "0") val verifiedSeq: Long = 0,
    @ColumnInfo(name = "materialized_seq", defaultValue = "0") val materializedSeq: Long = 0,
    @ColumnInfo(name = "verified_hash") val verifiedHash: String? = null,
    @ColumnInfo(name = "transparency_id") val transparencyId: String? = null,
    @ColumnInfo(name = "transparency_index", defaultValue = "0") val transparencyIndex: Long = 0,
    @ColumnInfo(name = "transparency_hash") val transparencyHash: String? = null,
    @ColumnInfo(name = "write_state", defaultValue = "'pending_verification'") val writeState: String = "pending_verification",
    @ColumnInfo(name = "index_revision", defaultValue = "0") val indexRevision: Long = 0,
)
