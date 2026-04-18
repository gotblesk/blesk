import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:blesk/core/sound_engine.dart';

import 'shared/widgets.dart';
import 'login_screen.dart';
import 'main_screen.dart';

// Re-export shared widgets so existing imports from other screens still work
export 'shared/widgets.dart';

class OnboardingScreen extends StatefulWidget {
  const OnboardingScreen({super.key});
  @override
  State<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends State<OnboardingScreen> {
  int _step = 0;
  bool _fwd = true;
  double _mx = 0.5, _my = 0.5;

  final _userCtrl = TextEditingController();
  final _passCtrl = TextEditingController();
  final _confCtrl = TextEditingController();
  final _mailCtrl = TextEditingController();

  String _userStatus = '';
  bool _obscPass = true;
  bool _obscConf = true;
  Timer? _debounce;

  String? _userHint;
  String _userHintType = 'info';
  String? _passHint;
  String _passHintType = 'info';
  String? _emailHint;
  bool _capsLockOn = false;
  Timer? _hintTimer;

  static const _commonPasswords = [
    '12345678', 'password', 'qwerty123', '123456789', 'qwerty',
    'abc12345', 'password1', '11111111', '00000000', 'iloveyou',
    'qwerty12', '123123123', 'admin123', 'letmein', 'welcome1',
  ];

  bool _keyHandler(KeyEvent event) {
    if (event is KeyDownEvent || event is KeyUpEvent) {
      final caps = HardwareKeyboard.instance.lockModesEnabled
          .contains(KeyboardLockMode.capsLock);
      if (caps != _capsLockOn) setState(() => _capsLockOn = caps);
    }
    return false;
  }

  @override
  void initState() {
    super.initState();
    HardwareKeyboard.instance.addHandler(_keyHandler);
  }

  @override
  void dispose() {
    HardwareKeyboard.instance.removeHandler(_keyHandler);
    _userCtrl.dispose(); _passCtrl.dispose();
    _confCtrl.dispose(); _mailCtrl.dispose();
    _debounce?.cancel(); _hintTimer?.cancel();
    super.dispose();
  }

  bool get _valid {
    switch (_step) {
      case 0: case 1: return true;
      case 2: return _userStatus == 'ok' && _userCtrl.text.length >= 3;
      case 3:
        final p = _passCtrl.text;
        return p.length >= 8 && p == _confCtrl.text && !_commonPasswords.contains(p.toLowerCase());
      case 4: return RegExp(r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$').hasMatch(_mailCtrl.text);
      default: return false;
    }
  }

  String get _btnLabel => const ['далее', 'далее', 'далее', 'далее', 'подтвердить'][_step.clamp(0, 4)];

  void _next() { if (_valid && _step < 5) { SoundEngine().play(_step == 4 ? 'success' : 'stepForward'); setState(() { _fwd = true; _step++; }); } }
  void _prev() { if (_step > 0 && _step < 5) { SoundEngine().play('stepBack'); setState(() { _fwd = false; _step--; }); } }
  void _goTo(int s) { setState(() { _fwd = s > _step; _step = s; }); }
  void _tryNext(String _) { if (_valid) _next(); }

  int get _strength {
    final p = _passCtrl.text;
    if (p.isEmpty) return 0;
    var s = p.length;
    if (RegExp(r'[0-9]').hasMatch(p)) s += 2;
    if (RegExp(r'[A-Z]').hasMatch(p)) s += 2;
    if (RegExp(r'[!@#\$%^&*(),.?":{}|<>]').hasMatch(p)) s += 2;
    return s;
  }

  void _showTimedHint(String text, String type, void Function(String?) setter, {int seconds = 3}) {
    setState(() { setter(text); });
    _hintTimer?.cancel();
    _hintTimer = Timer(Duration(seconds: seconds), () {
      if (mounted) setState(() => setter(null));
    });
  }

  void _onUsernameChanged(String value) {
    // Check for cyrillic — show hint
    final hasCyrillic = RegExp(r'[а-яА-ЯёЁ]').hasMatch(value);
    if (hasCyrillic) {
      _showTimedHint('только латиница, цифры, точка и _', 'warning', (v) => _userHint = v, seconds: 3);
      _userHintType = 'warning';
    }
    // Allow a-z A-Z 0-9 . _
    var filtered = value.replaceAll(RegExp(r'[^a-zA-Z0-9._]'), '');
    if (filtered.isNotEmpty && (filtered[0] == '.' || filtered[0] == '_')) {
      filtered = filtered.substring(1);
      _showTimedHint('логин не может начинаться с точки или _', 'warning', (v) => _userHint = v, seconds: 2);
      _userHintType = 'warning';
    }
    if (filtered != value) {
      _userCtrl.text = filtered;
      _userCtrl.selection = TextSelection.collapsed(offset: filtered.length);
    }
    if (filtered.length >= 19) {
      setState(() { _userHint = 'осталось ${24 - filtered.length} символов'; _userHintType = 'info'; });
    }
    _debounce?.cancel();
    if (filtered.length < 3) { setState(() => _userStatus = filtered.isEmpty ? '' : 'short'); return; }
    setState(() => _userStatus = 'checking');
    _debounce = Timer(const Duration(milliseconds: 600), () {
      setState(() => _userStatus = 'ok');
    });
  }

  void _onPasswordChanged(String value) {
    setState(() {
      if (_commonPasswords.contains(value.toLowerCase())) {
        _passHint = 'слишком простой пароль'; _passHintType = 'error';
      } else { _passHint = null; }
    });
  }

  void _onEmailChanged(String value) {
    final filtered = value.replaceAll(RegExp(r'[а-яА-ЯёЁ\s]'), '');
    if (filtered != value) {
      _mailCtrl.text = filtered;
      _mailCtrl.selection = TextSelection.collapsed(offset: filtered.length);
      _showTimedHint('email только на латинице', 'warning', (v) => _emailHint = v, seconds: 3);
    }
    setState(() {
      _emailHint = (filtered.length > 5 && !RegExp(r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$').hasMatch(filtered))
          ? 'проверь адрес' : null;
    });
  }

  Widget _buildHint(String? text, String type) {
    if (text == null) return const SizedBox.shrink();
    Color color;
    switch (type) {
      case 'warning': color = const Color(0xB3FFB800);
      case 'error': color = const Color(0x99FF4444);
      case 'success': color = BColors.accent.withValues(alpha: 0.7);
      default: color = BColors.textMuted;
    }
    return Padding(
      padding: EdgeInsets.only(top: rs(context, 8)),
      child: Text(text, style: TextStyle(fontFamily: 'Onest', fontSize: rf(context, 12), color: color)),
    ).animate().fadeIn(duration: 200.ms).slideY(begin: -0.2);
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: _step == 0,
      onPopInvokedWithResult: (didPop, _) { if (!didPop && _step > 0 && _step < 5) _prev(); },
      child: KeyboardListener(
        focusNode: FocusNode(), autofocus: true,
        onKeyEvent: (event) {
          if (event is KeyDownEvent && event.logicalKey == LogicalKeyboardKey.escape) {
            if (_step > 0 && _step < 5) _prev();
          }
        },
        child: Scaffold(
          backgroundColor: BColors.bg,
          body: Column(children: [
            const BLeskTitleBar(),
            Container(height: 1, color: BColors.borderLow),
            Expanded(
              child: MouseRegion(
                onHover: (e) {
                  final sz = MediaQuery.of(context).size;
                  setState(() { _mx = e.position.dx / sz.width; _my = (e.position.dy - 39) / (sz.height - 39); });
                },
                child: Stack(children: [
                  DecoLayer(mouseX: _mx, mouseY: _my),
                  Row(children: [
                    const Expanded(child: BLeskLeftPanel()),
                    Container(width: 1, color: BColors.borderLow),
                    Expanded(
                      child: Stack(children: [
                        Column(children: [
                          Expanded(
                            child: Center(
                              child: SingleChildScrollView(
                                child: ConstrainedBox(
                                  constraints: const BoxConstraints(maxWidth: 440),
                                  child: Padding(
                                    padding: EdgeInsets.symmetric(horizontal: rs(context, 28), vertical: rs(context, 40)),
                                    child: AnimatedSwitcher(
                                      duration: const Duration(milliseconds: 350),
                                      transitionBuilder: (child, anim) {
                                        final off = _fwd ? 1.0 : -1.0;
                                        return FadeTransition(opacity: anim, child: SlideTransition(
                                          position: Tween<Offset>(begin: Offset(off * 0.1, 0), end: Offset.zero)
                                              .animate(CurvedAnimation(parent: anim, curve: Curves.easeOutCubic)),
                                          child: child,
                                        ));
                                      },
                                      child: KeyedSubtree(
                                        key: ValueKey(_step),
                                        child: Column(
                                          mainAxisSize: MainAxisSize.min,
                                          crossAxisAlignment: _step == 5 ? CrossAxisAlignment.center : CrossAxisAlignment.start,
                                          children: [_buildStep()],
                                        ),
                                      ),
                                    ),
                                  ),
                                ),
                              ),
                            ),
                          ),
                          if (_step < 5) _buildBottom(),
                        ]),
                        if (_step > 0 && _step < 5)
                          Positioned(top: 24, left: 32,
                            child: HoverBackButton(onTap: _prev).animate().fadeIn(duration: 300.ms)),
                      ]),
                    ),
                  ]),
                ]),
              ),
            ),
          ]),
        ),
      ),
    );
  }

  Widget _buildStep() {
    switch (_step) {
      case 0: return _step0(); case 1: return _step1(); case 2: return _step2();
      case 3: return _step3(); case 4: return _step4(); case 5: return _step5();
      default: return const SizedBox();
    }
  }

  Widget _step0() => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
    Image.asset('assets/logo/blesk-logo.png', width: rs(context, 120), fit: BoxFit.contain)
        .animate().fadeIn(duration: 600.ms).slideY(begin: 0.08),
    SizedBox(height: rs(context, 12)),
    Container(width: rs(context, 32), height: 1, decoration: BoxDecoration(
      color: BColors.accent.withValues(alpha: 0.2), borderRadius: BorderRadius.circular(0.5),
    )).animate(delay: 200.ms).scaleX(begin: 0, alignment: Alignment.centerLeft, duration: 400.ms),
    SizedBox(height: rs(context, 44)),
    Text('привет!', style: TextStyle(fontFamily: 'Nekst', fontSize: rf(context, 32),
      fontWeight: FontWeight.w700, color: BColors.textPrimary)).animate(delay: 350.ms).fadeIn(duration: 500.ms).slideY(begin: 0.06),
    SizedBox(height: rs(context, 12)),
    Text('рады тебя видеть.\nэто начало чего-то нового.', style: TextStyle(fontFamily: 'Onest',
      fontSize: rf(context, 16), fontWeight: FontWeight.w400, color: BColors.textSecondary, height: 1.5,
    )).animate(delay: 550.ms).fadeIn(duration: 450.ms),
    SizedBox(height: rs(context, 8)),
    Text('создание аккаунта займёт минуту', style: TextStyle(fontFamily: 'Onest',
      fontSize: rf(context, 13), fontWeight: FontWeight.w400, color: BColors.textMuted,
    )).animate(delay: 700.ms).fadeIn(duration: 350.ms),
  ]);

  Widget _step1() => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
    Text('это blesk', style: TextStyle(fontFamily: 'Nekst', fontSize: rf(context, 28),
      fontWeight: FontWeight.w700, color: BColors.textPrimary)).animate().fadeIn(duration: 400.ms).slideY(begin: 0.05),
    SizedBox(height: rs(context, 16)),
    Text('мессенджер, в котором\nтвои данные — только твои.', style: TextStyle(fontFamily: 'Onest',
      fontSize: rf(context, 17), fontWeight: FontWeight.w400, color: BColors.textSecondary, height: 1.6,
    )).animate(delay: 150.ms).fadeIn(duration: 400.ms),
    SizedBox(height: rs(context, 36)),
    _featureCard(Icons.shield_outlined, 'сквозное шифрование', 300),
    SizedBox(height: rs(context, 20)),
    _featureCard(Icons.bolt_outlined, 'мгновенные сообщения', 400),
    SizedBox(height: rs(context, 20)),
    _featureCard(Icons.face_outlined, 'интерфейс с характером', 500),
  ]);

  Widget _featureCard(IconData icon, String text, int ms) => BLeskGlass(
    radius: 12, padding: EdgeInsets.symmetric(horizontal: rs(context, 16), vertical: rs(context, 14)),
    child: Row(children: [
      Icon(icon, size: rs(context, 20), color: BColors.accent.withValues(alpha: 0.5)),
      SizedBox(width: rs(context, 14)),
      Text(text, style: TextStyle(fontFamily: 'Onest', fontSize: rf(context, 14),
        fontWeight: FontWeight.w500, color: BColors.textPrimary.withValues(alpha: 0.7))),
    ]),
  ).animate(delay: Duration(milliseconds: ms)).fadeIn(duration: 350.ms).slideX(begin: 0.05);

  Widget _step2() => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
    Text('придумай логин', style: TextStyle(fontFamily: 'Nekst', fontSize: rf(context, 28),
      fontWeight: FontWeight.w700, color: BColors.textPrimary)).animate().fadeIn(duration: 400.ms).slideY(begin: 0.05),
    SizedBox(height: rs(context, 8)),
    Text('так тебя будут находить друзья', style: TextStyle(fontFamily: 'Onest',
      fontSize: rf(context, 14), fontWeight: FontWeight.w400, color: BColors.textSecondary,
    )).animate(delay: 100.ms).fadeIn(duration: 350.ms),
    SizedBox(height: rs(context, 36)),
    BLeskGlassInput(controller: _userCtrl, hint: 'username', autofocus: true,
      maxLength: 24, prefixText: '@ ', onChanged: _onUsernameChanged, onSubmitted: _tryNext,
    ).animate(delay: 200.ms).fadeIn(duration: 400.ms),
    SizedBox(height: rs(context, 10)),
    _userStatusWidget(),
    _buildHint(_userHint, _userHintType),
    SizedBox(height: rs(context, 28)),
    Row(children: [
      Text('уже есть аккаунт? ', style: TextStyle(fontFamily: 'Onest', fontSize: rf(context, 13), color: BColors.textSecondary)),
      HoverLink(text: 'войти', onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const LoginScreen()))),
    ]).animate(delay: 400.ms).fadeIn(duration: 350.ms),
  ]);

  Widget _userStatusWidget() {
    if (_userStatus.isEmpty) return const SizedBox.shrink();
    Widget dot; String text; Color col;
    switch (_userStatus) {
      case 'checking':
        dot = SizedBox(width: 12, height: 12, child: CircularProgressIndicator(strokeWidth: 1.5, color: BColors.accent));
        text = 'проверяю...'; col = BColors.textMuted;
      case 'ok':
        dot = Container(width: 6, height: 6, decoration: const BoxDecoration(color: BColors.accent, shape: BoxShape.circle))
            .animate().scale(begin: const Offset(0, 0), duration: 250.ms, curve: Curves.easeOutBack);
        text = 'свободно'; col = BColors.accent.withValues(alpha: 0.7);
      case 'taken':
        dot = Container(width: 6, height: 6, decoration: const BoxDecoration(color: BColors.error, shape: BoxShape.circle))
            .animate().scale(begin: const Offset(0, 0), duration: 250.ms, curve: Curves.easeOutBack);
        text = 'занято'; col = BColors.error;
      case 'short':
        dot = const SizedBox.shrink(); text = 'минимум 3 символа'; col = BColors.textMuted;
      default: return const SizedBox.shrink();
    }
    return Row(children: [
      dot, if (_userStatus != 'short') SizedBox(width: rs(context, 8)),
      Text(text, style: TextStyle(fontFamily: 'Onest', fontSize: rf(context, 12), color: col)),
    ]).animate().fadeIn(duration: 200.ms);
  }

  Widget _step3() {
    final s = _strength;
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text('придумай пароль', style: TextStyle(fontFamily: 'Nekst', fontSize: rf(context, 28),
        fontWeight: FontWeight.w700, color: BColors.textPrimary)).animate().fadeIn(duration: 400.ms).slideY(begin: 0.05),
      SizedBox(height: rs(context, 8)),
      Text('минимум 8 символов, одна цифра', style: TextStyle(fontFamily: 'Onest',
        fontSize: rf(context, 14), fontWeight: FontWeight.w400, color: BColors.textSecondary,
      )).animate(delay: 100.ms).fadeIn(duration: 350.ms),
      SizedBox(height: rs(context, 36)),
      BLeskGlassInput(controller: _passCtrl, hint: 'пароль', obscure: _obscPass,
        onChanged: (v) { _onPasswordChanged(v); setState(() {}); },
        prefix: Padding(padding: EdgeInsets.only(left: rs(context, 14)),
          child: Icon(Icons.lock_outline, size: rs(context, 20), color: BColors.textMuted)),
        suffix: GestureDetector(onTap: () => setState(() => _obscPass = !_obscPass),
          child: Padding(padding: EdgeInsets.only(right: rs(context, 14)),
            child: Icon(_obscPass ? Icons.visibility_off_outlined : Icons.visibility_outlined,
              size: rs(context, 20), color: BColors.textMuted))),
      ).animate(delay: 200.ms).fadeIn(duration: 400.ms),
      SizedBox(height: rs(context, 14)),
      Row(children: List.generate(4, (i) {
        Color c;
        if (_passCtrl.text.isEmpty) { c = Colors.white.withValues(alpha: 0.06); }
        else if (s < 5) { c = i == 0 ? const Color(0x80FF4444) : Colors.white.withValues(alpha: 0.06); }
        else if (s < 8) { c = i < 2 ? const Color(0x80FFB800) : Colors.white.withValues(alpha: 0.06); }
        else if (s < 12) { c = i < 3 ? BColors.accent.withValues(alpha: 0.4) : Colors.white.withValues(alpha: 0.06); }
        else { c = BColors.accent.withValues(alpha: 0.6); }
        return Expanded(child: AnimatedContainer(
          duration: Duration(milliseconds: 200 + i * 50), curve: Curves.easeOutCubic,
          height: rs(context, 3), margin: EdgeInsets.only(right: i < 3 ? rs(context, 6) : 0),
          decoration: BoxDecoration(color: c, borderRadius: BorderRadius.circular(rs(context, 1.5))),
        ));
      })).animate(delay: 300.ms).fadeIn(duration: 300.ms),
      if (_capsLockOn && _passCtrl.text.isNotEmpty)
        Padding(padding: EdgeInsets.only(top: rs(context, 8)), child: Row(children: [
          Icon(Icons.keyboard_capslock, size: 14, color: const Color(0xB3FFB800)),
          SizedBox(width: rs(context, 6)),
          Text('Caps Lock включён', style: TextStyle(fontFamily: 'Onest', fontSize: rf(context, 12), color: const Color(0xB3FFB800))),
        ])).animate().fadeIn(duration: 200.ms),
      _buildHint(_passHint, _passHintType),
      if (_passCtrl.text.length >= 8) ...[
        SizedBox(height: rs(context, 14)),
        BLeskGlassInput(controller: _confCtrl, hint: 'ещё раз', obscure: _obscConf,
          onChanged: (_) => setState(() {}), onSubmitted: _tryNext,
          suffix: GestureDetector(onTap: () => setState(() => _obscConf = !_obscConf),
            child: Padding(padding: EdgeInsets.only(right: rs(context, 14)),
              child: Icon(_obscConf ? Icons.visibility_off_outlined : Icons.visibility_outlined,
                size: rs(context, 20), color: BColors.textMuted))),
        ).animate().fadeIn(duration: 300.ms).slideY(begin: 0.05),
        if (_confCtrl.text.isNotEmpty && _passCtrl.text != _confCtrl.text) ...[
          SizedBox(height: rs(context, 8)),
          Text('пароли не совпадают', style: TextStyle(fontFamily: 'Onest', fontSize: rf(context, 12), color: BColors.error)),
        ],
      ],
    ]);
  }

  Widget _step4() => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
    Text('почти готово', style: TextStyle(fontFamily: 'Nekst', fontSize: rf(context, 28),
      fontWeight: FontWeight.w700, color: BColors.textPrimary)).animate().fadeIn(duration: 400.ms).slideY(begin: 0.05),
    SizedBox(height: rs(context, 8)),
    Text('укажи почту для восстановления', style: TextStyle(fontFamily: 'Onest',
      fontSize: rf(context, 14), fontWeight: FontWeight.w400, color: BColors.textSecondary,
    )).animate(delay: 100.ms).fadeIn(duration: 350.ms),
    SizedBox(height: rs(context, 36)),
    BLeskGlassInput(controller: _mailCtrl, hint: 'email', keyboardType: TextInputType.emailAddress,
      onChanged: _onEmailChanged, onSubmitted: _tryNext,
      prefix: Padding(padding: EdgeInsets.only(left: rs(context, 14)),
        child: Icon(Icons.mail_outline, size: rs(context, 20), color: BColors.textMuted)),
    ).animate(delay: 200.ms).fadeIn(duration: 400.ms),
    _buildHint(_emailHint, _emailHint == 'проверь адрес' ? 'error' : 'warning'),
  ]);

  Widget _step5() => Column(crossAxisAlignment: CrossAxisAlignment.center, children: [
    CustomPaint(size: Size(rs(context, 64), rs(context, 64)), painter: StarPainter(BColors.accent))
        .animate().scale(begin: const Offset(0, 0), duration: 700.ms, curve: Curves.elasticOut).rotate(begin: -0.25),
    SizedBox(height: rs(context, 36)),
    Text('добро пожаловать', textAlign: TextAlign.center, style: TextStyle(fontFamily: 'Nekst',
      fontSize: rf(context, 24), fontWeight: FontWeight.w700, color: BColors.textPrimary,
    )).animate(delay: 350.ms).fadeIn(duration: 500.ms).slideY(begin: 0.08),
    Text('в мир blesk', textAlign: TextAlign.center, style: TextStyle(fontFamily: 'Nekst',
      fontSize: rf(context, 24), fontWeight: FontWeight.w700, color: BColors.accent,
    )).animate(delay: 450.ms).fadeIn(duration: 500.ms).slideY(begin: 0.08),
    SizedBox(height: rs(context, 12)),
    Text('аккаунт создан', textAlign: TextAlign.center, style: TextStyle(fontFamily: 'Onest',
      fontSize: rf(context, 14), color: BColors.textSecondary,
    )).animate(delay: 600.ms).fadeIn(duration: 400.ms),
    SizedBox(height: rs(context, 56)),
    BLeskCtaButton(label: 'начать', enabled: true, width: rs(context, 200),
      onTap: () => Navigator.of(context).pushReplacement(MaterialPageRoute(builder: (_) => const MainScreen())),
    ).animate(delay: 800.ms).fadeIn(duration: 400.ms).slideY(begin: 0.06),
  ]);

  Widget _buildBottom() => Padding(
    padding: EdgeInsets.only(left: rs(context, 28), right: rs(context, 28), bottom: rs(context, 24)),
    child: Column(children: [
      _indicator(),
      SizedBox(height: rs(context, 16)),
      BLeskCtaButton(label: _btnLabel, enabled: _valid, onTap: _valid ? _next : null),
    ]),
  );

  Widget _indicator() => Row(
    mainAxisAlignment: MainAxisAlignment.center,
    children: List.generate(6, (i) => _IndicatorDot(
      active: i == _step, past: i < _step, onTap: i < _step ? () => _goTo(i) : null,
    )),
  );
}

