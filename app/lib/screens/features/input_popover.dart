import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:solar_icons/solar_icons.dart';

import '../shared/theme.dart';
import 'chat_bubble_parts.dart' show senderColorFor;

// ═══════════════════════════════════════════════════════════════
// INPUT POPOVERS — @mentions (B6) and /slash (B7)
// ═══════════════════════════════════════════════════════════════

// ─── MENTION ITEM ─────────────────────────────────────────────

class MentionItem {
  final String id, name, handle, initial;
  final bool online;
  const MentionItem({
    required this.id, required this.name, required this.handle,
    required this.initial, this.online = false,
  });
}

// ─── SLASH COMMAND ────────────────────────────────────────────

class SlashCommand {
  final String cmd, description;
  final IconData icon;
  final bool isBot;
  final String? insertsText; // if set, selecting inserts this instead of /cmd
  const SlashCommand({
    required this.cmd, required this.description, required this.icon,
    this.isBot = false, this.insertsText,
  });
}

// ─── STUB DATASETS ────────────────────────────────────────────

const List<MentionItem> stubMentions = [
  MentionItem(id: 'all', name: '@all', handle: 'упомянуть всех', initial: '@', online: true),
  MentionItem(id: 'katya', name: 'Катя', handle: '@katya_design', initial: 'К', online: true),
  MentionItem(id: 'maxim', name: 'Максим', handle: '@maxsmith', initial: 'М', online: true),
  MentionItem(id: 'artem', name: 'Артём', handle: '@artem_404', initial: 'А', online: false),
  MentionItem(id: 'lesha', name: 'Лёша', handle: '@alexdev', initial: 'Л', online: true),
  MentionItem(id: 'anya', name: 'Аня', handle: '@annayaa', initial: 'А', online: false),
  MentionItem(id: 'liza', name: 'Лиза', handle: '@lizad', initial: 'Л', online: true),
];

const List<SlashCommand> stubCommands = [
  SlashCommand(cmd: '/shrug', description: r'¯\_(ツ)_/¯',
      icon: SolarIconsOutline.smileCircle, insertsText: r'¯\_(ツ)_/¯'),
  SlashCommand(cmd: '/me', description: 'действие от 3-го лица',
      icon: SolarIconsOutline.user),
  SlashCommand(cmd: '/poll', description: 'создать опрос',
      icon: SolarIconsOutline.chartSquare),
  SlashCommand(cmd: '/gif', description: 'найти GIF',
      icon: SolarIconsOutline.gallery),
  SlashCommand(cmd: '/call', description: 'начать звонок',
      icon: SolarIconsOutline.phone),
  SlashCommand(cmd: '/location', description: 'поделиться геолокацией',
      icon: SolarIconsOutline.global),
  SlashCommand(cmd: '/silent', description: 'следующее без уведомления',
      icon: SolarIconsOutline.bellOff),
  SlashCommand(cmd: '/remind', description: 'напоминание',
      icon: SolarIconsOutline.clockCircle),
  SlashCommand(cmd: '/clear', description: 'локально очистить',
      icon: SolarIconsOutline.trashBinTrash),
];

// ─── TRIGGER DETECTION ────────────────────────────────────────

/// Returns the query after `@` if the cursor is inside an @-word,
/// else null. The query is text between `@` and cursor (exclusive).
String? detectMentionTrigger(String text, int cursorPos) {
  if (cursorPos <= 0 || cursorPos > text.length) return null;
  // Walk backwards from cursor to find the start of current word
  int start = cursorPos - 1;
  while (start >= 0) {
    final c = text[start];
    if (c == '@') {
      // Check that @ is at start of text OR preceded by whitespace
      if (start == 0 || text[start - 1] == ' ' || text[start - 1] == '\n') {
        return text.substring(start + 1, cursorPos);
      }
      return null;
    }
    if (c == ' ' || c == '\n') return null;
    start--;
  }
  return null;
}

