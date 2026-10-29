# PROMPT FOR RIKA - FIX CP1 (cp1c), NEW QUALITY METHOD, THEN CP2

Hi Rika! You are the ORCHESTRATOR (boss).
Read ALL of this first. Save it in the repo as `docs/PHASE4_CP1C_CP2.md` and commit it.
Then tell me in 3 short sentences what you understood.

I have a LOT of tokens (billions). So: do NOT rush, do NOT skip checks to save tokens. Quality is more important than speed.
If you can choose models, use your STRONGEST model for the Reviewer and the Tester, and for hard modules.

---

## 1. WHAT I SAW ON MY PHONE (cp1b APK) - these are real bugs

1. WRONG NUMBERS. Screenshot A shows `5675/3037` (done is bigger than total!). Screenshot B shows `79/5` and the message `smart 79/5`.
   The counters are accumulated across stages and never reset, and they are not capped by the total.
2. STUCK "WAITING". In screenshot A, 5,675 files were "processed" but "ready to sort 0 / needs review 0" and the list still shows files as "Waiting".
   My guess: the engine takes the same PENDING rows again and again from the database before they were saved (saves are delayed), so files are processed many times and the status never changes.
   DO NOT TRUST MY GUESS. Prove the real cause with a failing test first (see TDD below).
3. STOP BUTTON DOES NOTHING. I press Stop and the scan keeps running.
4. VIDEOS FAIL. `lv_0_20260816214044.mp4` shows "Error: decode failed". Stage 1 must treat videos with the VideoAnalyzer frames (as in Phase 3), not with ImageDecoder.
5. The Scan screen list shows files that are "Waiting". It must show the latest PROCESSED files, not waiting ones.
6. Still missing from cp1b: Review screen paging (Repo.page with offset, 100 per page + load more), real "Change category" dialog in History, the per-processor workers / decode pool / separate DB writer thread (check they are REALLY done now, not just claimed).

---

## 2. NEW WORKING METHOD: THE "TRIO" PROTOCOL (mandatory for EVERY module)

For every module (one sub-agent job) three roles work together. They must be DIFFERENT agents:

- BUILDER: writes the code.
- REVIEWER (the Gatekeeper): runs in PARALLEL with the builder. Strongest model you have. Has a VETO: nothing is merged without the Reviewer's PASS.
- TESTER: writes and runs tests.

You may run many Trios at the same time (one per module). Reviewers of different modules also run in parallel.

### Phase 0 - FACTS SHEET (before ANY code)
The Builder writes `docs/facts/<module>.md` with:
a) every external API / class / function / library / version it will use;
b) for EACH one: the source. Allowed sources: an official doc page the builder really opened (copy the exact signature) OR an existing file + line in this repo. "From memory" is NOT allowed for external APIs;
c) risks and unknowns;
d) the plan in max 10 bullets.

### Phase 1 - INTERROGATION + CONFIDENCE GATE
The Reviewer asks the Builder 8 probe questions:
- 3 about the exact APIs it will call (signature, thread rules, what it returns, what it throws);
- 2 about edge cases (null, empty list, cancel in the middle, huge file, file deleted while scanning);
- 2 about threading and memory;
- 1 "what do you do when X fails?".
The Builder answers EACH question with evidence and a confidence score from 0 to 10.
The Reviewer then VERIFIES at least 3 answers on its own (web search, open the docs, or read the repo code). Verify, never trust.

GATE RULES:
- GO (Builder may write code) only if: all verified answers are correct AND every confidence is 8 or more AND the facts sheet has no unverified API.
- LEARN: if not GO, the Builder must study first (read docs, search the web, read the repo) and answer again. Maximum 2 LEARN rounds.
- STRIKE: if the Builder says confidence 8+ but the answer is WRONG, that is a STRIKE (confident-but-wrong is the worst). 2 strikes = REPLACE immediately.
- REPLACE: if the gate still fails after 2 LEARN rounds (or 2 strikes), spawn a NEW Builder with a fresh context. Give it the facts sheet + the Reviewer's notes (what was wrong). If you can, give it a stronger model.
  If the new Builder also fails the gate: YOU (Rika) write the module, with the Reviewer watching.
  If that also fails: ask me ONE simple question.

