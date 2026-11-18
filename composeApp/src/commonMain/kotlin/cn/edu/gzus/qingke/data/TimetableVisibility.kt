package cn.edu.gzus.qingke.data

/** 周课表的可见星期：默认工作日，手动展开后包含周末。月历始终显示完整七天。 */
fun visibleTimetableDays(showWeekend: Boolean): IntRange = 1..(if (showWeekend) 7 else 5)

/** 判断给定 ARGB 色块底色在当前画布背景上是否需要浅色文字以保证对比度。 */
internal fun cellNeedsLightInk(argb: Int, darkBg: Boolean): Boolean {
    val alpha = ((argb ushr 24) and 0xFF) / 255.0
    val bg = if (darkBg) 28.0 else 247.0
    val r = ((argb ushr 16) and 0xFF) * alpha + bg * (1.0 - alpha)
    val g = ((argb ushr 8) and 0xFF) * alpha + bg * (1.0 - alpha)
    val b = (argb and 0xFF) * alpha + bg * (1.0 - alpha)
    val luma = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
    return luma < 0.55
}
