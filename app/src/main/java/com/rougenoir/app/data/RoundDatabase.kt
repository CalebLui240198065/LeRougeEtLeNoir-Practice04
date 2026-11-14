package com.rougenoir.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.os.Handler
import android.os.Looper
import com.rougenoir.app.game.GameType
import com.rougenoir.app.game.RoundOutcome
import com.rougenoir.app.game.RoundResult
import com.rougenoir.app.game.SnakeEnding
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Saves completed rounds for all four games. Keep the original database filename
 * so Android can upgrade the earlier Bingo-only schema in place.
 */
internal class RoundDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "salon_bingo.db", null, 2) {

    data class Round(
        val username: String,
        val number: Long,
        val completedAtMillis: Long,
        val result: RoundResult
    )

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(db: SQLiteDatabase) {
        // Use a common set of searchable columns and a JSON object for game-specific data.
        // SQLite on Android 7 can store JSON as text without a JSON extension.
        db.execSQL(
            """
            CREATE TABLE game_rounds (
                round_id TEXT NOT NULL UNIQUE,
                username TEXT NOT NULL,
                game_type TEXT NOT NULL CHECK (game_type IN ('bingo', 'tic_tac_toe', 'snake', 'breakout')),
                round_number INTEGER NOT NULL CHECK (round_number > 0),
                system_time INTEGER NOT NULL,
                outcome TEXT NOT NULL CHECK (outcome IN ('win', 'loss', 'draw')),
                details_json TEXT NOT NULL,
                PRIMARY KEY (username, game_type, round_number)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX game_rounds_by_time ON game_rounds (username, system_time DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion != 1 || newVersion != 2) {
            throw SQLiteException("No round database migration from $oldVersion to $newVersion")
        }
        // SQLiteOpenHelper runs the upgrade in a transaction. Copy the existing
        // IDs, round numbers, winning numbers, and times before retiring the old table.
        onCreate(db)
        db.execSQL(
            """
            INSERT INTO game_rounds
                (round_id, username, game_type, round_number, system_time, outcome, details_json)
            SELECT round_id, username, 'bingo', round_number, system_time, 'win',
                printf('{"winning_numbers":[%d,%d,%d,%d,%d]}', number_1, number_2, number_3, number_4, number_5)
            FROM bingo_rounds
            """.trimIndent()
        )
        db.execSQL("DROP TABLE bingo_rounds")
    }

    /** Reusing the same ID for a retry prevents duplicate rows and preserves the original time. */
    fun saveRound(
        id: String,
        username: String,
        result: RoundResult,
        completedAtMillis: Long,
        onResult: (Result<Long>) -> Unit
    ) = onWorker(onResult) {
        val db = writableDatabase
        // Find a retry, choose the next number for this player and game, and
        // insert inside one transaction so concurrent saves cannot reuse a number.
        db.beginTransaction()
        try {
            val existing = db.rawQuery(
                "SELECT round_number FROM game_rounds WHERE round_id = ? AND username = ? AND game_type = ?",
                arrayOf(id, username, result.game.key)
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }

            val number = existing ?: insertRound(db, id, username, result, completedAtMillis)
            db.setTransactionSuccessful()
            number
        } finally {
            db.endTransaction()
        }
    }

    private fun insertRound(
        db: SQLiteDatabase,
        id: String,
        username: String,
        result: RoundResult,
        completedAtMillis: Long
    ): Long {
        val next = db.rawQuery(
            "SELECT COALESCE(MAX(round_number), 0) + 1 FROM game_rounds WHERE username = ? AND game_type = ?",
            arrayOf(username, result.game.key)
        ).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getLong(0)
        }
        val values = ContentValues().apply {
            put("round_id", id)
            put("username", username)
            put("game_type", result.game.key)
            put("round_number", next)
            put("system_time", completedAtMillis)
            put("outcome", result.outcome.key)
            put("details_json", encodeDetails(result).toString())
        }
        db.insertOrThrow("game_rounds", null, values)
        return next
    }

    /** Read the player's newest completions, optionally limited to one game. */
    fun readRounds(
        username: String,
        game: GameType?,
        onResult: (Result<List<Round>>) -> Unit
    ) = onWorker(onResult) {
        val filter = if (game == null) "" else " AND game_type = ?"
        val args = if (game == null) arrayOf(username) else arrayOf(username, game.key)
        readableDatabase.rawQuery(
            """
            SELECT username, round_number, system_time, game_type, outcome, details_json
            FROM game_rounds
            WHERE username = ?$filter
            ORDER BY system_time DESC, rowid DESC
            """.trimIndent(),
            args
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val type = GameType.entries.first { it.key == cursor.getString(3) }
                    val outcome = RoundOutcome.entries.first { it.key == cursor.getString(4) }
                    add(
                        Round(
                            username = cursor.getString(0),
                            number = cursor.getLong(1),
                            completedAtMillis = cursor.getLong(2),
                            result = decodeDetails(type, outcome, JSONObject(cursor.getString(5)))
                        )
                    )
                }
            }
        }
    }

    // Keep stored values independent of the labels displayed in each language.
    private fun encodeDetails(result: RoundResult): JSONObject = JSONObject().apply {
        when (result) {
            is RoundResult.Bingo -> {
                put("winning_numbers", JSONArray(result.winningNumbers))
                result.drawCount?.let { put("draw_count", it) }
            }
            is RoundResult.TicTacToe -> {
                put("army_rival", result.armyRival)
                put("difficulty", result.difficulty)
                put("move_count", result.moveCount)
            }
            is RoundResult.Snake -> {
                put("score", result.score)
                put("ending", result.ending.key)
                put("difficulty", result.difficulty)
            }
            is RoundResult.Breakout -> {
                put("score", result.score)
                put("lives_left", result.livesLeft)
                put("difficulty", result.difficulty)
            }
        }
    }

    private fun decodeDetails(game: GameType, outcome: RoundOutcome, json: JSONObject): RoundResult =
        when (game) {
            GameType.BINGO -> {
                val numbers = json.getJSONArray("winning_numbers")
                RoundResult.Bingo(
                    List(numbers.length()) { numbers.getInt(it) },
                    json.optInt("draw_count").takeIf { it > 0 }
                )
            }
            GameType.TIC_TAC_TOE -> RoundResult.TicTacToe(
                outcome, json.getBoolean("army_rival"), json.getInt("difficulty"), json.getInt("move_count")
            )
            GameType.SNAKE -> {
                val key = json.getString("ending")
                val ending = SnakeEnding.entries.firstOrNull { it.key == key }
                    ?: throw JSONException("Unknown Snake ending")
                RoundResult.Snake(json.getInt("score"), ending, json.getInt("difficulty"))
            }
            GameType.BREAKOUT -> RoundResult.Breakout(
                outcome, json.getInt("score"), json.getInt("lives_left"), json.getInt("difficulty")
            )
        }

    /** Keep I/O serialized off the UI thread and deliver results on the main thread. */
    private fun <T> onWorker(onResult: (Result<T>) -> Unit, operation: () -> T) {
        worker.execute {
            val result = try {
                Result.success(operation())
            } catch (error: SQLiteException) {
                Result.failure(error)
            } catch (error: JSONException) {
                Result.failure(error)
            }
            main.post { onResult(result) }
        }
    }
}
