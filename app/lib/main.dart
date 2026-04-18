import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:bitsdojo_window/bitsdojo_window.dart';
import 'package:blesk/app.dart';
void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  // SoundEngine disabled — audio backend shows DEMO watermark
  runApp(const ProviderScope(child: BleskApp()));

  doWhenWindowReady(() {
    final win = appWindow;
    win.minSize = const Size(900, 600);
    win.size = const Size(1280, 800);
    win.alignment = Alignment.center;
    win.title = 'blesk';
    win.show();
  });
}
