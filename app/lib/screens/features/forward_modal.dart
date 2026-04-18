import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:solar_icons/solar_icons.dart';

import '../shared/theme.dart';
import 'chat_bubble_parts.dart' show senderColorFor;
import 'chat_messages.dart';

// ═══════════════════════════════════════════════════════════════
// FORWARD MODAL — multi-select chats, preview, optional caption
// ═══════════════════════════════════════════════════════════════

class ForwardTarget {
  final String id, name, initial;
  final Color? tint;
  final bool isFavourite; // "Избранное"
  const ForwardTarget({
    required this.id, required this.name, required this.initial,
    this.tint, this.isFavourite = false,
  });
}

/// Simple stub target list — same ids as stubMessages chats.
const List<ForwardTarget> _favouriteTargets = [
  ForwardTarget(
    id: 'saved', name: 'избранное', initial: '★',
    tint: BColors.accent, isFavourite: true,
  ),
];

const List<ForwardTarget> _chatTargets = [
  ForwardTarget(id: 'c1', name: 'Катя', initial: 'К'),
  ForwardTarget(id: 'c2', name: 'Дизайн-банда', initial: 'Д'),
  ForwardTarget(id: 'c3', name: 'Максим', initial: 'М'),
  ForwardTarget(id: 'c4', name: 'blesk team', initial: 'B'),
  ForwardTarget(id: 'c5', name: 'Аня', initial: 'А'),
  ForwardTarget(id: 'c6', name: 'Лиза', initial: 'Л'),
];

OverlayEntry showForwardModal(BuildContext context, {
  required MessageData source,
  required String sourceChatId,
  required String sourceSenderName,
  ValueChanged<List<String>>? onForwarded,
}) {
  final overlay = Overlay.of(context);
  late OverlayEntry entry;
  entry = OverlayEntry(builder: (_) => _ForwardModal(
    source: source,
    sourceChatId: sourceChatId,
    sourceSenderName: sourceSenderName,
    onClose: () => entry.remove(),
    onConfirm: (selected, caption, hideAuthor) {
      _performForward(source, sourceSenderName, selected,
          caption: caption, hideAuthor: hideAuthor);
      entry.remove();
      onForwarded?.call(selected);
    },
  ));
  overlay.insert(entry);
  return entry;
}

void _performForward(
  MessageData source,
  String sourceSender,
  List<String> targetChatIds, {
  String caption = '',
  bool hideAuthor = false,
}) {
  for (final chatId in targetChatIds) {
    final msgs = stubMessages[chatId] ??= [];
    final newMsg = MessageData(
      id: '${DateTime.now().microsecondsSinceEpoch}_$chatId',
      text: source.text,
      time: 'сейчас',
      own: true,
      read: false,
      type: source.type,
      forwardFrom: hideAuthor ? null : sourceSender,
      photoTints: source.photoTints,
      videoDuration: source.videoDuration,
      gifTint: source.gifTint,
      voiceDuration: source.voiceDuration,
      waveform: source.waveform,
      fileName: source.fileName,
      fileSize: source.fileSize,
      fileExt: source.fileExt,
      stickerEmoji: source.stickerEmoji,
      linkPreview: source.linkPreview,
    );
    msgs.add(newMsg);
    if (caption.isNotEmpty) {
      msgs.add(MessageData(
        id: '${DateTime.now().microsecondsSinceEpoch}_${chatId}_c',
        text: caption, time: 'сейчас', own: true, read: false,
      ));
    }
  }
}

// ─── ROOT ─────────────────────────────────────────────────────

class _ForwardModal extends StatefulWidget {
  final MessageData source;
  final String sourceChatId, sourceSenderName;
  final VoidCallback onClose;
  final void Function(List<String> selected, String caption, bool hideAuthor) onConfirm;
  const _ForwardModal({
    required this.source, required this.sourceChatId, required this.sourceSenderName,
    required this.onClose, required this.onConfirm,
  });
  @override
  State<_ForwardModal> createState() => _ForwardModalState();
}

