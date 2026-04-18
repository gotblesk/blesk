import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:solar_icons/solar_icons.dart';

import '../shared/theme.dart';
import 'chat_bubble_parts.dart';
import 'emoji_picker.dart';
import 'forward_modal.dart';

export 'chat_bubble_parts.dart' show
    MessageStatus, MessageType, Reaction, ReplyQuote, LinkPreviewData;

// ═══════════════════════════════════════════════════════════════
// CHAT MESSAGES — list + bubble + hover actions + reactions
// ═══════════════════════════════════════════════════════════════

// ─── MESSAGE MODEL ────────────────────────────────────────────

class MessageData {
  final String id;
  final String? text;
  final String time;
  final DateTime? sentAt; // for date grouping
  final bool own;
  final MessageStatus status;
  final MessageType type;
  final String? senderName;
  final String? senderInitial;
  final List<Reaction> reactions;
  final ReplyQuote? reply;
  final String? forwardFrom;
  final bool edited;
  // Photo/video/gif
  final List<Color>? photoTints; // stubs
  final Duration? videoDuration;
  final Color? gifTint;
  // Voice
  final Duration? voiceDuration;
  final List<double>? waveform;
  final bool voicePlayed;
  // File
  final String? fileName;
  final String? fileSize;
  final String? fileExt;
  // Sticker
  final String? stickerEmoji;
  // Link
  final LinkPreviewData? linkPreview;
  // System
  final String? systemText;

  const MessageData({
    required this.id,
    this.text,
    required this.time,
    this.sentAt,
    required this.own,
    bool? read,
    MessageStatus? status,
    this.type = MessageType.text,
    this.senderName,
    this.senderInitial,
    this.reactions = const [],
    this.reply,
    this.forwardFrom,
    this.edited = false,
    this.photoTints,
    this.videoDuration,
    this.gifTint,
    this.voiceDuration,
    this.waveform,
    this.voicePlayed = false,
    this.fileName,
    this.fileSize,
    this.fileExt,
    this.stickerEmoji,
    this.linkPreview,
    this.systemText,
  }) : status = status ?? (read == true
            ? MessageStatus.read
            : (own ? MessageStatus.delivered : MessageStatus.delivered));

  MessageData copyWith({
    MessageStatus? status,
    List<Reaction>? reactions,
    String? text,
    bool? edited,
  }) =>
      MessageData(
        id: id, text: text ?? this.text, time: time, sentAt: sentAt, own: own,
        status: status ?? this.status, type: type,
        senderName: senderName, senderInitial: senderInitial,
        reactions: reactions ?? this.reactions,
        reply: reply, forwardFrom: forwardFrom, edited: edited ?? this.edited,
        photoTints: photoTints, videoDuration: videoDuration,
        gifTint: gifTint, voiceDuration: voiceDuration,
        waveform: waveform, voicePlayed: voicePlayed,
        fileName: fileName, fileSize: fileSize, fileExt: fileExt,
        stickerEmoji: stickerEmoji, linkPreview: linkPreview,
        systemText: systemText,
      );
}

// ─── STUB DATA ────────────────────────────────────────────────

final Map<String, List<MessageData>> stubMessages = {
  'c1': [
    MessageData(
      id: '0', type: MessageType.system,
      time: '', own: false,
      systemText: 'сообщения в этом чате защищены сквозным шифрованием',
    ),
    MessageData(id: '1', text: 'привет!', time: '14:28', own: false),
    MessageData(id: '2', text: 'как дела?', time: '14:30', own: false),
    MessageData(
      id: '3', text: 'привет! всё хорошо, работаю над blesk',
      time: '14:31', own: true, read: true,
      reactions: const [Reaction('❤️', 1, mine: false)],
    ),
    MessageData(
      id: '4', text: 'круто! покажешь потом?', time: '14:32', own: false,
      reply: const ReplyQuote(
        id: '3', senderName: 'ты',
        text: 'всё хорошо, работаю над blesk',
        senderColor: BColors.accent,
      ),
    ),
    MessageData(
      id: '5', type: MessageType.photo,
      photoTints: const [Color(0xFFf87070), Color(0xFF5b8fff), Color(0xFFffb25c)],
      text: 'вот скриншоты',
      time: '14:32', own: true, read: true,
    ),
    MessageData(id: '6', text: 'огонь! 🔥', time: '14:33', own: false,
      reactions: const [Reaction('🔥', 2, mine: true), Reaction('👍', 1)]),
    MessageData(
      id: '7', type: MessageType.voice,
      voiceDuration: const Duration(seconds: 12),
      waveform: const [0.3, 0.6, 0.9, 0.5, 0.7, 1.0, 0.8, 0.4, 0.6,
        0.9, 0.7, 0.3, 0.5, 0.8, 1.0, 0.6, 0.4, 0.7, 0.9, 0.5, 0.3, 0.6],
      time: '14:35', own: false,
    ),
    MessageData(
      id: '8', type: MessageType.file,
      fileName: 'презентация-blesk.pdf',
      fileSize: '2.4 МБ', fileExt: 'pdf',
      time: '14:36', own: true, read: true,
    ),
    MessageData(
      id: '9', type: MessageType.link,
      text: 'посмотри сайт',
      linkPreview: const LinkPreviewData(
        url: 'https://blesk.fun',
        title: 'blesk — твой блеск. твои правила.',
        description: 'приватный десктопный мессенджер',
        domain: 'blesk.fun',
        tint: Color(0xFFc8ff00),
      ),
      time: '14:38', own: false,
    ),
  ],
  'c2': [
    MessageData(
      id: '0', type: MessageType.system,
      time: '', own: false,
      systemText: 'Артём создал группу «Дизайн-банда»',
    ),
    MessageData(id: '1', text: 'ребят, скинул макеты', time: '13:05', own: false,
      senderName: 'Артём', senderInitial: 'А'),
    MessageData(id: '2', text: 'смотрите в фигме', time: '13:05', own: false,
      senderName: 'Артём', senderInitial: 'А'),
    MessageData(
      id: '3', type: MessageType.photo,
      photoTints: const [
        Color(0xFFb490ff), Color(0xFFff90d4),
        Color(0xFF90d4ff), Color(0xFF90ffa0),
        Color(0xFFffd490), Color(0xFFff9090),
      ],
      time: '13:06', own: false, senderName: 'Артём', senderInitial: 'А',
    ),
    MessageData(id: '4', text: 'огонь!', time: '13:08', own: true, read: true,
      reactions: const [Reaction('❤️', 1), Reaction('🔥', 2, mine: true)]),
    MessageData(id: '5', text: 'скинул макеты в фигму', time: '13:10', own: false,
      senderName: 'Катя', senderInitial: 'К'),
    MessageData(
      id: '6', type: MessageType.sticker,
      stickerEmoji: '🎉', time: '13:11', own: false,
      senderName: 'Катя', senderInitial: 'К',
    ),
  ],
  'c3': [
    MessageData(id: '1', text: 'завтра созвон в 10?', time: '12:40', own: false),
    MessageData(id: '2', text: 'да, буду', time: '12:42', own: true, read: true),
    MessageData(id: '3', text: 'отлично', time: '12:45', own: false),
    MessageData(
      id: '4', type: MessageType.system,
      time: '', own: false,
      systemText: 'пропущенный звонок · 12:50',
    ),
  ],
  'c4': [
    MessageData(id: '1', text: 'новый билд готов', time: 'вчера', own: false,
      senderName: 'bot', senderInitial: 'B'),
    MessageData(id: '2', text: 'v1.0.7-beta запушен', time: 'вчера', own: false,
      senderName: 'bot', senderInitial: 'B'),
    MessageData(id: '3', text: 'тестирую', time: 'вчера', own: true,
      status: MessageStatus.error),
  ],
  'c5': [
    MessageData(id: '1', text: 'увидимся вечером?', time: 'вчера', own: true, read: true),
    MessageData(id: '2', text: 'ок', time: 'вчера', own: false),
  ],
  'c6': [
    MessageData(id: '1', text: 'увидимся!', time: 'пн', own: false),
    MessageData(
      id: '2', type: MessageType.gif,
      gifTint: const Color(0xFF9b8bff),
      time: 'пн', own: false,
    ),
    MessageData(id: '3', text: 'пока!', time: 'пн', own: true, read: true),
  ],
};

