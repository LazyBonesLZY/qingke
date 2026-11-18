package cn.edu.gzus.qingke.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GpaMoreTest {
    @Test fun recognizesChineseSemesterNames() {
        assertEquals(2026 to 1, gradeTermKey("2026-2027学年第一学期"))
        assertEquals(2026 to 2, gradeTermKey("2026-2027学年第2学期"))
        assertEquals(2026 to 3, gradeTermKey("2026-2027第3学期"))
        assertEquals(2026 to 1, gradeTermKey("2026-2027上学期"))
        assertEquals(2026 to 2, gradeTermKey("2026-2027下学期"))
        assertEquals(2026 to 1, gradeTermKey("2026-2027-1"))
        assertNull(gradeTermKey("2026-2027"))
        assertNull(gradeTermKey("2026-2027第4学期"))
    }

    @Test fun linksKingosoftSlotsAndGradesByCourseNameWhenIdsDiffer() {
        val slot = LessonSlot(
            courseId = "20262027100123",
            courseName = "高等数学Ⅰ",
            credit = "4",
            teacher = "张老师",
            room = "A101",
            weekday = 1,
            weekdayName = "星期一",
            period = "1-2",
            periodLabel = "1-2节",
            weeks = "1-16周",
        )
        val grade = GradeItem(
            courseId = "0401001",
            courseName = "高等数学Ⅰ",
            credit = "4",
            score = "92",
            gpa = "4.2",
            term = "2026-2027-1",
        )
        val snapshot = AppSnapshot(slots = listOf(slot), grades = listOf(grade))

        assertTrue(grade.matchesCourse(slot.courseId, slot.courseName))
        assertEquals("92", snapshot.bestGradeForCourse(slot.courseId)?.score)
        assertEquals("92", snapshot.bestGradeForCourse(grade.courseId)?.score)

        val detailFromGrade = resolveCourseDetail(snapshot, grade.courseId)
        assertNotNull(detailFromGrade)
        assertEquals("0401001", detailFromGrade.courseId)
        assertEquals(1, detailFromGrade.slots.size)
        assertEquals("A101", detailFromGrade.slots.first().room)
    }
}
