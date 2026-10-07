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
import cn.edu.gzus.qingke.data.compactRoomName
import cn.edu.gzus.qingke.data.uniqueRooms
import cn.edu.gzus.qingke.ui.components.EmptyHint
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun RoomAliasesScreen(
    snapshot: AppSnapshot,
    contentPadding: PaddingValues,
    onSaveAlias: (room: String, alias: String) -> Unit,
) {
    val rooms = uniqueRooms(snapshot.slots)
    val aliases = snapshot.settings.roomAliases
    var editing by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(bottom = 24.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            "只改课表格子里的教室写法。默认会砍掉「电教实训室」这类尾巴，只留楼名和房号。",
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            style = MiuixTheme.textStyles.body2,
        )
        Spacer(Modifier.height(12.dp))
        if (rooms.isEmpty()) {
            EmptyHint("同步课表后才能改教室缩写")
        } else {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                insideMargin = PaddingValues(0.dp),
            ) {
                rooms.forEach { room ->
                    val shown = compactRoomName(room, aliases)
                    ArrowPreference(
                        title = room,
                        summary = if (shown == room) "格子里照原样显示" else "格子里显示 $shown",
                        onClick = { editing = if (editing == room) null else room },
                    )
                    if (editing == room) {
                        RoomAliasEditor(
                            room = room,
                            aliases = aliases,
                            onSave = { value ->
                                onSaveAlias(room, value)
                                editing = null
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomAliasEditor(
    room: String,
    aliases: Map<String, String>,
    onSave: (String) -> Unit,
) {
    val fallback = compactRoomName(room)
    var draft by remember(room) {
        mutableStateOf(aliases[room]?.trim().orEmpty().ifBlank { fallback })
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            "默认是 $fallback，最多 8 个字",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
        )
        Spacer(Modifier.height(8.dp))
        TextField(
            value = draft,
            onValueChange = { draft = it.take(8) },
            label = "课表格子缩写",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onSave(draft.trim()) },
            enabled = draft.trim().isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            minHeight = 44.dp,
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) { Text("保存缩写") }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onSave("") },
            modifier = Modifier.fillMaxWidth(),
            minHeight = 44.dp,
        ) { Text("恢复默认") }
    }
}
