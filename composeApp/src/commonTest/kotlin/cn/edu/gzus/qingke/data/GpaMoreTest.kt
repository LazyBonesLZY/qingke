package cn.edu.gzus.qingke.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
