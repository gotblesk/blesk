import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_animate/flutter_animate.dart';

import 'shared/widgets.dart';
import 'features/profile_view.dart';
import 'features/settings_screen.dart';
import 'features/call_ui.dart';
import 'features/input_bar.dart';
import 'features/chat_messages.dart';
import 'features/media_viewer.dart';
import 'features/global_search.dart';
import 'features/chat_search_bar.dart';
import 'features/channel_feed.dart';
import 'features/create_flows.dart';
import 'features/members_panel.dart';

// ═══════════════════════════════════════════════════════════════
// MAIN SHELL — Living Sidebar + Content Area
// ═══════════════════════════════════════════════════════════════

enum Section { chats, contacts, channels }

class MainScreen extends StatefulWidget {
  const MainScreen({super.key});
  @override
  State<MainScreen> createState() => _MainScreenState();
}

class _MainScreenState extends State<MainScreen> {
  Section _section = Section.chats;
  List<String> _panels = []; // open chat panels (max 3)
  String? _peekChat;
  double _peekY = 0;
  int _focusedPanel = 0;
  bool _focusMode = false;
  String? _hint;
  String? _profileView;
  bool _showSettings = false;
  String? _callWith; // chat id of active call
  bool _callVideo = false;
  bool _callMinimized = false;
  String? _incomingCall; // chat id of incoming call
  bool get _showProfile => _profileView != null;
  bool get _inCall => _callWith != null;
  final _searchCtrl = TextEditingController();
  final _searchFocus = FocusNode();

  String? get _activeChat => _panels.isNotEmpty ? _panels[_focusedPanel.clamp(0, _panels.length - 1)] : null;

  void _openChat(String id) {
    setState(() {
      _peekChat = null;
      if (_panels.contains(id)) {
        _focusedPanel = _panels.indexOf(id);
      } else {
        if (_panels.isEmpty) { _panels = [id]; _focusedPanel = 0; }
        else { _panels = [id]; _focusedPanel = 0; } // single mode by default
      }
    });
  }

  bool _hintSplitShown = false;
  bool _hintFocusShown = false;

  void _showHint(String text) {
    setState(() => _hint = text);
    Future.delayed(const Duration(seconds: 3), () {
      if (mounted) setState(() => _hint = null);
    });
  }

  void _addSplit(String id) {
    setState(() {
      _peekChat = null;
      if (_panels.contains(id)) { _focusedPanel = _panels.indexOf(id); return; }
      if (_panels.length >= 3) {
        _showHint('максимум 3 чата, закрой один');
        return;
      }
      _panels.add(id);
      _focusedPanel = _panels.length - 1;
      if (!_hintSplitShown && _panels.length == 2) {
        _hintSplitShown = true;
        _showHint('совет: Tab переключает между панелями');
      }
    });
  }

  void _closePanel(int index) {
    setState(() {
      _panels.removeAt(index);
      if (_focusedPanel >= _panels.length) _focusedPanel = (_panels.length - 1).clamp(0, 2);
    });
  }

  @override
  void dispose() {
    _searchCtrl.dispose();
    _searchFocus.dispose();
    super.dispose();
  }

  void _showCreateMenu(BuildContext ctx) {
    final overlay = Overlay.of(ctx);
    late OverlayEntry entry;
    entry = OverlayEntry(builder: (_) => _CreateMenuPopup(
      onClose: () => entry.remove(),
      onGroup: () {
        entry.remove();
        showCreateGroup(ctx, onCreated: (g) {
          setState(() => _section = Section.chats);
        });
      },
      onChannel: () {
        entry.remove();
        showCreateChannel(ctx, onCreated: (c) {
          setState(() => _section = Section.channels);
        });
      },
      onContact: () {
        entry.remove();
        showAddContact(ctx);
      },
    ));
    overlay.insert(entry);
  }

