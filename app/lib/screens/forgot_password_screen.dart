import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:solar_icons/solar_icons.dart';

import 'shared/widgets.dart';

class ForgotPasswordScreen extends StatefulWidget {
  const ForgotPasswordScreen({super.key});
  @override
  State<ForgotPasswordScreen> createState() => _ForgotPasswordScreenState();
}

class _ForgotPasswordScreenState extends State<ForgotPasswordScreen> {
  final _emailCtrl = TextEditingController();
  bool _sent = false;
  bool _sending = false;

  bool get _valid => RegExp(r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$').hasMatch(_emailCtrl.text);

  @override
  void dispose() {
    _emailCtrl.dispose();
    super.dispose();
  }

  void _submit() {
    if (!_valid || _sending) return;
    setState(() => _sending = true);
    Timer(const Duration(seconds: 2), () {
      if (mounted) setState(() { _sending = false; _sent = true; });
    });
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
                    child: SingleChildScrollView(
                      child: ConstrainedBox(
                        constraints: const BoxConstraints(maxWidth: 440),
                        child: Padding(
                          padding: EdgeInsets.symmetric(
                            horizontal: rs(context, 48), vertical: rs(context, 40),
                          ),
                          child: AnimatedSwitcher(
                            duration: const Duration(milliseconds: 350),
                            child: _sent ? _buildSent() : _buildForm(),
                          ),
                        ),
                      ),
                    ),
                  ),
                  Positioned(
                    top: 24, left: 32,
                    child: HoverBackButton(onTap: () => Navigator.of(context).pop()),
                  ),
                ]),
              ),
            ]),
          ]),
        ),
      ]),
    );
  }

  Widget _buildForm() {
    return Column(
      key: const ValueKey('form'),
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('восстановление', style: TextStyle(
          fontFamily: 'Nekst', fontSize: rf(context, 28),
          fontWeight: FontWeight.w700, color: BColors.textPrimary,
        )).animate().fadeIn(duration: 400.ms).slideY(begin: 0.05),
        SizedBox(height: rs(context, 8)),
        Text('введи email от аккаунта', style: TextStyle(
          fontFamily: 'Onest', fontSize: rf(context, 14),
          fontWeight: FontWeight.w400, color: BColors.textSecondary,
        )).animate(delay: 100.ms).fadeIn(duration: 350.ms),
        SizedBox(height: rs(context, 36)),
        BLeskGlassInput(
          controller: _emailCtrl, hint: 'email',
          keyboardType: TextInputType.emailAddress,
          autofocus: true,
          onChanged: (_) => setState(() {}),
          onSubmitted: _valid ? (_) => _submit() : null,
          prefix: Padding(
            padding: EdgeInsets.only(left: rs(context, 14)),
            child: Icon(SolarIconsOutline.letter, size: rs(context, 20), color: BColors.textMuted),
          ),
        ).animate(delay: 200.ms).fadeIn(duration: 400.ms),
        SizedBox(height: rs(context, 12)),
        Text('отправим ссылку для сброса', style: TextStyle(
          fontFamily: 'Onest', fontSize: rf(context, 12), color: BColors.textMuted,
        )).animate(delay: 300.ms).fadeIn(duration: 300.ms),
        SizedBox(height: rs(context, 32)),
        _sending
            ? Center(
                child: SizedBox(width: 24, height: 24,
                  child: CircularProgressIndicator(strokeWidth: 2, color: BColors.accent)),
              )
            : BLeskCtaButton(
                label: 'отправить', enabled: _valid, onTap: _valid ? _submit : null,
              ).animate(delay: 350.ms).fadeIn(duration: 400.ms),
        SizedBox(height: rs(context, 28)),
        Row(children: [
          Text('вспомнил пароль? ', style: TextStyle(
            fontFamily: 'Onest', fontSize: rf(context, 13), color: BColors.textSecondary,
          )),
          HoverLink(
            text: 'войти',
            onTap: () => Navigator.of(context).pop(),
          ),
        ]).animate(delay: 400.ms).fadeIn(duration: 350.ms),
      ],
    );
  }

  Widget _buildSent() {
    return Column(
      key: const ValueKey('sent'),
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        Icon(SolarIconsOutline.letter, size: rs(context, 32),
          color: BColors.accent.withValues(alpha: 0.5)),
        SizedBox(height: rs(context, 24)),
        Text('проверь почту', style: TextStyle(
          fontFamily: 'Nekst', fontSize: rf(context, 24),
          fontWeight: FontWeight.w700, color: BColors.textPrimary,
        )).animate().fadeIn(duration: 500.ms).slideY(begin: 0.08),
        SizedBox(height: rs(context, 12)),
        Text('отправили ссылку на ${_emailCtrl.text}', textAlign: TextAlign.center, style: TextStyle(
          fontFamily: 'Onest', fontSize: rf(context, 14), color: BColors.textSecondary,
        )).animate(delay: 200.ms).fadeIn(duration: 400.ms),
        SizedBox(height: rs(context, 36)),
        BLeskCtaButton(
          label: 'обратно ко входу', enabled: true, width: rs(context, 240),
          onTap: () => Navigator.of(context).pop(),
        ).animate(delay: 400.ms).fadeIn(duration: 400.ms).slideY(begin: 0.06),
      ],
    );
  }
}

// _HoverBackButton removed — using shared HoverBackButton from widgets.dart
