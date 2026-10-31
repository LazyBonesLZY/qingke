package cn.edu.gzus.qingke.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 中山学院（强智 jsxsd）解析回归。
 *
 * 夹具是从真机课表页抄下来的片段，保留了空格子、多门课同格、`&nbsp;` 填充这几样
 * 最容易翻车的结构，不联网也能跑。
 */
class ZscParsingTest {
    private val timetableHtml = """
        <table id="kbtable" class="Nsb_r_list Nsb_table">
        <tr>
        <th>&nbsp;</th><th>星期一</th><th>星期二</th><th>星期三</th><th>星期四</th><th>星期五</th><th>星期六</th><th>星期日</th>
        </tr>
        <tr>
        <th width="70" height="28" align="center">
        第1，2节&nbsp;
        </th>
        <td width="123" height="28" align="center" valign="top">
        <input type="hidden" name="jx0415zbdiv_1" value="2C81-1-1">
        <input type="hidden" name="jx0415zbdiv_2" value="2C81-1-2">
        <div id="2C81-1-1" class="kbcontent1">英语Ⅰ<br/><font title='周次(节次)'>1-8,11-18(周)</font><br/><font title='教室'>3-102</font><br/></div>
        <div id="2C81-1-2" style="display: none;" class="kbcontent" >英语Ⅰ<br/><font title='老师'>祝燕敏</font><br/><font title='周次(节次)'>1-8,11-18(周)</font><br/><font title='教室'>3-102</font><br/></div>
        </td>
        <td width="123" height="28" align="center" valign="top">
        <div id="2C81-2-1" class="kbcontent1">&nbsp;</div>
        <div id="2C81-2-2" style="display: none;" class="kbcontent">&nbsp;</div>
        </td>
        <td width="123" height="28" align="center" valign="top">
        <div id="2C81-3-1" class="kbcontent1">&nbsp;</div>
        <div id="2C81-3-2" style="display: none;" class="kbcontent">&nbsp;</div>
        </td>
        <td width="123" height="28" align="center" valign="top">
        <div id="2C81-4-1" class="kbcontent1">&nbsp;</div>
        <div id="2C81-4-2" style="display: none;" class="kbcontent">&nbsp;</div>
        </td>
        <td width="123" height="28" align="center" valign="top">
        <div id="2C81-5-1" class="kbcontent1">职业生涯素养与规划<br/></div>
        <div id="2C81-5-2" style="display: none;" class="kbcontent" >职业生涯素养与规划<br/><font title='老师'>刘鹏</font><br/><font title='周次(节次)'>7-8,11-13(周)</font><br/><font title='教室'>4-307</font><br/>----------------<br>职业生涯素养与规划<br/><font title='老师'>刘鹏</font><br/><font title='周次(节次)'>1-4(周)</font><br/><font title='教室'>4-307</font><br/></div>
        </td>
        <td></td><td></td>
        </tr>
        <tr>
        <th width="70" height="28" align="center">
        第3，4节&nbsp;
        </th>
        <td></td><td></td><td></td><td></td><td></td><td></td>
        <td>
        <div id="2C81-7-1" class="kbcontent1">高等数学Ⅰ<br/></div>
        <div id="2C81-7-2" style="display: none;" class="kbcontent" >高等数学Ⅰ<br/><font title='老师'>田继青</font><br/><font title='周次(节次)'>1-8,11-18(周)</font><br/><font title='教室'>厚德楼B204</font><br/></div>
        </td>
        </tr>
        <tr>
        <th>备注:</th>
        <td colspan="7" align="left">军事技能  1-8,11-18周;</td>
        </tr>
        </table>
    """.trimIndent()

