# Plan — Floating Frosted-Glass Nav Bar + Pro Home UI

**Decisions (from you):** True frosted blur via **Haze** · Scope = **glass nav bar (all tabs) + deep polish on Home** · **Lock brand palette** (navy `#1E3A5F` + gold `#8A6D00`).

---

## The core problem to fix
Today every tab is a separate Activity using:
```
Scaffold(bottomBar = { AttendMateNavigationBar(...) }) { padding -> content(padding) }
```
`Scaffold` **reserves** space for the bar, so content stops *above* it. To make content scroll **behind** the bar and **frost** through it, the content and the bar must be siblings in one `Box`, share a `HazeState`, and the bar must be drawn on top.

## The fix — a reusable glass wrapper
New composable `GlassNavScaffold(selectedRoute) { bottomPadding -> content }`:
- Creates one `HazeState`.
- `Box(fillMaxSize)`:
  - **content layer** with `Modifier.haze(hazeState)` (the blur source), edge-to-edge.
  - **AttendMateNavigationBar** overlaid at bottom-center with `Modifier.hazeChild(...)` (the frosted glass).
- Hands the content a **bottom padding** (≈120dp = bar height + FAB overhang + margins) so lists can scroll clear and nothing hides permanently behind the bar.

---

## Files & changes

**1. `gradle/libs.versions.toml` + `app/build.gradle.kts`** — add `dev.chrisbanes.haze:haze`.
   - *Gated first step:* sync/compile to confirm the version resolves against Kotlin 2.0.21 / Compose 1.7.
   - *Fallback if it won't resolve:* the bar falls back to a translucent tinted surface (no dependency) — same content-behind behavior, just without real blur. One-line switch, and I'll tell you if we hit this.

**2. `ui/theme/Theme.kt`** — default `dynamicColor = false` so your navy+gold brand always wins (no wallpaper override on Android 12+).

**3. `ui/theme/DesignSystem.kt`** — add glass tokens: `NavBarHeight`, `NavContentBottomPadding`, `GlassTintAlpha`, `GlassBlurRadius`, `GlassBorder`.

**4. `ui/components/AttendMateNavigationBar.kt`** — make it the frosted centerpiece:
   - Accept optional `hazeState`; apply `hazeChild` for real backdrop blur.
   - Translucent tint + hairline top-light border + soft ambient shadow (glass depth).
   - Keep 4 items + center Add FAB; refine active state with a brand-colored pill/indicator + spring scale.
   - Keeps `navigationBarsPadding()` so it floats above the gesture pill.

**5. `ui/components/GlassNavScaffold.kt` (NEW)** — the wrapper above.

**6. `MainActivity.kt`** — swap `Scaffold(bottomBar)` → `GlassNavScaffold("home") { pad -> HomeScreen(pad) }`.

**7. `AnalyticsActivity.kt` + `AttendanceListActivity.kt`** — swap to `GlassNavScaffold` so their content also scrolls behind the glass bar (behavior only, visuals untouched) and add matching bottom padding so nothing is clipped.

**8. `ui/screens/MainScreen.kt` — Home polish (the "pro" pass):**
   - `HomeScreen(bottomPadding)` → feed padding into the `LazyColumn` `contentPadding`.
   - **Header:** cleaner greeting → name hierarchy, avatar, date chip; drop the heavy card border/shadow for a lighter, intentional look.
   - **Summary card:** tighten to design tokens, brand-colored ring/stats, calmer spacing.
   - **Section header + lecture cards:** consistent radii/spacing/borders, brand accents.
   - Confirm the last card scrolls fully clear of the floating bar.

---

## Verification
- `./gradlew :app:assembleDebug` compiles clean.
- Manual check: content visibly scrolls behind **and frosts** under the bar; last list item reachable; bar floats above gesture nav; light + dark both correct.

## Risks
- **Haze version drift** (web was blocked this session) → gated build step + translucent fallback, no guesswork left in the tree.
- **minSdk 26**: Haze auto-degrades to a tint below API 31 — expected and acceptable.

## Out of scope (this pass)
Deep visual redesign of Attendance / Analytics / Settings screens — they get the glass-nav behavior now; we can polish them next.
