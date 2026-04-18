import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:solar_icons/solar_icons.dart';

import '../shared/theme.dart';
import 'emoji_picker.dart';

// ═══════════════════════════════════════════════════════════════
// INPUT BAR — glass pill with attachments, emoji, voice/send
// ═══════════════════════════════════════════════════════════════

class InputBar extends StatefulWidget {
  final ValueChanged<String>? onSend;
  final String? replyTo; // name of person being replied to
  final String? replyText; // quoted text
  final String? editText; // text being edited
  final VoidCallback? onCancelReply;
  final VoidCallback? onCancelEdit;
  // Draft support:
  final String? initialText; // pre-fill input with draft
  final ValueChanged<String>? onTextChanged; // persist draft on every change
  final bool showDraftRestored; // show "черновик восстановлен" bar briefly

  const InputBar({super.key, this.onSend, this.replyTo, this.replyText,
    this.editText, this.onCancelReply, this.onCancelEdit,
    this.initialText, this.onTextChanged, this.showDraftRestored = false});

  @override
  State<InputBar> createState() => _InputBarState();
}

class _InputBarState extends State<InputBar> {
  final _ctrl = TextEditingController();
  final _focus = FocusNode();
  bool _hasText = false;
  bool _recording = false;
  int _recordSeconds = 0;
  bool _draftBannerVisible = false;

  @override
  void initState() {
    super.initState();
    if (widget.editText != null) {
      _ctrl.text = widget.editText!;
      _hasText = true;
    } else if (widget.initialText != null && widget.initialText!.isNotEmpty) {
      _ctrl.text = widget.initialText!;
      _hasText = true;
      if (widget.showDraftRestored) {
        _draftBannerVisible = true;
        Future.delayed(const Duration(seconds: 3), () {
          if (mounted) setState(() => _draftBannerVisible = false);
        });
      }
    }
    _ctrl.addListener(() {
      final has = _ctrl.text.trim().isNotEmpty;
      if (has != _hasText) setState(() => _hasText = has);
      widget.onTextChanged?.call(_ctrl.text);
      // Dismiss draft banner on first keystroke
      if (_draftBannerVisible && _ctrl.text != widget.initialText) {
        setState(() => _draftBannerVisible = false);
      }
    });
  }

  void _clearDraft() {
    _ctrl.clear();
    setState(() {
      _hasText = false;
      _draftBannerVisible = false;
    });
    widget.onTextChanged?.call('');
  }

  @override
  void dispose() {
    _ctrl.dispose();
    _focus.dispose();
    super.dispose();
  }

  void _send() {
    final text = _ctrl.text.trim();
    if (text.isEmpty) return;
    widget.onSend?.call(text);
    _ctrl.clear();
    widget.onTextChanged?.call('');
    if (_draftBannerVisible) {
      setState(() => _draftBannerVisible = false);
    }
  }

  void _toggleRecord() {
    setState(() {
      _recording = !_recording;
      _recordSeconds = 0;
    });
  }

