# RatePrince

A home-screen currency converter widget for Android, built for travelling.

You type in the exchange rate you actually got — at the airport counter, or what your card
is charging — and RatePrince converts from the local currency (e.g. JPY) into the currency
you think in (e.g. INR). There is no network access: the rate is yours, and everything works
offline.

- **The widget** shows an amount and its conversion right on the home screen. Tap the amount
  to type a new one; tap anywhere else to open the app.
- **The app** has the same amount/result pair plus a "ladder" of 46 reference amounts
  (¥1, ¥2, ¥5 … ¥100,000) so you can build intuition for prices at a glance.

The full technical design is in [`Docs/RateTD.md`](Docs/RateTD.md). It was written when the
app was called "Rate"; the code uses the RatePrince name throughout.

## Features

| Area | What it does |
| :--- | :--- |
| Converter | Amount entry, live result, swap button, rate caption, and the 46-row ladder with the row nearest your amount highlighted |
| First run | A "Set your exchange rate" card until a rate is saved (2 taps to a working converter) |
| Settings | Home and local currency, swap, and a two-way rate editor ("1 JPY = 0.58 INR" or "1 INR = 1.72 JPY") |
| Currency picker | 152 active currencies with flags, search by code or name, last 5 picks under "Recent" |
| Widget | Resizable: one-line (2×1), stacked (3×2), wide with swap button (5×2), large (4×4). Several widgets can sit on the home screen, each with its own amount |
| Quick-convert overlay | Tapping the widget's amount opens a small sheet over the home screen with the number keyboard up |
| Adding the widget | "Add widget" button in the app (Converter card until a widget exists, and in Settings). On launchers that can't do this, the app shows the manual steps instead |
| Widget keypad | At the large 4×4 size, a keypad button turns the widget into a calculator-style keypad, so you can type without leaving the home screen |
| Picker previews | On Android 15+, the launcher's widget picker shows your own currency pair and rate |

## How it's built

- Kotlin, Jetpack Compose for the app, [Glance](https://developer.android.com/develop/ui/compose/glance) for the widget
- Min SDK 30 (Android 11), target SDK 36, compile SDK 37
- Money maths uses `BigDecimal` only, rounded once, half-up, to the target currency's digits.
  The ladder, the converter, the widget and the overlay all use the same conversion call, so
  they can never disagree.
- Settings are stored with DataStore as JSON (the rate is saved as text, never as a float)
- One Gradle module; packages: `domain/` (pure Kotlin, no Android imports — enforced by a
  test), `data/`, `ui/`, `widget/`

## Building and testing

Requires JDK 21 (Android Studio's bundled JDK works).

```sh
./gradlew assembleDebug        # build the app
./gradlew testDebugUnitTest    # unit tests, incl. widget layout tests via Robolectric
./gradlew lintDebug            # lint
```

## Progress

Development follows the phases in `Docs/RateTD.md` §11.

| Phase | Status | Scope |
| :--- | :--- | :--- |
| 0 — Skeleton | ✅ Done | Project setup, theme, navigation |
| 1 — Domain and data | ✅ Done | Conversion engine, parsing, formatting, currency catalog, stored settings |
| 2 — In-app UI | ✅ Done | Converter, Settings, currency picker, first-run flow |
| 3 — Widget | ✅ Done | Widget in all sizes, quick-convert overlay, per-widget amounts, live updates |
| 4 — Placement polish and keypad | ✅ Done (awaiting on-phone checks) | One-tap "Add widget", Android 15+ live picker previews, on-widget keypad |
| 5 — Live rates | ⏳ Later (post-v1) | Optional fetched rates |

### Decisions that differ from the design doc

- **Min SDK 30** instead of 26.
- **No widget configuration screen.** Every widget uses the app's currencies and rate.
- **JSON DataStore** instead of Proto DataStore (simpler build, same guarantees).
- **Changing a currency clears the rate** (unless it's an exact swap, which inverts it), so a
  rate for one currency is never silently applied to another.
- **The widget's swap button is global**: it flips the pair for the whole app and every widget.
- **Lakh grouping (₹1,23,456.00) is applied by the app itself**, so it looks the same on every
  device and in tests.
- **The on-widget keypad is off by default** and only offered at the 4×4 size; the pop-up
  keyboard stays the main way to type an amount. Each keypad tap round-trips through the
  launcher, so it needs checking on a real phone for lag.
- The widget shows the date the rate was set ("Edited 24 Sep") rather than "2 days ago",
  because widgets don't refresh on a timer.
