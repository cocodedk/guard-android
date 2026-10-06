# 05 — The Blade Runner look and the dashboard

## Goal

The owner asked for two things: the numbers must read like a **dashboard** ("better for
understanding than reading among the text for numbers"), and the app must look **dazzling, anno
3000, Blade Runner style** — the whole app, not only the dashboard. This spec restyles every screen
in a neon-noir look and replaces the counter sentences with stat tiles and a list-status panel. No
new permission, no new library, no new font file. Blind-friendliness is not traded for looks.

Visual reference (the owner approved it): `docs/lean/design/05-blade-runner-mock.html`. Open it in a
browser; its colors, cut corners, glows, tile layout, list panel and motion are what to match. It is
a mock: where it and this spec differ, this spec wins.

Builds on specs 01–04 as merged. Read `CLAUDE.md` first; its rules apply, including accessibility
and the 200-line limit.

## Palette (`ui/theme/GuardColors.kt`, the only place colors live)

| Token | Hex | Used for |
|---|---|---|
| Night | `#070B14` | screen background (also `res/values/colors.xml` `night`) |
| Panel | `#0E1424` | cards, tiles, panels |
| OnNight | `#EEF3FF` | body text, tile values |
| OnNightQuiet | `#A9B4CC` | secondary text, labels |
| Ok | `#35C47C` | "protected", active lists, Start button fill |
| Notice | `#F0A43A` | paused lists, notifications-off card |
| Urgent | `#F07478` | stopped, refused, lockdown, Private DNS cards |
| Cyan | `#24E5F2` | neon accent: first tile, promises panel, outlined secondary buttons, sweep |
| Magenta | `#FF3D8B` | neon accent: second tile, Stop button outline |
| Amber | `#FFB347` | neon accent: list panel border and title |

`ok` in `colors.xml` stays `#35C47C`; `night` becomes `#070B14` (the existing
`xmlColorsMatchThePalette` test keeps them in step). Measured contrast, every text color on Night /
Panel: OnNight 17.7 / 16.5, OnNightQuiet 9.5 / 8.8, Ok 8.8 / 8.2, Notice 9.4 / 8.8, Urgent 7.0 / 6.5,
Cyan 12.7 / 11.9, Magenta 5.9 / 5.5, Amber 11.1 / 10.3; Night on Ok 8.8. Add Cyan, Magenta and
Amber to `ContrastTest.textColorsReadOnBothBackgrounds`. Text never sits on a glow or a gradient
brighter than Panel.

## Look (match the mock)

- **Background:** a vertical gradient from `#0A0F1E` to Night, with three soft radial tints
  (Magenta top-right, Cyan upper-left, Amber bottom, each at most 22% alpha), drawn once behind the
  scrolling column. On top of the background only, never over text: faint diagonal **rain** lines
  (Canvas, 22% alpha, slow fall) and faint horizontal **scanlines** (10% alpha).
- **Shapes:** `ui/theme/Shapes.kt` defines `NeonCut` = `CutCornerShape(topEnd = 16.dp, bottomStart =
  16.dp)` for tiles and cards, and `NeonCutSmall` (12.dp) for list panels. Buttons keep rounded
  corners of 4.dp.
- **Borders and glow:** every card, tile and panel has a 1.dp border in its accent color at 50–60%
  alpha, and a colored glow from `Modifier.shadow(elevation = 12.dp, shape, ambientColor = accent,
  spotColor = accent)` (colored shadows work from API 28; minSdk is 29). Which accent:
  tile 1 Cyan, tile 2 Magenta, list panel Amber, "Godt at vide" panel Cyan, warning cards by meaning
  (Urgent for refused/stopped/lockdown/Private DNS, Notice for notifications off, Cyan for always-on).
- **Type:** headings, the status word, tile tags, panel titles and button labels use the system's
  condensed sans (`FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))` — built into
  Android, no font file), shown in UPPER CASE with wide letter spacing (title 1.sp, tags and panel
  titles 3–4.sp). Body text, labels and list rows stay in the regular sans for reading. Tile values
  are 48.sp semibold regular sans with proportional digits. Upper case is visual only: each such
  text keeps its normal-case string as its `contentDescription`/semantics text, so TalkBack never
  spells capitals letter by letter.
- **Buttons:** Start protection = filled Ok with Night text and an Ok glow (the one bright fill on
  the screen); Stop protection = Magenta 1.dp outline on a 14% Magenta tint with OnNight text and a
  Magenta glow; every secondary button (About, About-page links, card actions) = Cyan 1.dp outline,
  Cyan text, transparent fill. All keep at least 48.dp height and full width.
- **Status orb** (replaces the status icon, left of the status word, 64.dp, decorative,
  `contentDescription = null`): a radial glow in the status tone with the shield glyph inside. When
  Protected (either Protected row of spec 01): a slow outward ping ring (3.2 s) and a Cyan sweep
  (`Brush.sweepGradient`, one turn per 4 s). In every other state: static, no ping, no sweep, tinted
  Notice or Urgent per spec 01's tone.

## Motion — and switching it off

- Motion: rain, the ping ring, the sweep, and a count-up of each tile value (`animateIntAsState`,
  600 ms, ease-out) when it changes. Nothing flashes, blinks or strobes (WCAG 2.3.1): no element
  changes brightness more than three times a second.
- `ui/fx/Motion.kt`: `fun motionAllowed(animatorScale: Float): Boolean = animatorScale > 0f`, fed with
  `Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)` (Android's
  "Remove animations" sets it to 0). When motion is not allowed: no rain (lines static at the same
  alpha, or not drawn), no ping, no sweep, values change without counting.
- TalkBack never hears an animated in-between number: a tile's semantics always carry the target
  value.

## The dashboard (`ui/Dashboard.kt`)

Replaces spec 02/03's counter text block (the `counter`, `counter_addresses`, `list_line`,
`address_blocking_off` and `address_list_*` lines) and appears exactly where it was: in the two
Protected rows of spec 01, after the cards and before Recent blocks. Recent blocks itself keeps its
content and gets the panel look (Cyan border).

