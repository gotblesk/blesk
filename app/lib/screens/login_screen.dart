import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:solar_icons/solar_icons.dart';

import 'shared/widgets.dart';
import 'forgot_password_screen.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});
  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _loginCtrl = TextEditingController();
  final _passCtrl = TextEditingController();
  bool _obscure = true;

  bool get _valid => _loginCtrl.text.isNotEmpty && _passCtrl.text.isNotEmpty;

  @override
  void dispose() {
    _loginCtrl.dispose();
    _passCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: BColors.bg,
      body: Column(children: [
        const BLeskTitleBar(),
        Container(height: 1, color: BColors.borderLow),
        Expanded(
          child: Stack(children: [
            const DecoLayer(),
            Row(children: [
              const Expanded(child: BLeskLeftPanel()),
              Container(width: 1, color: BColors.borderLow),
              Expanded(
                child: Stack(children: [
                  Center(
                  child: ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 440),
                    child: SingleChildScrollView(
                      child: Padding(
                        padding: EdgeInsets.symmetric(
                          horizontal: rs(context, 48), vertical: rs(context, 40),
                        ),
                        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                          SizedBox(height: rs(context, 80)),

                          // Logo + line
                          Image.asset('assets/logo/blesk-logo.png',
                            width: rs(context, 120), fit: BoxFit.contain,
                          ).animate().fadeIn(duration: 500.ms).slideY(begin: 0.06),
                          SizedBox(height: rs(context, 12)),
                          Container(
                            width: rs(context, 32), height: 1,
                            decoration: BoxDecoration(
                              color: BColors.accent.withValues(alpha: 0.2),
                              borderRadius: BorderRadius.circular(0.5),
                            ),
                          ).animate(delay: 150.ms).scaleX(
                            begin: 0, alignment: Alignment.centerLeft, duration: 400.ms,
                          ),

                          SizedBox(height: rs(context, 44)),

                          // Heading
                          Text('с возвращением', style: TextStyle(
                            fontFamily: 'Nekst', fontSize: rf(context, 28),
                            fontWeight: FontWeight.w700, color: BColors.textPrimary,
                          )).animate(delay: 200.ms).fadeIn(duration: 400.ms).slideY(begin: 0.05),

                          SizedBox(height: rs(context, 36)),

                          // Login field
                          BLeskGlassInput(
                            controller: _loginCtrl, hint: 'логин или email',
                            prefixText: '@ ', autofocus: true,
                            onChanged: (_) => setState(() {}),
                          ).animate(delay: 300.ms).fadeIn(duration: 400.ms),

                          SizedBox(height: rs(context, 14)),

                          // Password field
                          BLeskGlassInput(
                            controller: _passCtrl, hint: 'пароль', obscure: _obscure,
                            onChanged: (_) => setState(() {}),
                            onSubmitted: _valid ? (_) {} : null,
                            prefix: Padding(
                              padding: EdgeInsets.only(left: rs(context, 14)),
                              child: Icon(SolarIconsOutline.lock,
                                size: rs(context, 20), color: BColors.textMuted),
                            ),
                            suffix: GestureDetector(
                              onTap: () => setState(() => _obscure = !_obscure),
                              child: Padding(
                                padding: EdgeInsets.only(right: rs(context, 14)),
                                child: Icon(
                                  _obscure ? SolarIconsOutline.eyeClosed : SolarIconsOutline.eye,
                                  size: rs(context, 20), color: BColors.textMuted,
                                ),
                              ),
                            ),
                          ).animate(delay: 350.ms).fadeIn(duration: 400.ms),

                          SizedBox(height: rs(context, 12)),

                          // Forgot password
                          Align(
                            alignment: Alignment.centerRight,
                            child: HoverLink(
                              text: 'забыл пароль?',
                              fontSize: 12,
                              fontWeight: FontWeight.w400,
                              onTap: () => Navigator.of(context).push(
                                MaterialPageRoute(builder: (_) => const ForgotPasswordScreen()),
                              ),
                            ),
                          ).animate(delay: 400.ms).fadeIn(duration: 300.ms),

                          SizedBox(height: rs(context, 36)),

                          // CTA
                          BLeskCtaButton(
                            label: 'войти', enabled: _valid, onTap: _valid ? () {} : null,
                          ).animate(delay: 450.ms).fadeIn(duration: 400.ms),

                          SizedBox(height: rs(context, 28)),

                          // Divider "или"
                          Row(children: [
                            Expanded(child: Container(
                              height: 1, color: Colors.white.withValues(alpha: 0.06),
                            )),
                            Padding(
                              padding: EdgeInsets.symmetric(horizontal: rs(context, 16)),
                              child: Text('или', style: TextStyle(
                                fontFamily: 'Onest', fontSize: rf(context, 12),
                                color: BColors.textMuted,
                              )),
                            ),
                            Expanded(child: Container(
                              height: 1, color: Colors.white.withValues(alpha: 0.06),
                            )),
                          ]).animate(delay: 500.ms).fadeIn(duration: 300.ms),

                          SizedBox(height: rs(context, 28)),

                          // Social buttons (glass + hover)
                          Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              _SocialBtn(icon: SolarIconsOutline.global),
                              SizedBox(width: rs(context, 16)),
                              _SocialBtn(icon: Icons.apple), // TODO(solar-migration): brand logo, Solar has no apple/google logos; keep Material
                              SizedBox(width: rs(context, 16)),
                              _SocialBtn(icon: SolarIconsOutline.plain),
                            ],
                          ).animate(delay: 550.ms).fadeIn(duration: 400.ms),

                          SizedBox(height: rs(context, 36)),

                          // Create account link
                          Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Text('нет аккаунта? ', style: TextStyle(
                                fontFamily: 'Onest', fontSize: rf(context, 13),
                                color: BColors.textSecondary,
                              )),
                              HoverLink(
                                text: 'создать',
                                onTap: () => Navigator.of(context).pop(),
                              ),
                            ],
                          ).animate(delay: 600.ms).fadeIn(duration: 350.ms),

                          SizedBox(height: rs(context, 32)),
                        ]),
                      ),
                    ),
                  ),
                ),
                  Positioned(
                    top: 24, left: 32,
                    child: HoverBackButton(onTap: () => Navigator.of(context).pop()),
                  ),
                  // VolumeControl — disabled until audio backend fixed
                ]),
              ),
            ]),
          ]),
        ),
      ]),
    );
  }
}

