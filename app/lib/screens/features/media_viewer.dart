import 'dart:math' as math;
import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_animate/flutter_animate.dart';

import '../shared/theme.dart';

// ═══════════════════════════════════════════════════════════════
// MEDIA VIEWER — fullscreen gallery with zoom, pan, navigation
// ═══════════════════════════════════════════════════════════════

/// Single media item. Image is represented as a gradient tint (stub).
class MediaItem {
  final String id;
  final Color tint; // stub "image" — gradient background
  final bool isVideo;
  final Duration? videoDuration;
  final String? caption;
  const MediaItem({
    required this.id, required this.tint,
    this.isVideo = false, this.videoDuration, this.caption,
  });
}

/// Shows a fullscreen media viewer over the current route.
/// Returns a close callback — call it if you need to dismiss manually.
OverlayEntry showMediaViewer(
  BuildContext context, {
  required List<MediaItem> items,
  required int startIndex,
  required String senderName,
  required String dateText,
  VoidCallback? onDownload,
  VoidCallback? onShare,
  VoidCallback? onDelete,
}) {
  final overlay = Overlay.of(context);
  late OverlayEntry entry;
  entry = OverlayEntry(builder: (ctx) => MediaViewer(
    items: items,
    startIndex: startIndex,
    senderName: senderName,
    dateText: dateText,
    onClose: () => entry.remove(),
    onDownload: onDownload,
    onShare: onShare,
    onDelete: onDelete,
  ));
  overlay.insert(entry);
  return entry;
}

// ─── VIEWER ROOT ──────────────────────────────────────────────

class MediaViewer extends StatefulWidget {
  final List<MediaItem> items;
  final int startIndex;
  final String senderName, dateText;
  final VoidCallback onClose;
  final VoidCallback? onDownload;
  final VoidCallback? onShare;
  final VoidCallback? onDelete;

  const MediaViewer({
    super.key,
    required this.items,
    required this.startIndex,
    required this.senderName,
    required this.dateText,
    required this.onClose,
    this.onDownload,
    this.onShare,
    this.onDelete,
  });

  @override
  State<MediaViewer> createState() => _MediaViewerState();
}

