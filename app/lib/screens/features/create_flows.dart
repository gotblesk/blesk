import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_animate/flutter_animate.dart';

import '../shared/theme.dart';
import 'chat_bubble_parts.dart' show senderColorFor;

// ═══════════════════════════════════════════════════════════════
// CREATE FLOWS — group, contact, channel dialogs
// ═══════════════════════════════════════════════════════════════

// ─── SHARED MODAL WRAPPER ─────────────────────────────────────

class _ModalScaffold extends StatelessWidget {
  final Widget child;
  final double width;
  final VoidCallback onClose;
  const _ModalScaffold({
    required this.child, required this.width, required this.onClose,
  });

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: Stack(children: [
        Positioned.fill(child: GestureDetector(
          onTap: onClose,
          behavior: HitTestBehavior.opaque,
          child: BackdropFilter(
            filter: ImageFilter.blur(sigmaX: 8, sigmaY: 8),
            child: Container(color: Colors.black.withValues(alpha: 0.4)),
          ),
        )),
        Center(child: Container(
          width: width,
          constraints: const BoxConstraints(maxHeight: 640),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(16),
            color: const Color(0xF50e0e12),
            border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(alpha: 0.6),
                blurRadius: 60, offset: const Offset(0, 18),
              ),
            ],
          ),
          child: ClipRRect(
            borderRadius: BorderRadius.circular(16),
            child: child,
          ),
        ).animate()
            .scale(begin: const Offset(0.94, 0.94),
                duration: 180.ms, curve: Curves.easeOutCubic)
            .fade(duration: 140.ms)),
      ]),
    );
  }
}

// ─── MODAL HEADER ─────────────────────────────────────────────

class _ModalHeader extends StatelessWidget {
  final String title;
  final VoidCallback onClose;
  final Widget? leading;
  final Widget? trailing;
  const _ModalHeader({
    required this.title, required this.onClose,
    this.leading, this.trailing,
  });
  @override
  Widget build(BuildContext context) {
    return Container(
      height: 52,
      padding: const EdgeInsets.symmetric(horizontal: 12),
      decoration: const BoxDecoration(
        border: Border(bottom: BorderSide(color: BColors.borderLow)),
      ),
      child: Row(children: [
        leading ?? const SizedBox(width: 8),
        const SizedBox(width: 4),
        Expanded(child: Center(child: Text(title, style: const TextStyle(
          fontFamily: 'Nekst', fontSize: 14, fontWeight: FontWeight.w600,
          color: BColors.textPrimary, letterSpacing: 0.3,
        )))),
        if (trailing != null) trailing!
        else _IconBtn(icon: Icons.close, onTap: onClose),
      ]),
    );
  }
}

class _IconBtn extends StatefulWidget {
  final IconData icon;
  final VoidCallback onTap;
  const _IconBtn({required this.icon, required this.onTap});
  @override
  State<_IconBtn> createState() => _IconBtnState();
}

class _IconBtnState extends State<_IconBtn> {
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
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(7),
            color: _h ? Colors.white.withValues(alpha: 0.06) : Colors.transparent,
          ),
          child: Icon(widget.icon, size: 16,
              color: _h ? BColors.textPrimary : BColors.textSecondary),
        ),
      ),
    );
  }
}

// ═══════════════════════════════════════════════════════════════
// CREATE GROUP (2-step)
// ═══════════════════════════════════════════════════════════════

OverlayEntry showCreateGroup(BuildContext context, {
  ValueChanged<CreatedGroup>? onCreated,
}) {
  final overlay = Overlay.of(context);
  late OverlayEntry entry;
  entry = OverlayEntry(builder: (_) => CreateGroupModal(
    onClose: () => entry.remove(),
    onCreated: (g) { entry.remove(); onCreated?.call(g); },
  ));
  overlay.insert(entry);
  return entry;
}

class CreatedGroup {
  final String name, description;
  final List<String> memberIds;
  const CreatedGroup({required this.name, required this.description,
    required this.memberIds});
}

class CreateGroupModal extends StatefulWidget {
  final VoidCallback onClose;
  final ValueChanged<CreatedGroup> onCreated;
  const CreateGroupModal({
    super.key, required this.onClose, required this.onCreated,
  });
  @override
  State<CreateGroupModal> createState() => _CreateGroupModalState();
}

