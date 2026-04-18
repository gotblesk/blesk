import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:solar_icons/solar_icons.dart';

import '../shared/theme.dart';
import 'chat_messages.dart';

// ═══════════════════════════════════════════════════════════════
// BOOKMARKS PANEL (C6) — slide-over with personal message bookmarks
// ═══════════════════════════════════════════════════════════════

OverlayEntry showBookmarksPanel(BuildContext context, {
  ValueChanged<BookmarkEntry>? onOpen,
}) {
  final overlay = Overlay.of(context);
  late OverlayEntry entry;
  entry = OverlayEntry(builder: (_) => _BookmarksPanel(
    onClose: () => entry.remove(),
    onOpen: (b) {
      entry.remove();
      onOpen?.call(b);
    },
  ));
  overlay.insert(entry);
  return entry;
}

class _BookmarksPanel extends StatelessWidget {
  final VoidCallback onClose;
  final ValueChanged<BookmarkEntry> onOpen;
  const _BookmarksPanel({required this.onClose, required this.onOpen});

  @override
  Widget build(BuildContext context) {
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
                    _Header(onClose: onClose),
                    Expanded(
                      child: ValueListenableBuilder<int>(
                        valueListenable: bookmarksVersion,
                        builder: (_, _, _) => stubBookmarks.isEmpty
                            ? const _EmptyState()
                            : ListView.builder(
                                padding: const EdgeInsets.fromLTRB(10, 10, 10, 16),
                                itemCount: stubBookmarks.length,
                                itemBuilder: (_, i) {
                                  final b = stubBookmarks[stubBookmarks.length - 1 - i];
                                  return _BookmarkCard(
                                    entry: b,
                                    onTap: () => onOpen(b),
                                    onRemove: () => removeBookmark(b.id),
                                  );
                                },
                              ),
                      ),
                    ),
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
  final VoidCallback onClose;
  const _Header({required this.onClose});
  @override
  Widget build(BuildContext context) {
    return Container(
      height: 52,
      padding: const EdgeInsets.symmetric(horizontal: 18),
      decoration: const BoxDecoration(
        border: Border(bottom: BorderSide(color: BColors.borderLow)),
      ),
      child: Row(children: [
        Icon(SolarIconsBold.bookmark, size: 16, color: BColors.accent.withValues(alpha: 0.9)),
        const SizedBox(width: 8),
        Expanded(child: ValueListenableBuilder<int>(
          valueListenable: bookmarksVersion,
          builder: (_, _, _) => Text(
            'закладки · ${stubBookmarks.length}',
            style: const TextStyle(
              fontFamily: 'Nekst', fontSize: 14, fontWeight: FontWeight.w600,
              color: BColors.textPrimary,
            ),
          ),
        )),
        _CloseBtn(onTap: onClose),
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

class _BookmarkCard extends StatefulWidget {
  final BookmarkEntry entry;
  final VoidCallback onTap;
  final VoidCallback onRemove;
  const _BookmarkCard({
    required this.entry, required this.onTap, required this.onRemove,
  });
  @override
  State<_BookmarkCard> createState() => _BookmarkCardState();
}

class _BookmarkCardState extends State<_BookmarkCard> {
  bool _h = false;
  @override
  Widget build(BuildContext context) {
    final b = widget.entry;
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setState(() => _h = true),
      onExit: (_) => setState(() => _h = false),
      child: GestureDetector(
        onTap: widget.onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 150),
          margin: const EdgeInsets.symmetric(vertical: 3),
          padding: const EdgeInsets.fromLTRB(14, 10, 10, 10),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(10),
            color: _h
                ? Colors.white.withValues(alpha: 0.06)
                : Colors.white.withValues(alpha: 0.03),
            border: Border.all(
              color: _h
                  ? BColors.accent.withValues(alpha: 0.2)
                  : Colors.white.withValues(alpha: 0.05),
              width: 0.5,
            ),
          ),
          child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Expanded(child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Row(children: [
                  Text('[${b.senderName}]', style: TextStyle(
                    fontFamily: 'Onest', fontSize: 11, fontWeight: FontWeight.w600,
                    color: BColors.accent.withValues(alpha: 0.85),
                  )),
                ]),
                const SizedBox(height: 4),
                Text(b.preview,
                    maxLines: 2, overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontFamily: 'Onest', fontSize: 13,
                      color: BColors.textPrimary, height: 1.4,
                    )),
                const SizedBox(height: 4),
                Text('${b.date} · ${b.chatName}', style: const TextStyle(
                  fontFamily: 'Onest', fontSize: 10, color: BColors.textMuted,
                )),
              ],
            )),
            if (_h) MouseRegion(
              cursor: SystemMouseCursors.click,
              child: GestureDetector(
                onTap: widget.onRemove,
                child: Container(
                  width: 24, height: 24,
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(5),
                    color: Colors.white.withValues(alpha: 0.06),
                  ),
                  child: const Icon(SolarIconsOutline.trashBinTrash, size: 12,
                      color: BColors.textMuted),
                ),
              ),
            ),
          ]),
        ),
      ),
    );
  }
}

class _EmptyState extends StatelessWidget {
  const _EmptyState();
  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(40),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(SolarIconsBroken.bookmark, size: 40,
              color: BColors.textMuted.withValues(alpha: 0.6)),
          const SizedBox(height: 12),
          const Text('закладок пока нет', style: TextStyle(
            fontFamily: 'Onest', fontSize: 14, fontWeight: FontWeight.w500,
            color: BColors.textSecondary,
          )),
          const SizedBox(height: 4),
          Text('клик ПКМ на сообщение → «в закладки»',
            textAlign: TextAlign.center,
            style: TextStyle(
              fontFamily: 'Onest', fontSize: 12,
              color: BColors.textMuted.withValues(alpha: 0.8),
            ),
          ),
        ],
      ),
    );
  }
}
