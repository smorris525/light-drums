# Drums — Light Phone Tool Spec

A step sequencer and drum pads for the Light Phone III. Version 1.

The app says **pattern** everywhere. Sound names are shown in lowercase.

## 1. Scope

- **Device:** Light Phone III only. One portrait screen, 1080 × 1240 px (about 360 × 413 dp). No other layouts.
- **Look:** Light's own building blocks wherever they exist: `LightTheme`, the LightOS font and text styles, `LightTopBar`, `LightBottomBar`, `LightScrollView`, `LightTextInputEditor`, `LightIcons`. Custom drawing only for the musical parts with no SDK equivalent: the step dots, the tick row, the pads, and a few icons. LightOS conventions throughout.
- **No animation.** Every change is an instant swap. The pad flashes are state flips held for a moment, not animations.
- **Synthesised sound only.** No audio files are bundled. Every sound is generated on the phone.
- **Local only.** No network, no accounts, no sync, no analytics. Nothing leaves the device.
- **Foreground only.** Audio stops when the tool leaves the screen.

## 2. Tool identity

`tool/lighttool.toml`:

```toml
[tool]
id = "com.stephenmorris.drums"   # permanent once registered
label = "Drums"
versionCode = 1
versionName = "1.0.0"
permissions = []
capabilities = []
serverPackage = "com.lightos"
```

The Kotlin package is `com.stephenmorris.drums`. The source is public, as Light requires.

## 3. Data & persistence

- **Room** database, local only. Every edit saves immediately.
- **The Click setting** (off / rec, default rec) is kept in **DataStore**. Any other stored value reads as rec.

### Model

```kotlin
@Entity
data class Pattern(
    @PrimaryKey val uuid: String,       // UUID
    val name: String = "New Pattern",
    val createdAt: Long,                // epoch ms; library order, ascending
    val editedAt: Long = 0,             // set by every edit
    val bpm: Int = 100,                 // 30..300; counts the meter's beat unit (§5)
    val meter: MeterId = MeterId.FourFour, // "3/4" "4/4" "5/4" "6/8" "7/8" "12/8"
    val bars: Int = 1,                  // 1..8
    val swing: Swing = Swing.Off,       // off | eighthLight | eighthHeavy | sixteenthLight | sixteenthHeavy
    val stepsJson: String = "",         // StepGrid as JSON (kotlinx.serialization); empty or unreadable = empty grid
)

/** Per sound, the steps it plays on and how loud. Sparse. */
@Serializable
data class StepGrid(val hitsBySound: Map<String, Map<Int, Level>>)  // sound id -> absolute step -> level
enum class Level { Hit, Accent }        // absence = rest
```

The grid is stored as JSON with sorted keys, for example `{"hitsBySound":{"kick":{"0":2}}}`, so equal grids store equal strings. Unknown meter, swing or sound values read back as safe defaults (4/4, off, ignored). Steps beyond the pattern's length are ignored when playing and trimmed on the next commit.

### Sample patterns

Seeded once, on first launch, when the database is empty. Five well-known grooves that between them use four meters, 1/16 swing, accents against quiet hits, lengths of 1, 2 and 4 bars, and every sound. Their grids are in `SamplePatterns.kt`.

| Name | BPM | Meter | Bars | Swing |
|---|---|---|---|---|
| Funk Break | 96 | 4/4 | 2 | 1/16 light |
| House | 124 | 4/4 | 1 | 1/16 light |
| Soul | 80 | 6/8 | 4 | off |
| Math Rock | 112 | 5/4 | 2 | off |
| Balkan Dance | 260 | 7/8 | 2 | off |

## 4. Sounds & audio

Eight fixed sounds, always in this order: `kick`, `snare`, `sidestick`, `hat`, `openHat` (shown as "open hat"), `ride`, `tom`, `shaker`.

