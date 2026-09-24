package cn.edu.gzus.qingke.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.util.SizeF
import android.util.TypedValue
import android.widget.RemoteViews
import cn.edu.gzus.qingke.MainActivity
import cn.edu.gzus.qingke.data.AppSnapshot
import cn.edu.gzus.qingke.data.LessonSlot
import cn.edu.gzus.qingke.data.WeekdayNames
import cn.edu.gzus.qingke.data.resolved
import cn.edu.gzus.qingke.data.forDate
import cn.edu.gzus.qingke.data.isAdjustOff
import cn.edu.gzus.qingke.data.occupiesBlock
import cn.edu.gzus.qingke.data.lookup
import cn.edu.gzus.qingke.data.mondayOfTeachingWeek
import cn.edu.gzus.qingke.data.combineMillis
import cn.edu.gzus.qingke.data.nextLiveLesson
import cn.edu.gzus.qingke.data.nowDateTime
import cn.edu.gzus.qingke.data.periodEnd
import cn.edu.gzus.qingke.data.periodStart
import cn.edu.gzus.qingke.data.periodClockRange
import cn.edu.gzus.qingke.data.resolvedCurrentWeek
import cn.edu.gzus.qingke.data.shortCourseName
import cn.edu.gzus.qingke.data.weekDateLabel
import cn.edu.gzus.qingke.data.weekdayIndex
import cn.edu.gzus.qingke.shared.R
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.json.Json
import java.io.File

enum class WidgetRange { Day, Week, Month }

enum class WidgetStyle { Solid, Blur, Glass }

object QingkeWidgets {
    private const val PREFS = "qingke_widgets"
    private const val STORE = "qingke-snapshot.json"
    private val ACCENT = 0xFF3482FF.toInt()
    private const val MAX_WIDGET_PIXELS = 1_600_000L
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun styleOf(context: Context, id: Int): WidgetStyle {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("style_$id", "solid")
        return when (raw) {
            "blur" -> WidgetStyle.Blur
            "glass" -> WidgetStyle.Glass
            else -> WidgetStyle.Solid
        }
    }

    fun saveStyle(context: Context, id: Int, style: WidgetStyle) {
        val key = when (style) {
            WidgetStyle.Solid -> "solid"
            WidgetStyle.Blur -> "blur"
            WidgetStyle.Glass -> "glass"
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("style_$id", key).apply()
    }

    fun clear(context: Context, ids: IntArray) {
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        ids.forEach { edit.remove("style_$it") }
        edit.apply()
    }

    fun rangeOf(context: Context, id: Int): WidgetRange {
        val manager = AppWidgetManager.getInstance(context)
        listOf(
            DayWidgetReceiver::class.java to WidgetRange.Day,
            WeekWidgetReceiver::class.java to WidgetRange.Week,
            MonthWidgetReceiver::class.java to WidgetRange.Month,
        ).forEach { (cls, range) ->
            if (id in manager.getAppWidgetIds(ComponentName(context, cls))) return range
        }
        return WidgetRange.Day
    }

    fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        var any = false
        listOf(
            DayWidgetReceiver::class.java to WidgetRange.Day,
            WeekWidgetReceiver::class.java to WidgetRange.Week,
            MonthWidgetReceiver::class.java to WidgetRange.Month,
        ).forEach { (cls, range) ->
            manager.getAppWidgetIds(ComponentName(context, cls)).forEach { id ->
                any = true
                update(context, manager, id, range)
            }
        }
        if (any) scheduleNextTick(context)
    }

    /**
     * 系统给小组件的自动刷新最快也就半小时一次，"上课中 / 下一节"会一直挂着过期的状态。
     * 所以自己在下一个上下课点再叫醒一次。用非精确闹钟，不要额外权限。
     */
    fun scheduleNextTick(context: Context) {
        runCatching {
            val alarms = context.getSystemService(AlarmManager::class.java) ?: return
            val at = nextBoundaryMillis(snapshot(context).resolved().hasPeriodClock) ?: return
            val intent = Intent(context, WidgetTickReceiver::class.java)
                .setAction(ACTION_TICK)
            val pending = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarms.set(AlarmManager.RTC, at, pending)
        }
    }

