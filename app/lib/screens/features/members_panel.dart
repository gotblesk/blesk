import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:solar_icons/solar_icons.dart';

import '../shared/theme.dart';
import 'chat_bubble_parts.dart' show senderColorFor;

// ═══════════════════════════════════════════════════════════════
// MEMBERS PANEL — group members slide-over with roles
// ═══════════════════════════════════════════════════════════════

enum MemberRole { owner, admin, member }

class Member {
  final String id, name, handle, initial;
  final bool online;
  final String lastSeen;
  final MemberRole role;
  const Member({
    required this.id, required this.name, required this.handle,
    required this.initial, required this.online,
    required this.lastSeen, required this.role,
  });
}

// ─── STUB DATA ────────────────────────────────────────────────

List<Member> stubMembers(String chatId) {
  switch (chatId) {
    case 'c2': return const [
      Member(id: 'm_self', name: 'gotblesk', handle: '@gotblesk',
          initial: 'g', online: true, lastSeen: 'в сети', role: MemberRole.owner),
      Member(id: 'm_k', name: 'Катя', handle: '@katya_design',
          initial: 'К', online: true, lastSeen: 'в сети', role: MemberRole.admin),
      Member(id: 'm_a', name: 'Артём', handle: '@artem_404',
          initial: 'А', online: true, lastSeen: 'в сети', role: MemberRole.member),
      Member(id: 'm_m', name: 'Максим', handle: '@maxsmith',
          initial: 'М', online: true, lastSeen: 'в сети', role: MemberRole.member),
      Member(id: 'm_l', name: 'Лёша', handle: '@alexdev',
          initial: 'Л', online: false, lastSeen: 'был вчера', role: MemberRole.member),
      Member(id: 'm_an', name: 'Аня', handle: '@annayaa',
          initial: 'А', online: false, lastSeen: 'был 3 часа назад', role: MemberRole.member),
    ];
    case 'c4': return const [
      Member(id: 'm_self', name: 'gotblesk', handle: '@gotblesk',
          initial: 'g', online: true, lastSeen: 'в сети', role: MemberRole.owner),
      Member(id: 'm_bot', name: 'blesk bot', handle: '@blesk_bot',
          initial: 'B', online: true, lastSeen: 'в сети', role: MemberRole.admin),
    ];
    default: return const [];
  }
}

// ─── PANEL ROOT ───────────────────────────────────────────────

OverlayEntry showMembersPanel(BuildContext context, {
  required String chatId,
  required String groupName,
}) {
  final overlay = Overlay.of(context);
  late OverlayEntry entry;
  entry = OverlayEntry(builder: (_) => MembersPanel(
    chatId: chatId,
    groupName: groupName,
    onClose: () => entry.remove(),
  ));
  overlay.insert(entry);
  return entry;
}

class MembersPanel extends StatefulWidget {
  final String chatId, groupName;
  final VoidCallback onClose;
  const MembersPanel({
    super.key, required this.chatId, required this.groupName,
    required this.onClose,
  });
  @override
  State<MembersPanel> createState() => _MembersPanelState();
}

class _MembersPanelState extends State<MembersPanel> {
  final _search = TextEditingController();
  String _query = '';

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final members = stubMembers(widget.chatId);
    final q = _query.trim().toLowerCase();
    final filtered = q.isEmpty
        ? members
        : members.where((m) =>
            m.name.toLowerCase().contains(q) ||
            m.handle.toLowerCase().contains(q)).toList();
    final owner = filtered.where((m) => m.role == MemberRole.owner).toList();
    final admins = filtered.where((m) => m.role == MemberRole.admin).toList();
    final regular = filtered.where((m) => m.role == MemberRole.member).toList();

