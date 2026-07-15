#!/usr/bin/env python3
"""Train one shared Siamese user tower and export shadow user embeddings."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
import random
from dataclasses import dataclass
from pathlib import Path

import torch
from torch import nn
from torch.nn import functional as F


HASH_BUCKETS = 65_536
EMBEDDING_DIMENSIONS = 128


def feature_bucket(value: str) -> int:
    digest = hashlib.blake2b(value.encode("utf-8"), digest_size=8, person=b"sync-taste").digest()
    return int.from_bytes(digest, "big") % HASH_BUCKETS


class UserTower(nn.Module):
    def __init__(self) -> None:
        super().__init__()
        self.features = nn.EmbeddingBag(HASH_BUCKETS, 192, mode="sum", include_last_offset=True)
        self.projection = nn.Sequential(
            nn.Linear(192, 256),
            nn.GELU(),
            nn.LayerNorm(256),
            nn.Dropout(0.12),
            nn.Linear(256, EMBEDDING_DIMENSIONS),
        )

    def forward(self, indices: torch.Tensor, offsets: torch.Tensor, weights: torch.Tensor) -> torch.Tensor:
        pooled = self.features(indices, offsets, per_sample_weights=weights)
        return F.normalize(self.projection(pooled), dim=-1)


@dataclass(frozen=True)
class Pair:
    left: str
    right: str
    target: float
    weight: float
    observed_at: str


def read_jsonl(path: Path) -> list[dict]:
    with path.open(encoding="utf-8") as source:
        return [json.loads(line) for line in source if line.strip()]


def encode_users(rows: list[dict]) -> dict[str, tuple[list[int], list[float]]]:
    return {
        row["user_id"]: (
            [feature_bucket(name) for name, _ in row["features"]],
            [float(weight) for _, weight in row["features"]],
        )
        for row in rows
        if row.get("features")
    }


def make_pairs(rows: list[dict], users: dict[str, tuple[list[int], list[float]]]) -> list[Pair]:
    pairs: list[Pair] = []
    for row in rows:
        if row["left"] not in users or row["right"] not in users or row["left"] == row["right"]:
            continue
        strength = max(-1.0, min(1.0, float(row["strength"])))
        pairs.append(Pair(row["left"], row["right"], (strength + 1.0) / 2.0, max(abs(strength), 0.15), row["observed_at"]))
    # Weak sampled negatives prevent a positive-only interaction graph from collapsing every
    # user onto one vector. Known interactions in either direction are excluded and the low
    # weight acknowledges that an unobserved pair is not necessarily a dislike.
    known_pairs = {frozenset((pair.left, pair.right)) for pair in pairs}
    user_ids = sorted(users)
    rng = random.Random(26)
    sampled: list[Pair] = []
    for pair in pairs:
        if pair.target < 0.75:
            continue
        for _ in range(12):
            candidate = rng.choice(user_ids)
            candidate_pair = frozenset((pair.left, candidate))
            if candidate != pair.left and candidate_pair not in known_pairs:
                sampled.append(Pair(pair.left, candidate, 0.25, 0.08, pair.observed_at))
                known_pairs.add(candidate_pair)
                break
    pairs.extend(sampled)
    return sorted(pairs, key=lambda item: item.observed_at)


def tower_batch(model: UserTower, user_ids: list[str], users: dict[str, tuple[list[int], list[float]]]) -> torch.Tensor:
    indices: list[int] = []
    weights: list[float] = []
    offsets = [0]
    for user_id in user_ids:
        user_indices, user_weights = users[user_id]
        indices.extend(user_indices)
        weights.extend(user_weights)
        offsets.append(len(indices))
    return model(
        torch.tensor(indices, dtype=torch.long),
        torch.tensor(offsets, dtype=torch.long),
        torch.tensor(weights, dtype=torch.float32),
    )


def pair_loss(model: UserTower, batch: list[Pair], users: dict[str, tuple[list[int], list[float]]]) -> torch.Tensor:
    left = tower_batch(model, [pair.left for pair in batch], users)
    right = tower_batch(model, [pair.right for pair in batch], users)
    logits = 6.0 * (left * right).sum(dim=-1)
    targets = torch.tensor([pair.target for pair in batch], dtype=torch.float32)
    weights = torch.tensor([pair.weight for pair in batch], dtype=torch.float32)
    return (F.binary_cross_entropy_with_logits(logits, targets, reduction="none") * weights).mean()


@torch.no_grad()
def metrics(model: UserTower, pairs: list[Pair], users: dict[str, tuple[list[int], list[float]]]) -> dict[str, float]:
    if not pairs:
        return {"pair_count": 0, "weighted_log_loss": math.nan, "pair_accuracy": math.nan}
    losses: list[float] = []
    correct = 0
    for start in range(0, len(pairs), 256):
        batch = pairs[start:start + 256]
        left = tower_batch(model, [pair.left for pair in batch], users)
        right = tower_batch(model, [pair.right for pair in batch], users)
        probabilities = torch.sigmoid(6.0 * (left * right).sum(dim=-1))
        for probability, pair in zip(probabilities.tolist(), batch):
            probability = min(max(probability, 1e-6), 1.0 - 1e-6)
            losses.append(-(pair.target * math.log(probability) + (1.0 - pair.target) * math.log(1.0 - probability)) * pair.weight)
            correct += int((probability >= 0.5) == (pair.target >= 0.5))
    return {
        "pair_count": len(pairs),
        "weighted_log_loss": sum(losses) / len(losses),
        "pair_accuracy": correct / len(pairs),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", type=Path, default=Path("artifacts/dataset"))
    parser.add_argument("--output", type=Path, default=Path("artifacts/model"))
    parser.add_argument("--model-version", required=True)
    parser.add_argument("--epochs", type=int, default=20)
    parser.add_argument("--seed", type=int, default=26)
    args = parser.parse_args()
    random.seed(args.seed)
    torch.manual_seed(args.seed)

    users = encode_users(read_jsonl(args.dataset / "users.jsonl"))
    pairs = make_pairs(read_jsonl(args.dataset / "pairs.jsonl"), users)
    if len(users) < 2 or len(pairs) < 10:
        raise SystemExit("Need at least 2 feature-bearing users and 10 labeled pairs")
    split = max(1, int(len(pairs) * 0.8))
    train_pairs, validation_pairs = pairs[:split], pairs[split:]
    model = UserTower()
    optimizer = torch.optim.AdamW(model.parameters(), lr=2e-3, weight_decay=1e-4)
    best_state: dict[str, torch.Tensor] | None = None
    best_loss = float("inf")
    for _ in range(args.epochs):
        model.train()
        random.shuffle(train_pairs)
        for start in range(0, len(train_pairs), 128):
            optimizer.zero_grad(set_to_none=True)
            loss = pair_loss(model, train_pairs[start:start + 128], users)
            loss.backward()
            nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            optimizer.step()
        model.eval()
        validation = metrics(model, validation_pairs, users)
        validation_loss = validation["weighted_log_loss"]
        if math.isnan(validation_loss) or validation_loss < best_loss:
            best_loss = validation_loss
            best_state = {name: value.detach().clone() for name, value in model.state_dict().items()}
    if best_state is not None:
        model.load_state_dict(best_state)

    args.output.mkdir(parents=True, exist_ok=True)
    torch.save(model.state_dict(), args.output / "user_tower.pt")
    report = {
        "model_version": args.model_version,
        "algorithm_version": "HYBRID_SIAMESE_V2_LEARNED",
        "dimensions": EMBEDDING_DIMENSIONS,
        "train": metrics(model, train_pairs, users),
        "validation": metrics(model, validation_pairs, users),
        "seed": args.seed,
    }
    (args.output / "metrics.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    model.eval()
    with (args.output / "user_embeddings.csv").open("w", newline="", encoding="utf-8") as output:
        writer = csv.writer(output)
        writer.writerow(["user_id", "evidence_count", "embedding"])
        for user_id, (_, feature_weights) in sorted(users.items()):
            embedding = tower_batch(model, [user_id], users)[0].tolist()
            writer.writerow([user_id, len(feature_weights), "{" + ",".join(f"{value:.8f}" for value in embedding) + "}"])
    print(json.dumps(report))


if __name__ == "__main__":
    main()
