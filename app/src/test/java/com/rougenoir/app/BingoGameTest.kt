package com.rougenoir.app

import com.rougenoir.app.game.BingoGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Covers Bingo rules and restoration cases that are easy to miss on a shuffled card. */
class BingoGameTest {
    @Test
    fun callsNeverRepeatAndUncalledOrRepeatMarksAreIgnored() {
        val game = BingoGame.shuffled(Random(5))
        val allNumbers = (1..25).toSet()
        assertEquals(allNumbers, game.card.toSet())
        assertFalse(game.mark(0))
        game.callNext()
        val calledCell = game.card.indexOf(game.calledNumbers.single())
        assertTrue(game.mark(calledCell))
        assertFalse(game.mark(calledCell))
        assertFalse(game.mark((calledCell + 1) % game.card.size))

        repeat(30) { game.callNext() }
        assertEquals(25, game.calledCount)
        assertEquals(allNumbers, game.calledNumbers.toSet())
        assertFalse(game.canCall)
    }

    @Test
    fun rowsColumnsAndBothDiagonalsNeedExactlyFiveMarks() {
        val numbers = (1..25).toList()
        val winningCases = listOf(
            listOf(10, 11, 12, 13, 14),
            listOf(2, 7, 12, 17, 22),
            listOf(0, 6, 12, 18, 24),
            listOf(4, 8, 12, 16, 20)
        )
        for (line in winningCases) {
            val calls = line.map { numbers[it] } + numbers.filterIndexed { index, _ -> index !in line }
            val game = BingoGame(numbers, calls)
            for (index in line) {
                assertNull(game.winningLine)
                game.callNext()
                assertTrue(game.mark(index))
            }
            assertEquals(line, game.winningLine)
            assertFalse(game.canCall)
            assertFalse(game.mark(1))
        }
    }

    @Test
    fun restoringProgressKeepsTheWinningLineWhenOneMarkCompletesTwoLines() {
        val numbers = (1..25).toList()
        val row = listOf(10, 11, 12, 13, 14)
        val column = listOf(2, 7, 12, 17, 22)
        val marks = (row + column).toSet() - 12
        val game = BingoGame(numbers, numbers, 25, marks)
        assertNull(game.winningLine)
        assertTrue(game.mark(12))

        val restored = BingoGame(game.card, game.callOrder, game.calledCount, game.markedIndices)
        assertEquals(row, restored.winningLine)
        assertEquals(listOf(11, 12, 13, 14, 15), restored.winningLine?.map { restored.card[it] })
    }
}