  void _showGlobalSearch() {
    showGlobalSearch(context, onPick: (result) {
      switch (result.kind) {
        case SearchResultKind.chat:
        case SearchResultKind.message:
          setState(() {
            _section = Section.chats;
            _openChat(result.kind == SearchResultKind.chat
                ? result.id
                : (result.inChat?.split(' · ').first ?? result.id));
          });
        case SearchResultKind.contact:
          setState(() { _section = Section.contacts; _profileView = result.id; });
        case SearchResultKind.channel:
          setState(() => _section = Section.channels);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Shortcuts(
      shortcuts: {
        LogicalKeySet(LogicalKeyboardKey.control, LogicalKeyboardKey.digit1): const _SectionIntent(Section.chats),
        LogicalKeySet(LogicalKeyboardKey.control, LogicalKeyboardKey.digit2): const _SectionIntent(Section.contacts),
        LogicalKeySet(LogicalKeyboardKey.control, LogicalKeyboardKey.digit3): const _SectionIntent(Section.channels),
        LogicalKeySet(LogicalKeyboardKey.control, LogicalKeyboardKey.keyK): const _SearchIntent(),
        LogicalKeySet(LogicalKeyboardKey.control, LogicalKeyboardKey.keyN): const _NewChatIntent(),
        LogicalKeySet(LogicalKeyboardKey.escape): const _EscIntent(),
      },
      child: Actions(
        actions: {
          _SectionIntent: CallbackAction<_SectionIntent>(
            onInvoke: (i) { setState(() => _section = i.section); return null; },
          ),
          _SearchIntent: CallbackAction<_SearchIntent>(
            onInvoke: (_) { _showGlobalSearch(); return null; },
          ),
          _NewChatIntent: CallbackAction<_NewChatIntent>(onInvoke: (_) => null),
          _EscIntent: CallbackAction<_EscIntent>(
            onInvoke: (_) { _searchFocus.unfocus(); return null; },
          ),
        },
        child: Focus(
          autofocus: true,
          child: Scaffold(
            backgroundColor: BColors.bg,
            body: Column(children: [
              Stack(children: [
                const BLeskTitleBar(),
                if (_inCall && _callMinimized)
                  Positioned(top: 5, left: 0, right: 0,
                    child: Center(child: CallPill(
                      name: _chats.where((c) => c.id == _callWith).firstOrNull?.name ?? '',
                      seconds: 0,
                      onTap: () => setState(() => _callMinimized = false),
                    )),
                  ),
              ]),
              Container(height: 1, color: BColors.borderLow),
              Expanded(
                child: Stack(children: [
                  Row(children: [
                    // Living Sidebar
                    SizedBox(
                      width: 280,
                      child: _LivingSidebar(
                        section: _section,
                        activeChat: _activeChat,
                        searchCtrl: _searchCtrl,
                        searchFocus: _searchFocus,
                        onSection: (s) => setState(() => _section = s),
                        onChat: _openChat,
                        onPeek: (id, y) => setState(() {
                          if (_peekChat == id) { _peekChat = null; return; }
                          if (_panels.contains(id)) return;
                          _peekChat = id; _peekY = y;
                        }),
                        onSplit: _addSplit,
                        onProfile: () => setState(() => _profileView = 'self'),
                        onSettings: () => setState(() => _showSettings = true),
                        onCreate: () => _showCreateMenu(context),
                      ),
                    ),
                    Container(width: 1, color: BColors.borderLow),
                    // Content Area
                    Expanded(
                      child: (_inCall && !_callMinimized)
                          ? CallView(
                              name: _chats.where((c) => c.id == _callWith).firstOrNull?.name ?? 'User',
                              initial: _chats.where((c) => c.id == _callWith).firstOrNull?.initial ?? '?',
                              video: _callVideo,
                              onEnd: () => setState(() { _callWith = null; _callMinimized = false; }),
                              onMinimize: () => setState(() => _callMinimized = true),
                            )
                          : _showSettings
                          ? SettingsScreen(onBack: () => setState(() => _showSettings = false))
                          : _panels.isEmpty
                              ? const _EmptyState()
                              : _SplitContent(
                              panels: _panels,
                              focusedPanel: _focusedPanel,
                              focusMode: _focusMode,
                              onFocus: (i) => setState(() => _focusedPanel = i),
                              onClose: _closePanel,
                              onProfile: (id) => setState(() => _profileView = id),
                              onCall: (id, video) => setState(() {
                                _callWith = id; _callVideo = video; _callMinimized = false;
                              }),
                              onToggleFocus: () {
                                setState(() => _focusMode = !_focusMode);
                                if (_focusMode && !_hintFocusShown) {
                                  _hintFocusShown = true;
                                  _showHint('кликни на стопку чтобы переключить чат');
                                }
                              },
                            ),
                    ),
                  ]),
                  // Hint toast
                  if (_hint != null)
                    Positioned(
                      bottom: 24, left: 280, right: 0,
                      child: Center(
                        child: Container(
                          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                          decoration: BoxDecoration(
                            color: const Color(0xE6141418),
                            borderRadius: BorderRadius.circular(8),
                            border: Border.all(color: BColors.accent.withValues(alpha: 0.15)),
                          ),
                          child: Text(_hint!, style: const TextStyle(
                            fontFamily: 'Onest', fontSize: 12, color: BColors.textSecondary,
                          )),
                        ).animate().fadeIn(duration: 200.ms).slideY(begin: 0.3),
                      ),
                    ),
                  // Incoming call overlay
                  if (_incomingCall != null)
                    Positioned.fill(child: IncomingCallOverlay(
                      name: _chats.where((c) => c.id == _incomingCall).firstOrNull?.name ?? 'User',
                      initial: _chats.where((c) => c.id == _incomingCall).firstOrNull?.initial ?? '?',
                      onAccept: () => setState(() {
                        _callWith = _incomingCall; _callVideo = false;
                        _callMinimized = false; _incomingCall = null;
                      }),
                      onDecline: () => setState(() => _incomingCall = null),
                    )),
                  // Call pill removed from here — moved to titlebar area
                  // Profile slide-over
                  if (_showProfile)
                    _profileView == 'self'
                        ? ProfileEditor(onClose: () => setState(() => _profileView = null))
                        : ProfileView(
                            name: _chats.where((c) => c.id == _profileView).firstOrNull?.name ?? 'User',
                            username: 'user_${_profileView}',
                            bio: 'дизайнер. котики. кофе.',
                            online: true,
                            initial: _chats.where((c) => c.id == _profileView).firstOrNull?.initial ?? '?',
                            onClose: () => setState(() => _profileView = null),
                          ),
                  // Peek overlay
                  if (_peekChat != null)
                    _PeekOverlay(
                      chatId: _peekChat!,
                      topOffset: _peekY,
                      onClose: () => setState(() => _peekChat = null),
                      onOpen: () { _openChat(_peekChat!); },
                      onSplit: () { _addSplit(_peekChat!); },
                    ),
                ]),
              ),
            ]),
          ),
        ),
      ),
    );
  }
}

// ─── Intents ──────────────────────────────────────────────────

class _SectionIntent extends Intent {
  final Section section;
  const _SectionIntent(this.section);
}

class _SearchIntent extends Intent { const _SearchIntent(); }
class _NewChatIntent extends Intent { const _NewChatIntent(); }
class _EscIntent extends Intent { const _EscIntent(); }

// ═══════════════════════════════════════════════════════════════
// LIVING SIDEBAR
// ═══════════════════════════════════════════════════════════════

class _LivingSidebar extends StatelessWidget {
  final Section section;
  final String? activeChat;
  final TextEditingController searchCtrl;
  final FocusNode searchFocus;
  final ValueChanged<Section> onSection;
  final ValueChanged<String> onChat;
  final void Function(String id, double y) onPeek;
  final ValueChanged<String> onSplit;
  final VoidCallback onProfile;
  final VoidCallback onSettings;
  final VoidCallback? onCreate;

  const _LivingSidebar({
    required this.section, required this.activeChat,
    required this.searchCtrl, required this.searchFocus,
    required this.onSection, required this.onChat, required this.onPeek,
    required this.onSplit, required this.onProfile, required this.onSettings,
    this.onCreate,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      color: const Color(0xFF0d0d10),
      child: Column(children: [
        // Header
        _SidebarHeader(onAvatarTap: onProfile, onSettings: onSettings, onCreate: onCreate),
        // Section capsule
        _SectionCapsule(section: section, onSection: onSection),
        // List
        Expanded(
          child: AnimatedSwitcher(
            duration: const Duration(milliseconds: 200),
            transitionBuilder: (child, anim) => FadeTransition(
              opacity: anim,
              child: SlideTransition(
                position: Tween<Offset>(begin: const Offset(0, 0.02), end: Offset.zero)
                    .animate(CurvedAnimation(parent: anim, curve: Curves.easeOutCubic)),
                child: child,
              ),
            ),
            child: KeyedSubtree(
              key: ValueKey(section),
              child: _buildList(context),
            ),
          ),
        ),
        // Search (bottom)
        Container(height: 1, color: BColors.borderLow),
        _BottomSearch(controller: searchCtrl, focusNode: searchFocus),
      ]),
    );
  }

  Widget _buildList(BuildContext context) {
    switch (section) {
      case Section.chats:
        return _ChatList(activeChat: activeChat, onChat: onChat, onPeek: onPeek, onSplit: onSplit);
      case Section.contacts:
        return _ContactList(onChat: onChat);
      case Section.channels:
        return _ChannelList(onChat: onChat);
    }
  }
}

// ─── Sidebar Header ───────────────────────────────────────────

class _SidebarHeader extends StatelessWidget {
  final VoidCallback onAvatarTap;
  final VoidCallback onSettings;
  final VoidCallback? onCreate;
  const _SidebarHeader({
    required this.onAvatarTap, required this.onSettings, this.onCreate,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 48,
      padding: const EdgeInsets.symmetric(horizontal: 14),
      child: Row(children: [
        // Avatar
        MouseRegion(
          cursor: SystemMouseCursors.click,
          child: GestureDetector(
            onTap: onAvatarTap,
            child: Container(
              width: 26, height: 26,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(8),
                color: BColors.accent.withValues(alpha: 0.12),
              ),
              child: const Center(child: Text('G', style: TextStyle(
                fontSize: 12, fontWeight: FontWeight.w700, color: BColors.accent,
              ))),
            ),
          ),
        ),
        const SizedBox(width: 8),
        Text('gotblesk', style: TextStyle(
          fontFamily: 'Onest', fontSize: rf(context, 13),
          fontWeight: FontWeight.w600, color: BColors.textPrimary,
        )),
        const Spacer(),
        _HeaderBtn(icon: Icons.add, tooltip: 'создать новое', onTap: onCreate),
        const SizedBox(width: 2),
        _HeaderBtn(icon: Icons.settings_outlined, tooltip: 'настройки', onTap: onSettings),
      ]),
    );
  }
}

class _HeaderBtn extends StatefulWidget {
  final IconData icon;
  final String tooltip;
  final VoidCallback? onTap;
  const _HeaderBtn({required this.icon, required this.tooltip, this.onTap});
  @override
  State<_HeaderBtn> createState() => _HeaderBtnState();
}

class _HeaderBtnState extends State<_HeaderBtn> {
  bool _h = false;
  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: widget.tooltip,
      waitDuration: const Duration(milliseconds: 400),
      textStyle: const TextStyle(fontSize: 11, color: BColors.textPrimary),
      decoration: BoxDecoration(
        color: const Color(0xFF1a1a1a), borderRadius: BorderRadius.circular(6),
        border: Border.all(color: BColors.borderLow),
      ),
      child: MouseRegion(
        cursor: SystemMouseCursors.click,
        onEnter: (_) => setState(() => _h = true),
        onExit: (_) => setState(() => _h = false),
        child: GestureDetector(
          onTap: widget.onTap ?? () {},
          child: SizedBox(
            width: 28, height: 28,
            child: Center(child: Icon(widget.icon, size: 18,
              color: _h ? BColors.accent.withValues(alpha: 0.6) : BColors.textMuted)),
          ),
        ),
      ),
    );
  }
}

// ─── Section Capsule ──────────────────────────────────────────

class _SectionCapsule extends StatelessWidget {
  final Section section;
  final ValueChanged<Section> onSection;
  const _SectionCapsule({required this.section, required this.onSection});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.fromLTRB(12, 0, 12, 8),
      padding: const EdgeInsets.all(3),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.03),
        border: Border.all(color: Colors.white.withValues(alpha: 0.06)),
        borderRadius: BorderRadius.circular(10),
      ),
      child: Row(children: [
        _CapsuleTab(label: 'чаты', badge: 4, active: section == Section.chats,
            onTap: () => onSection(Section.chats)),
        _CapsuleTab(label: 'контакты', active: section == Section.contacts,
            onTap: () => onSection(Section.contacts)),
        _CapsuleTab(label: 'каналы', active: section == Section.channels,
            onTap: () => onSection(Section.channels)),
      ]),
    );
  }
}

class _CapsuleTab extends StatefulWidget {
  final String label;
  final int? badge;
  final bool active;
  final VoidCallback onTap;
  const _CapsuleTab({required this.label, this.badge, required this.active, required this.onTap});
  @override
  State<_CapsuleTab> createState() => _CapsuleTabState();
}

class _CapsuleTabState extends State<_CapsuleTab> {
  bool _h = false;
  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: MouseRegion(
        cursor: SystemMouseCursors.click,
        onEnter: (_) => setState(() => _h = true),
        onExit: (_) => setState(() => _h = false),
        child: GestureDetector(
          onTap: widget.onTap,
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 200),
            curve: Curves.easeOutCubic,
            height: 30,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(8),
              color: widget.active ? BColors.accent.withValues(alpha: 0.08) : Colors.transparent,
            ),
            child: Center(
              child: Column(mainAxisSize: MainAxisSize.min, children: [
                Row(mainAxisSize: MainAxisSize.min, children: [
                  Text(widget.label, style: TextStyle(
                    fontFamily: 'Onest', fontSize: 13,
                    fontWeight: widget.active ? FontWeight.w600 : FontWeight.w400,
                    color: widget.active ? BColors.accent
                        : _h ? Colors.white.withValues(alpha: 0.5)
                        : Colors.white.withValues(alpha: 0.25),
                  )),
                  if (widget.badge != null && widget.badge! > 0) ...[
                    const SizedBox(width: 5),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1),
                      decoration: BoxDecoration(
                        color: BColors.accent,
                        borderRadius: BorderRadius.circular(6),
                      ),
                      child: Text('${widget.badge}', style: const TextStyle(
                        fontSize: 9, fontWeight: FontWeight.w700, color: BColors.bg,
                      )),
                    ),
                  ],
                ]),
                const SizedBox(height: 3),
                AnimatedContainer(
                  duration: const Duration(milliseconds: 200),
                  curve: Curves.easeOutCubic,
                  width: widget.active ? 20 : 0,
                  height: 2,
                  decoration: BoxDecoration(
                    color: BColors.accent,
                    borderRadius: BorderRadius.circular(1),
                  ),
                ),
              ]),
            ),
          ),
        ),
      ),
    );
  }
}

