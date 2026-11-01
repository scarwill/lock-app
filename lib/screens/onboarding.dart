import 'package:flutter/material.dart';

import '../native.dart';
import '../settings.dart';
import '../widgets/permissions.dart';
import '../widgets/sections.dart';
import 'home.dart';

class _Step {
  final String title;
  final String? subtitle;
  final Widget Function() body;
  final bool Function() canNext;

  /// Body khud scroll karti hai (jaise apps ki list).
  final bool scrolls;
  _Step(
    this.title,
    this.body, {
    this.subtitle,
    bool Function()? canNext,
    this.scrolls = false,
  }) : canNext = canNext ?? (() => true);
}

/// Pehli baar app kholne par setup: mode, lock type, apps, quiz, free time,
/// caution, permissions, test run.
class OnboardingScreen extends StatefulWidget {
  const OnboardingScreen({super.key});

  @override
  State<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends State<OnboardingScreen> {
  final _s = LockSettings();
  int _index = 0;

  final _pin1 = TextEditingController();
  final _pin2 = TextEditingController();
  bool _understood = false;
  bool _confirmed = false;
  bool _permissionsOk = false;
  bool _tested = false;

  @override
  void dispose() {
    _pin1.dispose();
    _pin2.dispose();
    super.dispose();
  }

  void _changed() => setState(() {});

  List<_Step> get _steps => [
    _Step(
      'Welcome to Quiz Lock 👋',
      _modeStep,
      subtitle: 'Who are you setting this up for?',
    ),
    if (_s.isParent)
      _Step(
        'Create a parent PIN',
        _pinStep,
        subtitle: 'You will need this PIN to change settings and to unlock your child\'s phone.',
        canNext: () => _pin1.text.length == 4 && _pin1.text == _pin2.text,
      ),
    _Step(
      'What should be locked?',
      () => LockTypeSection(settings: _s, onChanged: _changed),
    ),
    _Step(
      'Which apps do you want to lock?',
      () => AppPicker(selected: _s.lockedApps, onChanged: _changed),
      subtitle: 'Pick at least 1 app.',
      canNext: () => _s.lockedApps.isNotEmpty,
      scrolls: true,
    ),
    _Step(
      'Quiz settings',
      () => Column(
        children: [
          QuizSection(settings: _s, onChanged: _changed),
          WalkSection(settings: _s, onChanged: _changed),
        ],
      ),
    ),
    _Step(
      'Free time and skips',
      () => FreeTimeSection(settings: _s, onChanged: _changed),
    ),
    _Step('⚠️ Read carefully', _cautionStep, canNext: () => _confirmed),
    _Step(
      'Permissions',
      _permissionsStep,
      subtitle: 'Allow them one by one. The lock won\'t work without the required ones.',
      canNext: () => _permissionsOk,
    ),
    _Step('Try it out', _testStep),
  ];

  Widget _modeStep() => Column(
    children: [
      ChoiceCard(
        icon: Icons.school,
        title: 'Student (for myself)',
        subtitle: 'Cut down phone use while studying. Loosening settings takes 24 hours.',
        selected: _s.mode == LockSettings.modeStudent,
        onTap: () => setState(() => _s.mode = LockSettings.modeStudent),
      ),
      const SizedBox(height: 8),
      ChoiceCard(
        icon: Icons.family_restroom,
        title: 'Parent (for my child)',
        subtitle: 'Quiz before your child\'s games and apps. Settings change only with your PIN.',
        selected: _s.mode == LockSettings.modeParent,
        onTap: () => setState(() => _s.mode = LockSettings.modeParent),
      ),
    ],
  );

  Widget _pinStep() => Column(
    children: [
      TextField(
        controller: _pin1,
        obscureText: true,
        keyboardType: TextInputType.number,
        maxLength: 4,
        decoration: const InputDecoration(
          labelText: '4 digit PIN',
          border: OutlineInputBorder(),
        ),
        onChanged: (_) => _changed(),
      ),
      const SizedBox(height: 8),
      TextField(
        controller: _pin2,
        obscureText: true,
        keyboardType: TextInputType.number,
        maxLength: 4,
        decoration: InputDecoration(
          labelText: 'Enter PIN again',
          border: const OutlineInputBorder(),
          errorText: _pin2.text.length == 4 && _pin1.text != _pin2.text
              ? 'PINs don\'t match'
              : null,
        ),
        onChanged: (_) => _changed(),
      ),
    ],
  );

  Widget _cautionStep() {
    final loosen = _s.isParent
        ? 'After setup, settings can only be changed with the parent PIN.'
        : 'After setup, loosening the lock (removing apps, turning it off) takes effect after 24 hours.';
    final points = [
      if (_s.locksScreen)
        'Turning the phone off or restarting it won\'t get you out of the quiz. You\'ll have to answer as soon as the phone is unlocked.'
      else
        'Turning the phone off or restarting it won\'t remove the lock. You\'ll have to answer whenever you open a locked app.',
      'Emergency calls and incoming calls always work.',
      if (_s.isParent) 'While the lock is on, Quiz Lock can\'t be uninstalled or switched off without your PIN.',
      loosen,
    ];
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (final p in points)
          Padding(
            padding: const EdgeInsets.only(bottom: 14),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('•  ', style: TextStyle(fontSize: 18)),
                Expanded(
                  child: Text(
                    p,
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                ),
              ],
            ),
          ),
        CheckboxListTile(
          contentPadding: EdgeInsets.zero,
          value: _understood,
          onChanged: (v) => setState(() => _understood = v ?? false),
          title: const Text('I understand'),
        ),
        const SizedBox(height: 12),
        if (_confirmed)
          const ListTile(
            leading: Icon(Icons.check_circle, color: Colors.green),
            title: Text('Confirmed. Tap Next.'),
          )
        else
          HoldButton(
            label: 'Yes, start the lock (hold 3 sec)',
            enabled: _understood,
            onDone: () => setState(() => _confirmed = true),
          ),
      ],
    );
  }

  Widget _permissionsStep() => PermissionsList(
    settings: _s,
    onRequiredChanged: (ok) {
      if (ok != _permissionsOk) setState(() => _permissionsOk = ok);
    },
  );

  Widget _testStep() => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      const Text(
        'Run a demo quiz. Give one wrong answer on purpose to see the penalty timer too.',
        style: TextStyle(fontSize: 16),
      ),
      const SizedBox(height: 16),
      OutlinedButton.icon(
        onPressed: () async {
          await Native.saveSettings(_s);
          await Native.testQuiz();
          setState(() => _tested = true);
        },
        icon: const Icon(Icons.play_arrow),
        label: Text(_tested ? 'Test again' : 'Start test quiz'),
      ),
    ],
  );

  Future<void> _next() async {
    final steps = _steps;
    if (_index < steps.length - 1) {
      setState(() => _index++);
      return;
    }
    if (_s.isParent) await Native.setPin(_pin1.text);
    _s
      ..onboarded = true
      ..enabled = true;
    await Native.saveSettings(_s);
    await Native.startLock();
    if (!mounted) return;
    Navigator.of(context)
        .pushReplacement(MaterialPageRoute(builder: (_) => const HomeScreen()));
  }

  @override
  Widget build(BuildContext context) {
    final steps = _steps;
    final step = steps[_index];
    final last = _index == steps.length - 1;
    final text = Theme.of(context).textTheme;
    return PopScope(
      canPop: _index == 0,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) setState(() => _index--);
      },
      child: Scaffold(
        body: SafeArea(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              LinearProgressIndicator(value: (_index + 1) / steps.length),
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 20, 16, 12),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(step.title, style: text.headlineSmall),
                    if (step.subtitle != null) ...[
                      const SizedBox(height: 4),
                      Text(step.subtitle!, style: text.bodyMedium),
                    ],
                  ],
                ),
              ),
              Expanded(
                child: step.scrolls
                    ? step.body()
                    : SingleChildScrollView(
                        padding: const EdgeInsets.symmetric(horizontal: 16),
                        child: step.body(),
                      ),
              ),
              Padding(
                padding: const EdgeInsets.all(16),
                child: Row(
                  children: [
                    if (_index > 0)
                      TextButton(
                        onPressed: () => setState(() => _index--),
                        child: const Text('Back'),
                      ),
                    const Spacer(),
                    FilledButton(
                      onPressed: step.canNext() ? _next : null,
                      child: Text(last ? 'Finish setup ✅' : 'Next'),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
