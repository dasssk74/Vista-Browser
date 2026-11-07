package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.robolectric.annotation.Config

@Config(sdk = [36])
class BrowserSyncRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun addAndObserveBookmark_authenticatedOwner_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = BrowserSyncRepository(firestore)

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addBookmark("Brave Search", "https://search.brave.com", "Privacy")
        }
        assertTrue(createResult.isSuccess)
        val bookmarkId = createResult.getOrThrow()

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeBookmarks(aliceUid).first { list -> list.any { it.id == bookmarkId } }
        }
        assertTrue(observed.any { it.id == bookmarkId && it.title == "Brave Search" })
    }

    @Test
    fun addAndObserveShortcut_authenticatedOwner_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = BrowserSyncRepository(firestore)

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addShortcut("Wikipedia", "wikipedia.org", isPinned = true)
        }
        assertTrue(createResult.isSuccess)
        val shortcutId = createResult.getOrThrow()

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeShortcuts(aliceUid).first { list -> list.any { it.id == shortcutId } }
        }
        val match = observed.firstOrNull { it.id == shortcutId }
        assertNotNull(match)
        assertTrue(match!!.logoUrl.contains("wikipedia.org"))
    }

    @Test
    fun getBookmarkById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = BrowserSyncRepository(firestore)
        val bookmarkId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.addBookmark("Private Note", "https://example.org", "Personal").getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = BrowserSyncRepository(firestore)
        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getBookmarkById(aliceUid, bookmarkId)
        }
        assertTrue(result.isFailure)
        val firestoreEx = result.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun observeBookmarks_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = BrowserSyncRepository(firestore)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeBookmarks("some_user_123").first()
            }
            fail("Expected PERMISSION_DENIED exception for unauthenticated user")
        } catch (e: Exception) {
            val firestoreEx = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", firestoreEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice@test.com"
        const val BOB_EMAIL = "bob@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
