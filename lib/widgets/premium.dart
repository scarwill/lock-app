import 'package:flutter/material.dart';

/// Paid feature par "Premium chahiye" dialog. Asli kharid (Play billing) abhi
/// nahi hai; testing ke liye Settings mein switch hai.
Future<void> showPremiumDialog(
  BuildContext context,
  String feature,
) => showDialog<void>(
  context: context,
  builder: (context) => AlertDialog(
    title: const Text('⭐ Premium'),
    content: Text(
      '$feature is part of Premium.\n\nBuying Premium is coming soon. For testing, turn on "Testing: Premium ON" at the bottom of Settings.',
    ),
    actions: [
      FilledButton(
        onPressed: () => Navigator.pop(context),
        child: const Text('OK'),
      ),
    ],
  ),
);