// Pinned messages per chat (stub)
final Map<String, List<({String sender, String text})>> stubPinned = {
  'c2': [
    (sender: 'Катя', text: 'завтра созвон в 11'),
    (sender: 'Артём', text: 'не забудьте про дедлайн'),
  ],
};

// Users currently typing per chat (stub). Empty list = nobody typing.
final Map<String, List<({String name, String initial})>> stubTyping = {
  'c1': [(name: 'Катя', initial: 'К')],
  'c2': [(name: 'Артём', initial: 'А'), (name: 'Лиза', initial: 'Л')],
};

// Draft text per chatId — persists between chat switches.
final Map<String, String> stubDrafts = {};

/// Bump this to notify listeners (sidebar) that drafts changed.
final ValueNotifier<int> draftsVersion = ValueNotifier(0);

void updateDraft(String chatId, String text) {
  if (text.isEmpty) {
    if (stubDrafts.remove(chatId) != null) draftsVersion.value++;
  } else if (stubDrafts[chatId] != text) {
    stubDrafts[chatId] = text;
    draftsVersion.value++;
  }
}

void clearDraft(String chatId) {
  if (stubDrafts.remove(chatId) != null) draftsVersion.value++;
}

/// Chats user has manually marked as unread. Cleared on chat open.
final Set<String> manuallyUnread = {};
final ValueNotifier<int> unreadStateVersion = ValueNotifier(0);

void markChatAsUnread(String chatId) {
  if (manuallyUnread.add(chatId)) unreadStateVersion.value++;
}
void markChatAsRead(String chatId) {
  if (manuallyUnread.remove(chatId)) unreadStateVersion.value++;
}

/// Unseen mentions of current user per chat (stub).
final Map<String, int> stubMentionCounts = {
  'c2': 2, // user was mentioned 2 times in group
};

/// New reactions on user's own messages since last view (stub).
final Map<String, int> stubNewReactionCounts = {
  'c1': 3,
  'c2': 1,
};

final ValueNotifier<int> counterStateVersion = ValueNotifier(0);

void consumeMentionCounter(String chatId) {
  if (stubMentionCounts.containsKey(chatId)) {
    stubMentionCounts.remove(chatId);
    counterStateVersion.value++;
  }
}

void consumeReactionCounter(String chatId) {
  if (stubNewReactionCounts.containsKey(chatId)) {
    stubNewReactionCounts.remove(chatId);
    counterStateVersion.value++;
  }
}

// ─── GROUP POSITION ──────────────────────────────────────────

enum _GroupPos { single, first, middle, last }

// ─── MESSAGE LIST (main entry) ───────────────────────────────

class ChatMessages extends StatefulWidget {
  final String chatId;
  final ValueChanged<ReplyQuote>? onReply;
  final ValueChanged<String>? onEditStart;
  final ValueChanged<MessageData>? onOpenMedia;
  final String? highlightQuery;
  final int? currentMatchId; // hash of current match (msgIndex * 1000 + occurrence)
  final ValueChanged<int>? onMatchCountChanged;

  const ChatMessages({
    super.key, required this.chatId,
    this.onReply, this.onEditStart, this.onOpenMedia,
    this.highlightQuery, this.currentMatchId, this.onMatchCountChanged,
  });

  @override
  State<ChatMessages> createState() => _ChatMessagesState();
}

class _ChatMessagesState extends State<ChatMessages> {
  final _scroll = ScrollController();
  bool _showScrollBtn = false;
  int _unreadNew = 0;
  String? _editingMessageId;
  OverlayEntry? _undoToastEntry;

  void _startEdit(String id) => setState(() => _editingMessageId = id);
  void _cancelEdit() => setState(() => _editingMessageId = null);

  void _saveEdit(String id, String newText) {
    final msgs = stubMessages[widget.chatId];
    if (msgs == null) return;
    final idx = msgs.indexWhere((m) => m.id == id);
    if (idx < 0) return;
    setState(() {
      msgs[idx] = msgs[idx].copyWith(text: newText, edited: true);
      _editingMessageId = null;
    });
  }

  void _forwardMessage(MessageData msg) {
    showForwardModal(
      context,
      source: msg,
      sourceChatId: widget.chatId,
      sourceSenderName: msg.own
          ? 'ты'
          : (msg.senderName ?? _resolveChatName(widget.chatId)),
    );
  }

  String _resolveChatName(String chatId) {
    return switch (chatId) {
      'c1' => 'Катя',
      'c2' => 'Дизайн-банда',
      'c3' => 'Максим',
      'c4' => 'blesk team',
      'c5' => 'Аня',
      'c6' => 'Лиза',
      _ => 'собеседник',
    };
  }

  void _deleteMessage(MessageData msg) {
    final modalCtx = context;
    late OverlayEntry modal;
    modal = OverlayEntry(builder: (_) => _DeleteMessageModal(
      msg: msg,
      canDeleteForAll: msg.own,
      onConfirm: (forAll) {
        modal.remove();
        _performDelete(msg);
      },
      onClose: () => modal.remove(),
    ));
    Overlay.of(modalCtx).insert(modal);
  }

  void _performDelete(MessageData msg) {
    final msgs = stubMessages[widget.chatId];
    if (msgs == null) return;
    final idx = msgs.indexWhere((m) => m.id == msg.id);
    if (idx < 0) return;

    // Soft-remove: keep a copy for undo
    final removed = msgs.removeAt(idx);
    setState(() {});

    _undoToastEntry?.remove();
    final overlay = Overlay.of(context);
    bool restored = false;
    late OverlayEntry toast;
    toast = OverlayEntry(builder: (_) => _UndoToast(
      onUndo: () {
        if (restored) return;
        restored = true;
        msgs.insert(idx.clamp(0, msgs.length), removed);
        setState(() {});
        toast.remove();
        _undoToastEntry = null;
      },
      onDismiss: () {
        if (_undoToastEntry == toast) _undoToastEntry = null;
        toast.remove();
      },
    ));
    _undoToastEntry = toast;
    overlay.insert(toast);
  }

  @override
  void initState() {
    super.initState();
    _scroll.addListener(_onScroll);
  }

  @override
  void dispose() {
    _scroll.removeListener(_onScroll);
    _scroll.dispose();
    _undoToastEntry?.remove();
    _undoToastEntry = null;
    super.dispose();
  }

  void _onScroll() {
    // Reverse list: offset 0 = bottom
    final pastThreshold = _scroll.offset > 500;
    if (pastThreshold != _showScrollBtn) {
      setState(() => _showScrollBtn = pastThreshold);
    }
  }

  void _scrollToBottom() {
    _scroll.animateTo(0,
      duration: const Duration(milliseconds: 400),
      curve: Curves.easeOutCubic);
    setState(() => _unreadNew = 0);
  }

  void _toggleReaction(MessageData msg, String emoji) {
    final msgs = stubMessages[widget.chatId];
    if (msgs == null) return;
    final idx = msgs.indexWhere((m) => m.id == msg.id);
    if (idx < 0) return;

    final existing = msg.reactions.indexWhere((r) => r.emoji == emoji);
    final updated = [...msg.reactions];
    if (existing >= 0) {
      final r = updated[existing];
      if (r.mine) {
        // Remove mine
        if (r.count <= 1) {
          updated.removeAt(existing);
        } else {
          updated[existing] = r.copyWith(count: r.count - 1, mine: false);
        }
      } else {
        updated[existing] = r.copyWith(count: r.count + 1, mine: true);
      }
    } else {
      updated.add(Reaction(emoji, 1, mine: true));
    }
    setState(() {
      msgs[idx] = msg.copyWith(reactions: updated);
    });
  }

