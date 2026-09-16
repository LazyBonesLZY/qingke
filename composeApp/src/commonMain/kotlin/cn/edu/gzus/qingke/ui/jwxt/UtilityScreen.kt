package cn.edu.gzus.qingke.ui.jwxt

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.edu.gzus.qingke.data.AppSnapshot
import cn.edu.gzus.qingke.data.GZUS_ECARD_ORIGIN
import cn.edu.gzus.qingke.data.JIANGMEN_ELECTRIC_PRICE
import cn.edu.gzus.qingke.data.JIANGMEN_WATER_PRICE
import cn.edu.gzus.qingke.data.UtilityBind
import cn.edu.gzus.qingke.data.UtilityOption
import cn.edu.gzus.qingke.data.gzusUsesCas
import cn.edu.gzus.qingke.data.hasUtilityBind
import cn.edu.gzus.qingke.data.openUrl
import cn.edu.gzus.qingke.data.resolvedElectricPrice
import cn.edu.gzus.qingke.data.resolvedUtilityBind
import cn.edu.gzus.qingke.data.resolvedWaterPrice
import cn.edu.gzus.qingke.data.utilityBrief
import cn.edu.gzus.qingke.nav.QingkeNavigator
import cn.edu.gzus.qingke.nav.TabDest
import cn.edu.gzus.qingke.ui.components.InfoCard
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun UtilityScreen(
    snapshot: AppSnapshot,
    nav: QingkeNavigator,
    contentPadding: PaddingValues,
    busy: Boolean,
    options: List<UtilityOption>,
    optionsBusy: Boolean,
    optionsError: String?,
    onLoadOptions: (String, String) -> Unit,
    onSaveBind: (UtilityBind) -> Unit,
    onSavePrice: (Boolean, Double, Double) -> Unit,
    onRefresh: () -> Unit,
    onPeekBind: () -> Unit = {},
) {
    val bind = snapshot.settings.resolvedUtilityBind()
    val waterPrice = snapshot.settings.resolvedWaterPrice()
    val electricPrice = snapshot.settings.resolvedElectricPrice()
    var custom by remember(snapshot.settings.utilityUseCustomPrice) { mutableStateOf(snapshot.settings.utilityUseCustomPrice) }
    var waterText by remember(snapshot.settings.utilityWaterPrice) {
        mutableStateOf(snapshot.settings.utilityWaterPrice.toString())
    }
    var electricText by remember(snapshot.settings.utilityElectricPrice) {
        mutableStateOf(snapshot.settings.utilityElectricPrice.toString())
    }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    val bound = snapshot.settings.hasUtilityBind()
    val shown = options.take(100)
    LaunchedEffect(snapshot.session.loggedIn, bound) {
        if (snapshot.session.loggedIn && snapshot.settings.gzusUsesCas() && !bound) {
            onPeekBind()
        }
    }
    LaunchedEffect(query, searching, bound) {
        if (bound && !searching) return@LaunchedEffect
        val q = query.trim()
        if (q.isBlank()) {
            onLoadOptions("clear", "")
            return@LaunchedEffect
        }
        delay(400)
        onLoadOptions("search", q)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(bottom = 24.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            "绑定后可查看电费、冷水和热水余额。",
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            style = MiuixTheme.textStyles.body2,
        )
        Spacer(Modifier.height(12.dp))
        if (!snapshot.settings.gzusUsesCas()) {
            InfoCard(
                title = "还不能看水电",
                summary = "「我的」里用统一身份认证登录。",
                modifier = Modifier.padding(horizontal = 16.dp),
                onClick = { nav.goTab(TabDest.Mine) },
            )
        } else if (!snapshot.session.loggedIn) {
            InfoCard(
                title = "未绑定宿舍",
                summary = "登录后再搜索宿舍。",
                modifier = Modifier.padding(horizontal = 16.dp),
                onClick = { nav.goTab(TabDest.Mine) },
            )
        } else {
            InfoCard(
                title = if (!bound) "未绑定宿舍" else bind.label.ifBlank { "已绑定宿舍" },
                summary = if (!bound) "搜索并选择你的宿舍" else snapshot.utilityBrief(),
                modifier = Modifier.padding(horizontal = 16.dp),
                height = null,
            )
        }
        SmallTitle(text = "宿舍")
        if (bound && !searching) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                insideMargin = PaddingValues(0.dp),
            ) {
                ArrowPreference(
                    title = "已绑定：${bind.label}",
                    summary = "点更换宿舍重新搜索",
                    onClick = { searching = true },
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { searching = true },
                enabled = snapshot.session.loggedIn,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                minHeight = 44.dp,
            ) { Text("更换宿舍") }
        } else {
            TextField(
                value = query,
                onValueChange = { query = it },
                label = "输入楼栋或房间号搜索",
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            if (bound) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        searching = false
                        query = ""
                        onLoadOptions("clear", "")
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    minHeight = 44.dp,
                ) { Text("取消") }
            }
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                insideMargin = PaddingValues(0.dp),
            ) {
                when {
                    optionsBusy -> ArrowPreference(title = "正在搜索", summary = query.trim(), onClick = {})
                    !optionsError.isNullOrBlank() -> ArrowPreference(
                        title = optionsError ?: "宿舍列表加载失败",
                        summary = "换个楼栋或房间号再搜",
                        onClick = {},
                    )
                    query.isBlank() -> ArrowPreference(
                        title = "请输入关键词搜索宿舍",
                        summary = "搜索并选择你的宿舍",
                        onClick = {},
                    )
                    shown.isEmpty() -> ArrowPreference(title = "未找到宿舍", summary = "换个楼栋或房间号再搜", onClick = {})
                    else -> shown.forEach { item ->
                        ArrowPreference(
                            title = item.name,
                            summary = listOf(item.areaName, item.buildingName, item.roomName)
                                .filter { it.isNotBlank() && it != item.name }
                                .joinToString(" ")
                                .ifBlank { "点选绑定" },
                            onClick = {
                                onSaveBind(
                                    UtilityBind(
                                        areaId = item.areaId,
                                        areaName = item.areaName,
                                        buildingId = item.buildingId,
                                        buildingName = item.buildingName.ifBlank { item.buildingId },
                                        roomId = item.roomId.ifBlank { item.id },
                                        roomName = item.roomName.ifBlank { item.name },
                                    ),
                                )
                                searching = false
                                query = ""
                                onRefresh()
                            },
                        )
                    }
                }
            }
        }
        SmallTitle(text = "单价")
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            insideMargin = PaddingValues(0.dp),
        ) {
            SwitchPreference(
                title = "自定义单价",
                summary = if (custom) "按下面填的算" else "江门校区 · 水 ${JIANGMEN_WATER_PRICE} 元/吨 · 电 ${JIANGMEN_ELECTRIC_PRICE} 元/度",
                checked = custom,
                onCheckedChange = { on ->
                    custom = on
                    onSavePrice(on, waterText.toDoubleOrNull() ?: JIANGMEN_WATER_PRICE, electricText.toDoubleOrNull() ?: JIANGMEN_ELECTRIC_PRICE)
                },
            )
        }
        if (custom) {
            Spacer(Modifier.height(8.dp))
            TextField(
                value = waterText,
                onValueChange = { waterText = it },
                label = "水单价，元/吨",
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            TextField(
                value = electricText,
                onValueChange = { electricText = it },
                label = "电单价，元/度",
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    onSavePrice(
                        true,
                        waterText.toDoubleOrNull() ?: JIANGMEN_WATER_PRICE,
                        electricText.toDoubleOrNull() ?: JIANGMEN_ELECTRIC_PRICE,
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                minHeight = 44.dp,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) { Text("保存单价") }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onRefresh,
            enabled = !busy && snapshot.session.loggedIn,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            minHeight = 44.dp,
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) { Text(if (busy) "正在同步" else "同步水电") }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { openUrl(GZUS_ECARD_ORIGIN) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            minHeight = 44.dp,
        ) { Text("打开一卡通") }
    }
}
