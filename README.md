# RatePrince

A home-screen currency converter widget for Android, built for travelling.

You type in the exchange rate you actually got — at the airport counter, or what your card
is charging — and RatePrince converts from the local currency (e.g. JPY) into the currency
you think in (e.g. INR). There is no network access: the rate is yours, and everything works
offline.

## What it does

### The widget

- Shows an amount and its conversion right on the home screen.
- **Tap the amount** to type a new one: a small sheet slides up over the home screen with the
  number keyboard ready. Tap anywhere else on the widget to open the app.
- **Keypad mode** (3×2 and larger): a keypad button splits the widget — the amount and result
  on the left third, a phone-style keypad (1–9, then C 0 ⌫) filling the right two-thirds — so
  you can type without leaving the home screen. Tap the amount for the full keyboard when you
  need a decimal point.
- **Resizable**: one line at 2×1, amount over result at 3×2, and a wide version with a swap
  button and the date the rate was set.
- **Several widgets**, each remembering its own amount; all of them share the app's
  currencies and rate, and update the moment the rate changes.
- On Android 15+, the launcher's widget picker previews your own currency pair and rate.

### The app

- **Converter**: amount entry with a live result, a swap button, and 46 reference amounts
  (¥1, ¥2, ¥5 … ¥100,000) as a compact grid — local amount over home amount — with the one
  nearest your amount highlighted.
- **First run**: a "Set your exchange rate" card; two taps to a working converter.
- **Settings**: home and local currency, swap, and a two-way rate editor — type either
  "1 JPY = 0.58 INR" or "1 INR = 1.72 JPY" and the other follows. Also an "Add widget"
  button, and the app version.
- **Currency picker**: 152 active currencies with flags, search by code or name, and your last
  five picks at the top.
- **Add widget from the app**: one tap on launchers that support it; the manual steps on
  those that don't.

### Behaviour worth knowing

- Money maths uses exact decimals, rounded once, half-up, to each currency's own digits
  (¥ has none, ₹ has two, KD has three). The converter, the reference grid, the widget and the
  pop-up all use the same calculation, so they never disagree.
- Numbers follow your phone's locale, including Indian lakh grouping (₹1,23,456.00).
- Changing a currency clears the rate, so a rate meant for one currency is never applied to
  another. Swapping the pair inverts the rate instead.

## Structure

One Gradle module, `app`, package `com.dicereligion.rateprince`:

| Package | What's in it |
| :--- | :--- |
| `domain/` | Pure Kotlin, no Android imports: the conversion engine, amount and rate parsing, number formatting, the reference amounts, and the currency and rate models |
| `data/` | The bundled currency catalogue, the saved rate and currency pair (DataStore, JSON), and recent currency picks |
| `ui/` | The Compose screens — converter, settings, currency picker — plus the quick-convert pop-up, navigation and theme |
| `widget/` | The Glance home-screen widget: its layouts, keypad, per-widget state, and adding it from the app |

Built with Kotlin, Jetpack Compose and [Glance](https://developer.android.com/develop/ui/compose/glance).
Runs on Android 11 (API 30) and up.