class _SocialBtn extends StatelessWidget {
  final IconData icon;
  const _SocialBtn({required this.icon});

  @override
  Widget build(BuildContext context) {
    final s = rs(context, 56);
    return Tooltip(
      message: 'в разработке',
      textStyle: const TextStyle(fontSize: 12, color: Color(0xE6FFFFFF)),
      decoration: BoxDecoration(
        color: const Color(0xFF1a1a1a),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: const Color(0x0FFFFFFF)),
      ),
      waitDuration: const Duration(milliseconds: 400),
      child: Opacity(
        opacity: 0.35,
        child: MouseRegion(
          cursor: SystemMouseCursors.forbidden,
          child: ClipRRect(
            borderRadius: BorderRadius.circular(rs(context, 14)),
            child: BackdropFilter(
              filter: ImageFilter.blur(sigmaX: 20, sigmaY: 20),
              child: Container(
                width: s, height: s,
                decoration: BoxDecoration(
                  color: Colors.white.withValues(alpha: 0.05),
                  borderRadius: BorderRadius.circular(rs(context, 14)),
                  border: Border.all(color: Colors.white.withValues(alpha: 0.08), width: 0.5),
                ),
                child: Center(
                  child: Icon(icon, size: rs(context, 22),
                    color: Colors.white.withValues(alpha: 0.35)),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
