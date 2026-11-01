import 'package:flutter/material.dart';

import '../native.dart';
import '../settings.dart';
import '../widgets/sections.dart';
import 'kids_lock.dart';
import 'my_questions.dart';
import 'phone_setup.dart';
import 'settings_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> with WidgetsBindingObserver {
  LockSettings? _settings;
  PendingChange? _pending;
  LockStatus? _status;
  KidsState? _kids;
  bool _guard = false;
  Map<String, bool> _perms = {};

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _refresh();
    _handleOpenRequest();
  }

  /// Notification tile se "Kids Lock" kholne ko kaha gaya ho.
  Future<void> _handleOpenRequest() async {
    final r = await Native.takeOpenRequest();
    if (r == 'kids' && mounted) _push(const KidsLockScreen());
  }

  Future<void> _push(Widget screen) async {
    await Navigator.of(context).push(MaterialPageRoute(builder: (_) => screen));
    _refresh();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _refresh();
      _handleOpenRequest();
    }
  }

  Future<void> _refresh() async {
    final s = await Native.loadSettings();
    final kids = await Native.kids();
    // Service kisi wajah se ruk gayi ho to dobara chalu karo.
    final shouldRun = (s != null && s.enabled) || kids.on;
    if (shouldRun) await Native.startLock();
    final p = await Native.pending();
    var st = await Native.status();
    if (shouldRun && !st.running) {
      // Service shuru hone mein ek pal lagta hai.
      await Future<void>.delayed(const Duration(milliseconds: 800));
      st = await Native.status();
    }
    final perms = await Native.permissions();
    final device = await Native.deviceInfo();
    if (!mounted) return;
    setState(() {
      _guard = device.guard;
      _settings = s;
      _pending = p;
      _status = st;
      _kids = kids;
      _perms = perms;
    });
  }

  Future<void> _openSettings() async {
    final s = _settings;
    if (s == null) return;
    if (s.isParent && !await askPin(context)) return;
    if (!mounted) return;
    await Navigator.of(context)
        .push(MaterialPageRoute(builder: (_) => SettingsScreen(current: s)));
    _refresh();
  }

  String _time(DateTime t) {
    final h = t.hour % 12 == 0 ? 12 : t.hour % 12;
    final m = t.minute.toString().padLeft(2, '0');
    return '$h:$m ${t.hour < 12 ? 'AM' : 'PM'}';
  }

  String _date(DateTime t) => '${t.day}/${t.month} ${_time(t)}';

  @override
  Widget build(BuildContext context) {
    final s = _settings;
    final st = _status;
    if (s == null || st == null) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    final text = Theme.of(context).textTheme;
    final missing = [
      if (_perms['usage'] != true) 'Usage Access',
      if (_perms['overlay'] != true) 'Display over other apps',
      if (_perms['battery'] != true) 'Battery optimization',
      if (_guard && s.isParent && _perms['accessibility'] != true)
        'Uninstall protection',
    ];
    final on = s.enabled && missing.isEmpty && st.running;
    final exam = s.examDate;
    final today = DateTime.now();
    final daysLeft = exam == null
        ? null
        : ((DateTime(exam.year, exam.month, exam.day)
                          .difference(
                            DateTime(today.year, today.month, today.day),
                          )
                          .inHours +
                      12) /
                  24)
              .floor();
    return Scaffold(
      appBar: AppBar(title: const Text('Quiz Lock')),
      body: RefreshIndicator(
        onRefresh: _refresh,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Card(
              color: on ? Colors.green.shade700 : Colors.orange.shade800,
              child: Padding(
                padding: const EdgeInsets.all(20),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      on ? '🔒 Lock is on' : '⚠️ Lock is not running',
                      style: text.headlineSmall?.copyWith(color: Colors.white),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      '${s.lockedApps.length} apps locked'
                      '${s.locksScreen ? ' + lock screen quiz' : ''}',
                      style: const TextStyle(color: Colors.white),
                    ),
                    if (st.freeUntil != null)
                      Text(
                        'Free until ${_time(st.freeUntil!)}',
                        style: const TextStyle(color: Colors.white),
                      ),
                    if (s.enabled && !st.running)
                      const Text(
                        'Your phone stopped the lock. Open "Phone setup" below to stop this happening.',
                        style: TextStyle(color: Colors.white),
                      ),
                  ],
                ),
              ),
            ),
            if (_kids?.on == true)
              Card(
                color: Colors.indigo.shade600,
                child: ListTile(
                  leading: const Icon(Icons.child_care, color: Colors.white),
                  title: const Text(
                    '🎮 Kids Lock is on',
                    style: TextStyle(color: Colors.white),
                  ),
                  trailing: const Icon(
                    Icons.chevron_right,
                    color: Colors.white,
                  ),
                  onTap: () => _push(const KidsLockScreen()),
                ),
              ),
            if (st.kills > 0)
              Card(
                child: ListTile(
                  leading: const Icon(
                    Icons.battery_alert,
                    color: Colors.orange,
                  ),
                  title: Text(
                    'Your phone stopped the lock ${st.kills} time${st.kills == 1 ? '' : 's'} recently',
                  ),
                  subtitle: const Text(
                    'Phones like Redmi, Realme, Vivo and Oppo do this to save battery. Fix the settings so it stays on.',
                  ),
                  isThreeLine: true,
                  trailing: FilledButton(
                    onPressed: () async {
                      await Native.clearKills();
                      if (mounted) _push(PhoneSetupScreen(settings: s));
                    },
                    child: const Text('Fix'),
                  ),
                ),
              ),
            if (s.examName.isNotEmpty && daysLeft != null && daysLeft >= 0)
              Card(
                child: ListTile(
                  leading: const Icon(Icons.hourglass_bottom),
                  title: Text(
                    daysLeft == 0
                        ? '${s.examName} is today. All the best!'
                        : '${s.examName}: $daysLeft day${daysLeft == 1 ? '' : 's'} left',
                  ),
                ),
              ),
            if (missing.isNotEmpty)
              Card(
                child: ListTile(
                  leading: const Icon(Icons.error, color: Colors.red),
                  title: Text('Permission turned off: ${missing.join(', ')}'),
                  subtitle: const Text('The lock will not work without it.'),
                  trailing: FilledButton(
                    onPressed: () => Native.openPermission(
                      _perms['usage'] != true
                          ? 'usage'
                          : _perms['overlay'] != true
                          ? 'overlay'
                          : _perms['battery'] != true
                          ? 'battery'
                          : 'accessibility',
                    ),
                    child: const Text('Fix'),
                  ),
                ),
              ),
            const SizedBox(height: 8),
            Text('Today', style: text.titleMedium),
            const SizedBox(height: 8),
            Row(
              children: [
                _Stat('Correct', '${st.todayCorrect}', Colors.green),
                _Stat('Wrong', '${st.todayWrong}', Colors.red),
                _Stat('Skips left', '${st.skipsLeft}', Colors.blue),
                if (s.walkEnabled)
                  _Stat('Walks left', '${st.walksLeft}', Colors.teal),
              ],
            ),
            if (_pending != null) ...[
              const SizedBox(height: 16),
              Card(
                child: ListTile(
                  leading: const Icon(Icons.schedule),
                  title: const Text('A change is pending'),
                  subtitle: Text(
                    'Takes effect on ${_date(_pending!.applyAt)}.',
                  ),
                  trailing: TextButton(
                    onPressed: () async {
                      await Native.cancelPending();
                      _refresh();
                    },
                    child: const Text('Cancel'),
                  ),
                ),
              ),
            ],
            const SizedBox(height: 24),
            FilledButton.icon(
              onPressed: Native.testQuiz,
              icon: const Icon(Icons.play_arrow),
              label: const Text('Try a test quiz'),
            ),
            const SizedBox(height: 8),
            OutlinedButton.icon(
              onPressed: () => _push(const MyQuestionsScreen()),
              icon: const Icon(Icons.edit_note),
              label: const Text('My questions'),
            ),
            const SizedBox(height: 8),
            OutlinedButton.icon(
              onPressed: () => _push(const KidsLockScreen()),
              icon: const Icon(Icons.child_care),
              label: const Text('Kids Lock (for your own phone) ⭐'),
            ),
            const SizedBox(height: 8),
            OutlinedButton.icon(
              onPressed: () => _push(PhoneSetupScreen(settings: s)),
              icon: const Icon(Icons.phonelink_setup),
              label: const Text('Phone setup (keep the lock running)'),
            ),
            const SizedBox(height: 8),
            OutlinedButton.icon(
              onPressed: _openSettings,
              icon: const Icon(Icons.settings),
              label: const Text('Change settings'),
            ),
          ],
        ),
      ),
    );
  }
}

class _Stat extends StatelessWidget {
  final String label;
  final String value;
  final Color color;
  const _Stat(this.label, this.value, this.color);

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Card(
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 16),
          child: Column(
            children: [
              Text(
                value,
                style: Theme.of(context).textTheme.headlineMedium
                    ?.copyWith(color: color),
              ),
              Text(label),
            ],
          ),
        ),
      ),
    );
  }
}
