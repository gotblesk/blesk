import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';

import '../shared/theme.dart';

// ═══════════════════════════════════════════════════════════════
// CHAT MESSAGES — bubbles with timestamps, grouping, read receipts
// ═══════════════════════════════════════════════════════════════

class MessageData {
  final String id, text, time;
  final bool own;
  final bool read;
  final String? senderName; // for group chats
  const MessageData({required this.id, required this.text, required this.time,
    required this.own, this.read = false, this.senderName});
}

// Stub messages per chat
Map<String, List<MessageData>> stubMessages = {
  'c1': [
    MessageData(id: '1', text: 'привет!', time: '14:28', own: false),
    MessageData(id: '2', text: 'как дела?', time: '14:30', own: false),
    MessageData(id: '3', text: 'привет! всё хорошо, работаю над blesk', time: '14:31', own: true, read: true),
    MessageData(id: '4', text: 'круто! покажешь потом?', time: '14:32', own: false),
    MessageData(id: '5', text: 'да конечно, скину скриншоты', time: '14:32', own: true, read: true),
    MessageData(id: '6', text: 'жду!', time: '14:33', own: false),
  ],
  'c2': [
    MessageData(id: '1', text: 'ребят, скинул макеты', time: '13:05', own: false, senderName: 'Артём'),
    MessageData(id: '2', text: 'смотрите в фигме', time: '13:05', own: false, senderName: 'Артём'),
    MessageData(id: '3', text: 'огонь!', time: '13:08', own: true, read: true),
    MessageData(id: '4', text: 'скинул макеты в фигму', time: '13:10', own: false, senderName: 'Катя'),
  ],
  'c3': [
    MessageData(id: '1', text: 'завтра созвон в 10?', time: '12:40', own: false),
    MessageData(id: '2', text: 'да, буду', time: '12:42', own: true, read: true),
    MessageData(id: '3', text: 'отлично', time: '12:45', own: false),
  ],
  'c4': [
    MessageData(id: '1', text: 'новый билд готов', time: 'вчера', own: false, senderName: 'bot'),
    MessageData(id: '2', text: 'v1.0.7-beta запушен', time: 'вчера', own: false, senderName: 'bot'),
    MessageData(id: '3', text: 'тестирую', time: 'вчера', own: true, read: true),
  ],
  'c5': [
    MessageData(id: '1', text: 'увидимся вечером?', time: 'вчера', own: true, read: true),
    MessageData(id: '2', text: 'ок', time: 'вчера', own: false),
  ],
  'c6': [
    MessageData(id: '1', text: 'увидимся!', time: 'пн', own: false),
    MessageData(id: '2', text: 'пока!', time: 'пн', own: true, read: true),
  ],
};

class ChatMessages extends StatelessWidget {
  final String chatId;
  final ValueChanged<String>? onSend;

  const ChatMessages({super.key, required this.chatId, this.onSend});

  @override
  Widget build(BuildContext context) {
    final messages = stubMessages[chatId] ?? [];

    return ListView.builder(
      reverse: true,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      itemCount: messages.length,
      itemBuilder: (_, i) {
        final idx = messages.length - 1 - i; // reverse
        final msg = messages[idx];
        final prev = idx > 0 ? messages[idx - 1] : null;
        final showSender = msg.senderName != null && (prev == null || prev.own != msg.own || prev.senderName != msg.senderName);
        final grouped = prev != null && prev.own == msg.own && !showSender;

        return _MessageBubble(
          msg: msg,
          grouped: grouped,
          showSender: showSender,
        ).animate(delay: Duration(milliseconds: 30 * i))
            .fadeIn(duration: 200.ms)
            .slideY(begin: 0.05, duration: 200.ms, curve: Curves.easeOut);
      },
    );
  }
}

class _MessageBubble extends StatefulWidget {
  final MessageData msg;
  final bool grouped;
  final bool showSender;
  const _MessageBubble({required this.msg, required this.grouped, required this.showSender});
  @override
  State<_MessageBubble> createState() => _MessageBubbleState();
}

class _MessageBubbleState extends State<_MessageBubble> {
  bool _h = false;

  @override
  Widget build(BuildContext context) {
    final msg = widget.msg;
    final own = msg.own;

    return MouseRegion(
      onEnter: (_) => setState(() => _h = true),
      onExit: (_) => setState(() => _h = false),
      child: Align(
        alignment: own ? Alignment.centerRight : Alignment.centerLeft,
        child: Container(
          margin: EdgeInsets.only(
            top: widget.grouped ? 2 : 8,
            left: own ? MediaQuery.of(context).size.width * 0.08 : 0,
            right: own ? 0 : MediaQuery.of(context).size.width * 0.08,
          ),
          child: Column(
            crossAxisAlignment: own ? CrossAxisAlignment.end : CrossAxisAlignment.start,
            children: [
              // Sender name (group chats)
              if (widget.showSender)
                Padding(
                  padding: const EdgeInsets.only(left: 12, bottom: 3),
                  child: Text(msg.senderName!, style: TextStyle(
                    fontFamily: 'Onest', fontSize: 11,
                    fontWeight: FontWeight.w600,
                    color: BColors.accent.withValues(alpha: 0.6),
                  )),
                ),
              // Bubble
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                decoration: BoxDecoration(
                  color: own
                      ? BColors.accent.withValues(alpha: 0.08)
                      : Colors.white.withValues(alpha: 0.05),
                  borderRadius: BorderRadius.only(
                    topLeft: Radius.circular(own ? 16 : (widget.grouped ? 6 : 16)),
                    topRight: Radius.circular(own ? (widget.grouped ? 6 : 16) : 16),
                    bottomLeft: const Radius.circular(16),
                    bottomRight: const Radius.circular(16),
                  ),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    // Text
                    Flexible(
                      child: Text(msg.text, style: TextStyle(
                        fontFamily: 'Onest', fontSize: rf(context, 14),
                        fontWeight: FontWeight.w400,
                        color: BColors.textPrimary, height: 1.4,
                      )),
                    ),
                    const SizedBox(width: 8),
                    // Time + read
                    Row(mainAxisSize: MainAxisSize.min, children: [
                      Text(msg.time, style: TextStyle(
                        fontFamily: 'Onest', fontSize: 10,
                        color: BColors.textMuted.withValues(alpha: 0.6),
                      )),
                      if (own) ...[
                        const SizedBox(width: 3),
                        Icon(
                          msg.read ? Icons.done_all : Icons.done,
                          size: 12,
                          color: msg.read
                              ? BColors.accent.withValues(alpha: 0.5)
                              : BColors.textMuted.withValues(alpha: 0.4),
                        ),
                      ],
                    ]),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