    return KeyboardListener(
      focusNode: FocusNode()..requestFocus(),
      onKeyEvent: (e) {
        if (e is KeyDownEvent && e.logicalKey == LogicalKeyboardKey.escape) {
          widget.onClose();
        }
      },
      child: Material(
        color: Colors.transparent,
        child: Stack(children: [
          Positioned.fill(child: GestureDetector(
            onTap: widget.onClose,
            behavior: HitTestBehavior.opaque,
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
                      groupName: widget.groupName,
                      count: members.length,
                      onClose: widget.onClose,
                    ),
                    _SearchBar(controller: _search,
                        onChanged: (v) => setState(() => _query = v)),
                    Expanded(child: ListView(
                      padding: const EdgeInsets.only(bottom: 16),
                      children: [
                        if (owner.isNotEmpty) ...[
                          const _SectionHeader(title: 'создатель'),
                          for (final m in owner) _MemberRow(member: m),
                        ],
                        if (admins.isNotEmpty) ...[
                          const _SectionHeader(title: 'админы'),
                          for (final m in admins) _MemberRow(member: m),
                        ],
                        if (regular.isNotEmpty) ...[
                          const _SectionHeader(title: 'участники'),
                          for (final m in regular) _MemberRow(member: m),
                        ],
                        const SizedBox(height: 10),
                        _AddMemberBtn(onTap: () {}),
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

// ─── HEADER ───────────────────────────────────────────────────

class _Header extends StatelessWidget {
  final String groupName;
  final int count;
  final VoidCallback onClose;
  const _Header({
    required this.groupName, required this.count, required this.onClose,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 56,
      padding: const EdgeInsets.symmetric(horizontal: 18),
      decoration: const BoxDecoration(
        border: Border(bottom: BorderSide(color: BColors.borderLow)),
      ),
      child: Row(children: [
        Expanded(child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('участники', style: const TextStyle(
              fontFamily: 'Nekst', fontSize: 14, fontWeight: FontWeight.w600,
              color: BColors.textPrimary,
            )),
            Text('$count · $groupName', maxLines: 1, overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  fontFamily: 'Onest', fontSize: 11, color: BColors.textMuted,
                )),
          ],
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

// ─── SEARCH ───────────────────────────────────────────────────

class _SearchBar extends StatelessWidget {
  final TextEditingController controller;
  final ValueChanged<String> onChanged;
  const _SearchBar({required this.controller, required this.onChanged});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(14, 10, 14, 8),
      child: Container(
        height: 34,
        padding: const EdgeInsets.symmetric(horizontal: 10),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(8),
          color: Colors.white.withValues(alpha: 0.04),
          border: Border.all(color: Colors.white.withValues(alpha: 0.05), width: 0.5),
        ),
        child: Row(children: [
          Icon(SolarIconsOutline.magnifier, size: 14,
              color: BColors.textMuted.withValues(alpha: 0.9)),
          const SizedBox(width: 8),
          Expanded(child: TextField(
            controller: controller,
            onChanged: onChanged,
            style: const TextStyle(
              fontFamily: 'Onest', fontSize: 12, color: BColors.textPrimary,
            ),
            cursorColor: BColors.accent,
            decoration: const InputDecoration(
              border: InputBorder.none, isDense: true,
              hintText: 'поиск участника...',
              hintStyle: TextStyle(
                fontFamily: 'Onest', fontSize: 12, color: BColors.textMuted,
              ),
              contentPadding: EdgeInsets.symmetric(vertical: 7),
            ),
          )),
        ]),
      ),
    );
  }
}

// ─── SECTION HEADER ───────────────────────────────────────────

class _SectionHeader extends StatelessWidget {
  final String title;
  const _SectionHeader({required this.title});
  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 12, 20, 4),
      child: Text(title.toUpperCase(), style: const TextStyle(
        fontFamily: 'Onest', fontSize: 10, fontWeight: FontWeight.w600,
        color: BColors.textMuted, letterSpacing: 1.2,
      )),
    );
  }
}

// ─── MEMBER ROW ───────────────────────────────────────────────

class _MemberRow extends StatefulWidget {
  final Member member;
  const _MemberRow({required this.member});
  @override
  State<_MemberRow> createState() => _MemberRowState();
}

class _MemberRowState extends State<_MemberRow> {
  bool _h = false;

  void _showContextMenu(Offset pos) {
    final m = widget.member;
    final overlay = Overlay.of(context);
    late OverlayEntry entry;
    final canManage = m.role != MemberRole.owner;
    entry = OverlayEntry(builder: (_) => _CtxMenu(
      position: pos,
      items: [
        const ('profile', 'профиль', SolarIconsOutline.user, false),
        const ('message', 'написать', SolarIconsOutline.chatRound, false),
        const ('call', 'позвонить', SolarIconsOutline.phone, false),
        if (canManage) const (null, null, null, false),
        if (canManage) m.role == MemberRole.admin
            ? const ('demote', 'снять админа', SolarIconsOutline.shieldCross, false)
            : const ('promote', 'назначить админом', SolarIconsOutline.shield, false),
        if (canManage) const ('mute', 'заглушить', SolarIconsOutline.volumeCross, false),
        if (canManage) const ('kick', 'удалить из группы', SolarIconsOutline.userMinus, true),
      ],
      onClose: () => entry.remove(),
    ));
    overlay.insert(entry);
  }

  @override
  Widget build(BuildContext context) {
    final m = widget.member;
    final tint = senderColorFor(m.name);
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setState(() => _h = true),
      onExit: (_) => setState(() => _h = false),
      child: GestureDetector(
        onSecondaryTapDown: (d) => _showContextMenu(d.globalPosition),
        onTapDown: (d) { /* tap to open profile later */ },
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 100),
          margin: const EdgeInsets.symmetric(horizontal: 10, vertical: 1),
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(8),
            color: _h ? Colors.white.withValues(alpha: 0.04) : Colors.transparent,
          ),
          child: Row(children: [
            Stack(children: [
              Container(
                width: 36, height: 36,
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(10),
                  color: tint.withValues(alpha: 0.16),
                  border: Border.all(color: tint.withValues(alpha: 0.28), width: 0.5),
                ),
                child: Center(child: Text(m.initial, style: TextStyle(
                  fontFamily: 'Onest', fontSize: 13, fontWeight: FontWeight.w700,
                  color: tint.withValues(alpha: 0.95),
                ))),
              ),
              if (m.online) Positioned(
                bottom: 0, right: 0,
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
                Row(children: [
                  Flexible(child: Text(m.name, maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontFamily: 'Onest', fontSize: 13, fontWeight: FontWeight.w500,
                        color: BColors.textPrimary,
                      ))),
                  const SizedBox(width: 6),
                  _RoleBadge(role: m.role),
                ]),
                Text(m.online ? m.handle : m.lastSeen,
                    maxLines: 1, overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontFamily: 'Onest', fontSize: 11, color: BColors.textMuted,
                    )),
              ],
            )),
            if (_h) _RowAction(icon: SolarIconsOutline.menuDots,
                onTap: () => _showContextMenu(Offset.zero)),
          ]),
        ),
      ),
    );
  }
}

