import 'package:flutter/material.dart';

import '../native.dart';
import '../settings.dart';

class PermissionInfo {
  final String key;
  final String title;
  final String why;
  final bool required;
  const PermissionInfo(this.key, this.title, this.why, {this.required = false});
}

const basePermissions = [
  PermissionInfo(
    'usage',
    'Usage Access',
    'To know which app is open.',
    required: true,
  ),
  PermissionInfo(
    'overlay',
    'Display over other apps',
    'To show the quiz on top of other apps.',
    required: true,
  ),
  PermissionInfo(
    'notifications',
    'Notifications',
    'To show a small notification while the lock is running.',
  ),
  PermissionInfo(
    'battery',
    'Battery optimization off',
    'So the phone doesn\'t stop the lock in the background.',
    required: true,
  ),
];

/// Sirf Parent mode: bachcha bina PIN ke app na hata sake.
const parentPermissions = [
  PermissionInfo(
    'accessibility',
    'Uninstall protection',
    'Stops your child from uninstalling or switching off Quiz Lock without your PIN. Find "Quiz Lock protection" in the list and turn it on.',
    required: true,
  ),
  PermissionInfo(
    'admin',
    'Extra uninstall protection',
    'Blocks uninstalling from the home screen on some phones.',
  ),
];

const walkPermission = PermissionInfo(
  'activity',
  'Physical activity',
  'For Walk-to-Unlock: counts your steps with the phone\'s step sensor.',
);

/// Company ke hisaab se "battery saver" mein kya chunna hai.
String batteryHint(String brand) => switch (brand) {
  'xiaomi' => 'Opens Battery saver. Choose "No restrictions".',
  'realme' || 'oppo' || 'oneplus' =>
    'Turn on "Allow background activity" (and "Allow foreground activity").',
  'vivo' => 'Allow "High background power usage" for Quiz Lock.',
  'samsung' => 'Battery → choose "Unrestricted" for Quiz Lock.',
  'huawei' => 'Turn off "Manage automatically" and allow all 3 options.',
  _ => 'App info → Battery → choose "Unrestricted".',
};

/// Recent apps mein Quiz Lock ko lock karne ke steps (swipe karne par band na ho).
String recentsHint(String brand) => switch (brand) {
  'xiaomi' => 'Open recent apps. Press and hold the Quiz Lock card (or swipe it down) and tap the 🔒 lock icon.',
  'realme' || 'oppo' || 'oneplus' => 'Open recent apps. Tap ⋮ on the Quiz Lock card (or press and hold it) and tap "Lock".',
  'vivo' => 'Open recent apps. Swipe the Quiz Lock card down (or press and hold it) and tap the 🔒 lock.',
  'samsung' => 'Open recent apps. Tap the Quiz Lock icon on top of its card and choose "Keep open".',
  'huawei' => 'Open recent apps and swipe the Quiz Lock card down to lock it.',
  _ =>
    'Open recent apps. If the Quiz Lock card has a 🔒 lock option, turn it on.',
};

const _autostartBrands = {
  'xiaomi',
  'realme',
  'oppo',
  'oneplus',
  'vivo',
  'huawei',
  'asus',
};

/// Permissions ki list. App par wapas aate hi status khud refresh hota hai.
class PermissionsList extends StatefulWidget {
  final LockSettings settings;
  final ValueChanged<bool>? onRequiredChanged;
  const PermissionsList({
    super.key,
    required this.settings,
    this.onRequiredChanged,
  });

  @override
  State<PermissionsList> createState() => _PermissionsListState();
}

