import 'package:flutter/material.dart';

import '../settings.dart';
import '../widgets/permissions.dart';

/// Home se: saari permissions aur "lock band na ho" wale steps ek jagah.
class PhoneSetupScreen extends StatelessWidget {
  final LockSettings settings;
  const PhoneSetupScreen({super.key, required this.settings});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Phone setup')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [PermissionsList(settings: settings)],
      ),
    );
  }
}
