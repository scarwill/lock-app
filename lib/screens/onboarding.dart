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
  _Step(this.title, this.body,
      {this.subtitle, bool Function()? canNext, this.scrolls = false})
      : canNext = canNext ?? (() => true);
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
        _Step('Quiz Lock mein swagat hai 👋', _modeStep,
            subtitle: 'Ye app kiske liye set kar rahe ho?'),
        if (_s.isParent)
          _Step('Parent PIN banao', _pinStep,
              subtitle:
                  'Ye PIN settings badalne aur bachche ka phone kholne mein lagega.',
              canNext: () =>
                  _pin1.text.length == 4 && _pin1.text == _pin2.text),
        _Step('Lock kaisa chahiye?', () => LockTypeSection(
                settings: _s, onChanged: _changed)),
        _Step('Kaunse apps lock karne hain?',
            () => AppPicker(selected: _s.lockedApps, onChanged: _changed),
            subtitle: 'Kam se kam 1 app chuno.',
            canNext: () => _s.lockedApps.isNotEmpty,
            scrolls: true),
        _Step('Quiz settings',
            () => QuizSection(settings: _s, onChanged: _changed)),
        _Step('Free time aur skip',
            () => FreeTimeSection(settings: _s, onChanged: _changed)),
        _Step('⚠️ Dhyan se padho', _cautionStep, canNext: () => _confirmed),
        _Step('Permissions', _permissionsStep,
            subtitle: 'Ek-ek karke allow karo. Zaroori wale ke bina lock nahi chalega.',
            canNext: () => _permissionsOk),
        _Step('Test karke dekho', _testStep),
      ];

  Widget _modeStep() => Column(
        children: [
          ChoiceCard(
            icon: Icons.school,
            title: 'Student (khud ke liye)',
            subtitle:
                'Padhai ke beech phone ki aadat kam karni hai. Settings dheeli karne par 24 ghante wait.',
            selected: _s.mode == LockSettings.modeStudent,
            onTap: () => setState(() => _s.mode = LockSettings.modeStudent),
          ),
          const SizedBox(height: 8),
          ChoiceCard(
            icon: Icons.family_restroom,
            title: 'Parent (bachche ke liye)',
            subtitle:
                'Bachche ke games aur apps par quiz. Settings sirf PIN se badlengi.',
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
                labelText: '4 digit PIN', border: OutlineInputBorder()),
            onChanged: (_) => _changed(),
          ),
          const SizedBox(height: 8),
          TextField(
            controller: _pin2,
            obscureText: true,
            keyboardType: TextInputType.number,
            maxLength: 4,
            decoration: InputDecoration(
              labelText: 'PIN dobara dalo',
              border: const OutlineInputBorder(),
              errorText: _pin2.text.length == 4 && _pin1.text != _pin2.text
                  ? 'Dono PIN alag hain'
                  : null,
            ),
            onChanged: (_) => _changed(),
          ),
        ],
      );

  Widget _cautionStep() {
    final loosen = _s.isParent
        ? 'Setup ke baad settings sirf Parent PIN se badlengi.'
        : 'Setup ke baad lock dheela karna (app hatana, lock band karna) 24 ghante baad lagu hoga.';
    final points = [
      if (_s.locksScreen)
        'Phone band karke ya restart karke bhi quiz se nahi bach sakte. Phone khulte hi quiz dena padega.'
      else
        'Phone band karke ya restart karke bhi lock nahi hatega. Chune hue apps kholte hi quiz dena padega.',
      'Emergency call aur aane wali call hamesha chalegi.',
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
                    child: Text(p,
                        style: Theme.of(context).textTheme.titleMedium)),
              ],
            ),
          ),
        CheckboxListTile(
          contentPadding: EdgeInsets.zero,
          value: _understood,
          onChanged: (v) => setState(() => _understood = v ?? false),
          title: const Text('Main samajh gaya/gayi'),
        ),
        const SizedBox(height: 12),
        if (_confirmed)
          const ListTile(
            leading: Icon(Icons.check_circle, color: Colors.green),
            title: Text('Pakka ho gaya. Aage badho.'),
          )
        else
          HoldButton(
            label: 'Haan, Lock Shuru Karo (3 sec dabao)',
            enabled: _understood,
            onDone: () => setState(() => _confirmed = true),
          ),
      ],
    );
  }

  Widget _permissionsStep() => PermissionsList(
        parentMode: _s.isParent,
        onRequiredChanged: (ok) {
          if (ok != _permissionsOk) setState(() => _permissionsOk = ok);
        },
      );

  Widget _testStep() => Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Ek demo quiz chala ke dekho. Jaan-boojh ke ek galat jawab bhi do, taki penalty timer bhi dikh jaye.',
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
            label: Text(_tested ? 'Dobara test karo' : 'Test quiz chalao'),
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
    Navigator.of(context).pushReplacement(
        MaterialPageRoute(builder: (_) => const HomeScreen()));
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
                        child: const Text('Peeche'),
                      ),
                    const Spacer(),
                    FilledButton(
                      onPressed: step.canNext() ? _next : null,
                      child: Text(last ? 'Setup complete ✅' : 'Aage'),
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
