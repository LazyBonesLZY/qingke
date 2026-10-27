package cn.edu.gzus.qingke.ui.jwxt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.edu.gzus.qingke.data.AppSnapshot
import cn.edu.gzus.qingke.data.GZUS_ECARD_ORIGIN
import cn.edu.gzus.qingke.data.JIANGMEN_ELECTRIC_PRICE
import cn.edu.gzus.qingke.data.JIANGMEN_WATER_PRICE
import cn.edu.gzus.qingke.data.UTILITY_LOW_POWER
import cn.edu.gzus.qingke.data.UTILITY_LOW_WATER
import cn.edu.gzus.qingke.data.UtilityBind
import cn.edu.gzus.qingke.data.UtilityOption
import cn.edu.gzus.qingke.data.formatMoney
import cn.edu.gzus.qingke.data.gzusUsesCas
import cn.edu.gzus.qingke.data.hasRoom
import cn.edu.gzus.qingke.data.hasUtilityBind
import cn.edu.gzus.qingke.data.isLow
import cn.edu.gzus.qingke.data.lowPower
import cn.edu.gzus.qingke.data.lowWater
import cn.edu.gzus.qingke.data.openUrl
import cn.edu.gzus.qingke.data.parseAmount
import cn.edu.gzus.qingke.data.resolvedElectricPrice
import cn.edu.gzus.qingke.data.resolvedUtilityBind
import cn.edu.gzus.qingke.data.resolvedWaterPrice
import cn.edu.gzus.qingke.data.utilityPriceLabel
import cn.edu.gzus.qingke.nav.QingkeNavigator
import cn.edu.gzus.qingke.nav.TabDest
import cn.edu.gzus.qingke.ui.components.EmptyState
import cn.edu.gzus.qingke.ui.components.Inset
import cn.edu.gzus.qingke.ui.components.Space
import cn.edu.gzus.qingke.ui.components.StatusTag
import cn.edu.gzus.qingke.ui.components.Tone
import cn.edu.gzus.qingke.ui.components.Type
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 页面里按钮统一 44dp 高，主次只靠颜色区分。 */
private val ButtonHeight = 44.dp

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
    val waterPrice = waterText.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
    val electricPrice = electricText.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
    val priceChanged = waterPrice != snapshot.settings.utilityWaterPrice ||
        electricPrice != snapshot.settings.utilityElectricPrice ||
        !snapshot.settings.utilityUseCustomPrice
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
            .padding(bottom = Space.xl),
    ) {
        Spacer(Modifier.height(Space.s))
        Text(
            "绑定宿舍后看电和水的余额，不用登录。",
            modifier = Modifier.padding(horizontal = Inset.page),
            color = Tone.muted(),
            style = MiuixTheme.textStyles.body2,
        )
        Spacer(Modifier.height(Space.m))
        when {
            // 没绑宿舍：一张空状态把「手动搜」和「登录自动认」两条路说清楚；已经绑好了就别再劝人去登录
            !bound -> {
                val suggestLogin = !snapshot.session.loggedIn || !snapshot.settings.gzusUsesCas()
                EmptyState(
                    title = "还没绑定宿舍",
                    detail = if (suggestLogin) {
                        "在下面搜楼栋或房间号直接选；也可以用统一身份认证登录，自动认出你的宿舍。"
                    } else {
                        "在下面搜楼栋或房间号，点一下就绑定。"
                    },
                    actionText = if (suggestLogin) "去登录" else "",
                    onAction = if (suggestLogin) ({ nav.goTab(TabDest.Mine) }) else null,
                )
            }
            snapshot.utility.ready -> BalanceCard(snapshot, bind)
            else -> {
                val error = snapshot.utility.error.trim()
                EmptyState(
                    title = if (error.isBlank()) "还没有余额数据" else "这次没查到余额",
                    detail = error.ifBlank { "同步一次就能看到电和水还剩多少。" }.take(120),
                    actionText = if (busy) "" else "重新同步",
                    onAction = if (busy) null else onRefresh,
                )
            }
        }
        // 同步和充值是看完余额最常做的两件事，紧跟在余额下面；同步是主操作
        Spacer(Modifier.height(Space.m))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Inset.page),
            horizontalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            Button(
                onClick = { openUrl(GZUS_ECARD_ORIGIN) },
                modifier = Modifier.weight(1f),
                minHeight = ButtonHeight,
            ) { Text("打开一卡通") }
            Button(
                onClick = onRefresh,
                enabled = !busy && bound,
                modifier = Modifier.weight(1f),
                minHeight = ButtonHeight,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                if (busy) {
                    InfiniteProgressIndicator()
                    Spacer(Modifier.width(Space.s))
                }
                Text(if (busy) "正在同步" else "同步水电")
            }
        }
        SmallTitle(text = "宿舍")
        if (bound && !searching) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Inset.page),
                insideMargin = PaddingValues(0.dp),
            ) {
                ArrowPreference(
                    title = bind.label,
                    summary = "点这里换宿舍",
                    onClick = { searching = true },
                )
            }
        } else {
            TextField(
                value = query,
                onValueChange = { query = it },
                label = "输入楼栋或房间号搜索",
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Inset.page),
            )
            Spacer(Modifier.height(Space.s))
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Inset.page),
                insideMargin = PaddingValues(0.dp),
            ) {
                when {
                    optionsBusy -> Row(
                        modifier = Modifier.fillMaxWidth().padding(Inset.card),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.m),
                    ) {
                        InfiniteProgressIndicator()
                        Text(
                            "正在搜索 ${query.trim()}",
                            style = MiuixTheme.textStyles.body2,
                            color = Tone.muted(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    !optionsError.isNullOrBlank() -> BasicComponent(
                        title = "没搜到宿舍",
                        summary = "${optionsError.trim().trimEnd('。')}。换个楼栋或房间号再搜。",
                    )
                    query.isBlank() -> BasicComponent(
                        title = "输入关键词搜索宿舍",
                        summary = "例如楼栋号或房间号，选中即绑定",
                    )
                    shown.isEmpty() -> BasicComponent(title = "未找到宿舍", summary = "换个楼栋或房间号再搜")
                    else -> Column {
                        shown.forEach { item ->
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
                        if (options.size > shown.size) {
                            Text(
                                "只显示前 ${shown.size} 个，房间号写全一点能更快找到。",
                                modifier = Modifier.padding(Inset.card),
                                color = Tone.muted(),
                                style = MiuixTheme.textStyles.footnote1,
                            )
                        }
                    }
                }
            }
            if (bound) {
                Spacer(Modifier.height(Space.s))
                Button(
                    onClick = {
                        searching = false
                        query = ""
                        onLoadOptions("clear", "")
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Inset.page),
                    minHeight = ButtonHeight,
                ) { Text("不换了") }
            }
        }
        SmallTitle(text = "单价")
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Inset.page),
            insideMargin = PaddingValues(0.dp),
        ) {
            SwitchPreference(
                title = "自定义单价",
                summary = snapshot.settings.utilityPriceLabel(bind),
                checked = custom,
                onCheckedChange = { on ->
                    custom = on
                    // 以前直接 toDoubleOrNull，填了 0 或负数也会被开关存进去，绕过下面的校验
                    onSavePrice(on, waterPrice ?: JIANGMEN_WATER_PRICE, electricPrice ?: JIANGMEN_ELECTRIC_PRICE)
                },
            )
        }
        if (custom) {
            Spacer(Modifier.height(Space.s))
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Inset.page),
                verticalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                TextField(
                    value = waterText,
                    onValueChange = { waterText = it },
                    label = "水单价，元/吨",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = electricText,
                    onValueChange = { electricText = it },
                    label = "电单价，元/度",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (waterPrice == null || electricPrice == null) {
                Spacer(Modifier.height(Space.xs))
                Text(
                    "水、电单价都需填写大于 0 的数字",
                    modifier = Modifier.padding(horizontal = Inset.page + Space.l),
                    color = Tone.danger(),
                    style = MiuixTheme.textStyles.footnote1,
                )
            }
            Spacer(Modifier.height(Space.m))
            Button(
                onClick = {
                    if (waterPrice != null && electricPrice != null) onSavePrice(true, waterPrice, electricPrice)
                },
                enabled = waterPrice != null && electricPrice != null && priceChanged,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Inset.page),
                minHeight = ButtonHeight,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) { Text(if (priceChanged) "保存单价" else "单价已保存") }
        }
    }
}

