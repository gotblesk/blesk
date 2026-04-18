import 'package:desktop_drop/desktop_drop.dart';
import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:solar_icons/solar_icons.dart';

import '../shared/theme.dart';

/// Global toggle — flips when OS drag enters content area or via
/// Ctrl+Shift+D demo shortcut.
final ValueNotifier<bool> dropOverlayActive = ValueNotifier(false);

/// Accumulates last drop payload (filenames) for feedback — toast / attach.
final ValueNotifier<List<String>> droppedFiles = ValueNotifier(const []);

/// Drag & drop file overlay — shown when files are dragged over the content area.
/// Wraps content with DropTarget from desktop_drop plugin.
class DropOverlay extends StatelessWidget {
  final Widget child;
  const DropOverlay({super.key, required this.child});

  @override
  Widget build(BuildContext context) {
    return DropTarget(
      onDragEntered: (_) => dropOverlayActive.value = true,
      onDragExited: (_) => dropOverlayActive.value = false,
      onDragDone: (details) {
        dropOverlayActive.value = false;
        droppedFiles.value = details.files.map((f) => f.name).toList();
      },
      child: Stack(children: [
        child,
        ValueListenableBuilder<bool>(
          valueListenable: dropOverlayActive,
          builder: (_, on, _) => on
              ? Positioned.fill(
                  child: _DropZone(
                    onDismiss: () => dropOverlayActive.value = false,
                  ),
                )
              : const SizedBox.shrink(),
        ),
      ]),
    );
  }
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
