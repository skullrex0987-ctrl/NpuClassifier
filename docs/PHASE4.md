# PHASE 4 - PROMPT FOR RIKA (read ALL of it before you start)

Hi Rika! Big job. You are the ORCHESTRATOR (team boss) again.
You make sub-agents (helpers), give each ONE small job, check their work, merge, build, and publish APKs.
Work slowly. ONE step at a time. Never guess. If you are not sure, ask me ONE simple question.

FIRST:
1. `git pull`
2. Save this whole file into the repo as `docs/PHASE4.md` and commit it.
3. Read `docs/SPEC.md` and `AGENTS.md`.
4. Tell me in 3 short sentences what you understood. Then start Checkpoint 1.

---

## 1. WHAT I WANT (simple words)

Phase 3 problems I saw: CLIP is slow, sometimes gives wrong answers, and the app FREEZES. Fix that FIRST.

New wishes:
- A "fastest" sorting mode, plus a smarter automatic two-stage mode.
- Sort into: Humans, Animals, Teeth, X-ray, Screenshots (more categories later). The vision/OCR tools should also find other things and save them as tags.
- OCR for English + Hindi (offline) so I can SEARCH text inside images and also use it to sort.
- One search box: finds words inside images AND lets me describe a picture ("dental x-ray").
- A History tab: everything the app sorted is saved in a database (kept until I clear it).
- Add EmbeddingGemma 2 (second smart model, switchable with CLIP).
- Optional face grouping by person (OFF by default).
- Everything runs on my phone (Poco X6 Pro, 8 GB RAM, Android 14, arm64). Nothing leaves the phone.

## 2. DECISIONS ALREADY MADE (do not change)

- Scan modes (user chooses in Settings): `fast` = MobileNet only (fastest) / `auto` = two stages: MobileNet first, smart model ONLY for photos MobileNet was unsure about (DEFAULT) / `smart` = smart model for everything.
- Smart model (user chooses): `clip` or `gemma` (EmbeddingGemma 2).
- Scans run in the BACKGROUND (foreground service with a notification), anytime. Target: whole folder (4,000+ files) with the smart model in under 1 hour. Fast mode must be MUCH faster.
- OCR scripts: English (Latin) + Hindi (Devanagari). ML Kit BUNDLED models (offline). OCR timing is the user's choice: `off` / `all` (every image) / `docs` (only screenshots and documents, DEFAULT) / `search` (only when I search).
- Hindi OCR: `auto` = run English first, run Hindi only if little text was found (DEFAULT); or `always`; or `off`.
- Tags on every image: ML Kit bundled Image Labeling + OCR keywords (+ CLIP labels when available). Max 10 tags per image.
- Search covers sorted files AND Review files (not files I never scanned). Semantic search model switch: `clip` or `gemma`.
- Categories: Humans, Animals (ONE folder, species only as tags), Teeth (close-up photos + dental X-rays + dental reports), X-ray (ANY X-ray or scan), Screenshots (split into subfolders by type using OCR, example `Screenshots/Payments`).
- A dental X-ray belongs to BOTH Teeth and X-ray: COPY it into both folders (in Move mode: move to the first, copy to the others).
- Medical photos go in normal folders (no hiding, no lock).
- Copy stays the default action.
- History: forever until I clear it. Actions: open file, show in folder, undo one, undo whole batch, change category, export CSV. If a sorted file is missing later: ASK me (Remove / Keep / Decide one by one).
- Tabs (bottom bar, 5 slots): Scan, Review, Search, History, More. "More" opens: Categories, People, Settings, Diagnostics, Background setup guide.
- Keep CLIP (about 154 MB bundled, as in Phase 3).
- Big vision model for handwriting: NOT NOW. Only add a greyed-out Settings line "Big vision model (handwriting): coming later".
- INTERNET permission is allowed, but ONLY used when I tap "Download model" (EmbeddingGemma 2). No other network use.
- Free storage for models: assume 3 GB max. Always show the download size and ask me to confirm before downloading.

## 3. THREE CHECKPOINTS (build and publish an APK after EACH one)

- CP1 = STABILITY + DATABASE + HISTORY + FASTEST MODE. (Fixes the freeze.)
- CP2 = OCR + TAGS + STARTER CATEGORIES + SEARCH + new SETTINGS.
- CP3 = EMBEDDINGGEMMA 2 + FACE GROUPING.