### Phase 2 - BUILD WITH LIVE REVIEW
The Builder writes ONE file at a time and commits after each file.
The Reviewer reads EACH diff (not only at the end) and answers PASS or FAIL with exact line numbers. It checks:
(1) the contract signatures in `docs/PHASE4.md`, (2) the facts sheet, (3) the RULES list (section 8).
FAIL -> the Builder fixes. After 3 FAIL loops on one module -> REPLACE (same as above).

### Phase 3 - TESTS (see section 4)
The Tester writes the tests, runs them, and reports test names with pass/fail.
For BUG FIXES use TDD: write a test that FAILS and shows the bug, then fix, then see it PASS.
No module is "done" until its tests pass and the Reviewer says PASS.

### Phase 4 - INTEGRATION REVIEW
After merging, the Reviewer does a whole-app pass: self-check list (section 8), greps for forbidden patterns, reads the test logs.

### EVIDENCE RULE
Every claim in your report needs evidence: file + class/function name, and the test name + result.
Never tick something you did not do or did not test. A false tick is worse than a "not done".

---

## 3. PART A - FIX CP1 (publish as `phase4-cp1c`)

Use a Trio for each module. Write the failing test FIRST.

### A1. Database claim, so nothing is processed twice (Agent "Data")
- Add Status values `RUNNING_FAST` and `RUNNING_SMART` (statuses are stored as TEXT, so no schema change; bump the DB version anyway and keep user data).
- New: `Repo.claim(ctx, status: Status /*PENDING or NEEDS_SMART*/, limit: Int): List<MediaItem>` = in ONE transaction select the rows AND set them to RUNNING_FAST (from PENDING) or RUNNING_SMART (from NEEDS_SMART). Two calls NEVER return the same row.
- New: `Repo.releaseRunning(ctx)` = RUNNING_FAST -> PENDING and RUNNING_SMART -> NEEDS_SMART. Call it when the service starts, when it stops, and when the app starts (so a killed scan resumes cleanly).
- `saveResults` sets the final status as before.
- `Repo.counts` must treat RUNNING_* as "in progress" and the SUM of all fields must equal total (add `running` to `Counts`).
- `Repo.page(Filter)` must support a filter "processed only, newest first by scanned_at".

### A2. Engine: stop, stages, videos, workers (Agent "Engine")
- STOP: a `@Volatile stopRequested` flag in `ScanController`. Decoders, workers and the writer check it in every loop. When it is set: stop taking new work, finish the current file, flush the sink, call `Repo.releaseRunning`, set `state.running=false`, stop the foreground service. It must really stop within 2 seconds. The notification Stop button does the same. The UI shows "Stopped".
- PROGRESS: `ScanState.done` and `ScanState.total` refer to the CURRENT STAGE. At the start of each stage take a snapshot: `total` = number of rows claimed for that stage so far plus those still waiting; `done` = number finished in THIS stage. Reset both when the stage changes. ALWAYS `done = min(done, total)`. Show the stage name ("Fast pass", "Smart pass") and the numbers. No more `5675/3037`.
- VIDEOS in stage 1 and 2: use `VideoAnalyzer.forEachFrame` + frameItems + `VideoVote` (Phase 3 behavior). Never run ImageDecoder on a video file. If a video fails, status ERROR with a clear note (never "decode failed" without the reason).
- Producer/consumer pipeline (REALLY implement, as in docs/PHASE4.md section 6): 3 decoder threads -> ArrayBlockingQueue(16) -> ONE worker thread per selected processor (each creates its own Classifier inside its own thread; the GPU delegate must be created and used on the same thread) -> `ResultSink` (ONE writer thread, flush every 100 results or 500 ms).
- Per-backend speed: keep per-backend counters and ms per file and expose them in `ScanState` so the Scan screen can show "NPU: n files, x ms/file" again.
- If a backend fails to start: `AppLog.e` and skip it; if ALL fail, use CPU.
- Log with AppLog: stage start/end, files per second, every ERROR with the reason.

