import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:solar_icons/solar_icons.dart';

import '../shared/theme.dart';

// ═══════════════════════════════════════════════════════════════
// CALL VIEW (replaces content panel during call)
// ═══════════════════════════════════════════════════════════════

class CallView extends StatefulWidget {
  final String name;
  final String initial;
  final bool video;
  final VoidCallback onEnd;
  final VoidCallback onMinimize;
  const CallView({super.key, required this.name, required this.initial,
    this.video = false, required this.onEnd, required this.onMinimize});
  @override
  State<CallView> createState() => _CallViewState();
}

class _CallViewState extends State<CallView> {
  bool _muted = false;
  bool _videoOn = false;
  bool _screenShare = false;
  int _seconds = 0;
  bool _connected = false;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    _videoOn = widget.video;
    // Simulate connection after 2s
    Future.delayed(const Duration(seconds: 2), () {
      if (mounted) setState(() => _connected = true);
      _timer = Timer.periodic(const Duration(seconds: 1), (_) {
        if (mounted) setState(() => _seconds++);
      });
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  String get _timeStr {
    final m = (_seconds ~/ 60).toString().padLeft(2, '0');
    final s = (_seconds % 60).toString().padLeft(2, '0');
    return '$m:$s';
  }

  String get _status => _connected ? _timeStr : 'соединение...';

  @override
  Widget build(BuildContext context) {
    return Container(
      color: BColors.bg,
      child: Column(children: [
        // Minimize button top-right
        Align(
          alignment: Alignment.topRight,
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: _CallIconBtn(icon: SolarIconsOutline.minimizeSquare, tooltip: 'свернуть',
              onTap: widget.onMinimize),
          ),
        ),
        const Spacer(),
        // Avatar
        Container(
          width: 160, height: 160,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(40),
            gradient: LinearGradient(
              begin: Alignment.topLeft, end: Alignment.bottomRight,
              colors: [BColors.accent.withValues(alpha: 0.15), BColors.accent.withValues(alpha: 0.05)],
            ),
          ),
          child: Center(child: Text(widget.initial, style: const TextStyle(
            fontFamily: 'Nekst', fontSize: 64, fontWeight: FontWeight.w700, color: BColors.accent,
          ))),
        ).animate().scale(begin: const Offset(0.8, 0.8), duration: 400.ms, curve: Curves.easeOutCubic)
            .then().animate(onPlay: (c) => _connected ? null : c.repeat(reverse: true))
            .scale(begin: const Offset(1.0, 1.0), end: const Offset(1.04, 1.04), duration: 1200.ms, curve: Curves.easeInOut),
        const SizedBox(height: 24),
        // Name
        Text(widget.name, style: TextStyle(fontFamily: 'Nekst',
          fontSize: rf(context, 24), fontWeight: FontWeight.w600, color: BColors.textPrimary,
        )).animate(delay: 200.ms).fadeIn(duration: 300.ms),
        const SizedBox(height: 8),
        // Status
        AnimatedSwitcher(
          duration: const Duration(milliseconds: 200),
          child: Text(_status, key: ValueKey(_status), style: TextStyle(fontFamily: 'Onest',
            fontSize: rf(context, 14), color: BColors.textSecondary)),
        ),
        const Spacer(),
        // Controls bar
        Container(
          height: 80,
          padding: const EdgeInsets.symmetric(horizontal: 32),
          decoration: BoxDecoration(
            border: Border(top: BorderSide(color: BColors.borderLow)),
          ),
          child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [
            _CallToggleBtn(icon: SolarIconsOutline.muted, activeIcon: SolarIconsBold.microphone, label: 'мьют',
              active: _muted, onTap: () => setState(() => _muted = !_muted)),
            const SizedBox(width: 16),
            _CallToggleBtn(icon: SolarIconsOutline.videocameraRecord, activeIcon: SolarIconsBold.videocamera, label: 'видео',
              active: _videoOn, onTap: () => setState(() => _videoOn = !_videoOn)),
            const SizedBox(width: 16),
            _CallToggleBtn(icon: SolarIconsOutline.monitor, activeIcon: SolarIconsBold.monitor, label: 'экран',
              active: _screenShare, onTap: () => setState(() => _screenShare = !_screenShare)),
            const SizedBox(width: 16),
            _CallToggleBtn(icon: SolarIconsOutline.chatRound, activeIcon: SolarIconsBold.chatRound, label: 'чат',
              active: false, onTap: () {}),
            const SizedBox(width: 32),
            // End call
            _EndCallBtn(onTap: widget.onEnd),
          ]),
        ).animate(delay: 300.ms).fadeIn(duration: 300.ms).slideY(begin: 0.2),
      ]),
    );
  }
}

