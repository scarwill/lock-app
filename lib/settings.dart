import 'dart:convert';

/// Lock ki saari settings. Android side (Kotlin) bhi yahi JSON padhta hai,
/// isliye keys badalni ho to `Settings.kt` mein bhi badalna.
class LockSettings {
  static const modeStudent = 'student';
  static const modeParent = 'parent';
  static const lockApps = 'apps';
  static const lockAppsAndScreen = 'apps_lockscreen';

  bool onboarded;
  bool enabled;
  String mode;
  String lockType;
  List<String> lockedApps;
  String category;
  int questionsPerUnlock;
  int penaltySeconds;
  int cooldownMinutes;
  int dailySkips;

  LockSettings({
    this.onboarded = false,
    this.enabled = false,
    this.mode = modeStudent,
    this.lockType = lockApps,
    List<String>? lockedApps,
    this.category = 'mixed',
    this.questionsPerUnlock = 1,
    this.penaltySeconds = 15,
    this.cooldownMinutes = 30,
    this.dailySkips = 1,
  }) : lockedApps = lockedApps ?? [];

  bool get isParent => mode == modeParent;
  bool get locksScreen => lockType == lockAppsAndScreen;

  LockSettings copy() => LockSettings.fromJson(toJson());

  Map<String, dynamic> toJson() => {
        'onboarded': onboarded,
        'enabled': enabled,
        'mode': mode,
        'lockType': lockType,
        'lockedApps': lockedApps,
        'category': category,
        'questionsPerUnlock': questionsPerUnlock,
        'penaltySeconds': penaltySeconds,
        'cooldownMinutes': cooldownMinutes,
        'dailySkips': dailySkips,
      };

  factory LockSettings.fromJson(Map<String, dynamic> j) => LockSettings(
        onboarded: j['onboarded'] as bool? ?? false,
        enabled: j['enabled'] as bool? ?? false,
        mode: j['mode'] as String? ?? modeStudent,
        lockType: j['lockType'] as String? ?? lockApps,
        lockedApps: (j['lockedApps'] as List?)?.cast<String>().toList() ?? [],
        category: j['category'] as String? ?? 'mixed',
        questionsPerUnlock: j['questionsPerUnlock'] as int? ?? 1,
        penaltySeconds: j['penaltySeconds'] as int? ?? 15,
        cooldownMinutes: j['cooldownMinutes'] as int? ?? 30,
        dailySkips: j['dailySkips'] as int? ?? 1,
      );

  String toJsonString() => jsonEncode(toJson());
  factory LockSettings.fromJsonString(String s) =>
      LockSettings.fromJson(jsonDecode(s) as Map<String, dynamic>);

  /// `old` ke muqable ye settings kahan dheeli hain. Khaali list = koi
  /// dheela change nahi, turant lagu ho sakta hai.
  List<String> looserThan(LockSettings old) {
    final reasons = <String>[];
    if (old.enabled && !enabled) reasons.add('Turning the lock off');
    final removed = old.lockedApps.where((p) => !lockedApps.contains(p));
    if (removed.isNotEmpty) reasons.add('Unlocking ${removed.length} app(s)');
    if (old.locksScreen && !locksScreen) reasons.add('Removing the lock screen quiz');
    if (questionsPerUnlock < old.questionsPerUnlock) reasons.add('Fewer questions');
    if (penaltySeconds < old.penaltySeconds) reasons.add('Shorter penalty');
    if (cooldownMinutes > old.cooldownMinutes) reasons.add('Longer free time');
    if (dailySkips > old.dailySkips) reasons.add('More skips');
    return reasons;
  }
}

class Category {
  final String id;
  final String label;
  const Category(this.id, this.label);
}

const categories = [
  Category('mixed', 'Mixed'),
  Category('maths', 'Maths'),
  Category('science', 'Science'),
  Category('english', 'English'),
  Category('gk', 'General Knowledge'),
];
