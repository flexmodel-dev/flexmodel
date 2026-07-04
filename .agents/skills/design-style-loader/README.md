# design-style-loader

A skill that forces AI coding agents to load the Flexmodel design system from
`DESIGN.md` **before** touching any UI, styling, component, or theme token
code. It guarantees every visual decision is sourced from the project's design
spec instead of invented on the fly.

## Why this exists

The repository ships a full design system in `DESIGN.md` (YAML front matter +
prose) and a TypeScript projection in `flexmodel-ui/src/theme/designTokens.ts`.
Without an explicit "read the spec first" step, agents tend to:

- Invent hex values that look right but aren't on-brand.
- Confuse `{colors.link}` (blue, for text links) with `{colors.primary}`
  (near-black, for the primary CTA) — the #1 mistake on this codebase.
- Use `{rounded.pill}` outside the pricing sub-system.
- Bold display type, add gradients to the hero, or add hover states — all
  explicitly forbidden by the spec.

This skill makes reading `DESIGN.md` the mandatory first move for any UI task.

## Install / Use

This is a project-local skill — no install needed. It lives at
`.agents/skills/design-style-loader/`. The agent runtime discovers it via the
`SKILL.md` front matter.

Trigger it (explicitly or implicitly) whenever the task involves:

- `flexmodel-ui/src/theme/*` — token or theme config changes
- `flexmodel-ui/src/pages/**` or `flexmodel-ui/src/components/**` — any
  component or page that renders UI
- Any file mentioning colors, fonts, border-radius, spacing, or Ant Design
  theme tokens

## What it enforces

1. **Read `DESIGN.md` first**, every session — never from memory.
2. **Read `designTokens.ts` second** to see the existing TypeScript projection.
3. **Prefer token exports** (`colors`, `typography`, `rounded`, `spacing`,
   `components`, `antdTheme`, `antdDarkTheme`) over raw hex literals.
4. **Honor the non-obvious brand rules** (primary CTA is near-black, not blue;
   no hover states; display type never bold; pill radius is pricing-only; etc.).
5. **Keep `DESIGN.md` and `designTokens.ts` in sync** — when one changes, the
   other changes in the same commit.

## Files

```text
design-style-loader/
├── SKILL.md          # main skill instructions (front matter + body)
├── README.md         # this file
└── agents/
    └── openai.yaml   # agent interface config (display name, default prompt)
```

## Boundaries

This skill is for **applying** the existing design system, not for redesigning
it. If the spec itself needs to change (new token, new component variant, new
sub-system), update `DESIGN.md` deliberately and document the rationale in
`progress.md`. Backend Java, GraphQL, build config, and non-visual logic are
out of scope.
