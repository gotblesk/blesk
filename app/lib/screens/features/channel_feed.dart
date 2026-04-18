import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:solar_icons/solar_icons.dart';

import '../shared/theme.dart';

// ═══════════════════════════════════════════════════════════════
// CHANNEL FEED — editorial timeline with post cards
// ═══════════════════════════════════════════════════════════════

// ─── MODELS ───────────────────────────────────────────────────

class ChannelInfo {
  final String id, name, description, initial;
  final int subscribers;
  final Color tint;
  final bool isPublic;
  const ChannelInfo({
    required this.id, required this.name, required this.description,
    required this.initial, required this.subscribers,
    required this.tint, this.isPublic = true,
  });
}

class ChannelPost {
  final String id, date;
  final String? title, text;
  final List<Color>? media;
  final int views, likes, comments;
  final bool liked;
  const ChannelPost({
    required this.id, required this.date,
    this.title, this.text, this.media,
    this.views = 0, this.likes = 0, this.comments = 0, this.liked = false,
  });

  ChannelPost toggleLike() => ChannelPost(
    id: id, date: date, title: title, text: text, media: media,
    views: views, likes: liked ? likes - 1 : likes + 1,
    comments: comments, liked: !liked,
  );
}

// ─── STUB DATA ────────────────────────────────────────────────

const _channels = <String, ChannelInfo>{
  'ch_news': ChannelInfo(
    id: 'ch_news', name: 'blesk news',
    description: 'обновления и новости blesk',
    initial: 'bl', subscribers: 1247,
    tint: Color(0xFFC8FF00), isPublic: true,
  ),
  'ch_design': ChannelInfo(
    id: 'ch_design', name: 'design daily',
    description: 'дизайн-вдохновение каждый день',
    initial: 'dd', subscribers: 482,
    tint: Color(0xFFFF90D4), isPublic: true,
  ),
  'ch_music': ChannelInfo(
    id: 'ch_music', name: 'soundtrack',
    description: 'любимая музыка редакции',
    initial: 'st', subscribers: 156,
    tint: Color(0xFF9B8BFF), isPublic: false,
  ),
};

final Map<String, List<ChannelPost>> _posts = {
  'ch_news': [
    const ChannelPost(
      id: 'p1', date: '15 апреля',
      title: 'Обновление 0.2.0',
      text: 'Новая версия blesk с поддержкой split view, peek & reply и '
          'переработанными пузырями. Теперь можно открывать до 3-х чатов одновременно.',
      media: [Color(0xFFC8FF00), Color(0xFF5B8FF9)],
      views: 234, likes: 18, comments: 5,
    ),
    const ChannelPost(
      id: 'p2', date: '12 апреля',
      title: 'Голосовые комнаты в бете',
      text: 'Тестовый режим WebRTC + mediasoup открыт для подписчиков blesk+. '
          'До 25 участников, HD-звук, шумоподавление.',
      views: 521, likes: 47, comments: 12,
    ),
    const ChannelPost(
      id: 'p3', date: '8 апреля',
      text: 'Начали работу над маркетплейсом. Каждый сможет продавать пресеты '
          'и темы за монеты. Скоро — больше деталей.',
      views: 312, likes: 24, comments: 7,
    ),
  ],
  'ch_design': [
    const ChannelPost(
      id: 'dp1', date: '16 апреля',
      title: 'Liquid Glass: как собрать слои',
      text: 'Разобрал иерархию слоёв: bg, surface, floating. Главный трюк — '
          'не больше 3 blur слоёв одновременно, иначе лагает.',
      media: [Color(0xFFFF90D4), Color(0xFF8BD4FF), Color(0xFFFFB15C)],
      views: 189, likes: 34, comments: 8, liked: true,
    ),
    const ChannelPost(
      id: 'dp2', date: '14 апреля',
      title: 'Цвета для темной темы',
      text: 'Фон #0A0A0F, текст #E6FFFFFF, muted #40FFFFFF. Акцент — единственный '
          'яркий элемент. Всё остальное — оттенки серого.',
      views: 412, likes: 56, comments: 14,
    ),
  ],
  'ch_music': [
    const ChannelPost(
      id: 'mp1', date: '17 апреля',
      text: 'Dreamy lo-fi для вечернего кодинга. Нажмите play ↓',
      media: [Color(0xFF9B8BFF), Color(0xFF5CFFCB)],
      views: 89, likes: 12, comments: 2,
    ),
  ],
};

