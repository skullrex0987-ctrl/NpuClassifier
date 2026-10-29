# NPU Sorter - locked spec and roadmap

## Decisions (from the owner)
- Files: scan folders the user picks, with an exclude list. Picked files are also supported.
- Permission: All-files access (MANAGE_EXTERNAL_STORAGE). Sideload build.
- Default action: COPY (originals stay put). Move is optional. Undo log for the last batch.
- Destination: one central folder, default Pictures/Sorted/<Category>. Videos: Sorted/Videos/<Category>.
- Unsure files: collected in a Review tab. Assigning one LEARNS from it (adds it as an example) - Phase 2.
- Two close categories (dog vs beach): send to Review (the "gap" setting).
- Categories: BOTH built-in ImageNet labels AND teach-by-example; after a first scan the app SUGGESTS categories, owner approves.
- Models: THREE - (1) MobileNet (fast, bundled), (2) a CLIP-style model (bundled), (3) EmbeddingGemma 2 (smart, multimodal; import from storage OR in-app download). User picks which one to use.
- Processors: CPU / GPU / NPU toggles, any combination; when several are selected the batch is split across them in parallel.
- Auto-sort of new files: runs in background but ASKS before each sort (notification with Approve) - Phase 5.
- UI: dark AMOLED black, Jetpack Compose, Material 3.

## Phases
1. (THIS DROP) Compose UI, multi-select, folder scan + excludes, CPU/GPU/NPU toggles (parallel split),
   confidence + gap thresholds (live re-decide), video frame voting, Review tab, copy/move into Sorted/,
   folder auto-creation, undo. Categories = ImageNet label of the top guess.
2. Categories tab + teach-by-example (embeddings + nearest example), suggest-categories-after-first-scan,
   learn-from-Review. Needs an EMBEDDING model (MobileNet classifier outputs are not embeddings -> use a
   feature-vector variant). Persist results/categories (Room).
3. CLIP-style model (image + text towers) -> sort by typed names (zero-shot). Model picker in Settings.
4. EmbeddingGemma 2 via LiteRT (.litertlm): import-from-storage + one-time download (adds INTERNET permission
   used ONLY for the download). Check NPU availability on Dimensity 8300; otherwise GPU/CPU.
5. Background auto-sort with approval notification, duplicate finder, CSV export, history screen.

## Known limits / risks
- Embeddings from different models are NOT comparable. Keep example images and re-embed on model switch.
- EmbeddingGemma 2 is very new (Oct 2026); LiteRT integration may need debugging. It will be slower than MobileNet.
- Phase 1 folder names are raw ImageNet labels (e.g. "Tabby"), which is why Phase 2 matters.
- NNAPI is deprecated on Android 15+; NPU may fall back to CPU for unsupported models.
- GPU delegate must run on the thread that created it (handled in SortEngine: one thread per backend).

## Phase 2 contracts (added)

### New models
- SortItem: + emb: FloatArray? = null, source: String = ""
- Example: data class (name, path, emb)
- Category: data class (name, examples)

### New settings
- SortSettings: + catThreshold (0.80), catGap (0.03), useMine (true), useBuiltin (true)

### New objects
- Emb: fromScores (sqrt + L2 normalize), cosine (dot product)
- Decision: matchCategories (best-first list), apply (mine/builtin/review cascade)
- CategoryStore: load/save JSON in filesDir/categories.json
- Suggest: clusters (greedy, centroid = normalized avg, minSize, max 12)

### New signatures
- SortEngine.run(items, cfg, cats, onItem, onBackendError)
- SortEngine.embedFiles(items): List<FloatArray?>
- SortViewModel: categories, suggestions, createCategory, deleteCategory, suggest, approveCluster, dismissCluster

### Speed
- decode() uses setTargetSampleSize (biggest power of 2 keeping min side >= 256)
