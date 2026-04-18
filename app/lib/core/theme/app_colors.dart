import 'package:flutter/material.dart';

abstract final class AppColors {
  // Background
  static const bgApp = Color(0xFF0A0A0F);

  // Surfaces
  static const surface0 = Color(0xFF12121A);
  static const surface1 = Color(0xFF1A1A24);
  static const surface2 = Color(0xFF22222E);
  static const surface3 = Color(0xFF2A2A38);

  // Accent (GOTBLESK green)
  static const accent = Color(0xFFC8FF00);
  static const accentHover = Color(0xFFD4FF33);
  static const accentDim = Color(0x33C8FF00); // 20% opacity

  // Text (dark theme)
  static const textPrimary = Color(0xDEFFFFFF);   // 87%
  static const textSecondary = Color(0x99FFFFFF);  // 60%
  static const textTertiary = Color(0x73FFFFFF);   // 45%
  static const textDisabled = Color(0x40FFFFFF);   // 25%

  // Semantic
  static const online = Color(0xFF4ADE80);
  static const danger = Color(0xFFEF4444);
  static const warning = Color(0xFFF59E0B);
  static const info = Color(0xFF3B82F6);

  // Glass
  static const glassBorder = Color(0x0FFFFFFF);    // 6%
  static const glassFill = Color(0x0AFFFFFF);      // 4%
  static const glassHighlight = Color(0x14FFFFFF);  // 8%

  // Light theme overrides
  static const lightBg = Color(0xFFF0F2F5);
  static const lightSurface = Color(0xFFFFFFFF);
  static const lightText = Color(0xFF111827);
  static const lightTextSecondary = Color(0xFF6B7280);
}
