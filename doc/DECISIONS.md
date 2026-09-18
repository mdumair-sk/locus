# Locus — Decisions Log

## D-1: detekt thresholds (TooManyFunctions=20, LongMethod=80) chosen as a generous baseline per NF-1's Compose/MVVM shape; tightened only if a real violation proves them too loose.

## D-2: tags stored as a comma-joined TypeConverter column, not a join table, until a prompt needs relational tag filtering.

## D-3: scheduled backup zips the entire chosen tree root, including .locus/trash and .locus/history — N-11 names no carve-out.

## D-4: llama.cpp pinned to tag v0.4.1 / commit b29c606e28a01b1bc8c1351026a0fa6e616bf6c4, resolved via GitHub Releases API on 2026-09-18.

## D-5: EmbeddingGemma-300M produces 768-d native embeddings; EmbeddingRunner reduces this to the S-2 specified fixed 512-d output via uniform binning / mean-pooling over the extra dimensions (for $i \in [0, 511]$, averaging dimensions $i + 512 \times k < D$) followed by L2-normalization, preserving energy across all native dimensions while matching the fixed 512-d vector contract.