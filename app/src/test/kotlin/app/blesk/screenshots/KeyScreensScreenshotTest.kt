package app.blesk.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.blesk.InAppNote
import app.blesk.core.calls.CallMember
import app.blesk.core.calls.MediaState
import app.blesk.core.crypto.phrase.RecoveryPhrase
import app.blesk.core.data.BotResult
import app.blesk.core.data.Connection
import app.blesk.core.data.KeyState
import app.blesk.core.data.SafetyCode
import app.blesk.core.data.SpaceResult
import app.blesk.core.data.Transfer
import app.blesk.core.data.calls.CallPhase
import app.blesk.core.data.calls.CallSession
import app.blesk.core.data.demo.DemoSeed
import app.blesk.core.data.diagnostics.CrashReport
import app.blesk.core.data.diagnostics.EventCode
import app.blesk.core.data.diagnostics.LogEntry
import app.blesk.core.data.model.Attachment
import app.blesk.core.data.model.BotButton
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.data.model.GLINT_REACTION
import app.blesk.core.data.model.Reaction
import app.blesk.core.data.model.RoomChannelKind
import app.blesk.core.data.settings.NotificationSettings
import app.blesk.core.data.settings.Privacy
import app.blesk.core.data.settings.Profile
import app.blesk.core.data.settings.ProxySetting
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.ChatBackdrop
import app.blesk.core.design.component.HeaderStyle
import app.blesk.core.design.component.IconButton
import app.blesk.core.design.glass.LensAction
import app.blesk.core.design.glass.LensScaffold
import app.blesk.core.design.glass.LensTab
import app.blesk.core.design.glass.LensTabBar
import app.blesk.core.design.glass.SearchLens
import app.blesk.core.design.glass.TopLens
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.icon.LivingIcon
import app.blesk.core.design.spark.KeyMerge
import app.blesk.core.design.theme.BleskTheme
import app.blesk.core.media.Playback
import app.blesk.core.motion.BleskMotion
import app.blesk.feature.calls.CallActions
import app.blesk.feature.calls.CallScreen
import app.blesk.feature.chats.ChatList
import app.blesk.feature.chats.ChatMotion
import app.blesk.feature.chats.ChatMotionDemo
import app.blesk.feature.chats.ConnectionStatus
import app.blesk.feature.chats.ContactActions
import app.blesk.feature.chats.ContactsTab
import app.blesk.feature.chats.ConversationCallbacks
import app.blesk.feature.chats.ConversationScreen
import app.blesk.feature.chats.ForwardScreen
import app.blesk.feature.chats.NewChatActions
import app.blesk.feature.chats.NewChatScreen
import app.blesk.feature.chats.Newcomer
import app.blesk.feature.chats.NewcomerChats
import app.blesk.feature.chats.PersonActions
import app.blesk.feature.chats.PersonProfileScreen
import app.blesk.feature.chats.RequestActions
import app.blesk.feature.chats.RequestsScreen
import app.blesk.feature.chats.SearchHit
import app.blesk.feature.chats.SearchResults
import app.blesk.feature.chats.VoiceChannelScreen
import app.blesk.feature.chats.groups.GroupActions
import app.blesk.feature.chats.groups.GroupInfoScreen
import app.blesk.feature.chats.groups.NewGroupScreen
import app.blesk.feature.chats.groups.SpaceActions
import app.blesk.feature.chats.groups.SpaceInfoScreen
import app.blesk.feature.chats.media.MiniPlayer
import app.blesk.feature.debug.DebugScreen
import app.blesk.feature.debug.HapticDemo
import app.blesk.feature.debug.MotionDemo
import app.blesk.feature.debug.MotionShowcase
import app.blesk.feature.profile.ProfileActions
import app.blesk.feature.profile.ProfileScreen
import app.blesk.feature.profile.QrCode
import app.blesk.feature.security.PhraseActions
import app.blesk.feature.security.PhraseScreen
import app.blesk.feature.security.PinAttempt
import app.blesk.feature.security.PinEntry
import app.blesk.feature.security.PrivacyActions
import app.blesk.feature.security.PrivacyScreen
import app.blesk.feature.security.WipeScreen
import app.blesk.feature.settings.AboutScreen
import app.blesk.feature.settings.AppearanceFileActions
import app.blesk.feature.settings.AppearanceScreen
import app.blesk.feature.settings.BotActions
import app.blesk.feature.settings.BotsScreen
import app.blesk.feature.settings.ChatUse
import app.blesk.feature.settings.ConnectOutcome
import app.blesk.feature.settings.LicensesScreen
import app.blesk.feature.settings.MetaballSandbox
import app.blesk.feature.settings.NotificationsScreen
import app.blesk.feature.settings.OfflineItem
import app.blesk.feature.settings.OfflineScreen
import app.blesk.feature.settings.PrivacyPolicyScreen
import app.blesk.feature.settings.ProxyCheck
import app.blesk.feature.settings.ProxyControls
import app.blesk.feature.settings.SafetyScreen
import app.blesk.feature.settings.ServerScreen
import app.blesk.feature.settings.ServerState
import app.blesk.feature.settings.SettingsActions
import app.blesk.feature.settings.SettingsList
import app.blesk.feature.settings.StorageActions
import app.blesk.feature.settings.StorageScreen
import app.blesk.feature.settings.StorageUse
import app.blesk.feature.settings.UpdateProgress
import app.blesk.feature.settings.UpdateScreen
import app.blesk.feature.settings.YouList
import app.blesk.navigation.InAppNotice
import app.blesk.shared.appearance.Appearance
import app.blesk.shared.appearance.ThemeSpec
import app.blesk.shared.protocol.BotCommand
import app.blesk.shared.protocol.BotInfo
import app.blesk.shared.protocol.GroupChannel
import app.blesk.shared.protocol.GroupChannelKind
import app.blesk.shared.protocol.GroupKind
import app.blesk.shared.protocol.GroupMember
import app.blesk.shared.protocol.GroupRole
import app.blesk.shared.protocol.GroupState
import app.blesk.shared.protocol.SpaceChannel
import app.blesk.shared.protocol.SpaceInfo
import app.blesk.shared.protocol.SpaceKind
import app.blesk.shared.protocol.SpaceMember
import app.blesk.shared.protocol.SpaceRole
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.util.TimeZone