// ─── Bottom Search ────────────────────────────────────────────

class _BottomSearch extends StatefulWidget {
  final TextEditingController controller;
  final FocusNode focusNode;
  const _BottomSearch({required this.controller, required this.focusNode});
  @override
  State<_BottomSearch> createState() => _BottomSearchState();
}

class _BottomSearchState extends State<_BottomSearch> {
  bool _focused = false;
  @override
  Widget build(BuildContext context) {
    return Focus(
      onFocusChange: (f) => setState(() => _focused = f),
      child: Container(
        height: 44,
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        decoration: BoxDecoration(
          border: Border(top: BorderSide(
            color: _focused ? BColors.accent.withValues(alpha: 0.15) : BColors.borderLow,
          )),
        ),
        child: TextField(
          controller: widget.controller,
          focusNode: widget.focusNode,
          style: TextStyle(fontFamily: 'Onest', fontSize: rf(context, 13), color: BColors.textPrimary),
          cursorColor: BColors.accent,
          decoration: InputDecoration(
            border: InputBorder.none, isDense: true,
            hintText: 'поиск',
            hintStyle: TextStyle(fontFamily: 'Onest', fontSize: rf(context, 13), color: BColors.textMuted),
            contentPadding: const EdgeInsets.symmetric(vertical: 8),
            prefixIcon: Icon(Icons.search, size: 16, color: BColors.textMuted),
            prefixIconConstraints: const BoxConstraints(minWidth: 28),
            suffixIcon: Padding(
              padding: const EdgeInsets.only(top: 6),
              child: Text('⌘K', style: TextStyle(
                fontFamily: 'Onest', fontSize: 10, color: BColors.textMuted,
              )),
            ),
            suffixIconConstraints: const BoxConstraints(minWidth: 28),
          ),
        ),
      ),
    );
  }
}

// ═══════════════════════════════════════════════════════════════
// CHAT LIST (Living)
// ═══════════════════════════════════════════════════════════════

enum ChatActivity { none, online, typing, newMessage, inVoice }

class _ChatStub {
  final String id, name, lastMsg, time, initial;
  final int unread;
  final ChatActivity activity;
  final bool pinned;
  const _ChatStub(this.id, this.name, this.lastMsg, this.time, this.initial, this.unread, this.activity, {this.pinned = false});
}

const _chats = [
  _ChatStub('c1', 'Катя', 'привет! как дела?', '14:32', 'К', 2, ChatActivity.typing),
  _ChatStub('c2', 'Дизайн-банда', 'скинул макеты в фигму', '13:10', 'Д', 0, ChatActivity.online),
  _ChatStub('c3', 'Максим', 'завтра созвон в 10?', '12:45', 'М', 1, ChatActivity.newMessage),
  _ChatStub('c4', 'blesk team', 'новый билд готов', 'вчера', 'B', 5, ChatActivity.inVoice, pinned: true),
  _ChatStub('c5', 'Аня', 'ок', 'вчера', 'А', 0, ChatActivity.none),
  _ChatStub('c6', 'Лиза', 'увидимся!', 'пн', 'Л', 0, ChatActivity.online),
];

class _ChatList extends StatelessWidget {
  final String? activeChat;
  final ValueChanged<String> onChat;
  final void Function(String id, double y) onPeek;
  final ValueChanged<String> onSplit;
  const _ChatList({required this.activeChat, required this.onChat, required this.onPeek, required this.onSplit});

  @override
  Widget build(BuildContext context) {
    return ListView.builder(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 4),
      itemCount: _chats.length,
      itemBuilder: (_, i) => _ChatItem(
        chat: _chats[i],
        active: activeChat == _chats[i].id,
        onTap: () => onChat(_chats[i].id),
        onPeek: (y) => onPeek(_chats[i].id, y),
        onSplit: () => onSplit(_chats[i].id),
      ).animate(delay: Duration(milliseconds: 40 * i))
          .fadeIn(duration: 300.ms, curve: Curves.easeOut)
          .slideX(begin: -0.05, duration: 300.ms, curve: Curves.easeOutCubic),
    );
  }
}

class _ChatItem extends StatefulWidget {
  final _ChatStub chat;
  final bool active;
  final VoidCallback onTap;
  final ValueChanged<double> onPeek;
  final VoidCallback onSplit;
  const _ChatItem({required this.chat, required this.active, required this.onTap, required this.onPeek, required this.onSplit});
  @override
  State<_ChatItem> createState() => _ChatItemState();
}

class _ChatItemState extends State<_ChatItem> {
  bool _h = false;

  void _showContextMenu(BuildContext ctx, Offset pos) {
    final overlay = Overlay.of(ctx);
    late OverlayEntry entry;
    entry = OverlayEntry(builder: (_) => _AnimatedContextMenu(
      position: pos,
      items: [
        _CMenuItem(widget.chat.pinned ? 'открепить' : 'закрепить', Icons.push_pin_outlined),
        _CMenuItem('заглушить', Icons.volume_off_outlined),
        _CMenuItem('прочитать', Icons.done_all),
        _CMenuItem('архивировать', Icons.archive_outlined),
        _CMenuItem('удалить', Icons.delete_outline, danger: true),
        null, // divider
        _CMenuItem('быстрый ответ', Icons.visibility_outlined),
        _CMenuItem('открыть рядом', Icons.grid_view_outlined),
      ],
      onSelect: (label) {
        entry.remove();
        if (label == 'быстрый ответ') {
          final box = ctx.findRenderObject() as RenderBox;
          widget.onPeek(box.localToGlobal(Offset.zero).dy);
        } else if (label == 'открыть рядом') {
          widget.onSplit();
        }
      },
      onClose: () => entry.remove(),
    ));
    overlay.insert(entry);
  }

  Color get _barColor {
    switch (widget.chat.activity) {
      case ChatActivity.none: return Colors.transparent;
      case ChatActivity.online: return BColors.accent.withValues(alpha: 0.04);
      case ChatActivity.typing: return BColors.accent.withValues(alpha: 0.15);
      case ChatActivity.newMessage: return BColors.accent.withValues(alpha: 0.25);
      case ChatActivity.inVoice: return const Color(0xFF3b82f6).withValues(alpha: 0.12);
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = widget.chat;
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setState(() => _h = true),
      onExit: (_) => setState(() => _h = false),
      child: GestureDetector(
        onTap: widget.onTap,
        onSecondaryTapDown: (details) => _showContextMenu(context, details.globalPosition),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 100),
          margin: const EdgeInsets.only(bottom: 1),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(12),
            color: widget.active ? Colors.white.withValues(alpha: 0.06)
                : _h ? Colors.white.withValues(alpha: 0.03) : Colors.transparent,
          ),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(10, 10, 10, 8),
              child: Row(children: [
                // Avatar
                Stack(children: [
                  Container(
                    width: 40, height: 40,
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(12),
                      color: BColors.accent.withValues(alpha: 0.12),
                    ),
                    child: Center(child: Text(c.initial, style: TextStyle(
                      fontFamily: 'Nekst', fontSize: 16, fontWeight: FontWeight.w700,
                      color: BColors.accent.withValues(alpha: 0.7),
                    ))),
                  ),
                  if (c.activity == ChatActivity.online || c.activity == ChatActivity.typing)
                    Positioned(bottom: 0, right: 0, child: Container(
                      width: 10, height: 10,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: const Color(0xFF00E676),
                        border: Border.all(color: const Color(0xFF0d0d10), width: 2),
                      ),
                    )),
                ]),
                const SizedBox(width: 10),
                // Name + message
                Expanded(child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(children: [
                      if (c.pinned) Padding(
                        padding: const EdgeInsets.only(right: 4),
                        child: Icon(Icons.push_pin, size: 11, color: BColors.textMuted),
                      ),
                      Expanded(child: Text(c.name, maxLines: 1, overflow: TextOverflow.ellipsis,
                        style: TextStyle(fontFamily: 'Onest', fontSize: rf(context, 14),
                          fontWeight: c.unread > 0 ? FontWeight.w600 : FontWeight.w500,
                          color: BColors.textPrimary))),
                      Text(c.time, style: TextStyle(fontFamily: 'Onest',
                        fontSize: rf(context, 11), color: BColors.textMuted)),
                    ]),
                    const SizedBox(height: 3),
                    Row(children: [
                      Expanded(child: c.activity == ChatActivity.typing
                        ? _TypingDots()
                        : Text(c.lastMsg, maxLines: 1, overflow: TextOverflow.ellipsis,
                            style: TextStyle(fontFamily: 'Onest', fontSize: rf(context, 13),
                              fontWeight: FontWeight.w400, color: BColors.textSecondary)),
                      ),
                      if (c.unread > 0) ...[
                        const SizedBox(width: 6),
                        Container(
                          constraints: const BoxConstraints(minWidth: 18),
                          height: 18,
                          padding: const EdgeInsets.symmetric(horizontal: 5),
                          decoration: BoxDecoration(
                            borderRadius: BorderRadius.circular(9),
                            color: BColors.accent,
                          ),
                          child: Center(child: Text(
                            c.unread > 99 ? '99+' : '${c.unread}',
                            style: const TextStyle(fontSize: 10, fontWeight: FontWeight.w700, color: BColors.bg),
                          )),
                        ),
                      ],
                    ]),
                  ],
                )),
                // Hover icons
                if (_h) ...[
                  const SizedBox(width: 4),
                  _MiniBtn(icon: Icons.visibility_outlined, tooltip: 'быстрый ответ',
                    onTap: () {
                      final box = context.findRenderObject() as RenderBox;
                      final pos = box.localToGlobal(Offset.zero);
                      widget.onPeek(pos.dy);
                    }),
                  _MiniBtn(icon: Icons.grid_view_outlined, tooltip: 'открыть рядом',
                    onTap: widget.onSplit),
                ],
              ]),
            ),
            // Activity bar
            AnimatedContainer(
              duration: const Duration(milliseconds: 300),
              height: 2,
              margin: const EdgeInsets.symmetric(horizontal: 10),
              decoration: BoxDecoration(
                color: _barColor,
                borderRadius: BorderRadius.circular(1),
              ),
            ).animate(
              target: (c.activity == ChatActivity.typing || c.activity == ChatActivity.inVoice) ? 1 : 0,
              onPlay: (ctrl) { if (c.activity == ChatActivity.typing || c.activity == ChatActivity.inVoice) ctrl.repeat(reverse: true); },
            ).fade(begin: 0.4, end: 1.0, duration: 1500.ms, curve: Curves.easeInOut),
          ]),
        ),
      ),
    );
  }
}

