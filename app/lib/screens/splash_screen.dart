import 'dart:math';
import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:bitsdojo_window/bitsdojo_window.dart';

import 'shared/theme.dart';
import 'onboarding_screen.dart';

/// Liquid Logo Preloader
/// Phase 1 (0-1.5s): acid-green dots drift chaotically
/// Phase 2 (1.5-2.5s): dots pull toward center, merge
/// Phase 3 (2.5-3.5s): logo fades in, dots dissolve
/// Phase 4 (3.5-4.5s): logo glow pulse, version text appears
/// Then: transition to onboarding
class SplashScreen extends StatefulWidget {
  const SplashScreen({super.key});
  @override
  State<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen> with TickerProviderStateMixin {
  late final AnimationController _ctrl;
  late final List<_Droplet> _drops;
  final _rng = Random(42);
  bool _navigated = false;

  @override
  void initState() {
    super.initState();

    // Resize window for splash
    doWhenWindowReady(() {
      appWindow.size = const Size(500, 350);
      appWindow.alignment = Alignment.center;
    });

    // 7 liquid droplets with random initial positions
    _drops = List.generate(7, (i) => _Droplet(
      x: 0.3 + _rng.nextDouble() * 0.4,  // 30-70% of width
      y: 0.25 + _rng.nextDouble() * 0.5,  // 25-75% of height
      vx: (_rng.nextDouble() - 0.5) * 0.15,
      vy: (_rng.nextDouble() - 0.5) * 0.12,
      radius: 12.0 + _rng.nextDouble() * 10,
    ));

    _ctrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 4500),
    )..forward();

    _ctrl.addStatusListener((status) {
      if (status == AnimationStatus.completed && !_navigated) {
        _navigated = true;
        // Restore window size
        appWindow.size = const Size(1280, 800);
        appWindow.alignment = Alignment.center;
        Navigator.of(context).pushReplacement(
          PageRouteBuilder(
            pageBuilder: (_, __, ___) => const OnboardingScreen(),
            transitionDuration: const Duration(milliseconds: 500),
            transitionsBuilder: (_, anim, __, child) =>
                FadeTransition(opacity: anim, child: child),
          ),
        );
      }
    });
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: BColors.bg,
      body: AnimatedBuilder(
        animation: _ctrl,
        builder: (context, _) {
          final t = _ctrl.value; // 0.0 → 1.0 over 4.5s
          return Stack(children: [
            // Metaball droplets
            Positioned.fill(
              child: CustomPaint(
                painter: _LiquidPainter(
                  drops: _drops,
                  progress: t,
                ),
              ),
            ),
            // Logo (phase 3+)
            if (t > 0.5)
              Center(
                child: Opacity(
                  opacity: _logoOpacity(t),
                  child: Transform.scale(
                    scale: _logoScale(t),
                    child: Image.asset(
                      'assets/logo/blesk-logo.png',
                      width: 140,
                      fit: BoxFit.contain,
                    ),
                  ),
                ),
              ),
            // Version text (phase 4)
            if (t > 0.78)
              Positioned(
                bottom: 40,
                left: 0, right: 0,
                child: Opacity(
                  opacity: ((t - 0.78) / 0.12).clamp(0.0, 1.0),
                  child: const Text(
                    'v1.0.6-beta',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      fontFamily: 'Onest',
                      fontSize: 11,
                      color: BColors.textMuted,
                      letterSpacing: 0.5,
                    ),
                  ),
                ),
              ),
            // Status dots + text (bottom center)
            Positioned(
              bottom: 60,
              left: 0, right: 0,
              child: _StatusIndicator(progress: t),
            ),
          ]);
        },
      ),
    );
  }

  double _logoOpacity(double t) {
    // Fade in from 0.5 to 0.7, then full
    if (t < 0.5) return 0;
    if (t < 0.7) return (t - 0.5) / 0.2;
    // Glow pulse at phase 4
    if (t > 0.78 && t < 0.9) {
      final p = (t - 0.78) / 0.12;
      return 1.0 - sin(p * pi) * 0.15; // subtle 15% dip
    }
    return 1.0;
  }

  double _logoScale(double t) {
    if (t < 0.5) return 0.8;
    if (t < 0.7) return 0.8 + (t - 0.5) / 0.2 * 0.2; // 0.8 → 1.0
    // Pulse at phase 4
    if (t > 0.78 && t < 0.9) {
      final p = (t - 0.78) / 0.12;
      return 1.0 + sin(p * pi) * 0.03;
    }
    return 1.0;
  }
}

// ─── Droplet data ─────────────────────────────────────────────

class _Droplet {
  double x, y, vx, vy, radius;
  _Droplet({required this.x, required this.y, required this.vx, required this.vy, required this.radius});
}

