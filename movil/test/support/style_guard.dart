import 'dart:io';

/// A hand-written style found outside the design system (ADR-007).
class StyleViolation {
  const StyleViolation(this.path, this.line, this.rule, this.snippet);

  final String path;
  final int line;
  final String rule;
  final String snippet;

  @override
  String toString() => '$path:$line [$rule] $snippet';
}

/// Folder whose files may hold literal styles: the tokens and the theme.
const designSystemDir = 'lib/design_system/';

/// [designSystemDir] relative to the scanned root.
const _designSystemSubdir = 'design_system/';

final _colorLiteral = RegExp(r'\bColor\s*\(\s*0x');
final _colorFromArgb = RegExp(r'\bColor\.from(ARGB|RGBO)\s*\(');
final _materialColors = RegExp(r'(?<![\w.])(Colors|CupertinoColors)\.');
final _geometryCall = RegExp(
  r'(?<![\w.])(EdgeInsetsDirectional|EdgeInsets|BorderRadiusDirectional|BorderRadius|Radius)\b\s*(\.\s*\w+\s*)?\(',
);
final _numberLiteral = RegExp(r'(?<![\w.$])\d+(\.\d+)?(?![\w.])');
const _token = r'(KdConstants\.\w+|\w+\.(space|radius)\w*)';
final _tokenArithmetic = RegExp('$_token\\s*[-+*/]\\s*$_token');

/// Returns the violations of the style rules in one Dart [source] file.
///
/// Rules: no `Color(0x…)` or `Color.fromARGB/RGBO`, no `Colors.*` /
/// `CupertinoColors.*`, and no `EdgeInsets*`, `BorderRadius*` or `Radius`
/// built with numeric literals or with arithmetic between tokens (a derived
/// value gets a name in `lib/design_system/`). Comments and string literals
/// are ignored.
List<StyleViolation> scanSource(String path, String source) {
  final code = _blankCommentsAndStrings(source);
  final violations = <StyleViolation>[];

  void add(int offset, String rule, String snippet) {
    final line = '\n'.allMatches(code.substring(0, offset)).length + 1;
    violations.add(StyleViolation(path, line, rule, snippet.trim()));
  }

  for (final m in _colorLiteral.allMatches(code)) {
    add(m.start, 'color-literal', _lineAt(source, m.start));
  }
  for (final m in _colorFromArgb.allMatches(code)) {
    add(m.start, 'color-literal', _lineAt(source, m.start));
  }
  for (final m in _materialColors.allMatches(code)) {
    add(m.start, 'named-color', _lineAt(source, m.start));
  }
  for (final m in _geometryCall.allMatches(code)) {
    final args = _balancedArgs(code, m.end - 1);
    if (args == null) continue;
    final isRadius = m.group(1)!.contains('Radius');
    if (_numberLiteral.hasMatch(args)) {
      final rule = isRadius ? 'radius-literal' : 'spacing-literal';
      add(m.start, rule, _lineAt(source, m.start));
    } else if (_tokenArithmetic.hasMatch(args)) {
      add(m.start, 'token-arithmetic', _lineAt(source, m.start));
    }
  }
  violations.sort((a, b) => a.line.compareTo(b.line));
  return violations;
}

/// Scans every `.dart` file under [root] except its `design_system/` folder
/// ([designSystemDir] for the default root).
///
/// Must run with the package root (`movil/`) as the working directory,
/// which is what `flutter test` does.
List<StyleViolation> scanLib({String root = 'lib'}) {
  final violations = <StyleViolation>[];
  final files =
      Directory(root)
          .listSync(recursive: true)
          .whereType<File>()
          .where((f) => f.path.endsWith('.dart'))
          .toList()
        ..sort((a, b) => a.path.compareTo(b.path));
  for (final file in files) {
    final path = file.path.replaceAll(r'\', '/');
    final relative = path
        .substring(root.length)
        .replaceFirst(RegExp('^/+'), '');
    if (relative.startsWith(_designSystemSubdir)) continue;
    violations.addAll(scanSource(path, file.readAsStringSync()));
  }
  return violations;
}

String _lineAt(String source, int offset) {
  final start = source.lastIndexOf('\n', offset) + 1;
  final end = source.indexOf('\n', offset);
  return source.substring(start, end == -1 ? source.length : end);
}

/// Text between the parenthesis at [open] and its match, or null.
String? _balancedArgs(String code, int open) {
  var depth = 0;
  for (var i = open; i < code.length; i++) {
    final c = code[i];
    if (c == '(') depth++;
    if (c == ')') {
      depth--;
      if (depth == 0) return code.substring(open + 1, i);
    }
  }
  return null;
}

/// Replaces comments and string contents with spaces, keeping offsets and
/// line breaks, so rules only see code. Interpolations count as string text.
String _blankCommentsAndStrings(String source) {
  final out = StringBuffer();
  var i = 0;
  final n = source.length;
  String blank(String s) => s.replaceAll(RegExp(r'[^\n]'), ' ');

  while (i < n) {
    final rest = source.substring(i);
    if (rest.startsWith('//')) {
      final end = source.indexOf('\n', i);
      final stop = end == -1 ? n : end;
      out.write(blank(source.substring(i, stop)));
      i = stop;
    } else if (rest.startsWith('/*')) {
      final end = source.indexOf('*/', i + 2);
      final stop = end == -1 ? n : end + 2;
      out.write(blank(source.substring(i, stop)));
      i = stop;
    } else if (_quoteAt(source, i) case final quote?) {
      final raw = i > 0 && source[i - 1] == 'r';
      var j = i + quote.length;
      while (j < n && !source.startsWith(quote, j)) {
        if (!raw && source[j] == r'\') j++;
        if (quote.length == 1 && j < n && source[j] == '\n') break;
        j++;
      }
      final closed = j < n && source.startsWith(quote, j);
      final bodyEnd = j > n ? n : j;
      out.write(quote);
      out.write(blank(source.substring(i + quote.length, bodyEnd)));
      if (closed) out.write(quote);
      i = closed ? j + quote.length : bodyEnd;
    } else {
      out.write(source[i]);
      i++;
    }
  }
  return out.toString();
}

String? _quoteAt(String source, int i) {
  for (final q in const ["'''", '"""', "'", '"']) {
    if (source.startsWith(q, i)) return q;
  }
  return null;
}
