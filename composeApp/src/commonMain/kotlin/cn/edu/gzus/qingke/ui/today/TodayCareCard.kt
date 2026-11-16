package cn.edu.gzus.qingke.ui.today

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import cn.edu.gzus.qingke.data.DailyCareHint
import cn.edu.gzus.qingke.ui.components.Inset
import cn.edu.gzus.qingke.ui.components.Space
import cn.edu.gzus.qingke.ui.components.Tone
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Unobtrusive and dismissable globally from settings. No extra notification permissions. */
@Composable
internal fun TodayCareCard(hint: DailyCareHint) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = Inset.page), insideMargin = Inset.card) {
        Column {
            Text(hint.title, style = MiuixTheme.textStyles.body2, fontWeight = FontWeight.Medium)
            Text(
                hint.detail,
                modifier = Modifier.padding(top = Space.xs),
                style = MiuixTheme.textStyles.footnote1,
                color = Tone.muted(),
            )
        }
    }
}
