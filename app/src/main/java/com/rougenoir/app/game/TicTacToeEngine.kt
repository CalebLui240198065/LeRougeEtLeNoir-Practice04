package com.rougenoir.app.game

import java.util.Random
import kotlin.math.max
import kotlin.math.min

/**
 * Pure tic-tac-toe rules with a small minimax opponent. Kept free of Android types so
 * the rules can be exercised by ordinary JVM tests.
 */
class TicTacToeEngine {

    companion object {
        const val EMPTY = 0
        const val TALENT = 1
        const val RIVAL = 2

        val WIN_LINES = arrayOf(
            intArrayOf(0, 1, 2), intArrayOf(3, 4, 5), intArrayOf(6, 7, 8),
            intArrayOf(0, 3, 6), intArrayOf(1, 4, 7), intArrayOf(2, 5, 8),
            intArrayOf(0, 4, 8), intArrayOf(2, 4, 6)
        )

        fun winnerOf(cells: IntArray): Pair<Int, IntArray?> {
            for (line in WIN_LINES) {
                val first = cells[line[0]]
                if (first != EMPTY && first == cells[line[1]] && first == cells[line[2]]) {
                    return first to line
                }
            }
            return EMPTY to null
        }
    }

    var cells: IntArray = IntArray(9)
        private set

    var currentPlayer: Int = TALENT
        private set

    var winner: Int = EMPTY
        private set

    var winningLine: IntArray? = null
        private set

    val isDraw: Boolean
        get() = winner == EMPTY && cells.none { it == EMPTY }

    val isOver: Boolean
        get() = winner != EMPTY || isDraw

    fun reset() {
        cells = IntArray(9)
        currentPlayer = TALENT
        winner = EMPTY
        winningLine = null
    }

    fun play(index: Int): Boolean {
        if (index !in 0..8 || isOver || cells[index] != EMPTY) return false
        cells[index] = currentPlayer
        val (found, line) = winnerOf(cells)
        if (found != EMPTY) {
            winner = found
            winningLine = line
        } else {
            currentPlayer = if (currentPlayer == TALENT) RIVAL else TALENT
        }
        return true
    }

    /**
     * Chooses a move for the rival. Level 0 leaves room for mistakes, level 2 is
     * unbeatable. The result is always a legal empty cell, or -1 for a finished board.
     */
    fun chooseRivalMove(level: Int, random: Random = Random()): Int {
        if (isOver || currentPlayer != RIVAL) return -1
        val legal = cells.indices.filter { cells[it] == EMPTY }
        if (legal.isEmpty()) return -1

        val mistakeChance = when (level) {
            0 -> 55
            1 -> 22
            else -> 0
        }
        if (random.nextInt(100) < mistakeChance) return legal[random.nextInt(legal.size)]
        return bestMove()
    }

    /** Evaluate each legal move with minimax and pick the best one for the current side. */
    fun bestMove(): Int {
        if (isOver) return -1
        val player = currentPlayer
        var bestScore = if (player == RIVAL) Int.MIN_VALUE else Int.MAX_VALUE
        var bestIndex = -1
        for (index in cells.indices) {
            if (cells[index] != EMPTY) continue
            val copy = cells.copyOf()
            copy[index] = player
            val score = minimax(copy, player = if (player == RIVAL) TALENT else RIVAL, depth = 0)
            if (player == RIVAL) {
                if (score > bestScore) {
                    bestScore = score
                    bestIndex = index
                }
            } else if (score < bestScore) {
                bestScore = score
                bestIndex = index
            }
        }
        return bestIndex
    }

    // The depth adjustment rewards quicker wins and makes the losing side delay defeat.
    private fun minimax(board: IntArray, player: Int, depth: Int): Int {
        val (winner, _) = winnerOf(board)
        if (winner == RIVAL) return 10 - depth
        if (winner == TALENT) return depth - 10
        if (board.none { it == EMPTY }) return 0

        return if (player == RIVAL) {
            var best = Int.MIN_VALUE
            for (index in board.indices) {
                if (board[index] != EMPTY) continue
                val copy = board.copyOf()
                copy[index] = RIVAL
                best = max(best, minimax(copy, TALENT, depth + 1))
            }
            best
        } else {
            var best = Int.MAX_VALUE
            for (index in board.indices) {
                if (board[index] != EMPTY) continue
                val copy = board.copyOf()
                copy[index] = TALENT
                best = min(best, minimax(copy, RIVAL, depth + 1))
            }
            best
        }
    }
}
