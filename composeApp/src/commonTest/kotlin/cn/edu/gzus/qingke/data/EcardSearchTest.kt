package cn.edu.gzus.qingke.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EcardSearchTest {
    private val row = EcardRoomRow(
        areaId = "82",
        areaName = "江门校区",
        buildingId = "B12",
        buildingName = "北苑12栋",
        roomId = "1203",
        roomName = "1203",
    )

    @Test
    fun matchesPlainText() {
        assertTrue(row.matches("北苑12"))
        assertTrue(row.matches("b12"))
    }

    @Test
    fun matchesFullWidthAcrossBuildingAndRoom() {
        assertTrue(row.matches("北苑１２栋１２０３"))
        assertTrue(row.matches("北苑12栋 1203"))
    }

    @Test
    fun rejectsOtherRoomsAndBlankQueries() {
        assertFalse(row.matches("南苑"))
        assertFalse(row.matches("   "))
    }

    @Test
    fun foldedRoomNameMatchesFoldRoomKey() {
        assertEquals(foldRoomKey(row.roomName), row.foldedRoomName)
    }
}