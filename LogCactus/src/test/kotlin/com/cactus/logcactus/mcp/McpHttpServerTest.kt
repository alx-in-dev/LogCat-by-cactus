package com.cactus.logcactus.mcp

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class McpHttpServerTest {
  private lateinit var server: McpHttpServer
  private val client = HttpClient.newHttpClient()

  @BeforeEach fun up() {
    server = McpHttpServer(McpProtocol("t", "1", "i", emptyList()), 0)
    server.start()
  }

  @AfterEach fun down() = server.stop()

  private fun post(body: String, origin: String? = null, method: String = "POST"): HttpResponse<String> {
    val b = HttpRequest.newBuilder(URI("http://127.0.0.1:${server.port}/mcp")).method(method, HttpRequest.BodyPublishers.ofString(body))
    origin?.let { b.header("Origin", it) }
    return client.send(b.build(), HttpResponse.BodyHandlers.ofString())
  }

  @Test fun initializeOverHttp() {
    val r = post("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""")
    assertEquals(200, r.statusCode())
    assertTrue(r.headers().firstValue("Content-Type").get().startsWith("application/json"))
    assertTrue(r.body().contains("\"serverInfo\""))
  }

  @Test fun notificationIs202() = assertEquals(202, post("""{"jsonrpc":"2.0","method":"notifications/initialized"}""").statusCode())

  @Test fun getIs405AndForeignOriginIs403() {
    assertEquals(405, post("", method = "GET").statusCode())
    assertEquals(403, post("""{"jsonrpc":"2.0","id":1,"method":"ping"}""", origin = "https://evil.example").statusCode())
  }
}