ChannelInfo? getChannel(String id) => _channels[id];
bool isChannelId(String id) => id.startsWith('ch_') && _channels.containsKey(id);

// ─── FEED ROOT ────────────────────────────────────────────────

class ChannelFeedPanel extends StatefulWidget {
  final String channelId;
  final bool showClose;
  final VoidCallback? onClose;
  const ChannelFeedPanel({
    super.key, required this.channelId,
    this.showClose = false, this.onClose,
  });
  @override
  State<ChannelFeedPanel> createState() => _ChannelFeedPanelState();
}

class _ChannelFeedPanelState extends State<ChannelFeedPanel> {
  late List<ChannelPost> _postsList;
  bool _notificationsOn = true;

  @override
  void initState() {
    super.initState();
    _postsList = List.of(_posts[widget.channelId] ?? []);
  }

  void _toggleLike(int i) {
    setState(() => _postsList[i] = _postsList[i].toggleLike());
  }

  @override
  Widget build(BuildContext context) {
    final info = getChannel(widget.channelId);
    if (info == null) {
      return const Center(child: Text('канал не найден', style: TextStyle(
        fontFamily: 'Onest', fontSize: 13, color: BColors.textMuted,
      )));
    }
    return Column(children: [
      _ChannelHeader(
        info: info,
        notificationsOn: _notificationsOn,
        onToggleNotifications: () => setState(() => _notificationsOn = !_notificationsOn),
        showClose: widget.showClose,
        onClose: widget.onClose,
      ),
      Expanded(child: _postsList.isEmpty
          ? _EmptyFeed(name: info.name)
          : ListView.separated(
              padding: const EdgeInsets.fromLTRB(24, 16, 24, 24),
              itemCount: _postsList.length,
              separatorBuilder: (_, _) => const SizedBox(height: 12),
              itemBuilder: (_, i) => _PostCard(
                post: _postsList[i],
                channelTint: info.tint,
                onLike: () => _toggleLike(i),
              ).animate(delay: Duration(milliseconds: 40 * i))
                  .fadeIn(duration: 240.ms)
                  .slideY(begin: 0.04, curve: Curves.easeOut),
            )),
    ]);
  }
}

// ─── HEADER ───────────────────────────────────────────────────

class _ChannelHeader extends StatelessWidget {
  final ChannelInfo info;
  final bool notificationsOn;
  final VoidCallback onToggleNotifications;
  final bool showClose;
  final VoidCallback? onClose;
  const _ChannelHeader({
    required this.info, required this.notificationsOn,
    required this.onToggleNotifications,
    required this.showClose, this.onClose,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 60,
      padding: const EdgeInsets.symmetric(horizontal: 18),
      decoration: const BoxDecoration(
        border: Border(bottom: BorderSide(color: BColors.borderLow)),
      ),
      child: Row(children: [
        Container(
          width: 36, height: 36,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(8),
            color: info.tint.withValues(alpha: 0.16),
            border: Border.all(color: info.tint.withValues(alpha: 0.3), width: 0.5),
          ),
          child: Center(child: Text(info.initial, style: TextStyle(
            fontFamily: 'Onest', fontSize: 13, fontWeight: FontWeight.w700,
            color: info.tint.withValues(alpha: 0.95),
          ))),
        ),
        const SizedBox(width: 12),
        Expanded(child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(children: [
              Text(info.name, style: const TextStyle(
                fontFamily: 'Nekst', fontSize: 15, fontWeight: FontWeight.w600,
                color: BColors.textPrimary,
              )),
              const SizedBox(width: 6),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1),
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(3),
                  color: info.tint.withValues(alpha: 0.15),
                ),
                child: Text(info.isPublic ? 'канал' : 'приватный', style: TextStyle(
                  fontFamily: 'Onest', fontSize: 9, fontWeight: FontWeight.w600,
                  color: info.tint.withValues(alpha: 0.9),
                  letterSpacing: 0.4,
                )),
              ),
            ]),
            const SizedBox(height: 1),
            Text('${_fmtSubs(info.subscribers)} подписчиков · ${info.description}',
                maxLines: 1, overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  fontFamily: 'Onest', fontSize: 11, color: BColors.textMuted,
                )),
          ],
        )),
        _HeaderBtn(
          icon: notificationsOn
              ? SolarIconsOutline.bellBing
              : SolarIconsOutline.bellOff,
          tooltip: notificationsOn ? 'уведомления' : 'выключены',
          accent: notificationsOn,
          onTap: onToggleNotifications,
        ),
        const SizedBox(width: 2),
        _HeaderBtn(icon: SolarIconsOutline.link, tooltip: 'ссылка', onTap: () {}),
        const SizedBox(width: 2),
        _HeaderBtn(icon: SolarIconsOutline.menuDots, tooltip: 'меню', onTap: () {}),
        if (showClose) ...[
          const SizedBox(width: 4),
          _HeaderBtn(icon: SolarIconsOutline.closeCircle, tooltip: 'закрыть', onTap: onClose ?? () {}),
        ],
      ]),
    );
  }

  String _fmtSubs(int n) {
    if (n >= 1000000) return '${(n / 1000000).toStringAsFixed(1)}М';
    if (n >= 1000) return '${(n / 1000).toStringAsFixed(1)}к';
    return '$n';
  }
}

