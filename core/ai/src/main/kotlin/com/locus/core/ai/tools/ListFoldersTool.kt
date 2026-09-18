package com.locus.core.ai.tools

import com.locus.core.ai.toolloop.ToolExecutor
import com.locus.core.ai.toolloop.ToolSchema
import com.locus.core.domain.notes.NoteRepository
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read tool: list_folders (C-3). Wraps [NoteRepository.listFolders], JSON-encoding the folder list.
 */
@Singleton
class ListFoldersTool
    @Inject
    constructor(
        private val noteRepository: NoteRepository,
        private val json: Json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            },
    ) : ToolExecutor {
        override val schema: ToolSchema =
            ToolSchema(
                name = "list_folders",
                description = "List all folder paths in the note library.",
                parametersJsonSchema =
                    """
                    {
                      "type": "object",
                      "properties": {}
                    }
                    """.trimIndent(),
            )

        override suspend fun execute(argumentsJson: String): String {
            val folders = noteRepository.listFolders()
            return json.encodeToString(
                ListSerializer(String.serializer()),
                folders,
            )
        }
    }
