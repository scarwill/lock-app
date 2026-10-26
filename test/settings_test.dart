import 'package:flutter_test/flutter_test.dart';
import 'package:lock_app/settings.dart';

void main() {
  LockSettings base() => LockSettings(
        enabled: true,
        lockType: LockSettings.lockAppsAndScreen,
        lockedApps: ['a', 'b'],
        questionsPerUnlock: 3,
        penaltySeconds: 15,
        cooldownMinutes: 30,
        dailySkips: 1,
      );

  test('tight karna ya same rakhna dheela nahi hai', () {
    final s = base()
      ..lockedApps.add('c')
      ..questionsPerUnlock = 5
      ..penaltySeconds = 30
      ..cooldownMinutes = 15
      ..dailySkips = 0
      ..category = 'maths';
    expect(s.looserThan(base()), isEmpty);
  });

  test('har dheela change pakda jata hai', () {
    final s = base()
      ..enabled = false
      ..lockedApps.remove('a')
      ..lockType = LockSettings.lockApps
      ..questionsPerUnlock = 1
      ..penaltySeconds = 10
      ..cooldownMinutes = 60
      ..dailySkips = 2;
    expect(s.looserThan(base()), hasLength(7));
  });

  test('JSON round trip', () {
    final s = base()..mode = LockSettings.modeParent;
    expect(LockSettings.fromJsonString(s.toJsonString()).toJson(), s.toJson());
  });
}