class _ForwardModalState extends State<_ForwardModal> {
  final _searchCtrl = TextEditingController();
  final _captionCtrl = TextEditingController();
  final Set<String> _selected = {};
  String _query = '';
  bool _hideAuthor = false;

  @override
  void dispose() {
    _searchCtrl.dispose();
    _captionCtrl.dispose();
    super.dispose();
  }

  List<ForwardTarget> _filter(List<ForwardTarget> list) {
    final q = _query.trim().toLowerCase();
    if (q.isEmpty) return list;
    return list.where((t) => t.name.toLowerCase().contains(q)).toList();
  }

  @override
  Widget build(BuildContext context) {
    // Exclude source chat from list
    final chats = _chatTargets.where((t) => t.id != widget.sourceChatId).toList();
    final filteredChats = _filter(chats);
    final filteredFav = _filter(_favouriteTargets);

    return Material(
      color: Colors.transparent,
      child: KeyboardListener(
        focusNode: FocusNode()..requestFocus(),
        onKeyEvent: (e) {
          if (e is KeyDownEvent && e.logicalKey == LogicalKeyboardKey.escape) {
            widget.onClose();
          }
        },
        child: Stack(children: [
          Positioned.fill(child: GestureDetector(
            onTap: widget.onClose,
            behavior: HitTestBehavior.opaque,
            child: BackdropFilter(
              filter: ImageFilter.blur(sigmaX: 6, sigmaY: 6),
              child: Container(color: Colors.black.withValues(alpha: 0.4)),
            ),
          )),
          Center(child: Container(
            width: 480,
            constraints: const BoxConstraints(maxHeight: 620),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(16),
              color: const Color(0xF50e0e12),
              border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
              boxShadow: [BoxShadow(
                color: Colors.black.withValues(alpha: 0.5),
                blurRadius: 60, offset: const Offset(0, 18),
              )],
            ),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(16),
              child: Column(mainAxisSize: MainAxisSize.min, children: [
                // Header
                Container(
                  height: 48,
                  padding: const EdgeInsets.fromLTRB(18, 0, 10, 0),
                  decoration: const BoxDecoration(
                    border: Border(bottom: BorderSide(color: BColors.borderLow)),
                  ),
                  child: Row(children: [
                    const Expanded(child: Text('переслать', style: TextStyle(
                      fontFamily: 'Nekst', fontSize: 14, fontWeight: FontWeight.w600,
                      color: BColors.textPrimary,
                    ))),
                    _CloseBtn(onTap: widget.onClose),
                  ]),
                ),
                // Preview
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
                  child: _SourcePreview(
                    source: widget.source, senderName: widget.sourceSenderName,
                  ),
                ),
                // Search
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  child: _SearchField(
                    controller: _searchCtrl,
                    onChanged: (v) => setState(() => _query = v),
                  ),
                ),
                const Divider(height: 16, color: BColors.borderLow),
                // List
                Flexible(child: ListView(
                  padding: const EdgeInsets.only(bottom: 8),
                  shrinkWrap: true,
                  children: [
                    if (filteredFav.isNotEmpty) ...[
                      const _SectionHeader('избранное'),
                      for (final t in filteredFav)
                        _TargetRow(
                          target: t,
                          selected: _selected.contains(t.id),
                          onToggle: () => setState(() {
                            if (_selected.contains(t.id)) {
                              _selected.remove(t.id);
                            } else {
                              _selected.add(t.id);
                            }
                          }),
                        ),
                    ],
                    if (filteredChats.isNotEmpty) ...[
                      const _SectionHeader('все чаты'),
                      for (final t in filteredChats)
                        _TargetRow(
                          target: t,
                          selected: _selected.contains(t.id),
                          onToggle: () => setState(() {
                            if (_selected.contains(t.id)) {
                              _selected.remove(t.id);
                            } else {
                              _selected.add(t.id);
                            }
                          }),
                        ),
                    ],
                    if (filteredFav.isEmpty && filteredChats.isEmpty)
                      const Padding(
                        padding: EdgeInsets.symmetric(vertical: 40, horizontal: 24),
                        child: Center(child: Text(
                          'ничего не найдено',
                          style: TextStyle(fontFamily: 'Onest', fontSize: 13,
                              color: BColors.textMuted),
                        )),
                      ),
                  ],
                )),
                // Bottom bar (options + confirm)
                Container(
                  padding: const EdgeInsets.fromLTRB(16, 10, 16, 14),
                  decoration: const BoxDecoration(
                    border: Border(top: BorderSide(color: BColors.borderLow)),
                  ),
                  child: Column(mainAxisSize: MainAxisSize.min, children: [
                    _HideAuthorToggle(
                      value: _hideAuthor,
                      onChanged: (v) => setState(() => _hideAuthor = v),
                    ),
                    const SizedBox(height: 8),
                    _CaptionField(controller: _captionCtrl),
                    const SizedBox(height: 10),
                    Row(children: [
                      _GhostBtn(label: 'отмена', onTap: widget.onClose),
                      const Spacer(),
                      _PrimaryBtn(
                        label: _selected.isEmpty
                            ? 'переслать'
                            : 'переслать (${_selected.length})',
                        enabled: _selected.isNotEmpty,
                        onTap: () => widget.onConfirm(
                          _selected.toList(),
                          _captionCtrl.text.trim(),
                          _hideAuthor,
                        ),
                      ),
                    ]),
                  ]),
                ),
              ]),
            ),
          ).animate()
              .scale(begin: const Offset(0.94, 0.94),
                  duration: 180.ms, curve: Curves.easeOutCubic)
              .fade(duration: 150.ms)),
        ]),
      ),
    );
  }
}

