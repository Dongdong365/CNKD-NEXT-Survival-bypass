目前正在尝试通过“脏”步骤——即正常模式中增加 自动重启房间 的开关，让服务端检测到游戏结束但不进行任何操作，从而让生存图运行。

适用于管理员全权看管在游戏结束.battleroom 运行。

初步推测是 生存图监测过于严格？ 但我已无力更改:(

更改内容几乎全依靠于ai，我提供口头描述进行修改。~~因为太多我已经看不懂了~~

这是针对于使用生存图的个性化优化，遵循AGPL开源。每个人有每个人的生存图实现方案，我把我的方案作为即将成功的参考;)

部分生存图完全可用，一小部分生存图可能因为本身就有bug所以无法用，推荐换图。

内测正常，正在准备实测。

```yml
{
  "common": {
    "defStartCommand": "start",
    "log": "WARN",
    "errorLogMaxSizeMb": 8,
    "errorLogRetainFiles": 3,
    "cmdTitle": "",
    "followBetaVersion": false,
    "port": 5123,
    "serverName": "CNKD NEXT Start 服",
    "chatSenderName": "CNKD",
    "subtitle": "",
    "autoUpList": false,
    "listStatusSuffixEnabled": false,
    "listNamePinnedCharacterCount": 0,
    "listPinnedCharacterCount": 20,
    "publicListEntries": [],
    "listExperimentEnabled": false,
    "listExperimentGameName": "",
    "listExperimentCreatedBy": "",
    "listExperimentMapName": "",
    "listExperimentGameStatus": "",
    "listExperimentPlayerCount": -1,
    "listExperimentMaxPlayerCount": -1,
    "listPromotionModeEnabled": false,
    "listPromotionGameStatus": "chat",
    "listPromotionPlayerCount": -1,
    "listPromotionMaxPlayerCount": -1,
    "listPromotionSortBoostEnabled": false,
    "listPromotionSortBoostPrefix": "𒀱",
    "listPromotionBlankMapEnabled": false
  },
  "network": {
    "ipCheckMultiLanguageSupport": true,
    "singleUserRelay": false,
    "singleUserRelayMod": false,
    "mixProtocolEnable": false,
    "rconMixEnable": false,
    "webMixEnable": false,
    "rconPort": 0,
    "rconPasswd": "RW-HPS",
    "webPort": 0,
    "webToken": "RW-HPS",
    "webHOST": "",
    "sslEnable": false,
    "sslMixEnable": false,
    "sslPasswd": "RW-HPS"
  },
  "gameplay": {
    "serverID": "CNKD-NEXT",
    "enterAd": "欢迎加入本服务器",
    "startAd": "服务器已就绪！",
    "maxPlayerJoinAd": "房间已达到管理员设置的人数上限 请稍后再试 @CNKD",
    "startPlayerJoinAd": "对局正在进行中 请稍后再试 @CNKD",
    "passwd": "",
    "maxPlayer": 100,
    "oneAdmin": true,
    "startMinPlayerSize": -1,
    "autoStartMinPlayerSize": -1,
    "autoStartCountdownSeconds": 5,
    "manualStartCountdownSeconds": 5,
    "maxGameIngTime": 7200,
    "maxOnlyAIGameIngTime": 3600,
    "enableAI": false,
    "maxMessageLen": 100,
    "maxUnit": 1000,
    "defIncome": 1.0,
    "defaultStartingCreditsAmount": 4000,
    "disableNukes": false,
    "defaultFogMode": "lineOfSight",
    "isAfk": true,
    "muteAll": false,
    "turnStoneIntoGold": false,
    "modsLoadErrorPrint": false,
    "saveRePlayFile": true
  },
  "lobby": {
    "disableTeamChange": false,
    "disablePositionChange": false,
    "adminCanOverrideTeamRules": true,
    "adminCanOverridePositionRules": true,
    "enableFreeForAllMode": false,
    "enableAlternateTeamsOnJoin": false,
    "enableSurvivalMapMode": false,
    "survivalGameOverGraceSeconds": 180,
    "survivalEnemyClearConfirmSeconds": 45,
    "enableLobbySyncOptimization": true,
    "lobbyRefreshHeartbeatMillis": 30000,
    "lobbyPacketHeartbeatMillis": 30000,
    "latencyHeartbeatMillis": 1000,
    "enableLobbyGameSyncPacketFilter": true,
    "lobbyGameSyncPacketHeartbeatMillis": 3000,
    "lobbyGameSyncPacketJoinGraceMillis": 180000,
    "reconnectGraceMillis": 5000,
    "enableGameOverBattleRoomReturn": false,
    "gameOverReturnCountdownSeconds": 30,
    "autoResumeAfterGameOver": true,
    "pruneDisconnectedPlayersOnGameOver": true,
    "enableJoinReadinessTips": false,
    "joinTipModWarningThreshold": 1,
    "enableIndividualSurrender": false,
    "enableAutoKickPlayerWhenAllUnitsDestroyed": false,
    "autoKickPlayerWhenAllUnitsDestroyedMessage": "你已失去全部单位 已被服务器移出本局"
  },
  "map": {
    "enableMapLock": false,
    "lockMap": "",
    "enableModPackageMapDiscovery": true,
    "modPackageMapScanMaxArchiveSizeMb": 512,
    "enableModPackageMapSupportExtraction": true,
    "enableModPackageMapAutoExtraction": true,
    "enableModPackageMapRuntimeGc": true,
    "modPackageMapRuntimeGcGraceHours": 24,
    "modPackageMapRuntimeGcMaxDeletePerRun": 20
  },
  "ranked": {
    "coreNodeEnabled": false,
    "gatewayBaseUrl": "http://127.0.0.1:3010",
    "queueCode": "ranked_1v1",
    "pollIntervalSeconds": 2,
    "jumpDelaySeconds": 1
  },
  "advertisingRedirect": {
    "enabled": false,
    "targetHost": "101.43.41.22",
    "targetPort": 5123,
    "targetRoomId": "",
    "redirectTiming": "afterJoin",
    "joinProbeSafeMode": true,
    "delaySeconds": 2,
    "showNotice": true,
    "noticeBeforeRedirect": "欢迎来到 CNKD 导航服 正在为你跳转到主服",
    "noticeOnManualFallback": "如未自动跳转 请手动加入 {host}:{port}",
    "kickAfterRedirect": true,
    "cooldownSecondsPerPlayer": 10,
    "maxRedirectAttemptsPerConnection": 1,
    "allowLocalhostTarget": false,
    "allowPrivateIpTarget": true,
    "allowedTargetHosts": [],
    "disableWhenRankedCoreNode": true,
    "disableWhenRankedShardNode": true,
    "adminBypass": true,
    "logRedirect": true
  },
  "console": {
    "verbosity": "normal",
    "showPlayerJoinLeave": true,
    "showPlayerChat": false,
    "showLowLatencySummary": false,
    "showSurvivalRepair": false,
    "showRankedCompatibility": false
  },
  "pluginLogging": {
    "consoleLevel": "INFO",
    "fileLevel": "INFO",
    "infoLinesPerMinutePerPlugin": 120,
    "maxMessageLength": 2048,
    "maxFileSizeMb": 8,
    "retainDays": 7
  },
  "singleRoomLowLatency": {
    "enabled": true,
    "metricsEnabled": false,
    "summaryIntervalSeconds": 30,
    "fastGameCommand": true,
    "preTickGameCommandDrain": true,
    "preTickGameCommandDrainRequiresStartedRoom": true,
    "preTickGameCommandDrainLimit": 128,
    "inlineGameCommandProcessing": false,
    "inlineGameCommandRequiresStartedRoom": true,
    "skipPlayerOperationEvents": true,
    "skipPingActionIntercept": true,
    "disableTurnStoneIntoGoldOnFastPath": true,
    "serverInjectedCommandLeadTicks": 8,
    "hessOutboundCommandLeadTicks": 8,
    "hessFrameInvariantGuardEnabled": true,
    "headlessFpsOverride": 60,
    "nettyWorkerThreadsOverride": 1,
    "directEventLoopWrites": true
  }
}
```

ServerConfig.json:97 → lobby 段的 "autoResumeAfterGameOver": true

改为false直接飞掉游戏结束后自动重置/返回、让房间停在结算态,等房主 .battleroom 逃生