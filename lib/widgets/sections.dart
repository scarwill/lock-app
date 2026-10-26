import 'package:flutter/material.dart';

import '../native.dart';
import '../settings.dart';

/// Bada select hone wala card (mode, lock type jaise choices ke liye).
class ChoiceCard extends StatelessWidget {
  final IconData icon;
  final String title;
  final String subtitle;
  final bool selected;
  final VoidCallback onTap;

  const ChoiceCard({
    super.key,
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.selected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Card(
      elevation: 0,
      color: selected ? scheme.primaryContainer : scheme.surfaceContainerHigh,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(
            color: selected ? scheme.primary : Colors.transparent, width: 2),
      ),
      child: InkWell(
        borderRadius: BorderRadius.circular(16),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              Icon(icon, size: 32, color: scheme.primary),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(title, style: Theme.of(context).textTheme.titleMedium),
                    const SizedBox(height: 4),
                    Text(subtitle,
                        style: Theme.of(context).textTheme.bodyMedium),
                  ],
                ),
              ),
              if (selected) Icon(Icons.check_circle, color: scheme.primary),
            ],
          ),
        ),
      ),
    );
  }
}

/// Label + chhote options ki row (1/3/5 jaisi choices).
class OptionRow extends StatelessWidget {
  final String label;
  final String? hint;
  final List<int> values;
  final int value;
  final String Function(int) format;
  final ValueChanged<int> onChanged;

  const OptionRow({
    super.key,
    required this.label,
    this.hint,
    required this.values,
    required this.value,
    required this.format,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 10),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: Theme.of(context).textTheme.titleMedium),
          if (hint != null)
            Padding(
              padding: const EdgeInsets.only(top: 2),
              child: Text(hint!, style: Theme.of(context).textTheme.bodySmall),
            ),
          const SizedBox(height: 8),
          SizedBox(
            width: double.infinity,
            child: SegmentedButton<int>(
              showSelectedIcon: false,
              segments: [
                for (final v in values)
                  ButtonSegment(value: v, label: Text(format(v))),
              ],
              selected: {value},
              onSelectionChanged: (s) => onChanged(s.first),
            ),
          ),
        ],
      ),
    );
  }
}

class LockTypeSection extends StatelessWidget {
  final LockSettings settings;
  final VoidCallback onChanged;
  const LockTypeSection(
      {super.key, required this.settings, required this.onChanged});

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        ChoiceCard(
          icon: Icons.apps,
          title: 'Sirf Apps',
          subtitle:
              'Jo apps tum chunoge, sirf wahi kholne par quiz aayega. Shuruaat ke liye ye best hai.',
          selected: settings.lockType == LockSettings.lockApps,
          onTap: () {
            settings.lockType = LockSettings.lockApps;
            onChanged();
          },
        ),
        const SizedBox(height: 8),
        ChoiceCard(
          icon: Icons.screen_lock_portrait,
          title: 'Apps + Lock Screen',
          subtitle:
              'Phone unlock karte hi quiz aayega, aur chune hue apps bhi lock rahenge. Sabse strict.',
          selected: settings.lockType == LockSettings.lockAppsAndScreen,
          onTap: () {
            settings.lockType = LockSettings.lockAppsAndScreen;
            onChanged();
          },
        ),
      ],
    );
  }
}

class QuizSection extends StatelessWidget {
  final LockSettings settings;
  final VoidCallback onChanged;
  const QuizSection(
      {super.key, required this.settings, required this.onChanged});

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('Subject', style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 8),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            for (final c in categories)
              ChoiceChip(
                label: Text(c.label),
                selected: settings.category == c.id,
                onSelected: (_) {
                  settings.category = c.id;
                  onChanged();
                },
              ),
          ],
        ),
        const SizedBox(height: 8),
        OptionRow(
          label: 'Ek baar mein kitne sahi jawab',
          values: const [1, 3, 5],
          value: settings.questionsPerUnlock,
          format: (v) => '$v',
          onChanged: (v) {
            settings.questionsPerUnlock = v;
            onChanged();
          },
        ),
        OptionRow(
          label: 'Galat jawab par rukna',
          hint: 'Taki tukka maar ke na nikal sako',
          values: const [10, 15, 30],
          value: settings.penaltySeconds,
          format: (v) => '$v sec',
          onChanged: (v) {
            settings.penaltySeconds = v;
            onChanged();
          },
        ),
      ],
    );
  }
}

class FreeTimeSection extends StatelessWidget {
  final LockSettings settings;
  final VoidCallback onChanged;
  const FreeTimeSection(
      {super.key, required this.settings, required this.onChanged});

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        OptionRow(
          label: 'Sahi jawab ke baad kitni der free',
          hint: 'Is time mein dobara quiz nahi aayega',
          values: const [15, 30, 60],
          value: settings.cooldownMinutes,
          format: (v) => '$v min',
          onChanged: (v) {
            settings.cooldownMinutes = v;
            onChanged();
          },
        ),
        OptionRow(
          label: 'Din mein Emergency Skip',
          hint: 'Bina quiz ke kholne ka mauka. Raat 12 baje reset hota hai.',
          values: const [0, 1, 2],
          value: settings.dailySkips,
          format: (v) => '$v',
          onChanged: (v) {
            settings.dailySkips = v;
            onChanged();
          },
        ),
      ],
    );
  }
}