class _CreateGroupModalState extends State<CreateGroupModal> {
  int _step = 0;
  final _search = TextEditingController();
  final _nameCtrl = TextEditingController();
  final _descCtrl = TextEditingController();
  final Set<String> _selected = {};

  static const _candidates = [
    ('u_katya', 'Катя', '@katya_design', true),
    ('u_maxim', 'Максим', '@maxsmith', true),
    ('u_lesha', 'Лёша', '@alexdev', true),
    ('u_anya', 'Аня', '@annayaa', false),
    ('u_artem', 'Артём', '@artem_404', false),
    ('u_liza', 'Лиза', '@lizad', true),
    ('u_dima', 'Дима', '@dimka', false),
  ];

  @override
  void dispose() {
    _search.dispose();
    _nameCtrl.dispose();
    _descCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return _ModalScaffold(
      width: 520, onClose: widget.onClose,
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        _ModalHeader(
          title: _step == 0 ? 'новая группа' : 'настройка группы',
          onClose: widget.onClose,
          leading: _step == 1
              ? _IconBtn(icon: Icons.arrow_back_ios_new_rounded,
                  onTap: () => setState(() => _step = 0))
              : null,
          trailing: _step == 0
              ? _NextBtn(
                  enabled: _selected.length >= 2,
                  label: 'далее',
                  onTap: () => setState(() => _step = 1),
                )
              : _NextBtn(
                  enabled: _nameCtrl.text.trim().isNotEmpty,
                  label: 'создать',
                  onTap: () => widget.onCreated(CreatedGroup(
                    name: _nameCtrl.text.trim(),
                    description: _descCtrl.text.trim(),
                    memberIds: _selected.toList(),
                  )),
                ),
        ),
        Flexible(child: _step == 0 ? _step0() : _step1()),
      ]),
    );
  }

  Widget _step0() {
    final q = _search.text.trim().toLowerCase();
    final filtered = _candidates.where((c) =>
        c.$2.toLowerCase().contains(q) ||
        c.$3.toLowerCase().contains(q)).toList();
    return Column(mainAxisSize: MainAxisSize.min, children: [
      // Search
      Container(
        padding: const EdgeInsets.fromLTRB(16, 10, 16, 6),
        child: _SearchField(controller: _search,
          hint: 'добавить участников...',
          onChanged: (_) => setState(() {}),
        ),
      ),
      if (_selected.isNotEmpty) _SelectedChips(
        candidates: _candidates,
        selected: _selected,
        onRemove: (id) => setState(() => _selected.remove(id)),
      ),
      const Divider(height: 1, color: BColors.borderLow),
      const Padding(
        padding: EdgeInsets.fromLTRB(20, 10, 20, 4),
        child: Align(alignment: Alignment.centerLeft,
          child: Text('КОНТАКТЫ', style: TextStyle(
            fontFamily: 'Onest', fontSize: 10, fontWeight: FontWeight.w600,
            color: BColors.textMuted, letterSpacing: 1.2,
          )),
        ),
      ),
      Flexible(child: ListView.builder(
        padding: const EdgeInsets.only(bottom: 12),
        shrinkWrap: true,
        itemCount: filtered.length,
        itemBuilder: (_, i) {
          final c = filtered[i];
          return _SelectableContactRow(
            id: c.$1, name: c.$2, handle: c.$3, online: c.$4,
            selected: _selected.contains(c.$1),
            onToggle: () => setState(() {
              if (_selected.contains(c.$1)) {
                _selected.remove(c.$1);
              } else {
                _selected.add(c.$1);
              }
            }),
          );
        },
      )),
    ]);
  }

  Widget _step1() {
    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(20, 20, 20, 16),
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        // Avatar placeholder
        Container(
          width: 84, height: 84,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            gradient: RadialGradient(colors: [
              BColors.accent.withValues(alpha: 0.3),
              BColors.accent.withValues(alpha: 0.08),
            ]),
            border: Border.all(color: BColors.accent.withValues(alpha: 0.3)),
          ),
          child: const Icon(Icons.camera_alt_outlined, size: 26, color: BColors.accent),
        ),
        const SizedBox(height: 6),
        const Text('добавить фото', style: TextStyle(
          fontFamily: 'Onest', fontSize: 11, fontWeight: FontWeight.w500,
          color: BColors.textMuted,
        )),
        const SizedBox(height: 20),
        _FieldLabel(label: 'название группы'),
        _FieldInput(controller: _nameCtrl, hint: 'например: Дизайн-банда',
          onChanged: (_) => setState(() {})),
        const SizedBox(height: 16),
        _FieldLabel(label: 'описание (необязательно)'),
        _FieldInput(controller: _descCtrl, hint: 'о чём группа', maxLines: 3),
        const SizedBox(height: 18),
        _FieldLabel(label: 'участники · ${_selected.length}'),
        const SizedBox(height: 6),
        Wrap(spacing: 6, runSpacing: 6, children: [
          for (final id in _selected)
            _MemberAvatarChip(
              name: _candidates.firstWhere((c) => c.$1 == id).$2,
            ),
        ]),
      ]),
    );
  }
}

