# Rate — Technical Design Document

**A home-screen currency converter widget for Android**

| Field | Value |
| :---- | :---- |
| Version | 1.0 |
| Date | 15 September 2026 |
| Owner | Anumey (DiceReligion) |
| Status | Draft for implementation |
| Target | Android 8.0 (API 26\) → Android 16 (API 36\) |
| Language / UI | Kotlin 2.x, Jetpack Compose (app) \+ Glance (widget) |

---

## 1\. Scope

### 1.1 What the product is

A single-purpose, offline-first currency converter built around a resizeable home-screen widget. The exchange rate is **user-owned**: you type in the rate you actually got at the airport counter or the rate your card is charging, and everything derives from that. No network dependency in v1.

Primary use case: you are travelling in a country with an unfamiliar currency (local \= JPY) and you want instant intuition in your own currency (home \= INR) without opening an app.

### 1.2 Functional requirements

| ID | Requirement |
| :---- | :---- |
| FR-1 | Widget shows an amount entry area at the top and the converted result at the bottom. |
| FR-2 | Any number entered is converted from local currency to home currency and displayed. |
| FR-3 | Tapping the widget opens the app on the Converter screen. |
| FR-4 | Converter screen has the same amount/result pair at the top, plus a scrollable ladder of 46 pre-computed reference amounts (1, 2, 5, 10 … 100000). |
| FR-5 | A Settings screen lets the user choose home currency, local currency, and manually set the exchange rate. |
| FR-6 | The widget can be placed anywhere on the home screen, resized, and instantiated multiple times with independent state. |
| FR-7 | The user can add the widget from inside the app (one-tap pin) as well as from the launcher's widget picker. |

### 1.3 Non-goals for v1

- Live rate fetching (designed for, deferred to Phase 5).  
- Historical charts, multi-currency baskets, fee/markup modelling.  
- Wear OS, tablets-specific layouts, lock-screen widgets (API 31 removed these anyway).  
- Account sync. All state is local.

### 1.4 The one thing you need to decide before writing code

**Android app widgets cannot contain a text input field.** This is not a Glance limitation, it is a `RemoteViews` limitation and it has never changed. The allow-list of inflatable view classes in `RemoteViews` is: `FrameLayout`, `LinearLayout`, `RelativeLayout`, `GridLayout`, `ListView`, `GridView`, `StackView`, `AdapterViewFlipper`, `ViewFlipper`, `TextView`, `Button`, `ImageView`, `ImageButton`, `ProgressBar`, `AnalogClock`, `Chronometer`, `TextClock`, and (API 31+) `CheckBox`, `RadioButton`, `Switch`. There is no `EditText`, no IME attachment, no `RemoteInput` (that is notifications only). A widget process does not own a window it can focus, so there is nothing for the keyboard to attach to.

So FR-1's "input field" has to be realised as one of three things. Pick one now, because it determines the widget's entire interaction model and the shape of `WidgetState`.

**Option A — Tap-to-type overlay (recommended).** The widget's top area is a `Text` styled to look like a field, showing the last entered amount. Tapping it fires `actionStartActivity` into a translucent, dialog-themed `QuickConvertActivity` that contains a real `BasicTextField` with the numeric IME already up. Result updates live as you type. On dismiss, the amount is written back to the widget's state and the widget re-renders.

- Real keyboard, real decimal/locale handling, real accessibility, zero per-keystroke IPC.  
- Cost: one activity launch (\~150–250 ms cold on a mid-range device, faster warm). Visually it is a small sheet over the home screen, so it does not feel like "leaving" the home screen.

**Option B — Inline tap keypad.** Render 0–9, `.`, and backspace as `Button`s inside the widget. Each tap fires an `ActionCallback`, which mutates Glance state and pushes a new `RemoteViews` tree to the launcher.

- Genuinely in-place, no activity at all, looks great in a 4×3 widget.  
- Cost: every single digit is a broadcast → suspend callback → DataStore write → RemoteViews build → cross-process apply. Measured round trips are typically 80–250 ms, and the launcher can coalesce or drop updates under load. Typing "12500" is five of those. It feels laggy in a way you cannot engineer away, because the latency is in the launcher IPC, not your code. It also consumes most of the widget's area with buttons, and each `Button` is a view in a tree that has a hard complexity ceiling.  
- Needs at least a 4×3 cell footprint to hit 48 dp touch targets.

**Option C — Stepper only.** `+`/`−` buttons over a set of common denominations. Cheap, but does not satisfy "enter any number".

**Decision: build Option A as the default interaction, and ship Option B as an opt-in behaviour that only activates when the widget is resized past a threshold (`LocalSize.width >= 240.dp && height >= 200.dp`).** The state model below supports both without change, so B can land in Phase 4 without refactoring. Everything downstream of "an amount exists" is identical.

The result "textfield" at the bottom is read-only output, so that one is a plain `Text` and presents no problem.

---

## 2\. Architecture

### 2.1 Shape

One Gradle module. This app has roughly 2,500 lines of production code in it; a multi-module graph would cost more in build-file ceremony than it returns. Package boundaries are enforced by convention and reviewed at PR time. If Phase 5 (network rates) lands, extract `:core:data` then.

com.dicereligion.rate  
├── RateApplication.kt            // DI container root  
├── domain/  
│   ├── model/                    // Currency, RateConfig, ConversionResult  
│   ├── ConversionEngine.kt        // pure, no Android imports  
│   └── LadderSpec.kt              // the 46 reference amounts  
├── data/  
│   ├── RateConfigRepository.kt    // DataStore-backed, single source of truth  
│   ├── RateConfigSerializer.kt  
│   └── CurrencyCatalog.kt         // ISO 4217 table, bundled  
├── widget/  
│   ├── RateWidget.kt              // GlanceAppWidget  
│   ├── RateWidgetReceiver.kt      // GlanceAppWidgetReceiver  
│   ├── WidgetStateKeys.kt  
│   ├── layout/                    // Narrow/Medium/Wide composables  
│   └── action/                    // ActionCallbacks  
├── ui/  
│   ├── converter/                 // ConverterScreen \+ ViewModel  
│   ├── settings/                  // SettingsScreen \+ ViewModel  
│   ├── picker/                    // CurrencyPickerScreen  
│   ├── quick/                     // QuickConvertActivity (the overlay)  
│   └── theme/  
└── MainActivity.kt

### 2.2 Layering and data flow

                      ┌─────────────────────────────┐  
                      │   RateConfigRepository      │  
                      │   (Proto DataStore, single  │  
                      │    instance per process)    │  
                      └───────────┬─────────────────┘  
                     Flow\<RateConfig\>│  
              ┌───────────────┴───────────────┐  
              ▼                               ▼  
   ┌─────────────────────┐          ┌──────────────────────┐  
   │  ConverterViewModel │          │  RateWidget          │  
   │  SettingsViewModel  │          │  .provideGlance()    │  
   └──────────┬──────────┘          └──────────┬───────────┘  
              │                                │ \+ currentState\<Preferences\>()  
              │                                │   (per-widget amount buffer)  
              ▼                                ▼  
     Compose UI (in-app)              RemoteViews (launcher process)  
              │                                ▲  
              └──── writes config ─────────────┘  
                    then RateWidget().updateAll(context)  
Two distinct pieces of state, and conflating them is the most common bug in widget apps:

| State | Where it lives | Scope | Survives |
| :---- | :---- | :---- | :---- |
| `RateConfig` (home, local, rate, updatedAt) | App's own Proto DataStore | Global, one per install | Reboot, app kill, widget removal |
| `amountInput` (the digits currently entered) | Glance `PreferencesGlanceStateDefinition` | **Per widget instance**, keyed by `GlanceId` | Reboot, app kill; deleted with the widget |

The widget's `provideGlance` reads both: config via a `Flow` collected inside `provideContent`, per-instance amount via `currentState`. Glance recomposes on either changing.

### 2.3 Process model — one hard rule

**Never set `android:process` on `RateWidgetReceiver`.** DataStore guarantees single-process access only; two processes holding the same file will corrupt it and the failure mode is a silently empty config. The `AppWidgetProvider` broadcast receiver runs in your main app process by default. Keep it that way.

### 2.4 Dependencies

// gradle/libs.versions.toml (excerpt)  
\[versions\]  
kotlin \= "2.1.0"  
agp \= "8.9.0"  
composeBom \= "2026.05.00"  
glance \= "1.1.1"          \# stable; 1.2.0-rc01 available if you want  
                          \# the newer preview \+ testing APIs  
datastore \= "1.1.1"  
work \= "2.10.0"           \# Phase 5 only

\[libraries\]  
glance-appwidget      \= { module \= "androidx.glance:glance-appwidget",      version.ref \= "glance" }  
glance-material3     \= { module \= "androidx.glance:glance-material3",      version.ref \= "glance" }  
glance-preview       \= { module \= "androidx.glance:glance-appwidget-preview", version.ref \= "glance" }  
glance-testing       \= { module \= "androidx.glance:glance-appwidget-testing", version.ref \= "glance" }  
datastore            \= { module \= "androidx.datastore:datastore",          version.ref \= "datastore" }  
Verify versions against `developer.android.com/jetpack/androidx/releases/glance` at implementation time — Glance moves.

