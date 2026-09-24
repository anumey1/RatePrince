# RatePrince — Project Status & Blueprint (`stats.md`)

> **⚠️ LIVING DOCUMENT — KEEP IT CURRENT.**
> This is the single source of truth for *where RatePrince stands right now*. A new session should
> be able to read this file alone and start working correctly. **If you change the app — a file, a
> screen, a dependency, a behaviour, a rule, a test, or fix a bug — update this document in the same
> change:** the relevant section, §1 (pending), §16 (history) and the *Last updated* line. A stale
> `stats.md` is worse than none. Every claim here was checked against the code on the date below;
> keep it that way.

**Last updated:** 2026-09-24 · **Version:** 0.1 (`versionCode` 1), shown in Settings as
"RatePrince v0.1 (1)"; never released, and **publishing is deferred** (owner ruling, §13).
**Status:** v1 of `Docs/RateTD.md` (its Phases 0–4) is **built**, plus the owner's changes after it
(the 34/66 widget keypad, the compact reference grid, the launcher icon). The owner uses the app on
their own phone, which is treated as **production**: it runs the R8-optimised **release** build.
The owner tests on the phone themselves; the assistant does not drive a device unless asked (§13).

> **⚠️ Working tree ≠ `HEAD`.** The last commit is `b314ab8 "Phased"` (cleanup, v0.1, the
> release-build crash fix). **Uncommitted:** the launcher icon (§9), the trimmed `README.md` and
> this file. The icon build **is installed on the owner's phone** (2026-09-24).

**Companion documents** — read them for depth, not for status:
- `Docs/RateTD.md` — the original technical design, written when the app was called **"Rate"**
  (package `com.dicereligion.rate`). The code uses RatePrince names throughout. Where the TD and
  this file disagree, this file wins; §12 lists every deviation.
- `README.md` — the user-facing overview: what the app does and how it's structured. Owner ruling:
  nothing else goes in it (no progress, no docs, no build steps).

---

## Working rules for this repo (read first)

1. **Never `git commit`, `git push` or `git merge`.** The owner does all three. Checkout, restore,
   diff and log are fine. Don't stage files either (`git rm --cached`, `git mv` stage). When work is
   ready, say so and let the owner commit.
2. **Don't drive a device or the emulator unless the owner asks.** Verify with the build, unit tests
   (including the Robolectric widget tests) and lint. At the end of a change, list what the owner
   should check on their phone. The owner stopped a 30+ minute emulator session over this.
3. **The owner's phone is production.** Give it the **release** build
   (`app/build/outputs/apk/release/app-release.apk`), and only install when asked:
   `adb -s 46281FDAS008EG install -r …`. It's signed with the local debug key, so it installs over a
   debug build and keeps the app's data.
4. **The owner decides product questions.** Rulings are in §13, and parked or ignored items in §1.
   Don't reopen or re-propose them without a reason.
5. **Report results honestly.** If something wasn't verified (on a device, or at all), say so.

---

## 0. Status at a glance

| Area | State |
|---|---|
| Converter screen (amount, result, swap, caption, reference grid) | **Done** |
| First-run "Set your exchange rate" card | **Done** (2 taps to a working converter, checked on the emulator in Phase 2) |
| Settings (currency pair, swap, two-way rate editor, Add widget, version label) | **Done** |
| Currency picker (152 currencies, search, 5 recents) | **Done** |
| Widget: 2×1 single line, 3×2 stacked, wide (≥ 320 dp) with swap | **Done**; checked on the emulator's Pixel launcher in Phase 3 |
| Widget: quick-convert overlay (tap the amount) | **Done** |
| Widget: keypad (34% amount/result, 66% 3×4 keys) | **Done**; owner approved the layout on the phone ("Perfection") |
| Several widgets with independent amounts | **Done**; checked on the emulator in Phase 3 |
| Add widget from the app (pin), manual fallback | **Built**; not yet confirmed by the owner on the phone |
| Android 15+ generated picker preview | **Built**; not yet confirmed by the owner |
| Release build (R8 + resource shrinking) | **Works**, 4.5 MB (debug 38 MB). The startup crash is fixed (§10.2); no crash in the phone's log after install |
| Launcher icon | **Built and installed on the phone, uncommitted** (§9). Owner hasn't confirmed the look yet |
| Tests | **104 JVM test runs, 0 failures** (94 test methods; the widget layout suite runs on API 30 and 36). 3 instrumented tests. Lint: 0 errors |
| Publishing (Play Store) | **Deferred** (owner ruling) |

---

## 1. PENDING — the complete open list

### Open
- [x] ~~Install the icon build on the phone~~ (installed 2026-09-24).
- [ ] Owner to confirm the **icon** in the launcher and the widget picker. SystemUI caches icons by
      `versionCode`, so a launcher restart or reboot may be needed to see it.
- [ ] Owner to confirm on the phone: **Add widget** from the app (card on the Converter, button in
      Settings), and the **Android 15+ picker preview** showing their own pair and rate.

### Parked or ignored (owner ruling, 2026-09-24): don't propose these
- **Publishing:** deferred. No upload key, Play listing, privacy policy or store assets.
- **Live rates** (TD Phase 5): parked.
- **TD Phase 6 backlog:** parked. That's a reference ladder scaled to the currency's magnitude (the
  46 fixed amounts suit JPY/KRW/VND, not USD/EUR), per-widget currency pairs, a tip/GST helper, and
  a Wear OS tile.