class _MediaViewerState extends State<MediaViewer>
    with SingleTickerProviderStateMixin {
  late int _index;
  late final PageController _page;
  final _focus = FocusNode();
  bool _chromeVisible = true;

  @override
  void initState() {
    super.initState();
    _index = widget.startIndex;
    _page = PageController(initialPage: _index);
    _focus.requestFocus();
    _scheduleAutoHide();
  }

  @override
  void dispose() {
    _page.dispose();
    _focus.dispose();
    super.dispose();
  }

  DateTime _lastMouseMove = DateTime.now();
  void _scheduleAutoHide() {
    Future.delayed(const Duration(seconds: 3), () {
      if (!mounted) return;
      final sinceMove = DateTime.now().difference(_lastMouseMove);
      if (sinceMove.inMilliseconds >= 2800 && _chromeVisible) {
        setState(() => _chromeVisible = false);
      }
    });
  }

  void _onMouseMove(PointerEvent e) {
    _lastMouseMove = DateTime.now();
    if (!_chromeVisible) setState(() => _chromeVisible = true);
    _scheduleAutoHide();
  }

  void _prev() {
    if (_index > 0) {
      _page.animateToPage(_index - 1,
          duration: const Duration(milliseconds: 250), curve: Curves.easeOut);
    }
  }

  void _next() {
    if (_index < widget.items.length - 1) {
      _page.animateToPage(_index + 1,
          duration: const Duration(milliseconds: 250), curve: Curves.easeOut);
    }
  }

  void _onKey(KeyEvent e) {
    if (e is! KeyDownEvent) return;
    final k = e.logicalKey;
    if (k == LogicalKeyboardKey.escape) {
      widget.onClose();
    } else if (k == LogicalKeyboardKey.arrowLeft) {
      _prev();
    } else if (k == LogicalKeyboardKey.arrowRight) {
      _next();
    }
  }

  @override
  Widget build(BuildContext context) {
    final screen = MediaQuery.of(context).size;
    final isAtStart = _index == 0;
    final isAtEnd = _index == widget.items.length - 1;

    return Material(
      color: Colors.transparent,
      child: KeyboardListener(
        focusNode: _focus,
        onKeyEvent: _onKey,
        child: MouseRegion(
          onHover: _onMouseMove,
          child: Stack(children: [
            // Blurred black background
            Positioned.fill(child: _Backdrop(onTap: widget.onClose)),

            // PageView with zoomable images
            Positioned.fill(child: PageView.builder(
              controller: _page,
              physics: const ClampingScrollPhysics(),
              itemCount: widget.items.length,
              onPageChanged: (i) => setState(() => _index = i),
              itemBuilder: (_, i) => _MediaPage(
                item: widget.items[i],
                isActive: i == _index,
              ),
            )),

            // Left navigation arrow
            if (!isAtStart && _chromeVisible)
              Positioned(
                left: 16, top: 0, bottom: 0,
                child: Center(child: _NavArrow(
                  icon: Icons.arrow_back_ios_new_rounded,
                  onTap: _prev,
                )),
              ),

            // Right navigation arrow
            if (!isAtEnd && _chromeVisible)
              Positioned(
                right: 16, top: 0, bottom: 0,
                child: Center(child: _NavArrow(
                  icon: Icons.arrow_forward_ios_rounded,
                  onTap: _next,
                )),
              ),

            // Top bar
            Positioned(
              top: 0, left: 0, right: 0,
              child: AnimatedSlide(
                duration: const Duration(milliseconds: 200),
                offset: Offset(0, _chromeVisible ? 0 : -1.2),
                child: AnimatedOpacity(
                  duration: const Duration(milliseconds: 200),
                  opacity: _chromeVisible ? 1 : 0,
                  child: _TopBar(
                    senderName: widget.senderName,
                    dateText: widget.dateText,
                    counter: widget.items.length > 1
                        ? '${_index + 1} / ${widget.items.length}' : null,
                    onClose: widget.onClose,
                    onDownload: widget.onDownload,
                    onShare: widget.onShare,
                    onDelete: widget.onDelete,
                  ),
                ),
              ),
            ),

            // Caption + thumbnail strip
            Positioned(
              bottom: 0, left: 0, right: 0,
              child: AnimatedSlide(
                duration: const Duration(milliseconds: 200),
                offset: Offset(0, _chromeVisible ? 0 : 1.2),
                child: AnimatedOpacity(
                  duration: const Duration(milliseconds: 200),
                  opacity: _chromeVisible ? 1 : 0,
                  child: Column(mainAxisSize: MainAxisSize.min, children: [
                    if (widget.items[_index].caption != null &&
                        widget.items[_index].caption!.isNotEmpty)
                      _Caption(text: widget.items[_index].caption!),
                    if (widget.items.length > 1) _ThumbStrip(
                      items: widget.items,
                      active: _index,
                      onTap: (i) => _page.animateToPage(i,
                          duration: const Duration(milliseconds: 200),
                          curve: Curves.easeOut),
                      screenWidth: screen.width,
                    ),
                  ]),
                ),
              ),
            ),
          ]),
        ),
      ),
    ).animate().fadeIn(duration: 220.ms, curve: Curves.easeOut);
  }
}

// ─── BACKDROP ─────────────────────────────────────────────────

class _Backdrop extends StatelessWidget {
  final VoidCallback onTap;
  const _Backdrop({required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      behavior: HitTestBehavior.opaque,
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 40, sigmaY: 40),
        child: Container(
          color: Colors.black.withValues(alpha: 0.96),
        ),
      ),
    );
  }
}

// ─── SINGLE PAGE (zoomable image or video) ────────────────────

class _MediaPage extends StatefulWidget {
  final MediaItem item;
  final bool isActive;
  const _MediaPage({required this.item, required this.isActive});
  @override
  State<_MediaPage> createState() => _MediaPageState();
}

class _MediaPageState extends State<_MediaPage> {
  late final TransformationController _tf;
  bool _zoomed = false;

  @override
  void initState() {
    super.initState();
    _tf = TransformationController();
  }

  @override
  void dispose() {
    _tf.dispose();
    super.dispose();
  }