class _RoleBadge extends StatelessWidget {
  final MemberRole role;
  const _RoleBadge({required this.role});
  @override
  Widget build(BuildContext context) {
    switch (role) {
      case MemberRole.owner:
        return _Badge(
          icon: SolarIconsOutline.crown,
          tint: const Color(0xFFFFD166),
        );
      case MemberRole.admin:
        return _Badge(icon: SolarIconsOutline.shield, tint: BColors.accent);
      case MemberRole.member:
        return const SizedBox.shrink();
    }
  }
}

class _Badge extends StatelessWidget {
  final IconData icon;
  final Color tint;
  const _Badge({required this.icon, required this.tint});
  @override
  Widget build(BuildContext context) {
    return Container(
      width: 18, height: 18,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(5),
        color: tint.withValues(alpha: 0.15),
      ),
      child: Icon(icon, size: 10, color: tint.withValues(alpha: 0.95)),
    );
  }
}

class _RowAction extends StatelessWidget {
  final IconData icon;
  final VoidCallback onTap;
  const _RowAction({required this.icon, required this.onTap});
  @override
  Widget build(BuildContext context) {
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      child: GestureDetector(
        onTap: onTap,
        child: Container(
          width: 24, height: 24,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(5),
            color: Colors.white.withValues(alpha: 0.06),
          ),
          child: Icon(icon, size: 14, color: BColors.textSecondary),
        ),
      ),
    );
  }
}