// ─── Typing dots ──────────────────────────────────────────────

class _TypingDots extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Row(children: [
      for (var i = 0; i < 3; i++)
        Container(
          width: 4, height: 4,
          margin: EdgeInsets.only(right: 3, top: 4),
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: BColors.accent.withValues(alpha: 0.5),
          ),
        ).animate(
          onPlay: (c) => c.repeat(reverse: true),
          delay: Duration(milliseconds: i * 200),
        ).fade(begin: 0.3, end: 1.0, duration: 800.ms, curve: Curves.easeInOut),
    ]);
  }
}

// ─── Mini hover button ────────────────────────────────────────

class _MiniBtn extends StatefulWidget {
  final IconData icon;
  final String tooltip;
  final VoidCallback? onTap;
  const _MiniBtn({required this.icon, required this.tooltip, this.onTap});
  @override
  State<_MiniBtn> createState() => _MiniBtnState();
}

class _MiniBtnState extends State<_MiniBtn> {
  bool _h = false;
  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: widget.tooltip,
      waitDuration: const Duration(milliseconds: 300),
      textStyle: const TextStyle(fontSize: 10, color: BColors.textPrimary),
      decoration: BoxDecoration(
        color: const Color(0xFF1a1a1a), borderRadius: BorderRadius.circular(6),
        border: Border.all(color: BColors.borderLow),
      ),
      child: MouseRegion(
        cursor: SystemMouseCursors.click,
        onEnter: (_) => setState(() => _h = true),
        onExit: (_) => setState(() => _h = false),
        child: GestureDetector(
          onTap: widget.onTap ?? () {},
          child: SizedBox(
            width: 22, height: 22,
            child: Center(child: Icon(widget.icon, size: 14,
              color: _h ? Colors.white.withValues(alpha: 0.5) : BColors.textMuted)),
          ),
        ),
      ),
    );
  }
}

// ═══════════════════════════════════════════════════════════════
// CONTACT LIST
// ═══════════════════════════════════════════════════════════════

const _contacts = [
  ('u1', 'Катя', 'в сети', true, 'К'),
  ('u2', 'Максим', 'был 2 часа назад', false, 'М'),
  ('u3', 'Аня', 'в сети', true, 'А'),
  ('u4', 'Дима', 'был вчера', false, 'Д'),
  ('u5', 'Лиза', 'не беспокоить', false, 'Л'),
];

class _ContactList extends StatelessWidget {
  final ValueChanged<String> onChat;
  const _ContactList({required this.onChat});

  @override
  Widget build(BuildContext context) {
    return ListView.builder(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 4),
      itemCount: _contacts.length,
      itemBuilder: (_, i) {
        final (id, name, status, online, initial) = _contacts[i];
        return _ContactItem(id: id, name: name, status: status, online: online,
          initial: initial, onTap: () => onChat(id))
            .animate(delay: Duration(milliseconds: 40 * i))
            .fadeIn(duration: 300.ms, curve: Curves.easeOut)
            .slideX(begin: -0.05, duration: 300.ms, curve: Curves.easeOutCubic);
      },
    );
  }
}

class _ContactItem extends StatefulWidget {
  final String id, name, status, initial;
  final bool online;
  final VoidCallback onTap;
  const _ContactItem({required this.id, required this.name, required this.status,
    required this.online, required this.initial, required this.onTap});
  @override
  State<_ContactItem> createState() => _ContactItemState();
}

class _ContactItemState extends State<_ContactItem> {
  bool _h = false;
  @override
  Widget build(BuildContext context) {
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setState(() => _h = true),
      onExit: (_) => setState(() => _h = false),
      child: GestureDetector(
        onTap: widget.onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 100),
          margin: const EdgeInsets.only(bottom: 1),
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 10),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(12),
            color: _h ? Colors.white.withValues(alpha: 0.04) : Colors.transparent,
          ),
          child: Row(children: [
            Container(
              width: 36, height: 36,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(10),
                color: BColors.accent.withValues(alpha: 0.10),
              ),
              child: Center(child: Text(widget.initial, style: TextStyle(
                fontFamily: 'Nekst', fontSize: 14, fontWeight: FontWeight.w700,
                color: BColors.accent.withValues(alpha: 0.7),
              ))),
            ),
            const SizedBox(width: 10),
            Expanded(child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(widget.name, style: TextStyle(fontFamily: 'Onest',
                  fontSize: rf(context, 14), fontWeight: FontWeight.w500, color: BColors.textPrimary)),
                const SizedBox(height: 2),
                Text(widget.status, style: TextStyle(fontFamily: 'Onest',
                  fontSize: rf(context, 12), color: BColors.textSecondary)),
              ],
            )),
            Container(width: 8, height: 8, decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: widget.online ? const Color(0xFF00E676) : Colors.white.withValues(alpha: 0.15),
            )),
          ]),
        ),
      ),
    );
  }
}

// ═══════════════════════════════════════════════════════════════
// CHANNEL LIST
// ═══════════════════════════════════════════════════════════════

const _channels = [
  ('ch_news', 'blesk news', '1.2к подписчиков', Icons.campaign_outlined),
  ('ch_design', 'design daily', '482 подписчика', Icons.palette_outlined),
  ('ch_music', 'soundtrack', '156 подписчиков', Icons.music_note_outlined),
];

class _ChannelList extends StatelessWidget {
  final ValueChanged<String> onChat;
  const _ChannelList({required this.onChat});

  @override
  Widget build(BuildContext context) {
    return ListView.builder(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 4),
      itemCount: _channels.length,
      itemBuilder: (_, i) {
        final (id, name, subs, icon) = _channels[i];
        return _ChannelItem(id: id, name: name, subs: subs, icon: icon,
          onTap: () => onChat(id))
            .animate(delay: Duration(milliseconds: 40 * i))
            .fadeIn(duration: 300.ms, curve: Curves.easeOut)
            .slideX(begin: -0.05, duration: 300.ms, curve: Curves.easeOutCubic);
      },
    );
  }
}

class _ChannelItem extends StatefulWidget {
  final String id, name, subs;
  final IconData icon;
  final VoidCallback onTap;
  const _ChannelItem({required this.id, required this.name, required this.subs,
    required this.icon, required this.onTap});
  @override
  State<_ChannelItem> createState() => _ChannelItemState();
}

class _ChannelItemState extends State<_ChannelItem> {
  bool _h = false;
  @override
  Widget build(BuildContext context) {
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setState(() => _h = true),
      onExit: (_) => setState(() => _h = false),
      child: GestureDetector(
        onTap: widget.onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 100),
          margin: const EdgeInsets.only(bottom: 1),
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 10),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(12),
            color: _h ? Colors.white.withValues(alpha: 0.04) : Colors.transparent,
          ),
          child: Row(children: [
            Container(
              width: 36, height: 36,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(10),
                color: BColors.accent.withValues(alpha: 0.08),
              ),
              child: Center(child: Icon(widget.icon, size: 18,
                color: BColors.accent.withValues(alpha: 0.6))),
            ),
            const SizedBox(width: 10),
            Expanded(child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(widget.name, style: TextStyle(fontFamily: 'Onest',
                  fontSize: rf(context, 14), fontWeight: FontWeight.w500, color: BColors.textPrimary)),
                const SizedBox(height: 2),
                Text(widget.subs, style: TextStyle(fontFamily: 'Onest',
                  fontSize: rf(context, 12), color: BColors.textMuted)),
              ],
            )),
          ]),
        ),
      ),
    );
  }
}

// ═══════════════════════════════════════════════════════════════
// CONTENT AREA
// ═══════════════════════════════════════════════════════════════

class _SplitContent extends StatelessWidget {
  final List<String> panels;
  final int focusedPanel;
  final bool focusMode;
  final ValueChanged<int> onFocus;
  final ValueChanged<int> onClose;
  final VoidCallback onToggleFocus;
  final ValueChanged<String> onProfile;
  final void Function(String id, bool video) onCall;

