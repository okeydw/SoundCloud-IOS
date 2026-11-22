package com.scd.android

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class OfflineLibraryTest {
    @Test fun all159LikesAreIndexedAndAvailableOfflineWithoutScrolling() = runBlocking {
        val previous = Api.sessionId
        val engine = if (Http.ready) Http.engine else null
        Prefs.init(MemorySettings())
        Api.storeSession("paged-likes")
        Likes.init(MemorySettings())
        val pages = mutableListOf<Int>()
        try {
            Http.engine = object : HttpEngine {
                override suspend fun execute(req: HttpRequest): HttpResponse {
                    val page = req.url.substringAfter("page=").toInt()
                    pages += page
                    val rows = (page * 50 until minOf(page * 50 + 50, 159)).joinToString { """{"urn":"track:$it"}""" }
                    val body = """{"collection":[$rows],"has_more":${page < 3}}"""
                    return HttpResponse(200, emptyMap(), body.encodeToByteArray(), req.url)
                }
                override fun <T> stream(req: HttpRequest, block: (HttpStream) -> T): T = error("Unexpected stream")
            }
            assertEquals(159, Library.likedPage(0, true, false).first.size)
            assertEquals(159, Likes.urns.size)
            assertEquals(159, Library.likedPage(0, false, true).first.size)
            assertEquals(listOf(0, 1, 2, 3), pages)
        } finally {
            Api.storeSession(previous)
            Library.reset()
            Likes.reset()
            if (engine != null) Http.engine = engine
            else Http::class.java.getDeclaredField("engine").apply { isAccessible = true }.set(null, null)
        }
    }

    @Test fun importsThePreviousSessionsCachedPageWithoutGoingOnline() = runBlocking {
        val previousSession = Api.sessionId
        val previousEngine = if (Http.ready) Http.engine else null
        Api.storeSession("upgrade-test")
        Likes.init(MemorySettings())
        val requests = mutableListOf<HttpRequest>()
        val urn = "soundcloud:tracks:12"
        try {
            Http.engine = object : HttpEngine {
                override suspend fun execute(req: HttpRequest): HttpResponse {
                    requests += req
                    val json = """{"collection":[{"urn":"soundcloud:tracks:12","title":"Cached"}],"has_more":false}"""
                    return HttpResponse(200, mapOf("vary" to "x-session-id"), json.encodeToByteArray(), req.url)
                }
                override fun <T> stream(req: HttpRequest, block: (HttpStream) -> T): T = error("Unexpected stream")
            }
            assertEquals(urn, Library.likedPage(0, false, true).first.single().urn)
            assertEquals(urn, Library.likedPage(0, false, true).first.single().urn)
            assertEquals(1, requests.size)
            assertTrue(requests.single().cacheOnly)
            assertTrue(requests.single().headers.contains("x-session-id" to "upgrade-test"))
        } finally {
            Api.storeSession(previousSession)
            Likes.reset()
            if (previousEngine != null) Http.engine = previousEngine
            else Http::class.java.getDeclaredField("engine").apply { isAccessible = true }.set(null, null)
        }
    }

    @Test fun reconnectSendsOnlyTheLatestOfflineChoice() = runBlocking {
        val store = MemorySettings()
        val previousSession = Api.sessionId
        val previousEngine = if (Http.ready) Http.engine else null
        val requests = java.util.concurrent.CopyOnWriteArrayList<HttpRequest>()
        Prefs.init(MemorySettings())
        Api.storeSession("like-sync-test")
        Likes.init(store)
        Prefs.setOfflineMode(true)
        try {
            Http.engine = object : HttpEngine {
                override suspend fun execute(req: HttpRequest): HttpResponse {
                    requests += req
                    return HttpResponse(200, emptyMap(), "{}".encodeToByteArray(), req.url)
                }
                override fun <T> stream(req: HttpRequest, block: (HttpStream) -> T): T = error("Unexpected stream")
            }
            val track = Track(urn = "soundcloud:tracks:1027")
            Likes.toggle(track)
            Likes.toggle(track)
            Likes.toggle(track)
            Likes.init(store)
            assertTrue(requests.isEmpty())
            Prefs.setOfflineMode(false)
            Likes.sync()
            kotlinx.coroutines.withTimeout(3000) {
                while (!store.getString("changes", "").orEmpty().contains("\"sent\":true")) kotlinx.coroutines.delay(5)
            }
            val updates = requests.filter { it.url.contains("/likes/tracks/") }
            assertEquals(1, updates.size)
            assertEquals("POST", updates.single().method)
            assertTrue(updates.single().headers.contains("x-session-id" to "like-sync-test"))
        } finally {
            Api.storeSession(previousSession)
            Likes.reset()
            Prefs.setOfflineMode(false)
            if (previousEngine != null) Http.engine = previousEngine
            else Http::class.java.getDeclaredField("engine").apply { isAccessible = true }.set(null, null)
        }
    }

    @Test fun offlineChangesSurviveRestartAndOverrideStalePages() = runBlocking {
        val store = MemorySettings()
        val previous = Api.sessionId
        Prefs.init(MemorySettings())
        Api.storeSession("library-test-session")
        Likes.init(store)
        Prefs.setOfflineMode(true)
        try {
            val track = Track(urn = "soundcloud:tracks:628", title = "Local")
            LibraryPages.save(LibraryPages.ListKind.LIKES, 0, listOf(track), false, Api.sessionId)
            Likes.seed(listOf(track))
            assertFalse(Likes.toggle(track))
            Likes.init(store)
            val stale = Library.likedPage(0, false, true).first
            assertTrue(Likes.visible(stale).isEmpty())
            assertFalse(Likes.isLiked(track.urn))
            assertTrue(Likes.toggle(track))
            Likes.init(store)
            assertEquals(listOf(track), Likes.visible(emptyList()))
            LibraryPages.save(LibraryPages.ListKind.HISTORY, 0, listOf(track), false, Api.sessionId)
            assertEquals(listOf(track), Library.historyPage(0, false, true).first)
            Api.storeSession("different-session")
            assertTrue(Likes.urns.isEmpty())
            assertTrue(Library.historyPage(0, false, true).first.isEmpty())
        } finally {
            Api.storeSession(previous)
            Likes.reset()
            Prefs.setOfflineMode(false)
        }
    }
}
