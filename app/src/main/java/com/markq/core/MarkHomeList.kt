package com.markq.core

/**
 * Home-list query shape and row matching.
 * The database is queried by completion (plus any just-completed ids that
 * should stay visible). Tag and search filters then run in memory with a
 * single tag decode per row.
 */
sealed class MarkListQuery {
    data object Incomplete : MarkListQuery()
    data class IncompleteOrIds(val ids: List<String>) : MarkListQuery()
    data object Completed : MarkListQuery()

    companion object {
        fun forFilter(filter: MarkListFilter, keptVisible: Set<String>): MarkListQuery =
            when (filter) {
                MarkListFilter.Completed -> Completed
                else -> if (keptVisible.isEmpty()) {
                    Incomplete
                } else {
                    IncompleteOrIds(keptVisible.toList())
                }
            }
    }
}

object MarkHomeList {
    fun matches(
        completed: Boolean,
        storedTags: String?,
        text: String,
        createdBy: String,
        completedBy: String?,
        filter: MarkListFilter,
        stayVisible: Boolean,
        query: String,
    ): Boolean {
        val tags = MarkTags.decode(storedTags)
        return MarkListVisibility.include(completed, tags, filter, stayVisible) &&
            MarkSearch.matches(query, text, tags, createdBy, completedBy)
    }
}
