package cn.edu.gzus.qingke.data

import kotlinx.serialization.json.*
import kotlin.test.*

class LiveOemPolicyTest {
    private val config = OppoFluidCloudConfig("Education.Class", "1.0.0", listOf("999900001"), 10, 20, 30)

    private val granted = """{"code":0,"data":{"enableIntelligentIntent":true,"userAcceptShareIntent":true,
        "querySupportIntent":[{"intentName":"Education.Class","support":true,"version":["1.0.0"]}]}}"""

    @Test fun requiresAllGrantsAndTheConfiguredIntentVersion() {
        assertTrue(oppoFeaturesAllowShare(granted, config))
        val data = Json.parseToJsonElement(granted).jsonObject.getValue("data")
        assertTrue(oppoFeaturesAllowShare(buildJsonObject {
            put("code", 0); put("data", data.toString())
        }.toString(), config))
        for (invalid in listOf(null, "", "{}", "[]", "bad JSON",
            granted.replace("\"code\":0", "\"code\":1"),
            granted.replace("\"userAcceptShareIntent\":true", "\"userAcceptShareIntent\":false"),
            granted.replace("\"enableIntelligentIntent\":true", "\"enableIntelligentIntent\":false"),
            granted.replace("\"support\":true", "\"support\":false"),
            granted.replace("\"querySupportIntent\"", "\"missing\""),
            granted.replace("Education.Class", "Unrelated.Intent"),
            granted.replace("1.0.0", "2.0.0"),
        )) assertFalse(oppoFeaturesAllowShare(invalid, config), invalid)
    }

    @Test fun missingOrInvalidServiceConfigurationNeverEnablesSharing() {
        for (invalid in listOf(config.copy(intentName = ""), config.copy(intentVersion = ""),
            config.copy(serviceIds = emptyList()), config.copy(serviceIds = listOf("")),
            config.copy(upcomingMilestoneCode = -1), config.copy(ongoingMilestoneCode = -1),
            config.copy(endedMilestoneCode = -1))) {
            assertFalse(invalid.complete)
            assertFalse(oppoFeaturesAllowShare(granted, invalid))
        }
    }

    @Test fun cancelledAndReplacedFramesCannotRemoveANewerNotification() {
        val lifecycle = LiveOemLifecycle()
        val old = lifecycle.begin("old:100", 1_000L)
        assertTrue(lifecycle.isCurrent(old, 999L))
        assertFalse(lifecycle.isCurrent(old, 1_000L))
        val next = lifecycle.begin("next:1000", 2_000L)
        assertFalse(lifecycle.isCurrent(old, 500L))
        assertTrue(lifecycle.isCurrent(next, 1_500L))
        lifecycle.cancel()
        assertFalse(lifecycle.isCurrent(next, 1_500L))
        val restarted = lifecycle.begin("next:1000", 2_000L)
        assertFalse(lifecycle.isCurrent(next, 1_500L))
        assertTrue(lifecycle.isCurrent(restarted, 1_500L))
    }

    @Test fun sameCourseNewFrameAndNewPhaseInvalidateSlowReplies() {
        val lifecycle = LiveOemLifecycle()
        val first = lifecycle.begin("course:1000", 2_000L)
        val refreshed = lifecycle.begin("course:1000", 2_000L)
        assertFalse(lifecycle.isCurrent(first, 1_500L))
        val inClass = lifecycle.begin("course:1000", 3_000L)
        assertFalse(lifecycle.isCurrent(refreshed, 2_000L))
        assertTrue(lifecycle.isCurrent(inClass, 2_000L))
    }

    private fun payload(now: Long, countdown: Boolean = true) = Json.parseToJsonElement(buildOppoLiveIntent(
        config, "cn.edu.gzus.qingke", "stable-identifier", "stable-entity", "课\"程\n标题", "A101 · 教室",
        120_000L, 240_000L, now, if (now < 120_000L) 0 else 1, countdown,
    )).jsonObject

    @Test fun localPayloadKeepsServiceIdsAsStringsAndEscapesCourseText() {
        val upcoming = payload(60_000L)
        assertEquals("999900001", upcoming["serviceId"]!!.jsonArray.single().jsonPrimitive.content)
        val entity = upcoming["intentEntity"]!!.jsonObject
        assertEquals("TASK", entity["entityName"]!!.jsonPrimitive.content)
        assertEquals("课\"程\n标题", entity["primary"]!!.jsonObject["title"]!!.jsonArray.first().jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals(10, entity["milestone"]!!.jsonObject["code"]!!.jsonPrimitive.int)
        assertEquals(20, payload(120_000L)["intentEntity"]!!.jsonObject["milestone"]!!.jsonObject["code"]!!.jsonPrimitive.int)
        assertEquals(50, payload(180_000L)["intentEntity"]!!.jsonObject["secondaryData"]!!.jsonObject["progress"]!!.jsonPrimitive.int)
        assertEquals(upcoming["identifier"], payload(180_000L)["identifier"])
    }

    @Test fun disabledCountdownAndExpiredWindowsDoNotCreateStalePrivateCards() {
        val capsule = payload(180_000L, countdown = false)["intentEntity"]!!.jsonObject["capsule"]!!.jsonObject
        assertEquals("上课中", capsule["rightText"]!!.jsonPrimitive.content)
        assertFailsWith<IllegalArgumentException> { payload(240_000L) }
    }
}