  const _SplitContent({
    required this.panels, required this.focusedPanel,
    required this.focusMode, required this.onFocus,
    required this.onClose, required this.onToggleFocus,
    required this.onProfile, required this.onCall,
  });

  @override
  Widget build(BuildContext context) {
    // Focus mode: one panel expanded + stack on the right
    if (focusMode && panels.length > 1) {
      final fi = focusedPanel.clamp(0, panels.length - 1);
      final others = <int>[for (var i = 0; i < panels.length; i++) if (i != fi) i];

      return Row(children: [
        // Focused panel
        Expanded(
          child: Container(
            color: BColors.bg,
            child: _panelForId(panels[fi],
                showClose: true, showFocus: true, isFocusMode: true,
                onClose: () => onClose(fi),
                onToggleFocus: onToggleFocus,
                onProfile: onProfile, onCall: onCall),
          ),
        ).animate().fadeIn(duration: 300.ms).slideX(begin: -0.02, duration: 350.ms, curve: Curves.easeOutCubic),
        // Stack
        _CardStack(
          panels: panels,
          indices: others,
          onTap: (i) => onFocus(i),
        ).animate().fadeIn(duration: 250.ms, delay: 100.ms)
            .slideX(begin: 0.3, duration: 350.ms, curve: Curves.easeOutCubic),
      ]);
    }

    // Split mode: panels side by side
    return Row(
      children: [
        for (var i = 0; i < panels.length; i++) ...[
          if (i > 0) _SplitDivider(),
          Expanded(
            child: GestureDetector(
              onTap: () => onFocus(i),
              child: Container(
                decoration: BoxDecoration(
                  color: BColors.bg,
                  border: i == focusedPanel && panels.length > 1
                      ? const Border(top: BorderSide(color: BColors.accent, width: 2))
                      : null,
                ),
                child: _panelForId(panels[i],
                    showClose: panels.length > 1,
                    showFocus: panels.length > 1,
                    isFocusMode: false,
                    onClose: () => onClose(i),
                    onToggleFocus: onToggleFocus,
                    onProfile: onProfile, onCall: onCall),
              ),
            ),
          ).animate().fadeIn(duration: 250.ms, curve: Curves.easeOut)
              .slideX(begin: 0.03, duration: 300.ms, curve: Curves.easeOutCubic),
        ],
      ],
    );
  }

  Widget _panelForId(String id, {
    required bool showClose, required bool showFocus, required bool isFocusMode,
    required VoidCallback onClose, required VoidCallback onToggleFocus,
    required ValueChanged<String> onProfile,
    required void Function(String id, bool video) onCall,
  }) {
    if (isChannelId(id)) {
      return ChannelFeedPanel(
        channelId: id,
        showClose: showClose,
        onClose: onClose,
      );
    }
    return _ChatPanel(
      chatId: id,
      showClose: showClose,
      showFocus: showFocus,
      isFocusMode: isFocusMode,
      onClose: onClose,
      onToggleFocus: onToggleFocus,
      onProfile: onProfile,
      onCall: onCall,
    );
  }
}

// ─── Card Stack (Focus mode) ──────────────────────────────────

class _CardStack extends StatefulWidget {
  final List<String> panels;
  final List<int> indices;
  final ValueChanged<int> onTap;
  const _CardStack({required this.panels, required this.indices, required this.onTap});
  @override
  State<_CardStack> createState() => _CardStackState();
}

class _CardStackState extends State<_CardStack> {
  bool _h = false;

  @override
  Widget build(BuildContext context) {
    return MouseRegion(
      onEnter: (_) => setState(() => _h = true),
      onExit: (_) => setState(() => _h = false),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        width: _h ? 52 : 42,
        color: const Color(0xFF0d0d10),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            for (var j = 0; j < widget.indices.length; j++)
              _StackCard(
                chatId: widget.panels[widget.indices[j]],
                offset: j,
                expanded: _h,
                onTap: () => widget.onTap(widget.indices[j]),
              ).animate(delay: Duration(milliseconds: 80 * j))
                  .fadeIn(duration: 250.ms)
                  .slideX(begin: 0.5, duration: 300.ms, curve: Curves.easeOutCubic),
          ],
        ),
      ),
    );
  }
}

class _StackCard extends StatefulWidget {
  final String chatId;
  final int offset;
  final bool expanded;
  final VoidCallback onTap;
  const _StackCard({required this.chatId, required this.offset, required this.expanded, required this.onTap});
  @override
  State<_StackCard> createState() => _StackCardState();
}

class _StackCardState extends State<_StackCard> {
  bool _h = false;

  @override
  Widget build(BuildContext context) {
    final chat = _chats.where((c) => c.id == widget.chatId).firstOrNull;
    final initial = chat?.initial ?? '?';
    final hasUnread = (chat?.unread ?? 0) > 0;

    return Tooltip(
      message: chat?.name ?? widget.chatId,
      waitDuration: const Duration(milliseconds: 300),
      textStyle: const TextStyle(fontSize: 11, color: BColors.textPrimary),
      decoration: BoxDecoration(
        color: const Color(0xFF1a1a1a), borderRadius: BorderRadius.circular(6),
        border: Border.all(color: BColors.borderLow),
      ),
      child: MouseRegion(
        cursor: SystemMouseCursors.click,
        onEnter: (_) => setState(() => _h = true),
        onExit: (_) => setState(() => _h = false),
        child: GestureDetector(
          onTap: widget.onTap,
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 200),
            curve: Curves.easeOutCubic,
            width: widget.expanded ? 40 : 35,
            height: widget.expanded ? 40 : 35,
            margin: EdgeInsets.only(bottom: widget.expanded ? 8 : 4),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(10),
              color: _h ? Colors.white.withValues(alpha: 0.08) : Colors.white.withValues(alpha: 0.04),
              border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
              boxShadow: const [BoxShadow(color: Color(0x30000000), blurRadius: 8, offset: Offset(0, 2))],
            ),
            child: Stack(children: [
              Center(child: Text(initial, style: TextStyle(
                fontSize: widget.expanded ? 14 : 12,
                fontWeight: FontWeight.w700,
                color: BColors.accent.withValues(alpha: 0.7),
              ))),
              if (hasUnread)
                Positioned(top: 2, right: 2, child: Container(
                  width: 7, height: 7,
                  decoration: const BoxDecoration(shape: BoxShape.circle, color: BColors.accent),
                )),
            ]),
          ),
        ),
      ),
    );
  }
}

class _SplitDivider extends StatefulWidget {
  @override
  State<_SplitDivider> createState() => _SplitDividerState();
}

class _SplitDividerState extends State<_SplitDivider> {
  bool _h = false;
  @override
  Widget build(BuildContext context) {
    return MouseRegion(
      cursor: SystemMouseCursors.resizeColumn,
      onEnter: (_) => setState(() => _h = true),
      onExit: (_) => setState(() => _h = false),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 150),
        width: _h ? 3 : 1,
        color: _h ? BColors.accent.withValues(alpha: 0.3) : BColors.borderLow,
      ),
    );
  }
}

class _ChatPanel extends StatefulWidget {
  final String chatId;
  final bool showClose;
  final bool showFocus;
  final bool isFocusMode;
  final VoidCallback onClose;
  final VoidCallback? onToggleFocus;
  final ValueChanged<String>? onProfile;
  final void Function(String id, bool video)? onCall;
  const _ChatPanel({super.key, required this.chatId, required this.showClose, required this.onClose,
    this.showFocus = false, this.isFocusMode = false, this.onToggleFocus, this.onProfile, this.onCall});

  @override
  State<_ChatPanel> createState() => _ChatPanelState();
}

class _ChatPanelState extends State<_ChatPanel> {
  ReplyQuote? _replyTo;
  String? _editText;
  bool _searchOpen = false;
  String _searchQuery = '';
  int _currentMatchIdx = 0; // 0-based among matches
  List<String> _matchMessageIds = const [];

  void _recomputeMatches() {
    final q = _searchQuery.trim().toLowerCase();
    if (q.isEmpty) {
      _matchMessageIds = const [];
      _currentMatchIdx = 0;
      return;
    }
    final msgs = stubMessages[widget.chatId] ?? const [];
    _matchMessageIds = msgs
        .where((m) => (m.text ?? '').toLowerCase().contains(q))
        .map((m) => m.id)
        .toList();
    if (_currentMatchIdx >= _matchMessageIds.length) _currentMatchIdx = 0;
  }

  void _toggleSearch() {
    setState(() {
      _searchOpen = !_searchOpen;
      if (!_searchOpen) {
        _searchQuery = '';
        _matchMessageIds = const [];
        _currentMatchIdx = 0;
      }
    });
  }

