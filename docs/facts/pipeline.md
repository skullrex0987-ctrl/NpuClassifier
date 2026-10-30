# facts: pipeline

## APIs used
- SortEngine(ctx, enricher) — enricher injectable for tests
- EnricherApi interface — run(ctx, m, cfg, wantOcr, wantTags): Enrichment
- Repo.saveEnrichment(ctx, mediaId, ocr, tags)
- Decision.apply(item, cats, cfg, cands) — priority: mine > StarterRules > text rule > builtin > Review

## Risks
- Enrichment must run on worker thread (not main).
- StarterRules needs ocr text, so enrichment must happen before Decision.apply.

## Plan
1. Make Enricher injectable.
2. Call enricher in SortEngine worker before Decision.apply.
3. Pass extraLabels to FileOps for multi-folder copy.