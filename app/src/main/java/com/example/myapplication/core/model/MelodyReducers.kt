package com.example.myapplication.core.model

data class NearbyDelta(
    val sequence: Long,
    val entered: List<NearbyListener> = emptyList(),
    val updated: List<NearbyListener> = emptyList(),
    val leftHandles: Set<String> = emptySet()
)

data class NearbyDeltaResult(
    val sequence: Long,
    val listeners: List<NearbyListener>,
    val applied: Boolean
)

object MelodyReducers {
    fun applyNearbyDelta(
        currentSequence: Long,
        current: List<NearbyListener>,
        delta: NearbyDelta
    ): NearbyDeltaResult {
        if (delta.sequence <= currentSequence) {
            return NearbyDeltaResult(currentSequence, current, applied = false)
        }

        val byHandle = current
            .filterNot { it.nearbyHandle in delta.leftHandles }
            .associateBy { it.nearbyHandle }
            .toMutableMap()
        delta.entered.forEach { byHandle[it.nearbyHandle] = it }
        delta.updated.forEach { update ->
            if (byHandle.containsKey(update.nearbyHandle)) {
                byHandle[update.nearbyHandle] = update
            }
        }
        return NearbyDeltaResult(
            sequence = delta.sequence,
            listeners = byHandle.values.sortedWith(
                compareByDescending<NearbyListener> { it.tasteMatch?.score ?: -1 }
                    .thenBy { it.displayAlias.lowercase() },
            ),
            applied = true
        )
    }

    fun applyVote(poll: LoungePoll, optionId: String): LoungePoll {
        if (!poll.isOpen || poll.options.none { it.id == optionId }) return poll
        val previous = poll.myChoice
        val options = poll.options.map { option ->
            val withoutPrevious = if (option.id == previous && previous != optionId) {
                (option.voteCount - 1).coerceAtLeast(0)
            } else {
                option.voteCount
            }
            option.copy(
                voteCount = if (option.id == optionId && previous != optionId) {
                    withoutPrevious + 1
                } else {
                    withoutPrevious
                }
            )
        }
        return poll.copy(options = options, myChoice = optionId)
    }

    fun chatValidationError(
        content: String,
        relationship: RelationshipStatus,
        maxLength: Int = 1_000
    ): String? = when {
        relationship != RelationshipStatus.MUTUAL -> "맞팔 사용자와만 대화할 수 있어요"
        content.isBlank() -> "메시지를 입력해 주세요"
        content.length > maxLength -> "메시지는 ${maxLength}자 이하여야 해요"
        else -> null
    }
}
