#!/usr/bin/env python3
"""Register a trained artifact and its embeddings as SHADOW, never ACTIVE."""

from __future__ import annotations

import argparse
import csv
import json
import os
from pathlib import Path

import psycopg


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--database-url", default=os.environ.get("DATABASE_URL"))
    parser.add_argument("--artifact", type=Path, required=True)
    parser.add_argument("--artifact-uri", required=True)
    args = parser.parse_args()
    if not args.database_url:
        raise SystemExit("DATABASE_URL or --database-url is required")
    metrics = json.loads((args.artifact / "metrics.json").read_text(encoding="utf-8"))
    model_version = metrics["model_version"]
    with psycopg.connect(args.database_url) as connection:
        with connection.cursor() as cursor:
            cursor.execute(
                """
                insert into taste_model_versions(model_version,algorithm_version,status,artifact_uri,calibration)
                values (%s,%s,'SHADOW',%s,%s::jsonb)
                on conflict(model_version) do update set
                  algorithm_version=excluded.algorithm_version,status='SHADOW',
                  artifact_uri=excluded.artifact_uri,calibration=excluded.calibration
                """,
                (model_version, metrics["algorithm_version"], args.artifact_uri, json.dumps(metrics)),
            )
            with (args.artifact / "user_embeddings.csv").open(encoding="utf-8") as source:
                for row in csv.DictReader(source):
                    evidence = int(row["evidence_count"])
                    confidence = "HIGH" if evidence >= 30 else "MEDIUM" if evidence >= 10 else "LOW"
                    cursor.execute(
                        """
                        insert into taste_user_embeddings(
                          user_id,model_version,algorithm_version,embedding,evidence_count,confidence
                        ) values (%s,%s,%s,%s::real[],%s,%s)
                        on conflict(user_id,model_version) do update set
                          algorithm_version=excluded.algorithm_version,
                          embedding=excluded.embedding,evidence_count=excluded.evidence_count,
                          confidence=excluded.confidence,calculated_at=now()
                        """,
                        (row["user_id"], model_version, metrics["algorithm_version"], row["embedding"], evidence, confidence),
                    )
    print(json.dumps({"model_version": model_version, "status": "SHADOW"}))


if __name__ == "__main__":
    main()
