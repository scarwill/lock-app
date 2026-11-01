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
    walkEnabled: false,
    walkSteps: 100,
  );

  test('tight karna ya same rakhna dheela nahi hai', () {
    final s = base()
      ..lockedApps.add('c')
      ..questionsPerUnlock = 5
      ..penaltySeconds = 30
      ..cooldownMinutes = 15
      ..dailySkips = 0
      ..category = 'maths'
      ..examName = 'NEET 2027'
      ..examDate = DateTime(2027, 5, 2);
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
      ..dailySkips = 2
      ..walkEnabled = true;
    expect(s.looserThan(base()), hasLength(8));
  });

  test('kam kadam dheela hai, zyada kadam nahi', () {
    final on = base()..walkEnabled = true;
    expect((on.copy()..walkSteps = 50).looserThan(on), ['Fewer steps']);
    expect((on.copy()..walkSteps = 200).looserThan(on), isEmpty);
    expect((on.copy()..walkEnabled = false).looserThan(on), isEmpty);
  });

  test('JSON round trip', () {
    final s = base()
      ..mode = LockSettings.modeParent
      ..examName = 'JEE Main'
      ..examDate = DateTime(2027, 1, 20);
    expect(LockSettings.fromJsonString(s.toJsonString()).toJson(), s.toJson());
  });
}
