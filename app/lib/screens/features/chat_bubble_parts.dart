import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:solar_icons/solar_icons.dart';

import '../shared/theme.dart';

// ═══════════════════════════════════════════════════════════════
// CHAT BUBBLE PARTS — content renderers, separators, helpers
// ═══════════════════════════════════════════════════════════════

// ─── MESSAGE STATUS & TYPES ───────────────────────────────────

enum MessageStatus { sending, sent, delivered, read, error }

enum MessageType {
  text, photo, video, voice, file,
  sticker, gif, link, system, forwarded,
}

// ─── REACTION ─────────────────────────────────────────────────

class Reaction {
  final String emoji;
  final int count;
  final bool mine;
  const Reaction(this.emoji, this.count, {this.mine = false});

  Reaction copyWith({int? count, bool? mine}) =>
      Reaction(emoji, count ?? this.count, mine: mine ?? this.mine);
}

// ─── REPLY QUOTE ──────────────────────────────────────────────

class ReplyQuote {
  final String id, senderName, text;
  final Color senderColor;
  final bool hasPhoto;
  const ReplyQuote({
    required this.id,
    required this.senderName,
    required this.text,
    this.senderColor = BColors.accent,
    this.hasPhoto = false,
  });
}

// ─── LINK PREVIEW ─────────────────────────────────────────────

class LinkPreviewData {
  final String url, title, description, domain;
  final Color tint; // stub image — gradient tint
  const LinkPreviewData({
    required this.url,
    required this.title,
    required this.description,
    required this.domain,
    this.tint = const Color(0xFF5B8FF9),
  });
}

// ─── HIGHLIGHTED TEXT (search match accent highlight) ─────────

class HighlightedText extends StatelessWidget {
  final String text;
  final String highlight;
  final TextStyle style;
  final int? maxLines;
  final int? currentMatchIndex; // which occurrence is "current" — painted stronger
  final int? textMatchStart; // used by parent to offset highlight indices
  const HighlightedText({
    super.key,
    required this.text, required this.highlight, required this.style,
    this.maxLines, this.currentMatchIndex, this.textMatchStart,
  });

  @override
  Widget build(BuildContext context) {
    if (highlight.trim().isEmpty) {
      return Text(text, maxLines: maxLines, overflow: TextOverflow.ellipsis,
          style: style);
    }
    final spans = <TextSpan>[];
    final lt = text.toLowerCase();
    final hl = highlight.toLowerCase();
    int cursor = 0, occurrence = 0;
    while (cursor < text.length) {
      final idx = lt.indexOf(hl, cursor);
      if (idx < 0) {
        spans.add(TextSpan(text: text.substring(cursor)));
        break;
      }
      if (idx > cursor) spans.add(TextSpan(text: text.substring(cursor, idx)));
      final isCurrent = currentMatchIndex != null &&
          occurrence == currentMatchIndex;
      spans.add(TextSpan(
        text: text.substring(idx, idx + hl.length),
        style: TextStyle(
          color: BColors.accent, fontWeight: FontWeight.w600,
          backgroundColor: BColors.accent.withValues(
              alpha: isCurrent ? 0.3 : 0.15),
        ),
      ));
      cursor = idx + hl.length;
      occurrence++;
    }
    return Text.rich(
      TextSpan(style: style, children: spans),
      maxLines: maxLines, overflow: TextOverflow.ellipsis,
    );
  }
}

// ─── SENDER COLORS (stable by name hash) ──────────────────────

const _senderPalette = <Color>[
  Color(0xFFFFB15C), Color(0xFF8BD4FF), Color(0xFFB7FF8B),
  Color(0xFFFF8BE0), Color(0xFFFFE45C), Color(0xFF9B8BFF),
  Color(0xFF5CFFCB), Color(0xFFFF7A7A),
];

Color senderColorFor(String name) {
  final h = name.codeUnits.fold<int>(0, (a, b) => a + b);
  return _senderPalette[h % _senderPalette.length];
}

// ─── READ STATUS ICON ─────────────────────────────────────────

class ReadStatusIcon extends StatelessWidget {
  final MessageStatus status;
  const ReadStatusIcon(this.status, {super.key});

