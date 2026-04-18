import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:solar_icons/solar_icons.dart';

import '../shared/theme.dart';
import 'chat_bubble_parts.dart' show senderColorFor;

// ═══════════════════════════════════════════════════════════════
// GLOBAL SEARCH — Command palette (Ctrl+K)
// ═══════════════════════════════════════════════════════════════

enum SearchResultKind { chat, contact, message, channel }

class SearchResult {
  final SearchResultKind kind;
  final String id;
  final String title;
  final String? subtitle;
  final String? initial; // avatar letter
  final Color? tint;
  final String? matchSnippet; // for message hits — highlighted excerpt
  final String? inChat; // context (for message/channel hits)
  const SearchResult({
    required this.kind,
    required this.id,
    required this.title,
    this.subtitle,
    this.initial,
    this.tint,
    this.matchSnippet,
    this.inChat,
  });
}

// ─── STUB DATASOURCE ──────────────────────────────────────────

class SearchCatalog {
  final List<SearchResult> all;
  final List<String> recentChatIds; // recently opened chats
  const SearchCatalog({required this.all, this.recentChatIds = const []});

  List<SearchResult> search(String q) {
    if (q.trim().isEmpty) return const [];
    final lower = q.toLowerCase();
    return all.where((r) {
      final t = r.title.toLowerCase();
      final s = (r.subtitle ?? '').toLowerCase();
      final sn = (r.matchSnippet ?? '').toLowerCase();
      return t.contains(lower) || s.contains(lower) || sn.contains(lower);
    }).toList();
  }

  List<SearchResult> recent() {
    final byId = {for (final r in all) r.id: r};
    return recentChatIds
        .map((id) => byId[id])
        .whereType<SearchResult>()
        .take(5)
        .toList();
  }
}

/// Default stub catalog — used when no catalog is provided.
SearchCatalog kDefaultCatalog = const SearchCatalog(
  recentChatIds: ['c1', 'c2', 'c3'],
  all: [
    // Chats
    SearchResult(kind: SearchResultKind.chat, id: 'c1',
        title: 'Катя', subtitle: 'привет! как дела?', initial: 'К'),
    SearchResult(kind: SearchResultKind.chat, id: 'c2',
        title: 'Дизайн-банда', subtitle: 'скинул макеты в фигму', initial: 'Д'),
    SearchResult(kind: SearchResultKind.chat, id: 'c3',
        title: 'Максим', subtitle: 'завтра созвон в 10?', initial: 'М'),
    SearchResult(kind: SearchResultKind.chat, id: 'c4',
        title: 'blesk team', subtitle: 'новый билд готов', initial: 'B'),
    // Contacts
    SearchResult(kind: SearchResultKind.contact, id: 'u_artem',
        title: 'Артём', subtitle: '@artem_404', initial: 'А'),
    SearchResult(kind: SearchResultKind.contact, id: 'u_liza',
        title: 'Лиза', subtitle: '@lizad', initial: 'Л'),
    SearchResult(kind: SearchResultKind.contact, id: 'u_ann',
        title: 'Аня', subtitle: '@anyaya', initial: 'А'),
    // Messages
    SearchResult(kind: SearchResultKind.message, id: 'm1',
        title: 'скинул макеты в фигму',
        matchSnippet: '...макеты в фигму, посмотри...',
        inChat: 'Дизайн-банда · Артём', initial: 'А'),
    SearchResult(kind: SearchResultKind.message, id: 'm2',
        title: 'завтра созвон в 10?',
        matchSnippet: '...созвон в 10 утра...',
        inChat: 'Максим', initial: 'М'),
    SearchResult(kind: SearchResultKind.message, id: 'm3',
        title: 'новый билд готов',
        matchSnippet: '...билд готов, v1.0.7-beta...',
        inChat: 'blesk team · bot', initial: 'B'),
    // Channels
    SearchResult(kind: SearchResultKind.channel, id: 'ch_news',
        title: 'blesk news', subtitle: '1.2к подписчиков', initial: 'bl'),
    SearchResult(kind: SearchResultKind.channel, id: 'ch_design',
        title: 'design daily', subtitle: '480 подписчиков', initial: 'dd'),
  ],
);