// ─── Liquid painter (metaball field) ──────────────────────────

class _LiquidPainter extends CustomPainter {
  final List<_Droplet> drops;
  final double progress;

  _LiquidPainter({required this.drops, required this.progress});

  @override
  void paint(Canvas canvas, Size size) {
    final t = progress;
    final cx = size.width / 2;
    final cy = size.height / 2;

    // Calculate current positions based on phase
    final positions = <(double, double, double)>[];
    for (final d in drops) {
      double x, y, r;

      if (t < 0.33) {
        // Phase 1: drift chaotically
        final pt = t / 0.33;
        x = (d.x + d.vx * pt) * size.width;
        y = (d.y + d.vy * pt) * size.height;
        // Slight wobble
        x += sin(t * 12 + d.x * 20) * 8;
        y += cos(t * 10 + d.y * 15) * 6;
        r = d.radius;
      } else if (t < 0.56) {
        // Phase 2: pull toward center
        final pt = (t - 0.33) / 0.23;
        final eased = Curves.easeInCubic.transform(pt);
        final startX = (d.x + d.vx * 1.0) * size.width + sin(0.33 * 12 + d.x * 20) * 8;
        final startY = (d.y + d.vy * 1.0) * size.height + cos(0.33 * 10 + d.y * 15) * 6;
        x = lerpDouble(startX, cx, eased)!;
        y = lerpDouble(startY, cy, eased)!;
        r = d.radius * (1.0 - eased * 0.3); // shrink slightly
      } else {
        // Phase 3-4: merged at center, dissolving
        final pt = ((t - 0.56) / 0.44).clamp(0.0, 1.0);
        x = cx + sin(d.x * 30 + t * 5) * (1 - pt) * 15;
        y = cy + cos(d.y * 25 + t * 4) * (1 - pt) * 12;
        r = d.radius * (1.0 - pt).clamp(0.0, 1.0); // dissolve
      }

      if (r > 0.5) positions.add((x, y, r));
    }

    if (positions.isEmpty) return;

    // Draw metaball field
    const step = 4.0;
    final paint = Paint();

    for (var py = 0.0; py < size.height; py += step) {
      for (var px = 0.0; px < size.width; px += step) {
        var field = 0.0;
        for (final (ox, oy, or) in positions) {
          final dx = px - ox;
          final dy = py - oy;
          field += (or * or) / (dx * dx + dy * dy + 1);
        }

        if (field > 0.8) {
          final intensity = ((field - 0.8) * 0.5).clamp(0.0, 1.0);
          // Core is brighter, edges softer
          final alpha = intensity * 0.35;
          paint.color = Color.fromRGBO(200, 255, 0, alpha);
          canvas.drawCircle(Offset(px, py), step * 0.5, paint);
        }
      }
    }

    // Center glow (phase 2+)
    if (t > 0.33) {
      final glowT = ((t - 0.33) / 0.3).clamp(0.0, 1.0);
      final glowR = 60.0 + glowT * 40;
      canvas.drawCircle(Offset(cx, cy), glowR, Paint()
        ..shader = RadialGradient(
          colors: [
            Color.fromRGBO(200, 255, 0, glowT * 0.08),
            Colors.transparent,
          ],
        ).createShader(Rect.fromCircle(center: Offset(cx, cy), radius: glowR)));
    }
  }

  @override
  bool shouldRepaint(covariant _LiquidPainter old) => old.progress != progress;
}

// ─── Status indicator ─────────────────────────────────────────

class _StatusIndicator extends StatelessWidget {
  final double progress;
  const _StatusIndicator({required this.progress});

  String get _text {
    if (progress < 0.25) return 'подключение...';
    if (progress < 0.5) return 'загрузка данных...';
    if (progress < 0.75) return 'подготовка...';
    return 'готово';
  }

  @override
  Widget build(BuildContext context) {
    return Column(mainAxisSize: MainAxisSize.min, children: [
      // 4 dot indicators
      Row(mainAxisAlignment: MainAxisAlignment.center, children: [
        for (var i = 0; i < 4; i++)
          Container(
            width: 4, height: 4,
            margin: const EdgeInsets.symmetric(horizontal: 3),
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: progress > i * 0.25
                  ? BColors.accent.withValues(alpha: 0.6)
                  : Colors.white.withValues(alpha: 0.1),
            ),
          ),
      ]),
      const SizedBox(height: 10),
      Text(_text, style: TextStyle(
        fontFamily: 'Onest',
        fontSize: 11,
        color: BColors.textMuted.withValues(alpha: 0.7),
        letterSpacing: 0.3,
      )),
    ]);
  }
}
