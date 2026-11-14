package com.rougenoir.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/**
 * Stores one row per completed Bingo round in the app's private SQLite database.
 * The application owns this helper so reads and writes share one background worker.
 */
internal class BingoDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "salon_bingo.db", null, 1) {

    data class Round(
        val username: String,
        val number: Long,
        val winningNumbers: List<Int>,
        val completedAtMillis: Long
    )

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(db: SQLiteDatabase) {
        // A stable round ID makes retries safe. The composite key gives every
        // player their own sequence, while keeping the five numbers as integers.
        db.execSQL(
            """
            CREATE TABLE bingo_rounds (
                round_id TEXT NOT NULL UNIQUE,
                username TEXT NOT NULL,
                round_number INTEGER NOT NULL CHECK (round_number > 0),
                number_1 INTEGER NOT NULL CHECK (number_1 BETWEEN 1 AND 25),
                number_2 INTEGER NOT NULL CHECK (number_2 BETWEEN 1 AND 25),
                number_3 INTEGER NOT NULL CHECK (number_3 BETWEEN 1 AND 25),
                number_4 INTEGER NOT NULL CHECK (number_4 BETWEEN 1 AND 25),
                number_5 INTEGER NOT NULL CHECK (number_5 BETWEEN 1 AND 25),
                system_time INTEGER NOT NULL,
                PRIMARY KEY (username, round_number)
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 is the initial schema. Add an explicit migration before
        // raising the version so completed rounds survive app updates.
        throw SQLiteException("No Bingo database migration from $oldVersion to $newVersion")
    }

    fun saveRound(
        id: String,
        username: String,
        numbers: List<Int>,
        completedAtMillis: Long,
        onResult: (Result<Long>) -> Unit
    ) = onWorker(onResult) {
        val db = writableDatabase
        // Check for a retry, assign the next round number, and insert as one
        // transaction. A failed insert leaves the player's sequence unchanged.
        db.beginTransaction()
        try {
            val existingRound = db.rawQuery(
                "SELECT round_number FROM bingo_rounds WHERE round_id = ? AND username = ?",
                arrayOf(id, username)
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }

            val roundNumber = existingRound ?: insertRound(
                db, id, username, numbers, completedAtMillis
            )
            db.setTransactionSuccessful()
            roundNumber
        } finally {
            db.endTransaction()
        }
    }

    private fun insertRound(
        db: SQLiteDatabase,
        id: String,
        username: String,
        numbers: List<Int>,
        completedAtMillis: Long
    ): Long {
        val nextRound = db.rawQuery(
            "SELECT COALESCE(MAX(round_number), 0) + 1 FROM bingo_rounds WHERE username = ?",
            arrayOf(username)
        ).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getLong(0)
        }
        val values = ContentValues().apply {
            put("round_id", id)
            put("username", username)
            put("round_number", nextRound)
            numbers.forEachIndexed { index, number -> put("number_${index + 1}", number) }
            // Store UTC epoch milliseconds from the device clock. The history
            // screen can format them using the player's current time zone.
            put("system_time", completedAtMillis)
        }
        db.insertOrThrow("bingo_rounds", null, values)
        return nextRound
    }

    /** Read only this account's rounds, in descending round order. */
    fun readRounds(username: String, onResult: (Result<List<Round>>) -> Unit) = onWorker(onResult) {
        readableDatabase.rawQuery(
            """
            SELECT username, round_number, number_1, number_2, number_3, number_4, number_5, system_time
            FROM bingo_rounds
            WHERE username = ?
            ORDER BY round_number DESC
            """.trimIndent(),
            arrayOf(username)
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        Round(
                            username = cursor.getString(0),
                            number = cursor.getLong(1),
                            winningNumbers = List(5) { index -> cursor.getInt(index + 2) },
                            completedAtMillis = cursor.getLong(7)
                        )
                    )
                }
            }
        }
    }

    /** Keep SQLite I/O off the UI thread and return the result there for rendering. */
    private fun <T> onWorker(onResult: (Result<T>) -> Unit, operation: () -> T) {
        worker.execute {
            val result = try {
                Result.success(operation())
            } catch (error: SQLiteException) {
                Result.failure(error)
            }
            main.post { onResult(result) }
        }
    }
}
