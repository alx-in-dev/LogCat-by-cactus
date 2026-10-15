package com.cactus.logcactus.domain.logcat

interface QueryHistoryRepository {
  fun load(limit: Int): List<String>

  fun save(items: List<String>)

  fun loadFavorites(): Set<String>

  fun saveFavorites(favorites: Set<String>)
}
