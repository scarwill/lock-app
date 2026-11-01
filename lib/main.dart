import 'package:flutter/material.dart';

import 'native.dart';
import 'screens/home.dart';
import 'screens/onboarding.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final settings = await Native.loadSettings();
  runApp(QuizLockApp(onboarded: settings?.onboarded ?? false));
}

class QuizLockApp extends StatelessWidget {
  final bool onboarded;
  const QuizLockApp({super.key, required this.onboarded});

  @override
  Widget build(BuildContext context) {
    ThemeData theme(Brightness b) => ThemeData(
      colorScheme: ColorScheme.fromSeed(
        seedColor: const Color(0xFF6366F1),
        brightness: b,
      ),
    );
    return MaterialApp(
      title: 'Quiz Lock',
      debugShowCheckedModeBanner: false,
      theme: theme(Brightness.light),
      darkTheme: theme(Brightness.dark),
      home: onboarded ? const HomeScreen() : const OnboardingScreen(),
    );
  }
}
