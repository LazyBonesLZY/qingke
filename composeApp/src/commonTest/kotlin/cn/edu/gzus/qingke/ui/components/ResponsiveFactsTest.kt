package cn.edu.gzus.qingke.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class ResponsiveFactsTest {
    @Test fun longTeachingClassUsesFullRow() {
        val rows = responsiveFactRows(listOf(
            "学分" to "3",
            "考核" to "考试",
            "校区" to "江门校区",
            "教学班" to "计算机科学与技术（端侧AI产业班）2026级01班",
        ))
        assertEquals(listOf(2, 1, 1), rows.map { it.size })
        assertEquals("教学班", rows.last().single().first)
    }

    @Test fun emptyValuesAreSkippedAndOriginalOrderIsRetained() {
        val rows = responsiveFactRows(listOf(
            "学分" to "3", "空" to "", "考核" to "考试",
            "地点" to "广东省江门市新会区某教学楼某间教室",
            "学时" to "48",
        ))
        assertEquals(listOf("学分", "考核", "地点", "学时"), rows.flatten().map { it.first })
        assertEquals(listOf(2, 1, 1), rows.map { it.size })
    }
}