  // Build items list: messages + date separators + "new" divider
  List<Widget> _buildItems(List<MessageData> messages) {
    final items = <Widget>[];
    String? lastDateLabel;

    for (var i = 0; i < messages.length; i++) {
      final msg = messages[i];
      final prev = i > 0 ? messages[i - 1] : null;
      final next = i < messages.length - 1 ? messages[i + 1] : null;

      // Date separator (stub: group by .time field when it changes meaningfully)
      final dayLabel = _dayLabelFor(msg.time);
      if (dayLabel != null && dayLabel != lastDateLabel && msg.type != MessageType.system) {
        items.add(DateSeparator(label: dayLabel));
        lastDateLabel = dayLabel;
      }

      // System messages: centered, no bubble
      if (msg.type == MessageType.system) {
        items.add(SystemMessage(text: msg.systemText ?? msg.text ?? ''));
      continue;
      }

      // Group position: consecutive messages from same sender within time window
      final sameAsPrev = prev != null &&
          prev.own == msg.own &&
          prev.senderName == msg.senderName &&
          prev.type != MessageType.system;
      final sameAsNext = next != null &&
          next.own == msg.own &&
          next.senderName == msg.senderName &&
          next.type != MessageType.system;
      final pos = !sameAsPrev && !sameAsNext
          ? _GroupPos.single
          : !sameAsPrev && sameAsNext
              ? _GroupPos.first
              : sameAsPrev && sameAsNext
                  ? _GroupPos.middle
                  : _GroupPos.last;

      items.add(_MessageBubble(
        msg: msg,
        pos: pos,
        highlightQuery: widget.highlightQuery,
        isCurrentMatch: widget.currentMatchId != null &&
            widget.currentMatchId == msg.id.hashCode,
        isEditing: _editingMessageId == msg.id,
        onDelete: () => _deleteMessage(msg),
        onForward: () => _forwardMessage(msg),
        onReact: (emoji) => _toggleReaction(msg, emoji),
        onReply: widget.onReply == null ? null : () {
          widget.onReply!(ReplyQuote(
            id: msg.id,
            senderName: msg.own ? 'ты' : (msg.senderName ?? 'собеседник'),
            text: msg.text ?? _previewForType(msg),
            senderColor: msg.own ? BColors.accent : senderColorFor(msg.senderName ?? 'x'),
            hasPhoto: msg.type == MessageType.photo,
          ));
        },
        onEdit: (msg.own && msg.text != null && msg.type == MessageType.text)
            ? () => _startEdit(msg.id) : null,
        onSaveEdit: (newText) => _saveEdit(msg.id, newText),
        onCancelEdit: _cancelEdit,
        onOpenMedia: widget.onOpenMedia == null ? null : () => widget.onOpenMedia!(msg),
      ));
    }

    return items;
  }

  String? _dayLabelFor(String time) {
    // Simple heuristic: map time string to day label
    if (time.contains(':')) return 'сегодня';
    if (time == 'вчера') return 'вчера';
    if (time == 'пн' || time == 'вт' || time == 'ср' ||
        time == 'чт' || time == 'пт' || time == 'сб' || time == 'вс') {
      return time;
    }
    return null;
  }

  String _previewForType(MessageData m) => switch (m.type) {
    MessageType.photo => '📷 фото',
    MessageType.video => '🎞 видео',
    MessageType.voice => '🎤 голосовое',
    MessageType.file => '📄 ${m.fileName ?? "файл"}',
    MessageType.sticker => '${m.stickerEmoji ?? "🎉"} стикер',
    MessageType.gif => 'GIF',
    MessageType.link => m.linkPreview?.url ?? 'ссылка',
    _ => '',
  };

  @override
  Widget build(BuildContext context) {
    final messages = stubMessages[widget.chatId] ?? [];
    final pinned = stubPinned[widget.chatId] ?? const [];
    final typing = stubTyping[widget.chatId] ?? const [];
    final isGroup = messages.any((m) => m.senderName != null);
    final items = _buildItems(messages);
    // Append typing widget(s) at the end (bottom of chat when reversed).
    if (typing.isNotEmpty) {
      if (isGroup) {
        items.add(TypingStack(users: typing));
      } else {
        items.add(TypingBubble(
          senderName: typing.first.name,
          senderInitial: typing.first.initial,
        ));
      }
    }

    return Column(children: [
      if (pinned.isNotEmpty)
        PinnedMessagesBar(pinned: pinned, onTap: () {}),
      Expanded(child: Stack(children: [
        ListView.builder(
          controller: _scroll,
          reverse: true,
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
          itemCount: items.length,
          itemBuilder: (_, i) {
            final idx = items.length - 1 - i;
            final w = items[idx];
            // Only animate the last 6 new items (near bottom)
            if (i < 6) {
              return w.animate(delay: Duration(milliseconds: 20 * i))
                  .fadeIn(duration: 180.ms)
                  .slideY(begin: 0.04, duration: 200.ms, curve: Curves.easeOut);
            }
            return w;
          },
        ),
        if (_showScrollBtn)
          Positioned(
            right: 16, bottom: 12,
            child: ScrollToBottomBtn(
              unread: _unreadNew,
              onTap: _scrollToBottom,
            ),
          ),
      ])),
    ]);
  }
}

// ─── MESSAGE BUBBLE ───────────────────────────────────────────

class _MessageBubble extends StatefulWidget {
  final MessageData msg;
  final _GroupPos pos;
  final String? highlightQuery;
  final bool isCurrentMatch;
  final bool isEditing;
  final ValueChanged<String>? onReact;
  final VoidCallback? onReply;
  final VoidCallback? onEdit;
  final VoidCallback? onDelete;
  final VoidCallback? onForward;
  final ValueChanged<String>? onSaveEdit;
  final VoidCallback? onCancelEdit;
  final VoidCallback? onOpenMedia;

  const _MessageBubble({
    required this.msg, required this.pos,
    this.highlightQuery, this.isCurrentMatch = false, this.isEditing = false,
    this.onReact, this.onReply, this.onEdit, this.onDelete, this.onForward,
    this.onSaveEdit, this.onCancelEdit,
    this.onOpenMedia,
  });

  @override
  State<_MessageBubble> createState() => _MessageBubbleState();
}

class _MessageBubbleState extends State<_MessageBubble> {
  bool _hover = false;
  OverlayEntry? _quickReactEntry;

  @override
  void dispose() {
    _quickReactEntry?.remove();
    super.dispose();
  }

  BorderRadius _radiusFor() {
    final own = widget.msg.own;
    const r = 16.0;
    const t = 4.0; // tail
    final p = widget.pos;
    if (own) {
      // Sender side: right (BR is tail on single/last)
      return switch (p) {
        _GroupPos.single => const BorderRadius.only(
            topLeft: Radius.circular(r), topRight: Radius.circular(r),
            bottomLeft: Radius.circular(r), bottomRight: Radius.circular(t),
          ),
        _GroupPos.first => const BorderRadius.only(
            topLeft: Radius.circular(r), topRight: Radius.circular(r),
            bottomLeft: Radius.circular(r), bottomRight: Radius.circular(t),
          ),
        _GroupPos.middle => const BorderRadius.only(
            topLeft: Radius.circular(r), topRight: Radius.circular(t),
            bottomLeft: Radius.circular(r), bottomRight: Radius.circular(t),
          ),
        _GroupPos.last => const BorderRadius.only(
            topLeft: Radius.circular(r), topRight: Radius.circular(t),
            bottomLeft: Radius.circular(r), bottomRight: Radius.circular(t),
          ),
      };
    } else {
      return switch (p) {
        _GroupPos.single => const BorderRadius.only(
            topLeft: Radius.circular(r), topRight: Radius.circular(r),
            bottomLeft: Radius.circular(t), bottomRight: Radius.circular(r),
          ),
        _GroupPos.first => const BorderRadius.only(
            topLeft: Radius.circular(r), topRight: Radius.circular(r),
            bottomLeft: Radius.circular(t), bottomRight: Radius.circular(r),
          ),
        _GroupPos.middle => const BorderRadius.only(
            topLeft: Radius.circular(t), topRight: Radius.circular(r),
            bottomLeft: Radius.circular(t), bottomRight: Radius.circular(r),
          ),
        _GroupPos.last => const BorderRadius.only(
            topLeft: Radius.circular(t), topRight: Radius.circular(r),
            bottomLeft: Radius.circular(t), bottomRight: Radius.circular(r),
          ),
      };
    }
  }