// ─── ENTRY ────────────────────────────────────────────────────

/// Shows the command palette. Returns the overlay entry — call .remove() to dismiss.
OverlayEntry showGlobalSearch(
  BuildContext context, {
  SearchCatalog? catalog,
  ValueChanged<SearchResult>? onPick,
}) {
  final overlay = Overlay.of(context);
  late OverlayEntry entry;
  entry = OverlayEntry(builder: (_) => GlobalSearch(
    catalog: catalog ?? kDefaultCatalog,
    onPick: (r) {
      entry.remove();
      onPick?.call(r);
    },
    onClose: () => entry.remove(),
  ));
  overlay.insert(entry);
  return entry;
}

// ─── PALETTE ROOT ─────────────────────────────────────────────

class GlobalSearch extends StatefulWidget {
  final SearchCatalog catalog;
  final ValueChanged<SearchResult> onPick;
  final VoidCallback onClose;
  const GlobalSearch({
    super.key,
    required this.catalog,
    required this.onPick,
    required this.onClose,
  });

  @override
  State<GlobalSearch> createState() => _GlobalSearchState();
}

class _GlobalSearchState extends State<GlobalSearch> {
  final _ctrl = TextEditingController();
  final _focus = FocusNode();
  final _scroll = ScrollController();
  String _query = '';
  int _active = 0;

  @override
  void initState() {
    super.initState();
    _focus.requestFocus();
    _ctrl.addListener(() {
      if (_ctrl.text != _query) {
        setState(() {
          _query = _ctrl.text;
          _active = 0;
        });
      }
    });
  }

  @override
  void dispose() {
    _ctrl.dispose();
    _focus.dispose();
    _scroll.dispose();
    super.dispose();
  }

  List<SearchResult> get _flatResults {
    final trimmed = _query.trim();
    if (trimmed.isEmpty) return widget.catalog.recent();
    return widget.catalog.search(trimmed);
  }

  /// Build grouped sections: returns list of (sectionHeader?, items).
  List<_Section> get _sections {
    final results = _flatResults;
    if (_query.trim().isEmpty) {
      return results.isEmpty
          ? const []
          : [_Section(title: 'недавние', items: results)];
    }
    final byKind = <SearchResultKind, List<SearchResult>>{};
    for (final r in results) {
      byKind.putIfAbsent(r.kind, () => []).add(r);
    }
    return [
      if (byKind.containsKey(SearchResultKind.chat))
        _Section(title: 'чаты', items: byKind[SearchResultKind.chat]!),
      if (byKind.containsKey(SearchResultKind.contact))
        _Section(title: 'контакты', items: byKind[SearchResultKind.contact]!),
      if (byKind.containsKey(SearchResultKind.message))
        _Section(title: 'сообщения', items: byKind[SearchResultKind.message]!),
      if (byKind.containsKey(SearchResultKind.channel))
        _Section(title: 'каналы', items: byKind[SearchResultKind.channel]!),
    ];
  }

  void _onKey(KeyEvent e) {
    if (e is! KeyDownEvent) return;
    final k = e.logicalKey;
    final flat = _flatResults;
    if (k == LogicalKeyboardKey.escape) {
      widget.onClose();
    } else if (k == LogicalKeyboardKey.arrowDown) {
      if (flat.isEmpty) return;
      setState(() => _active = (_active + 1) % flat.length);
    } else if (k == LogicalKeyboardKey.arrowUp) {
      if (flat.isEmpty) return;
      setState(() => _active = (_active - 1 + flat.length) % flat.length);
    } else if (k == LogicalKeyboardKey.enter) {
      if (flat.isEmpty) return;
      widget.onPick(flat[_active.clamp(0, flat.length - 1)]);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: KeyboardListener(
        focusNode: FocusNode()..requestFocus(),
        onKeyEvent: _onKey,
        child: Stack(children: [
          // Backdrop
          Positioned.fill(child: GestureDetector(
            onTap: widget.onClose,
            behavior: HitTestBehavior.opaque,
            child: BackdropFilter(
              filter: ImageFilter.blur(sigmaX: 6, sigmaY: 6),
              child: Container(color: Colors.black.withValues(alpha: 0.35)),
            ),
          )),

          // Palette
          Center(child: _buildPalette(context)),
        ]),
      ),
    );
  }