class _NextBtn extends StatefulWidget {
  final String label;
  final bool enabled;
  final VoidCallback onTap;
  const _NextBtn({required this.label, required this.enabled, required this.onTap});
  @override
  State<_NextBtn> createState() => _NextBtnState();
}

class _NextBtnState extends State<_NextBtn> {
  bool _h = false;
  @override
  Widget build(BuildContext context) {
    return Opacity(opacity: widget.enabled ? 1 : 0.3,
      child: IgnorePointer(ignoring: !widget.enabled,
        child: MouseRegion(
          cursor: SystemMouseCursors.click,
          onEnter: (_) => setState(() => _h = true),
          onExit: (_) => setState(() => _h = false),
          child: GestureDetector(
            onTap: widget.onTap,
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 120),
              height: 32,
              padding: const EdgeInsets.symmetric(horizontal: 14),
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(7),
                color: _h ? BColors.accent : BColors.accent.withValues(alpha: 0.88),
              ),
              child: Center(child: Text(widget.label, style: const TextStyle(
                fontFamily: 'Onest', fontSize: 12, fontWeight: FontWeight.w600,
                color: BColors.bg,
              ))),
            ),
          ),
        ),
      ),
    );
  }
}

class _SearchField extends StatelessWidget {
  final TextEditingController controller;
  final String hint;
  final ValueChanged<String>? onChanged;
  const _SearchField({
    required this.controller, required this.hint, this.onChanged,
  });
  @override
  Widget build(BuildContext context) {
    return Container(
      height: 36,
      padding: const EdgeInsets.symmetric(horizontal: 10),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(8),
        color: Colors.white.withValues(alpha: 0.04),
        border: Border.all(color: Colors.white.withValues(alpha: 0.06), width: 0.5),
      ),
      child: Row(children: [
        Icon(Icons.search_rounded, size: 15,
            color: BColors.textMuted.withValues(alpha: 0.9)),
        const SizedBox(width: 8),
        Expanded(child: TextField(
          controller: controller,
          onChanged: onChanged,
          style: const TextStyle(
            fontFamily: 'Onest', fontSize: 13, color: BColors.textPrimary,
          ),
          cursorColor: BColors.accent,
          decoration: InputDecoration(
            border: InputBorder.none, isDense: true,
            hintText: hint,
            hintStyle: const TextStyle(
              fontFamily: 'Onest', fontSize: 13, color: BColors.textMuted,
            ),
            contentPadding: const EdgeInsets.symmetric(vertical: 8),
          ),
        )),
      ]),
    );
  }
}

class _SelectedChips extends StatelessWidget {
  final List<(String, String, String, bool)> candidates;
  final Set<String> selected;
  final ValueChanged<String> onRemove;
  const _SelectedChips({
    required this.candidates, required this.selected, required this.onRemove,
  });
  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 4, 16, 8),
      child: Wrap(spacing: 4, runSpacing: 4, children: [
        for (final id in selected) _ContactChip(
          name: candidates.firstWhere((c) => c.$1 == id).$2,
          onRemove: () => onRemove(id),
        ),
      ]),
    );
  }
}

class _ContactChip extends StatefulWidget {
  final String name;
  final VoidCallback onRemove;
  const _ContactChip({required this.name, required this.onRemove});
  @override
  State<_ContactChip> createState() => _ContactChipState();
}