  @override
  Widget build(BuildContext context) {
    switch (status) {
      case MessageStatus.sending:
        return Icon(SolarIconsOutline.clockCircle, size: 11,
            color: BColors.textMuted.withValues(alpha: 0.5));
      case MessageStatus.sent:
        return Icon(SolarIconsOutline.checkCircle, size: 12,
            color: BColors.textMuted.withValues(alpha: 0.6));
      case MessageStatus.delivered:
        return Icon(SolarIconsBold.checkCircle, size: 12,
            color: BColors.textMuted.withValues(alpha: 0.7));
      case MessageStatus.read:
        // Wow-accent #1: read receipts in accent Bold
        return Icon(SolarIconsBold.checkCircle, size: 13,
            color: BColors.accent.withValues(alpha: 0.85));
      case MessageStatus.error:
        return const Icon(SolarIconsOutline.dangerCircle,
            size: 12, color: Color(0xFFff5c5c));
    }
  }
}

// ─── USER AVATAR (28×28 for group chats) ──────────────────────

class GroupAvatar extends StatelessWidget {
  final String initial;
  final Color tint;
  final double size;
  const GroupAvatar({super.key, required this.initial, required this.tint, this.size = 28});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size, height: size,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(8),
        color: tint.withValues(alpha: 0.14),
        border: Border.all(color: tint.withValues(alpha: 0.22), width: 0.5),
      ),
      child: Center(child: Text(
        initial,
        style: TextStyle(
          fontFamily: 'Onest', fontSize: 12, fontWeight: FontWeight.w700,
          color: tint.withValues(alpha: 0.95),
        ),
      )),
    );
  }
}

// ─── DATE SEPARATOR ───────────────────────────────────────────

class DateSeparator extends StatelessWidget {
  final String label;
  final bool highlighted; // for "N новых сообщений"
  const DateSeparator({super.key, required this.label, this.highlighted = false});

  @override
  Widget build(BuildContext context) {
    final lineColor = highlighted
        ? BColors.accent.withValues(alpha: 0.3)
        : Colors.white.withValues(alpha: 0.06);
    final textColor = highlighted
        ? BColors.accent.withValues(alpha: 0.75)
        : BColors.textMuted;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 24),
      child: Row(children: [
        Expanded(child: Container(height: 1, color: lineColor)),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12),
          child: Text(label, style: TextStyle(
            fontFamily: 'Onest', fontSize: 11, fontWeight: FontWeight.w500,
            color: textColor, letterSpacing: 0.3,
          )),
        ),
        Expanded(child: Container(height: 1, color: lineColor)),
      ]),
    );
  }
}

// ─── SCROLL TO BOTTOM BUTTON ──────────────────────────────────

class ScrollToBottomBtn extends StatefulWidget {
  final int unread;
  final VoidCallback onTap;
  const ScrollToBottomBtn({super.key, required this.unread, required this.onTap});
  @override
  State<ScrollToBottomBtn> createState() => _ScrollToBottomBtnState();
}

class _ScrollToBottomBtnState extends State<ScrollToBottomBtn> {
  bool _h = false;
  @override
  Widget build(BuildContext context) {
    final hasUnread = widget.unread > 0;
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setState(() => _h = true),
      onExit: (_) => setState(() => _h = false),
      child: GestureDetector(
        onTap: widget.onTap,
        child: Stack(clipBehavior: Clip.none, children: [
          AnimatedContainer(
            duration: const Duration(milliseconds: 150),
            width: 36, height: 36,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: _h
                  ? Colors.white.withValues(alpha: 0.1)
                  : Colors.white.withValues(alpha: 0.06),
              border: Border.all(
                color: hasUnread
                    ? BColors.accent.withValues(alpha: 0.4)
                    : Colors.white.withValues(alpha: 0.08),
                width: 0.8,
              ),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withValues(alpha: 0.3),
                  blurRadius: 12, offset: const Offset(0, 4),
                ),
              ],
            ),
            child: Icon(
              SolarIconsOutline.arrowDown,
              size: 18,
              color: hasUnread ? BColors.accent : BColors.textSecondary,
            ),
          ),
          if (hasUnread)
            Positioned(
              top: -4, right: -4,
              child: Container(
                constraints: const BoxConstraints(minWidth: 18, minHeight: 18),
                padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1),
                decoration: BoxDecoration(
                  color: BColors.accent,
                  borderRadius: BorderRadius.circular(9),
                ),
                child: Center(child: Text(
                  widget.unread > 99 ? '99+' : '${widget.unread}',
                  style: const TextStyle(
                    fontFamily: 'Onest', fontSize: 10, fontWeight: FontWeight.w700,
                    color: BColors.bg,
                  ),
                )),
              ),
            ),
        ]),
      ),
    ).animate().fadeIn(duration: 180.ms).slideY(begin: 0.25, curve: Curves.easeOut);
  }
}

// ─── PINNED MESSAGES BAR ──────────────────────────────────────

