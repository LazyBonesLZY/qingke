package cn.edu.gzus.qingke.data

/** 周课表的可见星期：默认工作日，手动展开后包含周末。月历始终显示完整七天。 */
fun visibleTimetableDays(showWeekend: Boolean): IntRange = 1..(if (showWeekend) 7 else 5)