class _ContactChipState extends State<_ContactChip> {
  bool _h = false;
  @override
  Widget build(BuildContext context) {
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setState(() => _h = true),
      onExit: (_) => setState(() => _h = false),
      child: GestureDetector(
        onTap: widget.onRemove,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 120),
          height: 24,
          padding: const EdgeInsets.symmetric(horizontal: 8),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(6),
            color: _h
                ? const Color(0xFFff5c5c).withValues(alpha: 0.15)
                : BColors.accent.withValues(alpha: 0.1),
            border: Border.all(
              color: _h
                  ? const Color(0xFFff5c5c).withValues(alpha: 0.3)
                  : BColors.accent.withValues(alpha: 0.2),
              width: 0.5,
            ),
          ),
          child: Row(mainAxisSize: MainAxisSize.min, children: [
            Text(widget.name, style: TextStyle(
              fontFamily: 'Onest', fontSize: 11, fontWeight: FontWeight.w500,
              color: _h ? const Color(0xFFff9b9b) : BColors.accent.withValues(alpha: 0.95),
            )),
            const SizedBox(width: 4),
            Icon(Icons.close, size: 10,
                color: _h ? const Color(0xFFff9b9b) : BColors.accent.withValues(alpha: 0.7)),
          ]),
        ),
      ),
    );
  }
}

class _SelectableContactRow extends StatefulWidget {
  final String id, name, handle;
  final bool online, selected;
  final VoidCallback onToggle;
  const _SelectableContactRow({
    required this.id, required this.name, required this.handle,
    required this.online, required this.selected, required this.onToggle,
  });
  @override
  State<_SelectableContactRow> createState() => _SelectableContactRowState();
}

class _SelectableContactRowState extends State<_SelectableContactRow> {
  bool _h = false;
  @override
  Widget build(BuildContext context) {
    final tint = senderColorFor(widget.name);
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setState(() => _h = true),
      onExit: (_) => setState(() => _h = false),
      child: GestureDetector(
        onTap: widget.onToggle,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 100),
          height: 48,
          margin: const EdgeInsets.symmetric(horizontal: 8),
          padding: const EdgeInsets.symmetric(horizontal: 10),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(8),
            color: widget.selected
                ? BColors.accent.withValues(alpha: 0.08)
                : _h ? Colors.white.withValues(alpha: 0.04) : Colors.transparent,
          ),
          child: Row(children: [
            _Checkbox(checked: widget.selected),
            const SizedBox(width: 10),
            Container(
              width: 32, height: 32,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(8),
                color: tint.withValues(alpha: 0.14),
                border: Border.all(color: tint.withValues(alpha: 0.22), width: 0.5),
              ),
              child: Center(child: Text(widget.name[0].toUpperCase(),
                  style: TextStyle(
                    fontFamily: 'Onest', fontSize: 12, fontWeight: FontWeight.w700,
                    color: tint.withValues(alpha: 0.95),
                  ))),
            ),
            const SizedBox(width: 10),
            Expanded(child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(widget.name, style: const TextStyle(
                  fontFamily: 'Onest', fontSize: 13, fontWeight: FontWeight.w500,
                  color: BColors.textPrimary,
                )),
                Text(widget.handle, style: const TextStyle(
                  fontFamily: 'Onest', fontSize: 11, color: BColors.textMuted,
                )),
              ],
            )),
            if (widget.online) Container(
              width: 7, height: 7,
              decoration: const BoxDecoration(
                shape: BoxShape.circle, color: Color(0xFF4ade80),
              ),
            ),
          ]),
        ),
      ),
    );
  }
}

class _Checkbox extends StatelessWidget {
  final bool checked;
  const _Checkbox({required this.checked});
  @override
  Widget build(BuildContext context) {
    return AnimatedContainer(
      duration: const Duration(milliseconds: 120),
      width: 18, height: 18,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(5),
        color: checked ? BColors.accent : Colors.transparent,
        border: Border.all(
          color: checked ? BColors.accent : Colors.white.withValues(alpha: 0.25),
          width: 1.2,
        ),
      ),
      child: checked ? const Icon(Icons.check, size: 12, color: BColors.bg) : null,
    );
  }
}

class _FieldLabel extends StatelessWidget {
  final String label;
  const _FieldLabel({required this.label});
  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 5),
      child: Align(alignment: Alignment.centerLeft,
        child: Text(label.toUpperCase(), style: const TextStyle(
          fontFamily: 'Onest', fontSize: 10, fontWeight: FontWeight.w600,
          color: BColors.textMuted, letterSpacing: 1.1,
        )),
      ),
    );
  }
}

