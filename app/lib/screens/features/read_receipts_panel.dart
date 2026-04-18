import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:solar_icons/solar_icons.dart';

import '../shared/theme.dart';
import 'chat_bubble_parts.dart' show senderColorFor;
import 'members_panel.dart' show stubMembers, Member, MemberRole;

// ═══════════════════════════════════════════════════════════════
// READ RECEIPTS PANEL (B8) — "кто прочитал" slide-over
// ═══════════════════════════════════════════════════════════════

class ReadReceipt {
  final Member member;
  final String? readAt; // null = not read yet
  final bool privacyHidden; // user opted out of read receipts
  const ReadReceipt({required this.member, this.readAt, this.privacyHidden = false});
}

/// Stub: returns mock read-by data for given group chat + message.
List<ReadReceipt> _stubReceipts(String chatId, String msgPreview) {
  final members = stubMembers(chatId);
  // Exclude self (owner)
  final others = members.where((m) => m.role != MemberRole.owner).toList();
  if (others.isEmpty) return const [];
  // Fake a read pattern based on msgPreview length hash
  final seed = msgPreview.length;
  return others.asMap().entries.map((e) {
    final i = e.key;
    final m = e.value;
    if ((seed + i) % 4 == 0) {
      return ReadReceipt(member: m, privacyHidden: true);
    }
    if ((seed + i) % 3 == 0) {
      return ReadReceipt(member: m, readAt: null);
    }
    final min = 30 + (i * 7) % 30;
    return ReadReceipt(member: m, readAt: '14:${min.toString().padLeft(2, '0')}');
  }).toList();
}

OverlayEntry showReadReceiptsPanel(BuildContext context, {
  required String chatId,
  required String messagePreview,
  required String messageTime,
}) {
  final overlay = Overlay.of(context);
  late OverlayEntry entry;
  entry = OverlayEntry(builder: (_) => _ReadReceiptsPanel(
    chatId: chatId,
    messagePreview: messagePreview,
    messageTime: messageTime,
    onClose: () => entry.remove(),
  ));
  overlay.insert(entry);
  return entry;
}

class _ReadReceiptsPanel extends StatelessWidget {
  final String chatId, messagePreview, messageTime;
  final VoidCallback onClose;
  const _ReadReceiptsPanel({
    required this.chatId, required this.messagePreview, required this.messageTime,
    required this.onClose,
  });

  @override
  Widget build(BuildContext context) {
    final receipts = _stubReceipts(chatId, messagePreview);
    final read = receipts.where((r) => r.readAt != null).toList();
    final hidden = receipts.where((r) => r.privacyHidden).toList();
    final unread = receipts.where((r) => r.readAt == null && !r.privacyHidden).toList();

    return KeyboardListener(
      focusNode: FocusNode()..requestFocus(),
      onKeyEvent: (e) {
        if (e is KeyDownEvent && e.logicalKey == LogicalKeyboardKey.escape) {
          onClose();
        }
      },
      child: Material(
        color: Colors.transparent,
        child: Stack(children: [
          Positioned.fill(child: GestureDetector(
            onTap: onClose, behavior: HitTestBehavior.opaque,
            child: Container(color: Colors.black.withValues(alpha: 0.2)),
          ).animate().fadeIn(duration: 180.ms)),
          Positioned(
            right: 0, top: 0, bottom: 0,
            child: ClipRRect(
              child: BackdropFilter(
                filter: ImageFilter.blur(sigmaX: 40, sigmaY: 40),
                child: Container(
                  width: 360,
                  decoration: BoxDecoration(
                    color: const Color(0xF50e0e12),
                    border: const Border(left: BorderSide(color: BColors.borderLow)),
                    boxShadow: [BoxShadow(
                      color: Colors.black.withValues(alpha: 0.4),
                      blurRadius: 48, offset: const Offset(-16, 0),
                    )],
                  ),
                  child: Column(children: [
                    _Header(
                      count: read.length, total: receipts.length,
                      onClose: onClose,
                    ),
                    _MessagePreview(text: messagePreview, time: messageTime),
                    Expanded(child: ListView(
                      padding: const EdgeInsets.only(bottom: 16),
                      children: [
                        if (read.isNotEmpty) ...[
                          _SectionHeader(
                            title: 'прочитали',
                            count: '${read.length} из ${receipts.length}',
                          ),
                          for (final r in read) _ReceiptRow(receipt: r),
                        ],
                        if (unread.isNotEmpty) ...[
                          _SectionHeader(title: 'не прочитали', count: '${unread.length}'),
                          for (final r in unread) _ReceiptRow(receipt: r),
                        ],
                        if (hidden.isNotEmpty) ...[
                          _SectionHeader(title: 'скрыто настройками', count: '${hidden.length}'),
                          for (final r in hidden) _ReceiptRow(receipt: r),
                        ],
                        if (receipts.isEmpty) const Padding(
                          padding: EdgeInsets.all(40),
                          child: Center(child: Text(
                            'никто кроме вас не в чате',
                            style: TextStyle(
                              fontFamily: 'Onest', fontSize: 13, color: BColors.textMuted,
                            ),
                          )),
                        ),
                      ],
                    )),
                  ]),
                ),
              ),
            ).animate()
                .slideX(begin: 1, duration: 280.ms, curve: Curves.easeOutCubic)
                .fade(duration: 240.ms),
          ),
        ]),
      ),
    );
  }
}