class PinnedMessagesBar extends StatefulWidget {
  final List<({String sender, String text})> pinned;
  final VoidCallback? onTap;
  final VoidCallback? onDismiss;
  const PinnedMessagesBar({super.key, required this.pinned, this.onTap, this.onDismiss});
  @override
  State<PinnedMessagesBar> createState() => _PinnedMessagesBarState();
}

class _PinnedMessagesBarState extends State<PinnedMessagesBar> {
  int _active = 0;

  @override
  Widget build(BuildContext context) {
    if (widget.pinned.isEmpty) return const SizedBox.shrink();
    final current = widget.pinned[_active];
    return Container(
      height: 40,
      padding: const EdgeInsets.symmetric(horizontal: 14),
      decoration: const BoxDecoration(
        color: Color(0x0AFFFFFF),
        border: Border(bottom: BorderSide(color: BColors.borderLow)),
      ),
      child: MouseRegion(
        cursor: widget.onTap != null ? SystemMouseCursors.click : MouseCursor.defer,
        child: GestureDetector(
          onTap: widget.onTap,
          child: Row(children: [
            Container(
              width: 3, height: 22,
              decoration: BoxDecoration(
                color: BColors.accent, borderRadius: BorderRadius.circular(2),
              ),
            ),
            const SizedBox(width: 10),
            Icon(SolarIconsOutline.pin, size: 13,
                color: BColors.accent.withValues(alpha: 0.7)),
            const SizedBox(width: 8),
            Expanded(child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(current.sender, style: TextStyle(
                  fontFamily: 'Onest', fontSize: 11, fontWeight: FontWeight.w600,
                  color: BColors.accent.withValues(alpha: 0.8),
                )),
                Text(current.text, maxLines: 1, overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontFamily: 'Onest', fontSize: 12, color: BColors.textSecondary,
                    )),
              ],
            )),
            if (widget.pinned.length > 1) ...[
              Text('${_active + 1}/${widget.pinned.length}', style: const TextStyle(
                fontFamily: 'Onest', fontSize: 11, color: BColors.textMuted,
              )),
              const SizedBox(width: 4),
              _PinArrow(icon: SolarIconsOutline.altArrowUp, onTap: () =>
                  setState(() => _active = (_active - 1 + widget.pinned.length) % widget.pinned.length)),
              _PinArrow(icon: SolarIconsOutline.altArrowDown, onTap: () =>
                  setState(() => _active = (_active + 1) % widget.pinned.length)),
            ],
            if (widget.onDismiss != null)
              _PinArrow(icon: SolarIconsOutline.closeCircle, onTap: widget.onDismiss!),
          ]),
        ),
      ),
    );
  }
}

class _PinArrow extends StatefulWidget {
  final IconData icon;
  final VoidCallback onTap;
  const _PinArrow({required this.icon, required this.onTap});
  @override
  State<_PinArrow> createState() => _PinArrowState();
}

class _PinArrowState extends State<_PinArrow> {
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
          width: 22, height: 22,
          margin: const EdgeInsets.symmetric(horizontal: 1),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(5),
            color: _h ? Colors.white.withValues(alpha: 0.06) : Colors.transparent,
          ),
          child: Icon(widget.icon, size: 14,
              color: _h ? BColors.textSecondary : BColors.textMuted),
        ),
      ),
    );
  }
}

// ─── REPLY PREVIEW INSIDE BUBBLE ──────────────────────────────

class ReplyPreview extends StatelessWidget {
  final ReplyQuote quote;
  final VoidCallback? onTap;
  const ReplyPreview({super.key, required this.quote, this.onTap});

  @override
  Widget build(BuildContext context) {
    return MouseRegion(
      cursor: onTap != null ? SystemMouseCursors.click : MouseCursor.defer,
      child: GestureDetector(
        onTap: onTap,
        child: Container(
          margin: const EdgeInsets.only(bottom: 6),
          padding: const EdgeInsets.fromLTRB(10, 6, 10, 6),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(6),
            color: quote.senderColor.withValues(alpha: 0.06),
            border: Border(left: BorderSide(color: quote.senderColor, width: 2.5)),
          ),
          child: Row(mainAxisSize: MainAxisSize.min, children: [
            if (quote.hasPhoto) ...[
              Container(
                width: 28, height: 28,
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(4),
                  gradient: LinearGradient(
                    colors: [quote.senderColor.withValues(alpha: 0.4),
                      quote.senderColor.withValues(alpha: 0.15)],
                  ),
                ),
                child: Icon(SolarIconsOutline.gallery, size: 14,
                    color: Colors.white.withValues(alpha: 0.7)),
              ),
              const SizedBox(width: 8),
            ],
            Flexible(child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(quote.senderName, style: TextStyle(
                  fontFamily: 'Onest', fontSize: 11, fontWeight: FontWeight.w600,
                  color: quote.senderColor,
                )),
                const SizedBox(height: 1),
                Text(
                  quote.text,
                  maxLines: 1, overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontFamily: 'Onest', fontSize: 12,
                    color: BColors.textSecondary,
                  ),
                ),
              ],
            )),
          ]),
        ),
      ),
    );
  }
}

