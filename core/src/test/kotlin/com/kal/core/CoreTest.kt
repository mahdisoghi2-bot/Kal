package com.kal.core

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class CoreTest {
    @Test fun grouping() = assertEquals("۱\u066C۷۰۰", Persian.format(1700))
    @Test fun rollover() = assertEquals(
        LocalDateTime.of(2026, 3, 9, 1, 30).minusDays(1).toLocalDate(),
        Calories.dayKey(LocalDateTime.of(2026, 3, 9, 1, 30)))
    @Test fun wedge() = assertEquals(0.5f, Calories.wedgeFill(2, 0.25f))
    @Test fun round5() = assertEquals(620, Calories.roundTo5(622))
    @Test fun ripe() = assertEquals(0f, Ripening.ripeness(0.5f))
}
