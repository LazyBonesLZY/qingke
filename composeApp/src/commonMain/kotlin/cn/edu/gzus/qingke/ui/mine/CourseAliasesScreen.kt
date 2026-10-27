package cn.edu.gzus.qingke.ui.mine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.edu.gzus.qingke.data.AppSnapshot
import cn.edu.gzus.qingke.data.compactCourseName
import cn.edu.gzus.qingke.data.uniqueCourses
import cn.edu.gzus.qingke.ui.components.EmptyState
import cn.edu.gzus.qingke.ui.components.Inset
import cn.edu.gzus.qingke.ui.components.Space
import cn.edu.gzus.qingke.ui.components.StatusTag
import cn.edu.gzus.qingke.ui.components.Tone
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun CourseAliasesScreen(
    snapshot: AppSnapshot,
    contentPadding: PaddingValues,
    onSaveAlias: (courseId: String, courseName: String, alias: String) -> Unit,
) {
    val courses = uniqueCourses(snapshot.slots)
    val aliases = snapshot.settings.courseAliases
    var editingId by remember { mutableStateOf<String?>(null) }
    // 刚保存 / 恢复过的那一行，右侧给一个成功回执，不然编辑区一收起像什么都没发生。
    var receiptKey by remember { mutableStateOf<String?>(null) }
    var receiptText by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(bottom = Space.xl),
    ) {
        Spacer(Modifier.height(Space.s))
        Text(
            "只改课表格子里的简称，课程全名不受影响。",
            modifier = Modifier.padding(horizontal = Inset.page),
            color = Tone.muted(),
            style = MiuixTheme.textStyles.body2,
        )
        if (courses.isEmpty()) {
            Spacer(Modifier.height(Space.m))
            EmptyState(
                title = "还没有课程",
                detail = "同步课表后，这里会列出每门课，可以逐个改缩写。",
            )
        } else {
            SmallTitle(text = "课程 · ${courses.size} 门")
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Inset.page),
                insideMargin = PaddingValues(0.dp),
            ) {
                courses.forEach { course ->
                    val key = course.courseId.ifBlank { course.courseName }
                    val shown = compactCourseName(course.courseName, aliases, course.courseId)
                    val custom = !aliases[key].isNullOrBlank() || !aliases[course.courseName].isNullOrBlank()
                    ArrowPreference(
                        title = course.courseName,
                        summary = "格子里显示 $shown",
                        endActions = {
                            when {
                                receiptKey == key -> StatusTag(receiptText, color = Tone.success())
                                custom -> StatusTag("已自定义")
                            }
                        },
                        onClick = { editingId = if (editingId == key) null else key },
                    )
                    if (editingId == key) {
                        AliasEditor(
                            courseId = course.courseId,
                            courseName = course.courseName,
                            aliases = aliases,
                            onSave = { id, name, alias ->
                                onSaveAlias(id, name, alias)
                                receiptKey = key
                                receiptText = if (alias.isBlank()) "已恢复默认" else "已保存"
                                editingId = null
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AliasEditor(
    courseId: String,
    courseName: String,
    aliases: Map<String, String>,
    onSave: (courseId: String, courseName: String, alias: String) -> Unit,
) {
    val key = courseId.ifBlank { courseName }
    val fallback = compactCourseName(courseName)
    val saved = aliases[key] ?: aliases[courseName]
    var draft by remember(key) {
        mutableStateOf(saved ?: fallback)
    }
    val trimmed = draft.trim()
    Column(Modifier.fillMaxWidth().padding(horizontal = Inset.page).padding(top = Space.xs, bottom = Space.l)) {
        TextField(
            value = draft,
            onValueChange = { draft = it.take(4) },
            label = "课表格子缩写",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Space.xs))
        // 校验错误紧贴输入框，用危险色；没问题时给规则提示。
        if (trimmed.isBlank()) {
            Text("缩写不能为空；想用默认就点「恢复默认」", style = MiuixTheme.textStyles.footnote1, color = Tone.danger())
        } else {
            Text("默认是 $fallback · ${trimmed.length}/4 字", style = MiuixTheme.textStyles.footnote1, color = Tone.muted())
        }
        Spacer(Modifier.height(Space.m))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            Button(
                onClick = { onSave(courseId, courseName, "") },
                // 没自定义过时「恢复默认」什么也不会改，置灰。
                enabled = !saved.isNullOrBlank(),
                modifier = Modifier.weight(1f),
                minHeight = 44.dp,
            ) { Text("恢复默认") }
            Button(
                onClick = { onSave(courseId, courseName, trimmed) },
                // 和当前生效的一样就没必要再存一遍。
                enabled = trimmed.isNotBlank() && trimmed != (saved ?: fallback),
                modifier = Modifier.weight(1f),
                minHeight = 44.dp,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) { Text("保存") }
        }
    }
}
