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
import net.rwhps.server.dependent.redirections.game.CooperativeSurvivalVerdictTransformer
import net.rwhps.server.game.room.SurvivalHumanRoster
import net.rwhps.server.plugin.internal.headless.inject.core.GameEngine

/** Repair the headless local-player reference and provide the native cooperative defeat test. */
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
            val roster = cooperativeRoster(engine, mission) ?: return
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

    /** Called only at the engine's original HQ decision; preserve every other verdict path. */
    @JvmStatic
    fun shouldDefeat(nativeDefeat: Boolean, target: Any?): Boolean {
        return try {
            val mission = target as? Mission ?: return nativeDefeat
            val engine = l.B() ?: return nativeDefeat
            val roster = cooperativeRoster(engine, mission) ?: return nativeDefeat
            !am.bE.any { liveHumanOwner(it, roster, headquartersOnly = false) != null }
        } catch (error: Exception) {
            l.b("Headless", "Falling back to native survival verdict: ${error.javaClass.simpleName}")
            nativeDefeat
        } catch (error: LinkageError) {
            l.b("Headless", "Unsupported cooperative survival verdict: ${error.javaClass.simpleName}")
            nativeDefeat
        }
    }

    private fun cooperativeRoster(engine: l, mission: Mission): SurvivalHumanRoster? {
        if (!isHeadlessSurvival(engine, mission)) return null
        val room = GameEngine.statisticsModuleOrNull?.room ?: return null
        return if (room.isStartGame) room.startingSurvivalHumans else null
    }

    private fun isHeadlessSurvival(engine: l, mission: Mission): Boolean {
        if (!mission.k || engine.ce !== mission) return false
        val net = engine.bX ?: return false
        return net.B && net.C && net.aW && net.z === n.i
    }

    internal fun restore(engine: l, mission: Mission, roster: SurvivalHumanRoster): Int? {
        if (!isHeadlessSurvival(engine, mission)) return null
        val current = engine.bs
        val headquartersOnly = !CooperativeSurvivalVerdictTransformer.isPatched(mission.javaClass)
        var replacement: n? = null
        for (entry in am.bE) {
            val owner = liveHumanOwner(entry, roster, headquartersOnly) ?: continue
            // Keep a live binding even when a lower-numbered teammate also has units.
            if (owner === current) return null
            if (replacement == null || owner.k < replacement.k) replacement = owner
        }

        val team = replacement ?: run {
            // Don't keep an unrelated binding alive. Let the engine handle actual defeat.
            if (current != null && current !== n.i && !isParticipant(current, roster)) engine.bs = n.i
            return null
        }
        engine.bs = team
        return team.k
    }

    private fun liveHumanOwner(entry: Any?, roster: SurvivalHumanRoster, headquartersOnly: Boolean): n? {
        val unit = entry as? am ?: return null
        if (unit.bV || unit.u() || (headquartersOnly && unit !is CommandCenter && !unit.bP)) return null
        val owner = unit.bX ?: return null
        return owner.takeIf { isParticipant(it, roster) && !it.E && !it.F && !it.G }
    }

    private fun isParticipant(team: n, roster: SurvivalHumanRoster): Boolean =
        roster.contains(team.k, team.r) && !team.w && !team.b() && n.k(team.k) === team
}
