package com.locus.core.domain.providers

data class ToolSchema(
    val name: String,
    val description: String,
    val parametersJsonSchema: String = "{}",
)
