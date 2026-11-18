package com.markq.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkListQueryTest {
    @Test
    fun allAndTagQueryIncompleteUnlessIdsAreKept() {
        assertEquals(MarkListQuery.Incomplete, MarkListQuery.forFilter(MarkListFilter.All, emptySet()))
        assertEquals(MarkListQuery.Incomplete, MarkListQuery.forFilter(MarkListFilter.Tag("work"), emptySet()))
        val kept = MarkListQuery.forFilter(MarkListFilter.All, setOf("a", "b"))
        assertTrue(kept is MarkListQuery.IncompleteOrIds)
        assertEquals(setOf("a", "b"), (kept as MarkListQuery.IncompleteOrIds).ids.toSet())
        val tagged = MarkListQuery.forFilter(MarkListFilter.Tag("work"), setOf("stay"))
        assertEquals(listOf("stay"), (tagged as MarkListQuery.IncompleteOrIds).ids)
    }

    @Test
    fun completedFilterIgnoresKeptIds() {
        assertEquals(
            MarkListQuery.Completed,
            MarkListQuery.forFilter(MarkListFilter.Completed, setOf("still-ignored")),
        )
        assertEquals(MarkListQuery.Completed, MarkListQuery.forFilter(MarkListFilter.Completed, emptySet()))
    }
}

class MarkHomeListTest {
    @Test
    fun decodesTagsOnceForFilterAndSearch() {
        val stored = MarkTags.encode(listOf("Work", "home"))
        assertTrue(
            MarkHomeList.matches(
                completed = false,
                storedTags = stored,
                text = "buy milk",
                createdBy = "ada",
                completedBy = null,
                filter = MarkListFilter.Tag("work"),
                stayVisible = false,
                query = "work",
            ),
        )
        assertFalse(
            MarkHomeList.matches(
                completed = false,
                storedTags = stored,
                text = "buy milk",
                createdBy = "ada",
                completedBy = null,
                filter = MarkListFilter.Tag("work"),
                stayVisible = false,
                query = "office",
            ),
        )
        assertFalse(
            MarkHomeList.matches(
                completed = true,
                storedTags = stored,
                text = "buy milk",
                createdBy = "ada",
                completedBy = "ada",
                filter = MarkListFilter.Tag("work"),
                stayVisible = false,
                query = "work",
            ),
        )
        assertTrue(
            MarkHomeList.matches(
                completed = true,
                storedTags = stored,
                text = "buy milk",
                createdBy = "ada",
                completedBy = "ada",
                filter = MarkListFilter.Tag("work"),
                stayVisible = true,
                query = "ada",
            ),
        )
    }

    @Test
    fun completedAndSearchStayConsistentWithVisibility() {
        val stored = MarkTags.encode(listOf("errand"))
        assertTrue(
            MarkHomeList.matches(
                completed = true,
                storedTags = stored,
                text = "buy milk",
                createdBy = "ada",
                completedBy = "ada",
                filter = MarkListFilter.Completed,
                stayVisible = false,
                query = "milk",
            ),
        )
        assertFalse(
            MarkHomeList.matches(
                completed = false,
                storedTags = stored,
                text = "buy milk",
                createdBy = "ada",
                completedBy = null,
                filter = MarkListFilter.Completed,
                stayVisible = true,
                query = "milk",
            ),
        )
        assertFalse(
            MarkHomeList.matches(
                completed = true,
                storedTags = stored,
                text = "buy milk",
                createdBy = "ada",
                completedBy = "ada",
                filter = MarkListFilter.All,
                stayVisible = false,
                query = "",
            ),
        )
    }
}
