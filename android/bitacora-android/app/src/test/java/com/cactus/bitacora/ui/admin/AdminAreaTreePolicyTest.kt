package com.cactus.bitacora.ui.admin

import com.cactus.bitacora.model.AreaTreeNodeOut
import com.cactus.bitacora.model.EmployeeAreaTreeNodeOut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun construye_filas_del_arbol_solo_con_id_area_y_nodo_padre_ignorando_nivel() {
        val agrigolaCactus = area(10006, null, 99, "Agrícola Cactus")
        val gerencia = area(10011, 10006, 99, "Gerencia")
        val finca1 = area(10017, 10011, 99, "finca 1")
        val f2CultivoRosa = area(10019, 10017, 99, "F2 Cultivo Rosa")
        val finca2 = area(10018, 10011, 99, "Finca 2")
        val finca3 = area(10014, 10011, 99, "finca 3")
        val finca4 = area(10016, 10011, 99, "finca 4")
        val areas = listOf(
            finca2, agrigolaCactus, finca3, gerencia, finca4, finca1, f2CultivoRosa
        )
        val expandedAll = areas.map { it.id_area }.toSet()

        val rows = buildAreaTreeRows(areas, expandedAll)

        assertEquals(
            listOf(10006, 10011, 10017, 10019, 10018, 10014, 10016),
            rows.map { it.area.id_area }
        )
        assertEquals(
            listOf(0, 1, 2, 3, 2, 2, 2),
            rows.map { it.depth }
        )
    }

    @Test
    fun resumen_solo_lectura_reproduce_preorden_simbolos_sangria_y_referencia() {
        val areas = listOf(
            area(10, null, 0, "Raíz", "RZ"),
            area(12, 10, 1, "Zeta", "ZT"),
            area(11, 10, 1, "Alfa", "AL")
        )

        val rows = buildAreaTreeRows(areas, setOf(10))

        assertEquals(listOf(10, 11, 12), rows.map { it.area.id_area })
        assertEquals(listOf(0, 1, 1), rows.map(AreaTreeRow::depth))
        assertEquals(listOf("● Raíz", "   └─ Alfa", "   └─ Zeta"), rows.map(::areaSummaryLabel))
        assertEquals(listOf("RZ", "AL", "ZT"), rows.map { it.area.nombre_corto })
    }

    @Test
    fun resumen_y_arbol_comparten_repliegue_sin_cambiar_hojas() {
        val areas = listOf(
            area(10, null, 0, "Raíz", "RZ"),
            area(11, 10, 1, "Hija", "HJ")
        )
        val expanded = setOf(10)
        val expandedRoot = buildAreaTreeRows(areas, expanded).first()

        val collapsed = toggleExpandedArea(expanded, expandedRoot)
        val collapsedRows = buildAreaTreeRows(areas, collapsed)
        val expandedAgain = toggleExpandedArea(collapsed, collapsedRows.first())
        val leaf = buildAreaTreeRows(areas, expandedAgain).last()

        assertEquals(emptySet<Int>(), collapsed)
        assertEquals(listOf(10), collapsedRows.map { it.area.id_area })
        assertEquals(expanded, expandedAgain)
        assertEquals(expandedAgain, toggleExpandedArea(expandedAgain, leaf))
    }

    @Test
    fun valida_referencia_y_descripcion_sin_rechazar_el_codigo_propio() {
        val catalog = listOf(
            area(1, null, 0, "Raíz", "ÁREA"),
            area(2, 1, 1, "Hija", "HIJA")
        )

        assertEquals(
            "Ya existe un área con esa Referencia",
            adminAreaFormError(catalog, null, "Otra", "area", null)
        )
        assertEquals(
            "Ya existe un área con esa descripción bajo el mismo padre",
            adminAreaFormError(catalog, null, " hija ", "NUEVA", 1)
        )
        assertNull(adminAreaFormError(catalog, 1, "Raíz", "ÁREA", null))
        assertEquals(
            "La Referencia es obligatoria",
            adminAreaFormError(catalog, null, "Nueva", "   ", null)
        )
        assertEquals(
            "La Referencia admite máximo 25 caracteres",
            adminAreaFormError(catalog, null, "Nueva", "A".repeat(26), null)
        )
    }

    private fun area(
        id: Int,
        parentId: Int?,
        level: Int,
        description: String,
        reference: String = "R$id"
    ) = AreaTreeNodeOut(
        id_area = id,
        descripcion = description,
        nombre_corto = reference,
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