  void _openMedia(BuildContext ctx, MessageData msg, String senderName) {
    // Build media list from the current chat's messages (photo/video/gif)
    final all = stubMessages[widget.chatId] ?? [];
    final media = <MediaItem>[];
    int startIndex = 0;
    for (final m in all) {
      if (m.type == MessageType.photo && m.photoTints != null) {
        for (var i = 0; i < m.photoTints!.length; i++) {
          if (m.id == msg.id && i == 0) startIndex = media.length;
          media.add(MediaItem(
            id: '${m.id}_$i', tint: m.photoTints![i],
            caption: i == 0 ? m.text : null,
          ));
        }
      } else if (m.type == MessageType.video) {
        if (m.id == msg.id) startIndex = media.length;
        media.add(MediaItem(
          id: m.id,
          tint: m.photoTints?.first ?? const Color(0xFF5b8fff),
          isVideo: true, videoDuration: m.videoDuration,
          caption: m.text,
        ));
      } else if (m.type == MessageType.gif && m.gifTint != null) {
        if (m.id == msg.id) startIndex = media.length;
        media.add(MediaItem(id: m.id, tint: m.gifTint!));
      }
    }
    if (media.isEmpty) return;
    showMediaViewer(
      ctx,
      items: media,
      startIndex: startIndex,
      senderName: msg.own ? 'ты' : senderName,
      dateText: msg.time,
      onDownload: () {},
    );
  }

  @override
  Widget build(BuildContext context) {
    final chatId = widget.chatId;
    final showClose = widget.showClose;
    final showFocus = widget.showFocus;
    final isFocusMode = widget.isFocusMode;
    final onClose = widget.onClose;
    final onToggleFocus = widget.onToggleFocus;
    final onProfile = widget.onProfile;
    final onCall = widget.onCall;
    final chat = _chats.where((c) => c.id == chatId).firstOrNull;
    final name = chat?.name ?? chatId;
    final initial = chat?.initial ?? '?';

    return Column(children: [
      // Header
      LayoutBuilder(builder: (context, constraints) {
        final compact = constraints.maxWidth < 300;
        return Container(
        height: 52,
        padding: EdgeInsets.symmetric(horizontal: compact ? 10 : 16),
        decoration: const BoxDecoration(
          border: Border(bottom: BorderSide(color: BColors.borderLow)),
        ),
        child: Row(children: [
          MouseRegion(
            cursor: SystemMouseCursors.click,
            child: GestureDetector(
              onTap: () => onProfile?.call(chatId),
              child: Container(
                width: 28, height: 28,
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(8),
                  color: BColors.accent.withValues(alpha: 0.10),
                ),
                child: Center(child: Text(initial, style: const TextStyle(
                  fontSize: 12, fontWeight: FontWeight.w700, color: BColors.accent,
                ))),
              ),
            ),
          ),
          const SizedBox(width: 8),
          Expanded(child: MouseRegion(
            cursor: SystemMouseCursors.click,
            child: GestureDetector(
              onTap: () => onProfile?.call(chatId),
              child: Text(name, maxLines: 1, overflow: TextOverflow.ellipsis,
                style: TextStyle(fontFamily: 'Onest',
                fontSize: rf(context, compact ? 13 : 15), fontWeight: FontWeight.w600, color: BColors.textPrimary)),
            ),
          )),
          if (!compact) ...[
            Container(width: 6, height: 6, decoration: const BoxDecoration(
              shape: BoxShape.circle, color: Color(0xFF00E676))),
            const SizedBox(width: 8),
          ],
          if (!compact) ...[
            if (stubMembers(chatId).isNotEmpty) ...[
              _CallHeaderBtn(icon: Icons.people_outline, tooltip: 'участники',
                onTap: () => showMembersPanel(context,
                    chatId: chatId, groupName: name)),
              const SizedBox(width: 2),
            ],
            _CallHeaderBtn(icon: Icons.search_rounded, tooltip: 'поиск в чате (ctrl+f)',
              onTap: _toggleSearch),
            const SizedBox(width: 2),
          ],
          if (onCall != null && !compact) ...[
            _CallHeaderBtn(icon: Icons.phone_outlined, tooltip: 'позвонить',
              onTap: () => onCall.call(chatId, false)),
            const SizedBox(width: 2),
            _CallHeaderBtn(icon: Icons.videocam_outlined, tooltip: 'видеозвонок',
              onTap: () => onCall.call(chatId, true)),
          ],
          if (showFocus && onToggleFocus != null) ...[
            const SizedBox(width: 10),
            _PanelActionBtn(
              icon: isFocusMode ? Icons.unfold_more : Icons.unfold_less,
              tooltip: isFocusMode ? 'показать все' : 'сфокусировать',
              onTap: onToggleFocus!,
            ),
          ],
          if (showClose) ...[
            const SizedBox(width: 4),
            _PanelCloseBtn(onTap: onClose),
          ],
        ]),
      );
      }),
      // In-chat search bar (Ctrl+F)
      if (_searchOpen) ChatSearchBar(
        onQueryChanged: (q) {
          setState(() { _searchQuery = q; _recomputeMatches(); });
        },
        onPrev: () => setState(() {
          if (_matchMessageIds.isEmpty) return;
          _currentMatchIdx = (_currentMatchIdx - 1 + _matchMessageIds.length)
              % _matchMessageIds.length;
        }),
        onNext: () => setState(() {
          if (_matchMessageIds.isEmpty) return;
          _currentMatchIdx = (_currentMatchIdx + 1) % _matchMessageIds.length;
        }),
        onClose: _toggleSearch,
        matchCount: _matchMessageIds.length,
        currentMatch: _matchMessageIds.isEmpty ? 0 : _currentMatchIdx + 1,
      ),
      // Messages
      Expanded(child: ChatMessages(
        chatId: chatId,
        highlightQuery: _searchOpen ? _searchQuery : null,
        currentMatchId: _matchMessageIds.isEmpty
            ? null
            : _matchMessageIds[_currentMatchIdx.clamp(0, _matchMessageIds.length - 1)].hashCode,
        onReply: (q) => setState(() => _replyTo = q),
        onEditStart: (text) => setState(() => _editText = text),
        onOpenMedia: (msg) => _openMedia(context, msg, name),
      )),
      // Input bar
      InputBar(
        replyTo: _replyTo?.senderName,
        replyText: _replyTo?.text,
        editText: _editText,
        onCancelReply: () => setState(() => _replyTo = null),
        onCancelEdit: () => setState(() => _editText = null),
        onSend: (text) {
        final msgs = stubMessages[chatId] ??= [];
        msgs.add(MessageData(
          id: '${msgs.length + 1}', text: text,
          time: 'сейчас', own: true, read: false,
          reply: _replyTo,
        ));
        setState(() { _replyTo = null; _editText = null; });
      }),
    ]);
  }
}

class _PanelActionBtn extends StatefulWidget {
  final IconData icon;
  final String tooltip;
  final VoidCallback onTap;
  const _PanelActionBtn({required this.icon, required this.tooltip, required this.onTap});
  @override
  State<_PanelActionBtn> createState() => _PanelActionBtnState();
}

class _PanelActionBtnState extends State<_PanelActionBtn> {
  bool _h = false;
  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: widget.tooltip,
      waitDuration: const Duration(milliseconds: 400),
      textStyle: const TextStyle(fontSize: 10, color: BColors.textPrimary),
      decoration: BoxDecoration(color: const Color(0xFF1a1a1a), borderRadius: BorderRadius.circular(6),
        border: Border.all(color: BColors.borderLow)),
      child: MouseRegion(
        cursor: SystemMouseCursors.click,
        onEnter: (_) => setState(() => _h = true),
        onExit: (_) => setState(() => _h = false),
        child: GestureDetector(
          onTap: widget.onTap,
          child: Container(
            width: 24, height: 24,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(6),
              color: _h ? Colors.white.withValues(alpha: 0.06) : Colors.transparent,
            ),
            child: Center(child: Icon(widget.icon, size: 14,
              color: _h ? BColors.textSecondary : BColors.textMuted)),
          ),
        ),
      ),
    );
  }
}

class _PanelCloseBtn extends StatefulWidget {
  final VoidCallback onTap;
  const _PanelCloseBtn({required this.onTap});
  @override
  State<_PanelCloseBtn> createState() => _PanelCloseBtnState();
}

class _PanelCloseBtnState extends State<_PanelCloseBtn> {
  bool _h = false;
  @override
  Widget build(BuildContext context) {
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setState(() => _h = true),
      onExit: (_) => setState(() => _h = false),
      child: GestureDetector(
        onTap: widget.onTap,
        child: Container(
          width: 24, height: 24,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(6),
            color: _h ? const Color(0x20FF4444) : Colors.transparent,
          ),
          child: Center(child: Icon(Icons.close, size: 14,
            color: _h ? const Color(0xCCFF4444) : BColors.textMuted)),
        ),
      ),
    );
  }
}

// ─── Offline Banner ───────────────────────────────────────────

class OfflineBanner extends StatelessWidget {
  const OfflineBanner({super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 32,
      color: const Color(0x33FFB800),
      child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [
        Icon(Icons.wifi_off, size: 14, color: const Color(0xCCFFB800)),
        const SizedBox(width: 8),
        Text('нет соединения', style: TextStyle(fontFamily: 'Onest',
          fontSize: 12, fontWeight: FontWeight.w500, color: const Color(0xCCFFB800))),
      ]),
    );
  }
}

// ─── Skeleton Chat Item ───────────────────────────────────────

class _SkeletonChatItem extends StatelessWidget {
  const _SkeletonChatItem();

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 10, 16, 10),
      child: Row(children: [
        Container(width: 40, height: 40, decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(12),
          color: Colors.white.withValues(alpha: 0.04),
        )),
        const SizedBox(width: 10),
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Container(width: 100, height: 12, decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(4),
            color: Colors.white.withValues(alpha: 0.04),
          )),
          const SizedBox(height: 6),
          Container(width: 160, height: 10, decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(4),
            color: Colors.white.withValues(alpha: 0.03),
          )),
        ])),
      ]),
    ).animate(onPlay: (c) => c.repeat(reverse: true))
        .fade(begin: 0.4, end: 1.0, duration: 1200.ms, curve: Curves.easeInOut);
  }
}