// ─── ADD MEMBER BUTTON ────────────────────────────────────────

class _AddMemberBtn extends StatefulWidget {
  final VoidCallback onTap;
  const _AddMemberBtn({required this.onTap});
  @override
  State<_AddMemberBtn> createState() => _AddMemberBtnState();
}

class _AddMemberBtnState extends State<_AddMemberBtn> {
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
          margin: const EdgeInsets.symmetric(horizontal: 14),
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(8),
            color: _h
                ? BColors.accent.withValues(alpha: 0.1)
                : BColors.accent.withValues(alpha: 0.06),
            border: Border.all(
              color: BColors.accent.withValues(alpha: _h ? 0.3 : 0.18),
              width: 0.5,
            ),
          ),
          child: Row(mainAxisSize: MainAxisSize.min, children: [
            Icon(SolarIconsOutline.userPlus, size: 14, color: BColors.accent),
            const SizedBox(width: 8),
            Text('добавить участника', style: TextStyle(
              fontFamily: 'Onest', fontSize: 12, fontWeight: FontWeight.w500,
              color: BColors.accent.withValues(alpha: 0.95),
            )),
          ]),
        ),
      ),
    );
  }
}

// ─── CONTEXT MENU ─────────────────────────────────────────────

class _CtxMenu extends StatelessWidget {
  final Offset position;
  final List<(String?, String?, IconData?, bool)> items;
  final VoidCallback onClose;
  const _CtxMenu({required this.position, required this.items, required this.onClose});

  @override
  Widget build(BuildContext context) {
    final screen = MediaQuery.of(context).size;
    const menuW = 210.0;
    final visibleItems = items.where((i) => i.$1 != null).length;
    final menuH = visibleItems * 32.0 + 10;
    final left = (position.dx - menuW).clamp(12.0, screen.width - menuW - 12);
    final top = (position.dy + menuH < screen.height)
        ? position.dy + 4 : position.dy - menuH - 4;

    return Material(color: Colors.transparent, child: Stack(children: [
      Positioned.fill(child: GestureDetector(
        onTap: onClose,
        behavior: HitTestBehavior.translucent,
        child: const SizedBox.expand(),
      )),
      Positioned(left: left, top: top, child: Container(
        width: menuW,
        padding: const EdgeInsets.symmetric(vertical: 5),
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
            it.$1 == null
                ? Container(
                    height: 1, margin: const EdgeInsets.symmetric(vertical: 4),
                    color: Colors.white.withValues(alpha: 0.05),
                  )
                : _CtxRow(
                    label: it.$2!, icon: it.$3!, danger: it.$4,
                    onTap: onClose,
                  ),
        ]),
      ).animate()
          .scale(begin: const Offset(0.95, 0.95), duration: 140.ms, curve: Curves.easeOutCubic)
          .fade(duration: 120.ms)),
    ]));
  }
}

class _CtxRow extends StatefulWidget {
  final String label;
  final IconData icon;
  final bool danger;
  final VoidCallback onTap;
  const _CtxRow({
    required this.label, required this.icon,
    required this.danger, required this.onTap,
  });
  @override
  State<_CtxRow> createState() => _CtxRowState();
}

class _CtxRowState extends State<_CtxRow> {
  bool _h = false;
  @override
  Widget build(BuildContext context) {
    final color = widget.danger
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
          height: 28,
          margin: const EdgeInsets.symmetric(horizontal: 4),
          padding: const EdgeInsets.symmetric(horizontal: 10),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(5),
            color: _h
                ? (widget.danger
                    ? const Color(0xFFff5c5c).withValues(alpha: 0.08)
                    : Colors.white.withValues(alpha: 0.05))
                : Colors.transparent,
          ),
          child: Row(children: [
            Icon(widget.icon, size: 13, color: color),
            const SizedBox(width: 10),
            Text(widget.label, style: TextStyle(
              fontFamily: 'Onest', fontSize: 12, color: color,
            )),
          ]),
        ),
      ),
    );
  }
}