    @Test
    fun tableParsesCoursesTeachersWeeksAndSkipsBlankCells() {
        val slots = parseZscTimetable(timetableHtml)
        // 空格子是 &nbsp;，不能算课；备注行也不是课。
        assertEquals(4, slots.size, "实际解析到：${slots.map { it.courseName + "/" + it.weeks }}")

        val english = slots.first { it.courseName == "英语Ⅰ" }
        assertEquals(1, english.weekday)
        assertEquals("1-2", english.period)
        assertEquals("祝燕敏", english.teacher)
        assertEquals("3-102", english.room)
        assertEquals("1-8,11-18", english.weeks)
    }

    @Test
    fun twoCoursesInOneCellAreSplitNotGlued() {
        val friday = parseZscTimetable(timetableHtml).filter { it.weekday == 5 }
        assertEquals(2, friday.size, "同格两门课要切开：${friday.map { it.weeks }}")
        val weeks = friday.map { it.weeks }.toSet()
        assertEquals(setOf("7-8,11-13", "1-4"), weeks)
        assertTrue(friday.all { it.courseName == "职业生涯素养与规划" && it.teacher == "刘鹏" })
    }

    @Test
    fun periodLabelAndSpanComeFromChineseLabels() {
        assertEquals("第1，2节", zscPeriodLabel("第1，2节&nbsp;"))
        assertEquals("1-2", zscPeriodFromLabel("第1，2节"))
        assertEquals("9-10", zscPeriodFromLabel("第9，10节"))
        assertEquals("11-12", zscPeriodFromLabel("第11，12节"))
        assertEquals("5", zscPeriodFromLabel("第5节"))
        // 表头和备注不是节次行，必须让调用方跳过。
        assertEquals("", zscPeriodFromLabel("星期一"))
        assertEquals("", zscPeriodFromLabel("备注:"))
    }

    @Test
    fun weekCalendarUsesMondayFromTitleNotWeekMinusOne() {
        // 第 0 周是报到军训周，第 1 周才是正式第一周。按 week-1 偏移会整体差一周。
        val html = """
            <table id="kbtable">
            <tr height='28'><td>0</td><td title='2026年08月24'><font color='red'>24</font></td><td title='2026年08月25'>25</td></tr>
            <tr height='28'><td>1</td><td title='2026年08月31'><font color='red'>31</font></td><td title='2026年09月01'>1</td></tr>
            <tr height='28'><td>2</td><td title='2026年09月07'>7</td></tr>
            </table>
        """.trimIndent()
        val weeks = parseZscWeeks(html)
        assertEquals(listOf(0, 1, 2), weeks.map { it.week })
        assertEquals("2026-08-31", weeks.first { it.week == 1 }.monday.toString())
        assertEquals("2026-08-24", weeks.first { it.week == 0 }.monday.toString())
    }

    @Test
    fun weekCalendarSurvivesMissingClosingTags() {
        // 真机页面里只有第一行写了 </tr>，2..N 周的行是 <tr …><td>1</td>…<tr …><td>2</td>…
        // 一路串下去（还夹着 rowspan 的备注格子）。按 </tr> 切行只会解析出第 0 周，
        // 课表日期就整体没得算——这个坑线上踩过一次。
        val html = """
            <table id="kbtable" border="0">
            <tr><th>星期</th><th>星期一</th><th>备注</th></tr>
            <tr height='28'><td>0</td><td title='2026年08月24'>24</td><td align='left' rowspan=211><textarea>周历备注</textarea></td></tr><tr height='28'><td>1</td><td title='2026年08月31'>31</td><td title='2026年09月01'>01</td><tr height='28'><td>2</td><td title='2026年09月07'>07</td><tr height='28'><td>3</td><td title='2026年09月14'>14</td>
            </table>
        """.trimIndent()
        val weeks = parseZscWeeks(html)
        assertEquals(listOf(0, 1, 2, 3), weeks.map { it.week }, "缺 </tr> 时要按 <tr 起头切行")
        assertEquals("2026-09-14", weeks.last().monday.toString())
    }

