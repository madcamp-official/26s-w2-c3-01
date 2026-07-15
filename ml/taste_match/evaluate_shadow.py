#!/usr/bin/env python3
"""Evaluate a stored SHADOW model against production exposures without serving it."""

from __future__ import annotations

import argparse
import json
import math
import os
from statistics import fmean

import psycopg


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--database-url", default=os.environ.get("DATABASE_URL"))
    parser.add_argument("--model-version")
    parser.add_argument("--days", type=int, default=7)
    parser.add_argument("--min-coverage", type=float, default=0.75)
    parser.add_argument("--max-labeled-log-loss", type=float, default=0.75)
    args = parser.parse_args()
    if not args.database_url:
        raise SystemExit("DATABASE_URL or --database-url is required")
    with psycopg.connect(args.database_url) as connection:
        with connection.cursor() as cursor:
            model_version = args.model_version
            if model_version is None:
                cursor.execute(
                    "select model_version from taste_model_versions where status='SHADOW' order by created_at desc limit 1"
                )
                row = cursor.fetchone()
                if row is None:
                    raise SystemExit("No SHADOW model is registered")
                model_version = row[0]
            cursor.execute(
                "select user_id,embedding from taste_user_embeddings where model_version=%s",
                (model_version,),
            )
            embeddings = {str(user_id): [float(value) for value in embedding] for user_id, embedding in cursor}
            cursor.execute(
                """
                select exposure.viewer_user_id,exposure.target_user_id,exposure.score,feedback.strength
                from taste_match_exposures exposure
                left join lateral (
                  select strength from taste_match_feedback
                  where exposure_id=exposure.id order by created_at desc limit 1
                ) feedback on true
                where exposure.created_at>now()-(%s * interval '1 day')
                """,
                (args.days,),
            )
            exposures = list(cursor)

    evaluated = 0
    score_deltas: list[float] = []
    labeled_losses: list[float] = []
    for viewer_id, target_id, active_score, strength in exposures:
        left = embeddings.get(str(viewer_id))
        right = embeddings.get(str(target_id))
        if left is None or right is None or len(left) != len(right):
            continue
        evaluated += 1
        cosine = max(-1.0, min(1.0, sum(a * b for a, b in zip(left, right))))
        probability = 1.0 / (1.0 + math.exp(-6.0 * cosine))
        shadow_score = round(probability * 100)
        if active_score is not None:
            score_deltas.append(abs(shadow_score - int(active_score)))
        if strength is not None:
            target = (max(-1.0, min(1.0, float(strength))) + 1.0) / 2.0
            probability = min(max(probability, 1e-6), 1.0 - 1e-6)
            labeled_losses.append(-(target * math.log(probability) + (1.0 - target) * math.log(1.0 - probability)))
    report = {
        "model_version": model_version,
        "exposures": len(exposures),
        "evaluated": evaluated,
        "coverage": evaluated / len(exposures) if exposures else 0.0,
        "mean_absolute_score_drift": fmean(score_deltas) if score_deltas else None,
        "labeled_pairs": len(labeled_losses),
        "labeled_log_loss": fmean(labeled_losses) if labeled_losses else None,
    }
    print(json.dumps(report, ensure_ascii=False))
    failed = report["coverage"] < args.min_coverage
    if report["labeled_log_loss"] is not None:
        failed = failed or report["labeled_log_loss"] > args.max_labeled_log_loss
    if failed:
        raise SystemExit(2)


if __name__ == "__main__":
    main()
