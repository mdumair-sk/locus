# Locus — Decisions Log

## D-1: detekt thresholds (TooManyFunctions=20, LongMethod=80) chosen as a generous baseline per NF-1's Compose/MVVM shape; tightened only if a real violation proves them too loose.

## D-2: tags stored as a comma-joined TypeConverter column, not a join table, until a prompt needs relational tag filtering.

## D-3: scheduled backup zips the entire chosen tree root, including .locus/trash and .locus/history — N-11 names no carve-out.