  void _showQuickReactPopup(Offset anchor) {
    _quickReactEntry?.remove();
    final overlay = Overlay.of(context);
    late OverlayEntry entry;
    entry = OverlayEntry(builder: (_) => _QuickReactPopup(
      anchor: anchor,
      own: widget.msg.own,
      onSelect: (emoji) {
        entry.remove();
        _quickReactEntry = null;
        widget.onReact?.call(emoji);
      },
      onOpenFull: () {
        entry.remove();
        _quickReactEntry = null;
        _showFullEmojiPicker();
      },
      onClose: () {
        entry.remove();
        _quickReactEntry = null;
      },
    ));
    _quickReactEntry = entry;
    overlay.insert(entry);
  }

  void _showFullEmojiPicker() {
    final overlay = Overlay.of(context);
    late OverlayEntry entry;
    entry = OverlayEntry(builder: (_) => EmojiPicker(
      onSelect: (emoji) {
        entry.remove();
        widget.onReact?.call(emoji);
      },
      onClose: () => entry.remove(),
    ));
    overlay.insert(entry);
  }

  void _showContextMenu(Offset pos) {
    final overlay = Overlay.of(context);
    late OverlayEntry entry;
    final isOwn = widget.msg.own;
    final hasText = widget.msg.text != null && widget.msg.text!.isNotEmpty;
    entry = OverlayEntry(builder: (_) => _BubbleContextMenu(
      position: pos,
      own: isOwn,
      items: [
        _CtxItem('ответить', SolarIconsOutline.reply, 'reply'),
        if (hasText) _CtxItem('копировать', SolarIconsOutline.copy, 'copy'),
        _CtxItem('переслать', SolarIconsOutline.forward, 'forward'),
        _CtxItem('закрепить', SolarIconsOutline.pin, 'pin'),
        if (isOwn && hasText) _CtxItem('редактировать', SolarIconsOutline.pen, 'edit'),
        if (isOwn) _CtxItem('удалить', SolarIconsOutline.trashBinTrash, 'delete', danger: true),
      ],
      onSelect: (id) {
        entry.remove();
        if (id == 'reply') widget.onReply?.call();
        if (id == 'edit') widget.onEdit?.call();
        if (id == 'delete') widget.onDelete?.call();
        if (id == 'forward') widget.onForward?.call();
        if (id == 'copy' && widget.msg.text != null) {
          Clipboard.setData(ClipboardData(text: widget.msg.text!));
        }
      },
      onClose: () => entry.remove(),
    ));
    overlay.insert(entry);
  }