DI: no Hilt. Three singletons behind a hand-rolled container on `Application`, because the widget receiver needs access from a non-`@AndroidEntryPoint` context and `EntryPointAccessors` for that is more ceremony than a field.

class RateApplication : Application() {  
    lateinit var container: AppContainer  
        private set

    override fun onCreate() {  
        super.onCreate()  
        container \= AppContainer(this)  
    }  
}

class AppContainer(context: Context) {  
    val rateConfigRepository: RateConfigRepository \= RateConfigRepository(context)  
    val conversionEngine: ConversionEngine \= ConversionEngine()  
    val currencyCatalog: CurrencyCatalog \= CurrencyCatalog  
}

val Context.container: AppContainer  
    get() \= (applicationContext as RateApplication).container  
`minSdk = 26`. API 26 is the floor where `requestPinAppWidget` exists; below that, in-app pinning is impossible and you would have to ship a "drag it from the widget tray yourself" instruction screen. Not worth supporting.

---

## 3\. Domain model and the conversion engine

### 3.1 Models

// domain/model/Currency.kt  
@JvmInline  
value class CurrencyCode(val value: String) {          // ISO 4217 alpha-3  
    init { require(value.length \== 3\) { "bad code: $value" } }  
}

data class CurrencyMeta(  
    val code: CurrencyCode,  
    val displayName: String,          // "Japanese Yen"  
    val symbol: String,               // "¥"  
    val fractionDigits: Int,          // JPY \= 0, INR \= 2, KWD \= 3  
    val flagEmoji: String,            // "🇯🇵" — for the picker, not the widget  
)  
// domain/model/RateConfig.kt  
data class RateConfig(  
    val homeCurrency: CurrencyCode,       // what you think in: INR  
    val localCurrency: CurrencyCode,      // where you are: JPY  
    /\*\* Home currency units per ONE unit of local currency. \*/  
    val rate: BigDecimal,  
    val updatedAtEpochMillis: Long,  
    val source: RateSource,  
) {  
    val isUsable: Boolean get() \= rate \> BigDecimal.ZERO  
}

enum class RateSource { MANUAL, FETCHED\_REMOTE }  
Rate direction is defined once and never inverted in storage: **`rate` is always home-per-one-local**. For JPY→INR at the time of writing that is about `0.58`. Every UI that shows or edits the rate can present the inverse (`1 / rate` → "1 INR \= 1.72 JPY") but must convert back before writing. Storing a direction flag alongside the number is how you end up with a 1/x bug six months later.

### 3.2 Arithmetic — `BigDecimal`, not `Double`

`Double` is disqualified. `0.58 * 3 = 1.7399999999999998`, and a converter that shows `₹1.74` in one place and `₹1.73` in another is broken in exactly the way that destroys trust in a money app. Everything is `BigDecimal` from parse to format.

// domain/ConversionEngine.kt  
class ConversionEngine {

    /\*\* Working precision: generous enough that no ladder row is ever off by a display unit. \*/  
    private val mathContext \= MathContext(20, RoundingMode.HALF\_UP)

    fun convert(  
        amount: BigDecimal,  
        config: RateConfig,  
        targetFractionDigits: Int,  
    ): BigDecimal \=  
        amount.multiply(config.rate, mathContext)  
              .setScale(targetFractionDigits, RoundingMode.HALF\_UP)

    fun convertInverse(  
        amount: BigDecimal,  
        config: RateConfig,  
        targetFractionDigits: Int,  
    ): BigDecimal \=  
        amount.divide(config.rate, mathContext)  
              .setScale(targetFractionDigits, RoundingMode.HALF\_UP)  
}  
Rules, enforced by unit test:

1. Multiply at `MathContext(20, HALF_UP)`, round **once** at the end, to the *target* currency's fraction digits.  
2. Never round an intermediate. Never round to the source currency's digits.  
3. `HALF_UP` everywhere, including the ladder, so the ladder and the live field can never disagree for the same input.  
4. Ladder rows are computed from the same `convert()` call as the live field. No separate code path. This is the single most important invariant in the app.  
5. Guard `rate <= 0` at the repository boundary; `ConversionEngine` may assume a usable config.

### 3.3 Parsing user input

Locale-aware and forgiving, because the numeric IME on an Indian-locale device offers `.` while a French-locale one offers `,`.

object AmountParser {  
    private val allowed \= Regex("""^\\d{0,12}(\[.,\]\\d{0,6})?$""")

    /\*\* Returns null for "not yet a number" (empty, bare separator). \*/  
    fun parse(raw: String): BigDecimal? {  
        val cleaned \= raw.trim()  
            .replace("\\u00A0", "")  
            .replace(" ", "")  
            .replace(",", ".")            // treat both separators as decimal point  
        if (cleaned.isEmpty() || cleaned \== ".") return null  
        if (\!allowed.matches(raw.trim().replace(" ", ""))) return null  
        return cleaned.toBigDecimalOrNull()  
    }

    fun isEditable(raw: String): Boolean \=  
        raw.isEmpty() || allowed.matches(raw)  
}  
12 integer digits is the input ceiling. It is past any realistic amount, keeps the widget text from overflowing, and keeps `BigDecimal` cheap. Grouping separators are not accepted as input (ambiguous across locales) but are always used in output.

### 3.4 Formatting output

class MoneyFormatter(private val locale: Locale \= Locale.getDefault()) {

    private val cache \= mutableMapOf\<CurrencyCode, NumberFormat\>()

    fun format(amount: BigDecimal, code: CurrencyCode): String \=  
        formatter(code).format(amount)

    /\*\* Symbol-free, for tight widget layouts where the symbol is in the label. \*/  
    fun formatPlain(amount: BigDecimal, code: CurrencyCode): String {  
        val digits \= CurrencyCatalog.fractionDigits(code)  
        return NumberFormat.getNumberInstance(locale).apply {  
            minimumFractionDigits \= digits  
            maximumFractionDigits \= digits  
        }.format(amount)  
    }

    private fun formatter(code: CurrencyCode) \= cache.getOrPut(code) {  
        NumberFormat.getCurrencyInstance(locale).apply {  
            currency \= java.util.Currency.getInstance(code.value)  
        }  
    }  
}  
Two notes that matter for a JPY↔INR app specifically:

- `Currency.getInstance("JPY").defaultFractionDigits == 0`, so ¥1,500 never shows decimals. `INR` is 2\. Pull digits from `Currency`, never hardcode 2\.  
- On an `en-IN` locale, `NumberFormat` groups INR as `₹1,23,456.00` (lakh grouping), which is correct and desirable. Do not normalise it away.

### 3.5 The reference ladder

// domain/LadderSpec.kt  
object LadderSpec {  
    /\*\* 46 rows, as specified. Ordered ascending, treated as an immutable contract. \*/  
    val DEFAULT: List\<Int\> \= listOf(  
        1, 2, 5, 10, 20, 30, 40, 50,  
        100, 150, 200, 250, 300, 400, 500, 600, 700, 800, 900,  
        1\_000, 1\_500, 2\_000, 2\_500, 3\_000, 3\_500, 4\_000, 4\_500, 5\_000,  
        6\_000, 7\_000, 8\_000, 9\_000,  
        10\_000, 15\_000, 20\_000, 25\_000, 30\_000, 35\_000, 40\_000, 45\_000, 50\_000,  
        60\_000, 70\_000, 80\_000, 90\_000, 100\_000,  
    )  
}  
One design caveat worth flagging rather than silently fixing: this ladder is tuned for a currency in the JPY/KRW/VND magnitude band. For a local currency of USD or EUR, rows above \~5,000 are dead weight and the interesting granularity (0.50, 1.50, 2.50) is missing. The ladder is specified as fixed for v1, so ship it fixed — but put it behind `LadderSpec` so a magnitude-aware generator can replace it later without touching the UI:

/\*\* Phase 6 candidate, not v1. \*/  
fun forMagnitude(rate: BigDecimal): List\<BigDecimal\> { /\* scale DEFAULT by 10^k \*/ }  
The ladder is **computed, never stored**. 46 `BigDecimal` multiplications is microseconds; a cache would be a correctness liability the first time the rate changes.

data class LadderRow(  
    val localAmount: BigDecimal,  
    val homeAmount: BigDecimal,  
    val localLabel: String,  
    val homeLabel: String,  
)

fun buildLadder(  
    config: RateConfig,  
    engine: ConversionEngine,  
    formatter: MoneyFormatter,  
): List\<LadderRow\> {  
    val homeDigits \= CurrencyCatalog.fractionDigits(config.homeCurrency)  
    return LadderSpec.DEFAULT.map { n \-\>  
        val local \= BigDecimal(n)  
        val home \= engine.convert(local, config, homeDigits)  
        LadderRow(  
            localAmount \= local,  
            homeAmount \= home,  
            localLabel \= formatter.format(local, config.localCurrency),  
            homeLabel \= formatter.format(home, config.homeCurrency),  
        )  
    }  
}  
---

