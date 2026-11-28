/*
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 * https://github.com/RW-HPS/RW-HPS/blob/master/LICENSE
 */

package com.corrodinggames.rts.gameFramework.j

import com.corrodinggames.rts.game.n
import net.rwhps.server.game.headless.core.link.TEST_RESYNC_PROPERTY
import java.lang.reflect.InvocationTargetException

/** One-shot test input to the native desync check, not a replacement for the check or its log. */
internal object HessAutoResyncTest {
    // The native first full-resync attempt requires bo > 60 in engine tick units.
    private const val TEST_DESYNC_AGE = 61f
    private val nativeDesyncCheck by lazy {
        ad::class.java.getDeclaredMethod("e", Float::class.javaPrimitiveType).apply { isAccessible = true }
    }

    /** Caller must be on the game thread, inside the shared full-sync lifecycle guard. */
    fun forceOnce(engine: ad): Throwable? = synchronized(engine) {
        if (!java.lang.Boolean.getBoolean(TEST_RESYNC_PROPERTY)) {
            return@synchronized IllegalStateException("test_auto_resync_disabled")
        }
        if (!engine.B || !engine.C || !engine.aW || engine.F) {
            return@synchronized IllegalStateException("full_game_sync_requires_started_room")
        }
        if (ad.`as`) {
            return@synchronized IllegalStateException("test_auto_resync_engine_full_sync_disabled")
        }
        val connection = engine.aM.asSequence().filterIsInstance<c>().firstOrNull { peer ->
            val team = peer.z
            peer.p && !peer.a && !peer.b && !peer.s && !peer.t &&
                team != null && team.k >= 0 && !team.w && !team.b() && n.k(team.k) === team
        } ?: return@synchronized IllegalStateException("test_auto_resync_no_player")

        val check = nativeDesyncCheck // Resolve first so incompatible builds leave all flags untouched.
        val originalFlag = connection.v
        val originalAge = engine.bo
        val originalAttempts = engine.bp
        val originalResyncCount = engine.ar
        try {
            connection.v = true
            // Simulate an eligible mismatch past the first-attempt delay, without spoofing a
            // checksum packet or advancing the real game clock. e(F) itself logs and syncs.
            engine.bo = TEST_DESYNC_AGE
            engine.bp = 0
            check.invoke(engine, 0f)
            if (engine.ar != originalResyncCount) null else IllegalStateException("test_auto_resync_not_completed")
        } catch (error: InvocationTargetException) {
            throw error.targetException
        } finally {
            // Native az() increments bp before sending the full save, and clears pending desync
            // flags. Preserve that cleanup, but don't charge a synthetic attempt to the backoff.
            val nativeSyncStarted = engine.bp != 0
            engine.bp = originalAttempts
            connection.v = !nativeSyncStarted && originalFlag
            if (!nativeSyncStarted) engine.bo = originalAge
        }
    }
}
