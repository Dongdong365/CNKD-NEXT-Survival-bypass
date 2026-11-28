/*
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 * https://github.com/RW-HPS/RW-HPS/blob/master/LICENSE
 */

package net.rwhps.server.game.room

/** Only a match that started with one human alliance has a shared survival side. */
internal class SurvivalHumanRoster private constructor(
    private val allyGroup: Int,
    private val slots: Set<Int>
) {
    fun contains(slot: Int, teamAllyGroup: Int): Boolean = teamAllyGroup == allyGroup && slot in slots

    companion object {
        /** Input contains the slot and ally group of each authenticated human at game start. */
        fun capture(participants: Iterable<Pair<Int, Int>>): SurvivalHumanRoster? {
            val slots = HashSet<Int>()
            var allyGroup: Int? = null
            for ((slot, group) in participants) {
                if (slot < 0 || group < 0) continue
                if (allyGroup != null && allyGroup != group) return null
                allyGroup = group
                slots.add(slot)
            }
            return allyGroup?.let { SurvivalHumanRoster(it, slots) }
        }
    }
}