// ─── SOURCE PREVIEW ───────────────────────────────────────────

class _SourcePreview extends StatelessWidget {
  final MessageData source;
  final String senderName;
  const _SourcePreview({required this.source, required this.senderName});
  @override
  Widget build(BuildContext context) {
    final tint = senderColorFor(senderName);
    return Container(
      padding: const EdgeInsets.fromLTRB(10, 8, 10, 8),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(10),
        color: Colors.white.withValues(alpha: 0.04),
        border: Border(left: BorderSide(color: tint, width: 2.5)),
      ),
      child: Row(children: [
        Expanded(child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(senderName, style: TextStyle(
              fontFamily: 'Onest', fontSize: 12, fontWeight: FontWeight.w600,
              color: tint.withValues(alpha: 0.9),
            )),
            const SizedBox(height: 2),
            Text(
              _previewText(source),
              maxLines: 2, overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                fontFamily: 'Onest', fontSize: 12,
                color: BColors.textSecondary, height: 1.4,
              ),
            ),
          ],
        )),
      ]),
    );
  }

  String _previewText(MessageData m) {
    if (m.text != null && m.text!.isNotEmpty) return m.text!;
    return switch (m.type) {
      MessageType.photo => '📷 фото',
      MessageType.video => '🎞 видео',
      MessageType.voice => '🎤 голосовое сообщение',
      MessageType.file => '📄 ${m.fileName ?? "файл"}',
      MessageType.sticker => '${m.stickerEmoji ?? "🎉"} стикер',
      MessageType.gif => 'GIF',
      MessageType.link => m.linkPreview?.url ?? 'ссылка',
      _ => 'сообщение',
    };
  }
}

// ─── SEARCH FIELD ─────────────────────────────────────────────

class _SearchField extends StatelessWidget {
  final TextEditingController controller;
  final ValueChanged<String> onChanged;
  const _SearchField({required this.controller, required this.onChanged});
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
        Icon(SolarIconsOutline.magnifier, size: 14,
            color: BColors.textMuted.withValues(alpha: 0.9)),
        const SizedBox(width: 8),
        Expanded(child: TextField(
          controller: controller,
          onChanged: onChanged,
          style: const TextStyle(
            fontFamily: 'Onest', fontSize: 13, color: BColors.textPrimary,
          ),
          cursorColor: BColors.accent,
          decoration: const InputDecoration(
            border: InputBorder.none, isDense: true,
            hintText: 'куда переслать...',
            hintStyle: TextStyle(
              fontFamily: 'Onest', fontSize: 13, color: BColors.textMuted,
            ),
            contentPadding: EdgeInsets.symmetric(vertical: 8),
          ),
        )),
      ]),
    );
  }
}