- **The synth:** each sound is built from tones, filtered noise and "metal" (six square waves at inharmonic ratios, band- and high-passed), each with an exponential envelope. The filters use the Web Audio `BiquadFilterNode` formulas. Every sound is **rendered once at launch into a PCM buffer**. Noise uses a fixed seed, so every hit of a sound is identical.
  - The **open hat** is the closed hat's metal under a wash of band-passed noise, decaying over 0.7 s.
  - The **ride** is a wash of noise, a stick's tick and six sine tones at unrelated pitches, with a trace of the hats' metal.
  - The **shaker** is a band-passed noise burst (6 kHz, 12 ms swell, 120 ms decay) under a short 9 kHz high-passed tick.
  - Metal is brought up to peak at twice its nominal peak, since its filters leave it well under its nominal level.
- **Per-sound trims:** kick 0.43, tom 0.48, open hat 0.62, ride 0.38, sidestick 1.12, closed hat 1.15. Together they balance the kit against the snare (K-weighted energy over each hit's first 300 ms): kick and tom about 1.5 dB under, open hat about 6 dB under, sidestick and closed hat about 8.5 dB under, shaker about 9 dB under, ride about 10 dB under.
- **Levels:** accent 1.0, hit 0.6. A pad tap plays at 0.85, or 1.0 when accented.
- **Kit levelling:** at launch, the kit's gain is set so the mean power of the kick and snare (K-weighted, ITU-R BS.1770, over each hit's first 300 ms) sits at −23 dB. The gain is held back if any one accented sound plus the accented click would pass 0.99.
- **Compressor** on the master bus: −12 dB threshold, 4:1 ratio, 30 dB knee, 3 ms attack, 250 ms release, no make-up gain. **Limiter** last: it turns the mix down at once on any sample that would pass 0.99 and recovers over 50 ms.
- **Voices:** every sound is monophonic, and a retrigger fades the previous voice out over a few milliseconds. **hat chokes open hat.** Both may be stored on one step; the choke decides what's heard.
- **Click** (count-in and metronome): a 45 ms sine blip, 1,760 Hz on beat 1 and 1,180 Hz on other beats, at gain 0.37. One click per beat (7/8 clicks three times a bar: 2+2+3).

### Engine

- **Kotlin only.** Light's build rejects native code. A single low-latency `AudioTrack` (`PERFORMANCE_MODE_LOW_LATENCY`, mono float or 16-bit at the device's native rate) is fed by the app's own mixer thread. The mixer mixes the voices, then runs the compressor and the limiter.
- **Sample-accurate lookahead scheduling** against the track's frame position, with no per-step timers. The UI playhead derives from the audio clock. Tempo, swing and step edits apply from the next unscheduled step. A pattern made shorter while playing carries on from the same step number, wrapped into the new length.
- **One app-wide transport,** so at most one pattern plays at a time.
- **Latency:** expect roughly 20–40 ms for the pads, but this is unmeasured on Light Phone hardware. The app logs its measured output latency (`adb logcat -s DrumsAudio`) so a tester with a phone can report it. On the API 34 emulator it measured 43 ms at 48 kHz, with 0 underruns. Recording compensates for output latency.
- **Audio focus:** not requested. The SDK doesn't give tools access to it. Playback stops when the tool is paused (`onAppPause`), which covers calls and leaving the tool.

## 5. Timing model

| Meter | Groups (steps per beat) | Steps per bar | BPM counts | Swing allowed |
|---|---|---|---|---|
| 3/4 | 4 4 4 | 12 | quarter | yes |
| 4/4 | 4 4 4 4 | 16 | quarter | yes |
| 5/4 | 4 4 4 4 4 | 20 | quarter | yes |
| 6/8 | 3 3 | 6 | dotted quarter | no |
| 7/8 | 2 2 3 | 7 | **eighth** | no |
| 12/8 | 3 3 3 3 | 12 | dotted quarter | no |

