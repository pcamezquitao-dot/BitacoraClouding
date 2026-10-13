package com.cactus.bitacora.ui.admin

import com.cactus.bitacora.model.NavigationAdminOut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationAdminPolicyTest {
    private fun item(id: Int, name: String, parent: Int? = null) = NavigationAdminOut(id, 1, name, null, parent, null, null, null, "f$id")

    @Test fun treeOrdersAndCollapsesDescendants() {
        val actions = listOf(item(3, "Zeta", 1), item(2, "Alfa", 1), item(1, "Raíz"), item(4, "Nieta", 2))
        val collapsed = buildNavigationTreeRows(actions, emptySet())
        assertEquals(listOf(1), collapsed.map { it.action.id_accion })
        val open = buildNavigationTreeRows(actions, setOf(1, 2))
        assertEquals(listOf(1, 2, 4, 3), open.map { it.action.id_accion })
        assertEquals(listOf(0, 1, 2, 1), open.map { it.depth })
    }

    @Test fun descendantsPreventCycle() {
        val actions = listOf(item(1, "Raíz"), item(2, "Hija", 1), item(3, "Nieta", 2))
        assertEquals(setOf(2, 3), navigationDescendantIds(actions, 1))
        assertFalse(navigationDescendantIds(actions, 3).contains(1))
    }

    @Test fun orphanRemainsVisibleAsInconsistent() {
        val rows = buildNavigationTreeRows(listOf(item(7, "Huérfana", 99)), emptySet())
        assertEquals(1, rows.size)
        assertTrue(rows.single().inconsistent)
    }
}
