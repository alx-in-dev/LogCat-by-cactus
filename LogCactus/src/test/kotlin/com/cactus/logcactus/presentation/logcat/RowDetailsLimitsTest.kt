package com.cactus.logcactus.presentation.logcat

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RowDetailsLimitsTest {
  @Test
  fun deeplyNestedJsonIsRejectedInsteadOfExhaustingMemory() {
    val msg = "Body: " + "[".repeat(15_000)
    assertNull(RowDetails.expansion(msg))
  }

  @Test
  fun ordinaryNestedJsonStillWorks() {
    assertNotNull(RowDetails.expansion("""Body: {"a":{"b":[1,2,{"c":3}]}}"""))
  }
}
