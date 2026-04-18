import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';

import '../shared/theme.dart';
import '../shared/slide_over.dart';

// ═══════════════════════════════════════════════════════════════
// OTHER USER'S PROFILE (slide-over, no backdrop)
// ═══════════════════════════════════════════════════════════════

class ProfileView extends StatelessWidget {
  final String name;
  final String username;
  final String bio;
  final bool online;
  final String initial;
  final VoidCallback onClose;

  const ProfileView({
    super.key, required this.name, required this.username,
    required this.bio, required this.online, required this.initial,
    required this.onClose,
  });

  @override
  Widget build(BuildContext context) {
    return SlideOver(
      onClose: onClose,
      child: Column(crossAxisAlignment: CrossAxisAlignment.center, children: [
        const SizedBox(height: 16),
        // Avatar
        Stack(alignment: Alignment.bottomRight, children: [
          Container(
            width: 96, height: 96,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(24),
              gradient: LinearGradient(
                begin: Alignment.topLeft, end: Alignment.bottomRight,
                colors: [BColors.accent.withValues(alpha: 0.15), BColors.accent.withValues(alpha: 0.06)],
              ),
            ),
            child: Center(child: Text(initial, style: const TextStyle(
              fontFamily: 'Nekst', fontSize: 36, fontWeight: FontWeight.w700, color: BColors.accent,
            ))),
          ).animate().scale(begin: const Offset(0.9, 0.9), duration: 300.ms, curve: Curves.easeOutCubic),
          if (online)
            Container(
              width: 18, height: 18,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: const Color(0xFF22c55e),
                border: Border.all(color: const Color(0xFF0e0e12), width: 3),
              ),
            ).animate(delay: 400.ms)
                .scale(begin: const Offset(0, 0), duration: 300.ms, curve: Curves.easeOutBack),
        ]),
        const SizedBox(height: 16),
        // Name
        Text(name, style: TextStyle(
          fontFamily: 'Nekst', fontSize: rf(context, 22),
          fontWeight: FontWeight.w600, color: BColors.textPrimary,
        )).animate(delay: 100.ms).fadeIn(duration: 300.ms),
        const SizedBox(height: 4),
        Text('@$username', style: TextStyle(
          fontFamily: 'Onest', fontSize: rf(context, 13),
          color: BColors.accent.withValues(alpha: 0.7),
        )).animate(delay: 150.ms).fadeIn(duration: 300.ms),
        const SizedBox(height: 20),
        const _Divider(),
        const SizedBox(height: 16),
        // Bio
        Align(alignment: Alignment.centerLeft, child: Text('био', style: _labelStyle(context))),
        const SizedBox(height: 6),
        Align(alignment: Alignment.centerLeft, child: Text(bio, style: TextStyle(
          fontFamily: 'Onest', fontSize: rf(context, 14),
          color: BColors.textPrimary, height: 1.5,
        ))).animate(delay: 200.ms).fadeIn(duration: 300.ms),
        const SizedBox(height: 20),
        const _Divider(),
        const SizedBox(height: 16),
        // Action buttons (staggered)
        _ActionButton(icon: Icons.phone_outlined, label: 'позвонить', onTap: () {})
            .animate(delay: 250.ms).fadeIn(duration: 250.ms).slideY(begin: 0.1),
        const SizedBox(height: 8),
        _ActionButton(icon: Icons.videocam_outlined, label: 'видеозвонок', onTap: () {})
            .animate(delay: 300.ms).fadeIn(duration: 250.ms).slideY(begin: 0.1),
        const SizedBox(height: 8),
        _ActionButton(icon: Icons.chat_bubble_outline, label: 'написать', onTap: () {})
            .animate(delay: 350.ms).fadeIn(duration: 250.ms).slideY(begin: 0.1),
        const SizedBox(height: 20),
        const _Divider(),
        const SizedBox(height: 16),
        // Toggle rows (staggered)
        _ToggleRow(icon: Icons.notifications_off_outlined, label: 'уведомления', value: true, onChanged: (_) {})
            .animate(delay: 400.ms).fadeIn(duration: 250.ms).slideX(begin: 0.05),
        _ToggleRow(icon: Icons.push_pin_outlined, label: 'закрепить чат', value: false, onChanged: (_) {})
            .animate(delay: 440.ms).fadeIn(duration: 250.ms).slideX(begin: 0.05),
        const SizedBox(height: 8),
        _ActionButton(
          icon: Icons.block_outlined, label: 'заблокировать',
          destructive: true, onTap: () {},
        ).animate(delay: 480.ms).fadeIn(duration: 250.ms),
        const SizedBox(height: 20),
        const _Divider(),
        const SizedBox(height: 16),
        // Shared media
        Align(alignment: Alignment.centerLeft, child: Text('общие медиа', style: _labelStyle(context))),
        const SizedBox(height: 10),
        Row(children: [
          for (var i = 0; i < 3; i++) ...[
            if (i > 0) const SizedBox(width: 4),
            Expanded(child: Container(
              height: 80,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(8),
                color: Colors.white.withValues(alpha: 0.04),
              ),
              child: Center(child: Icon(Icons.image_outlined, size: 24, color: BColors.textMuted)),
            )),
          ],
        ]).animate(delay: 520.ms).fadeIn(duration: 300.ms).slideY(begin: 0.05),
        const SizedBox(height: 8),
        Align(alignment: Alignment.centerLeft, child: Text('показать всё (24)', style: TextStyle(
          fontFamily: 'Onest', fontSize: rf(context, 12), color: BColors.accent.withValues(alpha: 0.7),
        ))),
        const SizedBox(height: 32),
      ]),
    );
  }

  TextStyle _labelStyle(BuildContext context) => TextStyle(
    fontFamily: 'Onest', fontSize: 11, fontWeight: FontWeight.w600,
    color: BColors.textMuted, letterSpacing: 1,
  );
}

