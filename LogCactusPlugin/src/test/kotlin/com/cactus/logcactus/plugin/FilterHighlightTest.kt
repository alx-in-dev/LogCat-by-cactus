package com.cactus.logcactus.plugin

import com.cactus.logcactus.plugin.ui.FilterRole
import com.cactus.logcactus.plugin.ui.FilterSpan
import com.cactus.logcactus.plugin.ui.highlightFilter
import org.junit.Test
import org.junit.Assert.assertEquals

class FilterHighlightTest {
  private fun roles(text: String) = highlightFilter(text).map { Triple(it.role, it.start, it.end) }

  @Test fun keyAndValue() = assertEquals(
    listOf(FilterSpan(FilterRole.KEY, 0, 3), FilterSpan(FilterRole.OPERATOR, 3, 4), FilterSpan(FilterRole.VALUE, 4, 7)),
    highlightFilter("tag:foo")
  )

  @Test fun negationAndRegexOperator() = assertEquals(
    listOf(
      FilterSpan(FilterRole.NEGATION, 0, 1), FilterSpan(FilterRole.KEY, 1, 8), FilterSpan(FilterRole.OPERATOR, 8, 9),
      FilterSpan(FilterRole.OPERATOR, 9, 10), FilterSpan(FilterRole.VALUE, 10, 13)
    ),
    highlightFilter("-message~:a.b")
  )

  @Test fun quotedValueWithSpaceStaysOneToken() = assertEquals(
    listOf(FilterSpan(FilterRole.KEY, 0, 7), FilterSpan(FilterRole.OPERATOR, 7, 8), FilterSpan(FilterRole.QUOTED, 8, 13)),
    highlightFilter("message:\"a b\"")
  )

  @Test fun logicAndParens() = assertEquals(
    listOf(FilterRole.PAREN, FilterRole.KEY, FilterRole.OPERATOR, FilterRole.VALUE, FilterRole.LOGIC, FilterRole.KEY, FilterRole.OPERATOR, FilterRole.VALUE, FilterRole.PAREN),
    highlightFilter("(tag:a||level:W)").map { it.role }
  )

  @Test fun unknownKeyIsPlainText() = assertEquals(listOf(FilterRole.VALUE), highlightFilter("http://x").map { it.role })

  @Test fun unclosedQuoteDoesNotThrow() = assertEquals(
    listOf(FilterRole.KEY, FilterRole.OPERATOR, FilterRole.QUOTED), highlightFilter("tag:\"ab").map { it.role }
  )

  @Test fun emptyAndSpaces() = assertEquals(emptyList<Triple<FilterRole, Int, Int>>(), roles("   "))
}
