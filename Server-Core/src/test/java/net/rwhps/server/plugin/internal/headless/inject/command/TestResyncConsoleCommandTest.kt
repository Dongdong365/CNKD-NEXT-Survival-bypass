package net.rwhps.server.plugin.internal.headless.inject.command

import net.rwhps.server.func.StrCons
import net.rwhps.server.game.headless.core.link.FullGameSyncLink
import net.rwhps.server.game.headless.core.link.FullGameSyncResult
import net.rwhps.server.util.game.command.CommandHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TestResyncConsoleCommandTest {
    private val handler = CommandHandler("")
    private val output = mutableListOf<String>()
    private val log = StrCons { output += it }

    @Test
    fun `disabled command is not registered and never touches the engine`() {
        TestResyncConsoleCommand.register(handler, enabled = false) { error("unexpected lookup") }

        assertEquals(CommandHandler.ResponseType.unknownCommand, handler.handleMessage("testresync", log).type)
        assertTrue(output.isEmpty())
    }

    @Test
    fun `enabled command calls the normal full sync path once`() {
        val link = FakeLink(FullGameSyncResult(success = true))
        TestResyncConsoleCommand.register(handler, enabled = true) { link }

        assertEquals(CommandHandler.ResponseType.manyArguments, handler.handleMessage("testresync auto extra", log).type)
        assertEquals(CommandHandler.ResponseType.valid, handler.handleMessage("testresync extra", log).type)
        assertTrue(output.last().contains("testresync [auto]"))
        assertEquals(0, link.calls)
        assertEquals(0, link.autoCalls)
        assertEquals(CommandHandler.ResponseType.valid, handler.handleMessage("testresync", log).type)
        assertEquals(1, link.calls)
        assertTrue(output.last().contains("尚未确认客户端加载结果"))
    }

    @Test
    fun `auto mode calls the automatic branch once instead of the direct sync`() {
        val link = FakeLink(FullGameSyncResult(success = true))
        TestResyncConsoleCommand.register(handler, enabled = true) { link }

        assertEquals(CommandHandler.ResponseType.valid, handler.handleMessage("testresync auto", log).type)
        assertEquals(0, link.calls)
        assertEquals(1, link.autoCalls)
        assertTrue(output.last().contains("引擎已执行自动全量同步流程"))
    }

    @Test
    fun `auto mode rejects a missing participant without trying the direct sync`() {
        val link = FakeLink(FullGameSyncResult(false, IllegalStateException("test_auto_resync_no_player")))
        TestResyncConsoleCommand.register(handler, enabled = true) { link }

        handler.handleMessage("testresync auto", log)

        assertEquals(0, link.calls)
        assertEquals(1, link.autoCalls)
        assertTrue(output.last().contains("没有可用于测试的在线参战玩家"))
    }

    @Test
    fun `not ready match reports the rejection from the existing sync path`() {
        val link = FakeLink(FullGameSyncResult(false, IllegalStateException("full_game_sync_requires_started_room")))
        TestResyncConsoleCommand.register(handler, enabled = true) { link }

        handler.handleMessage("testresync", log)

        assertEquals(1, link.calls)
        assertTrue(output.last().contains("全量同步未完成"))
        assertTrue(output.last().contains("对局未开始"))
    }

    @Test
    fun `unsupported engine is reported without calling any sync`() {
        TestResyncConsoleCommand.register(handler, enabled = true) { null }

        handler.handleMessage("testresync", log)

        assertEquals(1, output.size)
        assertTrue(output.single().contains("当前引擎不支持全量同步"))
    }

    private class FakeLink(private val result: FullGameSyncResult) : FullGameSyncLink {
        var calls = 0
            private set

        var autoCalls = 0
            private set

        override fun syncFullGameState(): FullGameSyncResult {
            calls++
            return result
        }

        override fun testAutoResync(): FullGameSyncResult {
            autoCalls++
            return result
        }

        override fun canSyncFullGameState(): Boolean = error("the full sync path owns readiness checks")

        override fun syncFullGameStateForGeneration(expectedGeneration: Long): FullGameSyncResult =
            error("the command does not schedule delayed work")
    }
}