After each checkpoint: build, make a GitHub pre-release (tags `phase4-cp1`, `phase4-cp2`, `phase4-cp3`) with the APK attached, send me the link + the report. Then CONTINUE to the next checkpoint unless I say STOP.
If a checkpoint fails to build 5 times in a row on the same error: STOP and show me the exact error.

---

## 4. SHARED CONTRACTS (Step 0 of CP1: YOU do this ALONE, make it compile, commit "phase4 contracts")

Use plain Android SQLite (`SQLiteOpenHelper`) with FTS4. Do NOT use Room, kapt or KSP (too risky for the build).

### 4.1 Models.kt changes
- `MediaItem`: add `val id: Long = 0`
- `Status` enum: add `NEEDS_SMART` and `MISSING`
- `SortItem`: add
  `val id: Long = 0`, `val extraLabels: List<String> = emptyList()`, `val tags: List<String> = emptyList()`, `val ocr: String = ""`, `val thumb: String? = null`, `val destPath: String? = null`
- A label may contain ONE "/" meaning a subfolder (example `Screenshots/Payments`). `FileOps.safeName` must clean each part separately and keep the "/".
- `FileOps.place` must also copy into every folder in `extraLabels` (Move mode: move to the first, copy to the extras). Return a list of `Op`.
- `SortSettings` add:
  `scanMode: String = "auto"` ("fast" | "auto" | "smart")
  `ocrMode: String = "docs"` ("off" | "all" | "docs" | "search")
  `ocrHindi: String = "auto"` ("off" | "auto" | "always")
  `tagsOn: Boolean = true`
  `searchModel: String = "clip"` ("clip" | "gemma")
  `facesOn: Boolean = false`
  `faceMatch: Float = 0.65f`
  `indexWhileCharging: Boolean = true`
  and `model` now means the SMART-stage model: "clip" or "gemma" (default "clip").
  Update `Prefs.kt` to save/load all of these.
- Add `data class Counts(val total: Int, val pending: Int, val needsSmart: Int, val auto: Int, val review: Int, val done: Int, val error: Int)`
- Add `data class Filter(val statuses: Set<Status> = emptySet(), val label: String? = null, val latestFirst: Boolean = true)`
- Add `data class HistoryRow(val id: Long, val batchId: Long, val ts: Long, val srcPath: String?, val destPath: String, val action: String, val label: String, val conf: Float, val source: String, val thumb: String?, val ocrSnippet: String, val tags: String, val status: String)`  (status = ok | undone | missing)

### 4.2 Db.kt + Repo (Agent A owns; you create stubs)
`object Db { fun get(ctx: Context): SQLiteDatabase }`  (SQLiteOpenHelper, WAL mode on)