class _CallToggleBtn extends StatefulWidget {
  final IconData icon, activeIcon;
  final String label;
  final bool active;
  final VoidCallback onTap;
  const _CallToggleBtn({required this.icon, required this.activeIcon,
    required this.label, required this.active, required this.onTap});
  @override
  State<_CallToggleBtn> createState() => _CallToggleBtnState();
}

class _CallToggleBtnState extends State<_CallToggleBtn> {
  bool _h = false;
  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: widget.label,
      textStyle: const TextStyle(fontSize: 10, color: BColors.textPrimary),
      decoration: BoxDecoration(color: const Color(0xFF1a1a1a), borderRadius: BorderRadius.circular(6)),
      child: MouseRegion(
        cursor: SystemMouseCursors.click,
        onEnter: (_) => setState(() => _h = true),
        onExit: (_) => setState(() => _h = false),
        child: GestureDetector(
          onTap: widget.onTap,
          child: AnimatedScale(
            scale: _h ? 1.08 : 1.0,
            duration: const Duration(milliseconds: 150),
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 200),
              width: 48, height: 48,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: Colors.white.withValues(alpha: 0.06),
                border: widget.active ? Border.all(color: BColors.accent, width: 1.5) : null,
              ),
              child: Center(child: Icon(
                widget.active ? widget.activeIcon : widget.icon,
                size: 20,
                color: widget.active ? BColors.accent : Colors.white.withValues(alpha: 0.3),
              )),
            ),
          ),
        ),
      ),
    );
  }
}

class _EndCallBtn extends StatefulWidget {
  final VoidCallback onTap;
  const _EndCallBtn({required this.onTap});
  @override
  State<_EndCallBtn> createState() => _EndCallBtnState();
}

class _EndCallBtnState extends State<_EndCallBtn> {
  bool _h = false;
  @override
  Widget build(BuildContext context) {
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setState(() => _h = true),
      onExit: (_) => setState(() => _h = false),
      child: GestureDetector(
        onTap: widget.onTap,
        child: AnimatedScale(
          scale: _h ? 1.08 : 1.0,
          duration: const Duration(milliseconds: 150),
          child: Container(
            width: 56, height: 48,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(24),
              color: const Color(0xFFef4444),
            ),
            child: Center(child: Transform.rotate(
              angle: 2.356, // 135° — visual hangup indicator
              child: const Icon(SolarIconsBold.phone, size: 22, color: Colors.white),
            )),
          ),
        ),
      ),
    );
  }
}

class _CallIconBtn extends StatefulWidget {
  final IconData icon;
  final String tooltip;
  final VoidCallback onTap;
  const _CallIconBtn({required this.icon, required this.tooltip, required this.onTap});
  @override
  State<_CallIconBtn> createState() => _CallIconBtnState();
}

class _CallIconBtnState extends State<_CallIconBtn> {
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
          child: Icon(widget.icon, size: 20, color: _h ? BColors.textSecondary : BColors.textMuted),
        ),
      ),
    );
  }
}

// ═══════════════════════════════════════════════════════════════
// INCOMING CALL OVERLAY
// ═══════════════════════════════════════════════════════════════

class IncomingCallOverlay extends StatelessWidget {
  final String name;
  final String initial;
  final VoidCallback onAccept;
  final VoidCallback onDecline;
  const IncomingCallOverlay({super.key, required this.name, required this.initial,
    required this.onAccept, required this.onDecline});

