package com.doctorknepper.personalore

import com.doctorknepper.personalore.data.PromptLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptLibraryTest {
    private val all = PromptLibrary.categories.flatMap { it.prompts }

    @Test fun existingPromptIdsAreStable() {
        // Favorites and answered history are keyed by these IDs, so they must never move.
        assertTrue(PromptLibrary.find("childhood:0", com.doctorknepper.personalore.data.PromptState())!!.text.startsWith("What is your earliest memory"))
        assertTrue(PromptLibrary.find("daily:11", com.doctorknepper.personalore.data.PromptState())!!.text.startsWith("Describe today in three words"))
        assertEquals(16, PromptLibrary.categories.indexOfFirst { it.slug == "body" })
    }

    @Test fun noDuplicatesAndNewCategoriesPresent() {
        assertEquals(all.size, all.map { it.id }.toSet().size)
        assertEquals(all.size, all.map { it.text.lowercase() }.toSet().size)
        listOf("Body & Grounding", "Anxiety & Thoughts", "Attachment & Trust", "Self-Worth & Focus", "Intimacy & Presence")
            .forEach { assertTrue(it in PromptLibrary.categoryNames) }
        println("PROMPTS ${all.size} in ${PromptLibrary.categories.size} categories")
    }
}
