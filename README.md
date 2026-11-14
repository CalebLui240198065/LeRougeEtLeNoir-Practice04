# Le Rouge et le Noir · Vibe Coding Practice 05

This Android project builds on Practice 04's *Le Rouge et le Noir* game collection. It adds a playable Bingo card and a SQLite history of completed rounds. The original login, settings, Tic Tac Toe, Snake, and Breakout are available from the same salon.

## Run the app

1. Open this folder in Android Studio and sync with the bundled Gradle wrapper.
2. Use Android SDK 37 and JDK 25 for the Gradle daemon. Run the `app` configuration on Android 7.0 (API 24) or later.
3. On the login screen, tap **First visit? Use the demo account**, then **Enter the salon**. The sample username is `julien` and the sample password is `rouge1820`.

Build and test from Windows PowerShell with the Android SDK configured:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. This practice uses application ID `com.rougenoir.app.practice05` so it can be installed alongside Practice 04 with its own local data.

## Play Bingo

1. Choose **Bingo** from **The Salon**. The 5 × 5 card uses the numbers 1 through 25, including a numbered center.
2. Tap **Draw number**, then tap any outlined square to mark it. Each number is drawn once per card. Previously drawn squares can still be marked.
3. Complete a row, column, or diagonal. The five winning squares turn gold and the app saves the round. When one tap completes several lines, the app records the first completed row, then checks columns, then diagonals.
4. Choose **New round** for a fresh card or **Round history** to view saved rounds, most recent first.

## SQLite round history

The app creates `salon_bingo.db` in private app storage. The `bingo_rounds` table contains:

| Column | Value |
| --- | --- |
| `username` | Username of the signed-in player |
| `round_number` | Completed round number, starting at 1 for each username |
| `number_1` through `number_5` | The five winning numbers in board order |
| `system_time` | Time of the winning mark from the device clock, stored as Unix epoch milliseconds and displayed in local time |
| `round_id` | Internal ID used to identify a save retry for the same round |

`BingoGame.kt` handles calls, marking, and win detection. `BingoDatabase.kt` uses Android's `SQLiteOpenHelper` and saves round numbering and results in one transaction. `BingoActivity.kt` and `BingoHistoryActivity.kt` present the game and history. Profile settings and the original game scores use `SharedPreferences`.

The unit tests cover duplicate calls, invalid marks, wins in both directions and diagonals, restoration, and the original Tic Tac Toe rules. The bundled fonts retain their OFL notices in `app/src/main/assets/licenses`.