  void _doubleTap(TapDownDetails d, Size viewport) {
    if (_zoomed) {
      _tf.value = Matrix4.identity();
      setState(() => _zoomed = false);
    } else {
      // Zoom to 2x at tap point
      final target = 2.0;
      final dx = -d.localPosition.dx * (target - 1);
      final dy = -d.localPosition.dy * (target - 1);
      _tf.value = Matrix4.identity()
        ..translateByDouble(dx, dy, 0, 1)
        ..scaleByDouble(target, target, target, 1);
      setState(() => _zoomed = true);
    }
  }

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(builder: (ctx, cons) {
      final size = Size(cons.maxWidth, cons.maxHeight);
      return GestureDetector(
        onDoubleTapDown: (d) => _doubleTap(d, size),
        onDoubleTap: () {}, // required so onDoubleTapDown fires
        child: InteractiveViewer(
          transformationController: _tf,
          minScale: 1.0,
          maxScale: 5.0,
          panEnabled: _zoomed,
          scaleEnabled: true,
          onInteractionEnd: (_) {
            final s = _tf.value.getMaxScaleOnAxis();
            setState(() => _zoomed = s > 1.05);
          },
          child: Center(child: widget.item.isVideo
              ? _VideoStub(item: widget.item)
              : _ImageStub(item: widget.item)),
        ),
      );
    });
  }
}

// ─── IMAGE STUB (gradient-based) ──────────────────────────────

class _ImageStub extends StatelessWidget {
  final MediaItem item;
  const _ImageStub({required this.item});

  @override
  Widget build(BuildContext context) {
    return AspectRatio(
      aspectRatio: 3 / 2,
      child: Container(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft, end: Alignment.bottomRight,
            colors: [
              item.tint.withValues(alpha: 0.85),
              item.tint.withValues(alpha: 0.5),
              Colors.black.withValues(alpha: 0.3),
            ],
          ),
        ),
        child: Stack(children: [
          Positioned.fill(child: Opacity(
            opacity: 0.15,
            child: CustomPaint(painter: _NoisePainter(item.tint)),
          )),
          Center(child: Icon(
            Icons.image_outlined,
            size: 80, color: Colors.white.withValues(alpha: 0.4),
          )),
        ]),
      ),
    );
  }
}

class _NoisePainter extends CustomPainter {
  final Color tint;
  _NoisePainter(this.tint);
  @override
  void paint(Canvas canvas, Size size) {
    final rnd = math.Random(tint.toARGB32());
    final p = Paint()..color = Colors.white.withValues(alpha: 0.2);
    for (var i = 0; i < 400; i++) {
      canvas.drawCircle(
        Offset(rnd.nextDouble() * size.width, rnd.nextDouble() * size.height),
        0.6, p,
      );
    }
  }
  @override bool shouldRepaint(covariant _NoisePainter o) => false;
}

// ─── VIDEO STUB ───────────────────────────────────────────────

class _VideoStub extends StatefulWidget {
  final MediaItem item;
  const _VideoStub({required this.item});
  @override
  State<_VideoStub> createState() => _VideoStubState();
}

