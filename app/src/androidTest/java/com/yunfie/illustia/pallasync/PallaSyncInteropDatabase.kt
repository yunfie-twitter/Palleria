package com.yunfie.illustia.pallasync

import android.content.Context
import android.os.Bundle
import androidx.room.Room
import androidx.room.RoomDatabase
import com.yunfie.illustia.pallasync.data.PallaSyncDatabase
import java.io.File

/** Disposable on-disk DB for the normal isolated-relay scenario; never the application's DB. */
internal object PallaSyncInteropDatabase {
    const val NAME = "pallasync-normal-interop-test.db"

    fun open(
        context: Context,
        persistent: Boolean,
        resume: Boolean,
        args: Bundle,
    ): PallaSyncDatabase {
        if (!persistent) return fresh(context)
        val name = persistentName(args)
        if (!resume) context.deleteDatabase(name)
        return Room.databaseBuilder(context, PallaSyncDatabase::class.java, name).build()
    }

    private fun persistentName(args: Bundle): String =
        when {
            args.getString("archive_restore_process_death") == "true" -> "pallasync-archive-restore-process-death-test.db"
            args.getString("records_413_process_death") == "true" -> "pallasync-413-process-death-test.db"
            args.getString("receipt_not_found_process_death") == "true" -> "pallasync-receipt-not-found-process-death-test.db"
            args.getString("transparency_recovery_process_death") == "true" -> "pallasync-transparency-recovery-process-death-test.db"
            args.getString("transparency_recovery_cancel_resume") == "true" -> "pallasync-transparency-recovery-cancel-test.db"
            else -> "pallasync-reseed-process-death-test.db"
        }

    private fun fresh(context: Context): PallaSyncDatabase {
        context.deleteDatabase(NAME)
        return Room
            .databaseBuilder(context, PallaSyncDatabase::class.java, NAME)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()
    }

    fun close(
        database: PallaSyncDatabase,
        context: Context,
    ) {
        val path = File(database.openHelper.writableDatabase.path)
        val disposable = path.canonicalFile == context.getDatabasePath(NAME).canonicalFile
        database.close()
        if (disposable) check(context.deleteDatabase(NAME))
    }
}