    @Test
    fun loginPageIsDetectedByEncodedFieldWithoutCaptcha() {
        val login = """
            <form action="/jsxsd/xk/LoginToXk" method="post" id="Form1">
            <input type="text" id="userAccount" value="" />
            <input type="hidden" id="encoded" name="encoded" />
            </form>
        """.trimIndent()
        assertTrue(isZscLoginPage(login))
        assertTrue(!isZscLoginPage("""<div>var userid = '9999999999999';</div>"""))
    }

    @Test
    fun successfulLoginReturnsEmptyBody302AndMustNotBeTreatedAsFailure() {
        // 线上实测：登录成功回 302 + **空 body**，Location 指向 framework/xsMain.jsp。
        // Ktor 不跟随 POST 的 302，所以 bodyAsText() 拿到的是空串——只按 body 判断
        // 就会把登录成功报成「学号或密码不正确」。这个用例锁死这个回归。
        val studentId = "9999999999999"
        assertTrue(
            zscLoginSucceeded(
                status = 302,
                location = "https://jwgln.zsc.edu.cn/jsxsd/framework/xsMain.jsp",
                body = "",
                studentId = studentId,
            ),
            "302 跳 xsMain 且 body 为空，必须判成功",
        )
        // 有的部署直接 200 渲染主页。
        assertTrue(
            zscLoginSucceeded(
                status = 200,
                location = "",
                body = "<html><body>欢迎 $studentId</body></html>",
                studentId = studentId,
            ),
        )
    }

    @Test
    fun wrongPasswordStillFailsWithTip() {
        val illegal = "<title>错误提示页面</title><h3>提示：非法访问！</h3>"
        assertTrue(
            !zscLoginSucceeded(
                status = 200,
                location = "",
                body = illegal,
                studentId = "9999999999999",
            ),
            "200 + 非法访问错误页不能判成功",
        )
        // 重新回登录页也是失败。
        val login = """<form action="/jsxsd/xk/LoginToXk"><input type="hidden" name="encoded"></form>"""
        assertTrue(!zscLoginSucceeded(200, "", login, "9999999999999"))
        assertTrue(!zscLoginSucceeded(302, "https://jwgln.zsc.edu.cn/jsxsd/xk/LoginToXk", "", "9999999999999"))

        assertEquals("学号或密码不正确", zscLoginFailureTip(200, ""))
        // 302 跳到登录页 = 账号问题；跳到别处又没进 xsMain = 跳转异常，不要误导成密码错。
        assertEquals(
            "学号或密码不正确",
            zscLoginFailureTip(302, "https://jwgln.zsc.edu.cn/jsxsd/xk/LoginToXk"),
        )
        assertEquals("登录跳转异常，再试一次", zscLoginFailureTip(302, "https://jwgln.zsc.edu.cn/jsxsd/"))
        assertEquals("教务服务器出错，过一会儿再试", zscLoginFailureTip(502, ""))
    }

    @Test
    fun illegalAccessPageGivesTipInsteadOfWholeHtml() {
        val page = """
            <title>错误提示页面</title>
            <h3>提示：非法访问！</h3>
        """.trimIndent()
        assertEquals("非法访问！", zscErrorTip(page))
        assertEquals("", zscErrorTip("<html><body>ok</body></html>"))
    }

    @Test
    fun schoolPresetCarriesOfficialClock() {
        val school = AppSettings(schoolId = School.Zsc.id).resolved()
        assertTrue(school.hasPeriodClock)
        // 作息照抄校方时间表：第 1 节 08:00，第 12 节 22:10 结束。
        assertEquals("08:00", school.periodBlocks.first().start)
        assertEquals("22:10", school.periodBlocks.last().end)
        assertEquals("08:00", school.periodStartOf("1"))
        assertEquals("22:10", school.periodEndOf("12"))
        // 强智那套走 jsxsd，没有正方自主选课。
        assertEquals("kingosoft", school.kind)
        assertTrue(!school.supportsCoursePick)
        assertTrue(!school.requiresCaptcha)
    }
}
