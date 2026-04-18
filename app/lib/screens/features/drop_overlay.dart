import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:solar_icons/solar_icons.dart';

import '../shared/theme.dart';

/// Drag & drop file overlay — shown when files are dragged over the content area.
/// Usage: wrap content in DropOverlay, it shows/hides automatically.
class DropOverlay extends StatefulWidget {
  final Widget child;
  const DropOverlay({super.key, required this.child});
  @override
  State<DropOverlay> createState() => _DropOverlayState();
}

class _DropOverlayState extends State<DropOverlay> {
  bool _dragging = false;

  @override
  Widget build(BuildContext context) {
    // Note: real drag detection requires platform channel or desktop_drop package.
    // This is a visual placeholder that can be triggered manually for demo.
    return Stack(children: [
      widget.child,
      if (_dragging)
        Positioned.fill(
          child: _DropZone(onDismiss: () => setState(() => _dragging = false)),
        ),
    ]);
  }

  /// Call this externally to simulate drag enter (for demo/testing)
  void showDrop() => setState(() => _dragging = true);
}

class _DropZone extends StatelessWidget {
  final VoidCallback onDismiss;
  const _DropZone({required this.onDismiss});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onDismiss,
      child: Container(
        decoration: BoxDecoration(
          color: BColors.accent.withValues(alpha: 0.04),
          border: Border.all(
            color: BColors.accent.withValues(alpha: 0.25),
            width: 2,
            strokeAlign: BorderSide.strokeAlignInside,
          ),
          borderRadius: BorderRadius.circular(16),
        ),
        margin: const EdgeInsets.all(16),
        child: Center(
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            Icon(SolarIconsOutline.uploadMinimalistic, size: 64,
              color: BColors.accent.withValues(alpha: 0.3)),
            const SizedBox(height: 16),
            Text('бросьте чтобы прикрепить', style: TextStyle(
              fontFamily: 'Onest', fontSize: 16, fontWeight: FontWeight.w500,
              color: BColors.accent.withValues(alpha: 0.5),
            )),
            const SizedBox(height: 6),
            Text('несколько файлов поддерживается', style: TextStyle(
              fontFamily: 'Onest', fontSize: 12,
              color: BColors.textMuted.withValues(alpha: 0.6),
            )),
          ]),
        ),
      ).animate().fadeIn(duration: 150.ms),
    );
  }
}
