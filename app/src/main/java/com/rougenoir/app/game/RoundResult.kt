package com.rougenoir.app.game

/** Stable keys identify the games in SQLite and in the round history filter. */
internal enum class GameType(val key: String) {
    BINGO("bingo"),
    TIC_TAC_TOE("tic_tac_toe"),
    SNAKE("snake"),
    BREAKOUT("breakout")
}

internal enum class RoundOutcome(val key: String) {
    WIN("win"),
    LOSS("loss"),
    DRAW("draw")
}

/** Snake distinguishes collisions from the player filling the entire grid. */
enum class SnakeEnding(val key: String) {
    WALL("wall"),
    TAIL("tail"),
    BOARD_FILLED("board_filled")
}

/** Each finished game supplies the facts that describe how that particular round went. */
internal sealed interface RoundResult {
    val game: GameType
    val outcome: RoundOutcome

    data class Bingo(val winningNumbers: List<Int>, val drawCount: Int?) : RoundResult {
        override val game = GameType.BINGO
        override val outcome = RoundOutcome.WIN
    }

    data class TicTacToe(
        override val outcome: RoundOutcome,
        val armyRival: Boolean,
        val difficulty: Int,
        val moveCount: Int
    ) : RoundResult {
        override val game = GameType.TIC_TAC_TOE
    }

    data class Snake(val score: Int, val ending: SnakeEnding, val difficulty: Int) : RoundResult {
        override val game = GameType.SNAKE
        override val outcome = if (ending == SnakeEnding.BOARD_FILLED) RoundOutcome.WIN else RoundOutcome.LOSS
    }

    data class Breakout(
        override val outcome: RoundOutcome,
        val score: Int,
        val livesLeft: Int,
        val difficulty: Int
    ) : RoundResult {
        override val game = GameType.BREAKOUT
    }
}