class _EmptyState extends StatelessWidget {
  const _EmptyState({super.key});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        Image.asset('assets/logo/blesk-logo.png', width: 80,
          opacity: const AlwaysStoppedAnimation(0.06))
            .animate().fadeIn(duration: 600.ms).scale(begin: const Offset(0.9, 0.9), duration: 600.ms, curve: Curves.easeOutCubic),
        const SizedBox(height: 16),
        Text('выбери чат', style: TextStyle(
          fontFamily: 'Onest', fontSize: rf(context, 14), color: BColors.textMuted,
        )).animate(delay: 200.ms).fadeIn(duration: 400.ms).slideY(begin: 0.1),
        const SizedBox(height: 6),
        Text('или начни новый — Ctrl+N', style: TextStyle(
          fontFamily: 'Onest', fontSize: rf(context, 12), color: BColors.textMuted.withValues(alpha: 0.6),
        )).animate(delay: 350.ms).fadeIn(duration: 400.ms),
      ]),
    );
  }
}

// ═══════════════════════════════════════════════════════════════
// PEEK & REPLY OVERLAY
// ═══════════════════════════════════════════════════════════════

class _PeekOverlay extends StatefulWidget {
  final String chatId;
  final double topOffset;
  final VoidCallback onClose;
  final VoidCallback onOpen;
  final VoidCallback onSplit;
  const _PeekOverlay({required this.chatId, required this.topOffset,
    required this.onClose, required this.onOpen, required this.onSplit});
  @override
  State<_PeekOverlay> createState() => _PeekOverlayState();
}

class _PeekOverlayState extends State<_PeekOverlay> {
  final _inputCtrl = TextEditingController();
  final _messages = <_PeekMsg>[];

  @override
  void initState() {
    super.initState();
    // Stub messages
    final chat = _chats.where((c) => c.id == widget.chatId).firstOrNull;
    if (chat != null) {
      _messages.addAll([
        _PeekMsg('привет!', false, '14:28'),
        _PeekMsg('как дела?', false, '14:30'),
        _PeekMsg(chat.lastMsg, false, chat.time),
      ]);
    }
  }

  @override
  void dispose() {
    _inputCtrl.dispose();
    super.dispose();
  }

  void _send() {
    final text = _inputCtrl.text.trim();
    if (text.isEmpty) return;
    setState(() => _messages.add(_PeekMsg(text, true, 'сейчас')));
    _inputCtrl.clear();
  }

  @override
  Widget build(BuildContext context) {
    final chat = _chats.where((c) => c.id == widget.chatId).firstOrNull;
    final name = chat?.name ?? widget.chatId;
    final initial = chat?.initial ?? '?';
    final screenH = MediaQuery.of(context).size.height;

    // Position: right of sidebar, aligned to chat item Y
    // Clamp so it doesn't go off screen
    final top = widget.topOffset.clamp(50.0, screenH - 420.0);

    return Stack(children: [
      // Scrim — click to close (dark overlay)
      Positioned.fill(
        child: GestureDetector(
          onTap: widget.onClose,
          child: Container(color: Colors.black.withValues(alpha: 0.2)),
        ),
      ).animate().fade(begin: 0, duration: 150.ms),
      // Peek card
      Positioned(
        left: 288, // sidebar 280 + 8 gap
        top: top,
        child: Material(
          color: Colors.transparent,
          child: ClipRRect(
            borderRadius: BorderRadius.circular(16),
            child: BackdropFilter(
              filter: ImageFilter.blur(sigmaX: 24, sigmaY: 24),
              child: Container(
                width: 320,
                constraints: const BoxConstraints(maxHeight: 400),
                decoration: BoxDecoration(
                  color: const Color(0xD90e0e12), // rgba(14,14,18,0.85)
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(color: BColors.accent.withValues(alpha: 0.06)),
                  boxShadow: const [BoxShadow(color: Color(0x66000000), blurRadius: 48, offset: Offset(0, 12))],
                ),
                child: Column(mainAxisSize: MainAxisSize.min, children: [
                  // Header
                  Padding(
                    padding: const EdgeInsets.fromLTRB(14, 12, 10, 0),
                    child: Row(children: [
                      Container(
                        width: 22, height: 22,
                        decoration: BoxDecoration(
                          borderRadius: BorderRadius.circular(7),
                          color: BColors.accent.withValues(alpha: 0.12),
                        ),
                        child: Center(child: Text(initial, style: const TextStyle(
                          fontSize: 10, fontWeight: FontWeight.w700, color: BColors.accent,
                        ))),
                      ),
                      const SizedBox(width: 8),
                      Text(name, style: TextStyle(fontFamily: 'Onest',
                        fontSize: rf(context, 13), fontWeight: FontWeight.w500, color: BColors.textPrimary)),
                      const SizedBox(width: 6),
                      Container(width: 5, height: 5, decoration: const BoxDecoration(
                        shape: BoxShape.circle, color: Color(0xFF00E676))),
                      const Spacer(),
                      _PeekCloseBtn(onTap: widget.onClose),
                    ]),
                  ),
                  Container(height: 1, margin: const EdgeInsets.only(top: 10),
                    color: BColors.borderLow),
                  // Messages
                  Flexible(
                    child: ListView.builder(
                      shrinkWrap: true,
                      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                      itemCount: _messages.length,
                      itemBuilder: (_, i) => _PeekBubble(msg: _messages[i]),
                    ),
                  ),
                  // Input
                  Container(
                    padding: const EdgeInsets.fromLTRB(12, 6, 8, 10),
                    child: Row(children: [
                      Expanded(
                        child: Container(
                          height: 32,
                          padding: const EdgeInsets.symmetric(horizontal: 10),
                          decoration: BoxDecoration(
                            borderRadius: BorderRadius.circular(8),
                            color: Colors.white.withValues(alpha: 0.04),
                            border: Border.all(color: Colors.white.withValues(alpha: 0.06)),
                          ),
                          child: TextField(
                            controller: _inputCtrl,
                            autofocus: true,
                            style: TextStyle(fontFamily: 'Onest', fontSize: 12.5, color: BColors.textPrimary),
                            cursorColor: BColors.accent,
                            onSubmitted: (_) => _send(),
                            decoration: const InputDecoration(
                              border: InputBorder.none, isDense: true,
                              hintText: 'быстрый ответ...',
                              hintStyle: TextStyle(fontFamily: 'Onest', fontSize: 12.5, color: BColors.textMuted),
                              contentPadding: EdgeInsets.symmetric(vertical: 7),
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(width: 6),
                      GestureDetector(
                        onTap: _send,
                        child: Icon(Icons.arrow_upward, size: 16,
                          color: BColors.accent.withValues(alpha: 0.6)),
                      ),
                    ]),
                  ),
                  // Footer buttons
                  Padding(
                    padding: const EdgeInsets.fromLTRB(14, 0, 14, 10),
                    child: Row(children: [
                      GestureDetector(
                        onTap: widget.onOpen,
                        child: MouseRegion(
                          cursor: SystemMouseCursors.click,
                          child: Text('открыть', style: TextStyle(fontFamily: 'Onest',
                            fontSize: 11, color: BColors.textSecondary)),
                        ),
                      ),
                      const Spacer(),
                      GestureDetector(
                        onTap: widget.onSplit,
                        child: MouseRegion(
                          cursor: SystemMouseCursors.click,
                          child: Row(mainAxisSize: MainAxisSize.min, children: [
                            Text('в split ', style: TextStyle(fontFamily: 'Onest',
                              fontSize: 11, color: BColors.textSecondary)),
                            Icon(Icons.grid_view_outlined, size: 11, color: BColors.textSecondary),
                          ]),
                        ),
                      ),
                    ]),
                  ),
                ]),
              ),
            ),
          ),
        ),
      ).animate().scale(begin: const Offset(0.92, 0.92), duration: 200.ms, curve: Curves.easeOutCubic)
          .fade(begin: 0, duration: 200.ms),
    ]);
  }
}

class _PeekCloseBtn extends StatefulWidget {
  final VoidCallback onTap;
  const _PeekCloseBtn({required this.onTap});
  @override
  State<_PeekCloseBtn> createState() => _PeekCloseBtnState();
}

class _PeekCloseBtnState extends State<_PeekCloseBtn> {
  bool _h = false;
  @override
  Widget build(BuildContext context) {
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setState(() => _h = true),
      onExit: (_) => setState(() => _h = false),
      child: GestureDetector(
        onTap: widget.onTap,
        child: SizedBox(width: 24, height: 24,
          child: Center(child: Icon(Icons.close, size: 16,
            color: _h ? Colors.white.withValues(alpha: 0.5) : BColors.textMuted))),
      ),
    );
  }
}

class _PeekMsg {
  final String text;
  final bool own;
  final String time;
  _PeekMsg(this.text, this.own, this.time);
}

class _PeekBubble extends StatelessWidget {
  final _PeekMsg msg;
  const _PeekBubble({required this.msg});

  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: msg.own ? Alignment.centerRight : Alignment.centerLeft,
      child: Container(
        margin: const EdgeInsets.only(bottom: 4),
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
        constraints: const BoxConstraints(maxWidth: 240),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(10),
          color: msg.own
              ? BColors.accent.withValues(alpha: 0.08)
              : Colors.white.withValues(alpha: 0.05),
        ),
        child: Row(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.end, children: [
          Flexible(child: Text(msg.text, style: TextStyle(
            fontFamily: 'Onest', fontSize: 12.5, color: BColors.textPrimary, height: 1.3,
          ))),
          const SizedBox(width: 6),
          Text(msg.time, style: TextStyle(
            fontFamily: 'Onest', fontSize: 9, color: BColors.textMuted,
          )),
        ]),
      ),
    );
  }
}