  @override
  Widget build(BuildContext context) {
    final msg = widget.msg;
    final own = msg.own;
    final p = widget.pos;
    final isLastInGroup = p == _GroupPos.last || p == _GroupPos.single;
    final isFirstInGroup = p == _GroupPos.first || p == _GroupPos.single;
    final showSenderName = !own &&
        msg.senderName != null &&
        isFirstInGroup;
    final showAvatar = !own &&
        msg.senderName != null &&
        isLastInGroup;

    // Gap: 2px within group, 6px between groups
    final topGap = (p == _GroupPos.single || p == _GroupPos.first) ? 6.0 : 2.0;

    // Sticker/GIF: no bubble background
    final noBubbleBg = msg.type == MessageType.sticker;

    return Padding(
      padding: EdgeInsets.only(top: topGap),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.end,
        mainAxisAlignment: own ? MainAxisAlignment.end : MainAxisAlignment.start,
        children: [
          // Left avatar slot (for group incoming)
          if (!own && msg.senderName != null) SizedBox(
            width: 36,
            child: showAvatar
                ? Padding(
                    padding: const EdgeInsets.only(right: 8, bottom: 2),
                    child: GroupAvatar(
                      initial: msg.senderInitial ?? msg.senderName![0].toUpperCase(),
                      tint: senderColorFor(msg.senderName!),
                    ),
                  )
                : null, // indent reserved
          ),

          // Bubble column (name + bubble + hover actions)
          Flexible(child: _buildBubbleColumn(
            context, showSenderName: showSenderName, noBubbleBg: noBubbleBg,
          )),
        ],
      ),
    );
  }

  Widget _buildBubbleColumn(BuildContext context, {
    required bool showSenderName,
    required bool noBubbleBg,
  }) {
    final msg = widget.msg;
    final own = msg.own;

    return MouseRegion(
      onEnter: (_) => setState(() => _hover = true),
      onExit: (_) => setState(() => _hover = false),
      child: LayoutBuilder(builder: (ctx, cons) {
        final maxBubble = cons.maxWidth * 0.72;
        return ConstrainedBox(
          constraints: BoxConstraints(maxWidth: maxBubble.clamp(180, 480)),
          child: Column(
            crossAxisAlignment: own ? CrossAxisAlignment.end : CrossAxisAlignment.start,
            children: [
              if (showSenderName) Padding(
                padding: const EdgeInsets.only(left: 14, bottom: 3),
                child: Text(
                  msg.senderName!,
                  style: TextStyle(
                    fontFamily: 'Onest', fontSize: 12, fontWeight: FontWeight.w600,
                    color: senderColorFor(msg.senderName!),
                  ),
                ),
              ),

              // Bubble + hover actions row
              Row(
                mainAxisSize: MainAxisSize.min,
                mainAxisAlignment: own ? MainAxisAlignment.end : MainAxisAlignment.start,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (own) _buildHoverActions(visible: _hover),
                  Flexible(child: _buildBubble(noBubbleBg: noBubbleBg)),
                  if (!own) _buildHoverActions(visible: _hover),
                ],
              ),

              if (msg.reactions.isNotEmpty) Padding(
                padding: EdgeInsets.only(
                  top: 3,
                  left: own ? 0 : 8, right: own ? 8 : 0,
                ),
                child: ReactionsRow(
                  reactions: msg.reactions,
                  onToggle: (emoji) => widget.onReact?.call(emoji),
                ),
              ),
            ],
          ),
        );
      }),
    );
  }

  Widget _buildBubble({required bool noBubbleBg}) {
    final msg = widget.msg;
    final own = msg.own;
    final isSticker = msg.type == MessageType.sticker;
    final isGif = msg.type == MessageType.gif;
    final isPhoto = msg.type == MessageType.photo;
    final isVideo = msg.type == MessageType.video;
    final isError = msg.status == MessageStatus.error;

    if (isSticker) {
      return Column(crossAxisAlignment: CrossAxisAlignment.end, children: [
        GestureDetector(
          onSecondaryTapDown: (d) => _showContextMenu(d.globalPosition),
          child: StickerContent(emoji: msg.stickerEmoji ?? '🎉'),
        ),
        _buildTimeAndStatus(faded: true),
      ]);
    }

    // Normal bubble
    final editing = widget.isEditing;
    final bgColor = noBubbleBg
        ? Colors.transparent
        : editing
            ? BColors.accent.withValues(alpha: 0.1)
            : own
                ? BColors.accent.withValues(alpha: 0.06)
                : const Color(0xFF141418);
    final borderColor = noBubbleBg
        ? Colors.transparent
        : editing
            ? BColors.accent.withValues(alpha: 0.4)
            : isError
                ? const Color(0xFFff5c5c).withValues(alpha: 0.35)
                : own
                    ? BColors.accent.withValues(alpha: 0.1)
                    : Colors.white.withValues(alpha: 0.05);

    // Padding strategy — tighter for media-only
    final mediaOnly = (isPhoto || isVideo || isGif) &&
        (msg.text == null || msg.text!.isEmpty);
    final padding = mediaOnly
        ? const EdgeInsets.all(4)
        : const EdgeInsets.fromLTRB(14, 8, 12, 7);

    return GestureDetector(
      onSecondaryTapDown: (d) => _showContextMenu(d.globalPosition),
      child: Container(
        padding: padding,
        decoration: BoxDecoration(
          color: bgColor,
          borderRadius: _radiusFor(),
          border: Border.all(color: borderColor, width: 1),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (msg.forwardFrom != null) ForwardLabel(senderName: msg.forwardFrom!),
            if (msg.reply != null) ReplyPreview(quote: msg.reply!),
            _buildContent(),
            if (msg.text != null && msg.text!.isNotEmpty &&
                (isPhoto || isVideo || msg.linkPreview != null))
              Padding(
                padding: const EdgeInsets.only(top: 6),
                child: Text(msg.text!, style: const TextStyle(
                  fontFamily: 'Onest', fontSize: 14, color: BColors.textPrimary,
                  height: 1.4,
                )),
              ),
            // Time + status inline for text / bottom-right for media
            if (widget.isEditing)
              const SizedBox.shrink()
            else if (msg.type == MessageType.text ||
                msg.text?.isNotEmpty == true ||
                msg.type == MessageType.file ||
                msg.type == MessageType.voice)
              Padding(
                padding: EdgeInsets.only(top: mediaOnly ? 4 : 2, left: mediaOnly ? 6 : 0),
                child: _buildTimeAndStatus(),
              )
            else
              Padding(
                padding: const EdgeInsets.only(top: 4, left: 4),
                child: _buildTimeAndStatus(),
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildContent() {
    final msg = widget.msg;
    // Inline edit mode replaces text content with an editor
    if (widget.isEditing &&
        msg.type == MessageType.text &&
        widget.onSaveEdit != null) {
      return _InlineEditor(
        initial: msg.text ?? '',
        onSave: widget.onSaveEdit!,
        onCancel: widget.onCancelEdit ?? () {},
      );
    }
    switch (msg.type) {
      case MessageType.text:
        return _textContent();
      case MessageType.photo:
        return GestureDetector(
          onTap: widget.onOpenMedia,
          child: PhotoContent(
            photoTints: msg.photoTints ?? [BColors.accent],
            caption: null, // caption rendered below at bubble level
          ),
        );
      case MessageType.video:
        return GestureDetector(
          onTap: widget.onOpenMedia,
          child: VideoContent(
            tint: msg.photoTints?.first ?? const Color(0xFF5b8fff),
            duration: msg.videoDuration ?? const Duration(seconds: 30),
            caption: null,
          ),
        );
      case MessageType.voice:
        return VoiceContent(
          duration: msg.voiceDuration ?? const Duration(seconds: 10),
          waveform: msg.waveform ?? List.generate(22, (i) =>
              0.3 + 0.7 * ((i * 37) % 100) / 100),
          own: msg.own,
          played: msg.voicePlayed,
        );
      case MessageType.file:
        return FileContent(
          fileName: msg.fileName ?? 'файл',
          fileSize: msg.fileSize ?? '0 КБ',
          ext: msg.fileExt ?? 'file',
        );
      case MessageType.sticker:
        return StickerContent(emoji: msg.stickerEmoji ?? '🎉');
      case MessageType.gif:
        return GestureDetector(
          onTap: widget.onOpenMedia,
          child: GifContent(tint: msg.gifTint ?? BColors.accent),
        );
      case MessageType.link:
        return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          if (msg.text != null && msg.text!.isNotEmpty) _textContent(),
          if (msg.linkPreview != null) Padding(
            padding: EdgeInsets.only(top: msg.text != null ? 6 : 0),
            child: LinkPreview(data: msg.linkPreview!),
          ),
        ]);
      case MessageType.system:
        return const SizedBox.shrink();
      case MessageType.forwarded:
        return _textContent();
    }
  }

  Widget _textContent() {
    final msg = widget.msg;
    final style = TextStyle(
      fontFamily: 'Onest', fontSize: rf(context, 14), fontWeight: FontWeight.w400,
      color: BColors.textPrimary, height: 1.4,
    );
    final query = widget.highlightQuery;
    if (query != null && query.trim().isNotEmpty && (msg.text?.isNotEmpty ?? false)) {
      return HighlightedText(
        text: msg.text!, highlight: query, style: style,
        currentMatchIndex: widget.isCurrentMatch ? 0 : null,
      );
    }
    return Text(msg.text ?? '', style: style);
  }

  Widget _buildTimeAndStatus({bool faded = false}) {
    final msg = widget.msg;
    return Row(mainAxisSize: MainAxisSize.min, children: [
      if (msg.edited) ...[
        Text('изм.', style: TextStyle(
          fontFamily: 'Onest', fontSize: 10,
          color: BColors.textMuted.withValues(alpha: faded ? 0.5 : 0.7),
          fontStyle: FontStyle.italic,
        )),
        const SizedBox(width: 5),
      ],
      Text(
        msg.time,
        style: TextStyle(
          fontFamily: 'Onest', fontSize: 11,
          color: BColors.textMuted.withValues(alpha: faded ? 0.6 : 0.85),
        ),
      ),
      if (msg.own) ...[
        const SizedBox(width: 4),
        ReadStatusIcon(msg.status),
      ],
    ]);
  }

  Widget _buildHoverActions({required bool visible}) {
    return AnimatedOpacity(
      duration: const Duration(milliseconds: 150),
      opacity: visible ? 1 : 0,
      child: IgnorePointer(
        ignoring: !visible,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 6),
          child: _HoverActions(
            onReact: (anchor) => _showQuickReactPopup(anchor),
            onReply: widget.onReply ?? () {},
            onMore: (anchor) => _showContextMenu(anchor),
          ),
        ),
      ),
    );
  }
}

// ─── HOVER ACTIONS ROW ────────────────────────────────────────

class _HoverActions extends StatelessWidget {
  final ValueChanged<Offset> onReact;
  final VoidCallback onReply;
  final ValueChanged<Offset> onMore;
  const _HoverActions({
    required this.onReact, required this.onReply, required this.onMore,
  });

  @override
  Widget build(BuildContext context) {
    return Row(mainAxisSize: MainAxisSize.min, children: [
      _HoverBtn(
        icon: SolarIconsOutline.smileCircle, tooltip: 'реакция',
        onTap: (anchor) => onReact(anchor),
      ),
      const SizedBox(width: 2),
      _HoverBtn(
        icon: SolarIconsOutline.reply, tooltip: 'ответить',
        onTap: (_) => onReply(),
      ),
      const SizedBox(width: 2),
      _HoverBtn(
        icon: SolarIconsOutline.menuDots, tooltip: 'ещё',
        onTap: (anchor) => onMore(anchor),
      ),
    ]);
  }
}

class _HoverBtn extends StatefulWidget {
  final IconData icon;
  final String tooltip;
  final ValueChanged<Offset> onTap;
  const _HoverBtn({required this.icon, required this.tooltip, required this.onTap});
  @override
  State<_HoverBtn> createState() => _HoverBtnState();
}

class _HoverBtnState extends State<_HoverBtn> {
  bool _h = false;
  final _key = GlobalKey();
  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: widget.tooltip,
      textStyle: const TextStyle(fontSize: 10, color: BColors.textPrimary),
      decoration: BoxDecoration(
        color: const Color(0xFF1a1a1a), borderRadius: BorderRadius.circular(6),
      ),
      child: MouseRegion(
        cursor: SystemMouseCursors.click,
        onEnter: (_) => setState(() => _h = true),
        onExit: (_) => setState(() => _h = false),
        child: GestureDetector(
          key: _key,
          onTap: () {
            final box = _key.currentContext?.findRenderObject() as RenderBox?;
            final anchor = box != null
                ? box.localToGlobal(box.size.center(Offset.zero))
                : Offset.zero;
            widget.onTap(anchor);
          },
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 120),
            width: 26, height: 26,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(6),
              color: _h ? Colors.white.withValues(alpha: 0.08) : Colors.white.withValues(alpha: 0.035),
              border: Border.all(
                color: Colors.white.withValues(alpha: _h ? 0.12 : 0.06), width: 0.5,
              ),
            ),
            child: Center(child: Icon(
              widget.icon, size: 14,
              color: _h ? BColors.textPrimary : BColors.textSecondary,
            )),
          ),
        ),
      ),
    );
  }
}