/// Returns the query after `/` if the input starts with /cmd and cursor
/// is within that first token. Null otherwise.
String? detectSlashTrigger(String text, int cursorPos) {
  if (text.isEmpty || text[0] != '/') return null;
  // Find first space or newline
  final firstBreak = text.indexOf(RegExp(r'[\s\n]'));
  final tokenEnd = firstBreak < 0 ? text.length : firstBreak;
  if (cursorPos < 1 || cursorPos > tokenEnd) return null;
  return text.substring(1, cursorPos);
}

// ─── MENTIONS POPOVER ─────────────────────────────────────────

class MentionsPopover extends StatelessWidget {
  final String query;
  final int activeIndex;
  final ValueChanged<MentionItem> onSelect;
  final ValueChanged<int>? onHoverIndex;
  const MentionsPopover({
    super.key, required this.query, required this.activeIndex,
    required this.onSelect, this.onHoverIndex,
  });

  List<MentionItem> get _filtered {
    final q = query.trim().toLowerCase();
    if (q.isEmpty) return stubMentions;
    return stubMentions.where((m) =>
        m.name.toLowerCase().contains(q) ||
        m.handle.toLowerCase().contains(q)).toList();
  }

  @override
  Widget build(BuildContext context) {
    final items = _filtered;
    return _PopoverFrame(
      child: items.isEmpty
          ? const _EmptyPopover(text: 'нет совпадений')
          : ListView.builder(
              padding: const EdgeInsets.symmetric(vertical: 4),
              shrinkWrap: true,
              itemCount: items.length,
              itemBuilder: (_, i) {
                final m = items[i];
                final tint = senderColorFor(m.name);
                return _PopoverRow(
                  active: i == activeIndex,
                  onTap: () => onSelect(m),
                  onHover: () => onHoverIndex?.call(i),
                  leading: _AvatarBadge(initial: m.initial, tint: tint, online: m.online),
                  title: m.name,
                  subtitle: m.handle,
                );
              },
            ),
    );
  }
}

// ─── SLASH POPOVER ────────────────────────────────────────────

class SlashPopover extends StatelessWidget {
  final String query;
  final int activeIndex;
  final ValueChanged<SlashCommand> onSelect;
  final ValueChanged<int>? onHoverIndex;
  const SlashPopover({
    super.key, required this.query, required this.activeIndex,
    required this.onSelect, this.onHoverIndex,
  });

  List<SlashCommand> get _filtered {
    final q = query.trim().toLowerCase();
    if (q.isEmpty) return stubCommands;
    return stubCommands.where((c) =>
        c.cmd.toLowerCase().contains(q) ||
        c.description.toLowerCase().contains(q)).toList();
  }

  @override
  Widget build(BuildContext context) {
    final items = _filtered;
    return _PopoverFrame(
      child: items.isEmpty
          ? const _EmptyPopover(text: 'команда не найдена')
          : ListView.builder(
              padding: const EdgeInsets.symmetric(vertical: 4),
              shrinkWrap: true,
              itemCount: items.length,
              itemBuilder: (_, i) {
                final c = items[i];
                return _PopoverRow(
                  active: i == activeIndex,
                  onTap: () => onSelect(c),
                  onHover: () => onHoverIndex?.call(i),
                  leading: Container(
                    width: 28, height: 28,
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(7),
                      color: BColors.accent.withValues(alpha: 0.1),
                    ),
                    child: Icon(c.icon, size: 14,
                        color: BColors.accent.withValues(alpha: 0.9)),
                  ),
                  title: c.cmd,
                  subtitle: c.description,
                  trailing: c.isBot ? const _BotBadge() : null,
                );
              },
            ),
    );
  }
}

// ─── SHARED POPOVER UI ────────────────────────────────────────

