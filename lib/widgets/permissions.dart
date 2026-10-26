import 'package:flutter/material.dart';

import '../native.dart';

class PermissionInfo {
  final String key;
  final String title;
  final String why;
  final bool required;
  const PermissionInfo(this.key, this.title, this.why, {this.required = false});
}

const basePermissions = [
  PermissionInfo('usage', 'Usage Access',
      'To know which app is open.',
      required: true),
  PermissionInfo('overlay', 'Display over other apps',
      'To show the quiz on top of other apps.',
      required: true),
  PermissionInfo('notifications', 'Notifications',
      'To show a small notification while the lock is running.'),
  PermissionInfo('battery', 'Battery optimization off',
      'So the phone doesn\'t stop this app in the background.'),
];

const adminPermission = PermissionInfo('admin', 'Uninstall protection',
    'So your child can\'t delete the app without you.');

/// Permissions ki list. App par wapas aate hi status khud refresh hota hai.
class PermissionsList extends StatefulWidget {
  final bool parentMode;
  final ValueChanged<bool>? onRequiredChanged;
  const PermissionsList(
      {super.key, required this.parentMode, this.onRequiredChanged});

  @override
  State<PermissionsList> createState() => _PermissionsListState();
}

class _PermissionsListState extends State<PermissionsList>
    with WidgetsBindingObserver {
  Map<String, bool> _granted = {};
  bool _autostartDone = false;

  List<PermissionInfo> get _items =>
      [...basePermissions, if (widget.parentMode) adminPermission];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _refresh();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _refresh();
  }

  Future<void> _refresh() async {
    final g = await Native.permissions();
    if (!mounted) return;
    setState(() => _granted = g);
    widget.onRequiredChanged
        ?.call(_items.where((p) => p.required).every((p) => g[p.key] == true));
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        for (final p in _items)
          _PermissionTile(
            title: p.required ? '${p.title} (required)' : p.title,
            why: p.why,
            granted: _granted[p.key] == true,
            onTap: () => Native.openPermission(p.key),
          ),
        _PermissionTile(
          title: 'Autostart',
          why:
              'Needed on phones like Xiaomi, Oppo, Vivo and Realme, otherwise the lock stops after a restart. Turn this app ON there and come back.',
          granted: _autostartDone,
          onTap: () async {
            await Native.openPermission('autostart');
            setState(() => _autostartDone = true);
          },
        ),
      ],
    );
  }
}

class _PermissionTile extends StatelessWidget {
  final String title;
  final String why;
  final bool granted;
  final VoidCallback onTap;
  const _PermissionTile(
      {required this.title,
      required this.why,
      required this.granted,
      required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Card(
      elevation: 0,
      color: Theme.of(context).colorScheme.surfaceContainerHigh,
      child: ListTile(
        leading: Icon(
          granted ? Icons.check_circle : Icons.radio_button_unchecked,
          color: granted ? Colors.green : null,
        ),
        title: Text(title),
        subtitle: Text(why),
        trailing: granted
            ? null
            : FilledButton.tonal(onPressed: onTap, child: const Text('Allow')),
      ),
    );
  }
}