  bool get _showReply => widget.replyTo != null;
  bool get _showEdit => widget.editText != null;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 0, 12, 12),
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        // Draft restored banner (appears briefly)
        if (_draftBannerVisible) _DraftBanner(onDismiss: _clearDraft),
        // Reply/Edit header
        if (_showReply) _ReplyHeader(
          name: widget.replyTo!, text: widget.replyText ?? '',
          onCancel: widget.onCancelReply ?? () {},
        ),
        if (_showEdit) _EditHeader(onCancel: widget.onCancelEdit ?? () {}),

        // Main input container
        ClipRRect(
          borderRadius: BorderRadius.circular(16),
          child: BackdropFilter(
            filter: ImageFilter.blur(sigmaX: 24, sigmaY: 24),
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 200),
              constraints: const BoxConstraints(minHeight: 48, maxHeight: 200),
              decoration: BoxDecoration(
                color: Colors.white.withValues(alpha: 0.04),
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: Colors.white.withValues(alpha: 0.06), width: 0.5),
              ),
              child: _recording ? _RecordingUI(
                seconds: _recordSeconds,
                onCancel: () => setState(() => _recording = false),
                onSend: () => setState(() => _recording = false),
              ) : Row(crossAxisAlignment: CrossAxisAlignment.center, children: [
                // Attachments button
                _InputIconBtn(icon: SolarIconsOutline.paperclip, tooltip: 'прикрепить',
                  onTap: () => _showAttachMenu(context)),
                // Text field
                Expanded(child: TextField(
                  controller: _ctrl,
                  focusNode: _focus,
                  maxLines: 6,
                  minLines: 1,
                  style: TextStyle(fontFamily: 'Onest', fontSize: rf(context, 14),
                    fontWeight: FontWeight.w400,
                    color: BColors.textPrimary, height: 1.4),
                  cursorColor: BColors.accent,
                  onSubmitted: (_) => _send(),
                  decoration: InputDecoration(
                    border: InputBorder.none, isDense: true,
                    hintText: _showEdit ? 'редактирование...' : 'написать сообщение...',
                    hintStyle: TextStyle(fontFamily: 'Onest', fontSize: rf(context, 14),
                      fontWeight: FontWeight.w400, color: BColors.textMuted),
                    contentPadding: const EdgeInsets.symmetric(vertical: 13, horizontal: 4),
                  ),
                )),
                // Emoji button
                _InputIconBtn(icon: SolarIconsOutline.smileCircle, tooltip: 'эмодзи',
                  onTap: () => _showEmojiPicker(context)),
                // Send / Voice button
                _SendOrVoiceBtn(
                  hasText: _hasText,
                  isEdit: _showEdit,
                  onSend: _send,
                  onVoice: _toggleRecord,
                ),
              ]),
            ),
          ),
        ),
      ]),
    );
  }

  void _showEmojiPicker(BuildContext ctx) {
    final overlay = Overlay.of(ctx);
    late OverlayEntry entry;
    entry = OverlayEntry(builder: (_) => EmojiPicker(
      onSelect: (emoji) {
        final pos = _ctrl.selection.baseOffset;
        final text = _ctrl.text;
        final newText = pos >= 0
            ? text.substring(0, pos) + emoji + text.substring(pos)
            : text + emoji;
        _ctrl.text = newText;
        _ctrl.selection = TextSelection.collapsed(offset: (pos >= 0 ? pos : text.length) + emoji.length);
        _focus.requestFocus();
      },
      onClose: () => entry.remove(),
    ));
    overlay.insert(entry);
  }

  void _showAttachMenu(BuildContext ctx) {
    final overlay = Overlay.of(ctx);
    late OverlayEntry entry;
    final box = ctx.findRenderObject() as RenderBox;
    final pos = box.localToGlobal(Offset.zero);

    entry = OverlayEntry(builder: (_) => Stack(children: [
      Positioned.fill(child: GestureDetector(onTap: () => entry.remove(),
        child: Container(color: Colors.transparent))),
      Positioned(
        left: pos.dx + 8, bottom: MediaQuery.of(ctx).size.height - pos.dy + 8,
        child: _AttachPopover(onSelect: (type) { entry.remove(); }),
      ),
    ]));
    overlay.insert(entry);
  }
}

// ─── Input Icon Button ────────────────────────────────────────

class _InputIconBtn extends StatefulWidget {
  final IconData icon;
  final String tooltip;
  final VoidCallback onTap;
  const _InputIconBtn({required this.icon, required this.tooltip, required this.onTap});
  @override
  State<_InputIconBtn> createState() => _InputIconBtnState();
}

class _InputIconBtnState extends State<_InputIconBtn> {
  bool _h = false;
  @override
  Widget build(BuildContext context) {
    return Tooltip(message: widget.tooltip,
      textStyle: const TextStyle(fontSize: 10, color: BColors.textPrimary),
      decoration: BoxDecoration(color: const Color(0xFF1a1a1a), borderRadius: BorderRadius.circular(6)),
      child: MouseRegion(
        cursor: SystemMouseCursors.click,
        onEnter: (_) => setState(() => _h = true),
        onExit: (_) => setState(() => _h = false),
        child: GestureDetector(
          onTap: widget.onTap,
          child: SizedBox(width: 36, height: 44, child: Center(
            child: Icon(widget.icon, size: 20,
              color: _h ? BColors.accent.withValues(alpha: 0.5) : BColors.textMuted),
          )),
        ),
      ),
    );
  }
}

// ─── Send / Voice Button ──────────────────────────────────────

class _SendOrVoiceBtn extends StatefulWidget {
  final bool hasText;
  final bool isEdit;
  final VoidCallback onSend;
  final VoidCallback onVoice;
  const _SendOrVoiceBtn({required this.hasText, required this.isEdit,
    required this.onSend, required this.onVoice});
  @override
  State<_SendOrVoiceBtn> createState() => _SendOrVoiceBtnState();
}