### A3. Screens and wiring
- Scan screen list = latest PROCESSED files (use the new filter), newest first, max 50. Plus a line "Waiting: N".
- The counters on the Scan screen come from `ScanState` (stage numbers) AND `Repo.counts` (ready to sort, needs review, done, error, waiting). They must agree.
- Review screen: paged (`Repo.page(Filter(REVIEW), offset, 100)` + "load more" at the end of the list).
- History: real "Change category" dialog (text field + OK) calling `vm.changeCategory`.
- Stop button: calls `ScanController.stop`, shows "Stopping..." then "Stopped".

### A4. Required tests for Part A (names are mandatory)
- `RepoClaimTest`: two claims in a row never return the same id; after `releaseRunning` the rows are claimable again.
- `RepoCountsTest`: the sum of all fields equals total after any mix of statuses.
- `ProgressMathTest`: done never exceeds total; the stage change resets the counters.
- `StopTest` (emulator/instrumented, see section 4): start a scan of 500 images, call stop after 1 s, assert `running=false` within 3 s and no row stays RUNNING_*.
- `ScanEndToEndTest` (emulator/instrumented): 60 generated images (some 4000x3000) + 1 tiny mp4 if possible; all rows leave PENDING; counts add up; `done <= total` at every sample; no crash.
- `NoDuplicateProcessingTest`: every media id is processed exactly once in a normal scan.

Publish `phase4-cp1c` ONLY if all required tests pass in CI. Send me the link. Then go on to CP2 (I do not want to wait).

---

## 4. TEST INFRASTRUCTURE (set it up FIRST, one Trio "QA")

1. JVM unit tests in `app/src/test`: pure logic (Decision, Emb, StarterRules, ScreenshotTyper, Suggest, VideoVote, ProgressMath, ClipTokenizer sanity vector). Run with `./gradlew testDebugUnitTest`.
2. Repo tests: use Robolectric or an in-memory SQLite approach so `Repo` (claim, counts, paging, history, FTS4 search) is tested on the JVM.
3. End-to-end tests in `app/src/androidTest`, run in GitHub Actions on an Android emulator (use `reactivecircus/android-emulator-runner`, API 34, x86_64).
   - Add a Gradle property `-PciAbi=x86_64` that ADDS "x86_64" to `abiFilters` (normal builds stay arm64-v8a only). TFLite and ONNX Runtime both have x86_64 libraries.
   - The emulator has no NPU. It tests the CPU path only. GPU/NNAPI failures must be caught and logged, never crash.
   - Tests create their own images in the app cache directory, so they need no storage permission. Grant POST_NOTIFICATIONS with a GrantPermissionRule.
   - The models are downloaded in CI by `scripts/fetch_models.sh` (already in the workflow).
   - If the emulator job fails 3 times for ENVIRONMENT reasons (not code bugs), tell me, keep the JVM and Repo tests, and continue.
4. CI rule: the workflow runs the tests BEFORE building the release APK. If a required test fails, no APK is published.
5. Every report lists the test names with pass/fail and the CI run link.

---

## 5. PART B - CP2 (publish as `phase4-cp2`)

CP2 = OCR + TAGS + STARTER CATEGORIES + SEARCH + SETTINGS, exactly as in `docs/PHASE4.md` section 7 (agents E "Vision", F "Search", G "Settings", and B-small for the pipeline hooks).
Every one of those modules uses the TRIO protocol. Extra requirements:

