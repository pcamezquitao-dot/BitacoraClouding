package com.cactus.bitacora.ui.admin

import com.cactus.bitacora.model.AreaTreeNodeOut
import com.cactus.bitacora.model.EmployeeAreaTreeNodeOut
import org.junit.Assert.assertEquals
import org.junit.Test

class AdminAreaTreePolicyTest {
    private val areas = listOf(
        area(1, null, 0, "Raíz"),
        area(2, 1, 1, "Hija"),
        area(3, 2, 2, "Nieta"),
        area(4, 1, 1, "Otra hija")
    )

    @Test
    fun muestra_todo_el_directorio_cuando_los_padres_estan_expandidos() {
        val visible = visibleAreaTreeNodes(areas, setOf(1, 2))

        assertEquals(listOf(1, 2, 3, 4), visible.map(AreaTreeNodeOut::id_area))
    }

    @Test
    fun oculta_todos_los_descendientes_de_un_directorio_contraido() {
        val visible = visibleAreaTreeNodes(areas, emptySet())

        assertEquals(listOf(1), visible.map(AreaTreeNodeOut::id_area))
    }

    @Test
    fun conserva_hermanos_visibles_al_contraer_una_rama_interna() {
        val visible = visibleAreaTreeNodes(areas, setOf(1))

        assertEquals(listOf(1, 2, 4), visible.map(AreaTreeNodeOut::id_area))
    }

    @Test
    fun acepta_como_raiz_padre_nulo_o_cero() {
        val roots = listOf(
            area(10, null, 0, "Raíz nula"),
            area(11, 0, 0, "Raíz cero")
        )

        assertEquals(listOf(10, 11), visibleAreaTreeNodes(roots, emptySet()).map { it.id_area })
    }

    @Test
    fun identifica_area_y_descendientes_sin_padre_valido() {
        val withOrphan = areas + listOf(
            area(20, 999, 0, "Huérfana"),
            area(21, 20, 1, "Hija de huérfana")
        )

        assertEquals(
            listOf(20, 21),
            orphanAreaTreeNodes(withOrphan).map(AreaTreeNodeOut::id_area)
        )
    }

    @Test
    fun un_ciclo_no_produce_recursion_infinita_y_se_reporta_aparte() {
        val cycle = listOf(
            area(30, 31, 0, "Ciclo A"),
            area(31, 30, 0, "Ciclo B")
        )

        assertEquals(listOf(30, 31), orphanAreaTreeNodes(cycle).map { it.id_area })
        assertEquals(emptyList<Int>(), visibleAreaTreeNodes(cycle, setOf(30, 31)).map { it.id_area })
    }

    @Test
    fun selector_de_padre_excluye_todos_los_descendientes() {
        assertEquals(setOf(2, 3, 4), descendantAreaIds(areas, 1))
        assertEquals(setOf(3), descendantAreaIds(areas, 2))
        assertEquals(emptySet<Int>(), descendantAreaIds(areas, 3))
    }

    @Test
    fun aplana_arbol_empleado_area_conservando_orden_jerarquico() {
        val child = employeeAreaNode(2, 1, 1, "Hija")
        val root = employeeAreaNode(1, null, 0, "Raíz", listOf(child))

        assertEquals(
            listOf(1, 2),
            flattenEmployeeAreaTree(listOf(root)).map { it.id_area }
        )
    }

    private fun area(
        id: Int,
        parentId: Int?,
        level: Int,
        description: String
    ) = AreaTreeNodeOut(
        id_area = id,
        descripcion = description,
        id_padre = parentId,
        nivel = level,
        ruta = description
    )

    private fun employeeAreaNode(
        id: Int,
        parentId: Int?,
        level: Int,
        description: String,
        children: List<EmployeeAreaTreeNodeOut> = emptyList()
    ) = EmployeeAreaTreeNodeOut(
        id_area = id,
        descripcion = description,
        nodo_padre = parentId,
        nivel = level,
        ruta = description,
        cantidad_participantes = 0,
        hijos = children
    )
}
