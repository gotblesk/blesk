import 'package:flutter/material.dart';
import 'package:blesk/core/theme/app_colors.dart';

abstract final class AppTypography {
  static const _nekst = 'Nekst';
  static const _onest = 'Onest';

  // Display — Nekst Black 32
  static const display = TextStyle(
    fontFamily: _nekst,
    fontSize: 32,
    fontWeight: FontWeight.w900,
    color: AppColors.textPrimary,
    height: 1.2,
  );

  // Headline — Nekst Bold 24
  static const headline = TextStyle(
    fontFamily: _nekst,
    fontSize: 24,
    fontWeight: FontWeight.w700,
    color: AppColors.textPrimary,
    height: 1.25,
  );

  // Title — Nekst SemiBold 18
  static const title = TextStyle(
    fontFamily: _nekst,
    fontSize: 18,
    fontWeight: FontWeight.w600,
    color: AppColors.textPrimary,
    height: 1.3,
  );

  // Subtitle — Onest SemiBold 15
  static const subtitle = TextStyle(
    fontFamily: _onest,
    fontSize: 15,
    fontWeight: FontWeight.w600,
    color: AppColors.textPrimary,
    height: 1.4,
  );

  // Body — Onest Regular 15
  static const body = TextStyle(
    fontFamily: _onest,
    fontSize: 15,
    fontWeight: FontWeight.w400,
    color: AppColors.textPrimary,
    height: 1.5,
  );

  // Body Medium — Onest Medium 15
  static const bodyMedium = TextStyle(
    fontFamily: _onest,
    fontSize: 15,
    fontWeight: FontWeight.w500,
    color: AppColors.textPrimary,
    height: 1.5,
  );

  // Caption — Onest Medium 11
  static const caption = TextStyle(
    fontFamily: _onest,
    fontSize: 11,
    fontWeight: FontWeight.w500,
    color: AppColors.textSecondary,
    height: 1.4,
  );
}