class _FieldInput extends StatelessWidget {
  final TextEditingController controller;
  final String hint;
  final int maxLines;
  final ValueChanged<String>? onChanged;
  const _FieldInput({
    required this.controller, required this.hint,
    this.maxLines = 1, this.onChanged,
  });
  @override
  Widget build(BuildContext context) {
    return Container(
      constraints: const BoxConstraints(minHeight: 40),
      padding: const EdgeInsets.symmetric(horizontal: 12),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(8),
        color: Colors.white.withValues(alpha: 0.04),
        border: Border.all(color: Colors.white.withValues(alpha: 0.06), width: 0.5),
      ),
      child: TextField(
        controller: controller,
        onChanged: onChanged,
        maxLines: maxLines,
        style: const TextStyle(
          fontFamily: 'Onest', fontSize: 14, color: BColors.textPrimary,
        ),
        cursorColor: BColors.accent,
        decoration: InputDecoration(
          border: InputBorder.none, isDense: true,
          hintText: hint,
          hintStyle: const TextStyle(
            fontFamily: 'Onest', fontSize: 14, color: BColors.textMuted,
          ),
          contentPadding: const EdgeInsets.symmetric(vertical: 10),
        ),
      ),
    );
  }
}

class _MemberAvatarChip extends StatelessWidget {
  final String name;
  const _MemberAvatarChip({required this.name});
  @override
  Widget build(BuildContext context) {
    final tint = senderColorFor(name);
    return Container(
      padding: const EdgeInsets.fromLTRB(3, 3, 10, 3),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(14),
        color: Colors.white.withValues(alpha: 0.04),
        border: Border.all(color: Colors.white.withValues(alpha: 0.06), width: 0.5),
      ),
      child: Row(mainAxisSize: MainAxisSize.min, children: [
        Container(
          width: 22, height: 22,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: tint.withValues(alpha: 0.2),
          ),
          child: Center(child: Text(name[0].toUpperCase(), style: TextStyle(
            fontFamily: 'Onest', fontSize: 10, fontWeight: FontWeight.w700,
            color: tint.withValues(alpha: 0.95),
          ))),
        ),
        const SizedBox(width: 6),
        Text(name, style: const TextStyle(
          fontFamily: 'Onest', fontSize: 12, color: BColors.textPrimary,
        )),
      ]),
    );
  }
}

// ═══════════════════════════════════════════════════════════════
// ADD CONTACT
// ═══════════════════════════════════════════════════════════════

OverlayEntry showAddContact(BuildContext context) {
  final overlay = Overlay.of(context);
  late OverlayEntry entry;
  entry = OverlayEntry(builder: (_) => AddContactModal(
    onClose: () => entry.remove(),
  ));
  overlay.insert(entry);
  return entry;
}

class AddContactModal extends StatefulWidget {
  final VoidCallback onClose;
  const AddContactModal({super.key, required this.onClose});
  @override
  State<AddContactModal> createState() => _AddContactModalState();
}

class _AddContactModalState extends State<AddContactModal> {
  final _nickCtrl = TextEditingController();
  bool _sent = false;

  @override
  void dispose() {
    _nickCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return _ModalScaffold(
      width: 440, onClose: widget.onClose,
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        _ModalHeader(title: 'добавить контакт', onClose: widget.onClose),
        Padding(
          padding: const EdgeInsets.fromLTRB(24, 20, 24, 24),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            _FieldLabel(label: 'введи ник'),
            _FieldInput(
              controller: _nickCtrl,
              hint: '@username',
              onChanged: (_) => setState(() {}),
            ),
            const SizedBox(height: 10),
            if (!_sent) _NextBtn(
              label: 'отправить запрос',
              enabled: _nickCtrl.text.trim().length >= 2,
              onTap: () => setState(() => _sent = true),
            )
            else _SentNotice(nick: _nickCtrl.text.trim()),
            const SizedBox(height: 20),
            const _OrDivider(),
            const SizedBox(height: 16),
            _FieldLabel(label: 'ссылка-приглашение'),
            _InviteLink(),
            const SizedBox(height: 16),
            _FieldLabel(label: 'QR-код'),
            _QrPlaceholder(),
          ]),
        ),
      ]),
    );
  }
}

