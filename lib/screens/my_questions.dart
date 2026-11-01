import 'package:flutter/material.dart';

import '../native.dart';
import '../widgets/premium.dart';

/// "Make your own questions": user khud MCQ likhe. Naye sawal 3 din tak quiz
/// mein pehle aate hain. Free mein ek saath 25, Premium mein unlimited.
class MyQuestionsScreen extends StatefulWidget {
  const MyQuestionsScreen({super.key});

  @override
  State<MyQuestionsScreen> createState() => _MyQuestionsScreenState();
}

class _MyQuestionsScreenState extends State<MyQuestionsScreen> {
  List<MyQuestion>? _list;
  bool _premium = false;

  /// Is baar kitne naye sawal likhe (har 5 par photo wala nudge).
  int _addedNow = 0;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final l = await Native.myQuestions();
    final p = await Native.premium();
    if (mounted) {
      setState(() {
        _list = l;
        _premium = p;
      });
    }
  }

  bool get _full => !_premium && (_list?.length ?? 0) >= MyQuestion.freeLimit;

  Future<void> _edit([MyQuestion? q]) async {
    if (q == null && _full) {
      await showPremiumDialog(
        context,
        'More than ${MyQuestion.freeLimit} questions at a time',
      );
      return;
    }
    final result = await Navigator.of(context).push<MyQuestion>(
      MaterialPageRoute(builder: (_) => _QuestionForm(existing: q)),
    );
    if (result == null) return;
    final list = [..._list!];
    final i = list.indexWhere((e) => e.id == result.id);
    if (i >= 0) {
      list[i] = result;
    } else {
      list.add(result);
      _addedNow++;
    }
    await Native.saveMyQuestions(list);
    setState(() => _list = list);
    if (q == null && _addedNow % 5 == 0 && mounted) _nudge();
  }

  void _nudge() {
    showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Tired of typing? 📸'),
        content: const Text(
          'Soon you can snap a photo of your book page or upload a PDF, and get 20 questions in 10 seconds. Coming with Premium.',
        ),
        actions: [
          FilledButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Nice!'),
          ),
        ],
      ),
    );
  }

  Future<void> _delete(MyQuestion q) async {
    final list = _list!.where((e) => e.id != q.id).toList();
    await Native.saveMyQuestions(list);
    setState(() => _list = list);
  }

  @override
  Widget build(BuildContext context) {
    final list = _list;
    return Scaffold(
      appBar: AppBar(title: const Text('My questions')),
      floatingActionButton: list == null
          ? null
          : FloatingActionButton.extended(
              onPressed: _edit,
              icon: Icon(_full ? Icons.lock : Icons.add),
              label: const Text('Add question'),
            ),
      body: list == null
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 96),
              children: [
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Text(
                      'Write your own MCQs, like tomorrow\'s test. New questions come first in the quiz for 3 days, then mix with the rest.\n\n'
                      '${_premium ? '⭐ Premium: no limit.' : '${list.length} / ${MyQuestion.freeLimit} used (free).'}',
                    ),
                  ),
                ),
                if (list.isEmpty)
                  const Padding(
                    padding: EdgeInsets.all(32),
                    child: Center(
                      child: Text('No questions yet. Tap "Add question".'),
                    ),
                  ),
                for (final q in list.reversed)
                  Card(
                    child: ListTile(
                      title: Text(
                        q.question,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                      subtitle: Text(
                        '✅ ${q.options[q.answer]}'
                        '${DateTime.now().difference(q.addedAt).inDays < 3 ? '   •   🆕 showing first' : ''}',
                      ),
                      onTap: () => _edit(q),
                      trailing: IconButton(
                        icon: const Icon(Icons.delete_outline),
                        onPressed: () => _delete(q),
                      ),
                    ),
                  ),
              ],
            ),
    );
  }
}

class _QuestionForm extends StatefulWidget {
  final MyQuestion? existing;
  const _QuestionForm({this.existing});

  @override
  State<_QuestionForm> createState() => _QuestionFormState();
}

class _QuestionFormState extends State<_QuestionForm> {
  late final _q = TextEditingController(text: widget.existing?.question);
  late final _opts = List.generate(
    4,
    (i) => TextEditingController(text: widget.existing?.options[i]),
  );
  late final _why = TextEditingController(text: widget.existing?.explanation);
  late int? _answer = widget.existing?.answer;
  String? _error;

  @override
  void dispose() {
    _q.dispose();
    _why.dispose();
    for (final c in _opts) {
      c.dispose();
    }
    super.dispose();
  }

  void _save() {
    final options = _opts.map((c) => c.text.trim()).toList();
    final err = MyQuestion.validate(_q.text, options, _answer);
    if (err != null) {
      setState(() => _error = err);
      return;
    }
    final old = widget.existing;
    Navigator.of(context).pop(
      MyQuestion(
        id: old?.id ?? 'my-${DateTime.now().microsecondsSinceEpoch}',
        question: _q.text.trim(),
        options: options,
        answer: _answer!,
        explanation: _why.text.trim(),
        addedAt: old?.addedAt ?? DateTime.now(),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.existing == null ? 'New question' : 'Edit question'),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          TextField(
            controller: _q,
            maxLines: 3,
            minLines: 1,
            decoration: const InputDecoration(
              labelText: 'Question',
              border: OutlineInputBorder(),
            ),
          ),
          const SizedBox(height: 16),
          const Text('Options (tap the circle for the correct one)'),
          RadioGroup<int>(
            groupValue: _answer,
            onChanged: (v) => setState(() => _answer = v),
            child: Column(
              children: [
                for (var i = 0; i < 4; i++)
                  Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: Row(
                      children: [
                        Radio<int>(value: i),
                        Expanded(
                          child: TextField(
                            controller: _opts[i],
                            decoration: InputDecoration(
                              labelText:
                                  'Option ${String.fromCharCode(65 + i)}',
                              border: const OutlineInputBorder(),
                              isDense: true,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
              ],
            ),
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _why,
            decoration: const InputDecoration(
              labelText: 'Explanation (optional)',
              border: OutlineInputBorder(),
            ),
          ),
          if (_error != null)
            Padding(
              padding: const EdgeInsets.only(top: 12),
              child: Text(
                _error!,
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
            ),
          const SizedBox(height: 24),
          FilledButton(onPressed: _save, child: const Text('Save')),
        ],
      ),
    );
  }
}