// ─── FORWARD LABEL ────────────────────────────────────────────

class ForwardLabel extends StatelessWidget {
  final String senderName;
  const ForwardLabel({super.key, required this.senderName});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 4),
      child: Row(mainAxisSize: MainAxisSize.min, children: [
        Icon(SolarIconsOutline.undoLeft, size: 11,
            color: BColors.accent.withValues(alpha: 0.55)),
        const SizedBox(width: 4),
        Text('от $senderName', style: TextStyle(
          fontFamily: 'Onest', fontSize: 11, fontWeight: FontWeight.w600,
          color: BColors.accent.withValues(alpha: 0.55),
          fontStyle: FontStyle.italic,
        )),
      ]),
    );
  }
}

// ─── SYSTEM MESSAGE ───────────────────────────────────────────

class SystemMessage extends StatelessWidget {
  final String text;
  final String? time;
  const SystemMessage({super.key, required this.text, this.time});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 24),
      child: Center(child: Column(mainAxisSize: MainAxisSize.min, children: [
        Text(text, textAlign: TextAlign.center, style: const TextStyle(
          fontFamily: 'Onest', fontSize: 12, fontWeight: FontWeight.w400,
          color: BColors.textMuted,
        )),
        if (time != null) Padding(
          padding: const EdgeInsets.only(top: 2),
          child: Text(time!, style: TextStyle(
            fontFamily: 'Onest', fontSize: 10,
            color: BColors.textMuted.withValues(alpha: 0.7),
          )),
        ),
      ])),
    );
  }
}

// ─── TYPING BUBBLE (in-stream) ────────────────────────────────

/// Single typing user — shows bubble with 3 bouncing dots.
/// Used both as standalone (1:1 chat) and stacked (group chat).
class TypingBubble extends StatelessWidget {
  final String? senderName; // null for 1:1 — no avatar
  final String? senderInitial;
  const TypingBubble({super.key, this.senderName, this.senderInitial});

  @override
  Widget build(BuildContext context) {
    final tint = senderName != null
        ? senderColorFor(senderName!)
        : BColors.textMuted;
    return Padding(
      padding: const EdgeInsets.only(top: 4, bottom: 4, left: 14, right: 14),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.start,
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          if (senderName != null)
            Padding(
              padding: const EdgeInsets.only(right: 8, bottom: 2),
              child: GroupAvatar(
                initial: senderInitial ?? senderName![0].toUpperCase(),
                tint: tint,
              ),
            ),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
            decoration: BoxDecoration(
              color: const Color(0xFF141418),
              borderRadius: const BorderRadius.only(
                topLeft: Radius.circular(16), topRight: Radius.circular(16),
                bottomLeft: Radius.circular(4), bottomRight: Radius.circular(16),
              ),
              border: Border.all(color: Colors.white.withValues(alpha: 0.05), width: 1),
            ),
            child: Row(mainAxisSize: MainAxisSize.min, children: const [
              _TypingDot(delay: 0),
              SizedBox(width: 3),
              _TypingDot(delay: 150),
              SizedBox(width: 3),
              _TypingDot(delay: 300),
            ]),
          ),
        ],
      ),
    ).animate()
        .fadeIn(duration: 200.ms)
        .slideY(begin: 0.4, end: 0, curve: Curves.easeOut, duration: 200.ms);
  }
}

class _TypingDot extends StatelessWidget {
  final int delay;
  const _TypingDot({required this.delay});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 6, height: 6,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: BColors.textMuted.withValues(alpha: 0.95),
      ),
    ).animate(onPlay: (c) => c.repeat(), delay: Duration(milliseconds: delay))
        .moveY(begin: 0, end: -4, duration: 300.ms, curve: Curves.easeOut)
        .fadeIn(begin: 0.4, duration: 300.ms, curve: Curves.easeOut)
        .then()
        .moveY(begin: -4, end: 0, duration: 300.ms, curve: Curves.easeIn)
        .fadeOut(duration: 300.ms, curve: Curves.easeIn, begin: 1)
        .then(delay: 600.ms);
  }
}

/// Stacked typing indicators — used in group chats.
/// Shows up to 3 bubbles; beyond that collapses to text.
class TypingStack extends StatelessWidget {
  final List<({String name, String initial})> users;
  const TypingStack({super.key, required this.users});

