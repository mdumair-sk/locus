package com.locus.core.data.files

/**
 * Pure function resolving filename / title collisions within a folder according to N-2:
 * "Filename collisions within a folder ... are resolved by auto-suffixing (`Title (2).md`, `Title (3).md`, ...)"
 */
object FilenameCollisionResolver {
    private const val MD_EXTENSION = ".md"
    private const val MD_EXTENSION_LENGTH = 3

    fun resolve(
        desiredTitle: String,
        existingTitlesInFolder: Set<String>,
    ): String {
        if (desiredTitle !in existingTitlesInFolder) {
            return desiredTitle
        }

        val hasMdExtension = desiredTitle.endsWith(MD_EXTENSION, ignoreCase = true)
        val (base, ext) =
            if (hasMdExtension) {
                desiredTitle.dropLast(MD_EXTENSION_LENGTH) to desiredTitle.takeLast(MD_EXTENSION_LENGTH)
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