## 4\. Data layer

### 4.1 Why Proto DataStore

Room is overkill: there is exactly one row and no queries. Preferences DataStore would work but `BigDecimal` has to be stringified, and the schema drifts as untyped keys. Proto DataStore gives a typed record, a real default, and a migration path, for the cost of one `.proto` file.

// src/main/proto/rate\_config.proto  
syntax \= "proto3";  
option java\_package \= "com.dicereligion.rate.data.proto";  
option java\_multiple\_files \= true;

message RateConfigProto {  
  string home\_currency \= 1;  
  string local\_currency \= 2;  
  string rate \= 3;              // BigDecimal.toPlainString() — never a float  
  int64 updated\_at\_millis \= 4;  
  int32 source \= 5;             // RateSource ordinal  
  int32 schema\_version \= 6;  
}  
`rate` is a string. Protobuf has no decimal type, and `double` here would reintroduce exactly the precision problem section 3.2 exists to avoid.

object RateConfigSerializer : Serializer\<RateConfigProto\> {  
    override val defaultValue: RateConfigProto \= RateConfigProto.newBuilder()  
        .setHomeCurrency("INR")  
        .setLocalCurrency("JPY")  
        .setRate("0")                 // 0 \== "unconfigured", drives first-run UI  
        .setSchemaVersion(1)  
        .build()

    override suspend fun readFrom(input: InputStream): RateConfigProto \=  
        try { RateConfigProto.parseFrom(input) }  
        catch (e: InvalidProtocolBufferException) { throw CorruptionException("proto", e) }

    override suspend fun writeTo(t: RateConfigProto, output: OutputStream) \= t.writeTo(output)  
}

private val Context.rateConfigStore: DataStore\<RateConfigProto\> by dataStore(  
    fileName \= "rate\_config.pb",  
    serializer \= RateConfigSerializer,  
    corruptionHandler \= ReplaceFileCorruptionHandler { RateConfigSerializer.defaultValue },  
)  
Defaults are `INR`/`JPY` with `rate = 0`. A zero rate is the explicit "not configured yet" signal — no nullable config, no separate `isFirstRun` flag, and the widget has something coherent to render the instant it is dropped on the home screen.

### 4.2 Repository

class RateConfigRepository(private val context: Context) {

    val config: Flow\<RateConfig\> \= context.rateConfigStore.data  
        .catch { emit(RateConfigSerializer.defaultValue) }   // never let the widget crash  
        .map { it.toDomain() }

    suspend fun current(): RateConfig \= config.first()

    suspend fun setRate(rate: BigDecimal) {  
        require(rate \> BigDecimal.ZERO) { "rate must be positive" }  
        context.rateConfigStore.updateData {  
            it.toBuilder()  
                .setRate(rate.stripTrailingZeros().toPlainString())  
                .setUpdatedAtMillis(System.currentTimeMillis())  
                .setSource(RateSource.MANUAL.ordinal)  
                .build()  
        }  
    }

    suspend fun setPair(home: CurrencyCode, local: CurrencyCode) { /\* … \*/ }

    suspend fun swapPair() { /\* also inverts rate: 1/rate at MathContext(20) \*/ }  
}

private fun RateConfigProto.toDomain() \= RateConfig(  
    homeCurrency \= CurrencyCode(homeCurrency),  
    localCurrency \= CurrencyCode(localCurrency),  
    rate \= rate.toBigDecimalOrNull() ?: BigDecimal.ZERO,  
    updatedAtEpochMillis \= updatedAtMillis,  
    source \= RateSource.entries.getOrElse(source) { RateSource.MANUAL },  
)  
Two things the repository is responsible for and nobody else is:

- **Rate validation.** `rate > 0` is rejected here, so `ConversionEngine` never divides by zero.  
- **Widget invalidation.** Every mutating call is followed by a widget refresh. Put it in the repository, not in each ViewModel, or you will eventually ship a settings path that updates the app and leaves the widget stale.

private suspend fun invalidateWidgets() {  
    RateWidget().updateAll(context)  
}

### 4.3 Currency catalog

`java.util.Currency.getAvailableCurrencies()` gives \~300 entries including defunct and fund codes (`XAU`, `XDR`, `CLF`). Filtering that at runtime is guesswork. Bundle a curated list instead.

object CurrencyCatalog {  
    /\*\* \~160 active, spendable currencies. Bundled as an asset, parsed once, cached. \*/  
    private val byCode: Map\<String, CurrencyMeta\> by lazy { load() }

    fun meta(code: CurrencyCode): CurrencyMeta \= byCode\[code.value\]  
        ?: fallbackFromJdk(code)

    fun fractionDigits(code: CurrencyCode): Int \= meta(code).fractionDigits

    fun search(query: String): List\<CurrencyMeta\> \= /\* code prefix, then name contains \*/  
}  
Source the asset from `Currency.getAvailableCurrencies()` at build time, filter by hand once, commit the JSON. \~14 KB. Symbols come from `Currency.getSymbol(locale)` but override the ones the JDK gets wrong for `en-IN` (it renders several as the bare code).

---

## 5\. The widget

### 5.1 Manifest declaration

\<\!-- AndroidManifest.xml \--\>  
\<application  
    android:name=".RateApplication"  
    android:theme="@style/Theme.Rate"\>

    \<activity  
        android:name=".MainActivity"  
        android:exported="true"  
        android:launchMode="singleTop"\>  
        \<intent-filter\>  
            \<action android:name="android.intent.action.MAIN" /\>  
            \<category android:name="android.intent.category.LAUNCHER" /\>  
        \</intent-filter\>  
    \</activity\>

    \<\!-- The tap-to-type overlay. Dialog-themed, keeps the launcher visible behind it. \--\>  
    \<activity  
        android:name=".ui.quick.QuickConvertActivity"  
        android:exported="false"  
        android:theme="@style/Theme.Rate.QuickConvert"  
        android:launchMode="singleTop"  
        android:taskAffinity=""  
        android:excludeFromRecents="true"  
        android:noHistory="true"  
        android:windowSoftInputMode="stateAlwaysVisible|adjustResize" /\>

    \<\!-- Optional placement-time configuration. \--\>  
    \<activity  
        android:name=".ui.settings.WidgetConfigActivity"  
        android:exported="true"  
        android:theme="@style/Theme.Rate.QuickConvert"\>  
        \<intent-filter\>  
            \<action android:name="android.appwidget.action.APPWIDGET\_CONFIGURE" /\>  
        \</intent-filter\>  
    \</activity\>

    \<receiver  
        android:name=".widget.RateWidgetReceiver"  
        android:exported="true"  
        android:label="@string/widget\_label"\>  
        \<intent-filter\>  
            \<action android:name="android.appwidget.action.APPWIDGET\_UPDATE" /\>  
        \</intent-filter\>  
        \<meta-data  
            android:name="android.appwidget.provider"  
            android:resource="@xml/rate\_widget\_info" /\>  
    \</receiver\>  
\</application\>  
Non-obvious requirements in there:

- `android:exported="true"` on the receiver is **mandatory**. The launcher is a different app sending you `APPWIDGET_UPDATE`; a non-exported receiver produces a widget that appears in the picker and then renders as a permanently blank box. On API 31+ the explicit attribute is required to compile at all.  
- The `APPWIDGET_UPDATE` intent-filter must be present even though Glance handles the broadcast. Its absence is the other cause of the blank-box symptom.  
- No `android:process`. See §2.3.  
- `QuickConvertActivity` needs `taskAffinity=""` **and** `excludeFromRecents` **and** `noHistory`, or you get a phantom Rate task in the recents switcher every time someone taps the widget, and back-navigation from the overlay lands in the app instead of the home screen.

### 5.2 The provider info XML — the file that controls placement

\<\!-- res/xml/rate\_widget\_info.xml \--\>  
\<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"  
    android:minWidth="110dp"  
    android:minHeight="40dp"  
    android:minResizeWidth="110dp"  
    android:minResizeHeight="40dp"  
    android:maxResizeWidth="320dp"  
    android:maxResizeHeight="250dp"  
    android:targetCellWidth="3"  
    android:targetCellHeight="2"  
    android:resizeMode="horizontal|vertical"  
    android:widgetCategory="home\_screen"  
    android:updatePeriodMillis="0"  
    android:previewLayout="@layout/widget\_preview"  
    android:previewImage="@drawable/widget\_preview\_legacy"  
    android:description="@string/widget\_description"  
    android:initialLayout="@layout/widget\_loading"  
    android:configure="com.dicereligion.rate.ui.settings.WidgetConfigActivity"  
    android:widgetFeatures="reconfigurable|configuration\_optional" /\>  
(XML does not permit comments between attributes inside a tag — annotations live in the table below, not in the file.)