- Agent E (ML Kit): the Reviewer MUST verify from the official ML Kit docs: the exact Gradle artifact names (BUNDLED, not play-services), `TextRecognition.getClient`, `DevanagariTextRecognizerOptions`, `InputImage.fromBitmap`, `Tasks.await` rules (never on the main thread), how to close recognizers, and the ImageLabeler options. Put the doc links in `docs/facts/vision.md`.
- Tests for E (JVM): `ScreenshotTyperTest` (at least 12 sample OCR texts: payments with ₹/UPI, WhatsApp chat, Instagram, Google Maps directions, shopping cart, Gmail inbox, code, random text -> "Other"), `StarterRulesTest` (at least 15 cases: dental report text -> Teeth; "chest x-ray" text -> X-ray; dental X-ray -> label Teeth + extra X-ray; screenshot by file name; screenshot by aspect ratio + text; person tags -> Humans; dog tags -> Animals; Humans win over Animals).
- Tests for F (Repo/JVM): FTS4 search with prefix queries, ranking merge (Reciprocal Rank Fusion) with fake data, empty query, special characters in the query (quotes, *, -) must not crash.
- Tests for G: settings save/load round trip for every new field (Prefs).
- E2E (emulator): a tiny scan with `ocrMode=all` on a generated image that contains drawn text; assert OCR text is stored and `Repo.ftsSearch` finds it (best effort: if ML Kit does not run on the x86_64 emulator, say so and skip this one test only).
- Everything from the CP1c list must keep passing.

CP2 acceptance on my phone (I will test): screenshots land in `Screenshots/<Type>`; dental and X-ray photos go to Teeth / X-ray; search finds words inside images and by meaning; Hindi text is searchable; Settings are remembered.

---

## 6. ORDER OF WORK
1. Save this file. Tell me what you understood (3 sentences).
2. QA Trio: test infrastructure (section 4).
3. Part A Trios (A1, A2, A3 in parallel where files do not clash; A4 tests first for bugs).
4. CI green -> publish `phase4-cp1c`, send me the link + report.
5. Part B Trios (CP2). CI green -> publish `phase4-cp2`, send me the link + report.
6. Then WAIT for my phone test. Do NOT start CP3.

If you cannot run parallel agents, do them one by one but KEEP the three roles separate (a different agent for Builder / Reviewer / Tester each time).

## 7. WHAT THE REVIEWER MUST ALWAYS CHECK (the checklist)
- No ML, database, file or network work on the main thread.
- No Bitmap lists; bitmaps recycled; OutOfMemoryError caught per file.
- No database write per file (only through ResultSink); one writer thread.
- No UI update per file; the UI reads from the database or `ScanState` (at most every 300 ms).
- No row is processed twice (claim works). No row stays RUNNING_* after stop or crash.
- `done <= total` everywhere. The Stop button works within 2 seconds.
- Videos never go through ImageDecoder.
- Every external API in the code appears in a facts sheet with a real source.
- Copy stays the default. No INTERNET permission yet (CP3 only).

## 8. RULES
1. Never delete my photos or files. Copy stays the default.
2. Do not say "done" unless the build AND the required tests passed. Show proof (CI link).
3. Be honest about failures and speed. Report real numbers from logs.
4. Short messages. Simple words.

## 9. REPORT FORMAT (after cp1c, and after cp2)
```
Checkpoint: cp1c / cp2
Build: PASS or FAIL        CI run: (link)
Tests: (every test name -> pass/fail)
Trio log per module: gate result (GO / LEARN x rounds / STRIKES / REPLACED?), reviewer PASS/FAIL loops
Bugs fixed (cp1c only): for each of my 6 bugs -> fixed / not fixed + the test that proves it
Speed numbers: files/sec in Fast mode (from logs), per-backend ms/file
Self-check list: each item ok / not ok, with evidence
APK: (GitHub pre-release link) and size
Problems left: (list or "none")
```