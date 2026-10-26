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

  void _changed() => setState(() {});

  Future<void> _pickApps() async {
    await Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => Scaffold(
        appBar: AppBar(title: const Text('Apps chuno')),
        body: AppPicker(selected: _draft.lockedApps, onChanged: () {}),
      ),
    ));
    setState(() {});
  }

  Future<void> _save() async {
    if (_draft.lockedApps.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Kam se kam 1 app chuno')));
      return;
    }
    final looser = _draft.looserThan(widget.current);
    if (looser.isNotEmpty && !_draft.isParent) {
      final applyAt = DateTime.now().add(const Duration(hours: 24));
      final ok = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('Ye change 24 ghante baad lagu hoga'),
          content: Text(
              'Isme lock dheela ho raha hai:\n• ${looser.join('\n• ')}\n\nTab tak purani settings chalti rahengi. Pehle se koi change pending hai to wo cancel ho jayega.'),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(context, false),
                child: const Text('Rehne do')),
            FilledButton(
                onPressed: () => Navigator.pop(context, true),
                child: const Text('Theek hai')),
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
            title: const Text('Lock chalu'),
            value: _draft.enabled,
            onChanged: (v) => setState(() => _draft.enabled = v),
          ),
          const Divider(),
          Text('Lock ka type', style: text.titleLarge),
          const SizedBox(height: 8),
          LockTypeSection(settings: _draft, onChanged: _changed),
          const SizedBox(height: 16),
          Card(
            child: ListTile(
              leading: const Icon(Icons.apps),
              title: const Text('Locked apps'),
              subtitle: Text('${_draft.lockedApps.length} app chune'),
              trailing: const Icon(Icons.chevron_right),
              onTap: _pickApps,
            ),
          ),
          const SizedBox(height: 16),
          Text('Quiz', style: text.titleLarge),
          const SizedBox(height: 8),
          QuizSection(settings: _draft, onChanged: _changed),
          const SizedBox(height: 16),
          Text('Free time', style: text.titleLarge),
          FreeTimeSection(settings: _draft, onChanged: _changed),
          const SizedBox(height: 24),
          FilledButton(onPressed: _save, child: const Text('Save')),
        ],
      ),
    );
  }
}
