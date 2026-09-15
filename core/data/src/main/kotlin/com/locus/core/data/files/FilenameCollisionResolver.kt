package com.locus.core.data.files

/**
 * Pure function resolving filename / title collisions within a folder according to N-2:
 * "Filename collisions within a folder ... are resolved by auto-suffixing (`Title (2).md`, `Title (3).md`, ...)"
 */
object FilenameCollisionResolver {
    fun resolve(
        desiredTitle: String,
        existingTitlesInFolder: Set<String>,
    ): String {
        if (desiredTitle !in existingTitlesInFolder) {
            return desiredTitle
        }

        val hasMdExtension = desiredTitle.endsWith(".md", ignoreCase = true)
        val (base, ext) =
            if (hasMdExtension) {
                desiredTitle.dropLast(3) to desiredTitle.takeLast(3)
            } else {
                desiredTitle to ""
            }

        var counter = 2
        while (true) {
            val candidate = "$base ($counter)$ext"
            if (candidate !in existingTitlesInFolder) {
                return candidate
            }
            counter++
        }
    }
}