Attribute-by-attribute, because every one of these has a visible consequence:

| Attribute | Why it is set this way |
| :---- | :---- |
| `minWidth` / `minHeight` | Pre-API-31 launchers compute cells with `dp = 70 × cells − 30`. `110×40` \= 2×1. Set this to the genuine minimum at which the widget is *readable*, not the smallest you can technically render — a launcher will never offer a size below it. |
| `minResizeWidth/Height` | The floor for user resizing. Equal to `minWidth` here. |
| `maxResizeWidth/Height` | API 31+. Without it, some launchers let the user stretch to full screen and your layout has to cope with 400×600 dp. |
| `targetCellWidth/Height` | API 31+, and it is what modern launchers actually honour. 3×2 is the sweet spot: amount \+ result \+ rate caption, comfortably. |
| `resizeMode` | \`horizontal |
| `updatePeriodMillis="0"` | The system minimum is 30 minutes (`1800000`) and it wakes the device. With user-set rates there is nothing to poll. Zero means "never", and we push with `updateAll()` instead. |
| `initialLayout` | A static XML layout shown in the \~100 ms before Glance's first composition lands. Make it the widget's background and a skeleton, not a spinner, or the drop animation flickers. |
| `previewLayout` | API 31+. A real layout the launcher inflates live in the picker, using the device's own theme. Vastly better than a bitmap. |
| `previewImage` | API 30 and below fallback. A 2:1 PNG at \~`xhdpi`. |
| `description` | API 31+. One line under the name in the picker. |
| `widgetCategory` | `home_screen` only. `keyguard` has been dead since API 31\. |
| `configure` \+ `configuration_optional` | See §6.4. Without `configuration_optional`, **every** drop forces a config screen — hostile for a widget that has sane defaults. |

`widget_preview.xml` should hardcode a representative conversion (`¥1,500 → ₹870`) rather than showing an empty state. The picker is where the user decides whether the widget is worth the screen space, and an empty box does not sell it.

### 5.3 Receiver and widget class

// widget/RateWidgetReceiver.kt  
class RateWidgetReceiver : GlanceAppWidgetReceiver() {  
    override val glanceAppWidget: GlanceAppWidget \= RateWidget()  
}  
That is the whole receiver. `GlanceAppWidgetReceiver` implements `onUpdate`, `onDeleted`, `onAppWidgetOptionsChanged` and `onEnabled`/`onDisabled`, maps `appWidgetId` → `GlanceId`, and on delete removes the per-instance state file. Overriding any of those is almost always a mistake; if you need "on first widget added" behaviour, override `onEnabled` and call `super`.

// widget/RateWidget.kt  
class RateWidget : GlanceAppWidget() {

    override val stateDefinition \= PreferencesGlanceStateDefinition

    override val sizeMode \= SizeMode.Responsive(  
        setOf(SIZE\_NARROW, SIZE\_MEDIUM, SIZE\_WIDE, SIZE\_TALL)  
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {  
        val repository \= context.container.rateConfigRepository  
        val engine \= context.container.conversionEngine

        // Read once before composing so the first frame is never empty.  
        val initial \= repository.current()

        provideContent {  
            val config by repository.config.collectAsState(initial \= initial)  
            val prefs \= currentState\<Preferences\>()  
            val rawAmount \= prefs\[WidgetStateKeys.AMOUNT\_INPUT\].orEmpty()  
            val keypadOpen \= prefs\[WidgetStateKeys.KEYPAD\_OPEN\] ?: false

            GlanceTheme(colors \= RateGlanceColors.providers) {  
                RateWidgetContent(  
                    config \= config,  
                    rawAmount \= rawAmount,  
                    keypadOpen \= keypadOpen,  
                    engine \= engine,  
                    size \= LocalSize.current,  
                )  
            }  
        }  
    }

    companion object {  
        val SIZE\_NARROW \= DpSize(110.dp, 40.dp)    // 2x1  
        val SIZE\_MEDIUM \= DpSize(180.dp, 110.dp)   // 3x2  
        val SIZE\_WIDE   \= DpSize(320.dp, 110.dp)   // 5x2  
        val SIZE\_TALL   \= DpSize(250.dp, 250.dp)   // 4x4 — keypad eligible  
    }  
}  
Points that are easy to get wrong:

- `provideGlance` is a `suspend` function running on a background dispatcher, so the `repository.current()` call is legal and does not risk an ANR. The composition inside `provideContent` must stay cheap, though — it runs on every state change.  
- `collectAsState` inside `provideContent` is the supported way to keep a widget live against a `Flow`. Glance keeps the composition alive while the widget is visible and re-emits `RemoteViews` on each change. Do not poll, do not use `LaunchedEffect` with a loop.  
- `SizeMode.Responsive` composes **once per declared size** and hands the launcher a mapped `RemoteViews` set, so resizing is instant with no IPC. `SizeMode.Exact` re-composes on every resize (more IPC, more correct for freeform sizes). `Responsive` is right here; four buckets cover every realistic placement.  
- Keep the view tree shallow. Glance maps composables onto a finite pool of generated `RemoteViews` layouts and will throw at runtime if you nest containers too deeply (roughly beyond 10 levels, and the per-container child limit is small). Every widget layout below is at most 4 deep.

### 5.4 Per-instance state keys

// widget/WidgetStateKeys.kt  
object WidgetStateKeys {  
    val AMOUNT\_INPUT \= stringPreferencesKey("amount\_input")   // raw digits, e.g. "1250."  
    val KEYPAD\_OPEN  \= booleanPreferencesKey("keypad\_open")  
    val INVERTED     \= booleanPreferencesKey("inverted")      // local→home vs home→local  
}  
The raw *string* is stored, not a parsed `BigDecimal`. A trailing decimal point (`"12."`) is a legitimate intermediate state that must round-trip, and re-formatting the user's digits under them is the classic currency-field annoyance.

### 5.5 Responsive layouts

@Composable  
private fun RateWidgetContent(  
    config: RateConfig,  
    rawAmount: String,  
    keypadOpen: Boolean,  
    engine: ConversionEngine,  
    size: DpSize,  
) {  
    if (\!config.isUsable) { UnconfiguredState(); return }

    val formatter \= remember(config) { MoneyFormatter() }  
    val parsed \= remember(rawAmount) { AmountParser.parse(rawAmount) }  
    val converted \= remember(parsed, config) {  
        parsed?.let {  
            engine.convert(it, config, CurrencyCatalog.fractionDigits(config.homeCurrency))  
        }  
    }

    Column(  
        modifier \= GlanceModifier  
            .fillMaxSize()  
            .background(GlanceTheme.colors.widgetBackground)  
            .appWidgetBackground()  
            .cornerRadius(android.R.dimen.system\_app\_widget\_background\_radius)  
            .padding(12.dp)  
            .clickable(actionStartActivity\<MainActivity\>()),   // fallback: whole surface  
    ) {  
        when {  
            size.height \>= 200.dp && size.width \>= 240.dp && keypadOpen \-\> KeypadLayout(...)  
            size.height \>= 100.dp  \-\> StackedLayout(...)          // 3x2 default  
            else                   \-\> SingleLineLayout(...)       // 2x1  
        }  
    }  
}

| Bucket | Cells | Contents |
| :---- | :---- | :---- |
| `SingleLineLayout` | 2×1 | One row: `¥1,500 → ₹870`. No input affordance; tap opens the app. |
| `StackedLayout` | 3×2 | Amount row (tappable, opens overlay) / divider / result row / rate caption. This is the canonical widget. |
| `WideLayout` | 5×2 | Same as stacked plus a swap-direction button and the `updatedAt` timestamp. |
| `KeypadLayout` | 4×4+ | Result at top, 4×3 button grid below. Option B from §1.4. |

@Composable  
private fun StackedLayout(  
    config: RateConfig, rawAmount: String, converted: BigDecimal?,  
    formatter: MoneyFormatter,  
) {  
    val local \= CurrencyCatalog.meta(config.localCurrency)  
    val home \= CurrencyCatalog.meta(config.homeCurrency)

    // Amount "field" — a Text dressed as an input, tapping it opens the overlay.  
    Row(  
        modifier \= GlanceModifier.fillMaxWidth()  
            .clickable(actionStartActivity(quickConvertIntent(LocalContext.current))),  
        verticalAlignment \= Alignment.CenterVertically,  
    ) {  
        Text(local.symbol, style \= captionStyle)  
        Spacer(GlanceModifier.width(6.dp))  
        Text(  
            text \= rawAmount.ifEmpty { "Tap to enter" },  
            style \= if (rawAmount.isEmpty()) placeholderStyle else amountStyle,  
            maxLines \= 1,  
            modifier \= GlanceModifier.defaultWeight(),  
        )  
    }

    Spacer(GlanceModifier.height(6.dp))

    Row(verticalAlignment \= Alignment.CenterVertically) {  
        Text(home.symbol, style \= captionStyle)  
        Spacer(GlanceModifier.width(6.dp))  
        Text(  
            text \= converted?.let { formatter.formatPlain(it, config.homeCurrency) } ?: "—",  
            style \= resultStyle,  
            maxLines \= 1,  
        )  
    }

    Spacer(GlanceModifier.defaultWeight())

    Text(  
        text \= "1 ${local.code.value} \= ${formatter.formatPlain(config.rate, config.homeCurrency)} ${home.code.value}",  
        style \= captionStyle,  
        maxLines \= 1,  
    )  
}  
**Click-region conflict — read this before implementing.** FR-3 says tapping the widget opens the app; the amount area needs to open the overlay; the keypad needs per-button taps. Glance click handling is innermost-wins, so the resolution is:

1. Amount row → `QuickConvertActivity` (the overlay).  
2. Keypad buttons → their own `ActionCallback`s.  
3. Everything else, including the result row and the rate caption → `MainActivity` on the Converter screen.

There is no long-press available to you: long-press on a widget is owned by the launcher for move/resize/remove and cannot be intercepted. Do not design any interaction around it.

Text sizing: `Text` in a widget cannot auto-shrink (no `autoSizeTextType` through `RemoteViews`). With a 12-digit input ceiling, pick sizes per bucket and clamp `maxLines = 1`. For the result, choose the size from the string length at compose time:

private fun resultTextSize(length: Int): TextUnit \= when {  
    length \<= 8  \-\> 24.sp  
    length \<= 12 \-\> 20.sp  
    else         \-\> 16.sp  
}

### 5.6 Actions

// widget/action/AppendDigitAction.kt  (Option B only)  
class AppendDigitAction : ActionCallback {  
    override suspend fun onAction(  
        context: Context, glanceId: GlanceId, parameters: ActionParameters,  
    ) {  
        val digit \= parameters\[DIGIT\_KEY\] ?: return  
        updateAppWidgetState(context, glanceId) { prefs \-\>  
            val current \= prefs\[WidgetStateKeys.AMOUNT\_INPUT\].orEmpty()  
            val next \= current \+ digit  
            if (AmountParser.isEditable(next)) {  
                prefs\[WidgetStateKeys.AMOUNT\_INPUT\] \= next  
            }  
        }  
        RateWidget().update(context, glanceId)  
    }

    companion object { val DIGIT\_KEY \= ActionParameters.Key\<String\>("digit") }  
}  
Button(  
    text \= "7",  
    onClick \= actionRunCallback\<AppendDigitAction\>(  
        actionParametersOf(AppendDigitAction.DIGIT\_KEY to "7")  
    ),  
)  
`updateAppWidgetState` then `update(context, glanceId)` is the required pair — the state write alone does not trigger a re-render. Also needed: `ClearAmountAction`, `BackspaceAction`, `ToggleKeypadAction`, `SwapDirectionAction`.

`update(context, glanceId)` targets one instance. `updateAll(context)` targets all of them and is what the repository calls after a config change.

### 5.7 The quick-convert overlay

class QuickConvertActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {  
        super.onCreate(savedInstanceState)  
        val appWidgetId \= intent.getIntExtra(  
            AppWidgetManager.EXTRA\_APPWIDGET\_ID, AppWidgetManager.INVALID\_APPWIDGET\_ID  
        )  
        setContent {  
            RateTheme {  
                QuickConvertSheet(  
                    onCommit \= { raw \-\> commitAndFinish(appWidgetId, raw) },  
                    onDismiss \= { finish() },  
                )  
            }  
        }  
    }

    private fun commitAndFinish(appWidgetId: Int, raw: String) {  
        lifecycleScope.launch {  
            if (appWidgetId \!= AppWidgetManager.INVALID\_APPWIDGET\_ID) {  
                val glanceId \= GlanceAppWidgetManager(this@QuickConvertActivity)  
                    .getGlanceIdBy(appWidgetId)  
                updateAppWidgetState(this@QuickConvertActivity, glanceId) {  
                    it\[WidgetStateKeys.AMOUNT\_INPUT\] \= raw  
                }  
                RateWidget().update(this@QuickConvertActivity, glanceId)  
            }  
            finish()  
        }  
    }  
}  
\<\!-- res/values/themes.xml \--\>  
\<style name="Theme.Rate.QuickConvert" parent="Theme.Material3.DayNight.Dialog"\>  
    \<item name="android:windowIsTranslucent"\>true\</item\>  
    \<item name="android:windowBackground"\>@android:color/transparent\</item\>  
    \<item name="android:windowIsFloating"\>false\</item\>  
    \<item name="android:backgroundDimEnabled"\>true\</item\>  
    \<item name="android:windowAnimationStyle"\>@style/Animation.Rate.Sheet\</item\>  
\</style\>  
The sheet composable: a `BasicTextField` with `KeyboardType.Decimal`, `rememberTextFieldState`, focus requested in a `LaunchedEffect(Unit)`, the live converted value directly beneath it, and a `Done` action that commits. Gravity `bottom` so the field sits just above the IME. Commit also on dismiss so nothing is lost to a stray back-press.

The `appWidgetId` extra is what makes multi-instance work. Build the intent per widget:

private fun quickConvertIntent(context: Context, appWidgetId: Int) \=  
    Intent(context, QuickConvertActivity::class.java).apply {  
        putExtra(AppWidgetManager.EXTRA\_APPWIDGET\_ID, appWidgetId)  
        // Distinct data per widget, or PendingIntent reuse sends every widget  
        // the first widget's extras.  
        data \= "rate://quick/$appWidgetId".toUri()  
    }  
That `data` line is not optional. `PendingIntent`s are deduplicated by everything except extras, so three widgets sharing an intent template all open the overlay bound to whichever one was created first. Distinct `data` URIs (or `FLAG_UPDATE_CURRENT` with unique request codes) is the fix. This bug is invisible with one widget on screen, which is why it reaches production so often.

To get the `appWidgetId` inside `provideGlance`, use `GlanceAppWidgetManager(context).getAppWidgetId(id)`.

### 5.8 Update triggers

| Trigger | Mechanism |
| :---- | :---- |
| Rate or currency pair changed in app | `RateConfigRepository.invalidateWidgets()` → `RateWidget().updateAll(context)` |
| Amount entered via overlay | `update(context, glanceId)` for that instance only |
| Keypad tap | `ActionCallback` → `update(context, glanceId)` |
| Widget resized | Handled by Glance from the `SizeMode.Responsive` set, no IPC |
| Widget dropped on home screen | `onUpdate` → `provideGlance` |
| Reboot / app update / launcher restart | System re-broadcasts `APPWIDGET_UPDATE`; state is on disk, renders identically |
| Locale change | `ACTION_LOCALE_CHANGED` receiver → `updateAll` (formatting is locale-dependent) |
| Periodic | None. `updatePeriodMillis="0"`. Phase 5 adds a `WorkManager` job. |

There is no formal rate limit on `AppWidgetManager.updateAppWidget`, but a `RemoteViews` payload crosses a Binder transaction with a \~1 MB ceiling, and launchers throttle under pressure. Keep the tree small, never put a `Bitmap` in it, and treat any burst faster than \~5 updates/second as a bug.

### 5.9 Theming

object RateGlanceColors {  
    val providers: ColorProviders  
        @Composable get() \= if (Build.VERSION.SDK\_INT \>= 31\) {  
            DynamicThemeColorProviders          // from glance-material3  
        } else {  
            RateStaticGlanceColors  
        }  
}  
Requirements:

- `.appWidgetBackground()` on the root container, so the launcher can apply its own corner masking and the system "widget background" treatment.  
- `.cornerRadius(android.R.dimen.system_app_widget_background_radius)` on API 31+, 16 dp fallback below.  
- Every colour from `GlanceTheme.colors`, never a literal, or the widget will be unreadable on someone's light wallpaper in dark mode.  
- Test on both a light and a dark wallpaper. Launchers apply a scrim inconsistently and `widgetBackground` at low alpha is a recurring readability failure.

---

## 6\. Getting the widget onto the home screen

This is the part that decides whether anyone ever uses the app, so it gets its own section. There are exactly two routes, and you must implement both.

### 6.1 Route 1 — the launcher's widget picker

The user long-presses the wallpaper → *Widgets* → scrolls to *Rate* → drags it out. You do not write code for this; you write resources, and the resources are what make it findable and attractive.

Checklist:

1. **Receiver declared, exported, with the `APPWIDGET_UPDATE` filter and the `android.appwidget.provider` meta-data.** Missing any one of these four \= not in the picker, or in the picker but blank. (§5.1)  
2. **`android:label` on the receiver** — the name in the picker. Short. "Rate", not "Rate — Currency Converter Widget".  
3. **`android:icon` on the receiver** (falls back to the app icon) — the small glyph in the picker list.  
4. **`android:description`** (API 31+) — one line, under 60 chars: "Convert currency at the rate you set."  
5. **`previewLayout`** (API 31+) — a real XML layout the launcher inflates in the picker. It must use only `RemoteViews`\-legal views, since it is inflated in the launcher process under the same constraints.  
6. **`previewImage`** — PNG fallback for API 26–30. Generate it once from the real widget with the Widget Preview tool, export at `xxhdpi`.  
7. **API 35+ generated previews (optional, high polish).** `AppWidgetManager.setWidgetPreview(provider, widgetCategory, remoteViews)` lets you push a live preview containing the user's *actual* currency pair and rate. Call it from `onEnabled` and again from `invalidateWidgets()`. Guard with `Build.VERSION.SDK_INT >= 35`.

Common failure to check for explicitly during QA: on a fresh install the app must have been launched **at least once** before the widget picker will show your provider on some OEM launchers (the package has to be out of the stopped state). Android lists the provider regardless on AOSP launchers, but several Chinese OEM skins do not. Mention it in the onboarding copy rather than fighting it.

### 6.2 Route 2 — one-tap pin from inside the app (do this)

`AppWidgetManager.requestPinAppWidget()` (API 26+) asks the launcher to show a "Add to Home screen?" confirmation with a preview. Glance wraps it:

class AddWidgetUseCase(private val context: Context) {

    fun isSupported(): Boolean \=  
        AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    suspend fun requestPin(): Boolean {  
        if (\!isSupported()) return false  
        return GlanceAppWidgetManager(context)  
            .requestPinGlanceAppWidget(  
                receiver \= RateWidgetReceiver::class.java,  
                preview \= RateWidget(),  
                previewState \= preferencesOf(  
                    WidgetStateKeys.AMOUNT\_INPUT to "1500",  
                ),  
                successCallback \= pinSuccessIntent(),  
            )  
    }

    private fun pinSuccessIntent(): PendingIntent \=  
        PendingIntent.getActivity(  
            context, 0,  
            Intent(context, MainActivity::class.java)  
                .putExtra(MainActivity.EXTRA\_WIDGET\_PINNED, true),  
            PendingIntent.FLAG\_UPDATE\_CURRENT or PendingIntent.FLAG\_IMMUTABLE,  
        )  
}  
Behaviour notes:

- `isRequestPinAppWidgetSupported` is `false` on launchers that do not implement it (some OEM and most third-party launchers). **Always check it, and hide the button when false** rather than showing a button that silently does nothing. Fall back to a short "long-press your home screen → Widgets → Rate" instruction with an illustration.  
- `previewState` seeds the confirmation dialog's preview with a populated widget, so the dialog shows a real conversion instead of "Tap to enter". Worth the four lines.  
- The `successCallback` fires only when the user confirms *and* the pin succeeds. Use it to dismiss your onboarding step. There is no callback for "user cancelled" — do not block your UI waiting for one.  
- On API 26–30, `requestPinAppWidget` works but the system dialog preview quality varies; on API 31+ it uses `previewLayout`.

Surface the button in three places: the end of first-run onboarding, an inline card at the top of the Converter screen while no widget exists, and Settings. Detect "no widget exists" with:

suspend fun hasWidget(context: Context): Boolean \=  
    GlanceAppWidgetManager(context).getGlanceIds(RateWidget::class.java).isNotEmpty()

### 6.3 Placement lifecycle

User drags from picker  
        │  
        ▼  
  onEnabled            (first instance only — one-time setup)  
        │  
        ▼  
  configure activity?  ──yes──►  WidgetConfigActivity  
        │                              │ must setResult(RESULT\_OK, intent  
        │ no                           │ with EXTRA\_APPWIDGET\_ID) or the  
        │                              │ launcher discards the widget  
        ▼                              ▼  
  onUpdate  ──►  Glance provideGlance  ──►  RemoteViews  ──►  rendered  
        │  
        ├── user resizes ──► onAppWidgetOptionsChanged ──► re-map from Responsive set  
        ├── rate changes  ──► updateAll() ──► re-render  
        ├── reboot        ──► APPWIDGET\_UPDATE re-broadcast ──► re-render from disk  
        │  
        ▼  
  onDeleted            (per instance — Glance clears that instance's state file)  
        │  
        ▼  
  onDisabled           (last instance removed — cancel any WorkManager jobs)  
The `configure` contract is the one that bites. If `WidgetConfigActivity` finishes without `setResult(RESULT_OK, ...)` carrying `EXTRA_APPWIDGET_ID`, the launcher assumes the user cancelled and **silently removes the widget it just placed**. The user experiences this as "I dragged it and nothing happened."

class WidgetConfigActivity : ComponentActivity() {  
    private var appWidgetId \= AppWidgetManager.INVALID\_APPWIDGET\_ID

    override fun onCreate(savedInstanceState: Bundle?) {  
        super.onCreate(savedInstanceState)  
        // Default to cancelled, so a back-press does the right thing.  
        setResult(RESULT\_CANCELED)  
        appWidgetId \= intent?.extras?.getInt(  
            AppWidgetManager.EXTRA\_APPWIDGET\_ID, AppWidgetManager.INVALID\_APPWIDGET\_ID  
        ) ?: AppWidgetManager.INVALID\_APPWIDGET\_ID  
        if (appWidgetId \== AppWidgetManager.INVALID\_APPWIDGET\_ID) { finish(); return }

        setContent { RateTheme { WidgetConfigSheet(onDone \= ::confirm) } }  
    }

    private fun confirm() {  
        lifecycleScope.launch {  
            RateWidget().update(this@WidgetConfigActivity,  
                GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(appWidgetId))  
            setResult(RESULT\_OK, Intent().putExtra(  
                AppWidgetManager.EXTRA\_APPWIDGET\_ID, appWidgetId))  
            finish()  
        }  
    }  
}

### 6.4 Why `configuration_optional`

With a bare `android:configure`, the config activity is forced on **every** drop. For a widget whose defaults are already meaningful, that is friction for no gain. `android:widgetFeatures="reconfigurable|configuration_optional"` (API 31+) means:

- `configuration_optional`: skip the config screen at drop time; render immediately with defaults.  
- `reconfigurable`: the launcher offers a reconfigure affordance on the widget's long-press menu, so the config screen stays reachable.

On API 26–30 the flag is ignored and configuration is mandatory, so `WidgetConfigSheet` must be genuinely useful and completable in two taps: currency pair, rate, Done. Alternative, if you would rather not write a config activity at all: drop the `android:configure` attribute entirely and rely on defaults plus in-app settings. That is a legitimate v1 choice and saves a screen.

### 6.5 Sizing reference

Pre-API-31 launchers derive cell counts from dp with `dp = 70 × cells − 30`:

| Cells | dp | Notes |
| :---- | :---- | :---- |
| 1 | 40 | Unusably narrow for text |
| 2 | 110 | Minimum readable — `SingleLineLayout` |
| 3 | 180 | Default — `StackedLayout` |
| 4 | 250 | Keypad-eligible height |
| 5 | 320 | `WideLayout` / max |

Actual cell dimensions vary by device and launcher grid (a Pixel is 4×5 or 5×5, some OEMs 4×6), so treat cell counts as intent and let `SizeMode.Responsive` handle reality. Never assume a dp size in layout code; read `LocalSize.current`.

---

## 7\. In-app UI

### 7.1 Navigation

MainActivity (single activity, Navigation Compose)  
├── converter   (start)  
├── settings  
└── settings/picker/{slot}     slot \= "home" | "local"  
Three destinations. Compose Navigation with a typed sealed route. Deep link `rate://converter` so the widget's tap target lands directly on the start destination even from a cold start.

