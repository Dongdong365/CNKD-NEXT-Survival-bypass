# 客户端 resync 帧保护（实验版）

这是独立的桌面 Java 客户端 Agent，不是服务端插件。它保留原版的存档重载和网络连接；只在客户端完成局内 resync 读档后、推进模拟帧之前检查原有队伍槽位。它不覆盖游戏文件、不改 `Server-All`、不改协议或地图，不会让没装 Agent 的玩家客户端自动获得这个行为。

## 构建与校验

在项目根目录用 JDK 构建。Agent 的字节码目标为 Java 8；`game-lib.jar`、`slick.jar` 和用于补齐缺失类型的 Android API JAR 仅用于校验，不会打包进 Agent。

```powershell
.\gradlew.bat :ASM-Framework:clientResyncAgentCheck
```

产物：`ASM-Framework/build/libs/cnkd-client-resync-agent.jar`。

## 隔离联机验证

1. 使用你有权运行、且字节码匹配的桌面版 Rusted Warfare 客户端。保留原来的 Java 启动参数、游戏资源目录和工作目录，在**客户端 JVM** 的参数中、主类或 `-jar` 之前追加 `-javaagent:路径/cnkd-client-resync-agent.jar=observe`。带空格的路径需要把整个参数放进引号。不要把这个参数加到服务端，不要加入全局 `JAVA_TOOL_OPTIONS`。
2. 两个挂点都会打印 `[ClientResync] patched ...`。如果只见 `unsupported client bytecode` 或没有两个 `patched` 日志，不要把结果当成生效：需要针对实际客户端重新核对字节码。测试只使用与本仓库 `game-lib.jar` 中七个相关类指纹匹配的版本。Android 不支持这一 JVM 启动方式。
3. 建议先在测试房中用同一张生存图复现。用服务端现有的主动全量同步路径触发；有条件时再验证引擎自动 `Resyncing game for ...`。`=observe` 只打印本该等待的 `would_hold`，不改变原本的模拟时序。记录 `loaded candidate=...`、`would_hold`/`holding` 和 `released reason=...`。
4. 去掉 `=observe`，重启同一客户端跑对照。期望同步期间本地不推进模拟帧、原有队伍仍在原槽位，地图不因此判输，同步后能继续同一局。再分别检查连续两次同步、真实断线，以及保持未加载 Agent 的对照端；注意有没有新增 desync。去掉整个 `-javaagent:...` 参数并重启，即回滚。

## 暂停与恢复

- 只在已有队伍、只持有一条房主连接的客户端处理实际进入读档分支的原版全量同步包（类型 35）时记录候选。确认原版读档成功、且 resync 计数已提交后才允许触发帧保护；普通包、失败读档、非局内、主机端都不触发。
- 挂到 `game.i.b(float,int)` 中网络处理后已有的暂停分支。没有 `sleep`，不会停掉后续外层网络更新，也不会选择性跳过地图触发器或改写原版暂停状态。
- 本机槽位身份和原有可见人类队伍槽位恢复即继续。远端状态只用客户端可见的队伍与近期 ping 信息，不等价于确认每位玩家 TCP 或全体客户端完成读档。未确认在线、败退、观战及 AI 不计入远端等待名单。
- 读档后的等待有 3 秒上限；连续重载复用已开始的等待上限，不会把玩家长期锁住。真正与房主断开就撤掉保护；超时也恢复原版行为，不冒充玩家在线。若在 `timeout` 后仍能复现、或 `ready` 时地图已判输，这个挂点没有覆盖实际根因。
