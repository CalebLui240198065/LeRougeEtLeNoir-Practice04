package com.rougenoir.app.game

import kotlin.random.Random

/**
 * Rules and in-memory state for one 5 × 5 Bingo card. All 25 squares hold a number,
 * including the center, so every winning line contains exactly five numbers.
 */
internal class BingoGame(
    val card: List<Int>,
    val callOrder: List<Int>,
    calledCount: Int = 0,
    markedIndices: Collection<Int> = emptyList()
) {
    private val marks = markedIndices.toMutableSet()

    var calledCount = calledCount
        private set

    val calledNumbers: List<Int>
        get() = callOrder.subList(0, calledCount)

    val markedIndices: Set<Int>
        get() = marks.toSet()

    // When one mark finishes two lines, always choose rows first, then columns,
    // then diagonals. Each round has a single, reproducible result.
    val winningLine: List<Int>?
        get() = lines.firstOrNull { line -> line.all { it in marks } }

    val canCall: Boolean
        get() = calledCount < CELL_COUNT && winningLine == null

    init {
        require(card.size == CELL_COUNT && card.toSet() == numbers)
        require(callOrder.size == CELL_COUNT && callOrder.toSet() == numbers)
        require(calledCount in 0..CELL_COUNT)
        require(marks.all { it in card.indices && isCalled(it) })
    }

    /** Advances through a shuffled call order, which prevents duplicate calls. */
    fun callNext() {
        if (canCall) calledCount++
    }

    fun isCalled(index: Int): Boolean = card[index] in calledNumbers

    /** Only a called, unmarked square on an unfinished card can change the game. */
    fun mark(index: Int): Boolean {
        if (index !in card.indices || !isCalled(index) || winningLine != null) return false
        return marks.add(index)
    }

    companion object {
        const val SIDE = 5
        const val CELL_COUNT = SIDE * SIDE

        private val numbers = (1..CELL_COUNT).toSet()
        private val lines = buildList {
            repeat(SIDE) { row -> add(List(SIDE) { column -> row * SIDE + column }) }
            repeat(SIDE) { column -> add(List(SIDE) { row -> row * SIDE + column }) }
            add(List(SIDE) { index -> index * SIDE + index })
            add(List(SIDE) { index -> index * SIDE + SIDE - 1 - index })
        }

        /** Shuffle the visible card and the hidden call order independently. */
        fun shuffled(random: Random = Random.Default): BingoGame =
            BingoGame(numbers.shuffled(random), numbers.shuffled(random))
    }
}
