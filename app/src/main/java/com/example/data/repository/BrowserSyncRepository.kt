package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.R
import com.example.data.model.BookmarkItem
import com.example.data.model.ShortcutItem
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class BrowserSyncRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeBookmarks(userId: String = auth.currentUser?.uid ?: "unauthenticated"): Flow<List<BookmarkItem>> = flow {
        val path = "users/$userId/bookmarks"
        emitAll(
            db.collection("users")
                .document(userId)
                .collection("bookmarks")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        BookmarkItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeShortcuts(userId: String = auth.currentUser?.uid ?: "unauthenticated"): Flow<List<ShortcutItem>> = flow {
        val path = "users/$userId/shortcuts"
        emitAll(
            db.collection("users")
                .document(userId)
                .collection("shortcuts")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ShortcutItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(compareByDescending<ShortcutItem> { it.isPinned }.thenBy { it.createdAt?.seconds ?: 0L })
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addBookmark(
        title: String,
        url: String,
        folder: String = "General"
    ): Result<String> {
        val uid = requireUserId()
        val normalizedUrl = normalizeWebsiteUrl(url)
        val faviconUrl = buildFaviconUrl(normalizedUrl)
        val docRef = db.collection("users").document(uid).collection("bookmarks").document()
        val payload = mapOf(
            "userId" to uid,
            "title" to title.trim().take(200).ifEmpty { extractDomainTitle(normalizedUrl) },
            "url" to normalizedUrl.take(2048),
            "folder" to folder.trim().take(100).ifEmpty { "General" },
            "faviconUrl" to faviconUrl.take(2048),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateBookmark(bookmark: BookmarkItem): Result<Unit> {
        val uid = requireUserId()
        require(bookmark.id.isNotBlank()) { "Bookmark ID must not be blank for update" }
        requireNotNull(bookmark.createdAt) { "Bookmark createdAt must not be null for update" }
        val docRef = db.collection("users").document(uid).collection("bookmarks").document(bookmark.id)
        return try {
            val normalizedUrl = normalizeWebsiteUrl(bookmark.url)
            val updatedItem = bookmark.copy(
                userId = uid,
                title = bookmark.title.trim().take(200).ifEmpty { extractDomainTitle(normalizedUrl) },
                url = normalizedUrl.take(2048),
                folder = bookmark.folder.trim().take(100).ifEmpty { "General" },
                faviconUrl = buildFaviconUrl(normalizedUrl).take(2048),
                updatedAt = null
            )
            docRef.set(updatedItem).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteBookmark(bookmarkId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("bookmarks").document(bookmarkId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getBookmarkById(targetUserId: String, bookmarkId: String): Result<BookmarkItem?> {
        val docRef = db.collection("users").document(targetUserId).collection("bookmarks").document(bookmarkId)
        return try {
            val snap = docRef.get().await()
            Result.success(
                snap.toObject(
                    BookmarkItem::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun addShortcut(
        name: String,
        url: String,
        isPinned: Boolean = true
    ): Result<String> {
        val uid = requireUserId()
        val normalizedUrl = normalizeWebsiteUrl(url)
        val logoUrl = buildFaviconUrl(normalizedUrl)
        val docRef = db.collection("users").document(uid).collection("shortcuts").document()
        val payload = mapOf(
            "userId" to uid,
            "name" to name.trim().take(100).ifEmpty { extractDomainTitle(normalizedUrl) },
            "url" to normalizedUrl.take(2048),
            "logoUrl" to logoUrl.take(2048),
            "isPinned" to isPinned,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateShortcut(shortcut: ShortcutItem): Result<Unit> {
        val uid = requireUserId()
        require(shortcut.id.isNotBlank()) { "Shortcut ID must not be blank for update" }
        requireNotNull(shortcut.createdAt) { "Shortcut createdAt must not be null for update" }
        val docRef = db.collection("users").document(uid).collection("shortcuts").document(shortcut.id)
        return try {
            val normalizedUrl = normalizeWebsiteUrl(shortcut.url)
            val updated = shortcut.copy(
                userId = uid,
                name = shortcut.name.trim().take(100).ifEmpty { extractDomainTitle(normalizedUrl) },
                url = normalizedUrl.take(2048),
                logoUrl = buildFaviconUrl(normalizedUrl).take(2048),
                updatedAt = null
            )
            docRef.set(updated).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteShortcut(shortcutId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("shortcuts").document(shortcutId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    companion object {
        fun normalizeWebsiteUrl(rawInput: String): String {
            val trimmed = rawInput.trim()
            if (trimmed.isEmpty()) return "https://search.brave.com"
            if (trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)
            ) {
                return trimmed
            }
            return "https://$trimmed"
        }

        fun extractHost(url: String): String {
            return try {
                val normalized = normalizeWebsiteUrl(url)
                val host = Uri.parse(normalized).host?.lowercase() ?: ""
                host.removePrefix("www.")
            } catch (e: Exception) {
                ""
            }
        }

        fun extractDomainTitle(url: String): String {
            val host = extractHost(url)
            if (host.isBlank()) return "Website"
            val primaryPart = host.substringBefore(".")
            return primaryPart.replaceFirstChar { it.uppercase() }
        }

        fun buildFaviconUrl(url: String): String {
            val host = extractHost(url)
            return if (host.isNotBlank() && host.contains(".")) {
                "https://www.google.com/s2/favicons?domain=$host&sz=128"
            } else {
                ""
            }
        }

        fun isValidWebsiteUrl(input: String): Boolean {
            val trimmed = input.trim()
            if (trimmed.isEmpty() || trimmed.contains(" ")) return false
            val host = extractHost(trimmed)
            return host.isNotBlank() && host.contains(".") && host.substringAfterLast(".").length >= 2
        }
    }
}