  @override
  Widget build(BuildContext context) {
    if (users.isEmpty) return const SizedBox.shrink();
    if (users.length <= 3) {
      return Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          for (final u in users)
            TypingBubble(senderName: u.name, senderInitial: u.initial),
        ],
      );
    }
    final names = users.take(2).map((u) => u.name).join(', ');
    return Padding(
      padding: const EdgeInsets.fromLTRB(18, 8, 14, 8),
      child: Row(mainAxisSize: MainAxisSize.min, children: [
        const _TypingDot(delay: 0),
        const SizedBox(width: 3),
        const _TypingDot(delay: 150),
        const SizedBox(width: 3),
        const _TypingDot(delay: 300),
        const SizedBox(width: 8),
        Flexible(child: Text(
          '$names и ещё ${users.length - 2} печатают...',
          style: TextStyle(
            fontFamily: 'Onest', fontSize: 12,
            color: BColors.textMuted.withValues(alpha: 0.9),
            fontStyle: FontStyle.italic,
          ),
        )),
      ]),
    ).animate().fadeIn(duration: 200.ms);
  }
}

// ─── PHOTO PLACEHOLDER (stub image via gradient) ──────────────

class PhotoContent extends StatelessWidget {
  final List<Color> photoTints; // stubs — gradient tints
  final String? caption;
  final double maxWidth;
  const PhotoContent({
    super.key, required this.photoTints,
    this.caption, this.maxWidth = 320,
  });

  @override
  Widget build(BuildContext context) {
    final n = photoTints.length;
    Widget grid;
    if (n == 1) {
      grid = _StubImage(tint: photoTints[0], width: maxWidth, height: 240);
    } else if (n == 2) {
      grid = _row([
        _StubImage(tint: photoTints[0], width: maxWidth / 2 - 1, height: 180),
        _StubImage(tint: photoTints[1], width: maxWidth / 2 - 1, height: 180),
      ]);
    } else if (n == 3) {
      grid = Row(mainAxisSize: MainAxisSize.min, children: [
        _StubImage(tint: photoTints[0], width: maxWidth * 0.66 - 1, height: 240),
        const SizedBox(width: 2),
        Column(mainAxisSize: MainAxisSize.min, children: [
          _StubImage(tint: photoTints[1], width: maxWidth * 0.34 - 1, height: 119),
          const SizedBox(height: 2),
          _StubImage(tint: photoTints[2], width: maxWidth * 0.34 - 1, height: 119),
        ]),
      ]);
    } else {
      // 4+ grid 2x2
      final extra = n > 4 ? n - 4 : 0;
      grid = Column(mainAxisSize: MainAxisSize.min, children: [
        _row([
          _StubImage(tint: photoTints[0], width: maxWidth / 2 - 1, height: 140),
          _StubImage(tint: photoTints[1], width: maxWidth / 2 - 1, height: 140),
        ]),
        const SizedBox(height: 2),
        _row([
          _StubImage(tint: photoTints[2], width: maxWidth / 2 - 1, height: 140),
          Stack(children: [
            _StubImage(tint: photoTints[3], width: maxWidth / 2 - 1, height: 140),
            if (extra > 0) Positioned.fill(child: Container(
              decoration: BoxDecoration(
                color: Colors.black.withValues(alpha: 0.55),
                borderRadius: BorderRadius.circular(6),
              ),
              child: Center(child: Text('+$extra', style: const TextStyle(
                fontFamily: 'Nekst', fontSize: 22, fontWeight: FontWeight.w700,
                color: Colors.white,
              ))),
            )),
          ]),
        ]),
      ]);
    }

    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      ClipRRect(
        borderRadius: BorderRadius.circular(12),
        child: grid,
      ),
      if (caption != null && caption!.isNotEmpty) Padding(
        padding: const EdgeInsets.only(top: 6),
        child: Text(caption!, style: const TextStyle(
          fontFamily: 'Onest', fontSize: 14, color: BColors.textPrimary, height: 1.4,
        )),
      ),
    ]);
  }

  Widget _row(List<Widget> children) {
    final list = <Widget>[];
    for (var i = 0; i < children.length; i++) {
      list.add(children[i]);
      if (i < children.length - 1) list.add(const SizedBox(width: 2));
    }
    return Row(mainAxisSize: MainAxisSize.min, children: list);
  }
}