/**
 * Key screens in every theme. The PNGs land in app/build/screenshots and are attached to each
 * release; they are reviewed by eye against the stop list and for contrast.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class KeyScreensScreenshotTest(private val name: String) {

    private val theme = ThemeSpec.presets.first { it.preset.name.lowercase() == name }
    private val now = Instant.parse("2026-10-11T15:00:00Z")
    private val seed by lazy { DemoSeed.from(ApplicationProvider.getApplicationContext<android.content.Context>().resources, now) }
    private val tabs = persistentListOf(
        LensTab("Чаты", BleskIcons.Chats, LivingIcon.Fill),
        LensTab("Контакты", BleskIcons.People, LivingIcon.Gather),
        LensTab("Комнаты", BleskIcons.Rooms, LivingIcon.Stick),
        LensTab("Настройки", BleskIcons.Settings, LivingIcon.Slide),
        LensTab("Ты", BleskIcons.Person) { Avatar("Ева", size = 24.dp) },
    )

    private fun capture(screen: String, textScale: Float = 1f, content: @Composable () -> Unit) {
        captureRoboImage("build/screenshots/${screen}_$name.png") {
            BleskTheme(appearance = Appearance(theme = theme, textScale = textScale), motion = BleskMotion(reduced = true)) {
                content()
            }
        }
    }

    @Composable
    private fun HomeFrame(
        title: String,
        tab: Int,
        collapsed: Boolean = false,
        connection: Connection = Connection.Online,
        content: @Composable (PaddingValues) -> Unit,
    ) {
        LensScaffold(
            top = {
                Column {
                    // «Ты» opens with its header right under the status bar, without a lens.
                    if (tab != YOU) {
                        TopLens(title = title, collapsed = collapsed, actions = {
                            if (tab <= 1) IconButton(BleskIcons.Search, "Поиск", {})
                        })
                    }
                    ConnectionStatus(connection, onRetry = {}, showAfterMillis = 0)
                }
            },
            bottom = {
                val action = when (tab) {
                    0 -> LensAction(BleskIcons.NewChat, "Новый чат", {})
                    1 -> LensAction(BleskIcons.Invite, "Добавить", {})
                    2 -> LensAction(BleskIcons.Plus, "Создать комнату", {})
                    3 -> null
                    else -> LensAction(BleskIcons.Edit, "Изменить профиль", {})
                }
                LensTabBar(tabs = tabs, selected = tab, onSelect = {}, action = action)
            },
            content = content,
        )
    }

    @Test
    fun chatList() = capture("01_chats") {
        HomeFrame("Чаты", 0) { padding ->
            ChatList(
                conversations = seed.conversations.filter { it.parentId == null && it.kind != ConversationKind.Room && !it.request }
                    .mapIndexed { i, c -> if (i < 2) c.copy(pinned = true) else c }
                    .toImmutableList(),
                requests = seed.conversations.filter { it.request }.toImmutableList(),
                typing = persistentSetOf(DemoSeed.MISHA),
                now = now,
                contentPadding = padding,
                onOpenConversation = {},
                onOpenChannel = { _, _ -> },
                online = persistentSetOf(DemoSeed.ANYA, DemoSeed.MISHA),
            )
        }
    }

    @Test
    fun serverSilent() = capture("01_chats_unreachable") {
        HomeFrame("Чаты", 0, collapsed = false, connection = Connection.Unreachable) { padding ->
            ChatList(
                conversations = seed.conversations.filter { it.parentId == null && it.kind != ConversationKind.Room }.toImmutableList(),
                typing = persistentSetOf(),
                now = now,
                contentPadding = padding,
                onOpenConversation = {},
                onOpenChannel = { _, _ -> },
            )
        }
    }

    /** The largest text the app allows: nothing may be cut off. */
    @Test
    fun largeText() = capture("28_large_text", textScale = Appearance.MAX_TEXT_SCALE) {
        HomeFrame("Чаты", 0) { padding ->
            ChatList(
                conversations = seed.conversations.filter { it.parentId == null }.toImmutableList(),
                typing = persistentSetOf(),
                now = now,
                contentPadding = padding,
                onOpenConversation = {},
                onOpenChannel = { _, _ -> },
            )
        }
    }

    @Test
    fun roomOpen() = capture("09_room_open") {
        HomeFrame("Комнаты", 2, collapsed = true) { padding ->
            ChatList(
                conversations = seed.conversations.filter { it.kind == ConversationKind.Room && it.parentId == null }.toImmutableList(),
                typing = persistentSetOf(),
                now = now,
                contentPadding = padding,
                onOpenConversation = {},
                onOpenChannel = { _, _ -> },
                openRooms = persistentSetOf(DemoSeed.ROOM),
            )
        }
    }

    @Test
    fun conversation() = capture("02_conversation") {
        val conversation = seed.conversations.first { it.id == DemoSeed.GROUP }
        ConversationScreen(
            conversation = conversation,
            messages = seed.messages.filter { it.conversationId == conversation.id }.toImmutableList(),
            typing = true,
            authorName = { id -> seed.contacts.firstOrNull { it.id == id }?.name },
            callbacks = ConversationCallbacks({ _, _ -> }, { _, _ -> }, {}, { _, _ -> }, {}, {}, {}, {}, {}),
        )
    }

    /** A run of one author's messages and a cluster of reactions: both must read as one shape. */
    @Test
    fun direct() = capture("02b_conversation_direct") {
        val conversation = seed.conversations.first { it.id == DemoSeed.ANYA }
            .copy(pinnedMessageIds = persistentListOf("${DemoSeed.ANYA}-1", "${DemoSeed.ANYA}-2"))
        val messages = seed.messages.filter { it.conversationId == conversation.id || it.conversationId == DemoSeed.SAVED }.map { message ->
            if (message.id == "${DemoSeed.ANYA}-2") {
                message.copy(
                    reactions = persistentListOf(
                        Reaction(GLINT_REACTION, 2, mine = true),
                        Reaction("\uD83D\uDD25", 1, mine = false),
                        Reaction("\uD83D\uDC4D", 1, mine = false),
                    ),
                )
            } else {
                message
            }
        }
        ConversationScreen(
            conversation = conversation,
            messages = messages.toImmutableList(),
            typing = false,
            authorName = { id -> seed.contacts.firstOrNull { it.id == id }?.name },
            callbacks = ConversationCallbacks({ _, _ -> }, { _, _ -> }, {}, { _, _ -> }, {}, {}, {}, {}, {}),
        )
    }

    @Test
    fun contacts() = capture("03_new_chat") {
        NewChatScreen(
            contacts = seed.contacts.take(5).toImmutableList(),
            actions = NewChatActions({}, {}, {}, {}, {}, onFind = { false }, spaces = null),
            onBack = {},
        )
    }

    @Test
    fun settings() = capture("04_settings") {
        HomeFrame("Настройки", 3) { padding ->
            SettingsList(
                serverHost = null,
                debugUnlocked = true,
                contentPadding = padding,
                actions = SettingsActions({}, {}, {}, {}, {}, {}),
            )
        }
    }

    @Test
    fun newcomer() = capture("01c_newcomer") {
        HomeFrame("Чаты", 0) { padding ->
            NewcomerChats(me = Newcomer("Ева", "eva", null), contentPadding = padding, onFind = {}, onInvite = {})
        }
    }

    @Test
    fun firstFriend() = capture("01d_first_friend") {
        HomeFrame("Чаты", 0) { padding ->
            NewcomerChats(
                me = Newcomer("Ева", "eva", null),
                contentPadding = padding,
                onFind = {},
                onInvite = {},
                friend = Newcomer("Аня Соколова", "anya", null),
            )
        }
    }

    @Test
    fun inAppNotice() = capture("01e_in_app_notice") {
        Box {
            HomeFrame("Чаты", 0) { padding ->
                ChatList(
                    conversations = seed.conversations.toImmutableList(),
                    typing = persistentSetOf(),
                    now = now,
                    contentPadding = padding,
                    onOpenConversation = {},
                    onOpenChannel = { _, _ -> },
                )
            }
            InAppNotice(InAppNote(DemoSeed.ANYA, "Аня Соколова", "Ты где? Мы уже у моста"), onOpen = {}, onDismiss = {})
        }
    }

    @Test
    fun requests() = capture("01f_requests") {
        RequestsScreen(
            requests = seed.conversations.filter { it.request }.toImmutableList(),
            actions = RequestActions({}, {}, {}, {}),
            onBack = {},
        )
    }

    @Test
    fun you() = capture("04b_you") {
        HomeFrame("Ты", YOU) { padding ->
            YouList(
                serverHost = "blesk.example",
                profile = profile,
                photo = null,
                contentPadding = padding,
                actions = SettingsActions({}, {}, {}, {}, {}, {}),
                demo = false,
            )
        }
    }

    @Test
    fun contactsTab() = capture("04c_contacts") {
        HomeFrame("Контакты", 1) { padding ->
            ContactsTab(
                contacts = seed.contacts.toImmutableList(),
                query = "",
                contentPadding = padding,
                actions = ContactActions(onOpen = {}, onWrite = {}, onInvite = {}),
            )
        }
    }

    @Test
    fun appearance() = capture("05_appearance") {
        AppearanceScreen(
            appearance = Appearance(theme = theme),
            onChange = {},
            files = AppearanceFileActions(
                hasWallpaper = true,
                onPickWallpaper = {},
                onRemoveWallpaper = {},
                onSaveTheme = {},
                onOpenTheme = {},
            ),
            onBack = {},
        )
    }

    @Test
    fun voice() = capture("06_voice") {
        val room = seed.conversations.first { it.id == DemoSeed.ROOM }
        VoiceChannelScreen(channel = room.channels.first { it.kind == RoomChannelKind.Voice }, onBack = {})
    }

    @Test
    fun about() = capture("07_about") {
        AboutScreen(versionName = "0.3.0", debugUnlocked = false, onUnlockDebug = {}, onOpenLicenses = {}, onOpenPolicy = {}, onBack = {})
    }

    @Test
    fun storage() = capture("07c_storage") {
        StorageScreen(
            use = StorageUse(
                photos = 412_000_000, videos = 1_250_000_000, voice = 18_400_000, files = 96_000_000,
                database = 24_000_000, cache = 3_100_000,
                chats = persistentListOf(ChatUse("anya", "Аня Соколова", 820_000_000), ChatUse("group", "Выходные", 410_000_000)),
            ),
            keepMediaDays = 90,
            actions = StorageActions({}, {}, {}, {}),
        )
    }

    @Test
    fun server() = capture("07e_server_proxy") {
        ServerScreen(
            connected = ServerState("https://chat.example.ru", "alex", pinned = true),
            onConnect = { _, _ -> ConnectOutcome.Ok },
            onBack = {},
            proxy = ProxyControls(ProxySetting(enabled = true, host = "10.0.0.2", port = 1080), {}, { ProxyCheck.Silent }),
        )
    }

    @Test
    fun update() = capture("07f_update") {
        UpdateScreen(
            title = "blesk 1.0 RC2 (сборка 412)",
            notes = "## 1.0 RC2\n- Прокси SOCKS5 и HTTP.\n- Превью ссылок.",
            progress = UpdateProgress.Downloading(0.42f),
            onInstall = {},
            onBack = {},
        )
    }

    @Test
    fun sandbox() = capture("07d_sandbox") {
        MetaballSandbox(onBack = {})
    }

    @Test
    fun policy() = capture("07b_policy") {
        PrivacyPolicyScreen(onBack = {})
    }

    @Test
    fun debug() = capture("08_debug") {
        DebugScreen(
            entries = persistentListOf(
                LogEntry(now.toEpochMilli(), EventCode.AppStarted, 1),
                LogEntry(now.toEpochMilli() + 1200, EventCode.ScreenOpened, 2),
                LogEntry(now.toEpochMilli() + 5400, EventCode.ThemeChanged, 3),
            ),
            crash = CrashReport(now.toEpochMilli(), "java.lang.IllegalStateException\n    at app.blesk.Example.run(Example.kt:12)"),
            appInfo = "blesk 0.2.0",
            onClearCrash = {},
            onClearJournal = {},
            onBack = {},
        )
    }

    private val profile = Profile(name = "Ева", username = "eva", bio = "Рисую интерфейсы", photoVersion = 0)

    @Test
    fun profile() = capture("10_profile") {
        ProfileScreen(profile = profile, photo = null, headerPhoto = null, actions = ProfileActions({}, {}, {}, {}, {}))
    }

    @Test
    fun privacy() = capture("11_privacy") {
        PrivacyScreen(
            privacy = Privacy(secureScreen = true),
            pinSet = true,
            biometricAvailable = true,
            actions = PrivacyActions({}, {}, {}, {}, {}, {}),
        )
    }

    @Test
    fun lock() = capture("12_lock") {
        PinEntry(title = "Введи код", length = 4, check = { PinAttempt.Ok }, onBiometric = {})
    }

    @Test
    fun wipe() = capture("13_wipe") {
        WipeScreen(onWipe = {}, onBack = {})
    }

    @Test
    fun licenses() = capture("14_licenses") {
        LicensesScreen(onOpenLicense = {}, onBack = {})
    }

    @Test
    fun longMessage() = capture("02c_long_message") {
        val base = seed.messages.first { it.conversationId == DemoSeed.ANYA }
        val essay = (1..40).joinToString("\n") { "Строка $it длинного письма о том, как мы ехали за город" }
        ConversationScreen(
            conversation = seed.conversations.first { it.id == DemoSeed.ANYA },
            messages = persistentListOf(
                base.copy(id = "link", authorId = "me", text = "@orlov, глянь маршрут: https://example.org/route, там всё"),
                base.copy(id = "long", authorId = DemoSeed.ANYA, text = essay),
            ),
            typing = false,
            authorName = { id -> seed.contacts.firstOrNull { it.id == id }?.name },
            callbacks = ConversationCallbacks({ _, _ -> }, { _, _ -> }, {}, { _, _ -> }, {}, {}, {}, {}, {}),
            focusMessageId = "link",
        )
    }

    @Test
    fun album() = capture("02e_album") {
        val base = seed.messages.first { it.conversationId == DemoSeed.ANYA }
        val sizes = listOf(4000 to 3000, 3000 to 4000, 1080 to 1920, 4000 to 3000, 1920 to 1080)
        val items = sizes.mapIndexed { index, (w, h) ->
            if (index == 2) Attachment.Video("", "", w, h, 12_000) else Attachment.Photo("", w, h)
        }
        ConversationScreen(
            conversation = seed.conversations.first { it.id == DemoSeed.ANYA },
            messages = persistentListOf(
                base.copy(id = "album", authorId = "me", text = "Вчерашний закат", attachment = Attachment.Album(items)),
            ),
            typing = false,
            authorName = { id -> seed.contacts.firstOrNull { it.id == id }?.name },
            callbacks = ConversationCallbacks({ _, _ -> }, { _, _ -> }, {}, { _, _ -> }, {}, {}, {}, {}, {}),
            transfers = mapOf("album/0" to Transfer(13_000_000, 48_000_000), "album/4" to Transfer(0, -1, failed = true)),
        )
    }

    @Test
    fun music() = capture("02f_music") {
        val base = seed.messages.first { it.conversationId == DemoSeed.ANYA }
        val song = Attachment.Music("song.mp3", "song.mp3", "Звезда по имени Солнце", "Кино", 225_000, 8_600_000, "audio/mpeg")
        Column {
            ConversationScreen(
                conversation = seed.conversations.first { it.id == DemoSeed.ANYA },
                messages = persistentListOf(base.copy(id = "song", authorId = DemoSeed.ANYA, text = "", attachment = song)),
                typing = false,
                authorName = { id -> seed.contacts.firstOrNull { it.id == id }?.name },
                callbacks = ConversationCallbacks({ _, _ -> }, { _, _ -> }, {}, { _, _ -> }, {}, {}, {}, {}, {}),
                modifier = Modifier.weight(1f),
            )
            MiniPlayer(
                playback = Playback(
                    path = "song.mp3",
                    playing = true,
                    title = song.title,
                    artist = song.artist,
                    positionMillis = 61_000,
                    durationMillis = 225_000,
                ),
                onToggle = {},
                onStop = {},
            )
        }
    }

    /** The bar at rest with motion on: the action capsule's bridge only shows when bridges are allowed. */
    @Test
    fun navigationBridge() {
        captureRoboImage("build/screenshots/34_navbar_bridge_$name.png") {
            BleskTheme(appearance = Appearance(theme = theme), motion = BleskMotion(reduced = false)) {
                LensScaffold(
                    bottom = {
                        LensTabBar(tabs = tabs, selected = 2, onSelect = {}, action = LensAction(BleskIcons.NewChat, "Новый чат") {})
                    },
                ) { }
            }
        }
    }

    @Test
    fun motionShowcase() = capture("33_motion_showcase") {
        MotionShowcase(
            animations = persistentListOf(
                MotionDemo("1. Отправка — «отрыв»", "Капсула отрывается от поля") { replay -> ChatMotionDemo(ChatMotion.Send, replay) },
                MotionDemo("13. Сверка ключей", "Формы сливаются и вспыхивают") { replay -> KeyMerge(matched = true, replay = replay) },
            ),
            haptics = persistentListOf(HapticDemo("Тик", "Вкладка, порог жеста") {}, HapticDemo("Успех", "Ключи сверены") {}),
            onBack = {},
        )
    }

    @Test
    fun phrase() = capture("32_phrase") {
        PhraseScreen(
            words = RecoveryPhrase.wordsOf(ByteArray(16) { (it * 37).toByte() }),
            actions = PhraseActions(onCopy = {}, onPasswordManager = {}, onChecked = {}, onLater = {}, onBack = null),
        )
    }

    @Test
    fun offline() = capture("31_offline") {
        OfflineScreen(
            items = persistentListOf(
                OfflineItem("anya", "m1", "Видео", "Аня Соколова", 48_000_000),
                OfflineItem("anya", "m2", "макеты.pdf", "Аня Соколова", 2_400_000),
                OfflineItem("work", "m3", "Звезда по имени Солнце", "Работа", 8_600_000, progress = 0.4f),
            ),
            onOpen = {},
            onRemove = {},
            onBack = {},
        )
    }

    @Test
    fun backdrop() = capture("02d_chat_backdrop") {
        val conversation = seed.conversations.first { it.id == DemoSeed.ANYA }
        ConversationScreen(
            conversation = conversation,
            messages = seed.messages.filter { it.conversationId == conversation.id }.toImmutableList(),
            typing = false,
            authorName = { id -> seed.contacts.firstOrNull { it.id == id }?.name },
            callbacks = ConversationCallbacks({ _, _ -> }, { _, _ -> }, {}, { _, _ -> }, {}, {}, {}, {}, {}),
            underlay = { ChatBackdrop(HeaderStyle.Light(2), null) },
            hintsSeen = emptySet(),
        )
    }

    @Test
    fun media() = capture("15_media") {
        val dir = ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir
        val picture = java.io.File(dir, "shot.jpg").also { file ->
            val bitmap = android.graphics.Bitmap.createBitmap(400, 300, android.graphics.Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(bitmap).drawColor(android.graphics.Color.rgb(120, 130, 140))
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, it) }
        }
        val base = seed.messages.first { it.conversationId == DemoSeed.ANYA }
        val wave = app.blesk.core.media.Waveform.encode(List(48) { i -> ((kotlin.math.sin(i / 3.0) + 1) / 2).toFloat() })
        val messages = listOf(
            base.copy(id = "m1", authorId = DemoSeed.ANYA, text = "", attachment = Attachment.Voice("v", 14_000, wave)),
            base.copy(
                id = "m2",
                authorId = DemoSeed.ANYA,
                text = "Вот макеты",
                attachment = Attachment.File("f", "макеты.pdf", 2_400_000, "application/pdf"),
            ),
            base.copy(id = "m3", authorId = "me", text = "Смотри, какой свет", attachment = Attachment.Photo(picture.path, 400, 300)),
            base.copy(id = "m4", authorId = "me", text = "", attachment = Attachment.Circle("c", picture.path, 9_000)),
        )
        ConversationScreen(
            conversation = seed.conversations.first { it.id == DemoSeed.ANYA },
            messages = messages.toImmutableList(),
            typing = false,
            authorName = { id -> seed.contacts.firstOrNull { it.id == id }?.name },
            callbacks = ConversationCallbacks({ _, _ -> }, { _, _ -> }, {}, { _, _ -> }, {}, {}, {}, {}, {}),
        )
    }

    @Test
    fun newSpace() = capture("16_new") {
        NewGroupScreen(contacts = seed.contacts.toImmutableList(), onCreate = { SpaceResult.Ok }, onBack = {})
    }

    @Test
    fun groupInfo() = capture("17_group") {
        val state = GroupState(
            id = "g",
            kind = GroupKind.Room,
            title = "Студия",
            version = 1,
            epoch = 0,
            members = listOf(
                GroupMember("eva", GroupRole.Owner),
                GroupMember("lera", GroupRole.Admin),
                GroupMember("anya", GroupRole.Member),
            ),
            channels = listOf(
                GroupChannel("general", "общий", GroupChannelKind.Text),
                GroupChannel("news", "новости", GroupChannelKind.Text, adminsOnly = true),
                GroupChannel("voice", "созвон", GroupChannelKind.Voice),
            ),
        )
        GroupInfoScreen(
            state = state,
            me = "eva",
            contacts = persistentListOf(),
            nameOf = { mapOf("eva" to "Ева", "lera" to "Лера", "anya" to "Аня")[it] ?: it },
            actions = GroupActions({}, {}, {}, { _, _ -> }, { _, _, _ -> }, {}, {}, {}),
            onBack = {},
        )
    }

    @Test
    fun spaceInfo() = capture("18_space") {
        SpaceInfoScreen(
            space = SpaceInfo(
                handle = "light_notes",
                kind = SpaceKind.Room,
                title = "Заметки о свете",
                about = "Открытая комната про свет, цвет и интерфейсы",
                members = 128,
                channels = listOf(SpaceChannel("general", "общий"), SpaceChannel("refs", "референсы")),
                role = SpaceRole.Owner,
            ),
            me = "eva",
            members = persistentListOf(SpaceMember("eva", SpaceRole.Owner), SpaceMember("lera", SpaceRole.Member)),
            nameOf = { mapOf("eva" to "Ева", "lera" to "Лера")[it] ?: it },
            actions = SpaceActions({ _, _ -> }, { _, _ -> }, {}, {}, {}, {}, {}),
            onBack = {},
        )
    }

    @Test
    fun search() = capture("19_search") {
        val query = rememberTextFieldState("свет")
        LensScaffold(
            top = { SearchLens(query, "Чат, человек или слово", "Закрыть поиск", open = true, onClose = {}, onClosed = {}) },
            bottom = { LensTabBar(tabs = tabs, selected = 0, onSelect = {}) },
        ) { padding ->
            SearchResults(
                query = query.text.toString(),
                hits = persistentListOf(
                    SearchHit("a", "1", "Аня", "Аня", "Смотри, какой свет сегодня на закате"),
                    SearchHit("b", "2", "Студия · общий", "Ты", "Свет в макете стоит сделать мягче, а тени длиннее"),
                ),
                onOpen = {},
                contentPadding = padding,
            )
        }
    }

    private val callActions = CallActions({}, {}, {}, {}, {}, {}, {}, {})

    @Test
    fun incomingCall() = capture("20_call_incoming") {
        CallScreen(
            session = CallSession("c", "anya", "Аня Соколова", incoming = true, video = true, phase = CallPhase.Ringing),
            media = MediaState(),
            room = null,
            nameOf = { it },
            actions = callActions,
        )
    }

    @Test
    fun voiceChannel() = capture("21_call_voice") {
        val members = listOf(
            CallMember("eva", local = true, speaking = false, micOn = true, camera = null, screen = null),
            CallMember("lera", local = false, speaking = true, micOn = true, camera = null, screen = null),
            CallMember("anya", local = false, speaking = false, micOn = false, camera = null, screen = null),
        )
        CallScreen(
            session = CallSession(
                "v", "g:voice", "Студия · созвон", incoming = false, video = false, phase = CallPhase.Active,
                voiceChannel = true, startedAtMillis = 0,
            ),
            media = MediaState(connected = true, members = members, othersJoined = true),
            room = null,
            nameOf = { mapOf("lera" to "Лера", "anya" to "Аня")[it] ?: it },
            actions = CallActions({}, {}, {}, {}, {}, {}, {}, toggleShare = {}),
            clock = { 65_000L },
        )
    }

    @Test
    fun botChat() = capture("22_bot") {
        val bot = seed.conversations.first { it.id == DemoSeed.BOT }
        val base = seed.messages.first { it.conversationId == DemoSeed.BOT }
        val messages = listOf(
            base.copy(id = "b1", authorId = "me", text = "/start"),
            base.copy(
                id = "b2",
                authorId = DemoSeed.BOT,
                text = "Привет! Я эхо. Повторяю всё, что напишешь. А ещё у меня есть кнопки — нажми любую.",
                buttons = persistentListOf(
                    persistentListOf(BotButton("Повтори", data = "a"), BotButton("Что ты умеешь?", data = "b")),
                    persistentListOf(BotButton("Сайт", url = "https://example.org")),
                ),
            ),
        )
        ConversationScreen(
            conversation = bot,
            messages = messages.toImmutableList(),
            typing = false,
            authorName = { "Эхо" },
            callbacks = ConversationCallbacks({ _, _ -> }, { _, _ -> }, {}, { _, _ -> }, {}, {}, {}, {}, {}),
            commands = persistentListOf(BotCommand("start", "начать сначала")),
        )
    }

    @Test
    fun bots() = capture("23_bots") {
        BotsScreen(
            bots = persistentListOf(BotInfo("weather_bot", "Погода", "Прогноз по запросу")),
            actions = BotActions({ _, _, _ -> BotResult.Ok to null }, { null }, {}),
            onBack = {},
        )
    }

    @Test
    fun safety() = capture("24_safety") {
        SafetyScreen(
            name = "Аня",
            code = SafetyCode("051234120938476501928374650192837465019283746501928374651234", "code"),
            state = KeyState(verified = false, changed = true),
            onScanned = { true },
            onSetVerified = {},
            onBack = {},
            qr = { content -> QrCode(content, Color.Black, Modifier.fillMaxSize()) },
        )
    }

    @Test
    fun notifications() = capture("25_notifications") {
        NotificationsScreen(settings = NotificationSettings(), serverConnected = true, onChange = {}, onBack = {})
    }

    @Test
    fun chatInfo() = capture("26_chat_info") {
        val anya = seed.conversations.first { it.id == DemoSeed.ANYA }.copy(timerSeconds = 86_400, pinned = true)
        PersonProfileScreen(
            conversation = anya,
            username = "anya",
            verified = true,
            muted = false,
            messages = seed.messages.filter { it.conversationId == DemoSeed.ANYA }.toImmutableList(),
            actions = PersonActions(
                onWrite = {}, onCall = {}, onMute = {}, onPin = {}, onTimer = {}, onVerify = {}, onTheme = {},
                onDeleteChat = {}, onOpenMedia = {}, onOpenFile = {}, onOpenLink = {}, onPlayVoice = {},
            ),
            onBack = {},
        )
    }

    @Test
    fun forward() = capture("27_forward") {
        ForwardScreen(conversations = seed.conversations.take(5).toImmutableList(), onSend = {}, onBack = {})
    }

    companion object {
        private const val YOU = 4

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun themes(): List<Array<Any>> = ThemeSpec.presets.map { arrayOf(it.preset.name.lowercase()) }

        @JvmStatic
        @BeforeClass
        fun fixTimeZone() {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Moscow"))
        }
    }
}