### 7.2 Converter screen

┌────────────────────────────────────────┐  
│  Rate                            \[⚙\]  │  
├────────────────────────────────────────┤  
│  ┌──────────────────────────────────┐  │  ← sticky card  
│  │ JPY                              │  │  
│  │ ¥ \[ 1500              \]     \[⇅\] │  │  ← focused on entry, Decimal IME  
│  │ ──────────────────────────────── │  │  
│  │ INR                              │  │  
│  │ ₹ 870.00                         │  │  ← selectable text, long-press copy  
│  └──────────────────────────────────┘  │  
│  1 JPY \= 0.58 INR · edited 2 days ago  │  
├────────────────────────────────────────┤  
│  \[ \+ Add widget to home screen \]       │  ← only while hasWidget() \== false  
├────────────────────────────────────────┤  
│  ¥1            →            ₹0.58     │  ← LazyColumn, 46 rows  
│  ¥2            →            ₹1.16     │  
│  ¥5            →            ₹2.90     │  
│  ¥10           →            ₹5.80     │  
│  …                                     │  
│  ¥100,000      →            ₹58,000   │  
└────────────────────────────────────────┘  
class ConverterViewModel(  
    private val repository: RateConfigRepository,  
    private val engine: ConversionEngine,  
) : ViewModel() {

    private val amountInput \= MutableStateFlow("")

    val state: StateFlow\<ConverterUiState\> \= combine(  
        repository.config, amountInput,  
    ) { config, raw \-\>  
        if (\!config.isUsable) return@combine ConverterUiState.NeedsRate(config)  
        val formatter \= MoneyFormatter()  
        val parsed \= AmountParser.parse(raw)  
        ConverterUiState.Ready(  
            config \= config,  
            rawInput \= raw,  
            converted \= parsed?.let {  
                engine.convert(it, config, CurrencyCatalog.fractionDigits(config.homeCurrency))  
            },  
            ladder \= buildLadder(config, engine, formatter),  
        )  
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5\_000),  
              ConverterUiState.Loading)

    fun onAmountChanged(raw: String) {  
        if (AmountParser.isEditable(raw)) amountInput.value \= raw  
    }  
}  
The ladder is rebuilt on every keystroke by that `combine`. 46 `BigDecimal` multiplies plus 92 `NumberFormat` calls is around 1–2 ms — measurably fine, but if a profile ever says otherwise, derive `ladder` from `repository.config` alone in a separate flow, since it does not depend on `amountInput` at all. Do that from the start if you prefer; it is strictly better and costs one more `combine`.

