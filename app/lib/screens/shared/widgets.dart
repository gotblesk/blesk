import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';
import 'package:flutter/services.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:bitsdojo_window/bitsdojo_window.dart';
import 'package:solar_icons/solar_icons.dart';
import 'package:blesk/core/sound_engine.dart';

import 'theme.dart';
export 'theme.dart';

// ─── Titlebar ─────────────────────────────────────────────────

class BLeskTitleBar extends StatelessWidget {
  const BLeskTitleBar({super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 38,
      color: BColors.bg,
      child: Row(children: [
        Expanded(
          child: MoveWindow(
            child: Padding(
              padding: const EdgeInsets.only(left: 16),
              child: Row(children: [
                CustomPaint(
                  size: const Size(14, 14),
                  painter: StarPainter(BColors.accent.withValues(alpha: 0.4)),
                ),
                const SizedBox(width: 10),
                const Text('blesk', style: TextStyle(
                  fontSize: 12, fontWeight: FontWeight.w600,
                  color: BColors.textMuted, letterSpacing: 0.5,
                )),
              ]),
            ),
          ),
        ),
        const _WinBtns(),
      ]),
    );
  }
}

class _WinBtns extends StatelessWidget {
  const _WinBtns();
  @override
  Widget build(BuildContext context) {
    return Row(children: [
      _WinBtn(onTap: () => appWindow.minimize(), icon: SolarIconsOutline.minusSquare),
      _WinBtn(onTap: () => appWindow.maximizeOrRestore(), icon: SolarIconsOutline.maximizeSquare),
      _WinBtn(onTap: () => appWindow.close(), icon: SolarIconsOutline.closeSquare, isClose: true),
    ]);
  }
}

class _WinBtn extends StatefulWidget {
  final VoidCallback onTap;
  final IconData icon;
  final bool isClose;
  const _WinBtn({required this.onTap, required this.icon, this.isClose = false});
  @override
  State<_WinBtn> createState() => _WinBtnState();
}

class _WinBtnState extends State<_WinBtn> {
  bool _h = false;
  @override
  Widget build(BuildContext context) {
    return MouseRegion(
      onEnter: (_) => setState(() => _h = true),
      onExit: (_) => setState(() => _h = false),
      child: GestureDetector(
        onTap: widget.onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 150),
          width: 46, height: 38,
          color: _h ? (widget.isClose ? const Color(0xFFE81123) : BColors.surfaceLow) : Colors.transparent,
          child: Center(child: Icon(widget.icon, size: 14,
            color: _h && widget.isClose ? Colors.white : BColors.textMuted)),
        ),
      ),
    );
  }
}

// ─── Deco Layer (GPU Metaball shader, self-contained Ticker) ──

class DecoLayer extends StatefulWidget {
  final double mouseX;
  final double mouseY;
  const DecoLayer({super.key, this.mouseX = 0.5, this.mouseY = 0.5});

  @override
  State<DecoLayer> createState() => _DecoLayerState();
}

class _DecoLayerState extends State<DecoLayer> with SingleTickerProviderStateMixin {
  FragmentShader? _shader;
  late final Ticker _ticker;
  double _smx = 0.5, _smy = 0.5;
  double _time = 0.0;

  @override
  void initState() {
    super.initState();
    FragmentProgram.fromAsset('assets/shaders/metaball.frag').then((prog) {
      if (mounted) setState(() => _shader = prog.fragmentShader());
    });
    _ticker = createTicker((_) {
      if (MediaQuery.of(context).disableAnimations) return;
      _time += 0.016;
      _smx = lerpDouble(_smx, widget.mouseX, 0.05)!;
      _smy = lerpDouble(_smy, widget.mouseY, 0.05)!;
      setState(() {});
    })..start();
  }

  @override
  void dispose() {
    _ticker.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (_shader == null) return const SizedBox.expand();
    return RepaintBoundary(
      child: SizedBox.expand(
        child: CustomPaint(
          painter: _ShaderPainter(
            shader: _shader!,
            mouseX: _smx,
            mouseY: _smy,
            time: _time,
          ),
        ),
      ),
    );
  }
}

