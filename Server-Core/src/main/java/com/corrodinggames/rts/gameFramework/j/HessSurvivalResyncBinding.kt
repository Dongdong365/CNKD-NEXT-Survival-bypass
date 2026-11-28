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
import java.util.Collections
import java.util.IdentityHashMap

/**
 * The headless host is n.i (slot -1). The network save loader assigns l.bs from ad.z,
 * so a resync can leave the native survival check looking for an HQ on that hidden team.
 * Rebind only the simulation-side reference; ad.z must remain the hidden network host.
 */
internal object HessSurvivalResyncBinding : RedirectionListener {
    override fun invoke(obj: Any, desc: String, vararg args: Any?) {
        try {
            val engine = l.B() ?: return
            val mission = engine.ce ?: return
            val afterLoad = obj === engine.ca
            if (obj !== mission) {
                // y.a(k, true, true, true) completes both full and quick resync loads.
                // Its single success return is where the save-loader listener runs.
                if (!afterLoad || args.size != 4 || args[1] != true || args[2] != true || args[3] != true) return
            }
            val slot = restore(engine, mission) ?: return
            val phase = if (afterLoad) "after resync" else "before mission update"
            l.b("Headless", "Restored survival team binding $phase: slot=$slot")
        } catch (error: Exception) {
            l.b("Headless", "Unable to restore survival team binding: ${error.javaClass.simpleName}")
        } catch (error: LinkageError) {
            l.b("Headless", "Unsupported survival team binding: ${error.javaClass.simpleName}")
        }
    }

    internal fun restore(engine: l, mission: Mission): Int? {
        if (!mission.k || engine.bs !== n.i) return null
        val net = engine.bX ?: return null
        if (!net.B || !net.C || !net.aW || net.z !== n.i) return null

        // Use the actual authenticated engine connections, not placeholder teams or just a count
        // of nonempty slots. A disconnected slot is not made online by the resync repair.
        val connected = Collections.newSetFromMap(IdentityHashMap<n, Boolean>())
        for (entry in net.aM) {
            val connection = entry as? c ?: continue
            if (!connection.p || connection.a || connection.b) continue
            val team = connection.z ?: continue
            if (team.k >= 0 && !team.w && !team.b() && !team.E && !team.F && !team.G && n.k(team.k) === team) {
                connected.add(team)
            }
        }
        if (connected.isEmpty()) return null

        // Match the native survival predicate in Mission.c(float): live command center or
        // a custom HQ (bP), whose owner is the exact team object, not just the same alliance.
        var boundTeam: n? = null
        for (entry in am.bE) {
            val unit = entry as? am ?: continue
            if (unit !is CommandCenter && !unit.bP || unit.bV || unit.u()) continue
            val owner = unit.bX ?: continue
            if (owner in connected && (boundTeam == null || owner.k < boundTeam.k)) boundTeam = owner
        }
        val team = boundTeam ?: return null
        engine.bs = team
        return team.k
    }
}