class _SendOrVoiceBtnState extends State<_SendOrVoiceBtn> {
  bool _h = false;
  @override
  Widget build(BuildContext context) {
    final isSend = widget.hasText;
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setState(() => _h = true),
      onExit: (_) => setState(() => _h = false),
      child: GestureDetector(
        onTap: isSend ? widget.onSend : widget.onVoice,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 150),
          width: 36, height: 44,
          child: Center(
            child: AnimatedSwitcher(
              duration: const Duration(milliseconds: 150),
              transitionBuilder: (child, anim) => ScaleTransition(scale: anim, child: child),
              child: isSend
                  ? Container(
                      key: const ValueKey('send'),
                      width: 28, height: 28,
                      decoration: BoxDecoration(
                        borderRadius: BorderRadius.circular(8),
                        color: BColors.accent,
                      ),
                      child: Center(child: Icon(
                        widget.isEdit ? SolarIconsBold.checkCircle : SolarIconsBold.plain,
                        size: 16, color: BColors.bg,
                      )),
                    )
                  : Icon(SolarIconsOutline.microphone, key: const ValueKey('mic'), size: 20,
                      color: _h ? BColors.accent.withValues(alpha: 0.5) : BColors.textMuted),
            ),
          ),
        ),
      ),
    );
  }
}

// ─── Attach Popover ───────────────────────────────────────────

class _AttachPopover extends StatelessWidget {
  final ValueChanged<String> onSelect;
  const _AttachPopover({required this.onSelect});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 200,
      padding: const EdgeInsets.symmetric(vertical: 6),
      decoration: BoxDecoration(
        color: const Color(0xF5141418),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
        boxShadow: const [BoxShadow(color: Color(0x80000000), blurRadius: 32, offset: Offset(0, -8))],
      ),
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        _AttachItem(icon: SolarIconsOutline.gallery, label: 'фото / видео', onTap: () => onSelect('photo')),
        _AttachItem(icon: SolarIconsOutline.documentText, label: 'документ', onTap: () => onSelect('doc')),
        _AttachItem(icon: SolarIconsOutline.musicNote, label: 'аудио', onTap: () => onSelect('audio')),
        _AttachItem(icon: SolarIconsOutline.chartSquare, label: 'опрос', onTap: () => onSelect('poll')),
      ]),
    ).animate()
        .scale(begin: const Offset(0.92, 0.92), duration: 150.ms, curve: Curves.easeOutCubic)
        .fade(begin: 0, duration: 150.ms);
  }
}

class _AttachItem extends StatefulWidget {
  final IconData icon;
  final String label;
  final VoidCallback onTap;
  const _AttachItem({required this.icon, required this.label, required this.onTap});
  @override
  State<_AttachItem> createState() => _AttachItemState();
}

class _AttachItemState extends State<_AttachItem> {
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
          duration: const Duration(milliseconds: 100),
          height: 34,
          padding: const EdgeInsets.symmetric(horizontal: 12),
          margin: const EdgeInsets.symmetric(horizontal: 4),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(6),
            color: _h ? Colors.white.withValues(alpha: 0.05) : Colors.transparent,
          ),
          child: Row(children: [
            Icon(widget.icon, size: 16, color: BColors.textMuted),
            const SizedBox(width: 10),
            Text(widget.label, style: const TextStyle(fontFamily: 'Onest', fontSize: 13,
              fontWeight: FontWeight.w400, color: BColors.textSecondary,
              decoration: TextDecoration.none)),
          ]),
        ),
      ),
    );
  }
}

// ─── Recording UI ─────────────────────────────────────────────

class _RecordingUI extends StatelessWidget {
  final int seconds;
  final VoidCallback onCancel;
  final VoidCallback onSend;
  const _RecordingUI({required this.seconds, required this.onCancel, required this.onSend});

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 44,
      child: Row(children: [
        const SizedBox(width: 14),
        // Red dot + timer
        Container(width: 8, height: 8, decoration: const BoxDecoration(
          shape: BoxShape.circle, color: Color(0xFFef4444),
        )).animate(onPlay: (c) => c.repeat(reverse: true))
            .fade(begin: 0.3, end: 1.0, duration: 800.ms),
        const SizedBox(width: 8),
        Text('0:${seconds.toString().padLeft(2, '0')}', style: const TextStyle(
          fontFamily: 'Onest', fontSize: 14, fontWeight: FontWeight.w500, color: BColors.textPrimary)),
        const SizedBox(width: 16),
        // Waveform bars
        Expanded(child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.center,
          children: List.generate(20, (i) => AnimatedContainer(
            duration: Duration(milliseconds: 300 + (i * 30)),
            width: 2.5, height: 6 + (i % 4) * 2.0,
            margin: const EdgeInsets.symmetric(horizontal: 1.5),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(1.5),
              color: BColors.accent.withValues(alpha: 0.35),
            ),
          ).animate(onPlay: (c) => c.repeat(reverse: true), delay: Duration(milliseconds: i * 40))
              .scaleY(begin: 0.3, end: 1.0, duration: 500.ms, curve: Curves.easeInOut),
          ),
        )),
        // Cancel
        _InputIconBtn(icon: SolarIconsOutline.closeCircle, tooltip: 'отмена', onTap: onCancel),
        // Send
        GestureDetector(
          onTap: onSend,
          child: Container(
            width: 28, height: 28,
            margin: const EdgeInsets.only(right: 10),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(8),
              color: BColors.accent,
            ),
            child: const Center(child: Icon(SolarIconsBold.plain, size: 16, color: BColors.bg)),
          ),
        ),
      ]),
    ).animate().fadeIn(duration: 200.ms).slideX(begin: 0.05);
  }
}