class _CallHeaderBtn extends StatefulWidget {
  final IconData icon;
  final String tooltip;
  final VoidCallback onTap;
  const _CallHeaderBtn({required this.icon, required this.tooltip, required this.onTap});
  @override
  State<_CallHeaderBtn> createState() => _CallHeaderBtnState();
}

class _CallHeaderBtnState extends State<_CallHeaderBtn> {
  bool _h = false;
  @override
  Widget build(BuildContext context) {
    return Tooltip(message: widget.tooltip,
      textStyle: const TextStyle(fontSize: 10, color: BColors.textPrimary),
      decoration: BoxDecoration(color: const Color(0xFF1a1a1a), borderRadius: BorderRadius.circular(6)),
      child: MouseRegion(
        cursor: SystemMouseCursors.click,
        onEnter: (_) => setState(() => _h = true),
        onExit: (_) => setState(() => _h = false),
        child: GestureDetector(
          onTap: widget.onTap,
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 150),
            width: 32, height: 32,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(8),
              color: _h ? BColors.accent.withValues(alpha: 0.08) : Colors.transparent,
            ),
            child: Center(child: Icon(widget.icon, size: 18,
              color: _h ? BColors.accent : BColors.textMuted)),
          ),
        ),
      ),
    );
  }
}

// ═══════════════════════════════════════════════════════════════
// ANIMATED CONTEXT MENU
// ═══════════════════════════════════════════════════════════════

class _CMenuItem {
  final String label;
  final IconData icon;
  final bool danger;
  const _CMenuItem(this.label, this.icon, {this.danger = false});
}

class _AnimatedContextMenu extends StatelessWidget {
  final Offset position;
  final List<_CMenuItem?> items; // null = divider
  final void Function(String label) onSelect;
  final VoidCallback onClose;

  const _AnimatedContextMenu({
    required this.position, required this.items,
    required this.onSelect, required this.onClose,
  });

  @override
  Widget build(BuildContext context) {
    final screen = MediaQuery.of(context).size;
    // Clamp position so menu stays on screen
    final menuW = 220.0;
    final menuH = items.length * 34.0;
    final left = (position.dx + menuW > screen.width) ? position.dx - menuW : position.dx;
    final top = (position.dy + menuH > screen.height) ? position.dy - menuH : position.dy;

    return Stack(children: [
      // Scrim
      Positioned.fill(
        child: GestureDetector(
          onTap: onClose,
          onSecondaryTap: onClose,
          child: Container(color: Colors.transparent),
        ),
      ),
      // Menu
      Positioned(
        left: left, top: top,
        child: Material(
          color: Colors.transparent,
          child: Container(
            width: menuW,
            padding: const EdgeInsets.symmetric(vertical: 6),
            decoration: BoxDecoration(
              color: const Color(0xF5141418),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
              boxShadow: const [BoxShadow(color: Color(0x80000000), blurRadius: 32, offset: Offset(0, 8))],
            ),
            child: Column(mainAxisSize: MainAxisSize.min, children: [
              for (var i = 0; i < items.length; i++)
                items[i] == null
                    ? Container(height: 1, margin: const EdgeInsets.symmetric(vertical: 4, horizontal: 8),
                        color: BColors.borderLow)
                        .animate(delay: Duration(milliseconds: 30 * i)).fadeIn(duration: 150.ms)
                    : _ContextMenuItem(
                        item: items[i]!,
                        onTap: () => onSelect(items[i]!.label),
                      ).animate(delay: Duration(milliseconds: 30 * i))
                          .fadeIn(duration: 200.ms, curve: Curves.easeOut)
                          .slideX(begin: -0.08, duration: 200.ms, curve: Curves.easeOutCubic),
            ]),
          ),
        ).animate()
            .scale(begin: const Offset(0.92, 0.92), duration: 150.ms, curve: Curves.easeOutCubic)
            .fade(begin: 0, duration: 150.ms),
      ),
    ]);
  }
}

class _ContextMenuItem extends StatefulWidget {
  final _CMenuItem item;
  final VoidCallback onTap;
  const _ContextMenuItem({required this.item, required this.onTap});
  @override
  State<_ContextMenuItem> createState() => _ContextMenuItemState();
}

class _ContextMenuItemState extends State<_ContextMenuItem> {
  bool _h = false;

  @override
  Widget build(BuildContext context) {
    final danger = widget.item.danger;
    final color = danger ? const Color(0xCCFF5C5C) : BColors.textPrimary;
    final iconColor = danger ? const Color(0xCCFF5C5C) : BColors.textSecondary;

    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setState(() => _h = true),
      onExit: (_) => setState(() => _h = false),
      child: GestureDetector(
        onTap: widget.onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 100),
          height: 32,
          padding: const EdgeInsets.symmetric(horizontal: 12),
          margin: const EdgeInsets.symmetric(horizontal: 4),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(6),
            color: _h
                ? (danger ? const Color(0x14FF5C5C) : Colors.white.withValues(alpha: 0.05))
                : Colors.transparent,
          ),
          child: Row(children: [
            Icon(widget.item.icon, size: 16, color: iconColor),
            const SizedBox(width: 10),
            Text(widget.item.label, style: TextStyle(
              fontFamily: 'Onest', fontSize: 13, color: color,
            )),
          ]),
        ),
      ),
    );
  }
}

// ═══════════════════════════════════════════════════════════════
// CREATE MENU POPUP (anchored under + in sidebar header)
// ═══════════════════════════════════════════════════════════════

class _CreateMenuPopup extends StatelessWidget {
  final VoidCallback onClose;
  final VoidCallback onGroup;
  final VoidCallback onChannel;
  final VoidCallback onContact;
  const _CreateMenuPopup({
    required this.onClose, required this.onGroup,
    required this.onChannel, required this.onContact,
  });

  @override
  Widget build(BuildContext context) {
    return Material(color: Colors.transparent, child: Stack(children: [
      Positioned.fill(child: GestureDetector(
        onTap: onClose, behavior: HitTestBehavior.opaque,
        child: const SizedBox.expand(),
      )),
      Positioned(
        left: 160, top: 72,
        child: Container(
          width: 220,
          padding: const EdgeInsets.symmetric(vertical: 6),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(10),
            color: const Color(0xF5141418),
            border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
            boxShadow: const [BoxShadow(
              color: Color(0x99000000), blurRadius: 28, offset: Offset(0, 10),
            )],
          ),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            _CreateMenuItem(
              icon: Icons.group_outlined, label: 'новая группа',
              hint: 'чат с несколькими людьми', onTap: onGroup,
            ),
            _CreateMenuItem(
              icon: Icons.campaign_outlined, label: 'новый канал',
              hint: 'публикации для подписчиков', onTap: onChannel,
            ),
            _CreateMenuItem(
              icon: Icons.person_add_alt_outlined, label: 'добавить контакт',
              hint: 'по нику или ссылке', onTap: onContact,
            ),
          ]),
        ).animate()
            .scale(begin: const Offset(0.96, 0.96),
                duration: 140.ms, curve: Curves.easeOutCubic)
            .fade(duration: 120.ms),
      ),
    ]));
  }
}

class _CreateMenuItem extends StatefulWidget {
  final IconData icon;
  final String label, hint;
  final VoidCallback onTap;
  const _CreateMenuItem({
    required this.icon, required this.label,
    required this.hint, required this.onTap,
  });
  @override
  State<_CreateMenuItem> createState() => _CreateMenuItemState();
}

class _CreateMenuItemState extends State<_CreateMenuItem> {
  bool _h = false;
  @override
  Widget build(BuildContext context) {
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setState(() => _h = true),
      onExit: (_) => setState(() => _h = false),
      child: GestureDetector(
        onTap: widget.onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 100),
          height: 46,
          margin: const EdgeInsets.symmetric(horizontal: 4),
          padding: const EdgeInsets.symmetric(horizontal: 10),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(7),
            color: _h ? Colors.white.withValues(alpha: 0.06) : Colors.transparent,
          ),
          child: Row(children: [
            Container(
              width: 30, height: 30,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(7),
                color: BColors.accent.withValues(alpha: _h ? 0.14 : 0.08),
              ),
              child: Icon(widget.icon, size: 15,
                  color: BColors.accent.withValues(alpha: 0.9)),
            ),
            const SizedBox(width: 10),
            Expanded(child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(widget.label, style: const TextStyle(
                  fontFamily: 'Onest', fontSize: 13, fontWeight: FontWeight.w500,
                  color: BColors.textPrimary,
                )),
                Text(widget.hint, style: const TextStyle(
                  fontFamily: 'Onest', fontSize: 11, color: BColors.textMuted,
                )),
              ],
            )),
          ]),
        ),
      ),
    );
  }
}
