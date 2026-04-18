import 'package:flutter/material.dart';

class BColors {
  static const bg = Color(0xFF0A0A0A);
  static const accent = Color(0xFFC8FF00);
  static const accentDim = Color(0x33C8FF00);
  static const textPrimary = Color(0xE6FFFFFF);
  static const textSecondary = Color(0x80FFFFFF);
  static const textMuted = Color(0x40FFFFFF);
  static const surfaceLow = Color(0x0AFFFFFF);
  static const borderLow = Color(0x0FFFFFFF);
  static const error = Color(0x99FF4444);
}

double rs(BuildContext context, double value) {
  final w = MediaQuery.of(context).size.width;
  return value * (w / 1280).clamp(0.75, 1.3);
}

double rf(BuildContext context, double value) {
  final w = MediaQuery.of(context).size.width;
  return value * (w / 1280).clamp(0.8, 1.2);
}

class StarPainter extends CustomPainter {
  final Color color;
  StarPainter(this.color);

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()..color = color;
    final path = Path();
    final cx = size.width / 2;
    final cy = size.height / 2;
    final r = size.width / 2;
    final nr = r * 0.12;
    path.moveTo(cx, 0);
    path.quadraticBezierTo(cx + nr, cy - nr, cx + r, cy);
    path.quadraticBezierTo(cx + nr, cy + nr, cx, cy + r);
    path.quadraticBezierTo(cx - nr, cy + nr, cx - r, cy);
    path.quadraticBezierTo(cx - nr, cy - nr, cx, 0);
    path.close();
    canvas.drawPath(path, paint);
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
