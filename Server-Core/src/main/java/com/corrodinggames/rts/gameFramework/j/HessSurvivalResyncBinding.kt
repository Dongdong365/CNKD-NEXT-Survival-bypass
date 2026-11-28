/*
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 * https://github.com/RW-HPS/RW-HPS/blob/master/LICENSE
 */

package com.corrodinggames.rts.gameFramework.j

import com.corrodinggames.rts.game.n
import com.corrodinggames.rts.game.units.am
import com.corrodinggames.rts.game.units.d.e as CommandCenter
import com.corrodinggames.rts.gameFramework.l
import com.corrodinggames.rts.gameFramework.n.f as Mission
import net.rwhps.asm.api.listener.RedirectionListener
import net.rwhps.server.game.room.SurvivalHumanRoster
import net.rwhps.server.plugin.internal.headless.inject.core.GameEngine

/** The single-player survival rule expects l.bs to own a live HQ. In a cooperative
 * headless match, keep that reference on a surviving member of the starting human alliance. */
internal object HessSurvivalResyncBinding : RedirectionListener {
    private const val NATIVE_VERDICT_INTERVAL_MS = 1_000
    private val lastNativeVerdictAt by lazy {
        Mission::class.java.getDeclaredField("c").apply { isAccessible = true }
    }

    override fun invoke(obj: Any, desc: String, vararg args: Any?) {
        try {
            val engine = l.B() ?: return
            val mission = engine.ce ?: return
            if (!mission.k) return
            val afterLoad = obj === engine.ca
            if (obj !== mission) {
                // The successful return of y.a(k, true, true, true) is shared by full
                // and quick resync. Do not retarget a normal saved-game load here.
                if (!afterLoad || args.size != 4 || args[1] != true || args[2] != true || args[3] != true) return
            } else if (engine.bs !== n.i && engine.by <= lastNativeVerdictAt.getInt(mission) + NATIVE_VERDICT_INTERVAL_MS) {
                // The verdict is periodic. Don't walk every unit every frame after binding.
                return
            }
            val room = GameEngine.statisticsModuleOrNull?.room ?: return
            if (!room.isStartGame) return
            val roster = room.startingSurvivalHumans ?: return
            val previous = engine.bs
            val slot = restore(engine, mission, roster) ?: return
            val phase = if (afterLoad) "after resync" else "before mission update"
            if (previous === n.i) {
                l.b("Headless", "Restored survival team binding $phase: slot=$slot")
            } else {
                l.b("Headless", "Reassigned survival team binding $phase: slot=${previous?.k} -> $slot")
            }
        } catch (error: Exception) {
            l.b("Headless", "Unable to restore survival team binding: ${error.javaClass.simpleName}")
        } catch (error: LinkageError) {
            l.b("Headless", "Unsupported survival team binding: ${error.javaClass.simpleName}")
        }
    }

    internal fun restore(engine: l, mission: Mission, roster: SurvivalHumanRoster): Int? {
        if (!mission.k) return null
        val net = engine.bX ?: return null
        if (!net.B || !net.C || !net.aW || net.z !== n.i) return null

        val current = engine.bs
        var replacement: n? = null
        for (entry in am.bE) {
            val unit = entry as? am ?: continue
            // Match the original survival verdict's HQ and alive predicates.
            if ((unit !is CommandCenter && !unit.bP) || unit.bV || unit.u()) continue
            val owner = unit.bX ?: continue
            if (!isParticipant(owner, roster) || owner.E || owner.F || owner.G) continue
            // Keep a still-valid binding even when a lower-numbered teammate also has an HQ.
            if (owner === current) return null
            if (replacement == null || owner.k < replacement.k) replacement = owner
        }

        val team = replacement ?: run {
            // No qualifying member: never let an unrelated team keep this mission alive.
            // An existing participant is left to the native defeat path.
            if (current != null && current !== n.i && !isParticipant(current, roster)) engine.bs = n.i
            return null
        }
        engine.bs = team
        return team.k
    }

    private fun isParticipant(team: n, roster: SurvivalHumanRoster): Boolean =
        roster.contains(team.k, team.r) && !team.w && !team.b() && n.k(team.k) === team
}