- `unit` is one step as a fraction of the BPM beat: 1/4 for x/4, 1/3 for 6/8 and 12/8, 1 for 7/8. Step duration = `60 / bpm × unit`.
- **Swing** applies to x/4 only, with ratio `r` = 0.58 light, 0.66 heavy. The position of each 16th within the beat, as a fraction of the beat: 1/8 swing `[0, r/2, r, r + (1−r)/2]`; 1/16 swing `[0, r/2, 0.5, 0.5 + r/2]`. In 6/8, 7/8 and 12/8 the stored swing is kept but not applied, and comes back on returning to an x/4 meter.
- **Meter change (remap):** each hit keeps its bar and beat number and moves to `round(offset × newBeatLength / oldBeatLength)` within the beat. Hits that round past the end of their beat, or sit in beats the new meter doesn't have, are dropped. Where two hits land on one step, the louder wins.
- **Bar-count change (resize):** added bars repeat the existing bars in order (step `i` copies step `i mod oldLength`). Fewer bars drops bars from the end.
- **Reversible settings session:** while Settings is open, meter and bar changes are computed from a snapshot taken when it opened, so 4/4 → 7/8 → 4/4 or 4 bars → 2 → 4 restores everything. Closing Settings commits, trimming hidden hits.

## 6. Navigation & lifecycle

The SDK owns the back stack and the back button. Screens:

```
Library ──► Pattern ──► Sound (per sound)
              │  ├──► Pads
              │  ├──► Settings ──► Tempo / Time Signature / Length / Swing
              │  ├──► Rename
              │  └──► Actions ──► Delete confirmation
              └── (Pads also opens Settings, Rename, Actions)
```

- **Back from Pattern or Pads to the Library stops playback** and ends any take in progress. Opening and leaving the Sound, Rename and Settings screens doesn't stop playback.
- **Leaving the tool** (home, screen off, a call) stops playback and ends a take. Returning while the tool is still in memory shows the same screen, stopped. **A fresh launch opens the Library.**
- One-time setup (rendering the kit, opening the database) goes in view model `init`, not `onScreenShow`, which runs again on every return.

## 7. Library

- **No header.** A `LightScrollView` of rows, oldest first. Each row shows the name, plus a meta line `68 BPM · 4/4 · 4 bars` ("1 bar" singular, swing not shown), in SDK text styles. Tapping a row opens the pattern.
- **Bottom bar:** `EDIT` at the left and ⊕ (`LightIcons.ADD`) at the right. With no patterns, only ⊕, centred.
- **Edit mode:** `EDIT` becomes `DONE`. Each row gets a `LightIcons.DELETE` button at its left (names indent to make room). Tapping it opens the **delete confirmation** (§12). Tapping a row does nothing in edit mode. Deleting the last pattern leaves edit mode.
- **⊕** creates "New Pattern" (then "New Pattern 2", …): 100 bpm, 4/4, 1 bar, swing off, no hits. It leaves edit mode and opens the new pattern on the Pattern screen.
- **Empty state:** "no patterns yet".

## 8. Pattern screen

- **Header (`LightTopBar`):** back on the left; the pattern name in the centre (one line, truncated; **tap to rename**, §11); the actions icon on the right (§12).
- **Bar dots** below the header when bars > 1: one `LightIcons.SELECT_ON` per bar in view and `SELECT_OFF` for the others, in 44 dp-tall tap targets. A short line under a dot marks the playing bar, only when that bar is out of view. Tapping a dot moves the window so that bar comes first, clamped so the window stays full.
- **Window:** one bar in view, or two in a meter with two beats or fewer (6/8) once there are two or more bars.
- **Follow:** Play turns follow on. While following, when the playhead leaves the window, the window jumps to start at the playing bar. Editing a step or tapping a bar dot turns follow off until the next Play, except tapping the playing bar's dot, which turns it back on. Play starts from the first bar in view; Stop leaves the view where it is.
- **The all-sounds grid:** a tick row (the playhead), then eight sound rows sharing the remaining height evenly, up to a sensible maximum, with any spare height centred. Each row shows the sound's name in a fixed column, then one dot per step, grouped by beat and bar, with gaps between beats and a wider one between bars.
  - **Dots are always circles.** The diameter scales from `min(step cell width, row height)`, keeping 10 : 6 : 2 proportions for accent, hit and rest, and never touching neighbours.
  - **Read-only.** A tap anywhere on a row (name or dots) opens that sound's **Sound screen** (§9). There's no selected sound.
