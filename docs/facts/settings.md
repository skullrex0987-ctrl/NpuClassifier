# facts: settings

## APIs used
- Prefs(ctx).load(): SortSettings and save(settings) — SharedPreferences
- SortSettings fields: scanMode, model, threshold, minGap, move, destRoot, excludes, videoDepth, backends, catThreshold, catGap, useMine, useBuiltin, ocrMode, tagsOn, searchModel, indexWhileCharging, etc.

## Risks
- New fields must have defaults for backward compat.

## Plan
1. Add Prefs round-trip test for every field.
2. Settings UI for all fields.
3. Fastest preset button.