package com.gonzaroman.acornix.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShortcutPlanTest {
    @Test
    fun readsAPlanInsideTheAnswer() {
        val plan = ShortcutPlan.parse("""Here it is:
            ```json
            {"name": "Modo cine", "emoji": "🎬", "steps": [
              {"action": "ringer", "params": {"mode": "silent"}},
              {"action": "if", "params": {"left": "{batería}", "op": "less", "right": "30"}},
              {"action": "brightness", "params": {"level": "10"}},
              {"action": "endif"}
            ]}
            ```""")
        assertEquals("Modo cine", plan?.name)
        assertEquals(4, plan?.steps?.size)
    }

    @Test
    fun rejectsAppsAndBrokenPlans() {
        assertNull(ShortcutPlan.parse("""{"steps": null}"""))
        assertNull(ShortcutPlan.parse("""{"steps": [{"action": "hack", "params": {}}]}"""))
        assertNull(ShortcutPlan.parse("""{"steps": [{"action": "if", "params": {}}]}"""))
        assertNull(ShortcutPlan.parse("no json"))
    }
}