    /** 今天下一个上课或下课的时刻；今天都过完了就等明天零点过五分。 */
    private fun nextBoundaryMillis(hasClock: Boolean): Long? {
        val now = nowDateTime()
        val nowMs = System.currentTimeMillis()
        val today = now.date
        val next = if (hasClock) {
            (1..16)
                .flatMap { listOf(periodStart("$it-$it"), periodEnd("$it-$it")) }
                .mapNotNull { combineMillis(today, it).takeIf { ms -> ms > nowMs } }
                .minOrNull()
        } else {
            null
        }
        if (next != null) return next + 5_000
        return combineMillis(today.plus(DatePeriod(days = 1)), "00:05")
            .takeIf { it > nowMs }
    }

    fun update(context: Context, manager: AppWidgetManager, id: Int, range: WidgetRange) {
        val options = manager.getAppWidgetOptions(id)
        val (width, height) = widgetBitmapSize(context, options)
        val bitmap = render(context, range, styleOf(context, id), width, height)
        val views = RemoteViews(context.packageName, R.layout.qingke_widget)
        views.setInt(R.id.qingke_widget_root, "setBackgroundColor", Color.TRANSPARENT)
        views.setInt(R.id.qingke_widget_image, "setBackgroundColor", Color.TRANSPARENT)
        views.setViewPadding(R.id.qingke_widget_root, 0, 0, 0, 0)
        views.setViewPadding(R.id.qingke_widget_image, 0, 0, 0, 0)
        views.setImageViewBitmap(R.id.qingke_widget_image, bitmap)
        val launch = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.qingke_widget_root, launch)
        views.setOnClickPendingIntent(R.id.qingke_widget_image, launch)
        manager.updateAppWidget(id, views)
    }

    private fun widgetBitmapSize(context: Context, options: android.os.Bundle): Pair<Int, Int> {
        val density = context.resources.displayMetrics.density
        val fromSizes = widgetOptionSizes(options)?.filter { it.width > 0f && it.height > 0f }
        val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val maxW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val maxH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        val widthDp = fromSizes?.maxOfOrNull { it.width }
            ?: listOf(maxW, minW).firstOrNull { it > 0 }?.toFloat()
            ?: 180f
        val heightDp = fromSizes?.maxOfOrNull { it.height }
            ?: listOf(maxH, minH).firstOrNull { it > 0 }?.toFloat()
            ?: 180f
        val width = (widthDp * density).roundToSize()
        val height = (heightDp * density).roundToSize()
        // RemoteViews 传位图有大小限制，平板上拉满的组件能到十几 MB，缩到能过的范围。
        val pixels = width.toLong() * height
        if (pixels <= MAX_WIDGET_PIXELS) return width to height
        val shrink = kotlin.math.sqrt(MAX_WIDGET_PIXELS.toDouble() / pixels)
        return (width * shrink).toInt().coerceAtLeast(160) to
            (height * shrink).toInt().coerceAtLeast(160)
    }

    @Suppress("DEPRECATION")
    private fun widgetOptionSizes(options: android.os.Bundle): List<SizeF>? {
        if (Build.VERSION.SDK_INT < 31) return null
        val raw = if (Build.VERSION.SDK_INT >= 33) {
            options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
        } else {
            options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES)
        }
        return raw
    }

    private fun Float.roundToSize(): Int = toInt().coerceIn(160, 1600)

    private fun snapshot(context: Context): AppSnapshot {
        val file = File(context.filesDir, STORE)
        if (!file.exists()) return AppSnapshot()
        return runCatching { json.decodeFromString(AppSnapshot.serializer(), file.readText()) }
            .getOrDefault(AppSnapshot())
    }

    private fun render(
        context: Context,
        range: WidgetRange,
        style: WidgetStyle,
        width: Int,
        height: Int,
    ): Bitmap {
        val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        val snap = snapshot(context)
        val today = nowDateTime().date
        val week = resolvedCurrentWeek(snap.settings, today)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setHasAlpha(true)
        bitmap.eraseColor(Color.TRANSPARENT)
        val canvas = Canvas(bitmap)
        val short = height < dp(context, 210f)
        val radius = dp(context, if (short) 20f else 24f)
        drawChrome(canvas, width, height, radius, style, dark)
        val pad = dp(context, if (short) 12f else 16f)
        val frosted = style != WidgetStyle.Solid
        val title = when (range) {
            WidgetRange.Day -> "${today.monthNumber}月${today.dayOfMonth}日"
            WidgetRange.Week -> if (week >= 1) "第${week}周" else "本周"
            WidgetRange.Month -> "${today.year}年${today.monthNumber}月"
        }
        val subtitle = when {
            !snap.hasTimetable -> "打开青课登录后才有课"
            range == WidgetRange.Day && week >= 1 -> "第${week}周"
            range == WidgetRange.Week -> "周一到周日"
            else -> "点一天看课"
        }
        val maxText = width - pad * 2
        val headerH: Float
        val accentDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT }
        if (short) {
            val line = if (range == WidgetRange.Day && week >= 1) "$title · $subtitle" else title
            val titlePaint = paint(context, ink(dark), 13f, true, frosted)
            canvas.drawCircle(pad + dp(context, 3f), pad + dp(context, 11f), dp(context, 3f), accentDot)
            canvas.drawText(fitText(titlePaint, line, maxText - dp(context, 12f)), pad + dp(context, 12f), pad + dp(context, 16f), titlePaint)
            headerH = pad + dp(context, 24f)
        } else {
            val titlePaint = paint(context, ink(dark), 16f, true, frosted)
            val subPaint = paint(context, muted(dark), 11f, false, frosted)
            canvas.drawCircle(pad + dp(context, 3.5f), pad + dp(context, 13f), dp(context, 3.5f), accentDot)
            canvas.drawText(fitText(titlePaint, title, maxText - dp(context, 14f)), pad + dp(context, 14f), pad + dp(context, 18f), titlePaint)
            canvas.drawText(fitText(subPaint, subtitle, maxText), pad, pad + dp(context, 36f), subPaint)
            headerH = dp(context, 50f)
        }
        val content = RectF(pad, headerH, width - pad, height - pad)
        if (!snap.hasTimetable) {
            drawCentered(
                canvas,
                "还没有课表",
                content.centerX(),
                content.centerY(),
                paint(context, muted(dark), 13f, false),
            )
            return bitmap
        }
        when (range) {
            WidgetRange.Day -> drawDay(canvas, content, snap, today, week, dark, context)
            WidgetRange.Week -> drawWeek(canvas, content, snap, today, week, dark, context)
            WidgetRange.Month -> drawMonth(canvas, content, snap, today, dark, context)
        }
        return bitmap
    }

    private fun drawChrome(
        canvas: Canvas,
        width: Int,
        height: Int,
        radius: Float,
        look: WidgetStyle,
        dark: Boolean,
    ) {
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val bg = Paint(Paint.ANTI_ALIAS_FLAG)
        when (look) {
            WidgetStyle.Solid -> {
                bg.color = if (dark) 0xFF1C1C1E.toInt() else 0xFFF7F8FA.toInt()
                canvas.drawRoundRect(rect, radius, radius, bg)
                val sheen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f, 0f, 0f, height * 0.35f,
                        if (dark) 0x14FFFFFF.toInt() else 0x66FFFFFF.toInt(),
                        0x00FFFFFF,
                        Shader.TileMode.CLAMP,
                    )
                }
                canvas.drawRoundRect(rect, radius, radius, sheen)
            }
            WidgetStyle.Blur -> {
                bg.color = if (dark) 0x66222224.toInt() else 0xB8F4F5F7.toInt()
                canvas.drawRoundRect(rect, radius, radius, bg)
                val frost = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (dark) 0x14FFFFFF.toInt() else 0x33FFFFFF.toInt()
                }
                canvas.drawRoundRect(rect, radius, radius, frost)
            }
            WidgetStyle.Glass -> {
                val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (dark) 0x4028282A.toInt() else 0x38FFFFFF.toInt()
                }
                canvas.drawRoundRect(rect, radius, radius, fill)
                bg.shader = LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    if (dark) 0x3DFFFFFF.toInt() else 0x77FFFFFF.toInt(),
                    if (dark) 0x10212121.toInt() else 0x14FFFFFF.toInt(),
                    Shader.TileMode.CLAMP,
                )
                canvas.drawRoundRect(rect, radius, radius, bg)
                val stroke = Paint(Paint.ANTI_ALIAS_FLAG)
                stroke.style = Paint.Style.STROKE
                stroke.strokeWidth = 1.6f
                stroke.color = if (dark) 0x55FFFFFF.toInt() else 0xAAFFFFFF.toInt()
                canvas.drawRoundRect(RectF(1.2f, 1.2f, width - 1.2f, height - 1.2f), radius, radius, stroke)
            }
        }
    }

    private fun drawDay(
        canvas: Canvas,
        box: RectF,
        snap: AppSnapshot,
        today: LocalDate,
        week: Int,
        dark: Boolean,
        context: Context,
    ) {
        val slots = snap.slots.forDate(today, snap.settings, today, snap.scheduleAdjust)
        if (slots.isEmpty()) {
            drawCentered(
                canvas,
                "今天没有理论课",
                box.centerX(),
                box.centerY(),
                paint(context, muted(dark), 12f, false),
            )
            return
        }
        val now = nowDateTime()
        val hasClock = snap.resolved().hasPeriodClock
        val live = if (hasClock) {
            nextLiveLesson(snap.slots, today, snap.settings, today, now.time, snap.scheduleAdjust)
        } else {
            null
        }
        val threeLine = box.height() / slots.size.coerceAtLeast(1) >= dp(context, 56f)
        val twoLine = box.height() / slots.size.coerceAtLeast(1) >= dp(context, 36f)
        val maxRows = when {
            threeLine -> 5
            twoLine -> 6
            else -> 8
        }
        val shown = slots.take(maxRows)
        val rowH = box.height() / shown.size
        val gap = dp(context, 3f)
        shown.forEachIndexed { index, slot ->
            val row = RectF(box.left, box.top + index * rowH + gap, box.right, box.top + (index + 1) * rowH - gap)
            if (row.height() < 8f) return@forEachIndexed
            val tint = courseColor(slot.courseId.ifBlank { slot.courseName }, dark)
            val nowClass = live?.inClass == true && sameLesson(live.slot, slot)
            val nextClass = live?.inClass == false && sameLesson(live.slot, slot)
            val card = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = when {
                    nowClass -> accentWash(dark)
                    else -> fade(tint, if (dark) 0xAA else 0xCC)
                }
            }
            canvas.drawRoundRect(row, dp(context, 10f), dp(context, 10f), card)
            if (nowClass || nextClass) {
                val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = dp(context, 1.2f)
                    color = if (nowClass) ACCENT else fade(ACCENT, 0x66)
                }
                canvas.drawRoundRect(row, dp(context, 10f), dp(context, 10f), ring)
            }
            val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (nowClass) ACCENT else tint }
            canvas.drawRoundRect(
                RectF(row.left, row.top, row.left + dp(context, 4f), row.bottom),
                dp(context, 10f),
                dp(context, 10f),
                bar,
            )
            val textLeft = row.left + dp(context, 12f)
            val textW = row.width() - dp(context, 18f)
            val period = periodText(slot)
            val clock = if (hasClock) periodClockRange(slot.period).replace("-", "–") else ""
            val name = paint(context, ink(dark), if (threeLine) 13f else 12f, true)
            val meta = paint(context, muted(dark), 10f, false)
            val badge = if (nowClass) "上课中" else if (nextClass) "下一节" else ""
            if (threeLine) {
                drawDayTitle(canvas, slot.courseName, badge, textLeft, row.top + row.height() * 0.30f, textW, name, context, dark)
                canvas.drawText(
                    fitText(meta, listOf(period, clock).filter { it.isNotBlank() }.joinToString("  "), textW),
                    textLeft,
                    row.top + row.height() * 0.58f,
                    meta,
                )
                canvas.drawText(
                    fitText(meta, listOf(slot.room, slot.teacher).filter { it.isNotBlank() }.joinToString(" · "), textW),
                    textLeft,
                    row.top + row.height() * 0.82f,
                    meta,
                )
            } else if (twoLine) {
                drawDayTitle(canvas, slot.courseName, badge, textLeft, row.top + row.height() * 0.38f, textW, name, context, dark)
                canvas.drawText(
                    fitText(meta, listOf(period, clock, slot.room).filter { it.isNotBlank() }.joinToString(" · "), textW),
                    textLeft,
                    row.top + row.height() * 0.74f,
                    meta,
                )
            } else {
                canvas.drawText(
                    fitText(name, listOf(period, slot.courseName).filter { it.isNotBlank() }.joinToString("  "), textW),
                    textLeft,
                    row.top + row.height() * 0.68f,
                    name,
                )
            }
        }
    }

    private fun drawDayTitle(
        canvas: Canvas,
        course: String,
        badge: String,
        left: Float,
        baseline: Float,
        maxWidth: Float,
        name: Paint,
        context: Context,
        dark: Boolean,
    ) {
        if (badge.isBlank()) {
            canvas.drawText(fitText(name, course, maxWidth), left, baseline, name)
            return
        }
        val tag = paint(context, ACCENT, 9f, true)
        val tagW = tag.measureText(badge) + dp(context, 10f)
        val nameW = (maxWidth - tagW - dp(context, 6f)).coerceAtLeast(dp(context, 40f))
        canvas.drawText(fitText(name, course, nameW), left, baseline, name)
        val tagLeft = left + name.measureText(fitText(name, course, nameW)) + dp(context, 6f)
        val fm = tag.fontMetrics
        val pill = RectF(
            tagLeft,
            baseline + fm.ascent - dp(context, 1f),
            tagLeft + tagW,
            baseline + fm.descent + dp(context, 1f),
        )
        canvas.drawRoundRect(pill, dp(context, 8f), dp(context, 8f), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentWash(dark) })
        canvas.drawText(badge, tagLeft + dp(context, 5f), baseline, tag)
    }

    private fun drawWeek(
        canvas: Canvas,
        box: RectF,
        snap: AppSnapshot,
        today: LocalDate,
        week: Int,
        dark: Boolean,
        context: Context,
    ) {
        val periodBlocks = snap.resolved().periodBlocks
        val monday = mondayOfTeachingWeek(week.coerceAtLeast(1), snap.settings, today)
        val byDay = (1..7).map { day ->
            snap.slots.forDate(
                monday.plus(DatePeriod(days = day - 1)),
                snap.settings,
                today,
                snap.scheduleAdjust,
            )
        }
        val blocks = periodBlocks.filter { block ->
            byDay.any { day -> day.any { it.occupiesBlock(block) } }
        }.ifEmpty { periodBlocks.take(6) }
        val cols = 8
        val rows = 1 + blocks.size
        val cw = box.width() / cols
        val rh = box.height() / rows
        val head = paint(context, muted(dark), 10f, true)
        val dayPaint = paint(context, muted(dark), 9f, false)
        val mark = paint(context, muted(dark), 8f, true)
        val label = paint(context, muted(dark), 9f, false)
        val todayIndex = (1..7).firstOrNull { monday.plus(DatePeriod(days = it - 1)) == today }
        if (todayIndex != null) {
            val wash = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentWash(dark) }
            val x0 = box.left + todayIndex * cw
            canvas.drawRoundRect(RectF(x0, box.top, x0 + cw, box.bottom), dp(context, 10f), dp(context, 10f), wash)
        }
        WeekdayNames.forEachIndexed { i, name ->
            val date = monday.plus(DatePeriod(days = i))
            val cx = box.left + (i + 1) * cw + cw / 2f
            val todayCol = date == today
            head.color = if (todayCol) ACCENT else muted(dark)
            dayPaint.color = if (todayCol) ACCENT else muted(dark)
            drawCentered(canvas, name, cx, box.top + rh * 0.28f, head)
            drawCentered(canvas, weekDateLabel(date, monday), cx, box.top + rh * 0.58f, dayPaint)
            val holiday = snap.holidays.lookup(date)
            if (holiday != null) {
                mark.color = if (holiday.off) ACCENT else 0xFFC9782A.toInt()
                drawCentered(canvas, if (holiday.off) "休" else "班", cx, box.top + rh * 0.84f, mark)
            } else if (isAdjustOff(date, snap.settings, snap.scheduleAdjust)) {
                mark.color = ACCENT
                drawCentered(canvas, "假", cx, box.top + rh * 0.84f, mark)
            }
        }
        val twoLine = rh >= dp(context, 30f)
        blocks.forEachIndexed { row, block ->
            val y0 = box.top + (row + 1) * rh
            drawCentered(
                canvas,
                fitText(label, block.label, cw - 4f),
                box.left + cw / 2f,
                y0 + rh / 2f,
                label,
            )
            for (day in 1..7) {
                val cell = byDay[day - 1].firstOrNull { it.occupiesBlock(block) } ?: continue
                val x0 = box.left + day * cw + 2.5f
                val cellRect = RectF(x0, y0 + 2.5f, x0 + cw - 5f, y0 + rh - 3f)
                val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = courseColor(cell.courseId.ifBlank { cell.courseName }, dark)
                }
                canvas.drawRoundRect(cellRect, dp(context, 7f), dp(context, 7f), fill)
                val fg = paint(context, if (dark) 0xFFF6F6F6.toInt() else 0xFF1A1A1A.toInt(), if (twoLine) 9f else 8f, true)
                val alias = shortCourseName(cell.courseName, if (twoLine) 5 else 4, snap.settings.courseAliases, cell.courseId)
                if (twoLine && cell.room.isNotBlank()) {
                    val roomPaint = paint(context, if (dark) 0xCCF6F6F6.toInt() else 0x991A1A1A.toInt(), 8f, false)
                    drawCentered(canvas, fitText(fg, alias, cellRect.width() - 6f), cellRect.centerX(), cellRect.centerY() - dp(context, 5f), fg)
                    drawCentered(canvas, fitText(roomPaint, cell.room, cellRect.width() - 6f), cellRect.centerX(), cellRect.centerY() + dp(context, 7f), roomPaint)
                } else {
                    drawCentered(canvas, fitText(fg, alias, cellRect.width() - 6f), cellRect.centerX(), cellRect.centerY(), fg)
                }
            }
        }
    }

    private fun drawMonth(
        canvas: Canvas,
        box: RectF,
        snap: AppSnapshot,
        today: LocalDate,
        dark: Boolean,
        context: Context,
    ) {
        val first = LocalDate(today.year, today.monthNumber, 1)
        val lead = weekdayIndex(first) - 1
        val days = first.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)).dayOfMonth
        val total = ((lead + days + 6) / 7) * 7
        val start = first.minus(DatePeriod(days = lead))
        val cols = 7
        val rows = 1 + total / 7
        val cw = box.width() / cols
        val rh = box.height() / rows
        val head = paint(context, muted(dark), 10f, true)
        WeekdayNames.forEachIndexed { i, name ->
            drawCentered(canvas, name, box.left + i * cw + cw / 2f, box.top + rh * 0.48f, head)
        }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fade(muted(dark), 0x33)
            strokeWidth = 1f
        }
        canvas.drawLine(box.left, box.top + rh - 2f, box.right, box.top + rh - 2f, line)
        for (i in 0 until total) {
            val date = start.plus(DatePeriod(days = i))
            val col = i % 7
            val row = 1 + i / 7
            val x = box.left + col * cw
            val y = box.top + row * rh
            val inMonth = date.monthNumber == today.monthNumber
            val isToday = date == today
            val cx = x + cw / 2f
            if (isToday) {
                val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT }
                val size = rh.coerceAtMost(cw) * 0.28f
                canvas.drawRoundRect(
                    RectF(cx - size, y + rh * 0.18f - size * 0.15f, cx + size, y + rh * 0.18f + size * 1.55f),
                    dp(context, 8f),
                    dp(context, 8f),
                    mark,
                )
            }
            val dayPaint = paint(
                context,
                when {
                    isToday -> 0xFFFFFFFF.toInt()
                    !inMonth -> fade(ink(dark), 0x4D)
                    else -> ink(dark)
                },
                11f,
                isToday,
            )
            drawCentered(canvas, date.dayOfMonth.toString(), cx, y + rh * 0.36f, dayPaint)
            if (inMonth) {
                val dots = snap.slots.forDate(date, snap.settings, today, snap.scheduleAdjust)
                    .map { courseColor(it.courseId.ifBlank { it.courseName }, dark) }
                    .distinct()
                    .take(3)
                val span = (dots.size - 1) * dp(context, 7f)
                dots.forEachIndexed { di, color ->
                    val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
                    canvas.drawCircle(cx - span / 2f + di * dp(context, 7f), y + rh * 0.72f, 3.4f, dot)
                }
            }
        }
    }

    private fun courseColor(key: String, dark: Boolean): Int {
        val light = intArrayOf(0xFFD7E6FF.toInt(), 0xFFD4F3E3.toInt(), 0xFFFFE4C4.toInt(), 0xFFE8DCFF.toInt(), 0xFFFFD6D2.toInt(), 0xFFCDEFF3.toInt())
        val night = intArrayOf(0xFF2E4A78.toInt(), 0xFF2C5743.toInt(), 0xFF6E4E2C.toInt(), 0xFF4E3C6C.toInt(), 0xFF6C3C3A.toInt(), 0xFF2C555A.toInt())
        val hash = key.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }
        return (if (dark) night else light)[hash % 6]
    }

    private fun sameLesson(left: LessonSlot, right: LessonSlot): Boolean =
        left.period == right.period &&
            (left.courseId.isNotBlank() && left.courseId == right.courseId || left.courseName == right.courseName)

    private fun periodText(slot: LessonSlot): String {
        val period = slot.periodLabel.ifBlank { slot.period }
        return if (period.endsWith("节")) period else "${period}节"
    }

    private fun ink(dark: Boolean): Int = if (dark) 0xFFF4F4F5.toInt() else 0xFF1A1A1A.toInt()

    private fun muted(dark: Boolean): Int = if (dark) 0x99F4F4F5.toInt() else 0x99303030.toInt()

    private fun accentWash(dark: Boolean): Int = if (dark) 0x333482FF else 0x1F3482FF

    private fun fade(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    private fun paint(
        context: Context,
        color: Int,
        sp: Float,
        bold: Boolean,
        shadow: Boolean = false,
    ): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.resources.displayMetrics)
        typeface = if (bold) Typeface.create("sans-serif-medium", Typeface.NORMAL) else Typeface.create("sans-serif", Typeface.NORMAL)
        isFakeBoldText = false
        if (shadow) setShadowLayer(2.4f, 0f, 0.8f, 0x4D000000)
    }

    private fun drawCentered(canvas: Canvas, text: String, cx: Float, cy: Float, paint: Paint) {
        val fm = paint.fontMetrics
        canvas.drawText(text, cx - paint.measureText(text) / 2f, cy - (fm.ascent + fm.descent) / 2f, paint)
    }

    private fun dp(context: Context, value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics)

    private fun fitText(paint: Paint, text: String, maxWidth: Float): String {
        if (text.isEmpty() || maxWidth <= 0f || paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        val extra = paint.measureText(ellipsis)
        if (maxWidth <= extra) return ellipsis
        var end = text.length
        while (end > 0 && paint.measureText(text, 0, end) + extra > maxWidth) end--
        return if (end <= 0) ellipsis else text.take(end) + ellipsis
    }

}

internal const val ACTION_TICK = "cn.edu.gzus.qingke.widget.TICK"

/** 到点叫醒：重画一次，顺便把下一次闹钟排上。 */
class WidgetTickReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching { QingkeWidgets.refreshAll(context) }
    }
}

/** 重启、改时间、改时区、覆盖安装都会清掉闹钟，得重画并重新排。 */
class WidgetClockReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> runCatching { QingkeWidgets.refreshAll(context) }
        }
    }
}

abstract class QingkeWidgetReceiver : AppWidgetProvider() {
    abstract val range: WidgetRange

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { QingkeWidgets.update(context, manager, it, range) }
        QingkeWidgets.scheduleNextTick(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        newOptions: android.os.Bundle?,
    ) {
        QingkeWidgets.update(context, manager, id, range)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        QingkeWidgets.clear(context, ids)
    }
}

class DayWidgetReceiver : QingkeWidgetReceiver() {
    override val range = WidgetRange.Day
}

class WeekWidgetReceiver : QingkeWidgetReceiver() {
    override val range = WidgetRange.Week
}

class MonthWidgetReceiver : QingkeWidgetReceiver() {
    override val range = WidgetRange.Month
}