// ═══════════════════════════════════════════════════════════════
// OWN PROFILE EDITOR (slide-over, with backdrop)
// ═══════════════════════════════════════════════════════════════

class ProfileEditor extends StatefulWidget {
  final VoidCallback onClose;
  const ProfileEditor({super.key, required this.onClose});
  @override
  State<ProfileEditor> createState() => _ProfileEditorState();
}

class _ProfileEditorState extends State<ProfileEditor> {
  final _nameCtrl = TextEditingController(text: 'Goshan');
  final _nickCtrl = TextEditingController(text: 'gotblesk');
  final _bioCtrl = TextEditingController(text: 'дизайнер. котики. кофе.');
  String _nickStatus = '';
  String _status = 'online';
  bool _dirty = false;
  bool _saving = false;
  Timer? _debounce;

  @override
  void initState() {
    super.initState();
    _nameCtrl.addListener(_markDirty);
    _bioCtrl.addListener(_markDirty);
  }

  void _markDirty() { if (!_dirty) setState(() => _dirty = true); }

  @override
  void dispose() {
    _nameCtrl.dispose(); _nickCtrl.dispose(); _bioCtrl.dispose();
    _debounce?.cancel();
    super.dispose();
  }

  void _checkNick(String v) {
    _markDirty();
    _debounce?.cancel();
    if (v.length < 3) { setState(() => _nickStatus = 'short'); return; }
    setState(() => _nickStatus = 'checking');
    _debounce = Timer(const Duration(milliseconds: 600), () {
      setState(() => _nickStatus = v == 'admin' ? 'taken' : 'ok');
    });
  }

  void _save() {
    setState(() => _saving = true);
    Future.delayed(const Duration(milliseconds: 800), () {
      if (mounted) setState(() { _saving = false; _dirty = false; });
    });
  }