class _SentNotice extends StatelessWidget {
  final String nick;
  const _SentNotice({required this.nick});
  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(8),
        color: BColors.accent.withValues(alpha: 0.08),
        border: Border.all(color: BColors.accent.withValues(alpha: 0.25), width: 0.5),
      ),
      child: Row(mainAxisSize: MainAxisSize.min, children: [
        Icon(Icons.check_circle_rounded, size: 14, color: BColors.accent),
        const SizedBox(width: 8),
        Expanded(child: Text(
          'запрос отправлен пользователю $nick',
          style: TextStyle(
            fontFamily: 'Onest', fontSize: 12,
            color: BColors.accent.withValues(alpha: 0.95),
          ),
        )),
      ]),
    );
  }
}

class _OrDivider extends StatelessWidget {
  const _OrDivider();
  @override
  Widget build(BuildContext context) {
    return Row(children: [
      Expanded(child: Container(height: 1,
          color: Colors.white.withValues(alpha: 0.06))),
      const Padding(padding: EdgeInsets.symmetric(horizontal: 10),
        child: Text('или', style: TextStyle(
          fontFamily: 'Onest', fontSize: 10, color: BColors.textMuted,
          letterSpacing: 1,
        )),
      ),
      Expanded(child: Container(height: 1,
          color: Colors.white.withValues(alpha: 0.06))),
    ]);
  }
}

class _InviteLink extends StatefulWidget {
  @override
  State<_InviteLink> createState() => _InviteLinkState();
}

class _InviteLinkState extends State<_InviteLink> {
  bool _copied = false;
  @override
  Widget build(BuildContext context) {
    return Container(
      height: 40,
      padding: const EdgeInsets.symmetric(horizontal: 12),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(8),
        color: Colors.white.withValues(alpha: 0.04),
        border: Border.all(color: Colors.white.withValues(alpha: 0.06), width: 0.5),
      ),
      child: Row(children: [
        Expanded(child: Text(
          'blesk.fun/u/gotblesk',
          style: TextStyle(
            fontFamily: 'Onest', fontSize: 13,
            color: BColors.accent.withValues(alpha: 0.9),
          ),
        )),
        MouseRegion(
          cursor: SystemMouseCursors.click,
          child: GestureDetector(
            onTap: () {
              Clipboard.setData(const ClipboardData(text: 'blesk.fun/u/gotblesk'));
              setState(() => _copied = true);
              Future.delayed(const Duration(seconds: 2), () {
                if (mounted) setState(() => _copied = false);
              });
            },
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 140),
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(6),
                color: _copied
                    ? BColors.accent.withValues(alpha: 0.2)
                    : Colors.white.withValues(alpha: 0.05),
              ),
              child: Row(mainAxisSize: MainAxisSize.min, children: [
                Icon(_copied ? Icons.check : Icons.copy_rounded, size: 12,
                    color: _copied ? BColors.accent : BColors.textMuted),
                const SizedBox(width: 4),
                Text(_copied ? 'скопировано' : 'копировать', style: TextStyle(
                  fontFamily: 'Onest', fontSize: 11, fontWeight: FontWeight.w500,
                  color: _copied
                      ? BColors.accent.withValues(alpha: 0.9)
                      : BColors.textMuted,
                )),
              ]),
            ),
          ),
        ),
      ]),
    );
  }
}

class _QrPlaceholder extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Center(child: Container(
      width: 160, height: 160,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(10),
        color: Colors.white.withValues(alpha: 0.96),
      ),
      child: CustomPaint(painter: _QrPainter(), size: const Size(136, 136)),
    ));
  }
}

