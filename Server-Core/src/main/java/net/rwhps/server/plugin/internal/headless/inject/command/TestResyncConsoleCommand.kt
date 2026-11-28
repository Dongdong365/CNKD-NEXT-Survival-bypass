/*
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 * https://github.com/RW-HPS/RW-HPS/blob/master/LICENSE
 */

package net.rwhps.server.plugin.internal.headless.inject.command

import net.rwhps.server.func.StrCons
import net.rwhps.server.game.headless.core.link.FullGameSyncLink
import net.rwhps.server.util.game.command.CommandHandler

internal object TestResyncConsoleCommand {
    fun register(
        handler: CommandHandler,
        enabled: Boolean = java.lang.Boolean.getBoolean("cnkd.testResync"),
        syncLink: () -> FullGameSyncLink?
    ) {
        if (!enabled) return

        handler.register("testresync", "#测试服：在当前对局触发一次全量同步") { _: Array<String>, log: StrCons ->
            val link = syncLink()
            if (link == null) {
                log("[TestResync] 当前引擎不支持全量同步。")
                return@register
            }

            log("[TestResync] 请求手动全量同步。")
            val result = link.syncFullGameState()
            if (result.success) {
                log("[TestResync] 服务端已执行全量同步流程，尚未确认客户端加载结果。")
            } else {
                val reason = when (result.error?.message) {
                    "full_game_sync_requires_started_room" -> "对局未开始、地图尚未就绪或房间正在切换状态"
                    "full_game_sync_busy" -> "服务端正在同步"
                    "full_game_sync_match_changed" -> "对局已经变更"
                    else -> result.error?.message ?: "未知原因"
                }
                log("[TestResync] 全量同步未完成：$reason")
            }
        }
    }
}
