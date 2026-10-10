package cn.edu.gzus.qingke.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.gzus.qingke.nav.TabDest
import cn.edu.gzus.qingke.ui.blur.DampedDragAnimation
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Weeks
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

val LocalQingkeWide = staticCompositionLocalOf { false }
val LocalQingkeFloatingBar = staticCompositionLocalOf { true }

private fun qingkeTabItems() = listOf(
    TabDest.Today to (MiuixIcons.Home to "首页"),
    TabDest.Timetable to (MiuixIcons.Weeks to "课表"),
    TabDest.Grades to (MiuixIcons.ListView to "成绩"),
    TabDest.Jwxt to (MiuixIcons.GridView to "服务"),
    TabDest.Mine to (MiuixIcons.Contacts to "我的"),
)

@Composable
fun rememberQingkeBackdrop(): LayerBackdrop = rememberLayerBackdrop()

fun Modifier.qingkeLayer(backdrop: LayerBackdrop): Modifier = this.layerBackdrop(backdrop)

fun tabScaffoldPadding(padding: PaddingValues, layout: LayoutDirection): PaddingValues = PaddingValues(
    start = padding.calculateStartPadding(layout),
    top = padding.calculateTopPadding(),
    end = padding.calculateEndPadding(layout),
    bottom = 0.dp,
)

@Composable
fun tabPagePadding(padding: PaddingValues): PaddingValues {
    val layout = LocalLayoutDirection.current
    return PaddingValues(
        start = padding.calculateStartPadding(layout),
        top = padding.calculateTopPadding(),
        end = padding.calculateEndPadding(layout),
        bottom = if (LocalQingkeWide.current) 24.dp else padding.calculateBottomPadding() + 12.dp,
    )
}

@Composable
fun QingkeWideFrame(fill: Boolean = false, content: @Composable () -> Unit) {
    if (!LocalQingkeWide.current || fill) {
        content()
        return
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 880.dp).fillMaxHeight()) { content() }
    }
}

