package cn.edu.gzus.qingke.ui.mine

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import cn.edu.gzus.qingke.ui.components.EmptyHint
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(bottom = 24.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            "只改课表格子里的简称，完整课名还在课程详情里。",
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            style = MiuixTheme.textStyles.body2,
        )
        Spacer(Modifier.height(12.dp))
        if (courses.isEmpty()) {
            EmptyHint("同步课表后才能改缩写")
        } else {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                insideMargin = PaddingValues(0.dp),
            ) {
                courses.forEach { course ->
                    val key = course.courseId.ifBlank { course.courseName }
                    val shown = compactCourseName(course.courseName, aliases, course.courseId)
                    ArrowPreference(
                        title = course.courseName,
                        summary = "格子里显示 $shown",
                        onClick = { editingId = if (editingId == key) null else key },
                    )
                    if (editingId == key) {
                        AliasEditor(
                            courseId = course.courseId,
                            courseName = course.courseName,
                            aliases = aliases,
                            onSave = { id, name, alias ->
                                onSaveAlias(id, name, alias)
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
    var draft by remember(key) {
        mutableStateOf(aliases[key] ?: aliases[courseName] ?: fallback)
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            "默认是 $fallback，最多 4 个字",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
        )
        Spacer(Modifier.height(8.dp))
        TextField(
            value = draft,
            onValueChange = { draft = it.take(4) },
            label = "课表格子缩写",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onSave(courseId, courseName, draft.trim()) },
            enabled = draft.trim().isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            minHeight = 44.dp,
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) { Text("保存缩写") }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onSave(courseId, courseName, "") },
            modifier = Modifier.fillMaxWidth(),
            minHeight = 44.dp,
        ) { Text("恢复默认") }
    }
}
