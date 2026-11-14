# Le Rouge et le Noir · Vibe Coding Practice 04

This Android project is a clean copy of the original *Le Rouge et le Noir* game collection for Vibe Coding Practice 04. It adds a playable Bingo card and saves completed rounds for all four games in SQLite. The Salon leads to Bingo, Tic Tac Toe, Snake, Breakout, and The Study settings.

## Run the app

1. Open this folder in Android Studio and sync with the bundled Gradle wrapper.
2. Use Android SDK 37 and JDK 25 for the Gradle daemon. Run the `app` configuration on Android 7.0 (API 24) or later.
3. On the login screen, tap **First visit? Use the demo account**, then **Enter the salon**. The sample username is `julien` and the sample password is `rouge1820`.

Build and test from Windows PowerShell with the Android SDK configured:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. The app uses the same installation ID as the first published build so installing this update preserves local data.

## Play Bingo

1. Choose **Bingo** from **The Salon**. The 5 × 5 card uses the numbers 1 through 25, including a numbered center.
2. Tap **Draw number**, then tap any outlined square to mark it. Each number is drawn once per card. Previously drawn squares can still be marked.
3. Complete a row, column, or diagonal. The five winning squares turn gold and the app saves the round. When one tap completes several lines, the app records the first completed row, then checks columns, then diagonals.
4. Choose **New round** for a fresh card or **Round history** to view your Bingo results.

## View round history

Open **Round history** from **The Salon** and choose **All games** or an individual game. Each completed round shows the player, the round number, the outcome, and the device time. Round numbers start at 1 for each player and game. Leaving or restarting an unfinished game does not create a record.

| Game | Details saved |
| --- | --- |
| Bingo | Five winning numbers in board order and number of draws |
| Tic Tac Toe | Win, loss, or draw, rival, difficulty, and number of moves |
| Snake | Score, ending reason, and difficulty |
| Breakout | Win or loss, score, lives left, and difficulty |

## SQLite schema

Android's `SQLiteOpenHelper` stores the records in the `game_rounds` table in private app storage. The file keeps its original name, `salon_bingo.db`, so the version 2 migration can preserve earlier Bingo results. Earlier Bingo rounds have winning numbers and a completion time. Draw counts are available for new rounds.

| Column | Value |
| --- | --- |
| `username` | Username of the signed-in player |
| `game_type` | Stable name of the game |
| `round_number` | Completed round number for the player and game |
| `system_time` | Device time on completion, stored as Unix epoch milliseconds and displayed in local time |
| `outcome` | Win, loss, or draw |
| `details_json` | Game-specific facts encoded as JSON text |
| `round_id` | Unique ID that makes a retried save safe |

`RoundResult.kt` describes each game's result. `RoundDatabase.kt` assigns numbers and writes each completed result in one transaction. `RoundHistoryActivity.kt` reads the records for the signed-in user. Profile settings and the original high-score summaries use `SharedPreferences`.

The unit tests cover Bingo calls, valid marks, winning lines, restoration, and the original Tic Tac Toe rules. The bundled fonts retain their OFL notices in `app/src/main/assets/licenses`.
