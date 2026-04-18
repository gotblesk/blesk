import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:solar_icons/solar_icons.dart';

import '../shared/theme.dart';

// ═══════════════════════════════════════════════════════════════
// CHAT SEARCH BAR — Ctrl+F in-chat search with match navigation
// ═══════════════════════════════════════════════════════════════

class ChatSearchBar extends StatefulWidget {
  final ValueChanged<String> onQueryChanged;
  final VoidCallback onPrev;
  final VoidCallback onNext;
  final VoidCallback onClose;
  final int matchCount;
  final int currentMatch; // 1-based or 0 when no matches

  const ChatSearchBar({
    super.key,
    required this.onQueryChanged,
    required this.onPrev,
    required this.onNext,
    required this.onClose,
    required this.matchCount,
    required this.currentMatch,
  });

  @override
  State<ChatSearchBar> createState() => _ChatSearchBarState();
}

class _ChatSearchBarState extends State<ChatSearchBar> {
  final _ctrl = TextEditingController();
  final _focus = FocusNode();

  @override
  void initState() {
    super.initState();
    _focus.requestFocus();
  }

  @override
  void dispose() {
    _ctrl.dispose();
    _focus.dispose();
    super.dispose();
  }

  void _handleKey(KeyEvent e) {
    if (e is! KeyDownEvent) return;
    final k = e.logicalKey;
    if (k == LogicalKeyboardKey.escape) {
      widget.onClose();
    } else if (k == LogicalKeyboardKey.enter) {
      final isShift = HardwareKeyboard.instance.isShiftPressed;
      if (isShift) {
        widget.onPrev();
      } else {
        widget.onNext();
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final count = widget.matchCount;
    final current = widget.currentMatch;
    return Container(
      height: 42,
      padding: const EdgeInsets.symmetric(horizontal: 14),
      decoration: const BoxDecoration(
        color: Color(0x0AFFFFFF),
        border: Border(bottom: BorderSide(color: BColors.borderLow)),
      ),
      child: KeyboardListener(
        focusNode: FocusNode()..requestFocus(),
        onKeyEvent: _handleKey,
        child: Row(children: [
          Icon(SolarIconsOutline.magnifier, size: 15,
              color: BColors.textMuted.withValues(alpha: 0.9)),
          const SizedBox(width: 10),
          Expanded(child: TextField(
            controller: _ctrl,
            focusNode: _focus,
            style: const TextStyle(
              fontFamily: 'Onest', fontSize: 13, color: BColors.textPrimary,
            ),
            cursorColor: BColors.accent,
            onChanged: widget.onQueryChanged,
            decoration: const InputDecoration(
              border: InputBorder.none, isDense: true,
              hintText: 'искать в чате...',
              hintStyle: TextStyle(
                fontFamily: 'Onest', fontSize: 13, color: BColors.textMuted,
              ),
              contentPadding: EdgeInsets.symmetric(vertical: 12),
            ),
          )),
          if (_ctrl.text.isNotEmpty) _Counter(count: count, current: current),
          const SizedBox(width: 6),
          _MiniBtn(
            icon: SolarIconsOutline.altArrowUp,
            tooltip: 'предыдущее',
            enabled: count > 0,
            onTap: widget.onPrev,
          ),
          _MiniBtn(
            icon: SolarIconsOutline.altArrowDown,
            tooltip: 'следующее',
            enabled: count > 0,
            onTap: widget.onNext,
          ),
          const SizedBox(width: 4),
          _MiniBtn(
            icon: SolarIconsOutline.closeCircle, tooltip: 'закрыть (esc)',
            enabled: true, onTap: widget.onClose,
          ),
        ]),
      ),
    ).animate().fadeIn(duration: 160.ms).slideY(begin: -0.3, curve: Curves.easeOut);
  }
}

class _Counter extends StatelessWidget {
  final int count, current;
  const _Counter({required this.count, required this.current});

  @override
  Widget build(BuildContext context) {
    final none = count == 0;
    return Container(
      margin: const EdgeInsets.only(right: 8),
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(4),
        color: none
            ? const Color(0x33ff5c5c)
            : BColors.accent.withValues(alpha: 0.12),
      ),
      child: Text(
        none ? 'нет' : '$current / $count',
        style: TextStyle(
          fontFamily: 'Onest', fontSize: 10, fontWeight: FontWeight.w600,
          color: none
              ? const Color(0xFFff9b9b)
              : BColors.accent.withValues(alpha: 0.95),
          letterSpacing: 0.3,
        ),
      ),
    );
  }
}

class _MiniBtn extends StatefulWidget {
  final IconData icon;
  final String tooltip;
  final bool enabled;
  final VoidCallback onTap;
  const _MiniBtn({
    required this.icon, required this.tooltip,
    required this.enabled, required this.onTap,
  });
  @override
  State<_MiniBtn> createState() => _MiniBtnState();
}

class _MiniBtnState extends State<_MiniBtn> {
  bool _h = false;
  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: widget.tooltip,
      textStyle: const TextStyle(fontSize: 10, color: BColors.textPrimary),
      decoration: BoxDecoration(
        color: const Color(0xFF1a1a1a), borderRadius: BorderRadius.circular(6),
      ),
      child: MouseRegion(
        cursor: widget.enabled ? SystemMouseCursors.click : MouseCursor.defer,
        onEnter: (_) => setState(() => _h = true),
        onExit: (_) => setState(() => _h = false),
        child: GestureDetector(
          onTap: widget.enabled ? widget.onTap : null,
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 100),
            width: 26, height: 26,
            margin: const EdgeInsets.symmetric(horizontal: 1),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(5),
              color: _h && widget.enabled
                  ? Colors.white.withValues(alpha: 0.08)
                  : Colors.transparent,
            ),
            child: Icon(widget.icon, size: 14,
                color: widget.enabled
                    ? (_h ? BColors.textPrimary : BColors.textSecondary)
                    : BColors.textMuted.withValues(alpha: 0.3)),
          ),
        ),
      ),
    );
  }
}