  @override
  Widget build(BuildContext context) {
    return SlideOver(
      onClose: widget.onClose,
      showBackdrop: true,
      title: 'мой профиль',
      child: Column(crossAxisAlignment: CrossAxisAlignment.center, children: [
        const SizedBox(height: 20),
        // Avatar
        Container(
          width: 96, height: 96,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(24),
            gradient: LinearGradient(
              begin: Alignment.topLeft, end: Alignment.bottomRight,
              colors: [BColors.accent.withValues(alpha: 0.15), BColors.accent.withValues(alpha: 0.06)],
            ),
          ),
          child: const Center(child: Text('G', style: TextStyle(
            fontFamily: 'Nekst', fontSize: 36, fontWeight: FontWeight.w700, color: BColors.accent,
          ))),
        ),
        const SizedBox(height: 12),
        _TextBtn(text: 'изменить фото', onTap: () {}),
        const SizedBox(height: 24),
        const _Divider(),
        const SizedBox(height: 20),

        // Name
        Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          _FieldLabel(label: 'имя', dirty: _dirty && _nameCtrl.text != 'Goshan'),
          const SizedBox(height: 6),
          _EditorInput(controller: _nameCtrl, hint: 'имя', maxLength: 32),
        ]).animate(delay: 150.ms).fadeIn(duration: 300.ms).slideY(begin: 0.08),

        const SizedBox(height: 16),

        // Nick
        Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          _FieldLabel(label: 'ник', dirty: _dirty && _nickCtrl.text != 'gotblesk'),
          const SizedBox(height: 6),
          _EditorInput(controller: _nickCtrl, hint: 'username', prefix: '@ ', maxLength: 24,
            onChanged: _checkNick),
        ]).animate(delay: 220.ms).fadeIn(duration: 300.ms).slideY(begin: 0.08),
        const SizedBox(height: 4),
        _NickStatus(status: _nickStatus),

        const SizedBox(height: 16),

        // Bio
        Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          _FieldLabel(label: 'био'),
          const SizedBox(height: 6),
          _EditorInput(controller: _bioCtrl, hint: 'расскажи о себе', maxLength: 200,
            maxLines: 3, minLines: 3, onChanged: (_) => _markDirty()),
        ]).animate(delay: 290.ms).fadeIn(duration: 300.ms).slideY(begin: 0.08),
        Align(
          alignment: Alignment.centerRight,
          child: Text('${_bioCtrl.text.length} / 200', style: TextStyle(
            fontFamily: 'Onest', fontSize: 11,
            color: _bioCtrl.text.length > 180
                ? (_bioCtrl.text.length >= 200 ? const Color(0xCCFF4444) : const Color(0xCCFFB800))
                : BColors.textMuted,
          )),
        ),

        const SizedBox(height: 20),
        const _Divider(),
        const SizedBox(height: 20),

        // Status (animated)
        Align(alignment: Alignment.centerLeft, child: Text('СТАТУС', style: TextStyle(
          fontFamily: 'Onest', fontSize: 11, fontWeight: FontWeight.w600,
          color: BColors.textMuted, letterSpacing: 1,
        ))),
        const SizedBox(height: 12),
        _StatusRadio(value: 'online', label: 'в сети', color: const Color(0xFF22c55e),
          selected: _status, onTap: () => setState(() { _status = 'online'; _markDirty(); })),
        _StatusRadio(value: 'busy', label: 'занят', color: const Color(0xFFFFB800),
          selected: _status, onTap: () => setState(() { _status = 'busy'; _markDirty(); })),
        _StatusRadio(value: 'brb', label: 'скоро вернусь', color: const Color(0xFFFFB800),
          selected: _status, onTap: () => setState(() { _status = 'brb'; _markDirty(); })),
        _StatusRadio(value: 'invisible', label: 'невидимка', color: BColors.textMuted,
          selected: _status, onTap: () => setState(() { _status = 'invisible'; _markDirty(); })),

        const SizedBox(height: 24),
        const _Divider(),
        const SizedBox(height: 20),

        // Save
        SizedBox(
          width: double.infinity, height: 44,
          child: _saving
              ? const Center(child: SizedBox(width: 20, height: 20,
                  child: CircularProgressIndicator(strokeWidth: 2, color: BColors.accent)))
              : Opacity(
                  opacity: _dirty ? 1.0 : 0.3,
                  child: IgnorePointer(
                    ignoring: !_dirty,
                    child: _ActionButton(
                      icon: Icons.check, label: 'сохранить',
                      accent: true, onTap: _save,
                    ),
                  ),
                ),
        ),
        const SizedBox(height: 32),
      ]),
    );
  }
}

// ═══════════════════════════════════════════════════════════════
// SHARED WIDGETS
// ═══════════════════════════════════════════════════════════════

class _Divider extends StatelessWidget {
  const _Divider();
  @override
  Widget build(BuildContext context) => Container(height: 1, color: BColors.borderLow);
}

class _ActionButton extends StatefulWidget {
  final IconData icon;
  final String label;
  final bool destructive;
  final bool accent;
  final VoidCallback onTap;
  const _ActionButton({required this.icon, required this.label, required this.onTap,
    this.destructive = false, this.accent = false});
  @override
  State<_ActionButton> createState() => _ActionButtonState();
}

class _ActionButtonState extends State<_ActionButton> {
  bool _h = false;
  bool _p = false;