Tables (create ALL of them now, even the ones used later):
```
media(id INTEGER PRIMARY KEY AUTOINCREMENT, key TEXT UNIQUE NOT NULL, path TEXT, uri TEXT NOT NULL, name TEXT NOT NULL,
      is_video INTEGER, size INTEGER, mtime INTEGER, w INTEGER, h INTEGER, status TEXT NOT NULL, label TEXT, extra_labels TEXT,
      source TEXT, conf REAL, gap REAL, top_json TEXT, backend TEXT, ms INTEGER, note TEXT, ocr TEXT, tags TEXT, thumb TEXT,
      dest_path TEXT, batch_id INTEGER, scanned_at INTEGER, emb_mnet BLOB, emb_clip BLOB, emb_gemma BLOB, frames_json TEXT)
  + indexes on status and label
media_fts  = FTS4 table with columns (name, ocr, tags, label); docid = media.id; Repo keeps it in sync
history(id INTEGER PRIMARY KEY AUTOINCREMENT, batch_id INTEGER, ts INTEGER, media_id INTEGER, src_path TEXT, dest_path TEXT,
        action TEXT, label TEXT, conf REAL, source TEXT, thumb TEXT, ocr_snippet TEXT, tags TEXT, status TEXT)
faces(id INTEGER PRIMARY KEY AUTOINCREMENT, media_id INTEGER, x INTEGER, y INTEGER, w INTEGER, h INTEGER, emb BLOB, person_id INTEGER)
people(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT)
```
```kotlin
object Repo {
  fun upsertPending(ctx: Context, items: List<MediaItem>, batchId: Long): List<MediaItem>  // inserts NEW files only (by key = path or uri), returns them WITH ids
  fun saveResults(ctx: Context, results: List<SortItem>)       // ONE transaction for the whole list (UPDATE + fts)
  fun nextPending(ctx: Context, status: Status, limit: Int): List<MediaItem>   // status PENDING or NEEDS_SMART
  fun counts(ctx: Context): Counts
  fun page(ctx: Context, f: Filter, offset: Int, limit: Int): List<SortItem>
  fun get(ctx: Context, id: Long): SortItem?
  fun resetForModelChange(ctx: Context)                        // results back to PENDING, keep embeddings of other models
  fun addHistory(ctx: Context, batchId: Long, item: SortItem, ops: List<Op>, action: String)
  fun historyPage(ctx: Context, offset: Int, limit: Int): List<HistoryRow>
  fun markHistory(ctx: Context, id: Long, status: String)
  fun clearHistory(ctx: Context)
  fun exportHistoryCsv(ctx: Context, out: java.io.OutputStream)
  fun ftsSearch(ctx: Context, query: String, limit: Int): List<Long>           // ids, best first
  fun embeddings(ctx: Context, model: String): List<Pair<Long, FloatArray>>    // model = mnet|clip|gemma
  fun saveEmbedding(ctx: Context, id: Long, model: String, emb: FloatArray)
  fun saveEnrichment(ctx: Context, id: Long, ocr: String, tags: List<String>)
  fun countMissingEmb(ctx: Context, model: String): Int
}
object Thumbs { fun save(ctx: Context, id: Long, bmp: Bitmap): String?  /* 256px JPEG q70 in filesDir/thumbs/<id>.jpg */ }
```

### 4.3 Scan service (Agent B owns)
```kotlin
data class ScanState(val running: Boolean, val stage: String /* idle|fast|smart|ocr|index */, val done: Int, val total: Int, val perSec: Float, val message: String?)
object ScanController {
  val state: StateFlow<ScanState>
  fun start(ctx: Context, cfg: SortSettings)       // starts ScanService (foreground)
  fun stop(ctx: Context)
  fun startIndex(ctx: Context, cfg: SortSettings)  // builds embeddings for search (items missing cfg.searchModel)
}
class ScanService : Service()   // foreground type dataSync
object AppLog { fun d(tag: String, msg: String); fun e(tag: String, msg: String, t: Throwable? = null); fun tail(n: Int): List<String> }
```

### 4.4 Enrichment (Agent E owns)
```kotlin
data class Enrichment(val ocr: String, val tags: List<String>)
object Enricher { fun run(ctx: Context, m: MediaItem, cfg: SortSettings, wantOcr: Boolean, wantTags: Boolean): Enrichment }  // BLOCKING, call from worker thread
data class StarterMatch(val label: String, val extra: List<String>)
object StarterRules { fun match(name: String, path: String?, ocr: String, tags: List<String>, w: Int, h: Int, smartProbs: Map<String, Float>?): StarterMatch? }
```

### 4.5 Search (Agent F owns)
```kotlin
object SearchEngine {
  fun search(ctx: Context, query: String, cfg: SortSettings, f: Filter): List<SortItem>   // BLOCKING
  fun indexStatus(ctx: Context, cfg: SortSettings): Pair<Int, Int>   // (indexed, total)
}
```

### 4.6 Gemma + Faces (CP3)
```kotlin
class GemmaEmbedder(ctx: Context) : Embedder          // image + text embeddings (same Embedder interface as CLIP)
object ModelDownloader { fun status(ctx: Context): String; fun importFrom(ctx: Context, uri: Uri); fun download(ctx: Context, onProgress: (Int) -> Unit) }
data class FaceHit(val x: Int, val y: Int, val w: Int, val h: Int, val emb: FloatArray)
object FaceEngine { fun detectAndEmbed(ctx: Context, bmp: Bitmap): List<FaceHit> }
```

### 4.7 SortEngine contract (Agent B owns)
Keep the old functions that still make sense. Replace the old `run(...)` with:
`fun runQueue(ctx: Context, cfg: SortSettings, cats: List<Category>)` (blocking; called by the service).

STUB EVERYTHING above so the project compiles. Commit "phase4 contracts".

---