class _StubImage extends StatelessWidget {
  final Color tint;
  final double width, height;
  const _StubImage({required this.tint, required this.width, required this.height});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: width, height: height,
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft, end: Alignment.bottomRight,
          colors: [
            tint.withValues(alpha: 0.75),
            tint.withValues(alpha: 0.35),
            Colors.black.withValues(alpha: 0.3),
          ],
        ),
      ),
      child: Stack(children: [
        // subtle noise
        Positioned.fill(child: Opacity(
          opacity: 0.12,
          child: CustomPaint(painter: _NoisePainter(tint)),
        )),
        Center(child: Icon(SolarIconsOutline.gallery, size: 28,
            color: Colors.white.withValues(alpha: 0.35))),
      ]),
    );
  }
}

class _NoisePainter extends CustomPainter {
  final Color tint;
  _NoisePainter(this.tint);
  @override
  void paint(Canvas canvas, Size size) {
    final rnd = math.Random(tint.toARGB32());
    final p = Paint()..color = Colors.white.withValues(alpha: 0.15);
    for (var i = 0; i < 200; i++) {
      canvas.drawCircle(
        Offset(rnd.nextDouble() * size.width, rnd.nextDouble() * size.height),
        0.6, p,
      );
    }
  }
  @override bool shouldRepaint(covariant _NoisePainter o) => false;
}

// ─── VIDEO CONTENT ────────────────────────────────────────────

class VideoContent extends StatelessWidget {
  final Color tint;
  final Duration duration;
  final String? caption;
  final double maxWidth;
  const VideoContent({
    super.key, required this.tint, required this.duration,
    this.caption, this.maxWidth = 320,
  });

  @override
  Widget build(BuildContext context) {
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      ClipRRect(
        borderRadius: BorderRadius.circular(12),
        child: Stack(children: [
          _StubImage(tint: tint, width: maxWidth, height: 200),
          // Play button
          Positioned.fill(child: Center(child: Container(
            width: 56, height: 56,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: Colors.black.withValues(alpha: 0.55),
              border: Border.all(color: Colors.white.withValues(alpha: 0.35), width: 1),
            ),
            child: const Icon(SolarIconsBold.play, size: 34, color: Colors.white),
          ))),
          // Duration badge
          Positioned(
            bottom: 8, right: 8,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(10),
                color: Colors.black.withValues(alpha: 0.65),
              ),
              child: Text(_fmt(duration), style: const TextStyle(
                fontFamily: 'Onest', fontSize: 11, fontWeight: FontWeight.w500,
                color: Colors.white,
              )),
            ),
          ),
        ]),
      ),
      if (caption != null && caption!.isNotEmpty) Padding(
        padding: const EdgeInsets.only(top: 6),
        child: Text(caption!, style: const TextStyle(
          fontFamily: 'Onest', fontSize: 14, color: BColors.textPrimary, height: 1.4,
        )),
      ),
    ]);
  }

  String _fmt(Duration d) {
    final m = d.inMinutes, s = d.inSeconds.remainder(60);
    return '$m:${s.toString().padLeft(2, '0')}';
  }
}

// ─── VOICE CONTENT ────────────────────────────────────────────

class VoiceContent extends StatefulWidget {
  final Duration duration;
  final List<double> waveform; // 0..1 values, target 22-30 bars
  final bool own;
  final bool played;
  const VoiceContent({
    super.key, required this.duration, required this.waveform,
    required this.own, this.played = false,
  });
  @override
  State<VoiceContent> createState() => _VoiceContentState();
}

class _VoiceContentState extends State<VoiceContent> with SingleTickerProviderStateMixin {
  bool _playing = false;
  double _progress = 0;
  double _speed = 1.0;
  late final AnimationController _ctrl;

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(
      vsync: this,
      duration: widget.duration,
    )..addListener(() {
      setState(() => _progress = _ctrl.value);
      if (_ctrl.isCompleted) {
        _playing = false;
        _ctrl.value = 0;
      }
    });
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  void _toggle() {
    setState(() {
      _playing = !_playing;
      if (_playing) {
        _ctrl.forward();
      } else {
        _ctrl.stop();
      }
    });
  }