// ─── SECTION HEADER ───────────────────────────────────────────

class _SectionHeader extends StatelessWidget {
  final String title;
  const _SectionHeader(this.title);
  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 10, 20, 4),
      child: Text(title.toUpperCase(), style: const TextStyle(
        fontFamily: 'Onest', fontSize: 10, fontWeight: FontWeight.w600,
        color: BColors.textMuted, letterSpacing: 1.2,
      )),
    );
  }
}

// ─── TARGET ROW ───────────────────────────────────────────────

class _TargetRow extends StatefulWidget {
  final ForwardTarget target;
  final bool selected;
  final VoidCallback onToggle;
  const _TargetRow({
    required this.target, required this.selected, required this.onToggle,
  });
  @override
  State<_TargetRow> createState() => _TargetRowState();
}

class _TargetRowState extends State<_TargetRow> {
  bool _h = false;
  @override
  Widget build(BuildContext context) {
    final t = widget.target;
    final tint = t.tint ?? senderColorFor(t.name);
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setState(() => _h = true),
      onExit: (_) => setState(() => _h = false),
      child: GestureDetector(
        onTap: widget.onToggle,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 100),
          height: 46,
          margin: const EdgeInsets.symmetric(horizontal: 8),
          padding: const EdgeInsets.symmetric(horizontal: 10),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(8),
            color: widget.selected
                ? BColors.accent.withValues(alpha: 0.1)
                : _h ? Colors.white.withValues(alpha: 0.04) : Colors.transparent,
            border: Border.all(
              color: widget.selected
                  ? BColors.accent.withValues(alpha: 0.3)
                  : Colors.transparent,
              width: 0.5,
            ),
          ),
          child: Row(children: [
            Container(
              width: 30, height: 30,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(8),
                color: tint.withValues(alpha: 0.16),
                border: Border.all(color: tint.withValues(alpha: 0.25), width: 0.5),
              ),
              child: Center(child: Text(t.initial, style: TextStyle(
                fontFamily: 'Onest', fontSize: 12, fontWeight: FontWeight.w700,
                color: tint.withValues(alpha: 0.95),
              ))),
            ),
            const SizedBox(width: 10),
            Expanded(child: Text(t.name,
                maxLines: 1, overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  fontFamily: 'Onest', fontSize: 13,
                  fontWeight: widget.selected ? FontWeight.w500 : FontWeight.w400,
                  color: BColors.textPrimary,
                ))),
            AnimatedContainer(
              duration: const Duration(milliseconds: 150),
              width: 18, height: 18,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(5),
                color: widget.selected ? BColors.accent : Colors.transparent,
                border: Border.all(
                  color: widget.selected ? BColors.accent : Colors.white.withValues(alpha: 0.25),
                  width: 1.2,
                ),
              ),
              child: widget.selected
                  ? const Icon(SolarIconsBold.checkCircle, size: 12, color: BColors.bg)
                  : null,
            ),
          ]),
        ),
      ),
    );
  }
}

// ─── HIDE AUTHOR TOGGLE ───────────────────────────────────────

class _HideAuthorToggle extends StatelessWidget {
  final bool value;
  final ValueChanged<bool> onChanged;
  const _HideAuthorToggle({required this.value, required this.onChanged});

