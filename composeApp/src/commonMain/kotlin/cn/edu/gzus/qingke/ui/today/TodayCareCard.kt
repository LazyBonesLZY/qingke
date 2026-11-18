package cn.edu.gzus.qingke.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.edu.gzus.qingke.data.DailyCareHint
import cn.edu.gzus.qingke.ui.components.Inset
import cn.edu.gzus.qingke.ui.components.Radius
import cn.edu.gzus.qingke.ui.components.Space
import cn.edu.gzus.qingke.ui.components.Tone
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Unobtrusive and dismissable globally from settings. No extra notification permissions. */
@Composable
internal fun TodayCareCard(hint: DailyCareHint) {
    val barColor = if (hint.title.contains("考试") || hint.title.contains("换教室")) {
        Tone.warning()
    } else {
        MiuixTheme.colorScheme.primary
    }
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = Inset.page), insideMargin = Inset.card) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(Radius.pill)
                    .background(barColor),
            )
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
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
}
