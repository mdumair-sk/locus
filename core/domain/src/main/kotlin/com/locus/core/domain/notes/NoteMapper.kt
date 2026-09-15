package com.locus.core.domain.notes

fun ParsedNote.toDomain(folderPath: String): Note =
    Note(
        id = id,
        title = title,
        type = type,
        folderPath = folderPath,
        pinned = pinned,
        color = color,
        tags = tags,
        created = created,
        modified = modified,
        checksum = checksum ?: Checksum.sha256(body),
    )