  @override
  Widget build(BuildContext context) {
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      child: GestureDetector(
        onTap: () => onChanged(!value),
        child: Row(mainAxisSize: MainAxisSize.min, children: [
          AnimatedContainer(
            duration: const Duration(milliseconds: 150),
            width: 14, height: 14,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(4),
              color: value ? BColors.accent : Colors.transparent,
              border: Border.all(
                color: value ? BColors.accent : Colors.white.withValues(alpha: 0.3),
                width: 1,
              ),
            ),
            child: value
                ? const Icon(SolarIconsBold.checkCircle, size: 10, color: BColors.bg)
                : null,
          ),
          const SizedBox(width: 8),
          const Text('скрыть имя отправителя', style: TextStyle(
            fontFamily: 'Onest', fontSize: 12, color: BColors.textSecondary,
          )),
        ]),
      ),
    );
  }
}

// ─── CAPTION FIELD ────────────────────────────────────────────

class _CaptionField extends StatelessWidget {
  final TextEditingController controller;
  const _CaptionField({required this.controller});
  @override
  Widget build(BuildContext context) {
    return Container(
      constraints: const BoxConstraints(minHeight: 36),
      padding: const EdgeInsets.symmetric(horizontal: 12),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(8),
        color: Colors.white.withValues(alpha: 0.04),
        border: Border.all(color: Colors.white.withValues(alpha: 0.06), width: 0.5),
      ),
      child: TextField(
        controller: controller,
        maxLines: 2, minLines: 1,
        style: const TextStyle(
          fontFamily: 'Onest', fontSize: 13, color: BColors.textPrimary,
        ),
        cursorColor: BColors.accent,
        decoration: const InputDecoration(
          border: InputBorder.none, isDense: true,
          hintText: 'добавить подпись (необязательно)',
          hintStyle: TextStyle(
            fontFamily: 'Onest', fontSize: 13, color: BColors.textMuted,
          ),
          contentPadding: EdgeInsets.symmetric(vertical: 8),
        ),
      ),
    );
  }
}

// ─── BUTTONS ──────────────────────────────────────────────────

class _GhostBtn extends StatefulWidget {
  final String label;
  final VoidCallback onTap;
  const _GhostBtn({required this.label, required this.onTap});
  @override
  State<_GhostBtn> createState() => _GhostBtnState();
}

class _GhostBtnState extends State<_GhostBtn> {
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
          height: 32, padding: const EdgeInsets.symmetric(horizontal: 14),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(7),
            color: _h ? Colors.white.withValues(alpha: 0.05) : Colors.transparent,
          ),
          alignment: Alignment.center,
          child: Text(widget.label, style: TextStyle(
            fontFamily: 'Onest', fontSize: 12, fontWeight: FontWeight.w500,
            color: _h ? BColors.textPrimary : BColors.textSecondary,
          )),
        ),
      ),
    );
  }
}

class _PrimaryBtn extends StatefulWidget {
  final String label;
  final bool enabled;
  final VoidCallback onTap;
  const _PrimaryBtn({required this.label, required this.enabled, required this.onTap});
  @override
  State<_PrimaryBtn> createState() => _PrimaryBtnState();
}

class _PrimaryBtnState extends State<_PrimaryBtn> {
  bool _h = false;
  @override
  Widget build(BuildContext context) {
    return Opacity(
      opacity: widget.enabled ? 1 : 0.3,
      child: IgnorePointer(
        ignoring: !widget.enabled,
        child: MouseRegion(
          cursor: SystemMouseCursors.click,
          onEnter: (_) => setState(() => _h = true),
          onExit: (_) => setState(() => _h = false),
          child: GestureDetector(
            onTap: widget.onTap,
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 120),
              height: 32, padding: const EdgeInsets.symmetric(horizontal: 16),
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(7),
                color: _h
                    ? const Color(0xFFd4ff33)
                    : BColors.accent.withValues(alpha: 0.88),
              ),
              alignment: Alignment.center,
              child: Text(widget.label, style: const TextStyle(
                fontFamily: 'Onest', fontSize: 12, fontWeight: FontWeight.w600,
                color: BColors.bg,
              )),
            ),
          ),
        ),
      ),
    );
  }
}

// ─── CLOSE BUTTON ─────────────────────────────────────────────

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
