# facts: search

## APIs used
- SQLite FTS4: CREATE VIRTUAL TABLE media_fts USING fts4(name, ocr, tags, label) — Repo.kt
- Repo.ftsSearch(ctx, query, limit): List<Long> — prefix queries with * suffix per token
- Repo.embeddings(ctx, model): List<Pair<Long, FloatArray>> — for semantic search
- Emb.cosine(a, b): Float — cosine similarity
- Reciprocal Rank Fusion: merge FTS ranks and semantic ranks

## Risks
- FTS4 special chars (*, ", -) must not crash → sanitize query (split on whitespace, append *).
- Empty query → return empty list.

## Plan
1. FTS search already implemented; add semantic search in Search screen.
2. Merge with RRF.
3. Show "N of M indexed" state.