Ladder rows: `LazyColumn` with `key = { it.localAmount }`, fixed 48 dp row height, `contentPadding` for the sticky card. Highlight the row nearest the current input (`ladder.minByOrNull { (it.localAmount - parsed).abs() }`) with a tonal background — small touch, genuinely useful for building intuition.

### 7.3 Settings screen

| Control | Behaviour |
| :---- | :---- |
| Home currency | Row → currency picker. Writes via `setPair`. |
| Local currency | Row → currency picker. |
| Exchange rate | Inline numeric field. Shows both directions live: "1 JPY \= \[0.58\] INR" and below, greyed: "1 INR \= 1.72 JPY". Editing either writes `rate` in canonical direction. |
| Swap | Swaps the pair and inverts the rate in one `updateData`. |
| Add widget | Visible only when `isRequestPinAppWidgetSupported`. |
| Rate age | "Last edited 2 days ago." Nudge only, no expiry enforcement. |

Rate input validation: reject `0`, negatives, and anything that parses to more than 12 integer digits. Show the error inline, keep the last valid value in the store, never write a partial parse. Allow up to 6 decimal places — a JPY→INR rate needs 2, but IDR→INR needs 5\.

### 7.4 Currency picker

`LazyColumn` over `CurrencyCatalog`, search field filtering on code prefix then name substring, a "recent" section of the last 5 selections (Preferences DataStore, separate file), flag emoji \+ code \+ name per row. Returns via `savedStateHandle` on the nav back stack.