  Widget _buildPalette(BuildContext context) {
    final sections = _sections;
    final flat = _flatResults;
    final hasResults = flat.isNotEmpty;

    return Container(
      width: 520,
      constraints: const BoxConstraints(maxHeight: 480),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(16),
        color: const Color(0xF50e0e12),
        border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.6),
            blurRadius: 80, offset: const Offset(0, 24),
          ),
        ],
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(16),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          // Search input row
          _SearchInput(
            controller: _ctrl,
            focus: _focus,
            onEsc: widget.onClose,
          ),
          const Divider(height: 1, color: BColors.borderLow),

          // Results / empty / sections
          Flexible(child: hasResults
              ? _buildResults(sections, flat)
              : _buildEmpty()),

          // Footer: shortcuts hint
          _FooterHints(),
        ]),
      ),
    ).animate()
        .scale(begin: const Offset(0.96, 0.96),
            duration: 180.ms, curve: Curves.easeOutCubic)
        .fade(duration: 140.ms);
  }

  Widget _buildResults(List<_Section> sections, List<SearchResult> flat) {
    // Build a flat list of widgets with section headers and flat index tracking.
    final widgets = <Widget>[];
    int cursor = 0;
    for (final s in sections) {
      widgets.add(_SectionHeader(title: s.title));
      for (final item in s.items) {
        final idx = cursor;
        widgets.add(_ResultRow(
          result: item,
          highlight: _query.trim(),
          active: idx == _active,
          onTap: () => widget.onPick(item),
          onHover: () => setState(() => _active = idx),
        ));
        cursor++;
      }
    }
    return ListView(
      controller: _scroll,
      padding: const EdgeInsets.symmetric(vertical: 6),
      shrinkWrap: true,
      children: widgets,
    );
  }

  Widget _buildEmpty() {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 36, horizontal: 24),
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        Icon(SolarIconsBroken.magnifier, size: 38,
            color: BColors.textMuted.withValues(alpha: 0.5)),
        const SizedBox(height: 10),
        Text(_query.trim().isEmpty
              ? 'начни вводить чтобы найти чат, контакт или сообщение'
              : 'ничего не найдено по «$_query»',
          textAlign: TextAlign.center,
          style: const TextStyle(
            fontFamily: 'Onest', fontSize: 13,
            color: BColors.textSecondary, height: 1.5,
          ),
        ),
      ]),
    );
  }
}

class _Section {
  final String title;
  final List<SearchResult> items;
  const _Section({required this.title, required this.items});
}

// ─── INPUT ROW ────────────────────────────────────────────────