class _HeaderBtn extends StatefulWidget {
  final IconData icon;
  final String tooltip;
  final bool accent;
  final VoidCallback onTap;
  const _HeaderBtn({
    required this.icon, required this.tooltip,
    this.accent = false, required this.onTap,
  });
  @override
  State<_HeaderBtn> createState() => _HeaderBtnState();
}

class _HeaderBtnState extends State<_HeaderBtn> {
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
              color: widget.accent
                  ? BColors.accent.withValues(alpha: _h ? 0.18 : 0.1)
                  : _h ? Colors.white.withValues(alpha: 0.06) : Colors.transparent,
            ),
            child: Icon(widget.icon, size: 16,
                color: widget.accent
                    ? BColors.accent.withValues(alpha: 0.95)
                    : _h ? BColors.textPrimary : BColors.textSecondary),
          ),
        ),
      ),
    );
  }
}

// ─── POST CARD ────────────────────────────────────────────────

class _PostCard extends StatefulWidget {
  final ChannelPost post;
  final Color channelTint;
  final VoidCallback onLike;
  const _PostCard({
    required this.post, required this.channelTint, required this.onLike,
  });
  @override
  State<_PostCard> createState() => _PostCardState();
}

class _PostCardState extends State<_PostCard> {
  bool _h = false;
  @override
  Widget build(BuildContext context) {
    final p = widget.post;
    return MouseRegion(
      onEnter: (_) => setState(() => _h = true),
      onExit: (_) => setState(() => _h = false),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(16),
          color: _h
              ? Colors.white.withValues(alpha: 0.045)
              : Colors.white.withValues(alpha: 0.03),
          border: Border.all(color: Colors.white.withValues(alpha: 0.05), width: 0.5),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            if (p.title != null) Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Text(p.title!, style: const TextStyle(
                fontFamily: 'Nekst', fontSize: 17, fontWeight: FontWeight.w600,
                color: BColors.textPrimary, height: 1.3,
              )),
            ),
            if (p.text != null && p.text!.isNotEmpty) Text(
              p.text!,
              style: const TextStyle(
                fontFamily: 'Onest', fontSize: 14, color: BColors.textPrimary,
                height: 1.55,
              ),
            ),
            if (p.media != null && p.media!.isNotEmpty) Padding(
              padding: EdgeInsets.only(top: (p.title != null || p.text != null) ? 14 : 0),
              child: _PostMedia(tints: p.media!),
            ),
            const SizedBox(height: 14),
            _PostFooter(
              post: p, channelTint: widget.channelTint,
              onLike: widget.onLike,
            ),
          ],
        ),
      ),
    );
  }
}

class _PostMedia extends StatelessWidget {
  final List<Color> tints;
  const _PostMedia({required this.tints});

  @override
  Widget build(BuildContext context) {
    if (tints.length == 1) {
      return ClipRRect(
        borderRadius: BorderRadius.circular(10),
        child: _MediaStub(tint: tints[0], height: 220),
      );
    }
    return ClipRRect(
      borderRadius: BorderRadius.circular(10),
      child: SizedBox(height: 200, child: Row(children: [
        for (var i = 0; i < tints.length; i++) ...[
          if (i > 0) const SizedBox(width: 2),
          Expanded(child: _MediaStub(tint: tints[i], height: 200)),
        ],
      ])),
    );
  }
}

class _MediaStub extends StatelessWidget {
  final Color tint;
  final double height;
  const _MediaStub({required this.tint, required this.height});

  @override
  Widget build(BuildContext context) {
    return Container(
      height: height,
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft, end: Alignment.bottomRight,
          colors: [
            tint.withValues(alpha: 0.8),
            tint.withValues(alpha: 0.4),
            Colors.black.withValues(alpha: 0.2),
          ],
        ),
      ),
      child: Center(child: Icon(
        SolarIconsOutline.gallery, size: 32, color: Colors.white.withValues(alpha: 0.35),
      )),
    );
  }
}

