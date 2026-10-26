import 'package:flutter/services.dart';

import 'settings.dart';

class InstalledApp {
  final String package;
  final String label;
  final Uint8List? icon;
  const InstalledApp(this.package, this.label, this.icon);
}

class PendingChange {
  final LockSettings settings;
  final DateTime applyAt;
  const PendingChange(this.settings, this.applyAt);
}

class LockStatus {
  final DateTime? freeUntil;
  final int todayCorrect;
  final int todayWrong;
  final int skipsLeft;
  final bool running;
  const LockStatus(this.freeUntil, this.todayCorrect, this.todayWrong,
      this.skipsLeft, this.running);
}

/// Android (Kotlin) side se baat karne ka ek hi rasta. Lock, overlay quiz aur
/// settings ka asli store Kotlin mein hai, taki app band hone par bhi chale.
class Native {
  static const _ch = MethodChannel('quizlock/native');

  static Future<LockSettings?> loadSettings() async {
    final s = await _ch.invokeMethod<String>('getSettings');
    return s == null ? null : LockSettings.fromJsonString(s);
  }

  static Future<void> saveSettings(LockSettings s) =>
      _ch.invokeMethod('saveSettings', s.toJsonString());

  static Future<PendingChange?> pending() async {
    final m = await _ch.invokeMapMethod<String, dynamic>('getPending');
    if (m == null) return null;
    return PendingChange(LockSettings.fromJsonString(m['json'] as String),
        DateTime.fromMillisecondsSinceEpoch(m['applyAt'] as int));
  }

  static Future<void> setPending(LockSettings s, DateTime applyAt) =>
      _ch.invokeMethod('setPending', {
        'json': s.toJsonString(),
        'applyAt': applyAt.millisecondsSinceEpoch,
      });

  static Future<void> cancelPending() => _ch.invokeMethod('cancelPending');

  static Future<List<InstalledApp>> installedApps() async {
    final list = await _ch.invokeListMethod<Map>('installedApps') ?? [];
    return list
        .map((m) => InstalledApp(m['package'] as String, m['label'] as String,
            m['icon'] as Uint8List?))
        .toList();
  }

  /// usage, overlay, battery, notifications, admin -> granted?
  static Future<Map<String, bool>> permissions() async =>
      await _ch.invokeMapMethod<String, bool>('permissions') ?? {};

  static Future<void> openPermission(String name) =>
      _ch.invokeMethod('openPermission', name);

  static Future<void> startLock() => _ch.invokeMethod('startLock');
  static Future<void> stopLock() => _ch.invokeMethod('stopLock');
  static Future<void> testQuiz() => _ch.invokeMethod('testQuiz');

  static Future<void> setPin(String pin) => _ch.invokeMethod('setPin', pin);
  static Future<bool> checkPin(String pin) async =>
      await _ch.invokeMethod<bool>('checkPin', pin) ?? false;

  static Future<LockStatus> status() async {
    final m = await _ch.invokeMapMethod<String, dynamic>('status') ?? {};
    final until = m['cooldownUntil'] as int? ?? 0;
    return LockStatus(
      until > DateTime.now().millisecondsSinceEpoch
          ? DateTime.fromMillisecondsSinceEpoch(until)
          : null,
      m['todayCorrect'] as int? ?? 0,
      m['todayWrong'] as int? ?? 0,
      m['skipsLeft'] as int? ?? 0,
      m['running'] as bool? ?? false,
    );
  }
}
