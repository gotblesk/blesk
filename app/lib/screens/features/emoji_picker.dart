import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';

import '../shared/theme.dart';

/// Glass emoji picker popup — shown above the input bar.
class EmojiPicker extends StatefulWidget {
  final ValueChanged<String> onSelect;
  final VoidCallback onClose;
  const EmojiPicker({super.key, required this.onSelect, required this.onClose});
  @override
  State<EmojiPicker> createState() => _EmojiPickerState();
}

class _EmojiPickerState extends State<EmojiPicker> {
  int _tab = 0;
  final _searchCtrl = TextEditingController();
  String _search = '';

  static const _tabs = ['часто', '😊', '🐱', '🍕', '⚽', '🏠', '💡', '🚩'];

  static const _recentEmojis = ['😊', '👍', '❤️', '😂', '🔥', '✨', '😍', '👋',
    '🎉', '💪', '😎', '🙏', '💜', '😭', '🤔', '👀',
    '💀', '🫡', '🤝', '😤', '🥺', '✅', '🎯', '💯'];

  static const _smileys = ['😀', '😃', '😄', '😁', '😅', '😂', '🤣', '😊',
    '😇', '🙂', '😉', '😌', '😍', '🥰', '😘', '😗',
    '😋', '😛', '😜', '🤪', '😝', '🤑', '🤗', '🤭',
    '🤫', '🤔', '😐', '😑', '😶', '😏', '😒', '🙄',
    '😬', '😮', '🤐', '😥', '😢', '😭', '😤', '😡',
    '🤬', '😈', '👿', '💀', '☠️', '💩', '🤡', '👹'];

  static const _animals = ['🐶', '🐱', '🐭', '🐹', '🐰', '🦊', '🐻', '🐼',
    '🐨', '🐯', '🦁', '🐮', '🐷', '🐸', '🐵', '🐔',
    '🐧', '🐦', '🐤', '🦆', '🦅', '🦉', '🦇', '🐺',
    '🐗', '🐴', '🦄', '🐝', '🪱', '🐛', '🦋', '🐌'];

  static const _food = ['🍎', '🍐', '🍊', '🍋', '🍌', '🍉', '🍇', '🍓',
    '🫐', '🍈', '🍒', '🍑', '🥭', '🍍', '🥥', '🥝',
    '🍅', '🥑', '🍆', '🥔', '🥕', '🌽', '🌶️', '🫑',
    '🍕', '🍔', '🍟', '🌭', '🍿', '🧂', '🥓', '🍳'];

  List<String> get _currentEmojis {
    List<String> emojis;
    switch (_tab) {
      case 0: emojis = _recentEmojis;
      case 1: emojis = _smileys;
      case 2: emojis = _animals;
      case 3: emojis = _food;
      default: emojis = _smileys;
    }
    if (_search.isNotEmpty) {
      return emojis; // real app would filter by name
    }
    return emojis;
  }

  @override
  void dispose() {
    _searchCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Stack(children: [
      Positioned.fill(child: GestureDetector(onTap: widget.onClose,
        child: Container(color: Colors.transparent))),
      Positioned(
        right: 16, bottom: 70,
        child: Material(
          color: Colors.transparent,
          child: Container(
          width: 320, height: 380,
          decoration: BoxDecoration(
            color: const Color(0xF5121216),
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
            boxShadow: const [BoxShadow(color: Color(0x60000000), blurRadius: 32, offset: Offset(0, -8))],
          ),
          child: Column(children: [
            // Tabs
            Container(
              height: 40,
              padding: const EdgeInsets.symmetric(horizontal: 8),
              decoration: BoxDecoration(
                border: Border(bottom: BorderSide(color: BColors.borderLow)),
              ),
              child: Row(children: [
                for (var i = 0; i < _tabs.length; i++)
                  _EmojiTab(
                    label: _tabs[i],
                    active: _tab == i,
                    onTap: () => setState(() => _tab = i),
                  ),
              ]),
            ),
            // Search
            Container(
              height: 36,
              margin: const EdgeInsets.fromLTRB(10, 8, 10, 4),
              padding: const EdgeInsets.symmetric(horizontal: 10),
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(8),
                color: Colors.white.withValues(alpha: 0.04),
              ),
              child: TextField(
                controller: _searchCtrl,
                onChanged: (v) => setState(() => _search = v),
                style: const TextStyle(fontFamily: 'Onest', fontSize: 12, color: BColors.textPrimary),
                cursorColor: BColors.accent,
                decoration: InputDecoration(
                  border: InputBorder.none, isDense: true,
                  hintText: 'поиск эмодзи...',
                  hintStyle: TextStyle(fontFamily: 'Onest', fontSize: 12, color: BColors.textMuted),
                  contentPadding: const EdgeInsets.symmetric(vertical: 9),
                  prefixIcon: Icon(Icons.search, size: 14, color: BColors.textMuted),
                  prefixIconConstraints: const BoxConstraints(minWidth: 24),
                ),
              ),
            ),
            // Grid
            Expanded(
              child: GridView.builder(
                padding: const EdgeInsets.all(8),
                gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                  crossAxisCount: 8, mainAxisSpacing: 2, crossAxisSpacing: 2,
                ),
                itemCount: _currentEmojis.length,
                itemBuilder: (_, i) => _EmojiCell(
                  emoji: _currentEmojis[i],
                  onTap: () => widget.onSelect(_currentEmojis[i]),
                ),
              ),
            ),
          ]),
        ),
        ).animate()
            .scale(begin: const Offset(0.95, 0.95), duration: 150.ms, curve: Curves.easeOutCubic)
            .fade(begin: 0, duration: 150.ms),
      ),
    ]);
  }
}

class _EmojiTab extends StatefulWidget {
  final String label;
  final bool active;
  final VoidCallback onTap;
  const _EmojiTab({required this.label, required this.active, required this.onTap});
  @override
  State<_EmojiTab> createState() => _EmojiTabState();
}

class _EmojiTabState extends State<_EmojiTab> {
  bool _h = false;
  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: MouseRegion(
        cursor: SystemMouseCursors.click,
        onEnter: (_) => setState(() => _h = true),
        onExit: (_) => setState(() => _h = false),
        child: GestureDetector(
          onTap: widget.onTap,
          child: Container(
            decoration: BoxDecoration(
              border: Border(bottom: BorderSide(
                color: widget.active ? BColors.accent : Colors.transparent, width: 2)),
            ),
            child: Center(child: Text(widget.label, style: TextStyle(
              fontSize: widget.label.length > 2 ? 11 : 16,
              color: widget.active ? BColors.textPrimary
                  : _h ? BColors.textSecondary : BColors.textMuted,
            ))),
          ),
        ),
      ),
    );
  }
}

class _EmojiCell extends StatefulWidget {
  final String emoji;
  final VoidCallback onTap;
  const _EmojiCell({required this.emoji, required this.onTap});
  @override
  State<_EmojiCell> createState() => _EmojiCellState();
}

class _EmojiCellState extends State<_EmojiCell> {
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
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(6),
            color: _h ? Colors.white.withValues(alpha: 0.06) : Colors.transparent,
          ),
          child: Center(child: AnimatedScale(
            scale: _h ? 1.2 : 1.0,
            duration: const Duration(milliseconds: 100),
            child: Text(widget.emoji, style: const TextStyle(fontSize: 22)),
          )),
        ),
      ),
    );
  }
}