class _QrPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final p = Paint()..color = const Color(0xFF0a0a0a);
    // Simple stylised QR-look: a grid of filled/empty squares with corner markers
    final grid = 15;
    final cell = size.width / grid;
    final rnd = _PrngSeed(0xBE73);
    for (var y = 0; y < grid; y++) {
      for (var x = 0; x < grid; x++) {
        final inCorner = (x < 4 && y < 4) ||
            (x >= grid - 4 && y < 4) ||
            (x < 4 && y >= grid - 4);
        final on = inCorner
            ? !((x == 1 || x == 2) && (y == 1 || y == 2)
                || (x == grid - 3 || x == grid - 2) && (y == 1 || y == 2)
                || (x == 1 || x == 2) && (y == grid - 3 || y == grid - 2))
                ? !(x == 0 || x == 3 || y == 0 || y == 3 ||
                    x == grid - 1 || x == grid - 4 || y == grid - 1 || y == grid - 4)
                : !(x > 0 && x < 3 && y > 0 && y < 3
                    || x > grid - 3 && x < grid - 0 && y > 0 && y < 3
                    || x > 0 && x < 3 && y > grid - 3 && y < grid - 0)
            : rnd.next() > 0.55;
        if (on) {
          canvas.drawRect(
            Rect.fromLTWH(x * cell, y * cell, cell, cell), p,
          );
        }
      }
    }
    // Reinforce corner eyes (solid 3×3 with hollow center)
    _drawCornerEye(canvas, 0, 0, cell, p);
    _drawCornerEye(canvas, (grid - 7) * cell, 0, cell, p);
    _drawCornerEye(canvas, 0, (grid - 7) * cell, cell, p);
  }

  void _drawCornerEye(Canvas c, double x, double y, double cell, Paint p) {
    // 7x7 outer frame
    c.drawRect(Rect.fromLTWH(x, y, cell * 7, cell), p);
    c.drawRect(Rect.fromLTWH(x, y + cell * 6, cell * 7, cell), p);
    c.drawRect(Rect.fromLTWH(x, y, cell, cell * 7), p);
    c.drawRect(Rect.fromLTWH(x + cell * 6, y, cell, cell * 7), p);
    // 3x3 center
    c.drawRect(Rect.fromLTWH(x + cell * 2, y + cell * 2, cell * 3, cell * 3), p);
  }

  @override
  bool shouldRepaint(covariant _QrPainter o) => false;
}

class _PrngSeed {
  int _s;
  _PrngSeed(this._s);
  double next() {
    _s = (_s * 16807) % 2147483647;
    return _s / 2147483647;
  }
}

// ═══════════════════════════════════════════════════════════════
// CREATE CHANNEL
// ═══════════════════════════════════════════════════════════════

OverlayEntry showCreateChannel(BuildContext context, {
  ValueChanged<CreatedChannel>? onCreated,
}) {
  final overlay = Overlay.of(context);
  late OverlayEntry entry;
  entry = OverlayEntry(builder: (_) => CreateChannelModal(
    onClose: () => entry.remove(),
    onCreated: (ch) { entry.remove(); onCreated?.call(ch); },
  ));
  overlay.insert(entry);
  return entry;
}

class CreatedChannel {
  final String name, description, slug;
  final bool isPublic;
  const CreatedChannel({
    required this.name, required this.description,
    required this.slug, required this.isPublic,
  });
}

class CreateChannelModal extends StatefulWidget {
  final VoidCallback onClose;
  final ValueChanged<CreatedChannel> onCreated;
  const CreateChannelModal({
    super.key, required this.onClose, required this.onCreated,
  });
  @override
  State<CreateChannelModal> createState() => _CreateChannelModalState();
}

class _CreateChannelModalState extends State<CreateChannelModal> {
  final _nameCtrl = TextEditingController();
  final _descCtrl = TextEditingController();
  final _slugCtrl = TextEditingController();
  bool _isPublic = true;
  bool _slugDirty = false;

  @override
  void initState() {
    super.initState();
    _nameCtrl.addListener(() {
      if (!_slugDirty) {
        _slugCtrl.text = _nameCtrl.text
            .toLowerCase()
            .replaceAll(RegExp(r'[^a-z0-9а-я]+'), '')
            .substring(0, _nameCtrl.text.length.clamp(0, 24));
      }
      setState(() {});
    });
  }