- **Remaining TD QA:** ignored. That's the TalkBack pass, 150–200% font scale, fr-FR locale, the
  light/dark wallpaper matrix, other launchers (Samsung, Nova, Xiaomi), and the instrumented tests
  for the overlay and for multi-instance.
- **Housekeeping:** ignored. That's the currency-list review, dependency upgrades (lint lists Kotlin
  2.4.20, AGP 9.4.1, Gradle 9.7.1 and others), targetSdk 37, and `.idea/deploymentTargetSelector.xml`
  still being tracked by git.
- **Monochrome launcher icon** (lint `MonochromeLauncherIcon`): not built. EdgeCase and ShadeCase
  don't have one either; it would need a one-colour silhouette of the badge.

---

## 2. What RatePrince is

A single-purpose, **offline** currency converter built around a resizable home-screen widget. The
exchange rate is **user-owned**: you type the rate you actually got, and everything derives from
it. There is no network code and no `INTERNET` permission.

| ID | Requirement (TD §1.2) | Where |
|---|---|---|
| FR-1 | Widget shows an amount area on top and the result below | `widget/WidgetContent.kt` |
| FR-2 | Any number entered converts local → home | `domain/ConversionEngine.kt` |
| FR-3 | Tapping the widget opens the app on the Converter | `WidgetIntents.openConverter` |
| FR-4 | Converter: the same pair plus 46 reference amounts (1 … 100,000) | `ui/converter/`, `domain/Ladder.kt` |
| FR-5 | Settings: home currency, local currency, manual rate | `ui/settings/`, `ui/picker/`, `ui/rate/` |
| FR-6 | Widget resizable, multi-instance, independent state | `SizeMode.Exact`, per-instance Glance state |
| FR-7 | Add the widget from inside the app, as well as from the picker | `widget/WidgetPlacement.kt` |

**Vocabulary:** *home* = the currency you think in (default INR); *local* = where you are (default
JPY); *rate* = **home units per 1 local unit**, always stored in that direction.

---

## 3. How it works

### 3.1 Money maths (the core invariant)
- **`BigDecimal` only, never `Double`** (`0.58 × 3` must be `1.74`).
- `ConversionEngine.convert(amount, config, targetFractionDigits)` multiplies at
  `MathContext(20, HALF_UP)` and rounds **once**, to the **target** currency's digits, `HALF_UP`.
- **One code path:** the converter, the reference grid (`buildLadder`), the widget, and the overlay
  all call `convert()`. `ConversionEngineTest` asserts the ladder and the live field agree for every
  reference amount across a JPY/INR/KWD/IDR matrix.
- **Fraction digits come from the bundled catalogue**, not `java.util.Currency` (JPY 0, INR 2,
  KWD 3, IDR 2 per ISO 4217).
- **Rate direction:** stored as home per local. The UI can show the inverse; `invertRate()`
  (`domain/RateMath.kt`) inverts at 10 significant digits, so a double swap returns the exact rate.

### 3.2 Input parsing (`domain/AmountParser.kt`)
- `AmountParser`: up to 12 integer and 6 decimal digits. `.` and `,` both count as the decimal
  separator; spaces and no-break spaces are ignored; no grouping, signs or exponents. `parse()`
  returns null for "not yet a number" ("", "."); `isEditable()` accepts intermediate states like
  "12.".
- `RateParser`: the same rules with **8 decimals** (a KWD-home/IDR-local rate is about 0.0000189),
  and it must be > 0.

### 3.3 Formatting (`domain/MoneyFormatter.kt`)
- Not thread-safe; **create one per screen, emission or composition**.
- **Grouping is applied by the class itself** (`group()`), because JVM `java.text` can't do lakh
  grouping and Android's ICU-backed one might, which would make tests and phone disagree. Regions
  IN, BD, NP and PK get lakh grouping (3, then 2s); others get 3s. The locale's separator is used.
- Symbols come from the catalogue (`currencySymbol` is overridden). Letter symbols get a no-break
  space before digits ("Rs 1,000.00"), because `java.text` doesn't apply CLDR currency spacing.
