package com.scd.android

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SessionRoutingTest {
    @Test fun playableServicesStartOnRelays() {
        for (url in listOf(Endpoints.apiBase, Endpoints.streamBase, Endpoints.imageBase,
            Endpoints.API_STAR, Endpoints.STREAM_STAR, Endpoints.STORAGE_STAR, Endpoints.STORAGE_MAIN)) {
            assertTrue(url, url.contains(".relay.scnative.dev"))
        }
    }

    @Test fun ambiguousResponsesNeverInvalidateASession() = runBlocking {
        val previousEngine = if (Http.ready) Http.engine else null
        val previousSession = Api.sessionId
        var status = 200
        var body = "{}"
        var requestedPath = ""
        val fake = object : HttpEngine {
            override suspend fun execute(req: HttpRequest): HttpResponse {
                requestedPath = req.url.substringAfter(".dev")
                return HttpResponse(status, emptyMap(), body.encodeToByteArray(), req.url)
            }
            override fun <T> stream(req: HttpRequest, block: (HttpStream) -> T): T = error("Unexpected stream")
        }
        try {
            Http.engine = fake
            Api.storeSession("session-test")
            for (invalid in listOf("{}", "null", "<html>error</html>", """{"authenticated":"false"}""")) {
                body = invalid
                assertNull(Api.sessionValid())
            }
            body = """{"authenticated":false}"""
            assertEquals(false, Api.sessionValid())
            body = """{"authenticated":true}"""
            assertEquals(true, Api.sessionValid())
            for (code in listOf(401, 403, 502)) {
                status = code
                assertNull(Api.sessionValid())
            }
            status = 202
            assertEquals(202, Api.refreshSession())
            assertEquals("/auth/soundcloud/refresh", requestedPath)
        } finally {
            if (previousEngine != null) Http.engine = previousEngine
            Api.storeSession(previousSession)
        }
    }
}
