# Self-hosted fonts

Web font files (`.woff2` only) of the family chosen in the identity guide, with its license file
next to them (e.g. `OFL.txt`). The license must allow embedding and self-hosting (plan risk R4).

- Declare each file in `src/styles/fonts.css` with a relative `url('../assets/fonts/…')`: the build
  copies it with a content hash under `/media/`, which nginx serves with a one-year immutable cache.
- Never link a font CDN: the style lint rejects remote URLs.
- Name files `<family>-<weight>[-italic].woff2`, lowercase with hyphens.
