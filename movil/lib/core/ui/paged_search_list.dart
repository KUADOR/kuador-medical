import 'dart:async';

import 'package:flutter/material.dart';

import '../api/result_page.dart';
import 'module_widgets.dart';

/// Searchable, paged list of a module (`q`, `page`, `size` of the contract).
///
/// Reloads from the first page when the search text or [reloadKey] changes.
class PagedSearchList<T> extends StatefulWidget {
  const PagedSearchList({
    super.key,
    required this.fetch,
    required this.itemBuilder,
    required this.searchLabel,
    required this.emptyMessage,
    this.reloadKey,
    this.header,
  });

  final Future<ResultPage<T>> Function(String query, int page) fetch;
  final Widget Function(BuildContext context, T item) itemBuilder;
  final String searchLabel;
  final String emptyMessage;
  final Object? reloadKey;

  /// Widgets shown between the search field and the results (filters).
  final Widget? header;

  @override
  State<PagedSearchList<T>> createState() => _PagedSearchListState<T>();
}

class _PagedSearchListState<T> extends State<PagedSearchList<T>> {
  final _search = TextEditingController();
  Timer? _debounce;
  final List<T> _items = [];
  int _page = 0;
  int _total = 0;
  bool _loading = false;
  Object? _error;
  int _generation = 0;

  @override
  void initState() {
    super.initState();
    _load(reset: true, initial: true);
  }

  @override
  void didUpdateWidget(PagedSearchList<T> oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.reloadKey != widget.reloadKey) _load(reset: true);
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _search.dispose();
    super.dispose();
  }

  Future<void> _load({required bool reset, bool initial = false}) async {
    final generation = reset ? ++_generation : _generation;
    final page = reset ? 0 : _page + 1;
    void start() {
      _loading = true;
      _error = null;
      if (reset) {
        _items.clear();
        _total = 0;
      }
    }

    // `setState` is not allowed while the state is being initialized.
    initial ? start() : setState(start);
    try {
      final result = await widget.fetch(_search.text, page);
      if (!mounted || generation != _generation) return;
      setState(() {
        _items.addAll(result.items);
        _page = result.page;
        _total = result.total;
      });
    } on Object catch (e) {
      if (mounted && generation == _generation) setState(() => _error = e);
    } finally {
      if (mounted && generation == _generation) {
        setState(() => _loading = false);
      }
    }
  }

  void _onSearch(String _) {
    _debounce?.cancel();
    _debounce = Timer(
      const Duration(milliseconds: 400),
      () => _load(reset: true),
    );
  }

  @override
  Widget build(BuildContext context) {
    final error = _error;
    return RefreshIndicator(
      onRefresh: () => _load(reset: true),
      child: ListView(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 96),
        children: [
          TextField(
            controller: _search,
            onChanged: _onSearch,
            onSubmitted: (_) => _load(reset: true),
            textInputAction: TextInputAction.search,
            decoration: InputDecoration(
              labelText: widget.searchLabel,
              prefixIcon: const Icon(Icons.search),
              border: const OutlineInputBorder(),
            ),
          ),
          if (widget.header != null) ...[
            const SizedBox(height: 12),
            widget.header!,
          ],
          const SizedBox(height: 12),
          Semantics(
            liveRegion: true,
            child: Text(
              _loading && _items.isEmpty
                  ? 'Buscando…'
                  : (_total == 1 ? '1 resultado' : '$_total resultados'),
              style: Theme.of(context).textTheme.labelMedium,
            ),
          ),
          const SizedBox(height: 8),
          for (final item in _items) widget.itemBuilder(context, item),
          if (!_loading && error == null && _items.isEmpty)
            Padding(
              padding: const EdgeInsets.all(24),
              child: Text(widget.emptyMessage, textAlign: TextAlign.center),
            ),
          if (error != null)
            LoadErrorView(
              error: error,
              onRetry: () => _load(reset: _items.isEmpty),
            ),
          if (_loading)
            const Padding(padding: EdgeInsets.all(16), child: LoadingView()),
          if (!_loading && error == null && _items.length < _total)
            OutlinedButton(
              onPressed: () => _load(reset: false),
              child: const Text('Cargar más'),
            ),
        ],
      ),
    );
  }
}
