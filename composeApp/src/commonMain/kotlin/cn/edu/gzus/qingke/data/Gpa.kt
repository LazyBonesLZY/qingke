package cn.edu.gzus.qingke.data

fun GradeItem.resolvedGpa(): Double? {
    gpa.toDoubleOrNull()?.let { return it }
    return gpaFromScore(score)
}

fun gpaFromScore(raw: String): Double? {
    val text = raw.trim()
    if (text.isBlank()) return null
    // 等级按 numericScore 的折算分（95/85/75/65）走同一条「分数/10-5」公式，
    // 否则「优秀」的绩点比 95 分低 0.5，等级制课程会被系统性压低。
    when (text) {
        "优秀" -> return 4.5
        "良好" -> return 3.5
        "中等", "中" -> return 2.5
        "及格" -> return 1.5
        "不及格", "不合格", "不通过" -> return 0.0
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
        "不及格", "不合格", "不通过" -> 0.0
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
    // 重修、补考会让同一门课出现好几行，只算最好的那次。
    val attempts = grades
        .groupBy { it.courseId.ifBlank { it.courseName } }
        .values
        .map { rows -> rows.maxBy { row -> row.resolvedGpa() ?: -1.0 } }
    attempts.forEach { item ->
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

/**
 * 成绩所属学期 → (起始学年, 第几学期)。正方是「2026-2027 1」，仲恺是「2026-2027-1」；
 * 认不出来返回 null，调用方就不按学期筛它。
 */
fun gradeTermKey(term: String): Pair<Int, Int>? {
    val numbers = Regex("\\d+").findAll(term).map { it.value }.toList()
    val year = numbers.firstOrNull { it.length == 4 }?.toIntOrNull() ?: return null
    val index = numbers.lastOrNull()?.takeIf { it.length <= 2 }?.toIntOrNull() ?: return null
    return year to index
}

/** 设置里当前学年学期（正方码：学期 3=第一、12=第二、16=第三）；强智等没有码时返回 null。 */
fun AppSettings.currentTermKey(): Pair<Int, Int>? {
    val year = yearCode.toIntOrNull() ?: return null
    val index = when (termCode) {
        "3", "1" -> 1
        "12", "2" -> 2
        "16" -> 3
        else -> return null
    }
    return year to index
}

/** 只留本学期的成绩：学期认得出、又和当前学期不同的才剔掉。 */
fun List<GradeItem>.inTerm(current: Pair<Int, Int>?): List<GradeItem> =
    if (current == null) this else filter { grade -> gradeTermKey(grade.term)?.let { it == current } ?: true }

fun formatGpa(value: Double): String {
    val scaled = kotlin.math.round(value * 100.0).toInt()
    return "${scaled / 100}.${(scaled % 100).toString().padStart(2, '0')}"
}

fun formatScore(value: Double): String {
    val scaled = kotlin.math.round(value * 10.0).toInt()
    return if (scaled % 10 == 0) "${scaled / 10}" else "${scaled / 10}.${scaled % 10}"
}
