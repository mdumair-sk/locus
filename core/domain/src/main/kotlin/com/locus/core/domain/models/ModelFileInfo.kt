package com.locus.core.domain.models

data class ModelFileInfo(
    val name: String,
    val size: Long,
    val sha256: String = "",
)