class _SearchInput extends StatelessWidget {
  final TextEditingController controller;
  final FocusNode focus;
  final VoidCallback onEsc;
  const _SearchInput({
    required this.controller, required this.focus, required this.onEsc,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 52,
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Row(children: [
        Icon(SolarIconsOutline.magnifier, size: 18,
            color: BColors.textMuted.withValues(alpha: 0.8)),
        const SizedBox(width: 10),
        Expanded(child: TextField(
          controller: controller,
          focusNode: focus,
          style: const TextStyle(
            fontFamily: 'Onest', fontSize: 14, color: BColors.textPrimary,
          ),
          cursorColor: BColors.accent,
          decoration: const InputDecoration(
            border: InputBorder.none, isDense: true,
            hintText: 'поиск...',
            hintStyle: TextStyle(
              fontFamily: 'Onest', fontSize: 14, color: BColors.textMuted,
            ),
            contentPadding: EdgeInsets.symmetric(vertical: 14),
          ),
        )),
        MouseRegion(
          cursor: SystemMouseCursors.click,
          child: GestureDetector(
            onTap: onEsc,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(4),
                color: Colors.white.withValues(alpha: 0.06),
                border: Border.all(color: Colors.white.withValues(alpha: 0.1), width: 0.5),
              ),
              child: const Text('esc', style: TextStyle(
                fontFamily: 'Onest', fontSize: 10, fontWeight: FontWeight.w500,
                color: BColors.textMuted,
              )),
            ),
          ),
        ),
      ]),
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
      padding: const EdgeInsets.fromLTRB(20, 10, 20, 4),
      child: Text(title.toUpperCase(), style: const TextStyle(
        fontFamily: 'Onest', fontSize: 10, fontWeight: FontWeight.w600,
        color: BColors.textMuted, letterSpacing: 1.2,
      )),
    );
  }
}

// ─── RESULT ROW ───────────────────────────────────────────────

class _ResultRow extends StatefulWidget {
  final SearchResult result;
  final String highlight;
  final bool active;
  final VoidCallback onTap;
  final VoidCallback onHover;

  const _ResultRow({
    required this.result, required this.highlight, required this.active,
    required this.onTap, required this.onHover,
  });

  @override
  State<_ResultRow> createState() => _ResultRowState();
}

class _ResultRowState extends State<_ResultRow> {
  @override
  Widget build(BuildContext context) {
    final r = widget.result;
    final active = widget.active;
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => widget.onHover(),
      child: GestureDetector(
        onTap: widget.onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 100),
          height: 44,
          margin: const EdgeInsets.symmetric(horizontal: 8, vertical: 1),
          padding: const EdgeInsets.symmetric(horizontal: 10),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(8),
            color: active
                ? BColors.accent.withValues(alpha: 0.1)
                : Colors.transparent,
            border: Border.all(
              color: active
                  ? BColors.accent.withValues(alpha: 0.25)
                  : Colors.transparent,
              width: 0.5,
            ),
          ),
          child: Row(children: [
            _Avatar(result: r),
            const SizedBox(width: 10),
            Expanded(child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _HighlightedText(
                  text: r.title,
                  highlight: widget.highlight,
                  style: const TextStyle(
                    fontFamily: 'Onest', fontSize: 13, fontWeight: FontWeight.w500,
                    color: BColors.textPrimary,
                  ),
                ),
                if (_buildSecondaryText(r).isNotEmpty)
                  _HighlightedText(
                    text: _buildSecondaryText(r),
                    highlight: widget.highlight,
                    style: const TextStyle(
                      fontFamily: 'Onest', fontSize: 11,
                      color: BColors.textMuted,
                    ),
                    maxLines: 1,
                  ),
              ],
            )),
            const SizedBox(width: 8),
            _KindLabel(kind: r.kind, active: active),
            if (active) ...[
              const SizedBox(width: 6),
              Icon(SolarIconsOutline.backspace, size: 12,
                  color: BColors.accent.withValues(alpha: 0.8)),
            ],
          ]),
        ),
      ),
    );
  }

  String _buildSecondaryText(SearchResult r) {
    if (r.kind == SearchResultKind.message) {
      return '${r.matchSnippet ?? ''} · ${r.inChat ?? ''}';
    }
    return r.subtitle ?? '';
  }
}

// ─── AVATAR (reused across result kinds) ──────────────────────

class _Avatar extends StatelessWidget {
  final SearchResult result;
  const _Avatar({required this.result});

  @override
  Widget build(BuildContext context) {
    final initial = result.initial ?? result.title.characters.first;
    final tint = result.tint ?? senderColorFor(result.title);
    final isChannel = result.kind == SearchResultKind.channel;
    return Container(
      width: 28, height: 28,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(isChannel ? 6 : 8),
        color: tint.withValues(alpha: 0.14),
        border: Border.all(color: tint.withValues(alpha: 0.22), width: 0.5),
      ),
      child: Center(child: Text(
        initial,
        style: TextStyle(
          fontFamily: 'Onest',
          fontSize: isChannel ? 10 : 11,
          fontWeight: FontWeight.w700,
          color: tint.withValues(alpha: 0.95),
        ),
      )),
    );
  }
}