1. **Two stat tiles** in a row (equal width, 12.dp gap):
   - Tile 1 (Cyan): tag `tile_lookups_tag`, value = `blockedCount`, label `tile_lookups_label`.
   - Tile 2 (Magenta): tag `tile_connections_tag`, value = `blockedAddressCount`, label
     `tile_connections_label`.
   Values are grouped with the locale (`NumberFormat.getIntegerInstance()`). Each tile is one
   TalkBack stop whose text is `tile_lookups_spoken` / `tile_connections_spoken` with the value. Not
   a live region. When the system font scale is 1.5 or more (`LocalDensity.current.fontScale >=
   1.5f`), the tiles stack vertically, full width.
2. **List panel** (Amber, `NeonCutSmall`): title `lists_title` (heading), then one row per list in
   this order: the name list (AdGuard DNS filter, always active while protected), then the three
   address lists in `AddressList` order. Each row: a small diamond marker (a 10.dp square rotated
   45°, filled Ok for active, filled Notice for paused, an OnNightQuiet outline for not yet), the
   list name, and the state in words on the right:
   - name list: `list_names_active` with the rule count;
   - address list Active: `list_active` with the entry count and the fetch date (medium date);
   - TooOld: `list_paused` with the fetch date;
   - NotYet: `list_not_yet`.
   Each row is one TalkBack stop reading name then state. State words are colored (Ok / Notice /
   OnNightQuiet) and never stand alone without the marker and the words.
3. If no address list is active, a line under the panel: `address_blocking_off` (kept, quiet text).

Pure logic (JVM-tested): `ui/DashboardModel.kt`

```kotlin
enum class ListLook { Active, Paused, NotYet }
data class ListRow(val name: String, val look: ListLook, val count: Int?, val fetched: Long?)
data class Dashboard(val lookups: Int, val connections: Int, val rows: List<ListRow>, val addressBlockingOff: Boolean)
fun dashboard(state: ProtectionState): Dashboard
```

