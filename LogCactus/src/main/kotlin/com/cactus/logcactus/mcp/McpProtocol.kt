package com.cactus.logcactus.mcp

import java.util.Base64
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** Результат инструмента: список блоков содержимого MCP (`text` / `image`). */
class McpToolResult(val content: List<JsonObject>, val isError: Boolean = false) {
  companion object {
    fun text(text: String, isError: Boolean = false) = McpToolResult(listOf(textBlock(text)), isError)
    fun image(png: ByteArray, caption: String) = McpToolResult(
      listOf(
        textBlock(caption),
        buildJsonObject {
          put("type", "image")
          put("data", Base64.getEncoder().encodeToString(png))
          put("mimeType", "image/png")
        }
      )
    )

    private fun textBlock(text: String) = buildJsonObject { put("type", "text"); put("text", text) }
  }
}

class McpTool(
  val name: String,
  val description: String,
  val inputSchema: JsonObject,
  val handler: (JsonObject) -> McpToolResult
)

/**
 * Минимальный MCP-сервер поверх JSON-RPC 2.0 (Streamable HTTP, без SSE): `initialize`, `ping`, `tools/list`, `tools/call`.
 * Транспорта не знает — [handle] принимает тело запроса и возвращает тело ответа (null — ответа нет, это уведомление).
 * Без официального SDK: он требует Kotlin 2.x, а приложение привязано к 1.9 из-за Compose.
 */
class McpProtocol(
  private val serverName: String,
  private val serverVersion: String,
  private val instructions: String,
  tools: List<McpTool>
) {
  private val tools = tools.associateBy { it.name }.also { require(it.size == tools.size) { "duplicate tool names" } }
  private val order = tools.map { it.name }

  fun handle(body: String): String? {
    val root = try {
      Json.parseToJsonElement(body)
    } catch (_: Exception) {
      return error(JsonNull, PARSE_ERROR, "Parse error")
    }
    if (root !is JsonObject) return error(JsonNull, INVALID_REQUEST, "Batch and non-object requests are not supported")
    val method = (root["method"] as? JsonPrimitive)?.contentOrNull
    val id = root["id"]
    // Ответы клиента и уведомления (нет id) подтверждаются пустым телом.
    if (method == null || id == null) return if (method == null && id == null) error(JsonNull, INVALID_REQUEST, "Missing method") else null
    if (id !is JsonPrimitive) return error(JsonNull, INVALID_REQUEST, "Invalid id")
    val params = root["params"] as? JsonObject ?: JsonObject(emptyMap())
    return try {
      when (method) {
        "initialize" -> result(id, initialize(params))
        "ping" -> result(id, JsonObject(emptyMap()))
        "tools/list" -> result(id, buildJsonObject { put("tools", toolList()) })
        "tools/call" -> result(id, callTool(params) ?: return error(id, INVALID_PARAMS, "Unknown tool"))
        else -> error(id, METHOD_NOT_FOUND, "Method not found: $method")
      }
    } catch (e: Exception) {
      error(id, INTERNAL_ERROR, e.message ?: e.javaClass.simpleName)
    }
  }

  private fun initialize(params: JsonObject): JsonObject {
    val requested = (params["protocolVersion"] as? JsonPrimitive)?.contentOrNull
    return buildJsonObject {
      put("protocolVersion", if (requested in SUPPORTED_VERSIONS) requested!! else SUPPORTED_VERSIONS.first())
      put("capabilities", buildJsonObject { put("tools", buildJsonObject { }) })
      put("serverInfo", buildJsonObject { put("name", serverName); put("version", serverVersion) })
      put("instructions", instructions)
    }
  }

  private fun toolList(): JsonArray = buildJsonArray {
    order.forEach { name ->
      val t = tools.getValue(name)
      add(buildJsonObject { put("name", t.name); put("description", t.description); put("inputSchema", t.inputSchema) })
    }
  }

  private fun callTool(params: JsonObject): JsonObject? {
    val tool = tools[(params["name"] as? JsonPrimitive)?.contentOrNull] ?: return null
    val args = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())
    // Сбой инструмента — это результат с isError (его увидит модель), а не ошибка протокола.
    val out = try {
      tool.handler(args)
    } catch (e: Exception) {
      McpToolResult.text("Tool failed: ${e.message ?: e.javaClass.simpleName}", isError = true)
    }
    return buildJsonObject {
      put("content", JsonArray(out.content))
      put("isError", out.isError)
    }
  }

  private fun result(id: JsonElement, result: JsonElement): String =
    buildJsonObject { put("jsonrpc", "2.0"); put("id", id); put("result", result) }.toString()

  private fun error(id: JsonElement, code: Int, message: String): String =
    buildJsonObject {
      put("jsonrpc", "2.0")
      put("id", id)
      put("error", buildJsonObject { put("code", code); put("message", message) })
    }.toString()

  companion object {
    val SUPPORTED_VERSIONS = listOf("2025-06-18", "2025-03-26", "2024-11-05")
    private const val PARSE_ERROR = -32700
    private const val INVALID_REQUEST = -32600
    private const val METHOD_NOT_FOUND = -32601
    private const val INVALID_PARAMS = -32602
    private const val INTERNAL_ERROR = -32603
  }
}
