package kr.java.documind.domain.issue.service.fingerprint;

/**
 * 게임 환경에서 실제로 발생하는 다양한 에러 패턴 모음
 *
 * <p>실제 게임 로그 샘플을 기반으로 Fingerprint 생성 테스트에 사용
 */
public class GameErrorPatterns {

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Java/Kotlin 표준 예외 패턴
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public static final String NPE_STANDARD =
            """
            java.lang.NullPointerException: Cannot invoke "com.game.entity.Player.getInventory()" because "this.player" is null
            at com.game.inventory.InventorySystem.addItem(InventorySystem.java:142)
            at com.game.player.PlayerController.handleItemPickup(PlayerController.java:89)
            at com.game.input.InputHandler.processAction(InputHandler.java:234)
            """;

    public static final String INDEX_OUT_OF_BOUNDS =
            """
            java.lang.IndexOutOfBoundsException: Index 5 out of bounds for length 3
            at java.util.ArrayList.rangeCheck(ArrayList.java:659)
            at com.game.quest.QuestManager.getQuest(QuestManager.java:67)
            at com.game.ui.QuestUI.displayQuest(QuestUI.java:45)
            """;

    public static final String CLASS_CAST =
            """
            java.lang.ClassCastException: class com.game.item.Item cannot be cast to class com.game.item.Weapon
            at com.game.combat.WeaponSystem.equipWeapon(WeaponSystem.java:123)
            at com.game.player.PlayerEquipment.equip(PlayerEquipment.java:56)
            """;

    public static final String ILLEGAL_STATE =
            """
            java.lang.IllegalStateException: Game session not initialized
            at com.game.session.GameSession.start(GameSession.java:78)
            at com.game.controller.GameController.startMatch(GameController.java:34)
            """;

    public static final String ILLEGAL_ARGUMENT =
            """
            java.lang.IllegalArgumentException: Player ID cannot be null or empty
            at com.game.player.PlayerService.findById(PlayerService.java:45)
            at com.game.social.FriendService.addFriend(FriendService.java:67)
            """;

    public static final String NUMBER_FORMAT =
            """
            java.lang.NumberFormatException: For input string: "abc123"
            at java.lang.Integer.parseInt(Integer.java:652)
            at com.game.config.ConfigParser.parseLevel(ConfigParser.java:89)
            """;

    public static final String OUT_OF_MEMORY =
            """
            java.lang.OutOfMemoryError: Java heap space
            at java.util.Arrays.copyOf(Arrays.java:3332)
            at com.game.graphics.TextureCache.loadTexture(TextureCache.java:234)
            at com.game.graphics.AssetLoader.loadAssets(AssetLoader.java:123)
            """;

    public static final String STACK_OVERFLOW =
            """
            java.lang.StackOverflowError: null
            at com.game.ai.PathFinder.findPath(PathFinder.java:78)
            at com.game.ai.PathFinder.findPath(PathFinder.java:78)
            at com.game.ai.PathFinder.findPath(PathFinder.java:78)
            """;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Unity 게임 엔진 에러 패턴
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public static final String UNITY_NULL_REFERENCE =
            """
            NullReferenceException: Object reference not set to an instance of an object
            PlayerInventory.AddItem (Item item) (at Assets/Scripts/PlayerInventory.cs:45)
            ItemPickup.OnTriggerEnter (UnityEngine.Collider other) (at Assets/Scripts/ItemPickup.cs:18)
            UnityEngine.Physics.Internal_TriggerEnter (UnityEngine.Collider col) (at <hash>:0)
            """;

    public static final String UNITY_UNASSIGNED_REFERENCE =
            """
            UnassignedReferenceException: The variable playerController of PlayerMovement has not been assigned.
            You probably need to assign the playerController variable of the PlayerMovement script in the inspector.
            UnityEngine.MonoBehaviour.StartCoroutine (System.Collections.IEnumerator routine) (at <hash>:0)
            GameManager.SpawnPlayer (System.String playerId, UnityEngine.Vector3 position) (at Assets/Scripts/GameManager.cs:142)
            NetworkManager.OnPlayerJoined (Photon.Realtime.Player newPlayer) (at Assets/Scripts/NetworkManager.cs:89)
            """;

    public static final String UNITY_MISSING_COMPONENT =
            """
            MissingComponentException: There is no 'Rigidbody' attached to the "Player" game object
            UnityEngine.GameObject.GetComponent[T] () (at <hash>:0)
            PlayerController.Start () (at Assets/Scripts/PlayerController.cs:23)
            UnityEngine.Object.Internal_InstantiateSingle (UnityEngine.Object data) (at <hash>:0)
            """;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Unreal Engine 에러 패턴
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public static final String UNREAL_ASSERTION_FAILED =
            """
            Assertion failed: IsValid(PlayerController) [File:/Build/++UE5/Sync/Engine/Source/Runtime/Engine/Private/GameMode.cpp] [Line: 234]
            LogTemp: Error: BP_PlayerCharacter_C::TakeDamage - Invalid DamageType
            LogScript: Warning: Script Msg: Attempted to access None
            """;