  @override
  Widget build(BuildContext context) {
    return Container(
      color: Colors.black.withValues(alpha: 0.5),
      child: Center(
        child: Container(
          width: 360, height: 220,
          decoration: BoxDecoration(
            color: const Color(0xFB0e0e12),
            borderRadius: BorderRadius.circular(24),
            border: Border.all(color: BColors.accent.withValues(alpha: 0.08)),
            boxShadow: [
              BoxShadow(color: Colors.black.withValues(alpha: 0.6), blurRadius: 80, offset: const Offset(0, 24)),
              BoxShadow(color: BColors.accent.withValues(alpha: 0.06), blurRadius: 40),
            ],
          ),
          child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
            // Pulsing avatar
            Container(
              width: 72, height: 72,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(20),
                gradient: LinearGradient(
                  begin: Alignment.topLeft, end: Alignment.bottomRight,
                  colors: [BColors.accent.withValues(alpha: 0.15), BColors.accent.withValues(alpha: 0.06)],
                ),
              ),
              child: Center(child: Text(initial, style: const TextStyle(
                fontFamily: 'Nekst', fontSize: 28, fontWeight: FontWeight.w700, color: BColors.accent,
              ))),
            ).animate(onPlay: (c) => c.repeat(reverse: true))
                .scale(begin: const Offset(1.0, 1.0), end: const Offset(1.08, 1.08), duration: 1200.ms, curve: Curves.easeInOut),
            const SizedBox(height: 16),
            Text(name, style: TextStyle(fontFamily: 'Nekst', fontSize: rf(context, 18),
              fontWeight: FontWeight.w600, color: BColors.textPrimary)),
            const SizedBox(height: 4),
            Text('входящий звонок...', style: TextStyle(fontFamily: 'Onest',
              fontSize: rf(context, 13), color: BColors.textSecondary)),
            const SizedBox(height: 24),
            // Buttons
            Row(mainAxisAlignment: MainAxisAlignment.center, children: [
              _RoundCallBtn(color: const Color(0xFFef4444), icon: SolarIconsOutline.closeCircle, onTap: onDecline),
              const SizedBox(width: 40),
              _RoundCallBtn(color: const Color(0xFF22c55e), icon: SolarIconsBold.phone, onTap: onAccept),
            ]),
          ]),
        ).animate()
            .slideY(begin: -0.15, duration: 300.ms, curve: Curves.easeOutCubic)
            .fade(begin: 0, duration: 250.ms),
      ),
    );
  }
}

class _RoundCallBtn extends StatefulWidget {
  final Color color;
  final IconData icon;
  final VoidCallback onTap;
  const _RoundCallBtn({required this.color, required this.icon, required this.onTap});
  @override
  State<_RoundCallBtn> createState() => _RoundCallBtnState();
}

class _RoundCallBtnState extends State<_RoundCallBtn> {
  bool _h = false;
  @override
  Widget build(BuildContext context) {
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setState(() => _h = true),
      onExit: (_) => setState(() => _h = false),
      child: GestureDetector(
        onTap: widget.onTap,
        child: AnimatedScale(
          scale: _h ? 1.1 : 1.0,
          duration: const Duration(milliseconds: 150),
          child: Container(
            width: 56, height: 56,
            decoration: BoxDecoration(shape: BoxShape.circle, color: widget.color),
            child: Center(child: Icon(widget.icon, size: 24, color: Colors.white)),
          ),
        ),
      ),
    );
  }
}

// ═══════════════════════════════════════════════════════════════
// CALL PILL (in titlebar area, when call is minimized)
// ═══════════════════════════════════════════════════════════════

class CallPill extends StatefulWidget {
  final String name;
  final int seconds;
  final VoidCallback onTap;
  const CallPill({super.key, required this.name, required this.seconds, required this.onTap});
  @override
  State<CallPill> createState() => _CallPillState();
}

class _CallPillState extends State<CallPill> {
  bool _h = false;
  @override
  Widget build(BuildContext context) {
    final m = (widget.seconds ~/ 60).toString().padLeft(2, '0');
    final s = (widget.seconds % 60).toString().padLeft(2, '0');
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setState(() => _h = true),
      onExit: (_) => setState(() => _h = false),
      child: GestureDetector(
        onTap: widget.onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 150),
          height: 28,
          padding: const EdgeInsets.symmetric(horizontal: 10),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(14),
            color: _h ? BColors.accent.withValues(alpha: 0.15) : BColors.accent.withValues(alpha: 0.08),
            border: Border.all(color: BColors.accent.withValues(alpha: 0.2)),
          ),
          child: Row(mainAxisSize: MainAxisSize.min, children: [
            Container(
              width: 6, height: 6,
              decoration: const BoxDecoration(shape: BoxShape.circle, color: Color(0xFF22c55e)),
            ).animate(onPlay: (c) => c.repeat(reverse: true))
                .fade(begin: 0.4, end: 1.0, duration: 800.ms),
            const SizedBox(width: 6),
            Text(widget.name, style: const TextStyle(fontFamily: 'Onest', fontSize: 11,
              fontWeight: FontWeight.w500, color: BColors.textPrimary)),
            const SizedBox(width: 6),
            Text('$m:$s', style: const TextStyle(fontFamily: 'Onest', fontSize: 11, color: BColors.textSecondary)),
          ]),
        ),
      ),
    ).animate().fadeIn(duration: 200.ms).slideX(begin: 0.1);
  }
}