---

## 8\. Cross-cutting concerns

### 8.1 Accessibility

- Every widget click target gets `GlanceModifier.semantics { contentDescription = … }`. The amount row reads "Amount, 1500 yen, tap to edit"; the result reads "870 rupees".  
- Keypad buttons (Option B) must be ≥ 48 dp. At 4 columns that needs ≥ 216 dp of width — hence the `240.dp` gate in §5.5. Do not ship a keypad below it.  
- In-app: `TalkBack` pass on the ladder. 46 rows of "¥1 arrow ₹0.58" is noisy, so give each row a single merged `contentDescription`: "1 yen is 0.58 rupees".  
- Result text in-app is `SelectionContainer`\-wrapped for copy. Widget text cannot be selected; provide a copy action in the wide layout instead if users ask.  
- Respect font scale up to 200%. The widget cannot, in practice, honour large font scales at 2×1 — accept clipping at the extreme and make sure 3×2 works at 150%.

### 8.2 Localisation and RTL

- All strings in `strings.xml`, no concatenation for the rate caption — use a positional format: `"1 %1$s = %2$s %3$s"`.  
- `GlanceModifier` handles RTL automatically if you use `Row`/`Column` with `Alignment.Start/End` rather than `Left/Right`. Never use `Left`/`Right`.  
- `MoneyFormatter` reads `Locale.getDefault()`, so grouping follows the user's locale. Rebuild the formatter on `ACTION_LOCALE_CHANGED` and `updateAll()` the widgets.

### 8.3 Error and empty states

| Condition | Widget | App |
| :---- | :---- | :---- |
| `rate == 0` (unconfigured) | "Tap to set your rate" on the whole surface → Settings | Converter shows a `NeedsRate` card with a rate field inline |
| Amount empty | Result shows `—` | Result shows `—`, ladder still populated |
| Amount unparseable | Not reachable (input is validated on entry) | Field shows error, previous result retained |
| Config read fails / corrupt | `.catch` in the repository emits defaults; widget renders the unconfigured state | Same |
| `glanceId` no longer valid (widget removed mid-flow) | — | `getGlanceIdBy` throws `IllegalArgumentException`; catch it and no-op |

The last row matters for `QuickConvertActivity`: the user can remove the widget while the overlay is open. Wrap `getGlanceIdBy` in a `runCatching` and just `finish()`.

---

## 9\. Testing

### 9.1 Unit — `ConversionEngine` and parsing (highest value per line)

