import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_animate/flutter_animate.dart';

import 'theme.dart';

/// Slide-over panel that enters from the right edge.
/// Used for: user profile, own profile editor.
class SlideOver extends StatelessWidget {
  final Widget child;
  final VoidCallback onClose;
  final bool showBackdrop;
  final String? title;

  const SlideOver({
    super.key,
    required this.child,
    required this.onClose,
    this.showBackdrop = false,
    this.title,
  });

  @override
  Widget build(BuildContext context) {
    return KeyboardListener(
      focusNode: FocusNode()..requestFocus(),
      onKeyEvent: (e) {
        if (e is KeyDownEvent && e.logicalKey == LogicalKeyboardKey.escape) onClose();
      },
      child: Stack(children: [
        // Backdrop
        if (showBackdrop)
          Positioned.fill(
            child: GestureDetector(
              onTap: onClose,
              child: Container(color: Colors.black.withValues(alpha: 0.2)),
            ),
          ).animate().fade(begin: 0, duration: 200.ms),

        // Panel
        Positioned(
          right: 0, top: 0, bottom: 0,
          child: ClipRRect(
            child: BackdropFilter(
              filter: ImageFilter.blur(sigmaX: 40, sigmaY: 40),
              child: Container(
                width: 360,
                decoration: BoxDecoration(
                  color: const Color(0xF50e0e12),
                  border: const Border(left: BorderSide(color: Color(0x0FFFFFFF))),
                  boxShadow: const [BoxShadow(color: Color(0x66000000), blurRadius: 48, offset: Offset(-16, 0))],
                ),
                child: Column(children: [
                  // Header
                  if (title != null)
                    Container(
                      height: 52,
                      padding: const EdgeInsets.symmetric(horizontal: 20),
                      decoration: const BoxDecoration(
                        border: Border(bottom: BorderSide(color: Color(0x0FFFFFFF))),
                      ),
                      child: Row(children: [
                        Text(title!, style: TextStyle(
                          fontFamily: 'Nekst', fontSize: rf(context, 16),
                          fontWeight: FontWeight.w600, color: BColors.textPrimary,
                        )),
                        const Spacer(),
                        _CloseBtn(onTap: onClose),
                      ]),
                    )
                  else
                    // Close button only (for profile view)
                    Align(
                      alignment: Alignment.topRight,
                      child: Padding(
                        padding: const EdgeInsets.all(16),
                        child: _CloseBtn(onTap: onClose),
                      ),
                    ),
                  // Content
                  Expanded(
                    child: SingleChildScrollView(
                      padding: const EdgeInsets.symmetric(horizontal: 24),
                      child: child,
                    ),
                  ),
                ]),
              ),
            ),
          ),
        ).animate()
            .slideX(begin: 1.0, duration: 280.ms, curve: Curves.easeOutCubic)
            .fade(begin: 0, duration: 280.ms),
      ]),
    );
  }
}

class _CloseBtn extends StatefulWidget {
  final VoidCallback onTap;
  const _CloseBtn({required this.onTap});
  @override
  State<_CloseBtn> createState() => _CloseBtnState();
}

class _CloseBtnState extends State<_CloseBtn> {
  bool _h = false;
  @override
  Widget build(BuildContext context) {
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setState(() => _h = true),
      onExit: (_) => setState(() => _h = false),
      child: GestureDetector(
        onTap: widget.onTap,
        child: SizedBox(width: 28, height: 28,
          child: Center(child: Icon(Icons.close, size: 18,
            color: _h ? BColors.textSecondary : BColors.textMuted))),
      ),
    );
  }
}