// ─── QUICK REACTION POPUP ─────────────────────────────────────

class _QuickReactPopup extends StatelessWidget {
  final Offset anchor;
  final bool own;
  final ValueChanged<String> onSelect;
  final VoidCallback onOpenFull;
  final VoidCallback onClose;
  const _QuickReactPopup({
    required this.anchor, required this.own,
    required this.onSelect, required this.onOpenFull, required this.onClose,
  });

  static const _quick = ['❤️', '😂', '👍', '😮', '😢', '🔥'];

  @override
  Widget build(BuildContext context) {
    final screen = MediaQuery.of(context).size;
    const popW = 258.0;
    final left = (anchor.dx - popW / 2).clamp(12.0, screen.width - popW - 12);
    final top = (anchor.dy - 56).clamp(44.0, screen.height - 56);

    return Stack(children: [
      Positioned.fill(child: GestureDetector(
        onTap: onClose,
        behavior: HitTestBehavior.translucent,
        child: const SizedBox.expand(),
      )),
      Positioned(
        left: left, top: top,
        child: Material(
          color: Colors.transparent,
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 4),
            decoration: BoxDecoration(
              color: const Color(0xF5141418),
              borderRadius: BorderRadius.circular(22),
              border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
              boxShadow: const [BoxShadow(
                color: Color(0x99000000), blurRadius: 24, offset: Offset(0, 6),
              )],
            ),
            child: Row(mainAxisSize: MainAxisSize.min, children: [
              for (final e in _quick) _QuickEmoji(emoji: e, onTap: () => onSelect(e)),
              const SizedBox(width: 4),
              _QuickMore(onTap: onOpenFull),
            ]),
          ).animate()
              .scale(begin: const Offset(0.85, 0.85),
                  duration: 160.ms, curve: Curves.easeOutBack)
              .fade(duration: 120.ms),
        ),
      ),
    ]);
  }
}

class _QuickEmoji extends StatefulWidget {
  final String emoji;
  final VoidCallback onTap;
  const _QuickEmoji({required this.emoji, required this.onTap});
  @override
  State<_QuickEmoji> createState() => _QuickEmojiState();
}

class _QuickEmojiState extends State<_QuickEmoji> {
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
          duration: const Duration(milliseconds: 120),
          width: 32, height: 32,
          margin: const EdgeInsets.symmetric(horizontal: 1),
          transform: Matrix4.identity()..scaleByDouble(_h ? 1.2 : 1.0, _h ? 1.2 : 1.0, 1, 1),
          transformAlignment: Alignment.center,
          child: Center(child: Text(widget.emoji, style: const TextStyle(fontSize: 22))),
        ),
      ),
    );
  }
}

class _QuickMore extends StatefulWidget {
  final VoidCallback onTap;
  const _QuickMore({required this.onTap});
  @override
  State<_QuickMore> createState() => _QuickMoreState();
}

class _QuickMoreState extends State<_QuickMore> {
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
          duration: const Duration(milliseconds: 120),
          width: 28, height: 28,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: _h ? Colors.white.withValues(alpha: 0.08) : Colors.white.withValues(alpha: 0.04),
          ),
          child: Icon(SolarIconsOutline.addCircle, size: 16,
              color: _h ? BColors.textPrimary : BColors.textMuted),
        ),
      ),
    );
  }
}

// ─── REACTIONS ROW (pills under bubble) ───────────────────────

class ReactionsRow extends StatelessWidget {
  final List<Reaction> reactions;
  final ValueChanged<String> onToggle;
  const ReactionsRow({super.key, required this.reactions, required this.onToggle});

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: 4, runSpacing: 4,
      children: [
        for (final r in reactions)
          _ReactionPill(reaction: r, onTap: () => onToggle(r.emoji)),
      ],
    );
  }
}

class _ReactionPill extends StatefulWidget {
  final Reaction reaction;
  final VoidCallback onTap;
  const _ReactionPill({required this.reaction, required this.onTap});
  @override
  State<_ReactionPill> createState() => _ReactionPillState();
}

class _ReactionPillState extends State<_ReactionPill> {
  bool _h = false;
  @override
  Widget build(BuildContext context) {
    final r = widget.reaction;
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setState(() => _h = true),
      onExit: (_) => setState(() => _h = false),
      child: GestureDetector(
        onTap: widget.onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 150),
          height: 24,
          padding: const EdgeInsets.symmetric(horizontal: 8),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(12),
            color: r.mine
                ? BColors.accent.withValues(alpha: _h ? 0.14 : 0.1)
                : Colors.white.withValues(alpha: _h ? 0.06 : 0.04),
            border: Border.all(
              color: r.mine
                  ? BColors.accent.withValues(alpha: 0.4)
                  : Colors.white.withValues(alpha: 0.06),
              width: 0.5,
            ),
          ),
          child: Row(mainAxisSize: MainAxisSize.min, children: [
            Text(r.emoji, style: const TextStyle(fontSize: 13)),
            const SizedBox(width: 4),
            Text(
              '${r.count}',
              style: TextStyle(
                fontFamily: 'Onest', fontSize: 11, fontWeight: FontWeight.w500,
                color: r.mine
                    ? BColors.accent.withValues(alpha: 0.95)
                    : BColors.textSecondary,
              ),
            ),
          ]),
        ),
      ),
    );
  }
}

// ─── CONTEXT MENU FOR BUBBLE ──────────────────────────────────

class _CtxItem {
  final String label;
  final IconData icon;
  final String id;
  final bool danger;
  const _CtxItem(this.label, this.icon, this.id, {this.danger = false});
}

class _BubbleContextMenu extends StatelessWidget {
  final Offset position;
  final bool own;
  final List<_CtxItem> items;
  final ValueChanged<String> onSelect;
  final VoidCallback onClose;
  const _BubbleContextMenu({
    required this.position, required this.own, required this.items,
    required this.onSelect, required this.onClose,
  });

  @override
  Widget build(BuildContext context) {
    final screen = MediaQuery.of(context).size;
    const menuW = 200.0;
    final menuH = items.length * 34.0 + 8;
    final left = (position.dx - menuW / 2).clamp(12.0, screen.width - menuW - 12);
    final top = (position.dy + 8 + menuH < screen.height)
        ? position.dy + 8 : position.dy - menuH - 8;

    return Stack(children: [
      Positioned.fill(child: GestureDetector(
        onTap: onClose,
        behavior: HitTestBehavior.translucent,
        child: const SizedBox.expand(),
      )),
      Positioned(
        left: left, top: top,
        child: Material(
          color: Colors.transparent,
          child: Container(
            width: menuW,
            padding: const EdgeInsets.symmetric(vertical: 4),
            decoration: BoxDecoration(
              color: const Color(0xF5141418),
              borderRadius: BorderRadius.circular(10),
              border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
              boxShadow: const [BoxShadow(
                color: Color(0x99000000), blurRadius: 24, offset: Offset(0, 8),
              )],
            ),
            child: Column(mainAxisSize: MainAxisSize.min, children: [
              for (final it in items)
                _CtxRow(item: it, onTap: () => onSelect(it.id)),
            ]),
          ).animate()
              .scale(begin: const Offset(0.92, 0.92), duration: 140.ms, curve: Curves.easeOutCubic)
              .fade(duration: 120.ms),
        ),
      ),
    ]);
  }
}

class _CtxRow extends StatefulWidget {
  final _CtxItem item;
  final VoidCallback onTap;
  const _CtxRow({required this.item, required this.onTap});
  @override
  State<_CtxRow> createState() => _CtxRowState();
}