- `format()` (with symbol), `formatPlain()` (no symbol), `formatAmount()` (typed amount, its own
  decimals), `formatTyped()` (raw input for display, keeping a trailing separator: "12."), and
  `formatRate()` (6 significant digits: 0.0058 stays "0.0058", not the TD's "0.01").

### 3.4 State and data flow
- **Global config** (`RateConfig`: home, local, rate, updatedAt, source) lives in **one DataStore**
  behind `RateConfigRepository`, exposed as `Flow<RateConfig>`.
- **Per-widget state** (the typed amount, keypad open) lives in **Glance's per-instance
  Preferences state**, keyed by `GlanceId` and deleted with the widget.
- **Every repository mutation calls `onConfigChanged`**, wired in `AppContainer` to
  `RatePrinceWidget().updateAll()` plus `WidgetPlacement.publishPreviews()`. No settings path can
  leave a widget stale.
- **Pair changes:** picking the exact reverse of the current pair is a **swap** (the rate inverts);
  any other change **clears the rate to 0**, so a JPY rate is never applied to USD. Picking the
  other slot's currency in the picker also swaps (`resolvePick`). Home equal to local is refused.
- **A rate of 0 means "not configured"**: the app shows the NeedsRate card, and the widget shows
  "Tap to set your rate".

### 3.5 Dependency wiring
There's no DI framework. `RatePrinceApplication` creates an `AppContainer`, reached through
`Context.container`. It holds `applicationScope`, `widgetPlacement`, `rateConfigRepository`,
`recentCurrencies`, `conversionEngine` and `currencyCatalog` (a `lazy` parse of the asset).
`onCreate` launches `publishPreviews()`.

---

## 4. File map

Package `com.dicereligion.rateprince`; line counts as of the date above.

| File | Lines | Role |
|---|---:|---|
| `MainActivity.kt` | 20 | Edge-to-edge host; `RatePrinceTheme { RatePrinceNavHost() }` |
| `RatePrinceApplication.kt` | 61 | Application plus `AppContainer` (§3.5) |
| `domain/model/Currency.kt` | 19 | `CurrencyCode` (value class, 3 uppercase letters) and `CurrencyMeta` (code, name, symbol, digits, flag) |
| `domain/model/RateConfig.kt` | 17 | `RateConfig` with `isUsable` (rate > 0), and `RateSource {MANUAL, FETCHED_REMOTE}` |
| `domain/ConversionEngine.kt` | 29 | `convert()` (§3.1) |
| `domain/AmountParser.kt` | 43 | `AmountParser` and `RateParser` (§3.2) |
| `domain/MoneyFormatter.kt` | 145 | Formatting and grouping (§3.3); `usesLakhGrouping()`, `withCurrencySpacing()` |
| `domain/Ladder.kt` | 57 | `LadderSpec.DEFAULT` (46 amounts), `LadderRow`, `buildLadder()` |
| `domain/RateMath.kt` | 24 | `invertRate()`, `rateForEditing()` |
| `data/CurrencyCatalog.kt` | 81 | Parses `assets/currencies.json`; `meta()` (with a JDK fallback), `search()` (code prefix first, then name); flag emoji derived from the code (none for X-codes) |
| `data/StoredRateConfig.kt` | 65 | On-disk shape, JSON `Serializer` (corruption becomes a `CorruptionException`), and `toDomain()`, which never throws |
| `data/RateConfigRepository.kt` | 104 | The DataStore (`rate_config.json`), `setRate` / `setPair` / `swapPair`, validation (0 < rate < 10¹²) |
| `data/RecentCurrenciesRepository.kt` | 39 | Preferences DataStore `currency_recents`; last 5 picks, newest first |
| `ui/navigation/Routes.kt` | 17 | `@Serializable` routes: `ConverterRoute`, `SettingsRoute`, `CurrencyPickerRoute(slot)`; `CurrencySlot` is `@Keep` |
| `ui/navigation/RatePrinceNavHost.kt` | 64 | NavHost; `dropUnlessResumed` guards; returns to the Converter on the widget's `rateprince://converter` intent |
| `ui/converter/ConverterViewModel.kt` | 111 | `ConverterUiState` (Loading / NeedsRate / Ready); builds the state off the main thread |
| `ui/converter/ConverterScreen.kt` | 355 | Amount card, rate caption, Add-widget card, and the **reference grid** (`FlowRow` of compact cells) |
| `ui/settings/SettingsViewModel.kt` | 63 | The pair, the rate and its timestamp; `saveRate`, `swap` |
| `ui/settings/SettingsScreen.kt` | 213 | Currency rows, swap, rate editor, widget section, version label |
| `ui/picker/CurrencyPickerViewModel.kt` | 116 | Search, recents, `resolvePick()` (§3.4) |
| `ui/picker/CurrencyPickerScreen.kt` | 146 | Search field, "Recent" and "All currencies" |
| `ui/rate/RateDraft.kt` | 68 | Two-way editor state; the saved rate is always computed from the field the user typed in |
| `ui/rate/RateEditor.kt` | 129 | "1 JPY = [ ] INR" over "1 INR = [ ] JPY"; `rememberRateDraft` (saveable) |
| `ui/quick/QuickConvertActivity.kt` | 87 | The widget's text input (§6.4) |
| `ui/quick/QuickConvertSheet.kt` | 169 | The bottom sheet: field, live result, rate, Clear and Done |
| `ui/common/AddWidget.kt` | 88 | `rememberWidgetStatus()` (re-checked on resume), `AddWidgetCard`, `AddWidgetAction` |
| `ui/common/AmountInputTransformation.kt` | 12 | Rejects keystrokes that `AmountParser.isEditable` rejects |
| `ui/common/RelativeTime.kt` | 14 | "2 days ago" / "just now" (app only; the widget uses a date) |
| `ui/theme/Color.kt`, `Theme.kt`, `Type.kt` | 42 / 80 / 51 | Indigo and gold palette with dynamic colour on 31+; `LightColorScheme` and `DarkColorScheme` are `internal` (the widget's API 30 palette); tabular figures |
| `widget/RatePrinceWidget.kt` | 253 | `GlanceAppWidget` (`SizeMode.Exact`), `providePreview`, `WidgetRoot` (in-session state plus background save), receiver, `SwapPairAction`, `setWidgetAmount()`, `WidgetStateKeys` |
| `widget/WidgetContent.kt` | 429 | Every widget layout and size threshold (§6) |
| `widget/WidgetModel.kt` | 124 | Fully formatted strings for the widget (`WidgetStrings` in, `WidgetModel` out); testable without a device |
| `widget/KeypadInput.kt` | 24 | Pure keypad editing rules (no leading zeros, "." → "0.", limits) |
| `widget/WidgetIntents.kt` | 41 | `openConverter()` and `quickConvert()` intents (§6.4) |
| `widget/WidgetPlacement.kt` | 61 | `status()`, `requestPin()`, `publishPreviews()` (API 35+) |
| `widget/LocaleChangedReceiver.kt` | 23 | `LOCALE_CHANGED` → `updateAll()` |

**Resources:**
- `assets/currencies.json`: **152** hand-curated active currencies (code, name, symbol, digits),
  sorted by code. Excluded: fund, metal and test codes, and withdrawn BGN, HRK, ANG and KPW.
  Included: new XCG and ZWG.
- `xml/rate_widget_info.xml`: widget provider (§6.1).
- `layout/widget_loading.xml` (skeleton) and `layout/widget_preview.xml` (API 31+ picker
  preview, a hard-coded ¥1,500 → ₹870.00).
- `drawable-xxhdpi/widget_preview_legacy.png`: the API 30 picker image, a real widget screenshot
  with a rounded alpha mask.
- `drawable/`: `widget_background.xml` (16 dp shape for API 30), vector icons (`ic_settings`,
  `ic_arrow_back`, `ic_swap_vert`, `ic_search`, `ic_check`, `ic_close`, `ic_chevron_right`,
  `ic_dialpad`, `ic_backspace`), and the launcher foreground and background.
- `drawable-nodpi/icon_round.png`: the launcher icon source (§9).
- `values/colors.xml` and `values-night/colors.xml`: static widget colours (preview, skeleton, API
  30 background).
- `values/themes.xml`: `Theme.RatePrince` (with a night variant) and `Theme.RatePrince.QuickConvert`.
- `values/strings.xml`: all text, including a `settings_widget_count` plural.
- `keepRules/rules.keep`: the one custom R8 rule (§10.2).

---

## 5. Data and persistence

| Store | File | Contents | Notes |
|---|---|---|---|
| Rate config | DataStore `rate_config.json` | `StoredRateConfig`: `homeCurrency`, `localCurrency`, `rate` (**string**, `toPlainString`), `updatedAtMillis`, `source` (**enum name**, not ordinal), `schemaVersion` 1 | kotlinx JSON with `ignoreUnknownKeys` and `encodeDefaults`. Corrupt file → `ReplaceFileCorruptionHandler` → defaults. Invalid fields → defaults in `toDomain()`. Default: INR/JPY, rate "0" |
| Recents | Preferences DataStore `currency_recents` | key `codes`: comma-separated, max 5 | Invalid codes are skipped |
| Per-widget | Glance `PreferencesGlanceStateDefinition` | `amount_input` (raw string, e.g. "12."), `keypad_open` | Deleted with the widget instance |

The TD specified **Proto** DataStore; it was replaced by **JSON** (owner ruling). The guarantees are
the same, with no protobuf Gradle plugin.

---

## 6. The widget

### 6.1 Provider (`xml/rate_widget_info.xml`) and manifest
- min 110×40 dp (2×1); resize 110×40 up to **420**×250 dp (TD: 320, raised so it can span a row);
  target 3×2 cells; `updatePeriodMillis="0"`; `home_screen` only.
- `initialLayout` is the skeleton; `previewLayout` is used on 31+, `previewImage` on 30.
- **No `android:configure`**: there's no widget config screen (owner ruling). The API 31+
  attributes carry `tools:ignore="UnusedAttribute"`.
- Receiver `RatePrinceWidgetReceiver`: **exported**, with the `APPWIDGET_UPDATE` filter.
  **Never set `android:process`**: DataStore is single-process.

### 6.2 Size mode and layouts (`WidgetContent.kt`)
- **`SizeMode.Exact`**, not the TD's Responsive. Responsive composed and shipped **all four size
  buckets on every update**, which made each keypad tap 4× the work. Exact renders only the real
  size (usually two: portrait and landscape). `previewSizeMode` stays
  `Responsive(NARROW, MEDIUM, WIDE)` for the generated preview. `SIZE_*` constants: NARROW 110×40,
  MEDIUM 180×110, WIDE 320×110.
- **Layout choice, in order:**
  1. Unconfigured (rate 0): "Tap to set your rate" plus "JPY → INR"; the whole surface opens the app.
  2. Height < 100 dp: **single line** "¥1,500 → ₹870.00" (or the rate caption with no amount).
  3. Keypad open and width ≥ 170, height ≥ 100: **keypad** (§6.3).
  4. Width ≥ 320: **wide**: amount and result rows, a swap button (36 dp), and a caption with
     "Edited 24 Sep".
  5. Otherwise **stacked**: the amount and result centred vertically (launcher cells vary a lot in
     height), the caption at the bottom, "large" text at height ≥ 200.
- The keypad toggle (24 dp `ic_dialpad`) sits at the right of the caption row in stacked and wide.
- **Padding** is 10 dp (`WIDGET_PADDING`). Text can't auto-size through RemoteViews, so
  `valueTextSize(length)` picks 22/18/15 sp (+6 when large).
- **Background:** API 31+ uses `GlanceTheme.colors.widgetBackground` plus
  `system_app_widget_background_radius`; API 30 uses the `widget_background` drawable (the
  `cornerRadius` modifier is a no-op there).
- **Colours:** `DynamicThemeColorProviders` on 31+; `ColorProviders(LightColorScheme,
  DarkColorScheme)` on 30.
- The "Edited" text is an **absolute date** (`DateFormat` "dMMM"), because the widget never
  refreshes on a timer, so "2 days ago" would go stale.

### 6.3 Keypad (owner-specified layout)
- A `Row`: the **left panel is an explicit 34% width**, computed from `LocalSize` (Glance weights
  only split equally). It holds the local code plus a ✕ (close keypad) on top, the amount (tap it
  to open the overlay), the home code, and the result. The **right side fills the rest** with a 3×4
  grid: `1 2 3 / 4 5 6 / 7 8 9 / C 0 ⌫`, with keys stretching to fill.
- **No decimal key** (owner's layout). Decimals are typed in the overlay, reached by tapping the
  amount. `KeypadInput.append` still supports "." for that reason.
- **Speed:** keys, C, ⌫ and the toggle are **in-session lambdas** (`clickable(key = …, block = …)`,
  with **explicit keys** because they're created in loops), not broadcast `ActionCallback`s. They
  change `mutableStateOf` in `WidgetRoot`, so the redraw is immediate. A `LaunchedEffect` then writes
  the Glance state **without `update()`**, compared against `SavedWidgetState` (what's on disk).
  When the stored value changes from outside (the overlay commits), `remember(storedAmount)` resets
  the local copy.
- **The first tap after the widget has been idle** can still lag. Glance restarts its session
  through WorkManager, and that can't be avoided.

### 6.4 Quick-convert overlay (`ui/quick/`)
- `QuickConvertActivity`: `exported=false`, `taskAffinity=""`, `excludeFromRecents`, `noHistory`,
  `singleTop`, and `stateAlwaysVisible|adjustResize`. The theme is translucent with transparent
  system bars and `windowLayoutInDisplayCutoutMode=always`, because otherwise a black band shows at
  the top. The sheet draws its own 32% black scrim.
- **Intent:** `rateprince://quick/<appWidgetId>?amount=<raw>`. The `data` URI makes each widget's
  PendingIntent unique (otherwise every widget would open the first one's overlay), and the amount
  in the URI can never be stale.
- The field opens with the current amount **selected** (typing replaces it). Focus is requested
  **inside the sheet's content**, after the config loads, because requesting it before the field
  existed gave no keyboard.
- **Commits on Done, on a scrim tap, on Back, and in `onStop`** (Home button), through
  `applicationScope`, so the write outlives the activity. `setWidgetAmount()` no-ops if the widget
  was removed.

### 6.5 Taps and intents
| Target | Action |
|---|---|
| Whole surface, result, caption | `actionStartActivity(openConverter)`: `MainActivity` with `rateprince://converter`; the NavHost pops back to the Converter if another screen is open |
| Amount row (stacked, wide, keypad panel) | `actionStartActivity(quickConvert(...))` |
| Wide swap | `actionRunCallback<SwapPairAction>()`: the **global** swap, so every widget flips (owner ruling) |
| Keypad keys and toggle | In-session lambdas (§6.3) |

### 6.6 Adding the widget from the app (`WidgetPlacement`)
- `status()`: `isRequestPinAppWidgetSupported` plus the Glance ID count.
- `requestPin()`: `requestPinGlanceAppWidget` with `previewState` amount "1500"; `successCallback`
  brings `MainActivity` to the front, and `rememberWidgetStatus()` re-checks on resume.
- **UI:** a Converter card while no widget exists; a Settings section that always offers to add
  another. Unsupported launchers get the manual steps instead of a button.
- `publishPreviews()` (API 35+): `setWidgetPreviews(RatePrinceWidgetReceiver::class)`. The system
  rate-limits it, so failures are ignored. It's called on app start and on every config change.
  `providePreview` shows the user's pair and rate, or the ¥1,500 → ₹870 sample when unconfigured.

---

## 7. In-app UI

- **Navigation** (typed routes): Converter (start) → Settings → CurrencyPicker(slot). Every
  navigation is guarded by `dropUnlessResumed` / a lifecycle check against double taps.
- **Converter:**
  - The amount field (`BasicTextField` with `TextFieldState` and `AmountInputTransformation`)
    **does not auto-focus**; the keyboard opens only on tap (owner ruling).
  - Result in a `SelectionContainer`; swap button; caption "1 JPY = 0.58 INR · edited 2 days ago".
  - The **reference grid** (owner-specified): `FlowRow`, and each cell is only as wide as its text
    (`IntrinsicSize.Max`). Local amount in grey `bodySmall`, a rule, then the home amount. No arrow.
    The cell nearest the typed amount is highlighted (`secondaryContainer`) and scrolled into view
    (`BringIntoViewRequester`). TalkBack reads one merged description per cell: "¥1 is ₹0.58".
- **NeedsRate** (rate 0): a card with the two-way `RateEditor`, "Change currencies" and "Save rate".
- **Settings:** currency rows (flag, code, name) to the picker; "Swap home and local"; the
  `RateEditor` plus Save (enabled only when dirty and valid) plus "Last edited …"; the widget section;
  and the version label "RatePrince v0.1 (1)" (`BuildConfig`, `buildConfig = true`, string
  `settings_version`).
- **Rate saving is explicit** (Save or IME Done), never per keystroke, so half-typed rates never
  reach the widgets.
- **Picker:** search (characters capitalisation), then "Recent" and "All currencies"; a tick on the
  current one. `pick()` writes, then navigates back; it ignores double taps.

---

## 8. Theme

- **Palette:** royal indigo primary and gold tertiary (`Color.kt`), light and dark. **Dynamic
  colour on 31+** (the default).
- **Type:** tabular figures (`tnum`) on display, headline and body styles, so digits don't jitter.
- **Window themes:** `Theme.RatePrince` is platform `Theme.Material.Light.NoActionBar`, with a
  `values-night` variant (no white flash). There's **no Material Components dependency**.

---

## 9. Launcher icon

- **Source:** `Work/Res/Icon/AppIconBay/RatePrince/icon_round.png` (512², badge 460 px wide),
  copied to `drawable-nodpi/icon_round.png`.
- **Implemented the EdgeCase / ShadeCase way:** an `adaptive-icon` (`mipmap-anydpi/ic_launcher.xml`
  and `ic_launcher_round.xml`) with a **transparent** vector background, and the foreground an
  `<inset>` around the PNG. There's **no monochrome layer** (the template's was removed; it would
  have rendered a filled circle as the themed icon).
- **The inset is 13%**, putting the badge at 71.8 dp inside the 72 dp mask. The maths is in the
  comment in `drawable/ic_launcher_foreground.xml`; **recompute it if the artwork is re-cut.**
- There are no per-density mipmap PNGs: minSdk 30 always uses the adaptive icon. The template's
  webp mipmaps were deleted.

---

## 10. Build and tooling

### 10.1 Setup
- **AGP 9.2.1 with built-in Kotlin 2.2.10.** Don't apply `org.jetbrains.kotlin.android`. Plugins:
  `kotlin.compose` and `kotlin.serialization`.
- **Gradle** 9.4.1 (configuration cache on), with a JDK 21 toolchain. **No system Java on this Mac:
  use `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.**
- **SDK levels:** `compileSdk = release(37)` (core-ktx 1.19.1 and lifecycle 2.11 require it),
  `targetSdk 36`, `minSdk 30`, Java 11 source and target.
- **Version:** `versionName "0.1"`, `versionCode 1` (in `app/build.gradle.kts`).
- **Dependencies** (`gradle/libs.versions.toml`): compose BOM 2026.09.00, activity-compose 1.13.0,
  lifecycle 2.11.0 (runtime-compose, viewmodel-compose), navigation-compose 2.10.2,
  kotlinx-serialization-json **1.9.0** (the last line for Kotlin 2.2), datastore and
  datastore-preferences 1.2.1, glance-appwidget and glance-material3 **1.2.0**, **work-runtime
  2.12.0** (§10.2). Tests: JUnit 4, coroutines-test 1.9.0, glance-appwidget-testing 1.2.0,
  Robolectric 4.17, and androidx.test / espresso / compose ui-test. **No DI, no Material
  Components, no image libraries, no network.**

### 10.2 Release build (R8): read before touching dependencies
- `release`: `isMinifyEnabled = true`, `isShrinkResources = true`, and
  `signingConfig = signingConfigs.getByName("debug")`. **AGP 9's `optimization { enable = true }`
  fails without `android.r8.gradual.support=true`** (ShadeCase uses that flag; RatePrince uses the
  classic flags instead).
- **Crash fixed 2026-09-24:** the first release build crashed at startup ("Failed to create an
  instance of `androidx.work.impl.WorkDatabase`"), taking down the app and the widget (one process).
  Glance 1.2.0 pulls in **WorkManager 2.7.1 / Room 2.2.5**, whose keep rules don't keep
  constructors under **R8 full mode**. The fix is pinning **work-runtime 2.12.0** (which brings Room
  2.7.0).
- **The same trap for Glance action callbacks:** Glance creates `ActionCallback` classes by name,
  but its rule doesn't keep the constructor. `keepRules/rules.keep` has
  `-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }`, without
  which the swap button would crash.
- **Verify a release build** by dumping its dex (`build-tools/…/dexdump`) and checking that
  `WorkDatabase_Impl`, `SwapPairAction`, `SessionWorker` and the serializers keep `<init>`.
  `mapping.txt` alone didn't show members. Serializers for the routes, `StoredRateConfig` and
  `CatalogEntry` were checked; the resources (widget XML, previews, `currencies.json`) survive
  shrinking.
- **Sizes:** release **4.5 MB**, debug 38 MB.

### 10.3 Commands
```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleRelease assembleDebug testDebugUnitTest lintDebug
adb -s 46281FDAS008EG install -r app/build/outputs/apk/release/app-release.apk   # only when asked
```

---

## 11. Testing

### 11.1 Automated: 104 JVM test runs, 0 failures
| Suite | Runs | Covers |
|---|---:|---|
| `domain.ConversionEngineTest` | 6 | Ladder = live field across the currency matrix; target digits; JPY 0 digits; KWD 3; HALF_UP; single rounding |
| `domain.AmountParserTest` | 9 | Comma = period, the 12/6 digit limits, intermediate states, spaces, garbage; `RateParser` 8 decimals and > 0 |
| `domain.MoneyFormatterTest` | 10 | Lakh grouping, locale separators, symbols and spacing, `formatAmount`, `formatTyped`, `formatRate` |
| `domain.LadderTest` | 4 | 46 ascending rows, TD Appendix A values, labels, mismatched metadata |
| `domain.DomainPurityTest` | 1 | No `android.` / `androidx.` imports in `domain/` |
| `data.CurrencyCatalogTest` | 6 | The asset is well formed, ISO digits, flags, search order, JDK fallback |
| `data.RateConfigRepositoryTest` | 14 | Real DataStore in a temp dir: defaults, validation, swap and double swap, pair rules, corruption, unknown keys |
| `data.RecentCurrenciesRepositoryTest` | 3 | Empty, dedupe, max 5 |
| `ui.ConverterViewModelTest` | 6 | NeedsRate; **the ladder matches `src/test/resources/ladder_spreadsheet.csv`** (computed independently with Python `decimal` for USD→INR, KRW→INR and INR→KWD); highlight; swap; save |
| `ui.RateDraftTest` | 8 | Two-way editing, the inverse keeps its precision, errors |
| `ui.ResolvePickTest` | 3 | Same pick is a no-op, the other slot's currency swaps, otherwise the pair is set |
| `widget.KeypadInputTest` | 6 | Leading zeros, ".", limits, backspace |
| `widget.WidgetModelTest` | 8 | Unconfigured, conversion, placeholders, "12.", keypad flag, the rate caption's precision, descriptions |
| `widget.WidgetLayoutTest` | 20 | **Robolectric, API 30 and 36** (10 tests each): every layout and threshold, the keypad at 3×2 / wide / tall, the keypad never at 2×1, click targets. Keypad lambdas are found with `hasContentDescriptionEqualTo` (Glance tests can't click) |

**Instrumented (`androidTest`, 3 tests):** `DataLayerDeviceTest`: the catalogue from assets, the
formatting contract on ICU (lakh grouping), and a repository round trip. It was last run in Phase 1
on the emulator.

### 11.2 Device results
- **Emulator (Phases 2–3, Pixel 9 Pro XL AVD, Pixel launcher):** first run in 2 taps; the picker
  lists the widget with its populated preview; drop and render; rate change updates every widget
  within about 1 s; the overlay; two widgets with independent amounts and bindings; resizing to
  wide and single-line; swap; reboot survival; widget tap returns to the Converter. This predates
  the Exact size mode and the keypad.
- **Owner's phone** (Pixel 9 Pro XL, Android 17, serial `46281FDAS008EG`): the owner reported the
  oversized keypad, the wasteful ladder and the 1 s keypad lag (all fixed), then approved the 34/66
  keypad. The release-build crash was read from the phone's `logcat -b crash`; after the fix, no new
  crash appeared in the log.

### 11.3 Traps that cost time
- **The shell is zsh:** unquoted `$var` doesn't word-split, so `set -- $b` breaks. Run such
  scripts with `bash`. There's no `timeout` command on macOS.
- **Gradle `--rerun` applies only to the last task named.** Kotlin incremental compilation can also
  skip files; `-Pkotlin.incremental=false --rerun` per task forces a real recompile when looking
  for warnings.
- **Glance unit tests need Robolectric**, and on JDK 21 the API 36 runtime needs
  `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED` and `--add-opens=java.base/java.io=…`
  (already in `testOptions`). In Glance's test API, `hasText` is a **substring** match (its boolean
  argument is `ignoreCase`); use `hasContentDescriptionEqualTo` for exact matches.
- **Glance weights split space equally.** For a 34/66 split, give one side an explicit width from
  `LocalSize`; don't add an empty third column.
- **`adb logcat -d` hangs if the phone disconnects mid-command.** With two devices, always pass
  `-s` or set `ANDROID_SERIAL=emulator-5554`.
- **A translucent activity** needs transparent bars and the cutout mode, or a black band shows.
- **Requesting focus before a field is composed** gives no keyboard.

---

## 12. Deviations from `Docs/RateTD.md`

1. **Name and package:** RatePrince, `com.dicereligion.rateprince`, `rateprince://` URIs.
2. **minSdk 30** (TD: 26).
3. **JSON DataStore** instead of Proto.
4. **No widget config activity** and no `android:configure`.
5. **`SizeMode.Exact`** instead of Responsive (§6.2).
6. **Keypad (Option B) from 3×2 up, 34/66 layout, no decimal key, in-session lambdas** (TD: 4×4+
   only, `ActionCallback`s).
7. **Reference grid** instead of a 48 dp-row `LazyColumn`.
8. **Pair changes clear the rate** (except an exact swap).
9. **The rate caption keeps the rate's own precision** (TD bug: it would show "0.01"). Rates are
   inverted at 10 significant digits; `RateParser` allows 8 decimals.
10. **The global swap on the widget**; the TD's `INVERTED` per-widget key was dropped.
11. **The TD's four doc bugs fixed:** `WideLayout` unreachable, the `quickConvertIntent` arity,
    swap precision against validation, and the unconfigured tap target.
12. **Lakh grouping implemented in-app**; symbols and digits from the bundled catalogue.
13. **`WorkManager` pinned, and an `ActionCallback` keep rule** (§10.2).
14. **The repository takes an `onConfigChanged` callback** instead of calling the widget directly.
15. **The overlay commits in `onStop` too**; the amount rides in the intent URI.
16. **The widget's "Edited" is a date**; `maxResizeWidth` is 420 dp.
17. **The Converter doesn't auto-focus** (TD: focused on entry).

---

## 13. Owner rulings (don't reopen without cause)

| Date | Ruling |
|---|---|
| 2026-09-24 | **No `git commit` / `push` / `merge` by the assistant, ever** |
| 2026-09-24 | The app is **RatePrince** (the TD says "Rate") |
| 2026-09-24 | minSdk 30; JSON DataStore; **no widget config screen** |
| 2026-09-24 | **No emulator or device testing unless asked**; the owner tests on their phone |
| 2026-09-24 | The Converter keyboard opens only when the field is tapped |
| 2026-09-24 | Keypad at 3×2: 34% amount/result on the left, 66% 3×4 grid (1–9, C 0 ⌫) filling the right |
| 2026-09-24 | Reference amounts as compact cells: local greyed, a rule, home below, no arrow |
| 2026-09-24 | Publishing deferred; live rates, Phase 6, the remaining QA and housekeeping are ignored or parked (§1) |
| 2026-09-24 | **The phone is production**: install the optimised release build |
| 2026-09-24 | Version v0.1 (1), shown as small text in the app |
| 2026-09-24 | README: only what the app is and its structure |
| 2026-09-24 | Launcher icon the EdgeCase / ShadeCase way (adaptive, inset, `nodpi` PNG) |

---

## 14. Known limitations

- **The first keypad tap after idle** can lag while Glance restarts its session through
  WorkManager (§6.3).
- **No decimal key on the widget keypad**; use the overlay.
- **The reference amounts are fixed** (1 … 100,000): right for JPY/KRW/VND, too coarse and too large
  for USD/EUR (the Phase 6 fix is parked).
- **One currency pair for the whole app.** All widgets share it, and the wide widget's swap flips
  it everywhere.
- **Widget text can't auto-size**; very long results step down to 15 sp and truncate.
- **The generated picker preview is rate-limited by the system**, so it can lag behind a rate
  change.

---

## 15. Where do I look for…

| I want to… | Go to |
|---|---|
| Change the maths or rounding | `domain/ConversionEngine.kt`; keep one code path and run `ConversionEngineTest` plus the spreadsheet test |
| Change number or currency display | `domain/MoneyFormatter.kt` (grouping, symbols, `formatTyped`) |
| Add or fix a currency | `assets/currencies.json` (keep it sorted; `CurrencyCatalogTest` checks the shape) |
| Change what's saved | `data/StoredRateConfig.kt`: add fields with defaults; never store a float |
| Change pair or swap rules | `data/RateConfigRepository.kt` and `ui/picker/CurrencyPickerViewModel.resolvePick` |
| Change a widget layout or size threshold | `widget/WidgetContent.kt` (constants at the bottom) plus `WidgetLayoutTest` |
| Change widget text or strings | `widget/WidgetModel.kt` plus `WidgetStrings` in `RatePrinceWidget.kt` |
| Change keypad behaviour | `widget/KeypadInput.kt` (rules) and `WidgetRoot` in `RatePrinceWidget.kt` (state and saving) |
| Change the overlay | `ui/quick/`; intents in `widget/WidgetIntents.kt` |
| Change the reference grid | `ui/converter/ConverterScreen.kt` `Ladder` / `LadderCell`; amounts in `domain/Ladder.kt` |
| Change the version | `versionName` / `versionCode` in `app/build.gradle.kts`; the label format is `settings_version` |
| Change the launcher icon | `drawable-nodpi/icon_round.png` plus the inset maths in `drawable/ic_launcher_foreground.xml` |
| Add a library | Check the release build keeps it working (§10.2): dump the dex, and look for reflection by class name |

---

## 16. Change history (newest first; the owner's commits in brackets)

- **2026-09-24, launcher icon, README trim, `stats.md` [uncommitted].** Icon from
  `AppIconBay/RatePrince`, adaptive with a 13% inset (§9). README reduced to what the app is and its
  structure. This file created.
- **2026-09-24, owner feedback, cleanup, v0.1 (1), release build, crash fix [`b314ab8`
  "Phased"].**
  - **From the owner's phone feedback:** the keypad was first shrunk to fit 3×2, then rebuilt as
    the 34/66 layout. The reference list became the compact grid. The keypad moved to in-session
    lambdas and `SizeMode.Exact`, fixing about 1 s of lag per tap; `KeypadActions.kt` was deleted.
  - **Cleanup:** removed dead code (`convertInverse`, `CurrencyCatalog.fractionDigits`,
    `KeypadInput.DIGITS`, `SIZE_TALL`), the template's example tests, webp mipmaps and
    `ic_arrow_forward`.
  - **Release:** the version label; R8 plus resource shrinking, signed with the debug key.
  - **Crash fix:** the WorkManager startup crash (work-runtime 2.12.0) and the `ActionCallback`
    constructor keep rule.
- **Phase 4, placement polish and keypad [`f7f4293`].** `WidgetPlacement` (pin, status, API 35
  previews), the Add-widget card and Settings section, `providePreview`, and the first keypad (4×4
  only, broadcast `ActionCallback`s). Also `formatTyped`, and the Converter no longer auto-focuses
  (owner ruling).
- **Phase 3, the widget [`f7f4293`].** Glance widget in all sizes, the provider XML and layouts,
  the quick-convert overlay, per-instance amounts, `updateAll` on every config change, the locale
  receiver, the legacy preview PNG, Robolectric layout tests. Fixed on the emulator: overlay focus,
  the black band, vertical centring.
- **Phase 2, in-app UI [`edae976`].** Converter, Settings, picker, the two-way rate editor, recents,
  the NeedsRate flow, icons as vector drawables, navigation guards.
- **Phases 0–1, skeleton, domain and data [`e468b77`].** Gradle (AGP 9, compileSdk 37), theme,
  navigation; the engine, parsers, formatter (lakh grouping), catalogue, JSON DataStore repository,
  and the unit suite.
- **Pre-phasing [`1bc0fb0`].** `.gitignore` rewritten.
- **[`ab81e9f` "Firsts", `43598c8` "first commit"].** Android Studio template plus `Docs/RateTD.md`.
