---
name: design-style-loader
description: >-
  Load and apply the Flexmodel design system before touching any UI, styling,
  component, or visual surface. Reads DESIGN.md as the authoritative spec,
  resolves token references ({colors.*}, {typography.*}, {rounded.*},
  {spacing.*}, {components.*}), and keeps generated code on-brand.
license: MIT
---

# Design Style Loader

Use this skill **before** writing or editing any visual code in `flexmodel-ui/`
(React components, Ant Design theme config, CSS-in-JS, inline styles, tokens,
or any file under `src/theme/`, `src/pages/`, `src/components/`). It guarantees
that every visual decision is sourced from the project's design system instead
of invented ad hoc.

Not for backend Java code, GraphQL schemas, build config, or non-visual logic.

## The Source of Truth

There is exactly one design spec in this repository:

| Artifact | Path | Role |
|---|---|---|
| Design spec | `DESIGN.md` (repository root) | **Authoritative** — human-readable design system with YAML front matter + prose |
| Token module | `flexmodel-ui/src/theme/designTokens.ts` | **Derived** — TypeScript export consumed by Ant Design v6 theme |
| Theme wiring | `flexmodel-ui/src/theme/*` | Runtime theme provider built on top of the token module |

`DESIGN.md` wins every conflict. `designTokens.ts` is a projection of it. If
you change a token in TypeScript, change it in `DESIGN.md` first; if you
reference a value in prose, reference the `DESIGN.md` token, never a raw hex.

## First Move — Always Read DESIGN.md First

Before writing a single line of styling code, do this in order:

1. **Read `DESIGN.md`** at the repository root with `read_file`. Do not skip
   this even if you "remember" the tokens — the spec is the contract, and it
   may have changed since your last session. Read the YAML front matter
   (tokens) AND the prose body (principles, do's/don'ts, responsive behavior).
2. **Read `flexmodel-ui/src/theme/designTokens.ts`** to see how the tokens are
   already projected into TypeScript (colors, typography, rounded, spacing,
   components, antdSeedTokens, antdMapTokens, sharedTokens, antdTheme,
   antdDarkTheme). Reuse these exports — do not duplicate hex values inline.
3. **Only then** start editing the target component or style file.

If `DESIGN.md` is missing or empty, stop and ask the user where the spec lives.
Do not guess tokens from screenshots or neighboring components.

## Token Reference Syntax

`DESIGN.md` uses a `{group.name}` reference syntax. Every value you write must
trace back to one of these references:

- `{colors.primary}` → near-black brand/CTA color (`#181d26`)
- `{colors.ink}` → strongest text (same hex as primary, different role)
- `{colors.canvas}` → default page surface (`#ffffff`)
- `{colors.signature-coral}` / `{colors.signature-forest}` / `{colors.surface-dark}` → full-bleed signature card surfaces
- `{colors.link}` → inline link blue (`#1b61c9`) — **NOT** the primary button color
- `{typography.display-xl|display-lg|display-md|title-lg|title-md|title-sm|label-md|button|body-md|caption|legal|pricing-*}` → type roles
- `{rounded.xs|sm|md|lg|pill|full}` → border radius scale
- `{spacing.xxs|xs|sm|md|lg|xl|xxl|section}` → spacing scale (4px base unit)
- `{components.<name>}` → component presets (e.g. `{components.button-primary}`)

When unsure which token a value maps to, grep `DESIGN.md` for the hex or the
role name — never eyeball it.

## Non-Obvious Brand Rules (Easy to Get Wrong)

These are the most common mistakes. Internalize them before generating code:

1. **Primary CTA is near-black, not blue.** `{colors.primary}` (`#181d26`) is
   the primary button background. `{colors.link}` (`#1b61c9`) is ONLY for
   inline text links. The CSS variable name
   `--theme_button-background-primary` is misleading — it maps to the link
   color, not the primary button. Do not be fooled.
