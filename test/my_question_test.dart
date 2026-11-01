import 'package:flutter_test/flutter_test.dart';
import 'package:lock_app/native.dart';

void main() {
  test('sahi sawal pass hota hai', () {
    expect(MyQuestion.validate('2 + 2?', ['3', '4', '5', '6'], 1), isNull);
  });

  test('khaali ya repeat option, aur bina jawab ke fail', () {
    expect(MyQuestion.validate(' ', ['a', 'b', 'c', 'd'], 0), isNotNull);
    expect(MyQuestion.validate('Q', ['a', '', 'c', 'd'], 0), isNotNull);
    expect(MyQuestion.validate('Q', ['a', 'A ', 'c', 'd'], 0), isNotNull);
    expect(MyQuestion.validate('Q', ['a', 'b', 'c', 'd'], null), isNotNull);
  });

  test('JSON round trip', () {
    final q = MyQuestion(
      id: 'my-1',
      question: 'Q',
      options: const ['a', 'b', 'c', 'd'],
      answer: 2,
      explanation: 'why',
      addedAt: DateTime.fromMillisecondsSinceEpoch(1000),
    );
    final back = MyQuestion.fromJson(q.toJson());
    expect(back.toJson(), q.toJson());
    expect(q.toJson()['category'], 'mine');
  });
}
