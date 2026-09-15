package cn.edu.gzus.qingke.widget

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
import android.util.SizeF
import android.util.TypedValue
import android.widget.RemoteViews
import cn.edu.gzus.qingke.MainActivity
import cn.edu.gzus.qingke.data.AppSnapshot
import cn.edu.gzus.qingke.data.WeekdayNames
import cn.edu.gzus.qingke.data.resolved
import cn.edu.gzus.qingke.data.forBlock
import cn.edu.gzus.qingke.data.forDate
import cn.edu.gzus.qingke.data.lookup
import cn.edu.gzus.qingke.data.mondayOfTeachingWeek
import cn.edu.gzus.qingke.data.nowDateTime
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
        listOf(
            DayWidgetReceiver::class.java to WidgetRange.Day,
            WeekWidgetReceiver::class.java to WidgetRange.Week,
            MonthWidgetReceiver::class.java to WidgetRange.Month,
        ).forEach { (cls, range) ->
            manager.getAppWidgetIds(ComponentName(context, cls)).forEach { id ->
                update(context, manager, id, range)
            }
        }
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
        val fromSizes = options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
            ?.filter { it.width > 0f && it.height > 0f }
        val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val maxW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val maxH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        val widthDp = listOf(minW, maxW).filter { it > 0 }.minOrNull()?.toFloat()
            ?: fromSizes?.minOfOrNull { it.width }
            ?: 110f
        val heightDp = listOf(minH, maxH).filter { it > 0 }.minOrNull()?.toFloat()
            ?: fromSizes?.minOfOrNull { it.height }
            ?: 110f
        return (widthDp * density).roundToSize() to (heightDp * density).roundToSize()
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
        val radius = dp(context, if (short) 18f else 22f)
        drawChrome(canvas, width, height, radius, style, dark)
        val pad = dp(context, if (short) 10f else 14f)
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
            else -> "点格子看一天"
        }
        val maxText = width - pad * 2
        val headerH: Float
        if (short) {
            val line = if (range == WidgetRange.Day && week >= 1) "$title · $subtitle" else title
            val titlePaint = paint(context, ink(dark), 13f, true, frosted)
            canvas.drawText(fitText(titlePaint, line, maxText), pad, pad + dp(context, 15f), titlePaint)
            headerH = pad + dp(context, 22f)
        } else {
            val titlePaint = paint(context, ink(dark), 15f, true, frosted)
            val subPaint = paint(context, muted(dark), 11f, false, frosted)
            canvas.drawText(fitText(titlePaint, title, maxText), pad, pad + dp(context, 18f), titlePaint)
            canvas.drawText(fitText(subPaint, subtitle, maxText), pad, pad + dp(context, 34f), subPaint)
            headerH = dp(context, 48f)
        }
        val content = RectF(pad, headerH, width - pad, height - pad)
        if (!snap.hasTimetable) {
            canvas.drawText("还没有课表", content.left, content.top + dp(context, 28f), paint(context, muted(dark), 13f, false))
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
                bg.color = if (dark) 0xFF1E1E1E.toInt() else 0xFFFFFFFF.toInt()
                canvas.drawRoundRect(rect, radius, radius, bg)
            }
            WidgetStyle.Blur -> {
                bg.color = if (dark) 0x59212121.toInt() else 0x4DF7F7F7.toInt()
                canvas.drawRoundRect(rect, radius, radius, bg)
                val frost = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (dark) 0x1AFFFFFF.toInt() else 0x22FFFFFF.toInt()
                }
                canvas.drawRoundRect(rect, radius, radius, frost)
            }
            WidgetStyle.Glass -> {
                bg.shader = LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    if (dark) 0x55FFFFFF.toInt() else 0x66FFFFFF.toInt(),
                    if (dark) 0x14212121.toInt() else 0x22FFFFFF.toInt(),
                    Shader.TileMode.CLAMP,
                )
                val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (dark) 0x33282828.toInt() else 0x28FFFFFF.toInt()
                }
                canvas.drawRoundRect(rect, radius, radius, fill)
                canvas.drawRoundRect(rect, radius, radius, bg)
                val stroke = Paint(Paint.ANTI_ALIAS_FLAG)
                stroke.setStyle(Paint.Style.STROKE)
                stroke.strokeWidth = 2f
                stroke.color = if (dark) 0x66FFFFFF.toInt() else 0x99FFFFFF.toInt()
                canvas.drawRoundRect(RectF(1.5f, 1.5f, width - 1.5f, height - 1.5f), radius, radius, stroke)
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
        val slots = snap.slots.forDate(today, snap.settings, today)
        if (slots.isEmpty()) {
            canvas.drawText("今天没有理论课", box.left, box.top + dp(context, 20f), paint(context, muted(dark), 12f, false))
            return
        }
        val twoLine = box.height() / slots.size.coerceAtLeast(1) >= dp(context, 32f)
        val maxRows = if (twoLine) 6 else 8
        val shown = slots.take(maxRows)
        val rowH = box.height() / shown.size
        val textW = box.width() - dp(context, 14f)
        shown.forEachIndexed { index, slot ->
            val top = box.top + index * rowH
            val tint = courseColor(slot.courseId.ifBlank { slot.courseName }, dark)
            val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint }
            canvas.drawRoundRect(RectF(box.left, top + 3f, box.left + dp(context, 5f), top + rowH - 3f), 4f, 4f, pill)
            val period = slot.periodLabel.ifBlank { slot.period }
            if (twoLine) {
                val name = paint(context, ink(dark), 12f, true)
                val meta = paint(context, muted(dark), 10f, false)
                canvas.drawText(
                    fitText(name, "$period  ${slot.courseName}", textW),
                    box.left + dp(context, 10f),
                    top + rowH * 0.40f,
                    name,
                )
                canvas.drawText(
                    fitText(meta, listOf(slot.room, slot.teacher).filter { it.isNotBlank() }.joinToString(" · "), textW),
                    box.left + dp(context, 10f),
                    top + rowH * 0.76f,
                    meta,
                )
            } else {
                val name = paint(context, ink(dark), 11f, true)
                canvas.drawText(
                    fitText(name, "$period  ${slot.courseName}", textW),
                    box.left + dp(context, 10f),
                    top + rowH * 0.68f,
                    name,
                )
            }
        }
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
        val blocks = periodBlocks.filter { block ->
            (1..7).any { day -> snap.slots.forBlock(week, day, block).isNotEmpty() }
        }.ifEmpty { periodBlocks.take(6) }
        val cols = 8
        val rows = 1 + blocks.size
        val cw = box.width() / cols
        val rh = box.height() / rows
        val head = paint(context, muted(dark), 10f, true)
        val dayPaint = paint(context, muted(dark), 9f, false)
        val mark = paint(context, muted(dark), 8f, true)
        val label = paint(context, muted(dark), 9f, false)
        val monday = mondayOfTeachingWeek(week.coerceAtLeast(1), snap.settings, today)
        WeekdayNames.forEachIndexed { i, name ->
            val date = monday.plus(DatePeriod(days = i))
            val x = box.left + (i + 1) * cw + cw * 0.18f
            val todayCol = date == today
            head.color = if (todayCol) 0xFF3482FF.toInt() else muted(dark)
            dayPaint.color = if (todayCol) 0xFF3482FF.toInt() else muted(dark)
            canvas.drawText(name, x, box.top + rh * 0.38f, head)
            canvas.drawText(weekDateLabel(date, monday), x, box.top + rh * 0.70f, dayPaint)
            val holiday = snap.holidays.lookup(date)
            if (holiday != null) {
                mark.color = if (holiday.off) 0xFF3482FF.toInt() else 0xFFC9782A.toInt()
                canvas.drawText(if (holiday.off) "休" else "班", x, box.top + rh * 0.94f, mark)
            }
        }
        blocks.forEachIndexed { row, block ->
            val y0 = box.top + (row + 1) * rh
            canvas.drawText(fitText(label, block.label, cw - 2f), box.left + 1f, y0 + rh * 0.62f, label)
            for (day in 1..7) {
                val cell = snap.slots.forBlock(week, day, block).firstOrNull() ?: continue
                val x0 = box.left + day * cw + 2f
                val cellRect = RectF(x0, y0 + 2f, x0 + cw - 4f, y0 + rh - 3f)
                val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = courseColor(cell.courseId.ifBlank { cell.courseName }, dark) }
                canvas.drawRoundRect(cellRect, 6f, 6f, fill)
                val fg = paint(context, if (dark) 0xFFF3F3F3.toInt() else 0xFF1A1A1A.toInt(), 8f, true)
                canvas.drawText(
                    fitText(fg, shortCourseName(cell.courseName, 4, snap.settings.courseAliases, cell.courseId), cellRect.width() - 4f),
                    cellRect.left + 3f,
                    cellRect.centerY() + 3f,
                    fg,
                )
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
            canvas.drawText(name, box.left + i * cw + cw * 0.28f, box.top + rh * 0.62f, head)
        }
        for (i in 0 until total) {
            val date = start.plus(DatePeriod(days = i))
            val col = i % 7
            val row = 1 + i / 7
            val x = box.left + col * cw
            val y = box.top + row * rh
            val inMonth = date.monthNumber == today.monthNumber
            val isToday = date == today
            if (isToday) {
                val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3482FF.toInt() }
                canvas.drawCircle(x + cw * 0.42f, y + rh * 0.32f, rh * 0.22f, mark)
            }
            val dayPaint = paint(
                context,
                when {
                    isToday -> 0xFFFFFFFF.toInt()
                    !inMonth -> 0x66303030
                    else -> ink(dark)
                },
                11f,
                isToday,
            )
            canvas.drawText(date.dayOfMonth.toString(), x + cw * 0.28f, y + rh * 0.42f, dayPaint)
            if (inMonth) {
                val dots = snap.slots.forDate(date, snap.settings, today)
                    .map { courseColor(it.courseId.ifBlank { it.courseName }, dark) }
                    .distinct()
                    .take(3)
                dots.forEachIndexed { di, color ->
                    val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
                    canvas.drawCircle(x + cw * 0.22f + di * dp(context, 7f), y + rh * 0.72f, 3.2f, dot)
                }
            }
        }
    }

    private fun courseColor(key: String, dark: Boolean): Int {
        val light = intArrayOf(0xFFDCE9FF.toInt(), 0xFFD8F3E4.toInt(), 0xFFFFE6CC.toInt(), 0xFFEADBFF.toInt(), 0xFFFFD9D6.toInt(), 0xFFD4F1F6.toInt())
        val night = intArrayOf(0xFF2B4570.toInt(), 0xFF2A5340.toInt(), 0xFF6A4A28.toInt(), 0xFF4A3868.toInt(), 0xFF6A3836.toInt(), 0xFF2A5358.toInt())
        val hash = key.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }
        return (if (dark) night else light)[hash % 6]
    }

    private fun ink(dark: Boolean): Int = if (dark) 0xFFF2F2F2.toInt() else 0xFF1A1A1A.toInt()

    private fun muted(dark: Boolean): Int = if (dark) 0x99F2F2F2.toInt() else 0x99303030.toInt()

    private fun paint(
        context: Context,
        color: Int,
        sp: Float,
        bold: Boolean,
        shadow: Boolean = false,
    ): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.resources.displayMetrics)
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        isFakeBoldText = bold
        if (shadow) setShadowLayer(3.5f, 0f, 1.2f, 0x66000000)
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

abstract class QingkeWidgetReceiver : AppWidgetProvider() {
    abstract val range: WidgetRange

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { QingkeWidgets.update(context, manager, it, range) }
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