  @override
  Widget build(BuildContext context) {
    final color = widget.destructive ? const Color(0xCCFF5C5C) : BColors.textPrimary;
    final hoverBg = widget.destructive
        ? const Color(0x0FFF5C5C)
        : widget.accent
            ? BColors.accent.withValues(alpha: 0.12)
            : Colors.white.withValues(alpha: 0.06);

    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setState(() => _h = true),
      onExit: (_) => setState(() => _h = false),
      child: GestureDetector(
        onTapDown: (_) => setState(() => _p = true),
        onTapUp: (_) { setState(() => _p = false); widget.onTap(); },
        onTapCancel: () => setState(() => _p = false),
        child: AnimatedScale(
          scale: _p ? 0.98 : 1.0,
          duration: const Duration(milliseconds: 100),
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 150),
            height: 44,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(12),
              color: _h ? hoverBg : Colors.white.withValues(alpha: 0.04),
              border: Border.all(color: _h
                  ? Colors.white.withValues(alpha: 0.1)
                  : Colors.white.withValues(alpha: 0.06)),
            ),
            child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [
              Icon(widget.icon, size: 18, color: widget.accent ? BColors.accent : widget.destructive ? color : BColors.textSecondary),
              const SizedBox(width: 10),
              Text(widget.label, style: TextStyle(
                fontFamily: 'Onest', fontSize: rf(context, 14),
                fontWeight: FontWeight.w500, color: widget.accent ? BColors.accent : color,
              )),
            ]),
          ),
        ),
      ),
    );
  }
}

class _ToggleRow extends StatefulWidget {
  final IconData icon;
  final String label;
  final bool value;
  final ValueChanged<bool> onChanged;
  const _ToggleRow({required this.icon, required this.label, required this.value, required this.onChanged});
  @override
  State<_ToggleRow> createState() => _ToggleRowState();
}

class _ToggleRowState extends State<_ToggleRow> {
  late bool _on = widget.value;
  bool _h = false;

  @override
  Widget build(BuildContext context) {
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setState(() => _h = true),
      onExit: (_) => setState(() => _h = false),
      child: GestureDetector(
        onTap: () { setState(() => _on = !_on); widget.onChanged(_on); },
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 100),
          height: 44,
          padding: const EdgeInsets.symmetric(horizontal: 14),
          margin: const EdgeInsets.only(bottom: 4),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(10),
            color: _h ? Colors.white.withValues(alpha: 0.03) : Colors.transparent,
          ),
          child: Row(children: [
            Icon(widget.icon, size: 18, color: BColors.textSecondary),
            const SizedBox(width: 12),
            Text(widget.label, style: TextStyle(fontFamily: 'Onest',
              fontSize: rf(context, 13), color: BColors.textPrimary)),
            const Spacer(),
            _Toggle(on: _on),
          ]),
        ),
      ),
    );
  }
}

class _Toggle extends StatelessWidget {
  final bool on;
  const _Toggle({required this.on});

  @override
  Widget build(BuildContext context) {
    return AnimatedContainer(
      duration: const Duration(milliseconds: 200),
      curve: Curves.easeOutCubic,
      width: 36, height: 20,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(10),
        color: on ? BColors.accent : Colors.white.withValues(alpha: 0.06),
      ),
      child: AnimatedAlign(
        duration: const Duration(milliseconds: 200),
        curve: Curves.easeOutCubic,
        alignment: on ? Alignment.centerRight : Alignment.centerLeft,
        child: Container(
          width: 16, height: 16,
          margin: const EdgeInsets.symmetric(horizontal: 2),
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: on ? BColors.bg : Colors.white.withValues(alpha: 0.3),
          ),
        ),
      ),
    );
  }
}

class _EditorInput extends StatelessWidget {
  final TextEditingController controller;
  final String hint;
  final String? prefix;
  final int? maxLength;
  final int maxLines;
  final int minLines;
  final ValueChanged<String>? onChanged;

  const _EditorInput({required this.controller, required this.hint,
    this.prefix, this.maxLength, this.maxLines = 1, this.minLines = 1, this.onChanged});

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(12),
        color: Colors.white.withValues(alpha: 0.04),
        border: Border.all(color: Colors.white.withValues(alpha: 0.06)),
      ),
      child: TextField(
        controller: controller,
        maxLength: maxLength,
        maxLines: maxLines,
        minLines: minLines,
        onChanged: onChanged,
        style: TextStyle(fontFamily: 'Onest', fontSize: rf(context, 14),
          fontWeight: FontWeight.w500, color: BColors.textPrimary),
        cursorColor: BColors.accent,
        decoration: InputDecoration(
          border: InputBorder.none, counterText: '',
          hintText: hint,
          hintStyle: TextStyle(fontFamily: 'Onest', fontSize: rf(context, 14), color: BColors.textMuted),
          contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
          prefixText: prefix,
          prefixStyle: TextStyle(fontFamily: 'Onest', fontSize: rf(context, 14),
            fontWeight: FontWeight.w500, color: BColors.textMuted),
        ),
      ),
    );
  }
}