class _ShaderPainter extends CustomPainter {
  final FragmentShader shader;
  final double mouseX, mouseY, time;

  _ShaderPainter({required this.shader, required this.mouseX, required this.mouseY, required this.time});

  @override
  void paint(Canvas canvas, Size size) {
    shader.setFloat(0, size.width);
    shader.setFloat(1, size.height);
    shader.setFloat(2, time);
    shader.setFloat(3, mouseX);
    shader.setFloat(4, mouseY);
    canvas.drawRect(
      Rect.fromLTWH(0, 0, size.width, size.height),
      Paint()..shader = shader,
    );
  }

  @override
  bool shouldRepaint(covariant _ShaderPainter old) =>
      old.mouseX != mouseX || old.mouseY != mouseY || old.time != time;
}

// ─── Left Panel (branding + atmosphere) ───────────────────────

class BLeskLeftPanel extends StatelessWidget {
  const BLeskLeftPanel({super.key});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        Stack(alignment: Alignment.center, children: [
          Container(
            width: rs(context, 400), height: rs(context, 400),
            decoration: BoxDecoration(
              gradient: RadialGradient(
                colors: [BColors.accent.withValues(alpha: 0.06), Colors.transparent],
              ),
            ),
          ),
          Image.asset('assets/logo/blesk-logo.png', width: rs(context, 160), fit: BoxFit.contain),
        ]),
        SizedBox(height: rs(context, 14)),
        Text('твой блеск.', style: TextStyle(
          fontFamily: 'Onest', fontSize: rf(context, 14),
          fontWeight: FontWeight.w400, color: Colors.white.withValues(alpha: 0.25),
        )),
        const SizedBox(height: 4),
        Text('твои правила.', style: TextStyle(
          fontFamily: 'Onest', fontSize: rf(context, 14),
          fontWeight: FontWeight.w400, color: Colors.white.withValues(alpha: 0.25),
        )),
      ]),
    );
  }
}

// ─── Glass Helpers ────────────────────────────────────────────

class BLeskGlass extends StatelessWidget {
  final Widget child;
  final double radius;
  final EdgeInsetsGeometry? padding;

  const BLeskGlass({super.key, required this.child, this.radius = 14, this.padding});

  @override
  Widget build(BuildContext context) {
    final r = rs(context, radius);
    return ClipRRect(
      borderRadius: BorderRadius.circular(r),
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 20, sigmaY: 20),
        child: Container(
          padding: padding,
          decoration: BoxDecoration(
            color: Colors.white.withValues(alpha: 0.05),
            borderRadius: BorderRadius.circular(r),
            border: Border.all(color: Colors.white.withValues(alpha: 0.08), width: 0.5),
          ),
          child: child,
        ),
      ),
    );
  }
}

class BLeskGlassInput extends StatefulWidget {
  final TextEditingController controller;
  final String hint;
  final Widget? prefix;
  final Widget? suffix;
  final String? prefixText;
  final bool obscure;
  final bool autofocus;
  final TextInputType? keyboardType;
  final List<TextInputFormatter>? formatters;
  final int? maxLength;
  final ValueChanged<String>? onChanged;
  final ValueChanged<String>? onSubmitted;

  const BLeskGlassInput({
    super.key, required this.controller, required this.hint,
    this.prefix, this.suffix, this.prefixText,
    this.obscure = false, this.autofocus = false,
    this.keyboardType, this.formatters, this.maxLength, this.onChanged,
    this.onSubmitted,
  });

  @override
  State<BLeskGlassInput> createState() => _BLeskGlassInputState();
}

class _BLeskGlassInputState extends State<BLeskGlassInput> {
  bool _focused = false;

  Widget? _buildPrefix() {
    if (widget.prefixText == null) return null;
    final empty = widget.controller.text.isEmpty;
    Widget txt = Text(widget.prefixText!, style: TextStyle(
      fontFamily: 'Onest', fontSize: rf(context, 16),
      fontWeight: FontWeight.w500, color: Colors.white,
    ));
    final noMotion = MediaQuery.of(context).disableAnimations;
    if (_focused && empty && !noMotion) {
      txt = txt.animate(onPlay: (c) => c.repeat(reverse: true))
        .fade(begin: 0.15, end: 0.4, duration: 1200.ms, curve: Curves.easeInOut);
    } else {
      txt = Opacity(opacity: 0.4, child: txt);
    }
    return Padding(
      padding: EdgeInsets.only(left: rs(context, 18)),
      child: txt,
    );
  }