  @override
  void dispose() {
    _nameCtrl.dispose();
    _descCtrl.dispose();
    _slugCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return _ModalScaffold(
      width: 480, onClose: widget.onClose,
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        _ModalHeader(
          title: 'новый канал',
          onClose: widget.onClose,
          trailing: _NextBtn(
            label: 'создать',
            enabled: _nameCtrl.text.trim().length >= 2,
            onTap: () => widget.onCreated(CreatedChannel(
              name: _nameCtrl.text.trim(),
              description: _descCtrl.text.trim(),
              slug: _slugCtrl.text.trim(),
              isPublic: _isPublic,
            )),
          ),
        ),
        SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(24, 20, 24, 24),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            Container(
              width: 80, height: 80,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(14),
                gradient: LinearGradient(
                  begin: Alignment.topLeft, end: Alignment.bottomRight,
                  colors: [
                    BColors.accent.withValues(alpha: 0.35),
                    BColors.accent.withValues(alpha: 0.08),
                  ],
                ),
                border: Border.all(color: BColors.accent.withValues(alpha: 0.3)),
              ),
              child: const Icon(Icons.campaign_outlined, size: 26, color: BColors.accent),
            ),
            const SizedBox(height: 6),
            const Text('добавить фото', style: TextStyle(
              fontFamily: 'Onest', fontSize: 11, fontWeight: FontWeight.w500,
              color: BColors.textMuted,
            )),
            const SizedBox(height: 20),
            _FieldLabel(label: 'название канала'),
            _FieldInput(controller: _nameCtrl, hint: 'например: blesk news'),
            const SizedBox(height: 14),
            _FieldLabel(label: 'описание'),
            _FieldInput(controller: _descCtrl, hint: 'о чём канал', maxLines: 3),
            const SizedBox(height: 14),
            _FieldLabel(label: 'тип'),
            _TypeOption(
              selected: _isPublic, title: 'публичный',
              subtitle: 'все могут найти и подписаться',
              onTap: () => setState(() => _isPublic = true),
            ),
            const SizedBox(height: 6),
            _TypeOption(
              selected: !_isPublic, title: 'приватный',
              subtitle: 'только по пригласительной ссылке',
              onTap: () => setState(() => _isPublic = false),
            ),
            const SizedBox(height: 14),
            _FieldLabel(label: 'ссылка'),
            Container(
              height: 40,
              padding: const EdgeInsets.only(left: 12, right: 4),
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(8),
                color: Colors.white.withValues(alpha: 0.04),
                border: Border.all(color: Colors.white.withValues(alpha: 0.06), width: 0.5),
              ),
              child: Row(children: [
                Text('blesk.fun/c/', style: TextStyle(
                  fontFamily: 'Onest', fontSize: 13,
                  color: BColors.textMuted.withValues(alpha: 0.9),
                )),
                Expanded(child: TextField(
                  controller: _slugCtrl,
                  onChanged: (_) => _slugDirty = true,
                  style: TextStyle(
                    fontFamily: 'Onest', fontSize: 13,
                    color: BColors.accent.withValues(alpha: 0.95),
                  ),
                  cursorColor: BColors.accent,
                  decoration: const InputDecoration(
                    border: InputBorder.none, isDense: true,
                    contentPadding: EdgeInsets.symmetric(vertical: 10),
                  ),
                )),
              ]),
            ),
          ]),
        ),
      ]),
    );
  }
}

class _TypeOption extends StatefulWidget {
  final bool selected;
  final String title, subtitle;
  final VoidCallback onTap;
  const _TypeOption({
    required this.selected, required this.title, required this.subtitle,
    required this.onTap,
  });
  @override
  State<_TypeOption> createState() => _TypeOptionState();
}

class _TypeOptionState extends State<_TypeOption> {
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
          padding: const EdgeInsets.fromLTRB(12, 10, 12, 10),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(8),
            color: widget.selected
                ? BColors.accent.withValues(alpha: 0.08)
                : _h ? Colors.white.withValues(alpha: 0.04) : Colors.white.withValues(alpha: 0.02),
            border: Border.all(
              color: widget.selected
                  ? BColors.accent.withValues(alpha: 0.3)
                  : Colors.white.withValues(alpha: 0.06),
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
                  color: widget.selected ? BColors.accent : Colors.white.withValues(alpha: 0.25),
                  width: 1.5,
                ),
              ),
              child: Center(child: AnimatedContainer(
                duration: const Duration(milliseconds: 150),
                width: widget.selected ? 8 : 0, height: widget.selected ? 8 : 0,
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
                Text(widget.title, style: const TextStyle(
                  fontFamily: 'Onest', fontSize: 13, fontWeight: FontWeight.w500,
                  color: BColors.textPrimary,
                )),
                const SizedBox(height: 1),
                Text(widget.subtitle, style: const TextStyle(
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