  void _cycleSpeed() {
    setState(() {
      _speed = _speed == 1.0 ? 1.5 : _speed == 1.5 ? 2.0 : 1.0;
      _ctrl.duration = Duration(
        microseconds: (widget.duration.inMicroseconds / _speed).round(),
      );
    });
  }

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 240,
      child: Row(children: [
        // Play button
        MouseRegion(
          cursor: SystemMouseCursors.click,
          child: GestureDetector(
            onTap: _toggle,
            child: Container(
              width: 32, height: 32,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: BColors.accent.withValues(alpha: _playing ? 0.9 : 0.75),
              ),
              child: Icon(
                _playing ? SolarIconsBold.pause : SolarIconsBold.play,
                size: 18, color: BColors.bg,
              ),
            ),
          ),
        ),
        const SizedBox(width: 10),
        // Waveform + time
        Expanded(child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            GestureDetector(
              onTapDown: (d) {
                final box = context.findRenderObject() as RenderBox?;
                if (box == null) return;
                final w = box.size.width - 42 - 40;
                final frac = (d.localPosition.dx / w).clamp(0.0, 1.0);
                _ctrl.value = frac;
                setState(() => _progress = frac);
              },
              child: SizedBox(height: 24, child: CustomPaint(
                painter: _WaveformPainter(
                  bars: widget.waveform,
                  progress: _progress,
                  played: widget.played || _progress > 0.05,
                ),
                child: const SizedBox.expand(),
              )),
            ),
            const SizedBox(height: 3),
            Row(children: [
              Text(
                _timeLabel(widget.duration, _progress),
                style: TextStyle(
                  fontFamily: 'Onest', fontSize: 11,
                  color: BColors.textMuted.withValues(alpha: 0.9),
                ),
              ),
              const Spacer(),
              if (_playing || _progress > 0)
                _SpeedChip(speed: _speed, onTap: _cycleSpeed),
            ]),
          ],
        )),
      ]),
    );
  }

  String _timeLabel(Duration total, double progress) {
    final played = total * progress;
    final m = played.inMinutes, s = played.inSeconds.remainder(60);
    final tm = total.inMinutes, ts = total.inSeconds.remainder(60);
    return '$m:${s.toString().padLeft(2, '0')} / $tm:${ts.toString().padLeft(2, '0')}';
  }
}

class _SpeedChip extends StatelessWidget {
  final double speed;
  final VoidCallback onTap;
  const _SpeedChip({required this.speed, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      child: GestureDetector(
        onTap: onTap,
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(6),
            color: BColors.accent.withValues(alpha: 0.12),
          ),
          child: Text(
            speed == 1.0 ? '1×' : speed == 1.5 ? '1.5×' : '2×',
            style: TextStyle(
              fontFamily: 'Onest', fontSize: 10, fontWeight: FontWeight.w600,
              color: BColors.accent.withValues(alpha: 0.9),
            ),
          ),
        ),
      ),
    );
  }
}

class _WaveformPainter extends CustomPainter {
  final List<double> bars;
  final double progress;
  final bool played;
  _WaveformPainter({required this.bars, required this.progress, required this.played});

  @override
  void paint(Canvas canvas, Size size) {
    final n = bars.length;
    final gap = 2.0;
    final barW = (size.width - gap * (n - 1)) / n;
    for (var i = 0; i < n; i++) {
      final x = i * (barW + gap);
      final h = (bars[i].clamp(0.1, 1.0)) * size.height;
      final y = (size.height - h) / 2;
      final frac = (i + 0.5) / n;
      final active = frac <= progress;
      final color = active
          ? BColors.accent.withValues(alpha: 0.95)
          : played
              ? BColors.accent.withValues(alpha: 0.35)
              : Colors.white.withValues(alpha: 0.25);
      canvas.drawRRect(
        RRect.fromRectAndRadius(
          Rect.fromLTWH(x, y, barW, h),
          const Radius.circular(1.2),
        ),
        Paint()..color = color,
      );
    }
  }

  @override
  bool shouldRepaint(covariant _WaveformPainter o) =>
      o.progress != progress || o.played != played;
}

// ─── FILE CONTENT ─────────────────────────────────────────────

class FileContent extends StatelessWidget {
  final String fileName, fileSize, ext;
  final VoidCallback? onDownload;
  const FileContent({
    super.key, required this.fileName, required this.fileSize,
    required this.ext, this.onDownload,
  });

