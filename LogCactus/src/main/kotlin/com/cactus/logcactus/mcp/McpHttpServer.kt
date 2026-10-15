package com.cactus.logcactus.mcp

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.Executors

/**
 * HTTP-транспорт MCP на `127.0.0.1`: единственная точка `POST /mcp`. Наружу не слушает; запросы с чужим `Host`/`Origin`
 * отклоняются (защита от DNS-rebinding: страница в браузере не должна дотянуться до логов устройства).
 */
class McpHttpServer(private val protocol: McpProtocol, port: Int) {
  private val server: HttpServer = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0)
  val port: Int get() = server.address.port

  init {
    server.createContext("/mcp") { ex -> ex.use { handle(it) } }
    // Инструменты блокируются на adb и на потоке UI; демон-потоки не мешают выходу приложения.
    server.executor = Executors.newCachedThreadPool { r -> Thread(r, "logcactus-mcp").also { it.isDaemon = true } }
  }

  fun start() = server.start()

  fun stop() {
    server.stop(0)
    (server.executor as? java.util.concurrent.ExecutorService)?.shutdownNow()
  }

  private fun handle(ex: HttpExchange) {
    if (!isLocalHost(ex.requestHeaders.getFirst("Host")) || !isLocalOrigin(ex.requestHeaders.getFirst("Origin"))) {
      return reply(ex, 403, "forbidden")
    }
    if (ex.requestMethod != "POST") {
      ex.responseHeaders.add("Allow", "POST")
      return reply(ex, 405, "POST only")
    }
    val body = ex.requestBody.readNBytes(MAX_BODY + 1)
    if (body.size > MAX_BODY) return reply(ex, 413, "too large")
    val answer = protocol.handle(String(body, Charsets.UTF_8))
    if (answer == null) reply(ex, 202, "") else reply(ex, 200, answer, "application/json")
  }

  private fun reply(ex: HttpExchange, code: Int, text: String, type: String = "text/plain") {
    val bytes = text.toByteArray(Charsets.UTF_8)
    ex.responseHeaders.add("Content-Type", "$type; charset=utf-8")
    if (bytes.isEmpty()) ex.sendResponseHeaders(code, -1) else {
      ex.sendResponseHeaders(code, bytes.size.toLong())
      ex.responseBody.write(bytes)
    }
  }

  companion object {
    private const val MAX_BODY = 1 shl 20
    private val LOCAL = setOf("127.0.0.1", "localhost", "[::1]")

    fun isLocalHost(header: String?): Boolean {
      if (header == null) return false
      val host = if (header.startsWith("[")) header.substringBefore(']') + "]" else header.substringBefore(':')
      return host in LOCAL
    }

    /** Нет заголовка Origin (curl, CLI-клиенты) — можно; есть — только с локального адреса. */
    fun isLocalOrigin(origin: String?): Boolean {
      if (origin == null) return true
      val host = runCatching { URI(origin).host }.getOrNull() ?: return false
      return host in LOCAL || host == "::1"
    }
  }
}
