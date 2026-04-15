package com.remotepad.network

import com.remotepad.model.RemoteEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Integration-style tests for [WebSocketClient] using MockWebServer.
 */
class WebSocketClientTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var okClient: OkHttpClient
    private lateinit var client: WebSocketClient

    @Before
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
        okClient = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
        client = WebSocketClient(okClient)
    }

    @After
    fun tearDown() {
        // close() cancels the coroutine scope and force-closes the websocket
        client.close()
        // Shut down OkHttp thread pools so MockWebServer can exit cleanly
        okClient.dispatcher.executorService.shutdown()
        okClient.dispatcher.executorService.awaitTermination(2, TimeUnit.SECONDS)
        okClient.connectionPool.evictAll()
        mockServer.shutdown()
    }

    @Test
    fun `connection state transitions to CONNECTED`() = runBlocking {
        mockServer.enqueue(MockResponse().withWebSocketUpgrade(EchoListener()))

        assertEquals(ConnectionState.DISCONNECTED, client.connectionState.value)

        client.connect("127.0.0.1", mockServer.port)

        withTimeout(3000) {
            client.connectionState.first { it == ConnectionState.CONNECTED }
        }
        assertEquals(ConnectionState.CONNECTED, client.connectionState.value)
    }

    @Test
    fun `auth flow reaches AUTHENTICATED`() = runBlocking {
        mockServer.enqueue(MockResponse().withWebSocketUpgrade(AuthOkListener()))

        client.connect("127.0.0.1", mockServer.port)
        withTimeout(3000) {
            client.connectionState.first { it == ConnectionState.CONNECTED }
        }

        val response = withTimeout(3000) {
            client.authenticate("1234")
        }
        assertTrue(response.success)
        assertEquals(ConnectionState.AUTHENTICATED, client.connectionState.value)
    }

    @Test
    fun `auth failure keeps CONNECTED state`() = runBlocking {
        mockServer.enqueue(MockResponse().withWebSocketUpgrade(AuthFailListener()))

        client.connect("127.0.0.1", mockServer.port)
        withTimeout(3000) {
            client.connectionState.first { it == ConnectionState.CONNECTED }
        }

        val response = withTimeout(3000) {
            client.authenticate("0000")
        }
        assertFalse(response.success)
        assertEquals(ConnectionState.CONNECTED, client.connectionState.value)
    }

    @Test
    fun `sendEvent before auth is ignored`() = runBlocking {
        mockServer.enqueue(MockResponse().withWebSocketUpgrade(EchoListener()))

        client.connect("127.0.0.1", mockServer.port)
        withTimeout(3000) {
            client.connectionState.first { it == ConnectionState.CONNECTED }
        }

        // Should not throw, just be silently ignored
        client.sendEvent(RemoteEvent.MouseMove(10, 5))
    }

    @Test
    fun `disconnect transitions to DISCONNECTED`() = runBlocking {
        mockServer.enqueue(MockResponse().withWebSocketUpgrade(EchoListener()))

        client.connect("127.0.0.1", mockServer.port)
        withTimeout(3000) {
            client.connectionState.first { it == ConnectionState.CONNECTED }
        }

        client.disconnect()
        assertEquals(ConnectionState.DISCONNECTED, client.connectionState.value)
    }

    // -- Helper WebSocket listeners -----------------------------------------

    private class EchoListener : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            webSocket.send(text)
        }
    }

    private class AuthOkListener : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            if ("auth" in text) {
                webSocket.send("""{"type":"auth_response","success":true,"message":"Authenticated"}""")
            }
        }
    }

    private class AuthFailListener : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            if ("auth" in text) {
                webSocket.send("""{"type":"auth_response","success":false,"message":"Invalid PIN"}""")
            }
        }
    }
}