class _PopoverFrame extends StatelessWidget {
  final Widget child;
  const _PopoverFrame({required this.child});
  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: Container(
        width: 320, constraints: const BoxConstraints(maxHeight: 240),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(12),
          color: const Color(0xF5141418),
          border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
          boxShadow: const [BoxShadow(
            color: Color(0x99000000), blurRadius: 40, offset: Offset(0, -8),
          )],
        ),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(12),
          child: child,
        ),
      ).animate()
          .scale(begin: const Offset(0.96, 0.96),
              duration: 140.ms, curve: Curves.easeOutCubic)
          .fade(duration: 120.ms),
    );
  }
}

class _PopoverRow extends StatefulWidget {
  final bool active;
  final VoidCallback onTap;
  final VoidCallback? onHover;
  final Widget leading;
  final String title, subtitle;
  final Widget? trailing;
  const _PopoverRow({
    required this.active, required this.onTap, this.onHover,
    required this.leading, required this.title, required this.subtitle,
    this.trailing,
  });
  @override
  State<_PopoverRow> createState() => _PopoverRowState();
}

class _PopoverRowState extends State<_PopoverRow> {
  @override
  Widget build(BuildContext context) {
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => widget.onHover?.call(),
      child: GestureDetector(
        onTap: widget.onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 100),
          height: 40,
          margin: const EdgeInsets.symmetric(horizontal: 4),
          padding: const EdgeInsets.symmetric(horizontal: 8),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(7),
            color: widget.active
                ? BColors.accent.withValues(alpha: 0.1)
                : Colors.transparent,
            border: Border(left: BorderSide(
              color: widget.active
                  ? BColors.accent : Colors.transparent,
              width: widget.active ? 2.5 : 0,
            )),
          ),
          child: Row(children: [
            widget.leading,
            const SizedBox(width: 10),
            Expanded(child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text(widget.title,
                    maxLines: 1, overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontFamily: 'Onest', fontSize: 13,
                      fontWeight: widget.active ? FontWeight.w600 : FontWeight.w500,
                      color: BColors.textPrimary,
                    )),
                Text(widget.subtitle,
                    maxLines: 1, overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontFamily: 'Onest', fontSize: 11, color: BColors.textMuted,
                    )),
              ],
            )),
            if (widget.trailing != null) widget.trailing!,
          ]),
        ),
      ),
    );
  }
}

class _AvatarBadge extends StatelessWidget {
  final String initial;
  final Color tint;
  final bool online;
  const _AvatarBadge({required this.initial, required this.tint, required this.online});
  @override
  Widget build(BuildContext context) {
    return Stack(children: [
      Container(
        width: 28, height: 28,
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(8),
          color: tint.withValues(alpha: 0.14),
          border: Border.all(color: tint.withValues(alpha: 0.22), width: 0.5),
        ),
        child: Center(child: Text(initial, style: TextStyle(
          fontFamily: 'Onest', fontSize: 11, fontWeight: FontWeight.w700,
          color: tint.withValues(alpha: 0.95),
        ))),
      ),
      if (online) Positioned(
        right: 0, bottom: 0,
        child: Container(
          width: 8, height: 8,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: const Color(0xFF4ade80),
            border: Border.all(color: const Color(0xFF141418), width: 1.5),
          ),
        ),
      ),
    ]);
  }
}

class _BotBadge extends StatelessWidget {
  const _BotBadge();
  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 2),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(4),
        color: BColors.accent.withValues(alpha: 0.12),
      ),
      child: Text('бот', style: TextStyle(
        fontFamily: 'Onest', fontSize: 9, fontWeight: FontWeight.w700,
        color: BColors.accent.withValues(alpha: 0.9),
        letterSpacing: 0.4,
      )),
    );
  }
}

class _EmptyPopover extends StatelessWidget {
  final String text;
  const _EmptyPopover({required this.text});
  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 20, horizontal: 14),
      child: Center(child: Text(text, style: const TextStyle(
        fontFamily: 'Onest', fontSize: 12, color: BColors.textMuted,
      ))),
    );
  }
}
