package com.cactus.logcactus.application.logcat

import com.cactus.logcactus.domain.logcat.QueryHistoryRepository

class QueryHistoryService(
  private val repository: QueryHistoryRepository,
  private val limit: Int
) {
  fun load(): List<String> = repository.load(limit)

  fun loadFavorites(): Set<String> = repository.loadFavorites()

  // В реальном Logcat запись в историю происходит не на каждый ввод символа,
  // а по конкретным событиям: Enter, потеря фокуса поля, переключение
  // избранного (FilterTextField.kt — focusLost/keyPressed(VK_ENTER)/addToHistory()).
  fun push(current: List<String>, value: String): List<String> {
    if (value.isBlank()) return current
    val normalized = value.trim()
    val next = ArrayList<String>(limit)
    next.add(normalized)
    current.forEach {
      if (it != normalized && next.size < limit) next.add(it)
    }
    repository.save(next)
    return next
  }

  fun toggleFavorite(favorites: Set<String>, value: String): Set<String> {
    if (value.isBlank()) return favorites
    val normalized = value.trim()
    val next = if (favorites.contains(normalized)) favorites - normalized else favorites + normalized
    repository.saveFavorites(next)
    return next
  }

  fun remove(current: List<String>, value: String): List<String> {
    val next = current.filterNot { it == value }
    repository.save(next)
    return next
  }

  fun clear(): List<String> {
    repository.save(emptyList())
    return emptyList()
  }
}