class _CtxRowState extends State<_CtxRow> {
  bool _h = false;
  @override
  Widget build(BuildContext context) {
    final it = widget.item;
    final color = it.danger
        ? const Color(0xFFff5c5c)
        : _h ? BColors.textPrimary : BColors.textSecondary;
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setState(() => _h = true),
      onExit: (_) => setState(() => _h = false),
      child: GestureDetector(
        onTap: widget.onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 100),
          height: 30,
          margin: const EdgeInsets.symmetric(horizontal: 4),
          padding: const EdgeInsets.symmetric(horizontal: 10),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(6),
            color: _h
                ? (it.danger
                    ? const Color(0xFFff5c5c).withValues(alpha: 0.08)
                    : Colors.white.withValues(alpha: 0.05))
                : Colors.transparent,
          ),
          child: Row(children: [
            Icon(it.icon, size: 14, color: color),
            const SizedBox(width: 10),
            Text(it.label, style: TextStyle(
              fontFamily: 'Onest', fontSize: 13, fontWeight: FontWeight.w400,
              color: color,
            )),
          ]),
        ),
      ),
    );
  }
}

// ─── INLINE EDITOR (replaces text when editing) ───────────────

class _InlineEditor extends StatefulWidget {
  final String initial;
  final ValueChanged<String> onSave;
  final VoidCallback onCancel;
  const _InlineEditor({
    required this.initial, required this.onSave, required this.onCancel,
  });
  @override
  State<_InlineEditor> createState() => _InlineEditorState();
}

class _InlineEditorState extends State<_InlineEditor> {
  late final TextEditingController _ctrl;
  late final FocusNode _focus;
  late String _initial;

  @override
  void initState() {
    super.initState();
    _initial = widget.initial;
    _ctrl = TextEditingController(text: _initial);
    _ctrl.selection = TextSelection.collapsed(offset: _initial.length);
    _focus = FocusNode();
    WidgetsBinding.instance.addPostFrameCallback((_) => _focus.requestFocus());
  }

  @override
  void dispose() {
    _ctrl.dispose();
    _focus.dispose();
    super.dispose();
  }

  bool get _canSave {
    final t = _ctrl.text.trim();
    return t.isNotEmpty && t != _initial.trim();
  }

  void _handleKey(KeyEvent e) {
    if (e is! KeyDownEvent) return;
    final k = e.logicalKey;
    if (k == LogicalKeyboardKey.escape) {
      widget.onCancel();
    } else if (k == LogicalKeyboardKey.enter &&
        !HardwareKeyboard.instance.isShiftPressed) {
      if (_canSave) widget.onSave(_ctrl.text.trim());
    }
  }

  @override
  Widget build(BuildContext context) {
    return KeyboardListener(
      focusNode: FocusNode(skipTraversal: true),
      onKeyEvent: _handleKey,
      child: ConstrainedBox(
        constraints: const BoxConstraints(minWidth: 220),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            TextField(
              controller: _ctrl,
              focusNode: _focus,
              maxLines: null,
              onChanged: (_) => setState(() {}),
              style: TextStyle(
                fontFamily: 'Onest', fontSize: rf(context, 14),
                fontWeight: FontWeight.w400,
                color: BColors.textPrimary, height: 1.4,
              ),
              cursorColor: BColors.accent,
              decoration: const InputDecoration(
                border: InputBorder.none, isDense: true,
                contentPadding: EdgeInsets.zero,
              ),
            ),
            const SizedBox(height: 8),
            Row(mainAxisSize: MainAxisSize.min, children: [
              _EditActionBtn(
                label: 'отмена', onTap: widget.onCancel,
                accent: false, enabled: true,
              ),
              const SizedBox(width: 12),
              _EditActionBtn(
                label: 'сохранить', onTap: () {
                  if (_canSave) widget.onSave(_ctrl.text.trim());
                },
                accent: true, enabled: _canSave,
              ),
            ]),
          ],
        ),
      ),
    );
  }
}

class _EditActionBtn extends StatefulWidget {
  final String label;
  final bool accent, enabled;
  final VoidCallback onTap;
  const _EditActionBtn({
    required this.label, required this.accent,
    required this.enabled, required this.onTap,
  });
  @override
  State<_EditActionBtn> createState() => _EditActionBtnState();
}

class _EditActionBtnState extends State<_EditActionBtn> {
  bool _h = false;
  @override
  Widget build(BuildContext context) {
    final color = !widget.enabled
        ? BColors.textMuted
        : widget.accent
            ? (_h ? const Color(0xFFd4ff33) : BColors.accent)
            : (_h ? BColors.textPrimary : BColors.textSecondary);
    return Opacity(
      opacity: widget.enabled ? 1.0 : 0.4,
      child: MouseRegion(
        cursor: widget.enabled
            ? SystemMouseCursors.click : SystemMouseCursors.forbidden,
        onEnter: (_) => setState(() => _h = true),
        onExit: (_) => setState(() => _h = false),
        child: GestureDetector(
          onTap: widget.enabled ? widget.onTap : null,
          child: Text(widget.label, style: TextStyle(
            fontFamily: 'Onest',
            fontSize: 12,
            fontWeight: widget.accent ? FontWeight.w600 : FontWeight.w500,
            color: color,
          )),
        ),
      ),
    );
  }
}

// ─── DELETE MESSAGE MODAL ─────────────────────────────────────

class _DeleteMessageModal extends StatefulWidget {
  final MessageData msg;
  final bool canDeleteForAll;
  final ValueChanged<bool> onConfirm; // forAll flag
  final VoidCallback onClose;
  const _DeleteMessageModal({
    required this.msg, required this.canDeleteForAll,
    required this.onConfirm, required this.onClose,
  });
  @override
  State<_DeleteMessageModal> createState() => _DeleteMessageModalState();
}

class _DeleteMessageModalState extends State<_DeleteMessageModal> {
  bool _forAll = false;

  @override
  void initState() {
    super.initState();
    _forAll = widget.canDeleteForAll;
  }

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: KeyboardListener(
        focusNode: FocusNode()..requestFocus(),
        onKeyEvent: (e) {
          if (e is KeyDownEvent && e.logicalKey == LogicalKeyboardKey.escape) {
            widget.onClose();
          }
        },
        child: Stack(children: [
          Positioned.fill(child: GestureDetector(
            onTap: widget.onClose,
            behavior: HitTestBehavior.opaque,
            child: Container(color: Colors.black.withValues(alpha: 0.4)),
          )),
          Center(child: Container(
            width: 400,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(16),
              color: const Color(0xF50e0e12),
              border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
              boxShadow: const [BoxShadow(
                color: Color(0xA6000000), blurRadius: 48, offset: Offset(0, 16),
              )],
            ),
            child: Column(mainAxisSize: MainAxisSize.min, children: [
              // Header
              Container(
                height: 48,
                padding: const EdgeInsets.fromLTRB(20, 0, 12, 0),
                decoration: const BoxDecoration(
                  border: Border(bottom: BorderSide(color: BColors.borderLow)),
                ),
                child: Row(children: [
                  const Expanded(child: Text(
                    'удалить сообщение?',
                    style: TextStyle(
                      fontFamily: 'Nekst', fontSize: 14, fontWeight: FontWeight.w600,
                      color: BColors.textPrimary,
                    ),
                  )),
                  _ModalCloseBtn(onTap: widget.onClose),
                ]),
              ),
              // Body
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 16, 20, 16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    // Preview
                    Container(
                      padding: const EdgeInsets.fromLTRB(12, 8, 12, 8),
                      decoration: BoxDecoration(
                        borderRadius: BorderRadius.circular(10),
                        color: Colors.white.withValues(alpha: 0.03),
                        border: Border.all(color: Colors.white.withValues(alpha: 0.05), width: 0.5),
                      ),
                      child: Opacity(opacity: 0.72, child: Text(
                        _previewText(widget.msg),
                        maxLines: 3, overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          fontFamily: 'Onest', fontSize: 13,
                          color: BColors.textPrimary, height: 1.4,
                        ),
                      )),
                    ),
                    const SizedBox(height: 14),
                    _DeleteRadio(
                      selected: !_forAll, title: 'удалить у себя',
                      subtitle: 'будет скрыто только у тебя',
                      onTap: () => setState(() => _forAll = false),
                    ),
                    const SizedBox(height: 6),
                    _DeleteRadio(
                      selected: _forAll,
                      title: 'удалить для всех',
                      subtitle: widget.canDeleteForAll
                          ? 'исчезнет у всех участников'
                          : 'нельзя — это чужое сообщение',
                      enabled: widget.canDeleteForAll,
                      onTap: widget.canDeleteForAll
                          ? () => setState(() => _forAll = true) : null,
                    ),
                    const SizedBox(height: 18),
                    Row(mainAxisAlignment: MainAxisAlignment.end, children: [
                      _ModalGhostBtn(label: 'отмена', onTap: widget.onClose),
                      const SizedBox(width: 10),
                      _ModalDangerBtn(
                        label: _forAll ? 'удалить для всех' : 'удалить',
                        onTap: () => widget.onConfirm(_forAll),
                      ),
                    ]),
                  ],
                ),
              ),
            ]),
          ).animate()
              .scale(begin: const Offset(0.94, 0.94),
                  duration: 180.ms, curve: Curves.easeOutCubic)
              .fade(duration: 150.ms)),
        ]),
      ),
    );
  }

  String _previewText(MessageData m) {
    if (m.text != null && m.text!.isNotEmpty) return m.text!;
    return switch (m.type) {
      MessageType.photo => '📷 фото',
      MessageType.video => '🎞 видео',
      MessageType.voice => '🎤 голосовое сообщение',
      MessageType.file => '📄 ${m.fileName ?? "файл"}',
      MessageType.sticker => '${m.stickerEmoji ?? "🎉"} стикер',
      MessageType.gif => 'GIF',
      MessageType.link => m.linkPreview?.url ?? 'ссылка',
      _ => 'сообщение',
    };
  }
}