- **Opening a pattern:** bar 1 in view, transport stopped.
- **Bottom bar:** [⚙︎ Settings] [▶/■] [⊞ Pads].

## 9. Sound screen

Opened by tapping a row on the Pattern screen.

- **Header:** back on the left and the sound name as the title (for example, "snare"). No rename, no actions.
- **Bar dots** as on the Pattern screen. They share the same window and follow state, so going back keeps the same bars in view.
- **Step editor:** one beat per row, each labelled with its beat number. Each step is a square tap target with a centred dot (accent, hit or rest). **The step size is calculated to fill the space without scrolling,** capped at 64 dp (56 when the meter has more than four beats) and never under 24 dp. Multiple bars in view stack. A line under the playing step marks the playhead.
- **Editing:** a tap toggles rest ↔ hit, and tapping an accent turns it off. Holding for 0.25 s sets an accent, with **one haptic buzz** at that moment; holding an accent leaves it accented. Drag to paint: starting on a rest adds hits to every step the drag crosses (existing accents kept); starting on a hit or accent erases them. The mode stays fixed for the whole drag, and each step is painted once. A movement of 8 dp starts painting, even after the accent hold has fired. Editing turns follow off. Edits are silent.
- **Bottom bar:** ▶/■ only, centred.
- Playback continues on the way in and out.

## 10. Pads & recording

- **Header and bar dots** as on the Pattern screen.
- **Eight pads in a 2 × 4 grid:** tom · shaker / sidestick · ride / snare · open hat / kick · hat. The pads **fill all the space** between the bar dots (or the header) and the bottom bar, with grid-unit gaps. There's no proportion cap, so pads may be taller than wide. Each pad has a 1 px white outline, rounded corners, and its name centred.
- **Pad text:** one size for all eight pads, the SDK's normal size shrunk just enough that the longest name fits on one line. Never truncated.
- **Touch:** each pad sounds on touch-down, with true multi-touch, and flashes solid white with black text for 120 ms. Sliding onto another pad plays nothing. No haptics. Pads don't light up from the sequencer.
- **Bottom bar:** [⚙︎] [metronome Click] [▶/■] [● Record] [step grid], five icons, the SDK's limit. Metronome and step grid are hand-drawn. Record uses `LightIcons.CIRCLE`. During the count-in, the record slot shows **the count drawn as a custom icon**, since a five-item bar can't hold text. A line under ● while recording. Click is underlined while on. Step grid returns to the Pattern screen.

### Recording

- **Record from stopped:** a one-bar count-in (its clicks always sound, whatever Click says), then recording starts with the transport. **Record while playing:** recording starts at once.
- Tap Record during a count-in: cancels and stops. Tap while recording: stops recording, keeps playing.
- **Leaving Pads** while recording ends the take.
- **Click:** on means the beats click while Pads is showing, playing or recording; off means only the count-in clicks. Off Pads, playback never clicks.
- **Overdub:** a pad tap lands on the nearest step, compensated for output latency. An accent can upgrade an existing hit; softer taps keep existing accents. A tap less than half a step before the first step (the end of the count-in) records on that step. Record, like Play, starts from the first bar in view.
- **No doubles:** a tap in the second half of a step lands on the next step, and that step's scheduled playback of that sound is suppressed once, since the tap already sounded it.
- **Accents from harder taps:** a contact radius of at least 25 dp (`MotionEvent.getTouchMajor`) or at least 50% pressure (`getPressure`) records an accent. **If the screen reports constant or missing values, this turns itself off and every tap records a plain hit.** This is unverified on hardware.

## 11. Rename

Tapping the pattern name on Pattern or Pads opens the SDK's `LightTextInputEditor`:

- Title "Rename", single line, opening with the current name and the cursor at the end.
- **DONE** saves the name (trimmed; an empty name keeps the old one) and returns.
- **Back cancels.** This is the LightOS convention.
- Playback continues. The keyboard's own haptics follow the system setting.

