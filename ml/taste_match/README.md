# Sync taste-match training

The production API always exposes one `TasteMatchSummary` contract. The current active encoder is
`HYBRID_SIAMESE_V2_BOOTSTRAP`; this directory trains its learned shared-tower successor without
changing Android or API response shapes.

## Pipeline

1. Create an isolated Python environment and install `requirements.txt`.
2. Run `export_dataset.py`. It exports only weighted feature tokens; listening history is included
   only when `listening_insights_enabled=true`.
3. Run `train.py --model-version taste-YYYYMMDD-N`. The last 20% of time-ordered pairs is held out.
4. Review `metrics.json`, subgroup coverage, score calibration, and block false-positive rates.
5. Run `publish_shadow.py`. It can only register `SHADOW`; activation is deliberately not automated.
6. Run `evaluate_shadow.py` against real exposure/feedback rows. It exits non-zero when coverage or
   labeled log-loss misses the configured gate, while the app continues serving the active model.

Example:

```bash
python export_dataset.py --output artifacts/dataset
python train.py --dataset artifacts/dataset --output artifacts/taste-20260715-1 \
  --model-version taste-20260715-1
python publish_shadow.py --artifact artifacts/taste-20260715-1 \
  --artifact-uri s3://sync-models/taste-20260715-1/user_tower.pt
python evaluate_shadow.py --model-version taste-20260715-1
```

Promotion requires a separately reviewed DB transaction that retires the previous active model and
activates the shadow model. Never promote on training accuracy alone: validation weighted log-loss,
pair accuracy, score distribution drift, low-evidence coverage, opt-out deletion, and block-rate
guardrails must all pass. Roll back by restoring the bootstrap model to `ACTIVE`.
