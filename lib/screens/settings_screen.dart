import 'package:flutter/material.dart';

import '../native.dart';
import '../settings.dart';
import '../widgets/sections.dart';

/// Lock tight karna turant lagu hota hai. Dheela karna: Student mode mein 24
/// ghante baad, Parent mode mein turant (PIN pehle hi maanga ja chuka hai).
class SettingsScreen extends StatefulWidget {
  final LockSettings current;
  const SettingsScreen({super.key, required this.current});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  late final LockSettings _draft = widget.current.copy();
  bool? _premium;

  @override
  void initState() {
    super.initState();
    Native.premium().then((p) {
      if (mounted) setState(() => _premium = p);
    });
  }

  void _changed() => setState(() {});

  Future<void> _pickApps() async {
    await Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => Scaffold(
          appBar: AppBar(title: const Text('Choose apps')),
          body: AppPicker(selected: _draft.lockedApps, onChanged: () {}),
        ),
      ),
    );
    setState(() {});
  }

  Future<void> _save() async {
    if (_draft.lockedApps.isEmpty) {
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('Pick at least 1 app')));
      return;
    }
    final looser = _draft.looserThan(widget.current);
    if (looser.isNotEmpty && !_draft.isParent) {
      final applyAt = DateTime.now().add(const Duration(hours: 24));
      final ok = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('This change takes effect in 24 hours'),
          content: Text(
            'This loosens the lock:\n• ${looser.join('\n• ')}\n\nYour current settings stay until then. Any change already pending will be cancelled.',
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Cancel'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('OK'),
            ),
          ],
        ),
      );
      if (ok != true) return;
      await Native.setPending(_draft, applyAt);
    } else {
      await Native.saveSettings(_draft);
      if (_draft.enabled) {
        await Native.startLock();
      } else {
        await Native.stopLock();
      }
    }
    if (mounted) Navigator.of(context).pop();
  }

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return Scaffold(
      appBar: AppBar(title: const Text('Settings')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: const Text('Lock on'),
            value: _draft.enabled,
            onChanged: (v) => setState(() => _draft.enabled = v),
          ),
          const Divider(),
          Text('Lock type', style: text.titleLarge),
          const SizedBox(height: 8),
          LockTypeSection(settings: _draft, onChanged: _changed),
          const SizedBox(height: 16),
          Card(
            child: ListTile(
              leading: const Icon(Icons.apps),
              title: const Text('Locked apps'),
              subtitle: Text('${_draft.lockedApps.length} selected'),
              trailing: const Icon(Icons.chevron_right),
              onTap: _pickApps,
            ),
          ),
          const SizedBox(height: 16),
          Text('Quiz', style: text.titleLarge),
          const SizedBox(height: 8),
          QuizSection(settings: _draft, onChanged: _changed),
          WalkSection(settings: _draft, onChanged: _changed),
          const SizedBox(height: 16),
          Text('Exam countdown', style: text.titleLarge),
          const SizedBox(height: 8),
          ExamSection(settings: _draft, onChanged: _changed),
          const SizedBox(height: 16),
          Text('Free time', style: text.titleLarge),
          FreeTimeSection(settings: _draft, onChanged: _changed),
          const SizedBox(height: 24),
          FilledButton(onPressed: _save, child: const Text('Save')),
          const SizedBox(height: 32),
          const Divider(),
          // Play billing aane tak Premium features test karne ke liye. Billing ke saath hatana hai.
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: const Text('🧪 Testing: Premium ON'),
            subtitle: const Text(
              'Only for testing until buying Premium is added. Unlocks Kids Lock and unlimited own questions.',
            ),
            value: _premium ?? false,
            onChanged: _premium == null
                ? null
                : (v) async {
                    await Native.setPremium(v);
                    setState(() => _premium = v);
                  },
          ),
        ],
      ),
    );
  }
}
