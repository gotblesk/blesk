import 'package:flutter/material.dart';
import 'package:blesk/core/theme/app_theme.dart';
import 'package:blesk/screens/splash_screen.dart';

class BleskApp extends StatelessWidget {
  const BleskApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'blesk',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.dark(),
      home: const SplashScreen(),
    );
  }
}
