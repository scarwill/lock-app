import 'package:flutter/material.dart';

import '../native.dart';
import '../widgets/premium.dart';
import '../widgets/sections.dart';

/// Kids Lock (Premium): parent apna phone bachche ko de, to chune hue apps
/// (games, WhatsApp, Gallery, UPI) sirf parent PIN ya fingerprint se, 15/30/60
/// minute ke liye khulte hain. Notification bar ke tile se bhi chalu hota hai.
class KidsLockScreen extends StatefulWidget {
  const KidsLockScreen({super.key});

  @override
  State<KidsLockScreen> createState() => _KidsLockScreenState();
}

class _KidsLockScreenState extends State<KidsLockScreen>
    with WidgetsBindingObserver {
  KidsState? _kids;
  bool _premium = false;
  bool _hasPin = false;
  bool _fingerprint = false;
  Map<String, bool> _perms = {};

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _load();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _load();
  }

  Future<void> _load() async {
    final k = await Native.kids();
    final p = await Native.premium();
    final st = await Native.status();
    final d = await Native.deviceInfo();
    final perms = await Native.permissions();
    if (!mounted) return;
    setState(() {
      _kids = k;
      _premium = p;
      _hasPin = st.hasPin;
      _fingerprint = d.fingerprint;
      _perms = perms;
    });
  }

  bool get _permsOk => _perms['usage'] == true && _perms['overlay'] == true;

  Future<void> _pickApps() async {
    final selected = [..._kids!.apps];
    await Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => Scaffold(
          appBar: AppBar(title: const Text('Apps to lock for kids')),
          body: AppPicker(
            selected: selected,
            onChanged: () {},
            gamesFirst: true,
          ),
        ),
      ),
    );
    await Native.setKidsApps(selected);
    _load();
  }

  Future<void> _turnOn() async {
    if (!_premium) return showPremiumDialog(context, 'Kids Lock');
    if (_kids!.apps.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Choose at least 1 app first')),
      );
      return;
    }
    if (!_hasPin && !await _createPin()) return;
    await Native.setKidsMode(true);
    _load();
  }

  Future<void> _turnOff() async {
    if (!await askPin(context)) return;
    await Native.setKidsMode(false);
    _load();
  }

  /// Student mode mein PIN nahi hota; Kids Lock ke liye ek bana lo.
  Future<bool> _createPin() async {
    final a = TextEditingController();
    final b = TextEditingController();
    String? error;
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setState) => AlertDialog(
          title: const Text('Create a parent PIN'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text(
                'Only you should know it. Kids need it to open locked apps.',
              ),
              TextField(
                controller: a,
                obscureText: true,
                keyboardType: TextInputType.number,
                maxLength: 4,
                decoration: const InputDecoration(labelText: '4 digit PIN'),
              ),
              TextField(
                controller: b,
                obscureText: true,
                keyboardType: TextInputType.number,
                maxLength: 4,
                decoration: InputDecoration(
                  labelText: 'Enter again',
                  errorText: error,
                ),
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Cancel'),
            ),
            FilledButton(
              onPressed: () async {
                if (a.text.length != 4 || a.text != b.text) {
                  setState(() => error = 'PINs must match and be 4 digits');
                  return;
                }
                await Native.setPin(a.text);
                if (context.mounted) Navigator.pop(context, true);
              },
              child: const Text('Save'),
            ),
          ],
        ),
      ),
    );
    a.dispose();
    b.dispose();
    return ok ?? false;
  }

  @override
  Widget build(BuildContext context) {
    final k = _kids;
    final text = Theme.of(context).textTheme;
    if (k == null) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    return Scaffold(
      appBar: AppBar(title: const Text('🎮 Kids Lock')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Card(
            color: k.on ? Colors.green.shade700 : null,
            child: Padding(
              padding: const EdgeInsets.all(20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    k.on ? '🔒 Kids Lock is on' : 'Kids Lock is off',
                    style: text.headlineSmall?.copyWith(
                      color: k.on ? Colors.white : null,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    k.on
                        ? (k.freeUntil != null
                              ? 'Unlocked by parent until ${TimeOfDay.fromDateTime(k.freeUntil!).format(context)}'
                              : '${k.apps.length} apps need your permission.')
                        : 'Giving your phone to a child? Turn this on. The apps you choose (games, WhatsApp, Gallery, payment apps) open only with your PIN${_fingerprint ? ' or fingerprint' : ''}, for 15, 30 or 60 minutes.',
                    style: TextStyle(color: k.on ? Colors.white : null),
                  ),
                ],
              ),
            ),
          ),
          if (!_premium)
            Card(
              child: ListTile(
                leading: const Icon(Icons.star, color: Colors.amber),
                title: const Text('Kids Lock is a Premium feature'),
                onTap: () => showPremiumDialog(context, 'Kids Lock'),
              ),
            ),
          if (!_permsOk)
            Card(
              child: ListTile(
                leading: const Icon(Icons.error, color: Colors.red),
                title: const Text(
                  'Needs Usage Access and Display over other apps',
                ),
                trailing: FilledButton(
                  onPressed: () => Native.openPermission(
                    _perms['usage'] != true ? 'usage' : 'overlay',
                  ),
                  child: const Text('Fix'),
                ),
              ),
            ),
          Card(
            child: ListTile(
              leading: const Icon(Icons.apps),
              title: const Text('Apps to lock'),
              subtitle: Text('${k.apps.length} selected'),
              trailing: const Icon(Icons.chevron_right),
              onTap: k.on ? null : _pickApps,
            ),
          ),
          const SizedBox(height: 16),
          if (k.on)
            OutlinedButton.icon(
              onPressed: _turnOff,
              icon: const Icon(Icons.lock_open),
              label: const Text('Turn off (needs PIN)'),
            )
          else
            FilledButton.icon(
              onPressed: _permsOk ? _turnOn : null,
              icon: const Icon(Icons.child_care),
              label: const Text('Turn on Kids Lock'),
            ),
          const SizedBox(height: 24),
          Text('Quick switch', style: text.titleMedium),
          const SizedBox(height: 4),
          const Text(
            'Add the "Kids Lock" tile: pull down the notification bar → tap ✏️ (edit) → drag "Kids Lock" up. Then turn it on with one tap before giving your phone.',
          ),
        ],
      ),
    );
  }
}