class _FieldLabel extends StatelessWidget {
  final String label;
  final bool dirty;
  const _FieldLabel({required this.label, this.dirty = false});

  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: Alignment.centerLeft,
      child: Row(children: [
        if (dirty)
          Container(
            width: 5, height: 5,
            margin: const EdgeInsets.only(right: 6),
            decoration: const BoxDecoration(shape: BoxShape.circle, color: BColors.accent),
          ),
        Text(label.toUpperCase(), style: TextStyle(
          fontFamily: 'Onest', fontSize: 11, fontWeight: FontWeight.w600,
          color: BColors.textMuted, letterSpacing: 1,
        )),
      ]),
    );
  }
}

class _NickStatus extends StatelessWidget {
  final String status;
  const _NickStatus({required this.status});

  @override
  Widget build(BuildContext context) {
    if (status.isEmpty) return const SizedBox.shrink();
    IconData? icon; String text; Color color;
    switch (status) {
      case 'checking':
        return Align(alignment: Alignment.centerLeft, child: Row(mainAxisSize: MainAxisSize.min, children: [
          SizedBox(width: 12, height: 12, child: CircularProgressIndicator(strokeWidth: 1.5, color: BColors.accent)),
          const SizedBox(width: 6),
          Text('проверяю...', style: TextStyle(fontFamily: 'Onest', fontSize: 12, color: BColors.textMuted)),
        ]));
      case 'ok':
        icon = Icons.check; text = 'доступно'; color = BColors.accent.withValues(alpha: 0.6);
      case 'taken':
        icon = Icons.close; text = 'занято'; color = const Color(0xCCFF5C5C);
      case 'short':
        icon = null; text = 'минимум 3 символа'; color = BColors.textMuted;
      default:
        return const SizedBox.shrink();
    }
    return Align(alignment: Alignment.centerLeft, child: Row(mainAxisSize: MainAxisSize.min, children: [
      if (icon != null) Icon(icon, size: 14, color: color),
      if (icon != null) const SizedBox(width: 4),
      Text(text, style: TextStyle(fontFamily: 'Onest', fontSize: 12, color: color)),
    ])).animate().fadeIn(duration: 200.ms);
  }
}

class _StatusRadio extends StatefulWidget {
  final String value, label, selected;
  final Color color;
  final VoidCallback onTap;
  const _StatusRadio({required this.value, required this.label, required this.color,
    required this.selected, required this.onTap});
  @override
  State<_StatusRadio> createState() => _StatusRadioState();
}

class _StatusRadioState extends State<_StatusRadio> {
  bool _h = false;
  @override
  Widget build(BuildContext context) {
    final sel = widget.value == widget.selected;
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setState(() => _h = true),
      onExit: (_) => setState(() => _h = false),
      child: GestureDetector(
        onTap: widget.onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 100),
          height: 36,
          padding: const EdgeInsets.symmetric(horizontal: 10),
          margin: const EdgeInsets.only(bottom: 2),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(8),
            color: _h ? Colors.white.withValues(alpha: 0.03) : Colors.transparent,
          ),
          child: Row(children: [
            // Radio circle
            AnimatedContainer(
              duration: const Duration(milliseconds: 200),
              width: 16, height: 16,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: sel ? BColors.accent : Colors.transparent,
                border: Border.all(
                  color: sel ? BColors.accent : Colors.white.withValues(alpha: 0.2),
                  width: sel ? 4 : 1.5,
                ),
              ),
            ),
            const SizedBox(width: 10),
            // Color dot
            Container(width: 4, height: 4, decoration: BoxDecoration(
              shape: BoxShape.circle, color: widget.color,
            )),
            const SizedBox(width: 8),
            Text(widget.label, style: TextStyle(fontFamily: 'Onest',
              fontSize: rf(context, 13), color: BColors.textPrimary)),
          ]),
        ),
      ),
    );
  }
}

class _TextBtn extends StatefulWidget {
  final String text;
  final VoidCallback onTap;
  const _TextBtn({required this.text, required this.onTap});
  @override
  State<_TextBtn> createState() => _TextBtnState();
}

class _TextBtnState extends State<_TextBtn> {
  bool _h = false;
  @override
  Widget build(BuildContext context) {
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setState(() => _h = true),
      onExit: (_) => setState(() => _h = false),
      child: GestureDetector(
        onTap: widget.onTap,
        child: Text(widget.text, style: TextStyle(
          fontFamily: 'Onest', fontSize: 13, fontWeight: FontWeight.w500,
          color: BColors.accent.withValues(alpha: _h ? 1.0 : 0.7),
          decoration: _h ? TextDecoration.underline : TextDecoration.none,
          decorationColor: BColors.accent.withValues(alpha: 0.4),
        )),
      ),
    );
  }
}