## 5. RULES FOR EVERY SUB-AGENT
- Edit ONLY your own files. Never change a Step 0 signature. Keep Phase 1, 2, 3 features working.
- All heavy work (decode, ML, database, files) on background threads. NEVER on the main thread.
- Never hold lists of Bitmaps. Recycle bitmaps after use. Catch OutOfMemoryError per file (mark that file ERROR, continue).
- Write short comments. Log important events with `AppLog`.

---

## 6. CHECKPOINT 1: STABILITY + DATABASE + HISTORY + FASTEST MODE

### Why the app froze (my best guess)
- The screen kept thousands of items in memory and redrew on EVERY file.
- CLIP loaded its big model once per worker.
- Everything ran inside the screen's own scope, with big bitmaps.

### Agent A "Data" - owns Db.kt, Repo.kt, Thumbs.kt
- Implement everything in 4.2. `saveResults` and `upsertPending` use ONE transaction per call. WAL mode on.
- `page()` uses LIMIT/OFFSET. `counts()` is ONE query (GROUP BY status).
- `exportHistoryCsv`: header row + one line per history row, CSV-escaped.
- `Thumbs.save` writes a 256 px JPEG (quality 70).

### Agent B "Engine" - owns SortEngine.kt, ScanService.kt, ScanController.kt, AppLog.kt, AndroidManifest.xml (service, permissions, largeHeap)
- Manifest: permissions `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`; `<service android:name=".ScanService" android:foregroundServiceType="dataSync" android:exported="false"/>`; `android:largeHeap="true"`. (INTERNET comes in CP3.)
- `ScanService`: foreground notification "Sorting 123/4000" with a Stop button. Runs `SortEngine.runQueue`. The queue = rows in the database (status PENDING / NEEDS_SMART). So after a crash or app kill the scan RESUMES.
- Pipeline:
  - Stage 1 (modes fast and auto): take PENDING files, decode small (use `setTargetSampleSize`, biggest power of 2 keeping min side >= 256), MobileNet scores, `emb_mnet`, decide with the Phase 2 rules + `StarterRules` (cheap cues only). `fast`: unsure -> REVIEW. `auto`: unsure -> `NEEDS_SMART`. `smart`: skip stage 1, every file starts as NEEDS_SMART.
  - Stage 2: take NEEDS_SMART files, ONE shared smart-embedder session (CLIP: ONNX Runtime with 4 threads; do NOT create one session per worker), save `emb_clip` (or `emb_gemma`), decide with the Phase 3 rules; still unsure -> REVIEW.
  - Parallel decode: a pool of 3 decoder threads feeding the model workers through a small ArrayBlockingQueue(16). MobileNet workers per selected processor (CPU/GPU/NPU) exactly as in Phase 1-3.
  - Save a thumbnail for each file (`Thumbs.save`) while you already have the bitmap.
  - RESULTS GO THROUGH A SINK: workers put results in a queue; ONE writer thread calls `Repo.saveResults` for every 100 results OR every 500 ms. Never one DB write per file, never one UI update per file.
  - `ScanController.state` is updated at most every 300 ms (done, total, files/sec).
  - Videos: same as Phase 3 (frames evenly across the whole video, vote).
  - Enrichment hook: if `cfg.ocrMode != "off"/"search"` or `tagsOn`, call `Enricher.run` (CP2). For now a no-op stub.
- `AppLog`: ring buffer (last 500 lines) + file `filesDir/logs/app.log` (max 1 MB, rotate). Install an uncaught-exception handler that writes the crash to the log.
- In DEBUG builds enable `StrictMode` (log disk/network on main thread).

### Agent C "Screens" - owns Ui.kt, HistoryUi.kt, MainActivity.kt
- Bottom bar: Scan, Review, Search (empty placeholder for now: "Coming in the next checkpoint"), History, More. "More" = a simple list: Categories, People (greyed), Settings, Diagnostics, Background setup guide.
- Scan screen: buttons Pick files / Pick folder, processors chips, scan-mode choice (Fastest / Auto / Smart), a progress card from `ScanController.state` + `Repo.counts()` (refresh every 500 ms while scanning), per-backend speed line, Start/Stop, "Apply" (copy/move all AUTO), Undo last batch. Show only the LAST 50 processed items (`Repo.page` newest first). NEVER show a list of all files.
- Request notification permission (Android 13+) the first time the user starts a scan.
- Review screen: `Repo.page(Filter(statuses = setOf(Status.REVIEW)), offset, 100)` with "load more" when scrolling to the end.
- History screen: grouped by day, thumbnail (from `thumb`), label, confidence, source (mine/builtin/starter), destination. Per row actions: Open (FileProvider + ACTION_VIEW), Show in folder (try a DocumentsContract directory intent; fallback: show/copy the path), Undo this one, Change category (dialog with a name field -> VM). Per batch: Undo batch. Top menu: Export CSV (CreateDocument), Clear history (confirm).
- On opening History, check that each visible destination file exists. If some are missing: ONE dialog: "N files are missing" with buttons Remove all / Keep all / Decide one by one.
- Keep AMOLED black. Keep chips clearly visible.

