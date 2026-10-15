package com.cactus.logcactus.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class McpProtocolTest {
  private val echo = McpTool("echo", "Echoes", Json.parseToJsonElement("""{"type":"object"}""").jsonObject) { a ->
    McpToolResult.text("hi " + (a["name"]?.jsonPrimitive?.content ?: "?"))
  }
  private val boom = McpTool("boom", "Fails", Json.parseToJsonElement("""{"type":"object"}""").jsonObject) { error("kaput") }
  private val p = McpProtocol("t", "1", "instr", listOf(echo, boom))

  private fun call(body: String): JsonObject = Json.parseToJsonElement(p.handle(body)!!).jsonObject

  @Test fun initializeEchoesSupportedVersionAndAdvertisesTools() {
    val r = call("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26"}}""")
    assertEquals("1", r["id"]!!.jsonPrimitive.content)
    val res = r["result"]!!.jsonObject
    assertEquals("2025-03-26", res["protocolVersion"]!!.jsonPrimitive.content)
    assertTrue(res["capabilities"]!!.jsonObject.containsKey("tools"))
    assertEquals("instr", res["instructions"]!!.jsonPrimitive.content)
  }

  @Test fun unknownProtocolVersionFallsBackToLatest() {
    val r = call("""{"jsonrpc":"2.0","id":"a","method":"initialize","params":{"protocolVersion":"1999-01-01"}}""")
    assertEquals(McpProtocol.SUPPORTED_VERSIONS.first(), r["result"]!!.jsonObject["protocolVersion"]!!.jsonPrimitive.content)
    assertEquals("a", r["id"]!!.jsonPrimitive.content)
  }

  @Test fun notificationGetsNoResponse() {
    assertNull(p.handle("""{"jsonrpc":"2.0","method":"notifications/initialized"}"""))
  }

  @Test fun toolsListKeepsOrder() {
    val names = call("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""")["result"]!!.jsonObject["tools"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content }
    assertEquals(listOf("echo", "boom"), names)
  }

  @Test fun toolCallPassesArguments() {
    val r = call("""{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"echo","arguments":{"name":"Bob"}}}""")["result"]!!.jsonObject
    assertEquals("hi Bob", r["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
    assertFalse(r["isError"]!!.jsonPrimitive.content.toBoolean())
  }

  @Test fun toolFailureIsResultNotProtocolError() {
    val r = call("""{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"boom"}}""")
    assertFalse(r.containsKey("error"))
    assertTrue(r["result"]!!.jsonObject["isError"]!!.jsonPrimitive.content.toBoolean())
  }

  @Test fun unknownToolAndMethodAndGarbage() {
    assertEquals(-32602, call("""{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"nope"}}""")["error"]!!.jsonObject["code"]!!.jsonPrimitive.content.toInt())
    assertEquals(-32601, call("""{"jsonrpc":"2.0","id":6,"method":"resources/list"}""")["error"]!!.jsonObject["code"]!!.jsonPrimitive.content.toInt())
    assertEquals(-32700, call("{nope")["error"]!!.jsonObject["code"]!!.jsonPrimitive.content.toInt())
    assertEquals(-32600, call("[]")["error"]!!.jsonObject["code"]!!.jsonPrimitive.content.toInt())
  }

  @Test fun hostAndOriginChecks() {
    assertTrue(McpHttpServer.isLocalHost("127.0.0.1:7788"))
    assertTrue(McpHttpServer.isLocalHost("localhost"))
    assertTrue(McpHttpServer.isLocalHost("[::1]:7788"))
    assertFalse(McpHttpServer.isLocalHost("evil.example:7788"))
    assertFalse(McpHttpServer.isLocalHost(null))
    assertTrue(McpHttpServer.isLocalOrigin(null))
    assertTrue(McpHttpServer.isLocalOrigin("http://localhost:3000"))
    assertFalse(McpHttpServer.isLocalOrigin("https://evil.example"))
  }
}
