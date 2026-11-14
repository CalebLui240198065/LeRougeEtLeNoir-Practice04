package com.rougenoir.app

import com.rougenoir.app.game.TicTacToeEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/** Exercises legal moves, victory detection, and the strongest rival's move choice. */
class TicTacToeEngineTest {

    @Test
    fun talentWinsOnTopRow() {
        val engine = TicTacToeEngine()
        engine.play(0) // talent
        engine.play(3) // rival
        engine.play(1)
        engine.play(4)
        assertTrue(engine.play(2))
        assertEquals(TicTacToeEngine.TALENT, engine.winner)
        assertTrue(engine.winningLine!!.contentEquals(intArrayOf(0, 1, 2)))
    }

    @Test
    fun occupiedCellCannotBePlayed() {
        val engine = TicTacToeEngine()
        assertTrue(engine.play(4))
        assertEquals(TicTacToeEngine.RIVAL, engine.currentPlayer)
        assertEquals(false, engine.play(4))
        assertEquals(1, engine.cells.count { it != TicTacToeEngine.EMPTY })
    }

    @Test
    fun masterBlocksImmediateThreat() {
        val engine = TicTacToeEngine()
        engine.play(0)
        engine.play(4)
        engine.play(1)
        // The rival must stop talent from taking cell 2.
        val move = engine.chooseRivalMove(2, Random(7))
        assertEquals(2, move)
    }

    @Test
    fun masterTakesWinningMove() {
        val engine = TicTacToeEngine()
        engine.play(0)
        engine.play(3)
        engine.play(1)
        engine.play(4)
        engine.play(8)
        // Rival has 3 and 4; it can win at 5.
        val move = engine.chooseRivalMove(2, Random(99))
        assertEquals(5, move)
    }
}