@Composable
fun QingkeSideRail(selected: TabDest, onSelect: (TabDest) -> Unit) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(
        Modifier.fillMaxHeight().width(96.dp)
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .selectableGroup().verticalScroll(rememberScrollState())
            .padding(top = top + 12.dp, bottom = bottom + 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        qingkeTabItems().forEach { (dest, item) ->
            val on = dest == selected
            Column(
                Modifier.padding(horizontal = 8.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (on) MiuixTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
                    .semantics { this.selected = on }
                    .clickable(role = Role.Tab) { onSelect(dest) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(item.first, null, Modifier.size(22.dp), tint = if (on) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface)
                Text(item.second, fontSize = 12.sp, maxLines = 1, color = if (on) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
fun QingkeBottomBar(
    selected: TabDest,
    backdrop: Backdrop,
    floating: Boolean = true,
    blurEnabled: Boolean = true,
    onSelect: (TabDest) -> Unit,
) {
    val items = remember { qingkeTabItems() }
    val selectedIndex = items.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val accent = MiuixTheme.colorScheme.primary
    val contentColor = MiuixTheme.colorScheme.onSurface
    val dark = isSystemInDarkTheme()
    val surface = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 1f)
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()
    val onSelectUpdated by rememberUpdatedState(onSelect)
    var totalWidthPx by remember { mutableFloatStateOf(0f) }
    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var currentIndex by remember { mutableIntStateOf(selectedIndex) }
    val insetPx = with(density) { 4.dp.toPx() }

    fun indexAt(x: Float): Int {
        if (tabWidthPx <= 0f) return currentIndex
        val logicalX = if (isLtr) x else totalWidthPx - x
        return ((logicalX - insetPx) / tabWidthPx).toInt().coerceIn(0, items.lastIndex)
    }

    val drag = remember(animationScope, density, isLtr, floating) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selectedIndex.toFloat(),
            valueRange = 0f..items.lastIndex.toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.06f,
            canDrag = { it.x in 0f..totalWidthPx },
            onDragStarted = { updateValue(indexAt(it.x).toFloat()) },
            onDragStopped = {
                val index = targetValue.roundToInt().coerceIn(0, items.lastIndex)
                if (currentIndex != index) {
                    currentIndex = index
                    onSelectUpdated(items[index].first)
                }
                updateValue(index.toFloat())
            },
            onDragCancelled = { updateValue(currentIndex.toFloat()) },
            onDrag = { _, amount ->
                if (tabWidthPx > 0f) updateValue(
                    (targetValue + amount.x / tabWidthPx * if (isLtr) 1f else -1f).coerceIn(valueRange),
                )
            },
        )
    }
    LaunchedEffect(selectedIndex, drag) {
        currentIndex = selectedIndex
        if (drag.targetValue != selectedIndex.toFloat()) drag.animateToValue(selectedIndex.toFloat())
    }
    fun activate(index: Int) {
        if (currentIndex != index) {
            currentIndex = index
            onSelectUpdated(items[index].first)
        }
        drag.animateToValue(index.toFloat())
    }

    val navigationInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomGap = if (floating) navigationInset + 8.dp else navigationInset
    val shape = if (floating) CircleShape else RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val barHeight = if (floating) 64.dp else 60.dp
    val glass = floating && blurEnabled
    val backgroundModifier = if (glass) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = { blur(12.dp.toPx(), 12.dp.toPx()) },
            onDrawSurface = { drawRect(surface.copy(alpha = if (dark) 0.38f else 0.30f)) },
        )
    } else {
        Modifier.clip(shape).background(surface)
    }

    Column(Modifier.fillMaxWidth().then(if (floating) Modifier else Modifier.background(surface))) {
        Box(
            Modifier.padding(start = if (floating) 16.dp else 0.dp, end = if (floating) 16.dp else 0.dp, bottom = bottomGap)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.widthIn(max = 480.dp).fillMaxWidth()
                    .onSizeChanged {
                        totalWidthPx = it.width.toFloat()
                        tabWidthPx = ((totalWidthPx - 2 * insetPx) / items.size).coerceAtLeast(0f)
                    }
                    .then(if (floating) Modifier.dropShadow(shape, Shadow(radius = 12.dp, color = Color.Black, alpha = if (dark) 0.22f else 0.08f)) else Modifier)
                    .then(backgroundModifier)
                    .then(drag.modifier)
                    .height(barHeight),
            ) {
                // Selection is a single background layer. Labels stay sharp above it.
                if (tabWidthPx > 0f) {
                    Box(
                        Modifier.align(Alignment.CenterStart).padding(horizontal = 4.dp)
                            .graphicsLayer {
                                translationX = drag.value * tabWidthPx * if (isLtr) 1f else -1f
                                scaleX = drag.scaleX
                                scaleY = drag.scaleY
                            }
                            .width(with(density) { tabWidthPx.toDp() }).height(barHeight - 8.dp)
                            .clip(CircleShape).background(accent.copy(alpha = if (dark) 0.24f else 0.13f)),
                    )
                }
                Row(
                    Modifier.fillMaxSize().padding(4.dp).selectableGroup(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEachIndexed { index, item ->
                        val on = selectedIndex == index
                        val tint = if (on) accent else contentColor.copy(alpha = 0.78f)
                        Column(
                            Modifier.weight(1f).fillMaxHeight()
                                .semantics(mergeDescendants = true) {
                                    this.selected = on
                                    role = Role.Tab
                                    onClick { activate(index); true }
                                }
                                .onKeyEvent {
                                    val activation = it.key == Key.Enter || it.key == Key.NumPadEnter || it.key == Key.Spacebar
                                    if (activation && it.type == KeyEventType.KeyUp) activate(index)
                                    activation
                                }
                                .focusable(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                        ) {
                            Icon(item.second.first, null, Modifier.size(22.dp), tint = tint)
                            Text(item.second.second, color = tint, fontSize = 12.sp,
                                fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
