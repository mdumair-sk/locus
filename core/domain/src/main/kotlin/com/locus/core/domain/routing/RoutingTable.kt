package com.locus.core.domain.routing

enum class TaskType {
    DIGEST_TAGGING_CLUSTER_LABEL,
    CHAT_RAG_QA,
    AGENTIC_MULTI_STEP,
}

data class RoutingPolicy(
    val default: ModelRef,
    val upgrade: ModelRef?,
    val fallback: ModelRef,
)

/**
 * P-4 automatic routing table. Every chain ends at a local model: Digest/tagging/cluster labels :
 * local utility -> cheap cloud -> local chat Chat / RAG Q&A : local chat -> strong cloud -> local
 * chat Agentic multi-step : strongest available cloud -> (none) -> local chat
 */
class RoutingTable(
    private val localUtilityModel: ModelRef,
    private val localChatModel: ModelRef,
    private val cheapCloudModel: ModelRef?,
    private val strongCloudModel: ModelRef?,
    private val strongestAvailableCloudModel: ModelRef?,
) {
    fun policyFor(task: TaskType): RoutingPolicy =
        when (task) {
            TaskType.DIGEST_TAGGING_CLUSTER_LABEL ->
                RoutingPolicy(localUtilityModel, cheapCloudModel, localChatModel)
            TaskType.CHAT_RAG_QA -> RoutingPolicy(localChatModel, strongCloudModel, localChatModel)
            TaskType.AGENTIC_MULTI_STEP ->
                RoutingPolicy(
                    strongestAvailableCloudModel ?: localChatModel,
                    null,
                    localChatModel,
                )
        }
}
