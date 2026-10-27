package cn.edu.gzus.qingke.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GpaTest {
    @Test
    fun gradeLevelsFollowTheScoreFormula() {
        assertEquals(gpaFromScore("95"), gpaFromScore("优秀"))
        assertEquals(gpaFromScore("85"), gpaFromScore("良好"))
        assertEquals(gpaFromScore("75"), gpaFromScore("中等"))
        assertEquals(gpaFromScore("65"), gpaFromScore("及格"))
        assertEquals(0.0, gpaFromScore("不及格"))
    }

    @Test
    fun parsesTermLabelsFromEachSchool() {
        assertEquals(2026 to 1, gradeTermKey("2026-2027 1"))
        assertEquals(2026 to 2, gradeTermKey("2026-2027-2"))
        assertNull(gradeTermKey(""))
        assertEquals(2026 to 1, AppSettings(yearCode = "2026", termCode = "3").currentTermKey())
        assertEquals(2026 to 2, AppSettings(yearCode = "2026", termCode = "12").currentTermKey())
        assertNull(AppSettings(yearCode = "", termCode = "").currentTermKey())
    }

    @Test
    fun termFilterDropsLastTermRetakeRecords() {
        val old = GradeItem(courseId = "A", courseName = "A", credit = "2", score = "45", term = "2025-2026 2")
        val now = GradeItem(courseId = "B", courseName = "B", credit = "2", score = "90", term = "2026-2027 1")
        val unknown = GradeItem(courseId = "C", courseName = "C", credit = "2", score = "80", term = "")
        assertEquals(listOf(now, unknown), listOf(old, now, unknown).inTerm(2026 to 1))
        assertEquals(listOf(old, now, unknown), listOf(old, now, unknown).inTerm(null))
    }
}
