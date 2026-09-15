package cn.edu.gzus.qingke.data

fun GradeItem.resolvedGpa(): Double? {
    gpa.toDoubleOrNull()?.let { return it }
    return gpaFromScore(score)
}

fun gpaFromScore(raw: String): Double? {
    val text = raw.trim()
    if (text.isBlank()) return null
    when (text) {
        "优秀" -> return 4.0
        "良好" -> return 3.0
        "中等", "中" -> return 2.0
        "及格" -> return 1.0
        "不及格", "不合格" -> return 0.0
    }
    val score = text.toDoubleOrNull() ?: return null
    if (score < 60.0) return 0.0
    return ((score / 10.0) - 5.0).coerceIn(1.0, 5.0)
}

fun GradeItem.numericScore(): Double? {
    val text = score.trim()
    if (text.isBlank()) return null
    return when (text) {
        "优秀" -> 95.0
        "良好" -> 85.0
        "中等", "中" -> 75.0
        "及格" -> 65.0
        "不及格", "不合格" -> 0.0
        else -> text.toDoubleOrNull()
    }
}

data class GpaSummary(
    val weighted: Double?,
    val averageScore: Double?,
    val counted: Int,
    val credits: Double,
)

fun summarizeGpa(grades: List<GradeItem>): GpaSummary {
    var creditSum = 0.0
    var weightedSum = 0.0
    var scoreSum = 0.0
    var scoreCount = 0
    var counted = 0
    grades.forEach { item ->
        val credit = item.credit.toDoubleOrNull() ?: 0.0
        val gpa = item.resolvedGpa()
        val score = item.numericScore()
        if (gpa != null && credit > 0.0) {
            weightedSum += gpa * credit
            creditSum += credit
            counted += 1
        }
        if (score != null) {
            scoreSum += score
            scoreCount += 1
        }
    }
    return GpaSummary(
        weighted = if (creditSum > 0.0) weightedSum / creditSum else null,
        averageScore = if (scoreCount > 0) scoreSum / scoreCount else null,
        counted = counted,
        credits = creditSum,
    )
}

fun formatGpa(value: Double): String {
    val scaled = kotlin.math.round(value * 100.0).toInt()
    return "${scaled / 100}.${(scaled % 100).toString().padStart(2, '0')}"
}

fun formatScore(value: Double): String {
    val scaled = kotlin.math.round(value * 10.0).toInt()
    return if (scaled % 10 == 0) "${scaled / 10}" else "${scaled / 10}.${scaled % 10}"
}