2. **One primary CTA per viewport.** `button-primary` is scarce by design.
   Secondary actions use `button-secondary` (white canvas + hairline outline).
3. **Display type is never bold.** `{typography.display-lg}` is weight 400;
   `{typography.display-xl}` is weight 500. Emphasis comes from size and color
   contrast, never from weight 600/700. The only 600 weight lives in
   `{typography.legal}`.
4. **Hero is white canvas.** No gradient, no mesh, no aurora, no atmospheric
   backdrop. Whitespace is the hero atmosphere.
5. **`{rounded.pill}` is pricing-sub-system only.** Do not use pill radius
   outside pricing surfaces.
6. **No hover states.** The system documents Default and Active/Pressed only.
   Do not add hover styling beyond what already exists.
7. **Section rhythm is `{spacing.section}` (96px).** Every major editorial band
   uses 96px top + 96px bottom padding. Card padding varies by card type —
   check `{components.*}` for the exact value.
8. **Pricing is its own dialect.** Pricing surfaces use Inter Display at weight
   475/575 and `{rounded.pill}` buttons. Do not mix Haas Grotesk button type
   into pricing, and do not bleed pill radius back into the editorial system.
9. **Font substitutes.** Haas Grotesk / Haas Groot Disp are licensed. The
   TypeScript projection uses **Inter / Inter Display** as the open-source
   substitute. Keep that substitution — do not swap to system-ui alone.

## Working Rules

- **Prefer token exports over raw values.** Import from
  `flexmodel-ui/src/theme/designTokens.ts` (`colors`, `typography`, `rounded`,
  `spacing`, `components`, `antdTheme`, `antdDarkTheme`). Raw hex literals in
  component files are a smell — only acceptable when a token genuinely does not
  exist, and then you should propose adding the token to `DESIGN.md`.
- **Reference tokens in prose, hex in code.** When explaining a decision in a
  comment or PR, cite `{colors.signature-coral}`. When writing TypeScript, use
  `colors['signature-coral']`.
- **Never introduce an undocumented accent color.** The signature palette is
  coral, forest, dark navy, cream, peach, mint, yellow, mustard. If you need a
  new color, add it to `DESIGN.md` first.
- **Dark mode is a projection, not a redesign.** `antdDarkTheme` already lifts
  the near-black primary to a readable blue (`#458fff`) because the original
  primary is invisible on dark backgrounds. Do not hand-roll dark overrides —
  extend `antdDarkTheme` instead.
- **When DESIGN.md and code disagree, fix the code.** If a component uses a
  value that contradicts `DESIGN.md`, treat it as a bug and align it to the
  spec. If the spec itself is wrong, update `DESIGN.md` in the same change.

## Verification Checklist

Before claiming a UI change is done:

- [ ] `DESIGN.md` was read in this session (not from memory)
- [ ] Every color, radius, spacing, and font value traces to a `{token}`
- [ ] No new raw hex literals introduced unless a token was added to `DESIGN.md`
- [ ] No hover states added
- [ ] Primary CTA is `{colors.primary}` (near-black), not `{colors.link}`
- [ ] `{rounded.pill}` used only on pricing surfaces
- [ ] Display type weights stay at 400/500
- [ ] Section padding uses `{spacing.section}` where applicable
- [ ] `npx tsc --noEmit` passes in `flexmodel-ui/` (token imports resolve)
- [ ] If tokens changed, `DESIGN.md` and `designTokens.ts` were updated together

## When to Escalate

- **Token genuinely missing:** Propose the addition in `DESIGN.md` (YAML front
  matter + prose mention) before using it in code.
- **Spec ambiguity:** Read the "Do's and Don'ts" and "Known Gaps" sections of
  `DESIGN.md`. If still unclear, ask the user rather than guessing.
- **Contradiction between `DESIGN.md` and `designTokens.ts`:** `DESIGN.md`
  wins. Fix the TypeScript. Note the discrepancy in `progress.md`.
