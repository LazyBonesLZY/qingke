package cn.edu.gzus.qingke.ui.timetable

/**
 * Miuix 弹簧对远跳也滚过全程，而课表每页有大量文字和格子。
 * 保持近距离的连续动画；远跳最多只动画最后一页，避免在动画帧里构建沿途所有周。
 * 输入是已夹到学期范围的零基页码，返回 null 表示无需预定位。
 */
internal fun timetablePreJumpPage(currentPage: Int, targetPage: Int): Int? = when {
    targetPage - currentPage > 3 -> targetPage - 1
    currentPage - targetPage > 3 -> targetPage + 1
    else -> null
}