class _IndicatorDot extends StatefulWidget {
  final bool active, past;
  final VoidCallback? onTap;
  const _IndicatorDot({required this.active, required this.past, this.onTap});
  @override
  State<_IndicatorDot> createState() => _IndicatorDotState();
}

class _IndicatorDotState extends State<_IndicatorDot> {
  bool _hover = false;
  @override
  Widget build(BuildContext context) {
    final hoverPast = widget.past && _hover;
    return MouseRegion(
      cursor: widget.past ? SystemMouseCursors.click : SystemMouseCursors.basic,
      onEnter: (_) => setState(() => _hover = true),
      onExit: (_) => setState(() => _hover = false),
      child: GestureDetector(
        onTap: widget.onTap,
        child: Padding(
          padding: EdgeInsets.symmetric(horizontal: rs(context, 3), vertical: 4),
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 200), curve: Curves.easeOut,
            width: widget.active ? rs(context, 24) : rs(context, 8),
            height: hoverPast ? rs(context, 3.6) : rs(context, 3),
            decoration: BoxDecoration(
              color: widget.active ? BColors.accent.withValues(alpha: 0.6)
                  : hoverPast ? BColors.accent.withValues(alpha: 0.5)
                  : widget.past ? BColors.accent.withValues(alpha: 0.25)
                  : Colors.white.withValues(alpha: 0.08),
              borderRadius: BorderRadius.circular(rs(context, 1.5)),
            ),
          ),
        ),
      ),
    );
  }
}