class _PostFooter extends StatelessWidget {
  final ChannelPost post;
  final Color channelTint;
  final VoidCallback onLike;
  const _PostFooter({
    required this.post, required this.channelTint, required this.onLike,
  });

  @override
  Widget build(BuildContext context) {
    return Row(children: [
      Text(post.date, style: TextStyle(
        fontFamily: 'Onest', fontSize: 11,
        color: BColors.textMuted.withValues(alpha: 0.9),
      )),
      const SizedBox(width: 10),
      _FooterStat(icon: SolarIconsOutline.eye, count: post.views),
      const SizedBox(width: 10),
      _FooterLikeBtn(liked: post.liked, count: post.likes,
          tint: channelTint, onTap: onLike),
      const SizedBox(width: 10),
      _FooterStat(icon: SolarIconsOutline.chatRound, count: post.comments),
      const Spacer(),
      _ShareBtn(),
    ]);
  }
}

class _FooterStat extends StatelessWidget {
  final IconData icon;
  final int count;
  const _FooterStat({required this.icon, required this.count});
  @override
  Widget build(BuildContext context) {
    return Row(mainAxisSize: MainAxisSize.min, children: [
      Icon(icon, size: 12, color: BColors.textMuted),
      const SizedBox(width: 4),
      Text('$count', style: const TextStyle(
        fontFamily: 'Onest', fontSize: 11, color: BColors.textMuted,
      )),
    ]);
  }
}

class _FooterLikeBtn extends StatefulWidget {
  final bool liked;
  final int count;
  final Color tint;
  final VoidCallback onTap;
  const _FooterLikeBtn({
    required this.liked, required this.count, required this.tint, required this.onTap,
  });
  @override
  State<_FooterLikeBtn> createState() => _FooterLikeBtnState();
}

class _FooterLikeBtnState extends State<_FooterLikeBtn> {
  bool _h = false;
  @override
  Widget build(BuildContext context) {
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setState(() => _h = true),
      onExit: (_) => setState(() => _h = false),
      child: GestureDetector(
        onTap: widget.onTap,
        child: Row(mainAxisSize: MainAxisSize.min, children: [
          AnimatedScale(
            duration: const Duration(milliseconds: 140),
            scale: widget.liked ? 1.15 : 1.0,
            child: Icon(
              widget.liked ? SolarIconsBold.heart : SolarIconsOutline.heart,
              size: 13,
              color: widget.liked
                  ? const Color(0xFFff5c7c)
                  : (_h ? BColors.textSecondary : BColors.textMuted),
            ),
          ),
          const SizedBox(width: 4),
          Text('${widget.count}', style: TextStyle(
            fontFamily: 'Onest', fontSize: 11,
            color: widget.liked
                ? const Color(0xFFff5c7c).withValues(alpha: 0.95)
                : (_h ? BColors.textSecondary : BColors.textMuted),
          )),
        ]),
      ),
    );
  }
}

class _ShareBtn extends StatefulWidget {
  @override
  State<_ShareBtn> createState() => _ShareBtnState();
}

class _ShareBtnState extends State<_ShareBtn> {
  bool _h = false;
  @override
  Widget build(BuildContext context) {
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setState(() => _h = true),
      onExit: (_) => setState(() => _h = false),
      child: GestureDetector(
        onTap: () {},
        child: Icon(SolarIconsOutline.shareCircle, size: 13,
            color: _h ? BColors.textSecondary : BColors.textMuted),
      ),
    );
  }
}

// ─── EMPTY STATE ──────────────────────────────────────────────

class _EmptyFeed extends StatelessWidget {
  final String name;
  const _EmptyFeed({required this.name});
  @override
  Widget build(BuildContext context) {
    return Center(child: Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(SolarIconsBroken.podcast, size: 40,
            color: BColors.textMuted.withValues(alpha: 0.6)),
        const SizedBox(height: 12),
        Text('в $name пока нет постов', style: const TextStyle(
          fontFamily: 'Onest', fontSize: 14, fontWeight: FontWeight.w500,
          color: BColors.textSecondary,
        )),
        const SizedBox(height: 4),
        const Text('как только автор опубликует — увидите тут',
          style: TextStyle(fontFamily: 'Onest', fontSize: 12, color: BColors.textMuted),
        ),
      ],
    ));
  }
}
