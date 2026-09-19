package com.locus.core.domain.usage

import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

/** Summary of token usage and associated costs for a single provider. */
data class ProviderUsageSummary(
    val providerId: String,
    val inputTokens: Long,
    val outputTokens: Long,
    val totalCost: Double,
) {
    val totalTokens: Long
        get() = inputTokens + outputTokens
}

/** Monthly aggregate summary of usage across all providers. */
data class MonthlyUsageSummary(
    val yearMonth: YearMonth,
    val providers: List<ProviderUsageSummary>,
    val totalCost: Double = providers.sumOf { it.totalCost },
    val totalInputTokens: Long = providers.sumOf { it.inputTokens },
    val totalOutputTokens: Long = providers.sumOf { it.outputTokens },
) {
    val totalTokens: Long
        get() = totalInputTokens + totalOutputTokens
}

/** P-5: Domain contract for recording and observing provider token usage. */
interface UsageTracker {
    suspend fun track(event: UsageEvent)

    fun observeMonthlySummary(yearMonth: YearMonth): Flow<MonthlyUsageSummary>

    suspend fun getMonthlySummary(yearMonth: YearMonth): MonthlyUsageSummary
}