class _PermissionsListState extends State<PermissionsList>
    with WidgetsBindingObserver {
  Map<String, bool> _granted = {};
  Map<String, bool> _flags = {};
  DeviceInfo? _device;

  /// Zaroori permissions jinke liye user settings mein ja chuka hai.
  final _tried = <String>{};

  /// Wo haath wala step jiske liye user abhi settings mein gaya (wapas aane par poochenge).
  String? _askAfterReturn;

  List<PermissionInfo> get _items => [
    ...basePermissions,
    if (widget.settings.isParent) ...parentPermissions,
    if (widget.settings.walkEnabled &&
        (_device?.stepSensor ?? false) &&
        (_device?.sdk ?? 0) >= 29)
      walkPermission,
  ];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    Native.deviceInfo().then((d) {
      if (mounted) setState(() => _device = d);
      _refresh();
    });
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
      final ask = _askAfterReturn;
      if (ask != null) {
        _askAfterReturn = null;
        _confirm(ask);
      }
    }
  }

  Future<void> _refresh() async {
    final g = await Native.permissions();
    final f = await Native.flags();
    if (!mounted) return;
    setState(() {
      _granted = g;
      _flags = f;
    });
    widget.onRequiredChanged?.call(
      _items.where((p) => p.required).every((p) => g[p.key] == true),
    );
  }

  /// Android ye check nahi kar sakta, isliye user se hi poochte hain.
  Future<void> _confirm(String flag) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Done?'),
        content: const Text(
          'Did you turn it on for Quiz Lock? Your phone can stop the lock without it.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Not yet'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Yes, done'),
          ),
        ],
      ),
    );
    await Native.setFlag(flag, ok == true);
    _refresh();
  }

  Future<void> _showRecents(String brand) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Lock Quiz Lock in recent apps'),
        content: Text(
          '${recentsHint(brand)}\n\nThen swiping away all apps won\'t stop the lock.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Later'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('I did it'),
          ),
        ],
      ),
    );
    if (ok != null) await Native.setFlag('recents', ok);
    _refresh();
  }

  @override
  Widget build(BuildContext context) {
    final brand = _device?.brand ?? 'other';
    return Column(
      children: [
        for (final p in _items)
          _PermissionTile(
            title: p.required ? '${p.title} (required)' : p.title,
            why: p.why,
            granted: _granted[p.key] == true,
            onTap: () {
              _tried.add(p.key);
              Native.openPermission(p.key);
            },
          ),
        if (_items.any(
          (p) =>
              p.required && _tried.contains(p.key) && _granted[p.key] != true,
        ))
          const _RestrictedHelp(),
        if (_device != null) ...[
          const Padding(
            padding: EdgeInsets.fromLTRB(4, 16, 4, 4),
            child: Text(
              'Keep the lock running (recommended). Many phones stop apps in the background after a while.',
            ),
          ),
          if (_autostartBrands.contains(brand))
            _PermissionTile(
              title: 'Autostart',
              why: 'Turn Quiz Lock ON there and come back.',
              granted: _flags['autostart'] == true,
              onTap: () {
                _askAfterReturn = 'autostart';
                Native.openPermission('autostart');
              },
            ),
          _PermissionTile(
            title: 'Phone battery saver',
            why: batteryHint(brand),
            granted: _flags['battery_brand'] == true,
            onTap: () {
              _askAfterReturn = 'battery_brand';
              Native.openPermission('battery_brand');
            },
          ),
          _PermissionTile(
            title: 'Lock in recent apps',
            why: 'So "clear all" in recent apps doesn\'t stop the lock.',
            granted: _flags['recents'] == true,
            onTap: () => _showRecents(brand),
          ),
        ],
      ],
    );
  }
}

class _PermissionTile extends StatelessWidget {
  final String title;
  final String why;
  final bool granted;
  final VoidCallback onTap;
  const _PermissionTile({
    required this.title,
    required this.why,
    required this.granted,
    required this.onTap,
  });

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

/// Android 13+ par Play Store ke bahar se install hue apps ke liye ye toggles
/// "Restricted setting" ki wajah se grey ho jaate hain.
class _RestrictedHelp extends StatelessWidget {
  const _RestrictedHelp();

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final text = Theme.of(context).textTheme;
    return Card(
      elevation: 0,
      color: scheme.errorContainer,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Phone not letting you turn it on?',
              style: text.titleMedium?.copyWith(color: scheme.onErrorContainer),
            ),
            const SizedBox(height: 8),
            Text(
              'Android blocks this for apps installed from outside the Play Store. To allow it:\n'
              '1. Tap "Open App info" below\n'
              '2. Tap the ⋮ menu (top right) → "Allow restricted settings"\n'
              '3. Confirm with your fingerprint/PIN\n'
              '4. Come back here and tap Allow again\n\n'
              'Xiaomi/Redmi/POCO: in App info, also open "Other permissions" and allow "Display pop-up windows while running in the background".',
              style: TextStyle(color: scheme.onErrorContainer),
            ),
            const SizedBox(height: 12),
            FilledButton(
              onPressed: () => Native.openPermission('appinfo'),
              child: const Text('Open App info'),
            ),
          ],
        ),
      ),
    );
  }
}
