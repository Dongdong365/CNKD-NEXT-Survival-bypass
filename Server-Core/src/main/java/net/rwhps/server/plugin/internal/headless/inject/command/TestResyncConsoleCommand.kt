/*
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 * https://github.com/RW-HPS/RW-HPS/blob/master/LICENSE
 */

package net.rwhps.server.plugin.internal.headless.inject.command

import net.rwhps.server.func.StrCons
import net.rwhps.server.game.headless.core.link.FullGameSyncLink
import net.rwhps.server.game.headless.core.link.TEST_RESYNC_PROPERTY
import net.rwhps.server.util.game.command.CommandHandler

internal object TestResyncConsoleCommand {
    fun register(
        handler: CommandHandler,
        enabled: Boolean = java.lang.Boolean.getBoolean(TEST_RESYNC_PROPERTY),
        syncLink: () -> FullGameSyncLink?
    ) {
        if (!enabled) return

        handler.register("testresync", "[auto]", "#测试服：在当前对局触发一次全量同步") { args: Array<String>, log: StrCons ->
            val auto = when {
                args.isEmpty() -> false
                args[0].equals("auto", ignoreCase = true) -> true
                else -> {
                    log("[TestResync] 用法：testresync [auto]")
                    return@register
                }
            }
            val link = syncLink()
            if (link == null) {
                log("[TestResync] 当前引擎不支持全量同步。")
                return@register
            }

            val result = if (auto) {
                log("[TestResync] 模拟一次失步，交给引擎自动处理。")
                link.testAutoResync()
            } else {
                log("[TestResync] 请求手动全量同步。")
                link.syncFullGameState()
            }
            if (result.success) {
                val completed = if (auto) "引擎已执行自动全量同步流程" else "服务端已执行全量同步流程"
                log("[TestResync] $completed，尚未确认客户端加载结果。")
            } else {
                val reason = when (result.error?.message) {
                    "full_game_sync_requires_started_room" -> "对局未开始、地图尚未就绪或房间正在切换状态"
                    "full_game_sync_busy" -> "服务端正在同步"
                    "full_game_sync_match_changed" -> "对局已经变更"
                    "test_auto_resync_disabled" -> "服务器未启用 cnkd.testResync"
                    "test_auto_resync_unsupported" -> "当前引擎不支持模拟失步测试"
                    "test_auto_resync_engine_full_sync_disabled" -> "引擎已禁用全量失步修复"
                    "test_auto_resync_no_player" -> "没有可用于测试的在线参战玩家"
                    "test_auto_resync_not_completed" -> "引擎没有完成本次自动全量同步"
                    else -> result.error?.message ?: "未知原因"
                }
                log("[TestResync] 全量同步未完成：$reason")
            }
        }
    }
}