class _VideoStubState extends State<_VideoStub>
    with SingleTickerProviderStateMixin {
  bool _playing = false;
  bool _controlsVisible = true;
  late final AnimationController _ctrl;
  double _progress = 0;

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(
      vsync: this,
      duration: widget.item.videoDuration ?? const Duration(seconds: 30),
    )..addListener(() {
      setState(() => _progress = _ctrl.value);
      if (_ctrl.isCompleted) {
        setState(() { _playing = false; });
      }
    });
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  void _toggle() {
    setState(() {
      _playing = !_playing;
      if (_playing) {
        _ctrl.forward();
      } else {
        _ctrl.stop();
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: () => setState(() => _controlsVisible = !_controlsVisible),
      child: Stack(children: [
        _ImageStub(item: widget.item),
        if (_controlsVisible || !_playing) Positioned.fill(child: Center(
          child: GestureDetector(
            onTap: _toggle,
            child: Container(
              width: 72, height: 72,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: Colors.black.withValues(alpha: 0.5),
                border: Border.all(color: Colors.white.withValues(alpha: 0.35)),
              ),
              child: Icon(
                _playing ? Icons.pause : Icons.play_arrow_rounded,
                size: 44, color: Colors.white,
              ),
            ),
          ),
        )),
        if (_controlsVisible) Positioned(
          left: 16, right: 16, bottom: 16,
          child: _VideoProgress(
            progress: _progress,
            duration: widget.item.videoDuration ?? const Duration(seconds: 30),
            onSeek: (f) {
              setState(() {
                _progress = f;
                _ctrl.value = f;
              });
            },
          ),
        ),
      ]),
    );
  }
}

class _VideoProgress extends StatelessWidget {
  final double progress;
  final Duration duration;
  final ValueChanged<double> onSeek;
  const _VideoProgress({
    required this.progress, required this.duration, required this.onSeek,
  });

  @override
  Widget build(BuildContext context) {
    return Row(children: [
      Text(_fmt(duration * progress), style: const TextStyle(
        fontFamily: 'Onest', fontSize: 11, color: Colors.white,
      )),
      const SizedBox(width: 10),
      Expanded(child: LayoutBuilder(builder: (ctx, cons) {
        return GestureDetector(
          onTapDown: (d) => onSeek(
              (d.localPosition.dx / cons.maxWidth).clamp(0.0, 1.0)),
          onPanUpdate: (d) => onSeek(
              (d.localPosition.dx / cons.maxWidth).clamp(0.0, 1.0)),
          child: Container(
            height: 24,
            alignment: Alignment.center,
            child: Stack(children: [
              Container(
                height: 3,
                decoration: BoxDecoration(
                  color: Colors.white.withValues(alpha: 0.25),
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
              FractionallySizedBox(
                widthFactor: progress,
                child: Container(
                  height: 3,
                  decoration: BoxDecoration(
                    color: BColors.accent,
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
              Positioned(
                left: (progress * cons.maxWidth).clamp(0.0, cons.maxWidth - 10),
                top: 8,
                child: Container(
                  width: 10, height: 10,
                  decoration: const BoxDecoration(
                    color: Colors.white, shape: BoxShape.circle,
                  ),
                ),
              ),
            ]),
          ),
        );
      })),
      const SizedBox(width: 10),
      Text(_fmt(duration), style: const TextStyle(
        fontFamily: 'Onest', fontSize: 11, color: Colors.white,
      )),
    ]);
  }

  String _fmt(Duration d) {
    final m = d.inMinutes;
    final s = d.inSeconds.remainder(60);
    return '$m:${s.toString().padLeft(2, '0')}';
  }
}

// ─── TOP BAR ──────────────────────────────────────────────────

class _TopBar extends StatelessWidget {
  final String senderName, dateText;
  final String? counter;
  final VoidCallback onClose;
  final VoidCallback? onDownload;
  final VoidCallback? onShare;
  final VoidCallback? onDelete;
  const _TopBar({
    required this.senderName, required this.dateText,
    this.counter, required this.onClose,
    this.onDownload, this.onShare, this.onDelete,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 56,
      padding: const EdgeInsets.symmetric(horizontal: 12),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topCenter, end: Alignment.bottomCenter,
          colors: [
            Colors.black.withValues(alpha: 0.6),
            Colors.black.withValues(alpha: 0),
          ],
        ),
      ),
      child: Row(children: [
        _TopBtn(icon: Icons.close, onTap: onClose, tooltip: 'закрыть (esc)'),
        const SizedBox(width: 4),
        Expanded(child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(children: [
              Text(senderName, style: const TextStyle(
                fontFamily: 'Onest', fontSize: 13, fontWeight: FontWeight.w600,
                color: Colors.white,
              )),
              if (counter != null) ...[
                const SizedBox(width: 10),
                Text(counter!, style: TextStyle(
                  fontFamily: 'Onest', fontSize: 11,
                  color: Colors.white.withValues(alpha: 0.55),
                )),
              ],
            ]),
            const SizedBox(height: 1),
            Text(dateText, style: TextStyle(
              fontFamily: 'Onest', fontSize: 11,
              color: Colors.white.withValues(alpha: 0.6),
            )),
          ],
        )),
        if (onDownload != null)
          _TopBtn(icon: Icons.file_download_outlined, onTap: onDownload!,
              tooltip: 'скачать'),
        if (onShare != null)
          _TopBtn(icon: Icons.share_outlined, onTap: onShare!, tooltip: 'поделиться'),
        if (onDelete != null)
          _TopBtn(icon: Icons.delete_outline, onTap: onDelete!, tooltip: 'удалить'),
      ]),
    );
  }
}

class _TopBtn extends StatefulWidget {
  final IconData icon;
  final String tooltip;
  final VoidCallback onTap;
  const _TopBtn({required this.icon, required this.tooltip, required this.onTap});
  @override
  State<_TopBtn> createState() => _TopBtnState();
}

class _TopBtnState extends State<_TopBtn> {
  bool _h = false;
  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: widget.tooltip,
      textStyle: const TextStyle(fontSize: 11, color: Colors.white),
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
            width: 36, height: 36,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(8),
              color: _h ? Colors.white.withValues(alpha: 0.12) : Colors.transparent,
            ),
            child: Icon(widget.icon, size: 18,
                color: Colors.white.withValues(alpha: _h ? 1 : 0.7)),
          ),
        ),
      ),
    );
  }
}