/** 一项余额：名字、数值、单位、折算的钱，以及按余量算出的语气色。 */
private data class Meter(val name: String, val amount: String, val unit: String, val yuan: Double?, val color: Color)

/**
 * 余额卡：最要紧的一项（通常是电）用大数字，其余两项并排在下面。
 * 低于充值线用危险色，接近（两倍以内）用警示色，其余正常色，深浅色都跟随 [Tone]。
 */
@Composable
private fun BalanceCard(snapshot: AppSnapshot, fallbackBind: UtilityBind) {
    val utility = snapshot.utility
    val bind = utility.bind.takeIf { it.hasRoom() } ?: fallbackBind
    val waterPrice = snapshot.settings.resolvedWaterPrice(bind)
    val electricPrice = snapshot.settings.resolvedElectricPrice(bind)
    val normal = MiuixTheme.colorScheme.onBackground
    val warning = Tone.warning()
    val danger = Tone.danger()
    fun meter(name: String, amount: String, unit: String, price: Double?, low: Double?): Meter? {
        if (amount.isBlank()) return null
        val value = parseAmount(amount)
        val color = when {
            value == null || low == null -> normal
            value < low -> danger
            value < low * 2 -> warning
            else -> normal
        }
        return Meter(name, amount.trim(), unit, value?.let { v -> price?.let { v * it } }, color)
    }
    // 充值线和数据层保持一致：电看度数，水只看冷水
    val meters = listOfNotNull(
        meter("电", utility.power, "度", electricPrice, UTILITY_LOW_POWER),
        meter("冷水", utility.coldWater, "吨", waterPrice, UTILITY_LOW_WATER),
        meter("热水", utility.hotWater, "吨", waterPrice, null),
    )
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = Inset.page), insideMargin = Inset.card) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                bind.label.ifBlank { "已绑定宿舍" },
                modifier = Modifier.weight(1f),
                style = MiuixTheme.textStyles.footnote1,
                color = Tone.muted(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (utility.isLow()) {
                Spacer(Modifier.width(Space.s))
                StatusTag("该充值了", color = danger)
            }
        }
        val hero = meters.firstOrNull()
        if (hero == null) {
            Spacer(Modifier.height(Space.s))
            Text("一卡通没有返回余额", style = MiuixTheme.textStyles.body2, color = Tone.muted())
        } else {
            Spacer(Modifier.height(Space.s))
            Text(hero.name + "余量", style = MiuixTheme.textStyles.footnote1, color = Tone.muted())
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    hero.amount,
                    style = MiuixTheme.textStyles.title2,
                    fontSize = Type.display,
                    lineHeight = Type.displayLine,
                    fontWeight = FontWeight.SemiBold,
                    color = hero.color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(Space.xs))
                Text(
                    hero.unit,
                    modifier = Modifier.padding(bottom = Space.s),
                    style = MiuixTheme.textStyles.body2,
                    color = Tone.muted(),
                )
            }
            hero.yuan?.let {
                Text("约 ${formatMoney(it)} 元", style = MiuixTheme.textStyles.footnote1, color = Tone.muted())
            }
            val rest = meters.drop(1)
            if (rest.isNotEmpty()) {
                Spacer(Modifier.height(Space.m))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    rest.forEach { item ->
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MiuixTheme.textStyles.footnote1, color = Tone.muted())
                            Spacer(Modifier.height(Space.xs))
                            Text(
                                "${item.amount} ${item.unit}",
                                style = MiuixTheme.textStyles.title3,
                                fontWeight = FontWeight.SemiBold,
                                color = item.color,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            item.yuan?.let {
                                Text("约 ${formatMoney(it)} 元", style = MiuixTheme.textStyles.footnote1, color = Tone.muted())
                            }
                        }
                    }
                    if (rest.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        // 低于充值线时补一句具体原因，和首页「该充值了」对得上
        val lowLine = when {
            utility.lowPower() -> "电量不到 ${UTILITY_LOW_POWER.toInt()} 度，记得充值"
            utility.lowWater() -> "水量不到 ${UTILITY_LOW_WATER.toInt()} 吨，记得充值"
            else -> ""
        }
        if (lowLine.isNotBlank()) {
            Spacer(Modifier.height(Space.m))
            Text(lowLine, style = MiuixTheme.textStyles.footnote1, fontWeight = FontWeight.Medium, color = danger)
        }
    }
}