    public static final String UNREAL_BLUEPRINT_ERROR =
            """
            LogScript: Warning: Script Msg: Attempted to access None trying to read property CallFunc_GetPlayerController_ReturnValue
            Function /Game/Blueprints/BP_GameMode.BP_GameMode_C:ReceiveBeginPlay
            Function /Script/Engine.Actor:ReceiveBeginPlay
            """;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 네트워크 에러 패턴
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public static final String NETWORK_CONNECT_TIMEOUT =
            """
            java.net.ConnectException: Connection timed out: connect
            at java.net.DualStackPlainSocketImpl.connect0(Native Method)
            at java.net.DualStackPlainSocketImpl.socketConnect(DualStackPlainSocketImpl.java:80)
            at com.game.network.MatchmakingClient.connect(MatchmakingClient.java:56)
            at com.game.matchmaking.MatchmakingService.joinQueue(MatchmakingService.java:123)
            """;

    public static final String NETWORK_SOCKET_TIMEOUT =
            """
            java.net.SocketTimeoutException: Read timed out
            at java.net.SocketInputStream.socketRead0(Native Method)
            at java.net.SocketInputStream.socketRead(SocketInputStream.java:116)
            at com.game.network.GameServerConnection.receive(GameServerConnection.java:123)
            at com.game.network.NetworkManager.update(NetworkManager.java:89)
            """;

    public static final String NETWORK_UNKNOWN_HOST =
            """
            java.net.UnknownHostException: game-server-asia.example.com
            at java.net.InetAddress.getAllByName0(InetAddress.java:1281)
            at java.net.InetAddress.getAllByName(InetAddress.java:1193)
            at com.game.network.ServerResolver.resolve(ServerResolver.java:34)
            at com.game.network.ConnectionPool.createConnection(ConnectionPool.java:67)
            """;

    public static final String NETWORK_NETTY_TIMEOUT =
            """
            io.netty.channel.ConnectTimeoutException: connection timed out: /192.168.1.100:8080
            at io.netty.channel.nio.AbstractNioChannel$AbstractNioUnsafe.finishConnect(AbstractNioChannel.java:321)
            at io.netty.channel.nio.NioEventLoop.processSelectedKey(NioEventLoop.java:719)
            at com.game.network.NettyClient.connect(NettyClient.java:145)
            """;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 데이터베이스 에러 패턴
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public static final String DB_DEADLOCK =
            """
            java.sql.SQLException: Deadlock found when trying to get lock; try restarting transaction
            at com.mysql.jdbc.SQLError.createSQLException(SQLError.java:1073)
            at com.mysql.jdbc.MysqlIO.checkErrorPacket(MysqlIO.java:3593)
            at com.game.database.PlayerDataRepository.save(PlayerDataRepository.java:78)
            at com.game.player.PlayerService.updatePlayerData(PlayerService.java:123)
            """;

    public static final String DB_DUPLICATE_KEY =
            """
            org.postgresql.util.PSQLException: ERROR: duplicate key value violates unique constraint "player_id_uq"
            Detail: Key (player_id)=(player_12345) already exists.
            at org.postgresql.core.v3.QueryExecutorImpl.receiveErrorResponse(QueryExecutorImpl.java:2533)
            at com.game.database.PlayerRepository.insert(PlayerRepository.java:45)
            """;

    public static final String DB_QUERY_TIMEOUT =
            """
            java.sql.SQLTimeoutException: Query execution was interrupted, maximum statement execution time exceeded
            at com.mysql.jdbc.StatementImpl.executeQuery(StatementImpl.java:1234)
            at com.game.ranking.RankingCalculator.updateRanks(RankingCalculator.java:234)
            at com.game.ranking.RankingScheduler.calculateDaily(RankingScheduler.java:89)
            """;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 게임 비즈니스 로직 에러 패턴
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public static final String GAME_INVENTORY_FULL =
            """
            com.game.exception.InventoryFullException: Inventory is full (50/50 slots)
            at com.game.inventory.InventorySystem.addItem(InventorySystem.java:142)
            at com.game.player.PlayerController.pickupItem(PlayerController.java:89)
            at com.game.world.ItemDrop.onPlayerInteract(ItemDrop.java:56)
            """;

    public static final String GAME_INSUFFICIENT_CURRENCY =
            """
            com.game.exception.InsufficientCurrencyException: Not enough gold: required 1000, available 500
            at com.game.shop.ShopService.purchase(ShopService.java:67)
            at com.game.ui.ShopUI.onBuyButtonClick(ShopUI.java:123)
            at com.game.ui.UIEventHandler.handleClick(UIEventHandler.java:45)
            """;

