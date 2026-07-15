#!/usr/bin/env python3
"""Export consent-filtered user features and implicit pair labels from PostgreSQL."""

from __future__ import annotations

import argparse
import json
import os
from collections import defaultdict
from pathlib import Path

import psycopg


def write_jsonl(path: Path, rows: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as output:
        for row in rows:
            output.write(json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--database-url", default=os.environ.get("DATABASE_URL"))
    parser.add_argument("--output", type=Path, default=Path("artifacts/dataset"))
    args = parser.parse_args()
    if not args.database_url:
        raise SystemExit("DATABASE_URL or --database-url is required")

    features: dict[str, dict[str, float]] = defaultdict(lambda: defaultdict(float))
    with psycopg.connect(args.database_url) as connection:
        with connection.cursor() as cursor:
            cursor.execute("select id,preferred_genres from users")
            for user_id, genres in cursor:
                for genre in split_tags(genres):
                    features[str(user_id)][f"genre:{genre.lower()}"] += 2.0

            cursor.execute("select user_id,title,artist_name,genre_tags from profile_signature_tracks")
            for user_id, title, artist, genres in cursor:
                bucket = features[str(user_id)]
                bucket[f"artist:{artist.strip().lower()}"] += 0.75
                bucket[f"track:{title.strip().lower()}|{artist.strip().lower()}"] += 1.0
                for genre in split_tags(genres):
                    bucket[f"genre:{genre.lower()}"] += 1.5

            cursor.execute("select user_id,artist_name,genre_tags from profile_favorite_artists")
            for user_id, artist, genres in cursor:
                bucket = features[str(user_id)]
                bucket[f"artist:{artist.strip().lower()}"] += 1.5
                for genre in split_tags(genres):
                    bucket[f"genre:{genre.lower()}"] += 1.0

            # Collection consent is checked again at export time. PRIVATE visibility does not
            # prevent use for the owner's model, but raw listening rows never leave this export.
            cursor.execute(
                """
                select event.user_id,event.canonical_key,event.title,event.artist_name,event.completion_ratio,
                  extract(epoch from (now()-event.ended_at))/86400.0 age_days
                from music_listen_events event
                join user_privacy_settings privacy on privacy.user_id=event.user_id
                where privacy.listening_insights_enabled=true
                  and event.ended_at>now()-interval '90 days'
                """
            )
            for user_id, key, title, artist, completion, age_days in cursor:
                completion = float(completion if completion is not None else 0.5)
                recency = 1.0 / (1.0 + max(float(age_days), 0.0) / 30.0)
                weight = (0.35 + 0.65 * completion) * recency
                bucket = features[str(user_id)]
                bucket[f"listen:{key}"] += weight
                bucket[f"artist:{artist.strip().lower()}"] += weight * 0.35
                bucket[f"track:{title.strip().lower()}|{artist.strip().lower()}"] += weight * 0.5

            cursor.execute(
                """
                select actor_user_id,target_user_id,strength,created_at
                from taste_match_feedback
                where created_at>now()-interval '180 days'
                union all
                select follower_id,followed_id,0.8,created_at
                from user_follows
                """
            )
            pairs = [
                {
                    "left": str(left),
                    "right": str(right),
                    "strength": float(strength),
                    "observed_at": observed_at.isoformat(),
                }
                for left, right, strength, observed_at in cursor
                if left != right
            ]

    users = [
        {
            "user_id": user_id,
            "features": sorted((name, min(weight, 6.0)) for name, weight in bucket.items()),
        }
        for user_id, bucket in features.items()
        if bucket
    ]
    write_jsonl(args.output / "users.jsonl", users)
    write_jsonl(args.output / "pairs.jsonl", pairs)
    print(json.dumps({"users": len(users), "pairs": len(pairs), "output": str(args.output)}))


def split_tags(value: str | None) -> list[str]:
    return [item.strip() for item in (value or "").split(",") if item.strip()]


if __name__ == "__main__":
    main()
