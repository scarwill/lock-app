import 'dart:convert';

import 'package:flutter/services.dart';

import 'settings.dart';

class InstalledApp {
  final String package;
  final String label;
  final Uint8List? icon;
  final bool isGame;
  const InstalledApp(
    this.package,
    this.label,
    this.icon, {
    this.isGame = false,
  });
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
  final int walksLeft;

  /// Service sach mein chal rahi hai (sirf setting on hona kaafi nahi).
  final bool running;

  /// Pichhle 3 din mein phone ne lock kitni baar band kiya.
  final int kills;
  final bool hasPin;
  const LockStatus(
    this.freeUntil,
    this.todayCorrect,
    this.todayWrong,
    this.skipsLeft,
    this.walksLeft,
    this.running,
    this.kills,
    this.hasPin,
  );
}

class DeviceInfo {
  /// xiaomi, realme, oppo, oneplus, vivo, samsung, huawei, asus ya other.
  final String brand;
  final int sdk;
  final bool stepSensor;
  final bool fingerprint;

  /// Uninstall protection (Accessibility) is build mein hai? Seedhe APK mein nahi
  /// hoti, kyunki Play Protect aise APK ka install rok deta hai.
  final bool guard;
  const DeviceInfo(
    this.brand,
    this.sdk,
    this.stepSensor,
    this.fingerprint, {
    this.guard = false,
  });

  /// Ye phone background mein apps zyada band karte hain.
  bool get aggressive => const {
    'xiaomi',
    'realme',
    'oppo',
    'oneplus',
    'vivo',
    'huawei',
    'asus',
  }.contains(brand);
}

class KidsState {
  final List<String> apps;
  final bool on;
  final DateTime? freeUntil;
  const KidsState(this.apps, this.on, this.freeUntil);
}

/// User ka apna banaya sawal. Format wahi jo assets/questions.json mein hai.
class MyQuestion {
  final String id;
  final String question;
  final List<String> options;
  final int answer;
  final String explanation;
  final DateTime addedAt;

  const MyQuestion({
    required this.id,
    required this.question,
    required this.options,
    required this.answer,
    this.explanation = '',
    required this.addedAt,
  });

  static const freeLimit = 25;

  /// Galti ho to kya galat hai, warna null.
  static String? validate(String question, List<String> options, int? answer) {
    if (question.trim().isEmpty) return 'Write the question';
    final filled = options.map((o) => o.trim()).toList();
    if (filled.any((o) => o.isEmpty)) return 'Fill all 4 options';
    if (filled.map((o) => o.toLowerCase()).toSet().length != filled.length) {
      return 'Options must be different';
    }
    if (answer == null || answer < 0 || answer >= filled.length) {
      return 'Choose the correct answer';
    }
    return null;
  }

  Map<String, dynamic> toJson() => {
    'id': id,
    'category': 'mine',
    'question': question,
    'options': options,
    'answer': answer,
    'explanation': explanation,
    'addedAt': addedAt.millisecondsSinceEpoch,
  };

  factory MyQuestion.fromJson(Map<String, dynamic> j) => MyQuestion(
    id: j['id'] as String,
    question: j['question'] as String,
    options: (j['options'] as List).cast<String>(),
    answer: j['answer'] as int,
    explanation: j['explanation'] as String? ?? '',
    addedAt: DateTime.fromMillisecondsSinceEpoch(j['addedAt'] as int? ?? 0),
  );
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
    return PendingChange(
      LockSettings.fromJsonString(m['json'] as String),
      DateTime.fromMillisecondsSinceEpoch(m['applyAt'] as int),
    );
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
        .map(
          (m) => InstalledApp(
            m['package'] as String,
            m['label'] as String,
            m['icon'] as Uint8List?,
            isGame: m['isGame'] as bool? ?? false,
          ),
        )
        .toList();
  }

  /// usage, overlay, battery, notifications, admin, accessibility, activity -> granted?
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
      m['walksLeft'] as int? ?? 0,
      m['running'] as bool? ?? false,
      m['kills'] as int? ?? 0,
      m['hasPin'] as bool? ?? false,
    );
  }

  static Future<void> clearKills() => _ch.invokeMethod('clearKills');

  static Future<DeviceInfo> deviceInfo() async {
    final m = await _ch.invokeMapMethod<String, dynamic>('deviceInfo') ?? {};
    return DeviceInfo(
      m['brand'] as String? ?? 'other',
      m['sdk'] as int? ?? 0,
      m['stepSensor'] as bool? ?? false,
      m['fingerprint'] as bool? ?? false,
      guard: m['guard'] as bool? ?? false,
    );
  }

  /// Wo steps jo Android check nahi kar sakta (autostart, recents lock...), user ne kiye ya nahi.
  static Future<Map<String, bool>> flags() async =>
      await _ch.invokeMapMethod<String, bool>('flags') ?? {};

  static Future<void> setFlag(String name, bool value) =>
      _ch.invokeMethod('setFlag', {'name': name, 'value': value});

  static Future<List<MyQuestion>> myQuestions() async {
    final s = await _ch.invokeMethod<String>('getMyQuestions') ?? '[]';
    return (jsonDecode(s) as List)
        .map((e) => MyQuestion.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  static Future<void> saveMyQuestions(List<MyQuestion> list) =>
      _ch.invokeMethod(
        'saveMyQuestions',
        jsonEncode(list.map((q) => q.toJson()).toList()),
      );

  /// Abhi sirf testing switch. Play billing aane par yahi asli kharid batayega.
  static Future<bool> premium() async =>
      await _ch.invokeMethod<bool>('getPremium') ?? false;

  static Future<void> setPremium(bool v) => _ch.invokeMethod('setPremium', v);

  static Future<KidsState> kids() async {
    final m = await _ch.invokeMapMethod<String, dynamic>('getKids') ?? {};
    final until = m['freeUntil'] as int? ?? 0;
    return KidsState(
      (m['apps'] as List?)?.cast<String>() ?? [],
      m['on'] as bool? ?? false,
      until > DateTime.now().millisecondsSinceEpoch
          ? DateTime.fromMillisecondsSinceEpoch(until)
          : null,
    );
  }

  static Future<void> setKidsApps(List<String> apps) =>
      _ch.invokeMethod('setKidsApps', apps);

  static Future<void> setKidsMode(bool on) =>
      _ch.invokeMethod('setKidsMode', on);

  /// Notification tile se app khula to kaunsi screen kholni hai ("kids"), ek hi baar.
  static Future<String?> takeOpenRequest() =>
      _ch.invokeMethod<String>('takeOpenRequest');
}
