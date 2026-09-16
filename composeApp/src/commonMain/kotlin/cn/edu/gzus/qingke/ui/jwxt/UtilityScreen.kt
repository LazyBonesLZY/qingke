package cn.edu.gzus.qingke.ui.jwxt

import androidx.compose.foundation.layout.Arrangement
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
import cn.edu.gzus.qingke.data.formatMoney
import cn.edu.gzus.qingke.data.gzusUsesCas
import cn.edu.gzus.qingke.data.openUrl
import cn.edu.gzus.qingke.data.powerYuan
import cn.edu.gzus.qingke.data.resolvedElectricPrice
import cn.edu.gzus.qingke.data.resolvedUtilityBind
import cn.edu.gzus.qingke.data.resolvedWaterPrice
import cn.edu.gzus.qingke.data.waterYuan
import cn.edu.gzus.qingke.nav.QingkeNavigator
import cn.edu.gzus.qingke.nav.TabDest
import cn.edu.gzus.qingke.ui.components.EmptyHint
import cn.edu.gzus.qingke.ui.components.InfoCard
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
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
) {
    val utility = snapshot.utility
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
    var picking by remember { mutableStateOf<String?>(null) }
    var roomFilter by remember { mutableStateOf("") }
    val powerYuan = utility.powerYuan(electricPrice)
    val waterYuan = utility.waterYuan(waterPrice)
    val shown = if (picking == "room" && roomFilter.isNotBlank()) {
        options.filter { it.name.contains(roomFilter) || it.id.contains(roomFilter) }
    } else {
        options
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
            "宿舍从一卡通目录里选：校区、楼栋、楼层、房间。不用手填编号。",
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            style = MiuixTheme.textStyles.body2,
        )
        Spacer(Modifier.height(12.dp))
        if (!snapshot.settings.gzusUsesCas()) {
            InfoCard(
                title = "还不能同步水电",
                summary = "「我的」里用统一身份认证登录。",
                modifier = Modifier.padding(horizontal = 16.dp),
                onClick = { nav.goTab(TabDest.Mine) },
            )
        } else if (!snapshot.session.loggedIn) {
            InfoCard(
                title = "还没有水电数据",
                summary = "登录门户后才能拉宿舍列表。",
                modifier = Modifier.padding(horizontal = 16.dp),
                onClick = { nav.goTab(TabDest.Mine) },
            )
        } else if (!utility.ready) {
            EmptyHint(utility.error.ifBlank { "先选宿舍，再同步水电" })
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InfoCard(
                    title = bind.label.ifBlank { listOf(utility.building, utility.room).filter { it.isNotBlank() }.joinToString(" ") }.ifBlank { "已绑定宿舍" },
                    summary = buildString {
                        if (utility.power.isNotBlank()) {
                            append("电 ${utility.power} 度")
                            if (powerYuan != null) append(" · 约 ${formatMoney(powerYuan)} 元")
                            append('\n')
                        }
                        val waters = listOfNotNull(
                            utility.coldWater.takeIf { it.isNotBlank() }?.let { "冷水 $it 吨" },
                            utility.hotWater.takeIf { it.isNotBlank() }?.let { "热水 $it 吨" },
                        )
                        if (waters.isNotEmpty()) {
                            append(waters.joinToString(" / "))
                            if (waterYuan != null) append(" · 约 ${formatMoney(waterYuan)} 元")
                        }
                    }.ifBlank { "已同步，还没有读到度数" },
                    height = null,
                )
            }
        }
        SmallTitle(text = "选择宿舍")
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            insideMargin = PaddingValues(0.dp),
        ) {
            ArrowPreference(
                title = "校区",
                summary = bind.areaName.ifBlank { "从目录里选" },
                onClick = {
                    picking = if (picking == "area") null else "area"
                    roomFilter = ""
                    if (picking == "area") onLoadOptions("area", "")
                },
            )
            ArrowPreference(
                title = "楼栋",
                summary = bind.buildingName.ifBlank { if (bind.areaId.isBlank()) "先选校区" else "从目录里选" },
                onClick = {
                    if (bind.areaId.isBlank() && bind.areaName.isBlank()) return@ArrowPreference
                    picking = if (picking == "building") null else "building"
                    roomFilter = ""
                    if (picking == "building") onLoadOptions("building", bind.areaId.ifBlank { bind.areaName })
                },
            )
            ArrowPreference(
                title = "楼层",
                summary = bind.floorName.ifBlank { if (bind.buildingId.isBlank() && bind.buildingName.isBlank()) "先选楼栋" else "从目录里选" },
                onClick = {
                    if (bind.buildingId.isBlank() && bind.buildingName.isBlank()) return@ArrowPreference
                    picking = if (picking == "floor") null else "floor"
                    roomFilter = ""
                    if (picking == "floor") onLoadOptions("floor", bind.buildingId.ifBlank { bind.buildingName })
                },
            )
            ArrowPreference(
                title = "房间",
                summary = bind.roomName.ifBlank { if (bind.floorId.isBlank() && bind.floorName.isBlank()) "先选楼层" else "从目录里选" },
                onClick = {
                    if (bind.floorId.isBlank() && bind.floorName.isBlank()) return@ArrowPreference
                    picking = if (picking == "room") null else "room"
                    roomFilter = ""
                    if (picking == "room") onLoadOptions("room", bind.floorId.ifBlank { bind.floorName })
                },
            )
        }
        if (picking != null) {
            Spacer(Modifier.height(8.dp))
            if (picking == "room") {
                TextField(
                    value = roomFilter,
                    onValueChange = { roomFilter = it },
                    label = "在列表里筛选房间",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(8.dp))
            }
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                insideMargin = PaddingValues(0.dp),
            ) {
                when {
                    optionsBusy -> ArrowPreference(title = "正在加载目录", summary = "一卡通宿舍列表", onClick = {})
                    !optionsError.isNullOrBlank() -> ArrowPreference(title = "目录没加载出来", summary = optionsError, onClick = {
                        val parent = when (picking) {
                            "building" -> bind.areaId.ifBlank { bind.areaName }
                            "floor" -> bind.buildingId.ifBlank { bind.buildingName }
                            "room" -> bind.floorId.ifBlank { bind.floorName }
                            else -> ""
                        }
                        onLoadOptions(picking ?: "area", parent)
                    })
                    shown.isEmpty() -> ArrowPreference(title = "这一层没有选项", summary = "换上一层再试，或打开一卡通核对", onClick = { openUrl(GZUS_ECARD_ORIGIN) })
                    else -> shown.forEach { item ->
                        ArrowPreference(
                            title = item.name,
                            summary = when {
                                bindLevelSelected(bind, item) -> "当前"
                                else -> item.id.takeIf { it.isNotBlank() && it != item.name }.orEmpty()
                            }.ifBlank { "点选" },
                            onClick = {
                                val next = when (picking) {
                                    "area" -> UtilityBind(areaId = item.id, areaName = item.name)
                                    "building" -> bind.copy(
                                        buildingId = item.id,
                                        buildingName = item.name,
                                        floorId = "",
                                        floorName = "",
                                        roomId = "",
                                        roomName = "",
                                    )
                                    "floor" -> bind.copy(
                                        floorId = item.id,
                                        floorName = item.name,
                                        roomId = "",
                                        roomName = "",
                                    )
                                    else -> bind.copy(roomId = item.id, roomName = item.name)
                                }
                                onSaveBind(next)
                                val after = picking
                                picking = null
                                roomFilter = ""
                                if (after == "room") onRefresh()
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

private fun bindLevelSelected(bind: UtilityBind, item: UtilityOption): Boolean =
    item.id == bind.areaId || item.id == bind.buildingId || item.id == bind.floorId || item.id == bind.roomId ||
        item.name == bind.areaName || item.name == bind.buildingName || item.name == bind.floorName || item.name == bind.roomName
