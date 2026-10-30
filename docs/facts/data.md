# facts: data (Repo)

## APIs used
- android.database.sqlite.SQLiteDatabase (rawQuery, beginTransaction, setTransactionSuccessful, endTransaction, update, execSQL) — Android SDK
- android.content.ContentValues — Android SDK
- Repo.claim(ctx, status, limit): List<MediaItem> — new; single transaction SELECT + UPDATE to RUNNING_FAST/RUNNING_SMART
- Repo.releaseRunning(ctx) — new; UPDATE media SET status='PENDING'/'NEEDS_SMART'
- Repo.counts(ctx): Counts — updated to include running

## Risks
- Race between two claim calls mitigated by single transaction + SQLite locking.
- releaseRunning on app start ensures crash recovery.

## Plan
1. Add RUNNING_FAST/RUNNING_SMART to Status enum (TEXT, no schema change).
2. Implement claim in one transaction.
3. Implement releaseRunning.
4. Update counts to include running.
5. Add processedOnly filter to Filter.