## 12. Actions & deleting

- **Actions** (the header's right icon, on Pattern and Pads): a full-screen menu with the pattern name, back, and **Duplicate** and **Delete**. Duplicate saves an independent copy of the pattern at the bottom of the library, named with the original name plus the lowest free numeric suffix starting at 2, and opens it.
- **Delete confirmation:** used both from library edit mode and from Actions. A full-screen modal styled like `LightFullscreenModal`, with **"Delete ‹name›?"** centred (sentence case, no quote marks, wrapping), then a `LightBottomBar` with `LightIcons.DENY` at the bottom left (cancel) and `LightIcons.ACCEPT` at the bottom right (delete). No undo.
  - From the library, you stay in edit mode.
  - From Actions, you return to the library.
  - Playback stops if the deleted pattern was playing.

## 13. Settings

Opened by ⚙︎. Every page has a `LightTopBar` (back plus title) and a bottom bar with only ▶/■. Going back from the main page closes Settings and commits the session (§5). Playback continues.

- **Main page:** rows for Tempo ("108 BPM"), Time Signature ("4/4"), Length ("4 bars" / "1 bar") and Swing ("1/16 heavy"; hidden in 6/8, 7/8 and 12/8, where the stored swing is kept). Each row is a small label above a large summary, and tapping it opens that page.
- **Time Signature** (3/4, 4/4, 5/4, 6/8, 7/8, 12/8) and **Swing** (Off, 1/8 light, 1/8 heavy, 1/16 light, 1/16 heavy): the options as rows, the current one **underlined**, as LightOS does. Picking one moves the underline, and the page stays open.
- **Length:** the bar count shown large, with `LightIcons.DOWN`/`UP` on either side (1…8). An arrow hides at its limit but keeps its space.
- **Tempo:** the BPM shown large, with `DOWN`/`UP` (30…300, clamped; hold to repeat: after 0.42 s, ±1 every 90 ms, then ±5 every 140 ms after eight repeats), above a hand-built 3 × 4 keypad (1–9, TAP, 0, delete). **Each keypad key gives the system keyboard's haptic click**, following the system setting.
  - The page opens with the current BPM shown as if typed, so delete takes digits off it; the first digit typed replaces it instead. Once the digits make a tempo, a digit that can't extend it (80 then 1, or 120 then 1) starts a new number. No cursor. Up to 3 digits, no leading 0. Delete hides once nothing's left.
  - Each digit typed or deleted sets the tempo at once when the digits make one in 30…300, so playback follows as you type. Going back applies the digits clamped to 30…300 (no digits: no change).
  - The arrows step from what's shown, and TAP sets the tempo; both show the new tempo afresh, ready to be replaced.
  - **TAP** triggers on touch-down with no flash: the tempo from the average interval of up to the last five taps within 2.5 s. It needs two taps and is clamped to 30…300.

## 14. Haptics

These follow the user's system haptics setting:

- **Accent hold on a step:** one buzz.
- **Tempo keypad keys:** a click.
- **The rename keyboard:** the SDK's own haptics.

Nothing else buzzes, including pads, step taps and painting.

## 15. Accessibility

Deferred until it's clear what screen-reader support LightOS offers tools.

## 16. Testing

Pure logic lives in `com.stephenmorris.drums.core` and the audio DSP in `com.stephenmorris.drums.audio`, with no Android dependencies, so the tests (`tool/src/test/kotlin`, kotlin.test) run on the JVM. They cover meters, step durations and swing; remap, resize and the settings session; step toggling and paint strokes; sample patterns and new-pattern naming; paging, window and follow; quantize and suppressed hits; count-in and click events; Settings summaries, tap tempo, hold acceleration and keypad entry; the pad grid; sound rendering (deterministic, never silent); and kit levelling, the compressor and the limiter (never above 0.99).

## 17. Open questions

- The pads' real latency, and whether the screen reports touch size or pressure, on Light Phone III hardware.
- An approved haptics API for tools (§14).
- Whether LightOS lets tools play audio in the background (out of scope for v1).
