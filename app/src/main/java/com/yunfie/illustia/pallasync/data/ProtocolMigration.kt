package com.yunfie.illustia.pallasync.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive §12.1 migration. Legacy rows await signature/acceptance validation; none are erased. */
private const val PREVIOUS_VERSION = 6
private const val LOGICAL_STATE_VERSION = 7

internal object ProtocolMigration : Migration(PREVIOUS_VERSION, LOGICAL_STATE_VERSION) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf(
            "generation INTEGER NOT NULL DEFAULT 0",
            "author_seq INTEGER NOT NULL DEFAULT 0",
            "author_tail_hash TEXT",
            "log_id TEXT",
            "parameters_hash TEXT",
            "epoch_hash TEXT",
            "downloaded_seq INTEGER NOT NULL DEFAULT 0",
            "verified_seq INTEGER NOT NULL DEFAULT 0",
            "materialized_seq INTEGER NOT NULL DEFAULT 0",
            "verified_hash TEXT",
            "transparency_id TEXT",
            "transparency_index INTEGER NOT NULL DEFAULT 0",
            "transparency_hash TEXT",
            "write_state TEXT NOT NULL DEFAULT 'pending_verification'",
            "index_revision INTEGER NOT NULL DEFAULT 0",
        ).forEach { db.execSQL("ALTER TABLE pallasync_chain_state ADD COLUMN $it") }
        listOf("record_id TEXT", "object_hash TEXT", "receipt_json TEXT").forEach {
            db.execSQL("ALTER TABLE pallasync_outbox ADD COLUMN $it")
        }
        db.execSQL(
            "CREATE UNIQUE INDEX index_pallasync_outbox_chain_id_record_id " +
                "ON pallasync_outbox(chain_id,record_id)",
        )
        db.execSQL("UPDATE pallasync_outbox SET status='legacy_pending_verification'")
        statements.forEach(db::execSQL)
    }

    private const val winner =
        "lamport INTEGER NOT NULL, op_rank INTEGER NOT NULL, device_id BLOB NOT NULL, " +
            "record_id BLOB NOT NULL, op_index INTEGER NOT NULL"
    private val statements =
        listOf(
            "CREATE TABLE ps_registry(chain_id TEXT NOT NULL,generation INTEGER NOT NULL,device_id TEXT NOT NULL,record_json TEXT NOT NULL,approval_evidence TEXT NOT NULL,revocation_evidence TEXT,status TEXT NOT NULL,verification_key TEXT NOT NULL,PRIMARY KEY(chain_id,generation,device_id))",
            "CREATE TABLE ps_epochs(chain_id TEXT NOT NULL,epoch INTEGER NOT NULL,generation INTEGER NOT NULL,epoch_json TEXT NOT NULL,envelopes_json TEXT NOT NULL,key_alias TEXT,wrapped_secret BLOB,retention_state TEXT NOT NULL,PRIMARY KEY(chain_id,epoch))",
            "CREATE TABLE ps_events(chain_id TEXT NOT NULL,record_id TEXT NOT NULL,log_id TEXT NOT NULL,relay_seq INTEGER,object_hash TEXT NOT NULL,signed_json TEXT NOT NULL,receipt_json TEXT,validation_state TEXT NOT NULL,PRIMARY KEY(chain_id,record_id))",
            "CREATE UNIQUE INDEX index_ps_events_log_id_relay_seq ON ps_events(log_id,relay_seq)",
            "CREATE TABLE ps_authors(chain_id TEXT NOT NULL,generation INTEGER NOT NULL,device_id TEXT NOT NULL,device_seq INTEGER NOT NULL,record_id TEXT NOT NULL,record_hash TEXT NOT NULL,previous_hash TEXT,gap_before INTEGER NOT NULL,PRIMARY KEY(chain_id,generation,device_id,device_seq))",
            "CREATE TABLE ps_entities(chain_id TEXT NOT NULL,collection TEXT NOT NULL,entity_id TEXT NOT NULL,$winner,operation TEXT NOT NULL,scope TEXT NOT NULL,body_json TEXT NOT NULL,context_json TEXT NOT NULL,order_key TEXT,PRIMARY KEY(chain_id,collection,entity_id))",
            "CREATE TABLE ps_registers(chain_id TEXT NOT NULL,collection TEXT NOT NULL,scope TEXT NOT NULL,register_kind TEXT NOT NULL,$winner,body_json TEXT NOT NULL,PRIMARY KEY(chain_id,collection,scope,register_kind))",
            "CREATE TABLE ps_history(chain_id TEXT NOT NULL,collection TEXT NOT NULL,scope TEXT NOT NULL,last_access_seq BLOB NOT NULL,entity_id TEXT COLLATE BINARY NOT NULL,$winner,PRIMARY KEY(chain_id,collection,scope,last_access_seq,entity_id))",
            "CREATE UNIQUE INDEX index_ps_history_chain_id_collection_entity_id ON ps_history(chain_id,collection,entity_id)",
            "CREATE TABLE ps_projections(chain_id TEXT NOT NULL,collection TEXT NOT NULL,scope TEXT NOT NULL,live_count INTEGER NOT NULL,projection_limit INTEGER NOT NULL,user_limit INTEGER,eviction_key BLOB,eviction_entity TEXT,index_revision INTEGER NOT NULL,PRIMARY KEY(chain_id,collection,scope))",
            "CREATE TABLE ps_log(chain_id TEXT NOT NULL,log_id TEXT NOT NULL,relay_seq INTEGER NOT NULL,entry_hash TEXT NOT NULL,signed_json TEXT NOT NULL,receipt_json TEXT NOT NULL,validation_state TEXT NOT NULL,PRIMARY KEY(chain_id,log_id,relay_seq))",
            "CREATE TABLE ps_quarantine(chain_id TEXT NOT NULL,object_hash TEXT NOT NULL,reason TEXT NOT NULL,evidence_json TEXT NOT NULL,resolution_state TEXT NOT NULL,PRIMARY KEY(chain_id,object_hash))",
            "CREATE TABLE ps_snapshots(chain_id TEXT NOT NULL,compaction_id TEXT NOT NULL,part_index INTEGER NOT NULL,part_hash TEXT NOT NULL,signed_json TEXT NOT NULL,covered_seq INTEGER NOT NULL,coverage_json TEXT NOT NULL,staging_state TEXT NOT NULL,PRIMARY KEY(chain_id,compaction_id,part_index))",
            "CREATE TABLE ps_evidence(origin TEXT NOT NULL,relay_key_id TEXT NOT NULL,chain_id TEXT NOT NULL,log_id TEXT NOT NULL,kind TEXT NOT NULL,sequence INTEGER NOT NULL,object_hash TEXT NOT NULL,signed_json TEXT NOT NULL,PRIMARY KEY(origin,relay_key_id,chain_id,log_id,kind,sequence,object_hash))",
            "CREATE TABLE ps_authority(chain_id TEXT NOT NULL,kind TEXT NOT NULL,object_hash TEXT NOT NULL,generation INTEGER NOT NULL,signed_json TEXT NOT NULL,validation_state TEXT NOT NULL,PRIMARY KEY(chain_id,kind,object_hash))",
            "CREATE TABLE ps_migrations(migration_id TEXT NOT NULL,chunk_index INTEGER NOT NULL,source_hash TEXT NOT NULL,source_identity TEXT NOT NULL,target_chain TEXT,state TEXT NOT NULL,report_json TEXT NOT NULL,PRIMARY KEY(migration_id,chunk_index))",
        )
}
