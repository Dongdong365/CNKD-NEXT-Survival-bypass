package net.rwhps.server.game.room

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SurvivalHumanRosterTest {
    @Test
    fun `one human alliance keeps all its starting participants regardless of slot order`() {
        val roster = checkNotNull(SurvivalHumanRoster.capture(listOf(5 to 2, 1 to 2, 3 to 2)))

        assertTrue(roster.contains(5, 2))
        assertTrue(roster.contains(1, 2))
        assertTrue(roster.contains(3, 2))
        assertFalse(roster.contains(0, 2))
        assertFalse(roster.contains(5, 1))
    }

    @Test
    fun `opposing teams are not treated as one cooperative survival side`() {
        for (playersPerSide in listOf(3, 5)) {
            val rosters = (0 until playersPerSide * 2).map { it to (it / playersPerSide) }
            assertNull(SurvivalHumanRoster.capture(rosters))
        }
    }

    @Test
    fun `spectators and hidden slots do not create or join a side`() {
        val nonParticipants = listOf(-1 to 0, 3 to -3)
        assertNull(SurvivalHumanRoster.capture(nonParticipants))
        val roster = checkNotNull(SurvivalHumanRoster.capture(nonParticipants + (2 to 4)))
        assertTrue(roster.contains(2, 4))
        assertFalse(roster.contains(-1, 4))
        assertFalse(roster.contains(3, 4))
    }
}