  @override
  Widget build(BuildContext context) {
    final color = _colorForExt(ext);
    return SizedBox(
      width: 260,
      child: Row(children: [
        // Icon
        Container(
          width: 36, height: 36,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(8),
            color: color.withValues(alpha: 0.12),
            border: Border.all(color: color.withValues(alpha: 0.25), width: 0.5),
          ),
          child: Center(child: Text(
            ext.toUpperCase(),
            style: TextStyle(
              fontFamily: 'Onest', fontSize: 9, fontWeight: FontWeight.w800,
              color: color.withValues(alpha: 0.95), letterSpacing: 0.5,
            ),
          )),
        ),
        const SizedBox(width: 10),
        Expanded(child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(fileName, maxLines: 1, overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  fontFamily: 'Onest', fontSize: 13, fontWeight: FontWeight.w500,
                  color: BColors.textPrimary,
                )),
            const SizedBox(height: 2),
            Text(fileSize, style: const TextStyle(
              fontFamily: 'Onest', fontSize: 11, color: BColors.textMuted,
            )),
          ],
        )),
        const SizedBox(width: 6),
        _DownloadBtn(onTap: onDownload ?? () {}),
      ]),
    );
  }

  Color _colorForExt(String ext) {
    switch (ext.toLowerCase()) {
      case 'pdf': return const Color(0xFFff6666);
      case 'doc': case 'docx': return const Color(0xFF5b8fff);
      case 'zip': case 'rar': case '7z': return const Color(0xFFffb25c);
      case 'xls': case 'xlsx': return const Color(0xFF4caf50);
      case 'mp3': case 'wav': return const Color(0xFFd98fff);
      default: return const Color(0xFF8fa5ff);
    }
  }
}

class _DownloadBtn extends StatefulWidget {
  final VoidCallback onTap;
  const _DownloadBtn({required this.onTap});
  @override
  State<_DownloadBtn> createState() => _DownloadBtnState();
}

class _DownloadBtnState extends State<_DownloadBtn> {
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
            color: _h ? BColors.accent.withValues(alpha: 0.14) : Colors.transparent,
          ),
          child: Icon(SolarIconsOutline.downloadMinimalistic, size: 16,
              color: _h ? BColors.accent : BColors.textSecondary),
        ),
      ),
    );
  }
}

// ─── STICKER CONTENT ──────────────────────────────────────────

class StickerContent extends StatelessWidget {
  final String emoji; // stub — display large emoji as sticker
  const StickerContent({super.key, required this.emoji});

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 160, height: 160,
      child: Center(child: Text(
        emoji,
        style: const TextStyle(fontSize: 120, height: 1.1),
      )),
    );
  }
}

// ─── GIF CONTENT ──────────────────────────────────────────────

class GifContent extends StatelessWidget {
  final Color tint;
  final double maxWidth;
  const GifContent({super.key, required this.tint, this.maxWidth = 280});

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(12),
      child: Stack(children: [
        _StubImage(tint: tint, width: maxWidth, height: 200),
        // Shimmer sweep to hint animation
        Positioned.fill(child: _GifShimmer()),
        // GIF badge
        Positioned(
          top: 8, left: 8,
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(4),
              color: Colors.black.withValues(alpha: 0.7),
            ),
            child: const Text('GIF', style: TextStyle(
              fontFamily: 'Onest', fontSize: 10, fontWeight: FontWeight.w700,
              color: Colors.white, letterSpacing: 0.5,
            )),
          ),
        ),
      ]),
    );
  }
}

class _GifShimmer extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.centerLeft, end: Alignment.centerRight,
          colors: [
            Colors.transparent,
            Colors.white.withValues(alpha: 0.08),
            Colors.transparent,
          ],
          stops: const [0, 0.5, 1],
        ),
      ),
    ).animate(onPlay: (c) => c.repeat())
        .slideX(begin: -1, end: 2, duration: 2200.ms, curve: Curves.easeInOut);
  }
}

// ─── LINK PREVIEW ─────────────────────────────────────────────

class LinkPreview extends StatefulWidget {
  final LinkPreviewData data;
  final VoidCallback? onTap;
  const LinkPreview({super.key, required this.data, this.onTap});
  @override
  State<LinkPreview> createState() => _LinkPreviewState();
}

class _LinkPreviewState extends State<LinkPreview> {
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
          duration: const Duration(milliseconds: 150),
          width: 280,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(10),
            color: Colors.white.withValues(alpha: _h ? 0.05 : 0.03),
            border: Border.all(color: Colors.white.withValues(alpha: 0.06), width: 0.5),
          ),
          child: ClipRRect(
            borderRadius: BorderRadius.circular(10),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                _StubImage(tint: widget.data.tint, width: 280, height: 130),
                Padding(
                  padding: const EdgeInsets.all(10),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(widget.data.title,
                          maxLines: 2, overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontFamily: 'Onest', fontSize: 13, fontWeight: FontWeight.w600,
                            color: BColors.textPrimary, height: 1.3,
                          )),
                      const SizedBox(height: 2),
                      Text(widget.data.description,
                          maxLines: 2, overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontFamily: 'Onest', fontSize: 12,
                            color: BColors.textSecondary, height: 1.35,
                          )),
                      const SizedBox(height: 4),
                      Text(widget.data.domain, style: TextStyle(
                        fontFamily: 'Onest', fontSize: 11,
                        color: BColors.textMuted.withValues(alpha: 0.8),
                      )),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