/// Ye packages installed hon to list mein upar "Suggested" mein dikhte hain,
/// par pehle se tick nahi hote.
const suggestedPackages = {
  'com.instagram.android',
  'com.google.android.youtube',
  'com.snapchat.android',
  'com.facebook.katana',
  'in.mohalla.sharechat',
  'in.mohalla.video',
  'com.dts.freefireth',
  'com.dts.freefiremax',
  'com.pubg.imobile',
  'com.roblox.client',
  'com.supercell.clashofclans',
  'com.netflix.mediaclient',
  'com.twitter.android',
};

class AppPicker extends StatefulWidget {
  final List<String> selected;
  final VoidCallback onChanged;
  const AppPicker({super.key, required this.selected, required this.onChanged});

  @override
  State<AppPicker> createState() => _AppPickerState();
}

class _AppPickerState extends State<AppPicker> {
  List<InstalledApp>? _apps;
  String _query = '';

  @override
  void initState() {
    super.initState();
    Native.installedApps().then((apps) {
      apps.sort(
          (a, b) => a.label.toLowerCase().compareTo(b.label.toLowerCase()));
      if (mounted) setState(() => _apps = apps);
    });
  }

  void _toggle(String pkg) {
    setState(() {
      if (!widget.selected.remove(pkg)) widget.selected.add(pkg);
    });
    widget.onChanged();
  }

  Widget _tile(InstalledApp a) => CheckboxListTile(
        value: widget.selected.contains(a.package),
        onChanged: (_) => _toggle(a.package),
        secondary: a.icon != null
            ? Image.memory(a.icon!, width: 40, height: 40)
            : const Icon(Icons.android, size: 40),
        title: Text(a.label),
      );

  @override
  Widget build(BuildContext context) {
    final apps = _apps;
    if (apps == null) return const Center(child: CircularProgressIndicator());
    final q = _query.toLowerCase();
    final filtered =
        apps.where((a) => a.label.toLowerCase().contains(q)).toList();
    final suggested =
        filtered.where((a) => suggestedPackages.contains(a.package)).toList();
    final style = Theme.of(context).textTheme.labelLarge;
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
          child: TextField(
            decoration: const InputDecoration(
              prefixIcon: Icon(Icons.search),
              hintText: 'App dhundo',
              border: OutlineInputBorder(),
              isDense: true,
            ),
            onChanged: (v) => setState(() => _query = v),
          ),
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Align(
            alignment: Alignment.centerLeft,
            child: Text('${widget.selected.length} app chune'),
          ),
        ),
        Expanded(
          child: ListView(
            children: [
              if (suggested.isNotEmpty) ...[
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
                  child: Text('Suggested', style: style),
                ),
                ...suggested.map(_tile),
                const Divider(),
              ],
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
                child: Text('Saare apps', style: style),
              ),
              ...filtered.map(_tile),
            ],
          ),
        ),
      ],
    );
  }
}

/// Button jo 3 second daba ke rakhne par hi chalta hai.
class HoldButton extends StatefulWidget {
  final String label;
  final bool enabled;
  final VoidCallback onDone;
  const HoldButton(
      {super.key,
      required this.label,
      required this.enabled,
      required this.onDone});

  @override
  State<HoldButton> createState() => _HoldButtonState();
}

class _HoldButtonState extends State<HoldButton>
    with SingleTickerProviderStateMixin {
  late final _anim =
      AnimationController(vsync: this, duration: const Duration(seconds: 3))
        ..addStatusListener((s) {
          if (s == AnimationStatus.completed) widget.onDone();
        });

  @override
  void dispose() {
    _anim.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Listener(
      onPointerDown: (_) {
        if (widget.enabled) _anim.forward(from: 0);
      },
      onPointerUp: (_) {
        if (!_anim.isCompleted) _anim.reset();
      },
      onPointerCancel: (_) {
        if (!_anim.isCompleted) _anim.reset();
      },
      child: AnimatedBuilder(
        animation: _anim,
        builder: (context, _) => ClipRRect(
          borderRadius: BorderRadius.circular(28),
          child: Container(
            height: 56,
            color: widget.enabled
                ? scheme.primaryContainer
                : scheme.surfaceContainerHighest,
            child: Stack(
              children: [
                FractionallySizedBox(
                  widthFactor: _anim.value,
                  child: Container(color: scheme.primary),
                ),
                Center(
                  child: Text(
                    _anim.value > 0 ? 'Dabaye rakho...' : widget.label,
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w600,
                      color: widget.enabled
                          ? scheme.onPrimaryContainer
                          : scheme.outline,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// 4 digit PIN puchne wala dialog. Sahi hone par true lautata hai.
Future<bool> askPin(BuildContext context) async {
  final ctrl = TextEditingController();
  String? error;
  final ok = await showDialog<bool>(
    context: context,
    builder: (context) => StatefulBuilder(
      builder: (context, setState) => AlertDialog(
        title: const Text('Parent PIN dalo'),
        content: TextField(
          controller: ctrl,
          autofocus: true,
          obscureText: true,
          keyboardType: TextInputType.number,
          maxLength: 4,
          decoration: InputDecoration(errorText: error),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Cancel')),
          FilledButton(
            onPressed: () async {
              if (await Native.checkPin(ctrl.text)) {
                if (context.mounted) Navigator.pop(context, true);
              } else {
                setState(() => error = 'Galat PIN');
              }
            },
            child: const Text('Kholo'),
          ),
        ],
      ),
    ),
  );
  return ok ?? false;
}
