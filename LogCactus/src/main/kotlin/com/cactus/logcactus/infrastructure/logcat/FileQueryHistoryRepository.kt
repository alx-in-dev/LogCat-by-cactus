package com.cactus.logcactus.infrastructure.logcat

import com.cactus.logcactus.domain.logcat.QueryHistoryRepository
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class FileQueryHistoryRepository(
  private val filePath: Path = Paths.get(System.getProperty("user.home"), ".logcactus", "query-history.txt"),
  private val favoritesPath: Path = Paths.get(System.getProperty("user.home"), ".logcactus", "favorite-filters.txt")
) : QueryHistoryRepository {
  override fun load(limit: Int): List<String> {
    if (!Files.exists(filePath)) return emptyList()
    return runCatching {
      Files.readAllLines(filePath, StandardCharsets.UTF_8)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .take(limit)
    }.getOrDefault(emptyList())
  }

  override fun save(items: List<String>) {
    runCatching {
      filePath.parent?.let { Files.createDirectories(it) }
      com.cactus.logcactus.standalone.AtomicFiles.writeLines(filePath, items)
    }
  }

  override fun loadFavorites(): Set<String> {
    if (!Files.exists(favoritesPath)) return emptySet()
    return runCatching {
      Files.readAllLines(favoritesPath, StandardCharsets.UTF_8)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()
    }.getOrDefault(emptySet())
  }

  override fun saveFavorites(favorites: Set<String>) {
    runCatching {
      favoritesPath.parent?.let { Files.createDirectories(it) }
      com.cactus.logcactus.standalone.AtomicFiles.writeLines(favoritesPath, favorites.toList())
    }
  }
}
