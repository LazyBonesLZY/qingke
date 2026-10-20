package cn.edu.gzus.qingke.data

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class CompactNamesTest {
    @Test
    fun courseNameDropsBracketsAndTrailingOrdinals() {
        assertEquals("嵌入式系", compactCourseName("嵌入式系统设计（实验）II"))
        assertEquals("信号系统", compactCourseName("信号与系统（双语）2"))
        assertEquals("高数", compactCourseName("高等数学A（上）"))
    }

    @Test
    fun roomNameKeepsBuildingAndRoomNumber() {
        assertEquals("北苑5-101", compactRoomName("北苑5-101电教实训室"))
        assertEquals("实训楼201", compactRoomName("实训楼201"))
    }

    @Test
    fun examSortKeyReadsChineseAndIsoDates() {
        val day = LocalDate(2026, 1, 12).toEpochDays().toLong()
        assertEquals(day, examSortKey("2026年1月12日 09:00-11:00"))
        assertEquals(day, examSortKey("2026-01-12(星期一)"))
        assertEquals(Long.MAX_VALUE, examSortKey("待定"))
    }
}