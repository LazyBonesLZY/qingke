package cn.edu.gzus.qingke.ui.components

/** Preserve field order, but never squeeze academic class names into half-width tiles. */
internal fun responsiveFactRows(fields: List<Pair<String, String>>): List<List<Pair<String, String>>> {
    val rows = mutableListOf<List<Pair<String, String>>>()
    var pending: Pair<String, String>? = null
    for (field in fields) {
        if (field.second.isBlank()) continue
        val needsWideCard = field.first == "教学班" ||
            field.second.length > 14 || '\n' in field.second
        if (needsWideCard) {
            pending?.let { rows += listOf(it) }
            pending = null
            rows += listOf(field)
        } else {
            val first = pending
            if (first == null) {
                pending = field
            } else {
                rows += listOf(first, field)
                pending = null
            }
        }
    }
    pending?.let { rows += listOf(it) }
    return rows
}