@Test fun \`ladder and live field agree for every ladder amount\`() {  
    val config \= RateConfig(inr, jpy, BigDecimal("0.5832"), 0L, MANUAL)  
    val ladder \= buildLadder(config, engine, formatter)  
    ladder.forEach { row \-\>  
        val live \= engine.convert(row.localAmount, config, 2\)  
        assertEquals(row.homeAmount, live)          // invariant from §3.2 rule 4  
    }  
}

@Test fun \`zero fraction digit currency never shows decimals\`() { /\* JPY as target \*/ }  
@Test fun \`rate with six decimals rounds half up at target scale\`() { /\* IDR \*/ }  
@Test fun \`comma decimal separator parses identically to period\`() { /\* fr-FR \*/ }  
@Test fun \`twelve integer digits accepted, thirteen rejected\`() { /\* … \*/ }  
@Test fun \`inverse of inverse returns original within tolerance\`() { /\* swapPair \*/ }  
Parameterise the arithmetic tests across a currency matrix that includes a 0-digit (JPY), 2-digit (INR), and 3-digit (KWD) currency, and rates spanning `0.000058` (IDR→INR) to `92.5` (KWD→INR). This is where the bugs actually are.

### 9.2 Glance unit tests

`androidx.glance:glance-appwidget-testing` gives composition-level assertions without a device:

@Test fun unconfigured\_state\_prompts\_for\_rate() \= runGlanceAppWidgetUnitTest {  
    setAppWidgetSize(DpSize(180.dp, 110.dp))  
    provideComposable { RateWidgetContent(config \= unconfigured, …) }  
    onNode(hasText("Tap to set your rate")).assertExists()  
}

@Test fun narrow\_size\_renders\_single\_line() \= runGlanceAppWidgetUnitTest {  
    setAppWidgetSize(DpSize(110.dp, 40.dp))  
    provideComposable { RateWidgetContent(config \= jpyToInr, rawAmount \= "1500", …) }  
    onNode(hasText("870.00", substring \= true)).assertExists()  
}  
Cover all four size buckets. Size-bucket regressions are the most common widget bug and the cheapest to test.

### 9.3 Instrumentation

- `RateConfigRepository` against a real DataStore in a temp dir; assert `updateData` concurrency and corruption recovery.  
- `QuickConvertActivity` with `ActivityScenario`: launch with an `appWidgetId` extra, type, assert the Glance state was written.  
- Multi-instance: pin two widgets programmatically via `AppWidgetHost` in the test process, write different amounts, assert no cross-talk. This is the test that catches the `PendingIntent` bug in §5.7.

### 9.4 Manual matrix

Widgets are the one Android surface where the launcher is a second implementation of your UI, so device testing is not optional.

| Axis | Values |
| :---- | :---- |
| Launcher | Pixel Launcher, Nova, Samsung One UI Home, Xiaomi HyperOS |
| API | 26, 31, 34, 36 |
| Size | 2×1, 3×2, 5×2, 4×4 |
| Theme | Light/dark × light/dark wallpaper × dynamic colour on/off |
| Font scale | 100%, 150%, 200% |
| Instances | 1, then 3 with different amounts |

Plus these specific checks: reboot with three widgets placed; force-stop the app then tap a widget; remove a widget while its overlay is open; change system locale to `fr-FR` and confirm separators; clear app data and confirm widgets fall back to the unconfigured state rather than going blank.

---

## 10\. Build and release

android {  
    compileSdk \= 36  
    defaultConfig {  
        minSdk \= 26  
        targetSdk \= 36  
    }  
    buildFeatures { compose \= true }  
    buildTypes {  
        release {  
            isMinifyEnabled \= true  
            isShrinkResources \= true  
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),  
                          "proguard-rules.pro")  
        }  
    }  
}  
R8 notes:

- Manifest-declared components (`RateWidgetReceiver`, activities) are kept automatically. No rule needed.  
- Glance and DataStore ship consumer rules; do not duplicate them.  
- Generated protobuf classes need no rule with the Kotlin-lite plugin, but verify the release build renders a configured widget before shipping. A stripped serializer shows up as a permanently unconfigured widget and nothing else.  
- `isShrinkResources = true` can strip `previewImage` if it is only referenced from the provider XML. Add `tools:keep` in `res/raw/keep.xml` if the picker preview disappears in release.

Baseline profile: worth generating for `QuickConvertActivity`, since its cold-start latency is the whole justification for Option A over Option B. Target under 200 ms warm.

App size target: under 3 MB. No images beyond the icon and the preview, no third-party UI libraries.

---

## 11\. Phased delivery plan

Sized for AI-agent execution: each phase is independently buildable, independently verifiable, and has an explicit exit gate. Do not start a phase before its predecessor's gate passes.

### Phase 0 — Skeleton (0.5 day)

1. Project from the Compose template, `minSdk 26`, version catalog per §2.4.  
2. `RateApplication` \+ `AppContainer`.  
3. Theme, colour scheme, typography.  
4. `MainActivity` with an empty Navigation graph.

**Gate:** `./gradlew assembleDebug` clean; app launches to a blank screen.

### Phase 1 — Domain and data (1 day)

1. `CurrencyCode`, `CurrencyMeta`, `RateConfig`, `RateSource`.  
2. `currencies.json` asset \+ `CurrencyCatalog` with lazy parse.  
3. `ConversionEngine`, `AmountParser`, `MoneyFormatter`, `LadderSpec`, `buildLadder`.  
4. Proto schema, `RateConfigSerializer`, `RateConfigRepository`.  
5. Full unit suite from §9.1.

**Gate:** all §9.1 tests green, including the JPY/INR/KWD/IDR matrix. No Android imports in `domain/`. Enforce with a lint rule or an import-check test.

### Phase 2 — In-app UI (1.5 days)

1. `ConverterViewModel`, `ConverterScreen`, sticky card, ladder `LazyColumn`.  
2. `SettingsViewModel`, `SettingsScreen`, bidirectional rate field.  
3. `CurrencyPickerScreen` with search and recents.  
4. `NeedsRate` first-run path.

**Gate:** with rate set manually in Settings, ladder values match a spreadsheet computed independently for 3 currency pairs. First-run flow reaches a usable converter in under 4 taps.

### Phase 3 — Widget, default interaction (2 days)

1. `rate_widget_info.xml`, manifest receiver, `widget_loading` and `widget_preview` layouts.  
2. `RateWidgetReceiver`, `RateWidget`, `WidgetStateKeys`.  
3. `SingleLineLayout`, `StackedLayout`, `WideLayout` \+ `SizeMode.Responsive`.  
4. `QuickConvertActivity` \+ dialog theme \+ per-widget intent `data` URIs.  
5. `invalidateWidgets()` wired into every repository mutation.  
6. Glance unit tests per §9.2.

**Gate:** widget appears in the Pixel Launcher picker with a populated preview; drops and renders in under 500 ms; resizes across all four buckets without clipping; three instances hold independent amounts; survives reboot; rate change in app updates all instances within 1 s.

### Phase 4 — Placement polish and the keypad (1.5 days)

1. `AddWidgetUseCase` \+ `requestPinGlanceAppWidget` with `previewState`.  
2. `hasWidget()`\-gated inline card on Converter, plus Settings entry, plus onboarding step.  
3. `isRequestPinAppWidgetSupported == false` fallback instruction screen.  
4. `WidgetConfigActivity` with the `RESULT_OK` contract (or the documented decision to drop `android:configure`).  
5. API 35+ `setWidgetPreview` with live values.  
6. `KeypadLayout` \+ `AppendDigit`/`Backspace`/`Clear`/`ToggleKeypad` actions, gated at 240×200 dp.

**Gate:** one-tap add works on Pixel Launcher and degrades correctly on a launcher without pin support. Keypad digit-to-render latency measured and recorded; if the median exceeds 250 ms, ship the keypad disabled by default and revisit.

### Phase 5 — Optional live rates (1 day, post-v1)

1. `RateSource.FETCHED_REMOTE`, `exchangerate.host` or similar, Ktor client.  
2. `WorkManager` periodic job, 12 h, `NetworkType.CONNECTED`, cancelled in `onDisabled`.  
3. "Use live rate" toggle; manual entry always wins when set.  
4. Stale-rate indicator in the widget caption.

**Gate:** offline behaviour identical to v1. A failed fetch never overwrites a manual rate and never blanks the widget.

### Phase 6 — Backlog

Magnitude-aware ladder; tip/GST helper; per-widget currency pair override; Wear tile; home-screen result-only "glance" variant.

**Total to v1 (Phases 0–4): \~6.5 focused days.** At roughly 2 hours a night that is three weeks of evenings; Phase 3 is the one that will overrun, because launcher behaviour is where the unknowns live.

---

## 12\. Risks

| \# | Risk | Likelihood | Impact | Mitigation |
| :---- | :---- | :---- | :---- | :---- |
| 1 | Widget text input is impossible, and the overlay feels like "leaving" the home screen | Certain | High | §1.4 Option A; dialog theme with the launcher visible behind; keypad as a large-size alternative |
| 2 | Keypad latency makes Option B unusable | High | Medium | Gate behind size; measure in Phase 4; ship disabled if median \> 250 ms |
| 3 | `PendingIntent` reuse cross-wires multiple widget instances | High if untested | High | Distinct `data` URI per widget; multi-instance instrumentation test |
| 4 | OEM launcher does not support `requestPinAppWidget` | Medium | Medium | Capability check \+ illustrated manual instructions |
| 5 | Glance view-tree limits hit by the keypad grid | Medium | Medium | Keep depth ≤ 4; 4×3 grid as three `Row`s in one `Column`, not nested containers |
| 6 | R8 strips proto serialisation; widget shows unconfigured in release only | Medium | High | Release-build smoke test in the Phase 3 gate, not at ship time |
| 7 | Rounding mismatch between ladder and live field | Low | High | §3.2 rule 4 \+ the invariant test in §9.1 |
| 8 | Ladder is wrong for non-JPY-magnitude currencies | Certain but accepted | Low | Documented; `LadderSpec` abstraction ready for Phase 6 |
| 9 | Fresh-install widget invisible in picker until first app launch on some skins | Low | Low | Onboarding copy |

---

## Appendix A — Ladder values

46 rows, ascending:

1, 2, 5, 10, 20, 30, 40, 50,  
100, 150, 200, 250, 300, 400, 500, 600, 700, 800, 900,  
1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500, 5000, 6000, 7000, 8000, 9000,  
10000, 15000, 20000, 25000, 30000, 35000, 40000, 45000, 50000,  
60000, 70000, 80000, 90000, 100000  
Worked example at `1 JPY = 0.58 INR`, target scale 2, `HALF_UP`:

| JPY | INR | JPY | INR |
| :---- | :---- | :---- | :---- |
| 1 | 0.58 | 1,000 | 580.00 |
| 2 | 1.16 | 5,000 | 2,900.00 |
| 5 | 2.90 | 10,000 | 5,800.00 |
| 10 | 5.80 | 50,000 | 29,000.00 |
| 100 | 58.00 | 100,000 | 58,000.00 |

## Appendix B — Key files checklist

| File | Purpose | Phase |
| :---- | :---- | :---- |
| `AndroidManifest.xml` | Receiver (exported, filter, meta-data), 3 activities | 3 |
| `res/xml/rate_widget_info.xml` | All placement and sizing behaviour | 3 |
| `res/layout/widget_loading.xml` | Pre-composition skeleton | 3 |
| `res/layout/widget_preview.xml` | API 31+ picker preview | 3 |
| `res/drawable/widget_preview_legacy.png` | API 26–30 picker preview | 3 |
| `res/values/themes.xml` | `Theme.Rate.QuickConvert` | 3 |
| `src/main/proto/rate_config.proto` | Persisted config schema | 1 |
| `assets/currencies.json` | ISO 4217 curated catalog | 1 |
| `widget/RateWidget.kt` | `GlanceAppWidget`, size modes, composition root | 3 |
| `widget/RateWidgetReceiver.kt` | Four lines; do not override lifecycle methods | 3 |
| `ui/quick/QuickConvertActivity.kt` | The real text input | 3 |

## Appendix C — API level feature map

| Feature | Min API | Fallback below |
| :---- | :---- | :---- |
| App widgets, `resizeMode` | 26 (project floor) | — |
| `requestPinAppWidget` | 26 | Manual instructions |
| `targetCellWidth/Height`, `maxResizeWidth/Height` | 31 | `minWidth`/`minHeight` cell math |
| `previewLayout`, `description` | 31 | `previewImage` PNG |
| `widgetFeatures="configuration_optional"` | 31 | Config screen forced on every drop |
| Dynamic colour in widgets | 31 | Static `ColorProviders` |
| `system_app_widget_background_radius` | 31 | 16 dp literal |
| `CheckBox`/`Switch` in `RemoteViews` | 31 | `Button` \+ state |
| `setWidgetPreview` (generated previews) | 35 | `previewLayout` |
| **`EditText` in a widget** | **never** | §1.4 |

---

*End of document.*