## The rest of the app in the new look

Home: title in condensed upper case; status orb + status word (condensed, upper case, tone color
with a soft glow) + detail; cards as `NeonCut` panels with meaning-colored borders; the dashboard;
Recent blocks; the primary button; "Godt at vide" as a Cyan panel; the About button (Cyan outline).
About page: same background, section titles condensed upper case in Cyan, link buttons Cyan
outline, credits in quiet text. Notifications are unchanged (Android draws them).

## Strings (Danish in `values/`, English in `values-en/`)

| Key | Danish | English |
|---|---|---|
| tile_lookups_tag | Opslag | Lookups |
| tile_lookups_label | blokeret, siden beskyttelsen startede | blocked since protection started |
| tile_lookups_spoken | %1$s blokerede DNS-opslag, siden beskyttelsen startede | %1$s blocked DNS lookups since protection started |
| tile_connections_tag | Forbindelser | Connections |
| tile_connections_label | til farlige adresser, afvist | to dangerous addresses, refused |
| tile_connections_spoken | %1$s blokerede forbindelser til farlige adresser, siden beskyttelsen startede | %1$s blocked connections to dangerous addresses since protection started |
| lists_title | Lister | Lists |
| list_names_active | %1$s navne | %1$s names |
| list_active | %1$s · hentet %2$s | %1$s · downloaded %2$s |
| list_paused | Sat på pause · hentet %1$s | Paused · downloaded %1$s |
| list_not_yet | Ikke hentet endnu | Not downloaded yet |

Remove the now-unused keys `counter`, `counter_addresses`, `list_line`, `address_list_active`,
`address_list_too_old` and `address_list_not_yet` from both files. Keep `address_blocking_off`. If
`PromiseStringsTest` lists required keys, update its set accordingly.

## Files

- `ui/theme/GuardColors.kt`, `ui/theme/Theme.kt` (typography: the condensed family for display
  styles), `ui/theme/Shapes.kt` (new), `res/values/colors.xml`.
- `ui/fx/NeonBackground.kt` (gradient, tints, rain, scanlines), `ui/fx/StatusOrb.kt`,
  `ui/fx/Motion.kt`, `ui/fx/Neon.kt` (a `Modifier.neonPanel(accent, shape)` for border + glow +
  background, used by every card/tile/panel).
- `ui/Dashboard.kt`, `ui/DashboardModel.kt` (new); `ui/HomeScreen.kt`, `ui/HomeCards.kt`,
  `ui/PromisesSection.kt`, `ui/RecentBlocksSection.kt`, `ui/AboutScreen.kt` restyled.
- Every code file stays under 200 lines; split further if needed.

## Acceptance tests (JVM, offline)

- `ui/DashboardModelTest`: `countsComeFromState`; `nameListFirstThenAddressListsInOrder`;
  `activeListHasCountAndDate`; `tooOldIsPausedWithDate`; `notYetHasNoCountOrDate`;
  `addressBlockingOffWhenNoListActive`.
- `ui/fx/MotionTest`: `zeroScaleMeansNoMotion`, `normalScaleAllowsMotion`.
- `ContrastTest`: the three new accents on Night and Panel; `xmlColorsMatchThePalette` with the new
  Night.
- `HomeUiTest` and the other existing tests keep passing (update only what the removed string keys
  require).

`./gradlew buildSmoke --no-daemon` is the gate.

## Checked by hand on the owner's phone (not part of the gate)

The home screen and the About page look like the mock; with TalkBack each tile reads as one
sentence with the real number and each list row as name then state; capitals are not spelled out;
with "Remove animations" on, nothing moves; at the largest font size the tiles stack and nothing
clips.

## Out of scope

History, trends or charts over time (the app keeps no history), sound, haptics, a light theme,
custom font files, restyling the website (the site keeps the box's look for now).