### Agent D "Wiring" - owns SortViewModel.kt
- Remove the big in-memory `items` list. Everything reads from `Repo`.
- `loadUris` / `loadFolder`: build MediaItems, `Repo.upsertPending(batchId = now)`, then `ScanController.start`.
- `applyAuto()`: for AUTO rows (paged, 200 at a time) call `FileOps.place`, `Repo.addHistory`, mark DONE, `destPath`. One batchId per Apply.
- `assign` (Review): place + history + learn (Phase 2 behavior) + mark DONE.
- `undoBatch(batchId)`, `undoOne(historyId)`, `changeCategory(historyId, newLabel)` (moves/copies the destination file to the new folder, updates the row and history, learns), `resolveMissing(...)`, `exportCsv(uri)`, `clearHistory()`.
- When settings change (thresholds), re-decide REVIEW/AUTO rows in pages of 500 in the background.

### CP1 self-check (you, Rika, must verify in the code before building)
- No `items.update` per file anywhere. No full-list `LazyColumn`. No DB write per file.
- No ML or DB or file work on the main thread.
- Only ONE CLIP session. Only ONE writer thread for the DB.

### CP1 acceptance (my tests on the phone)
1. Scan my 4,000+ folder in Fast mode: no freeze, notification shows progress, files/sec reported (report it!).
2. Kill the app in the middle, open it again, press Scan: it RESUMES (does not start over).
3. History survives closing the app. Undo one, Undo batch, Export CSV all work.
4. Auto mode: the smart model only runs on the unsure files.

---

## 7. CHECKPOINT 2: OCR + TAGS + STARTER CATEGORIES + SEARCH + SETTINGS

### Agent E "Vision" - owns Enricher.kt, OcrEngine.kt, TagEngine.kt, ScreenshotTyper.kt, StarterRules.kt, and the ML Kit lines in app/build.gradle.kts
- Dependencies (BUNDLED, offline; pick the latest stable versions that are at least 2 weeks old): `com.google.mlkit:text-recognition`, `com.google.mlkit:text-recognition-devanagari`, `com.google.mlkit:image-labeling`, and (CP3) `com.google.mlkit:face-detection`. NOT the `play-services-` versions.
- OcrEngine: decode a bigger bitmap (long side about 1600 px) just for OCR. `TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)` for English; `DevanagariTextRecognizerOptions` for Hindi. Use `Tasks.await(...)` on the worker thread. Hindi mode `auto`: run Hindi only if the English text is shorter than 12 characters or mostly symbols. Merge both texts. Close recognizers when the scan ends.
- TagEngine: ML Kit ImageLabeler (confidence >= 0.6), keep max 10 tags, lowercase.
- Enricher.run: returns OCR text (max 4,000 characters) and tags. OCR runs when `wantOcr`.
  - `docs` mode rule: OCR only if (file name or path contains "screenshot") OR (height/width >= 1.9 or width/height >= 1.9) OR (stage-1 label or tags contain one of: menu, web site, comic book, envelope, book jacket, document, text, paper, poster, receipt, screenshot).
- ScreenshotTyper: from lowercase OCR text, count keyword hits per type; best type wins with >= 2 hits, else "Other":
  - Payments: ₹, upi, paid, payment, transaction, gpay, google pay, phonepe, paytm, bank, debited, credited, txn, amount
  - Chat: whatsapp, telegram, signal, typing, delivered, seen, online, last seen, reply, message
  - Social: instagram, facebook, twitter, reels, followers, following, likes, retweet, story, tiktok, youtube, subscribe, comments
  - Maps: google maps, directions, route, navigate, traffic, km, min
  - Shopping: add to cart, buy now, order, delivery, amazon, flipkart, cart, price
  - Email: inbox, compose, subject, gmail
  - Code: def, function, import, class, error, exception, http
  - Result label = `Screenshots/<Type>` (example `Screenshots/Payments`).