    public static final String GAME_MATCHMAKING_TIMEOUT =
            """
            com.game.exception.MatchmakingTimeoutException: No match found after 300 seconds
            at com.game.matchmaking.MatchmakingQueue.findMatch(MatchmakingQueue.java:234)
            at com.game.matchmaking.MatchmakingService.poll(MatchmakingService.java:156)
            at com.game.matchmaking.MatchmakingScheduler.run(MatchmakingScheduler.java:89)
            """;

    public static final String GAME_PLAYER_BANNED =
            """
            com.game.exception.PlayerBannedException: Player account banned until 2024-12-31 23:59:59 for reason: Cheating detected
            at com.game.auth.AuthenticationService.validatePlayerStatus(AuthenticationService.java:78)
            at com.game.auth.AuthenticationService.login(AuthenticationService.java:45)
            at com.game.controller.AuthController.handleLogin(AuthController.java:123)
            """;

    public static final String GAME_INVALID_STATE =
            """
            com.game.exception.InvalidGameStateException: Cannot perform action 'ATTACK' in current game state: GAME_OVER
            at com.game.state.GameStateManager.validateAction(GameStateManager.java:123)
            at com.game.combat.CombatSystem.attack(CombatSystem.java:67)
            at com.game.player.PlayerController.performAttack(PlayerController.java:234)
            """;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 동적 데이터 포함 패턴 (정규화 테스트용)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public static final String DYNAMIC_UUID =
            """
            com.game.exception.PlayerNotFoundException: Player 550e8400-e29b-41d4-a716-446655440000 not found in session abc-123-def-456
            at com.game.player.PlayerService.findById(PlayerService.java:45)
            """;

    public static final String DYNAMIC_IP_ADDRESS =
            """
            java.net.ConnectException: Connection refused to 192.168.1.100:3306 from 10.0.0.5:54321
            at com.game.network.DatabaseConnector.connect(DatabaseConnector.java:78)
            """;

    public static final String DYNAMIC_TIMESTAMP =
            """
            com.game.exception.SessionExpiredException: Session expired at 2024-02-04T14:30:25.123Z for user player_12345
            at com.game.session.SessionManager.validate(SessionManager.java:67)
            """;

    public static final String DYNAMIC_FILE_PATH_WINDOWS =
            """
            java.io.FileNotFoundException: C:\\Users\\Player\\Documents\\GameData\\config.json (The system cannot find the file specified)
            at com.game.config.ConfigLoader.load(ConfigLoader.java:34)
            """;

    public static final String DYNAMIC_FILE_PATH_UNIX =
            """
            java.io.FileNotFoundException: /home/user/game/save/player_data_12345.sav (No such file or directory)
            at com.game.save.SaveDataManager.load(SaveDataManager.java:89)
            """;

    public static final String DYNAMIC_MEMORY_ADDRESS =
            """
            java.lang.NullPointerException: GameObject@7f3a8bc9 has null reference to Transform@a1b2c3d4
            at com.game.entity.EntityManager.update(EntityManager.java:123)
            """;

    public static final String DYNAMIC_ITEM_IDS =
            """
            com.game.exception.InvalidEquipmentException: Cannot equip item_weapon_legendary_001 in slot item_armor_helmet_005
            at com.game.equipment.EquipmentSystem.equip(EquipmentSystem.java:67)
            """;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 멀티스레딩 에러 패턴
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public static final String CONCURRENT_MODIFICATION =
            """
            java.util.ConcurrentModificationException: null
            at java.util.ArrayList$Itr.checkForComodification(ArrayList.java:911)
            at java.util.ArrayList$Itr.next(ArrayList.java:861)
            at com.game.entity.EntityManager.updateAll(EntityManager.java:67)
            at com.game.world.WorldUpdater.tick(WorldUpdater.java:123)
            """;

    public static final String ILLEGAL_MONITOR_STATE =
            """
            java.lang.IllegalMonitorStateException: object not locked by thread before notify()
            at java.lang.Object.notify(Native Method)
            at com.game.sync.SyncManager.notifyUpdate(SyncManager.java:45)
            at com.game.sync.SyncWorker.run(SyncWorker.java:89)
            """;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // JSON/Serialization 에러 패턴
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public static final String JSON_MAPPING_EXCEPTION =
            """
            com.fasterxml.jackson.databind.JsonMappingException: Cannot deserialize instance of `java.lang.String` out of START_OBJECT token
            at com.fasterxml.jackson.databind.DeserializationContext.handleUnexpectedToken(DeserializationContext.java:1635)
            at com.game.config.ConfigLoader.load(ConfigLoader.java:34)
            at com.game.GameInitializer.initialize(GameInitializer.java:67)
            """;

    public static final String GSON_SYNTAX_EXCEPTION =
            """
            com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1 path $
            at com.google.gson.Gson.fromJson(Gson.java:941)
            at com.game.network.PacketSerializer.deserialize(PacketSerializer.java:78)
            at com.game.network.PacketHandler.handle(PacketHandler.java:45)
            """;
}