class _Header extends StatelessWidget {
  final int count, total;
  final VoidCallback onClose;
  const _Header({required this.count, required this.total, required this.onClose});

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 52,
      padding: const EdgeInsets.symmetric(horizontal: 18),
      decoration: const BoxDecoration(
        border: Border(bottom: BorderSide(color: BColors.borderLow)),
      ),
      child: Row(children: [
        Expanded(child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('прочитано', style: TextStyle(
              fontFamily: 'Nekst', fontSize: 14, fontWeight: FontWeight.w600,
              color: BColors.textPrimary,
            )),
            Text('$count из $total', style: const TextStyle(
              fontFamily: 'Onest', fontSize: 11, color: BColors.textMuted,
            )),
          ],
        )),
        _CloseBtn(onTap: onClose),
      ]),
    );
  }
}

class _MessagePreview extends StatelessWidget {
  final String text, time;
  const _MessagePreview({required this.text, required this.time});
  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.fromLTRB(14, 12, 14, 6),
      padding: const EdgeInsets.fromLTRB(12, 10, 12, 10),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(10),
        color: BColors.accent.withValues(alpha: 0.06),
        border: Border.all(color: BColors.accent.withValues(alpha: 0.15), width: 0.5),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(text, maxLines: 3, overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                fontFamily: 'Onest', fontSize: 13,
                color: BColors.textPrimary, height: 1.4,
              )),
          const SizedBox(height: 4),
          Text(time, style: const TextStyle(
            fontFamily: 'Onest', fontSize: 10, color: BColors.textMuted,
          )),
        ],
      ),
    );
  }
}

class _SectionHeader extends StatelessWidget {
  final String title, count;
  const _SectionHeader({required this.title, required this.count});
  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 14, 20, 4),
      child: Row(children: [
        Text(title.toUpperCase(), style: const TextStyle(
          fontFamily: 'Onest', fontSize: 10, fontWeight: FontWeight.w600,
          color: BColors.textMuted, letterSpacing: 1.2,
        )),
        const Spacer(),
        Text(count, style: TextStyle(
          fontFamily: 'Onest', fontSize: 10, fontWeight: FontWeight.w500,
          color: BColors.textMuted.withValues(alpha: 0.7),
        )),
      ]),
    );
  }
}

class _ReceiptRow extends StatelessWidget {
  final ReadReceipt receipt;
  const _ReceiptRow({required this.receipt});
  @override
  Widget build(BuildContext context) {
    final m = receipt.member;
    final tint = senderColorFor(m.name);
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 10, vertical: 1),
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      child: Row(children: [
        Stack(children: [
          Container(
            width: 34, height: 34,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(10),
              color: tint.withValues(alpha: 0.16),
              border: Border.all(color: tint.withValues(alpha: 0.25), width: 0.5),
            ),
            child: Center(child: Text(m.initial, style: TextStyle(
              fontFamily: 'Onest', fontSize: 12, fontWeight: FontWeight.w700,
              color: tint.withValues(alpha: 0.95),
            ))),
          ),
          if (m.online) Positioned(
            right: 0, bottom: 0,
            child: Container(
              width: 10, height: 10,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: const Color(0xFF4ade80),
                border: Border.all(color: const Color(0xFF0e0e12), width: 2),
              ),
            ),
          ),
        ]),
        const SizedBox(width: 10),
        Expanded(child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(m.name, maxLines: 1, overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  fontFamily: 'Onest', fontSize: 13, fontWeight: FontWeight.w500,
                  color: BColors.textPrimary,
                )),
            Text(
              receipt.privacyHidden
                  ? 'скрыто настройками'
                  : (m.online ? m.handle : m.lastSeen),
              style: const TextStyle(
                fontFamily: 'Onest', fontSize: 11, color: BColors.textMuted,
              ),
            ),
          ],
        )),
        if (receipt.privacyHidden)
          Icon(SolarIconsOutline.lock, size: 14,
              color: BColors.textMuted.withValues(alpha: 0.7))
        else if (receipt.readAt != null)
          Text(receipt.readAt!, style: TextStyle(
            fontFamily: 'Onest', fontSize: 11,
            color: BColors.accent.withValues(alpha: 0.85),
            fontWeight: FontWeight.w500,
          ))
        else
          Icon(SolarIconsOutline.clockCircle, size: 14,
              color: BColors.textMuted.withValues(alpha: 0.5)),
      ]),
    );
  }
}

class _CloseBtn extends StatefulWidget {
  final VoidCallback onTap;
  const _CloseBtn({required this.onTap});
  @override
  State<_CloseBtn> createState() => _CloseBtnState();
}

class _CloseBtnState extends State<_CloseBtn> {
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