  @override
  Widget build(BuildContext context) {
    return Focus(
      onFocusChange: (f) { setState(() => _focused = f); if (f) SoundEngine().play('focus'); },
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        curve: Curves.easeOut,
        decoration: BoxDecoration(
          border: Border.all(
            color: _focused ? BColors.accent.withValues(alpha: 0.25) : Colors.white.withValues(alpha: 0.06),
            width: 1,
          ),
          borderRadius: BorderRadius.circular(rs(context, 14)),
        ),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(rs(context, 14)),
          child: BackdropFilter(
            filter: ImageFilter.blur(sigmaX: 20, sigmaY: 20),
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 200),
              decoration: BoxDecoration(
                color: _focused
                    ? BColors.accent.withValues(alpha: 0.02)
                    : Colors.white.withValues(alpha: 0.04),
                borderRadius: BorderRadius.circular(rs(context, 14)),
              ),
              child: TextField(
                controller: widget.controller,
                obscureText: widget.obscure,
                autofocus: widget.autofocus,
                keyboardType: widget.keyboardType,
                inputFormatters: widget.formatters,
                maxLength: widget.maxLength,
                onChanged: widget.onChanged,
                onSubmitted: widget.onSubmitted,
                textInputAction: widget.onSubmitted != null ? TextInputAction.go : null,
                style: TextStyle(
                  fontFamily: 'Onest', fontSize: rf(context, 16),
                  fontWeight: FontWeight.w500, color: BColors.textPrimary,
                ),
                cursorColor: BColors.accent,
                decoration: InputDecoration(
                  border: InputBorder.none, counterText: '',
                  hintText: widget.hint,
                  hintStyle: TextStyle(fontFamily: 'Onest', fontSize: rf(context, 16), color: BColors.textMuted),
                  contentPadding: EdgeInsets.symmetric(
                    horizontal: rs(context, 18), vertical: rs(context, 16),
                  ),
                  prefixIcon: widget.prefix ?? _buildPrefix(),
                  prefixIconConstraints: BoxConstraints(
                    minWidth: widget.prefix != null ? 48 : 0, minHeight: 0,
                  ),
                  suffixIcon: widget.suffix,
                  suffixIconConstraints: const BoxConstraints(minWidth: 48),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

// ─── Glass CTA Button ─────────────────────────────────────────

class BLeskCtaButton extends StatefulWidget {
  final String label;
  final bool enabled;
  final VoidCallback? onTap;
  final double? width;

  const BLeskCtaButton({
    super.key, required this.label, required this.enabled, this.onTap, this.width,
  });

  @override
  State<BLeskCtaButton> createState() => _BLeskCtaButtonState();
}

class _BLeskCtaButtonState extends State<BLeskCtaButton> {
  bool _hover = false;
  bool _press = false;
  bool _focused = false;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true, label: widget.label, enabled: widget.enabled,
      child: Opacity(
        opacity: widget.enabled ? 1.0 : 0.3,
        child: IgnorePointer(
          ignoring: !widget.enabled,
          child: Focus(
            onFocusChange: (f) => setState(() => _focused = f),
            child: MouseRegion(
              cursor: SystemMouseCursors.click,
              onEnter: (_) => setState(() => _hover = true),
              onExit: (_) => setState(() => _hover = false),
              child: GestureDetector(
                onTapDown: (_) => setState(() => _press = true),
                onTapUp: (_) { setState(() => _press = false); SoundEngine().play('tap'); widget.onTap?.call(); },
                onTapCancel: () => setState(() => _press = false),
                child: AnimatedScale(
                  scale: _press ? 0.97 : 1.0,
                  duration: const Duration(milliseconds: 100),
                  child: AnimatedOpacity(
                    opacity: _press ? 0.85 : 1.0,
                    duration: const Duration(milliseconds: 100),
                    child: AnimatedContainer(
                      duration: const Duration(milliseconds: 200),
                      width: widget.width ?? double.infinity,
                      height: rs(context, 54),
                      decoration: BoxDecoration(
                        borderRadius: BorderRadius.circular(rs(context, 14)),
                        border: _focused ? Border.all(color: BColors.accent, width: 2) : null,
                        boxShadow: _hover ? [
                          BoxShadow(color: BColors.accent.withValues(alpha: 0.25), blurRadius: 24),
                        ] : [],
                      ),
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(rs(context, 14)),
                        child: BackdropFilter(
                          filter: ImageFilter.blur(sigmaX: 16, sigmaY: 16),
                          child: Stack(children: [
                            Container(decoration: BoxDecoration(
                              color: Colors.white.withValues(alpha: 0.06),
                              borderRadius: BorderRadius.circular(rs(context, 14)),
                            )),
                            Container(
                              decoration: BoxDecoration(
                                color: BColors.accent.withValues(alpha: 0.88),
                                borderRadius: BorderRadius.circular(rs(context, 14)),
                              ),
                              child: Center(child: Row(mainAxisSize: MainAxisSize.min, children: [
                                CustomPaint(size: Size(rs(context, 12), rs(context, 12)), painter: StarPainter(BColors.bg)),
                                SizedBox(width: rs(context, 8)),
                                Text(widget.label, style: TextStyle(
                                  fontFamily: 'Onest', fontSize: rf(context, 15),
                                  fontWeight: FontWeight.w700, color: BColors.bg,
                                )),
                              ])),
                            ),
                            Positioned(top: 0, left: 0, right: 0, child: Container(
                              height: 1,
                              margin: EdgeInsets.symmetric(horizontal: rs(context, 24)),
                              decoration: BoxDecoration(gradient: LinearGradient(colors: [
                                Colors.white.withValues(alpha: 0),
                                Colors.white.withValues(alpha: 0.2),
                                Colors.white.withValues(alpha: 0),
                              ])),
                            )),
                          ]),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

// ─── Hover Back Button ────────────────────────────────────────

class HoverBackButton extends StatefulWidget {
  final VoidCallback onTap;
  const HoverBackButton({super.key, required this.onTap});
  @override
  State<HoverBackButton> createState() => _HoverBackButtonState();
}

class _HoverBackButtonState extends State<HoverBackButton> {
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
          width: 40, height: 40,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(10),
            color: _h ? const Color(0x0AFFFFFF) : Colors.transparent,
          ),
          child: Center(
            child: Icon(SolarIconsOutline.altArrowLeft, size: 16,
              color: Color(_h ? 0x59FFFFFF : 0x26FFFFFF)),
          ),
        ),
      ),
    );
  }
}

// ─── Hover Link ───────────────────────────────────────────────

class HoverLink extends StatefulWidget {
  final String text;
  final VoidCallback onTap;
  final Color color;
  final double fontSize;
  final FontWeight fontWeight;

  const HoverLink({
    super.key, required this.text, required this.onTap,
    this.color = BColors.accent, this.fontSize = 13, this.fontWeight = FontWeight.w600,
  });

  @override
  State<HoverLink> createState() => _HoverLinkState();
}

class _HoverLinkState extends State<HoverLink> {
  bool _hovering = false;

  @override
  Widget build(BuildContext context) {
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setState(() => _hovering = true),
      onExit: (_) => setState(() => _hovering = false),
      child: GestureDetector(
        onTap: widget.onTap,
        child: AnimatedDefaultTextStyle(
          duration: const Duration(milliseconds: 150),
          style: TextStyle(
            fontFamily: 'Onest',
            fontSize: rf(context, widget.fontSize),
            fontWeight: widget.fontWeight,
            color: widget.color.withValues(alpha: _hovering ? 1.0 : 0.7),
            decoration: _hovering ? TextDecoration.underline : TextDecoration.none,
            decorationColor: widget.color.withValues(alpha: 0.4),
            decorationThickness: 1,
          ),
          child: Text(widget.text),
        ),
      ),
    );
  }
}