- StarterRules.match (cheap deterministic rules; `smartProbs` is optional and holds the smart model's zero-shot probabilities for these prompts: "a screenshot of a phone screen", "an X-ray image", "a radiograph", "an MRI scan", "a CT scan", "a dental X-ray", "a close-up photo of teeth", "a dental report document", "a photo of a person", "a photo of an animal"):
  - Teeth if: OCR has dental, dentist, tooth, teeth, orthodont, root canal, cavity, scaling, braces, endodont, periodont, opg, rvg; OR smartProbs for dental/teeth prompts >= 0.5.
  - X-ray if: OCR has x-ray, xray, radiolog, radiograph, mri, ct scan, ultrasound; OR smartProbs for X-ray/radiograph/MRI/CT prompts >= 0.5.
  - If BOTH signals: label "Teeth", extra ["X-ray"].
  - Screenshots if: name/path has "screenshot" OR (aspect ratio >= 1.9 AND OCR text length > 20); label = ScreenshotTyper result.
  - Humans if tags contain person, selfie, face, smile, crowd OR smartProbs "a photo of a person" >= 0.5.
  - Animals if tags contain dog, cat, bird, animal, pet, horse, cow, fish, wildlife OR smartProbs "a photo of an animal" >= 0.5. (Humans win over Animals when both match.)
  - The animal species (and everything else found) stays in `tags`.
- Priority in Decision (Agent B updates Decision.kt in CP2): my example-category rule > StarterRules > smart text rule > MobileNet builtin > Review.

### Agent F "Search" - owns SearchEngine.kt, SearchUi.kt
- `search`: (1) `Repo.ftsSearch` on name/ocr/tags/label (FTS4 MATCH with prefix `word*`). (2) semantic: embed the query text with the model `cfg.searchModel` (CLIP or Gemma text side), cosine against `Repo.embeddings(model)`, take the top 50. (3) Merge both rankings with Reciprocal Rank Fusion (score = sum of 1/(60 + rank)). Return SortItems with a `note` such as "text", "meaning", or "text + meaning".
- If the model has no embeddings yet: show "N of M images indexed" with a "Build index" button -> `ScanController.startIndex`. Setting `indexWhileCharging`: indexing continues only while charging (check BatteryManager).
- Search tab: one search box, filter chips (category, photo/video, date range), results grid with thumbnails and a small badge (text / meaning / tag), tap -> full-screen viewer showing the image, OCR text, tags, label, and actions (Open, Show in folder, Change category). In `ocrMode = "search"`: a button "Read text now" runs OCR on files that have none (with progress).
- Never run search work on the main thread. Debounce typing (400 ms).

### Agent G "Settings" - owns SettingsUi.kt, DiagnosticsUi.kt, SetupGuideUi.kt
- Settings sections (cards with a short help text under every control):
  - Scan: scan mode (Fastest / Auto / Smart), smart model (CLIP / EmbeddingGemma 2 - Gemma greyed until CP3), processors chips, "Fastest preset" button (fast mode + OCR off + tags off + all 3 processors).
  - OCR and tags: ocrMode (Off / Every image / Screenshots and documents / Only when I search), Hindi mode (Off / Auto / Always), tags switch.
  - Search: search model (CLIP / Gemma), "Index while charging" switch, "Build index now".
  - Everything that Phase 3 settings had (presets, thresholds, videos, folders, data, about).
  - "Big vision model (handwriting): coming later" greyed.
- Diagnostics screen: last 300 lines of `AppLog.tail`, buttons Copy / Share (text) and "Clear log"; at start, if the last run crashed, show a dialog "The app crashed last time. View log?".
- Background setup guide screen (Xiaomi / HyperOS kills background apps): numbered steps with buttons: (1) ask to ignore battery optimization (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`), (2) open Xiaomi autostart (try component `com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity`, fallback = app details settings), (3) tell me to set Battery saver to "No restrictions" and lock the app in Recents.

### Agent B again (small): wire `Enricher`/`StarterRules` into the pipeline and `Decision.kt` priority; extend `FileOps` for subfolders and extraLabels (if you, Rika, did not do it in Step 0).

### CP2 acceptance (my tests)
1. Scan a folder with screenshots: they land in `Screenshots/Payments`, `Screenshots/Chat`...
2. A dental report and an X-ray photo land in Teeth / X-ray (dental X-ray in both).
3. Search "invoice" (text inside an image) and "dental x-ray" (meaning) both find results. Hindi text in a screenshot is searchable.
4. Settings: all new controls work and are remembered.

---

## 8. CHECKPOINT 3: EMBEDDINGGEMMA 2 + FACE GROUPING

### Agent H "Gemma" - owns GemmaEmbedder.kt, ModelDownloader.kt (and INTERNET permission in the manifest)
- EmbeddingGemma 2 is a NEW multimodal embedding model from Google (text, images, video, audio into one space), runs with LiteRT / LiteRT-LM on CPU and GPU. It is NEW (Oct 2026). DO NOT GUESS the API.
  1. Web-search for the official Google AI Edge / LiteRT-LM Android docs and the model page (Hugging Face `google/embeddinggemma-2` and the LiteRT community conversion). Find the exact model FILE for Android, its SIZE, its LICENSE, and the official Kotlin/Java example.
  2. If the Android API does NOT support image or text EMBEDDINGS yet, STOP. Tell me honestly. Ship CP3 without Gemma (leave it greyed "not available yet") and continue with faces.
  3. If it works: implement `GemmaEmbedder` (image + text, L2-normalized, use the embedding size the model gives).
- ModelDownloader: model file lives in `filesDir/models/`. `importFrom` (file picker, copies the file) and `download` (INTERNET permission, resumable, progress callback). BEFORE downloading, show me the size in a confirm dialog. INTERNET is used ONLY here.
- Settings: Gemma entry shows status (not installed / installed + size), Import / Download buttons, delete button.
- Report: ms per image for CPU and GPU on 20 photos, honestly.

### Agent I "Faces" - owns FaceEngine.kt, PeopleUi.kt, FaceStore.kt
- OFF by default (`facesOn`). A clear warning text in Settings: "Face grouping stores face fingerprints on this phone only."
- Detection: ML Kit Face Detection (bundled). Embedding: a small face-embedding TFLite model (MobileFaceNet or similar). You must find one with a PERMISSIVE license (Apache-2.0 / MIT). Check the license text. Tell me which model and source you picked BEFORE using it. If you cannot verify a permissive license, STOP and ask me.
- Crop each face with a margin, resize (usually 112x112), embed, L2-normalize. Store in `faces` (Repo tables already exist). Group greedily: same person if cosine >= `faceMatch` (default 0.65, slider 0.5-0.9).
- People screen (inside More): grid of person clusters (sample face thumbnail + photo count). I can rename a person, merge two people, remove a wrong photo. Search also matches person names.
- Face work runs only for files that already have a Humans label (or tags with person/face), and only when `facesOn`.

### CP3 acceptance (my tests)
1. If Gemma works: import or download it, switch the smart model and the search model to Gemma, search works.
2. Faces ON: People screen shows groups; I can name them; searching a name finds photos.

---

## 9. YOUR JOBS AS ORCHESTRATOR
- Do Step 0 contracts alone. Then run the agents of each checkpoint in parallel if you can.
- Merge. Fix cross-file compile errors yourself (sub-agents must not touch other files).
- Commit each agent's work separately (so I can undo one part).
- Keep `docs/SPEC.md` updated (Phase 4 contracts + status).
- If you cannot run sub-agents, do the jobs yourself in this order: CP1: A, B, D, C. CP2: E, F, G, B-small. CP3: H, I.

## 10. RULES
1. Never delete my photos or files. Copy stays the default.
2. INTERNET permission only for the Gemma download (CP3). No other network use.
3. Do not say "done" unless the build really passed. Show proof.
4. Be honest about failures AND about speed. Report real numbers.
5. Short messages. Simple words.

## 11. REPORT FORMAT (after EACH checkpoint)
```
Checkpoint: CP1 / CP2 / CP3
Build: PASS or FAIL
Sub-agents: (one line each: done / not done)
Self-check list: (each item ok or not)
Speed numbers: files/sec in Fast mode, CLIP ms per photo, OCR ms per image (if known)
Errors I fixed: (short list)
APK: (GitHub pre-release link) and size
Problems left: (list or "none")
```