// ─── NAV ARROW ────────────────────────────────────────────────

class _NavArrow extends StatefulWidget {
  final IconData icon;
  final VoidCallback onTap;
  const _NavArrow({required this.icon, required this.onTap});
  @override
  State<_NavArrow> createState() => _NavArrowState();
}

class _NavArrowState extends State<_NavArrow> {
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
          width: 48, height: 48,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: Colors.white.withValues(alpha: _h ? 0.14 : 0.06),
            border: Border.all(color: Colors.white.withValues(alpha: 0.12), width: 0.5),
          ),
          child: Icon(widget.icon, size: 18, color: Colors.white),
        ),
      ),
    );
  }
}

// ─── CAPTION ──────────────────────────────────────────────────

class _Caption extends StatelessWidget {
  final String text;
  const _Caption({required this.text});
  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(24, 16, 24, 10),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.bottomCenter, end: Alignment.topCenter,
          colors: [
            Colors.black.withValues(alpha: 0.55),
            Colors.black.withValues(alpha: 0),
          ],
        ),
      ),
      child: Text(text, textAlign: TextAlign.center,
        style: TextStyle(
          fontFamily: 'Onest', fontSize: 13, fontWeight: FontWeight.w400,
          color: Colors.white.withValues(alpha: 0.92), height: 1.4,
        ),
      ),
    );
  }
}

// ─── THUMBNAIL STRIP ──────────────────────────────────────────

class _ThumbStrip extends StatelessWidget {
  final List<MediaItem> items;
  final int active;
  final ValueChanged<int> onTap;
  final double screenWidth;
  const _ThumbStrip({
    required this.items, required this.active,
    required this.onTap, required this.screenWidth,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 72,
      padding: const EdgeInsets.symmetric(vertical: 12),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.bottomCenter, end: Alignment.topCenter,
          colors: [
            Colors.black.withValues(alpha: 0.6),
            Colors.black.withValues(alpha: 0),
          ],
        ),
      ),
      child: Center(child: ListView.separated(
        padding: const EdgeInsets.symmetric(horizontal: 12),
        scrollDirection: Axis.horizontal,
        itemCount: items.length,
        shrinkWrap: true,
        separatorBuilder: (_, _) => const SizedBox(width: 4),
        itemBuilder: (_, i) => _Thumb(
          item: items[i], active: i == active,
          onTap: () => onTap(i),
        ),
      )),
    );
  }
}

class _Thumb extends StatefulWidget {
  final MediaItem item;
  final bool active;
  final VoidCallback onTap;
  const _Thumb({required this.item, required this.active, required this.onTap});
  @override
  State<_Thumb> createState() => _ThumbState();
}

class _ThumbState extends State<_Thumb> {
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
          width: 48, height: 48,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(6),
            border: Border.all(
              color: widget.active
                  ? BColors.accent
                  : _h
                      ? Colors.white.withValues(alpha: 0.4)
                      : Colors.transparent,
              width: widget.active ? 2 : 1,
            ),
          ),
          child: ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: Stack(children: [
              Container(decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topLeft, end: Alignment.bottomRight,
                  colors: [
                    widget.item.tint.withValues(alpha: 0.75),
                    widget.item.tint.withValues(alpha: 0.3),
                  ],
                ),
              )),
              if (widget.item.isVideo) const Center(child: Icon(
                Icons.play_arrow_rounded, size: 18, color: Colors.white,
              )),
            ]),
          ),
        ),
      ),
    );
  }
}