class _ModalCloseBtn extends StatefulWidget {
  final VoidCallback onTap;
  const _ModalCloseBtn({required this.onTap});
  @override
  State<_ModalCloseBtn> createState() => _ModalCloseBtnState();
}

class _ModalCloseBtnState extends State<_ModalCloseBtn> {
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
          width: 28, height: 28,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(6),
            color: _h ? Colors.white.withValues(alpha: 0.06) : Colors.transparent,
          ),
          child: Icon(SolarIconsOutline.closeCircle, size: 16,
              color: _h ? BColors.textPrimary : BColors.textMuted),
        ),
      ),
    );
  }
}

class _DeleteRadio extends StatelessWidget {
  final bool selected;
  final String title, subtitle;
  final VoidCallback? onTap;
  final bool enabled;
  const _DeleteRadio({
    required this.selected, required this.title, required this.subtitle,
    this.onTap, this.enabled = true,
  });
  @override
  Widget build(BuildContext context) {
    return Opacity(opacity: enabled ? 1.0 : 0.4,
      child: MouseRegion(
        cursor: enabled ? SystemMouseCursors.click : MouseCursor.defer,
        child: GestureDetector(
          onTap: onTap,
          child: Container(
            padding: const EdgeInsets.fromLTRB(10, 8, 10, 8),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(8),
              color: selected
                  ? BColors.accent.withValues(alpha: 0.08)
                  : Colors.transparent,
              border: Border.all(
                color: selected
                    ? BColors.accent.withValues(alpha: 0.3)
                    : Colors.white.withValues(alpha: 0.05),
                width: 0.5,
              ),
            ),
            child: Row(children: [
              AnimatedContainer(
                duration: const Duration(milliseconds: 150),
                width: 16, height: 16,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  border: Border.all(
                    color: selected ? BColors.accent : Colors.white.withValues(alpha: 0.25),
                    width: 1.5,
                  ),
                ),
                child: Center(child: AnimatedContainer(
                  duration: const Duration(milliseconds: 150),
                  width: selected ? 8 : 0, height: selected ? 8 : 0,
                  decoration: const BoxDecoration(
                    shape: BoxShape.circle, color: BColors.accent,
                  ),
                )),
              ),
              const SizedBox(width: 10),
              Expanded(child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(title, style: const TextStyle(
                    fontFamily: 'Onest', fontSize: 13, fontWeight: FontWeight.w500,
                    color: BColors.textPrimary,
                  )),
                  const SizedBox(height: 1),
                  Text(subtitle, style: const TextStyle(
                    fontFamily: 'Onest', fontSize: 11, color: BColors.textMuted,
                  )),
                ],
              )),
            ]),
          ),
        ),
      ),
    );
  }
}

class _ModalGhostBtn extends StatefulWidget {
  final String label;
  final VoidCallback onTap;
  const _ModalGhostBtn({required this.label, required this.onTap});
  @override
  State<_ModalGhostBtn> createState() => _ModalGhostBtnState();
}

class _ModalGhostBtnState extends State<_ModalGhostBtn> {
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
          height: 32, padding: const EdgeInsets.symmetric(horizontal: 14),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(7),
            color: _h ? Colors.white.withValues(alpha: 0.05) : Colors.transparent,
          ),
          alignment: Alignment.center,
          child: Text(widget.label, style: TextStyle(
            fontFamily: 'Onest', fontSize: 12, fontWeight: FontWeight.w500,
            color: _h ? BColors.textPrimary : BColors.textSecondary,
          )),
        ),
      ),
    );
  }
}

class _ModalDangerBtn extends StatefulWidget {
  final String label;
  final VoidCallback onTap;
  const _ModalDangerBtn({required this.label, required this.onTap});
  @override
  State<_ModalDangerBtn> createState() => _ModalDangerBtnState();
}

class _ModalDangerBtnState extends State<_ModalDangerBtn> {
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
          duration: const Duration(milliseconds: 120),
          height: 32, padding: const EdgeInsets.symmetric(horizontal: 14),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(7),
            color: _h
                ? const Color(0xFFff7070)
                : const Color(0xFFff5c5c),
          ),
          alignment: Alignment.center,
          child: Text(widget.label, style: const TextStyle(
            fontFamily: 'Onest', fontSize: 12, fontWeight: FontWeight.w600,
            color: Colors.white,
          )),
        ),
      ),
    );
  }
}

// ─── UNDO TOAST ───────────────────────────────────────────────

class _UndoToast extends StatefulWidget {
  final VoidCallback onUndo;
  final VoidCallback onDismiss;
  const _UndoToast({required this.onUndo, required this.onDismiss});
  @override
  State<_UndoToast> createState() => _UndoToastState();
}

class _UndoToastState extends State<_UndoToast> {
  bool _actioned = false;

  @override
  void initState() {
    super.initState();
    Future.delayed(const Duration(seconds: 5), () {
      if (!mounted || _actioned) return;
      widget.onDismiss();
    });
  }

  @override
  Widget build(BuildContext context) {
    return Positioned(
      left: 0, right: 0, bottom: 24,
      child: Center(child: Material(
        color: Colors.transparent,
        child: Container(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(12),
            color: const Color(0xF5141418),
            border: Border(
              left: BorderSide(color: BColors.accent, width: 3),
              top: BorderSide(color: Colors.white.withValues(alpha: 0.05), width: 0.5),
              right: BorderSide(color: Colors.white.withValues(alpha: 0.05), width: 0.5),
              bottom: BorderSide(color: Colors.white.withValues(alpha: 0.05), width: 0.5),
            ),
            boxShadow: const [BoxShadow(
              color: Color(0x99000000), blurRadius: 32, offset: Offset(0, 12),
            )],
          ),
          child: Row(mainAxisSize: MainAxisSize.min, children: [
            Icon(SolarIconsOutline.checkCircle, size: 14,
                color: BColors.accent.withValues(alpha: 0.85)),
            const SizedBox(width: 10),
            const Text('сообщение удалено', style: TextStyle(
              fontFamily: 'Onest', fontSize: 13, color: BColors.textPrimary,
            )),
            const SizedBox(width: 16),
            MouseRegion(
              cursor: SystemMouseCursors.click,
              child: GestureDetector(
                onTap: () {
                  _actioned = true;
                  widget.onUndo();
                },
                child: Text('вернуть', style: TextStyle(
                  fontFamily: 'Onest', fontSize: 13, fontWeight: FontWeight.w600,
                  color: BColors.accent,
                )),
              ),
            ),
          ]),
        ).animate()
            .fadeIn(duration: 200.ms)
            .slideY(begin: 0.5, end: 0, curve: Curves.easeOutCubic, duration: 220.ms),
      )),
    );
  }
}