// ─── HIGHLIGHTED TEXT (matches get accent bg) ─────────────────

class _HighlightedText extends StatelessWidget {
  final String text;
  final String highlight;
  final TextStyle style;
  final int? maxLines;
  const _HighlightedText({
    required this.text, required this.highlight, required this.style,
    this.maxLines,
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
    int cursor = 0;
    while (cursor < text.length) {
      final idx = lt.indexOf(hl, cursor);
      if (idx < 0) {
        spans.add(TextSpan(text: text.substring(cursor)));
        break;
      }
      if (idx > cursor) {
        spans.add(TextSpan(text: text.substring(cursor, idx)));
      }
      spans.add(TextSpan(
        text: text.substring(idx, idx + hl.length),
        style: TextStyle(
          color: BColors.accent, fontWeight: FontWeight.w600,
          backgroundColor: BColors.accent.withValues(alpha: 0.14),
        ),
      ));
      cursor = idx + hl.length;
    }
    return Text.rich(
      TextSpan(style: style, children: spans),
      maxLines: maxLines, overflow: TextOverflow.ellipsis,
    );
  }
}

// ─── KIND LABEL (right side) ──────────────────────────────────

class _KindLabel extends StatelessWidget {
  final SearchResultKind kind;
  final bool active;
  const _KindLabel({required this.kind, required this.active});

  @override
  Widget build(BuildContext context) {
    final label = switch (kind) {
      SearchResultKind.chat => 'чат',
      SearchResultKind.contact => 'контакт',
      SearchResultKind.message => 'сообщ.',
      SearchResultKind.channel => 'канал',
    };
    return Text(label, style: TextStyle(
      fontFamily: 'Onest', fontSize: 10, fontWeight: FontWeight.w500,
      color: active
          ? BColors.accent.withValues(alpha: 0.8)
          : BColors.textMuted.withValues(alpha: 0.7),
      letterSpacing: 0.3,
    ));
  }
}

// ─── FOOTER HINTS ─────────────────────────────────────────────

class _FooterHints extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Container(
      height: 30,
      padding: const EdgeInsets.symmetric(horizontal: 14),
      decoration: BoxDecoration(
        border: Border(top: BorderSide(color: Colors.white.withValues(alpha: 0.05))),
        color: Colors.black.withValues(alpha: 0.25),
      ),
      child: Row(children: [
        _Key(label: '↑↓'), const SizedBox(width: 4),
        const Text('навигация', style: _hintStyle),
        const SizedBox(width: 12),
        _Key(label: 'Enter'), const SizedBox(width: 4),
        const Text('открыть', style: _hintStyle),
        const SizedBox(width: 12),
        _Key(label: 'Esc'), const SizedBox(width: 4),
        const Text('закрыть', style: _hintStyle),
        const Spacer(),
        const Text('ctrl k', style: TextStyle(
          fontFamily: 'Onest', fontSize: 10, color: BColors.textMuted,
          letterSpacing: 1,
        )),
      ]),
    );
  }

  static const _hintStyle = TextStyle(
    fontFamily: 'Onest', fontSize: 10, color: BColors.textMuted,
  );
}

class _Key extends StatelessWidget {
  final String label;
  const _Key({required this.label});
  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 2),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(3),
        color: Colors.white.withValues(alpha: 0.06),
        border: Border.all(color: Colors.white.withValues(alpha: 0.08), width: 0.5),
      ),
      child: Text(label, style: const TextStyle(
        fontFamily: 'Onest', fontSize: 10, fontWeight: FontWeight.w500,
        color: BColors.textSecondary, height: 1.2,
      )),
    );
  }
}
