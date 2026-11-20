import 'package:flutter/material.dart';

import '../tokens.g.dart';
import 'kd_icon.dart';
import 'kd_style.dart';

/// A group of [KdListTile] rows on surface.raised with radius `lg`, a
/// hairline border and hairline dividers between rows (DESIGN.md, R-03).
///
/// For long lists build the rows lazily with a `ListView.builder` of
/// [KdListTile] instead; this group is for short, fixed sets (menus,
/// details, a page of results).
class KdListSection extends StatelessWidget {
  const KdListSection({super.key, required this.children, this.title});

  final List<Widget> children;

  /// Optional heading above the group, announced as a header.
  final String? title;

  @override
  Widget build(BuildContext context) {
    final t = KdTokens.of(context);
    final heading = title;
    final group = DecoratedBox(
      decoration: BoxDecoration(
        color: t.surfaceRaised,
        borderRadius: BorderRadius.circular(t.radiusLg),
        border: Border.fromBorderSide(t.borderHairline),
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(t.radiusLg),
        child: Material(
          type: MaterialType.transparency,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              for (var i = 0; i < children.length; i++) ...[
                if (i > 0)
                  Divider(
                    height: t.borderHairline.width,
                    thickness: t.borderHairline.width,
                    color: t.borderHairline.color,
                    indent: t.space4,
                    endIndent: t.space4,
                  ),
                children[i],
              ],
            ],
          ),
        ),
      ),
    );
    if (heading == null) return group;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Padding(
          padding: EdgeInsets.only(left: t.space1, bottom: t.space2),
          child: Semantics(
            header: true,
            child: Text(
              heading,
              style: t.typeLabel.copyWith(color: t.textMuted),
            ),
          ),
        ),
        group,
      ],
    );
  }
}

/// List row: optional leading icon or widget (avatar), title, optional
/// subtitle and trailing widget. At least 56 tall and full width, so the tap
/// area always exceeds 48.
///
/// With [onTap] it is a button and shows a chevron unless [trailing] is
/// given. [selected] uses the soft selection background and an accent bar at
/// the start (R-10), announced as selected.
class KdListTile extends StatelessWidget {
  const KdListTile({
    super.key,
    required this.title,
    this.subtitle,
    this.leadingIcon,
    this.leading,
    this.trailing,
    this.onTap,
    this.selected = false,
  });

  final String title;
  final String? subtitle;
  final KdIconData? leadingIcon;

  /// Custom leading widget (an avatar); wins over [leadingIcon].
  final Widget? leading;
  final Widget? trailing;
  final VoidCallback? onTap;
  final bool selected;

  /// Minimum row height: the tap target plus a space.3 margin.
  static const double minHeight =
      KdConstants.sizeTargetMin + KdConstants.space3;

  @override
  Widget build(BuildContext context) {
    final t = KdTokens.of(context);
    final icon = leadingIcon;
    final lead =
        leading ??
        (icon == null
            ? null
            : KdIcon(icon, color: selected ? t.colorAccent : t.textMuted));
    final sub = subtitle;
    final end =
        trailing ??
        (onTap == null
            ? null
            : KdIcon(KdIcons.chevronRight, color: t.textMuted));

    final row = ConstrainedBox(
      constraints: const BoxConstraints(minHeight: minHeight),
      child: Padding(
        padding: EdgeInsets.symmetric(horizontal: t.space4, vertical: t.space3),
        child: Row(
          children: [
            if (lead != null) ...[lead, SizedBox(width: t.space3)],
            Expanded(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title, style: t.typeBody.copyWith(color: t.textDefault)),
                  if (sub != null)
                    Text(sub, style: t.typeBodySm.copyWith(color: t.textMuted)),
                ],
              ),
            ),
            if (end != null) ...[SizedBox(width: t.space2), end],
          ],
        ),
      ),
    );

    final background = selected
        ? KdStyle.selectionTint(t, t.surfaceRaised)
        : null;
    // Ink, not DecoratedBox, so the press ripple shows over the selection.
    final decorated = Ink(
      decoration: BoxDecoration(
        color: background,
        border: selected
            ? BorderDirectional(
                start: BorderSide(
                  color: t.colorAccent,
                  width: KdStyle.focusRingWidth * 2,
                ),
              )
            : null,
      ),
      child: row,
    );

    final tap = onTap;
    return Semantics(
      container: true,
      button: tap != null,
      selected: selected ? true : null,
      child: tap == null
          ? decorated
          : Material(
              type: MaterialType.transparency,
              child: InkWell(
                onTap: tap,
                focusColor: t.colorFocus.withValues(
                  alpha: KdStyle.selectionTintOpacity,
                ),
                child: decorated,
              ),
            ),
    );
  }
}