// ─── Reply Header ─────────────────────────────────────────────

class _ReplyHeader extends StatelessWidget {
  final String name;
  final String text;
  final VoidCallback onCancel;
  const _ReplyHeader({required this.name, required this.text, required this.onCancel});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 6),
      padding: const EdgeInsets.fromLTRB(14, 8, 8, 8),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(12),
        color: Colors.white.withValues(alpha: 0.03),
        border: Border(left: BorderSide(color: BColors.accent, width: 3)),
      ),
      child: Row(children: [
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(name, style: TextStyle(fontFamily: 'Onest', fontSize: 12,
            fontWeight: FontWeight.w600, color: BColors.accent.withValues(alpha: 0.7))),
          const SizedBox(height: 2),
          Text(text, maxLines: 1, overflow: TextOverflow.ellipsis,
            style: const TextStyle(fontFamily: 'Onest', fontSize: 12, color: BColors.textSecondary)),
        ])),
        MouseRegion(
          cursor: SystemMouseCursors.click,
          child: GestureDetector(onTap: onCancel,
            child: const SizedBox(width: 28, height: 28,
              child: Center(child: Icon(SolarIconsOutline.closeCircle, size: 14, color: BColors.textMuted)))),
        ),
      ]),
    ).animate().fadeIn(duration: 200.ms).slideY(begin: 0.2);
  }
}

// ─── Edit Header ──────────────────────────────────────────────

class _DraftBanner extends StatelessWidget {
  final VoidCallback onDismiss;
  const _DraftBanner({required this.onDismiss});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 6),
      height: 26,
      padding: const EdgeInsets.symmetric(horizontal: 10),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(8),
        color: BColors.accent.withValues(alpha: 0.08),
        border: Border.all(color: BColors.accent.withValues(alpha: 0.2), width: 0.5),
      ),
      child: Row(children: [
        Container(
          width: 5, height: 5,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: BColors.accent.withValues(alpha: 0.85),
          ),
        ),
        const SizedBox(width: 8),
        Text('черновик · восстановлено', style: TextStyle(
          fontFamily: 'Onest', fontSize: 11, fontWeight: FontWeight.w500,
          color: BColors.accent.withValues(alpha: 0.9),
        )),
        const Spacer(),
        MouseRegion(
          cursor: SystemMouseCursors.click,
          child: GestureDetector(
            onTap: onDismiss,
            child: Icon(SolarIconsOutline.closeCircle, size: 12,
                color: BColors.accent.withValues(alpha: 0.8)),
          ),
        ),
      ]),
    ).animate().fadeIn(duration: 180.ms).slideY(begin: -0.4, curve: Curves.easeOut);
  }
}

class _EditHeader extends StatelessWidget {
  final VoidCallback onCancel;
  const _EditHeader({required this.onCancel});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 6),
      padding: const EdgeInsets.fromLTRB(14, 8, 8, 8),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(12),
        color: Colors.white.withValues(alpha: 0.03),
      ),
      child: Row(children: [
        Icon(SolarIconsOutline.pen, size: 14, color: BColors.accent.withValues(alpha: 0.5)),
        const SizedBox(width: 8),
        Text('редактирование', style: TextStyle(fontFamily: 'Onest', fontSize: 12,
          color: BColors.accent.withValues(alpha: 0.7))),
        const Spacer(),
        MouseRegion(
          cursor: SystemMouseCursors.click,
          child: GestureDetector(onTap: onCancel,
            child: const SizedBox(width: 28, height: 28,
              child: Center(child: Icon(SolarIconsOutline.closeCircle, size: 14, color: BColors.textMuted)))),
        ),
      ]),
    ).animate().fadeIn(duration: 200.ms).slideY(begin: 0.2);
  }
}
