package cn.edu.gzus.qingke.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import cn.edu.gzus.qingke.nav.TabDest
import cn.edu.gzus.qingke.ui.blur.DampedDragAnimation
import cn.edu.gzus.qingke.ui.blur.InnerShadow
import cn.edu.gzus.qingke.ui.blur.InteractiveHighlight
import cn.edu.gzus.qingke.ui.blur.innerShadow
import cn.edu.gzus.qingke.ui.blur.lens
import cn.edu.gzus.qingke.ui.blur.rememberCombinedBackdrop
import cn.edu.gzus.qingke.ui.blur.vibrancy
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.highlight.LightSource
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Weeks
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.Platform
import top.yukonga.miuix.kmp.utils.platform
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

private val LocalIosTabScale = staticCompositionLocalOf { { 1f } }

/** 平板横屏（或更宽的窗口）用侧栏，不再把底部胶囊拉满。 */
val LocalQingkeWide = staticCompositionLocalOf { false }

/** 悬浮底栏（true）或贴底常驻（false）。影响 tabPagePadding 的底部留白。 */
val LocalQingkeFloatingBar = staticCompositionLocalOf { true }

private fun qingkeTabItems() = listOf(
    TabDest.Today to (MiuixIcons.Home to "首页"),
    TabDest.Timetable to (MiuixIcons.Weeks to "课表"),
    TabDest.Grades to (MiuixIcons.ListView to "成绩"),
    TabDest.Jwxt to (MiuixIcons.GridView to "服务"),
    TabDest.Mine to (MiuixIcons.Contacts to "我的"),
)

private val iosIndicatorSpecular: Highlight = Highlight(
    width = 1.dp,
    alpha = 1f,
    style = BloomStroke(
        color = Color.White.copy(alpha = 0.12f),
        innerBlurRadius = 2.0.dp,
        primaryLight = LightSource(
            position = LightPosition(0.5f, -0.3f, -0.05f),
            color = Color.White,
            intensity = 1f,
        ),
        secondaryLight = LightSource(
            position = LightPosition(0.5f, 0.8f, -0.5f),
            color = Color.White,
            intensity = 0.4f,
        ),
        dualPeak = true,
    ),
)

@Composable
fun rememberQingkeBackdrop(): LayerBackdrop = rememberLayerBackdrop()

fun Modifier.qingkeLayer(backdrop: LayerBackdrop): Modifier = this.layerBackdrop(backdrop)

fun tabScaffoldPadding(padding: PaddingValues, layout: LayoutDirection): PaddingValues =
    PaddingValues(
        start = padding.calculateStartPadding(layout),
        top = padding.calculateTopPadding(),
        end = padding.calculateEndPadding(layout),
        bottom = 0.dp,
    )

@Composable
fun tabPagePadding(padding: PaddingValues): PaddingValues {
    val layout = LocalLayoutDirection.current
    val base = tabScaffoldPadding(padding, layout)
    val bottom = when {
        LocalQingkeWide.current -> 24.dp
        LocalQingkeFloatingBar.current -> 96.dp
        else -> 0.dp
    }
    return PaddingValues(
        start = base.calculateStartPadding(layout),
        top = base.calculateTopPadding(),
        end = base.calculateEndPadding(layout),
        bottom = bottom,
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
fun QingkeSideRail(
    selected: TabDest,
    onSelect: (TabDest) -> Unit,
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(96.dp)
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .verticalScroll(rememberScrollState())
            .padding(top = top + 12.dp, bottom = bottom + 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        qingkeTabItems().forEach { (dest, item) ->
            val on = dest == selected
            Column(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (on) MiuixTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable { onSelect(dest) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = item.first,
                    contentDescription = item.second,
                    modifier = Modifier.size(22.dp),
                    tint = if (on) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    item.second,
                    fontSize = 11.sp,
                    maxLines = 1,
                    color = if (on) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
fun QingkeBottomBar(
    selected: TabDest,
    backdrop: LayerBackdrop,
    floating: Boolean = true,
    blurEnabled: Boolean = true,
    onSelect: (TabDest) -> Unit,
) {
    val items = qingkeTabItems()
    val selectedIndex = items.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val pillShape = remember { CircleShape }
    val accentColor = MiuixTheme.colorScheme.primary
    val tabContentColor = MiuixTheme.colorScheme.onSurface
    val dark = isSystemInDarkTheme()
    val containerColor = if (dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.16f)
    val tabsBackdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()
    val tabsCount = items.size
    val onSelectUpdated by rememberUpdatedState(onSelect)
    val itemsUpdated by rememberUpdatedState(items)

    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }
    val offsetAnimation = remember { Animatable(0f) }
    val rubberBandPx = with(density) { 4.dp.toPx() }
    val panelOffset by remember(rubberBandPx) {
        derivedStateOf {
            if (totalWidthPx == 0f) {
                0f
            } else {
                val fraction = (offsetAnimation.value / totalWidthPx).coerceIn(-1f, 1f)
                rubberBandPx * fraction.sign * EaseOut.transform(abs(fraction))
            }
        }
    }

    var currentIndex by remember { mutableIntStateOf(selectedIndex) }

    fun indexAt(positionX: Float): Int {
        if (tabWidthPx == 0f) return currentIndex
        val horizontalPaddingPx = with(density) { 4.dp.toPx() }
        val logicalX = if (isLtr) positionX else totalWidthPx - positionX
        return ((logicalX - horizontalPaddingPx) / tabWidthPx)
            .toInt()
            .coerceIn(0, tabsCount - 1)
    }

    val dampedDrag = remember(animationScope, tabsCount, density, isLtr) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selectedIndex.toFloat(),
            valueRange = 0f..(tabsCount - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 78f / 56f,
            canDrag = { position ->
                position.x in 0f..totalWidthPx
            },
            onDragStarted = { position ->
                updateValue(indexAt(position.x).toFloat())
            },
            onDragStopped = {
                val targetIndex = targetValue.roundToInt().coerceIn(0, tabsCount - 1)
                if (currentIndex != targetIndex) {
                    currentIndex = targetIndex
                    itemsUpdated.getOrNull(targetIndex)?.first?.let(onSelectUpdated)
                }
                updateValue(targetIndex.toFloat())
                animationScope.launch {
                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                }
            },
            onDragCancelled = {
                updateValue(currentIndex.toFloat())
                animationScope.launch {
                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                }
            },
            onDrag = { _, dragAmount ->
                if (tabWidthPx > 0f && dragAmount.x != 0f) {
                    updateValue(
                        (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                            .coerceIn(0f, (tabsCount - 1).toFloat()),
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            },
        )
    }

    LaunchedEffect(selectedIndex) {
        if (currentIndex != selectedIndex) {
            currentIndex = selectedIndex
            dampedDrag.animateToValue(selectedIndex.toFloat())
        }
    }

    fun activateTab(index: Int) {
        if (currentIndex != index) {
            currentIndex = index
            itemsUpdated.getOrNull(index)?.first?.let(onSelectUpdated)
        }
        dampedDrag.animateToValue(index.toFloat())
    }

    val interactiveHighlight = remember(animationScope, isLtr, dampedDrag) {
        InteractiveHighlight(
            animationScope = animationScope,
            position = { layerSize, _ ->
                Offset(
                    x = if (isLtr) {
                        (dampedDrag.value + 0.5f) * tabWidthPx + panelOffset
                    } else {
                        layerSize.width - (dampedDrag.value + 0.5f) * tabWidthPx + panelOffset
                    },
                    y = layerSize.height / 2f,
                )
            },
        )
    }

    val combinedBackdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop)

    val navBarBottomPadding = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
    val bottomPaddingValue = when {
        !floating -> navBarBottomPadding
        else -> when (platform()) {
            Platform.IOS -> 20.dp
            else -> if (navBarBottomPadding != 0.dp) 8.dp + navBarBottomPadding else 36.dp
        }
    }
    val barHorizontalPadding = if (floating) 24.dp else 0.dp
    val dockedShape = if (floating) pillShape else RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val dockedContainer = MiuixTheme.colorScheme.surfaceContainer

    val tabsContent: @Composable RowScope.() -> Unit = {
        val tabScale = LocalIosTabScale.current
        items.forEachIndexed { index, item ->
            Column(
                modifier = Modifier
                    .semantics(mergeDescendants = true) {
                        this.selected = index == currentIndex
                        this.role = Role.Tab
                        onClick {
                            activateTab(index)
                            true
                        }
                    }
                    .onKeyEvent { event ->
                        val isActivationKey = event.key == Key.Enter ||
                            event.key == Key.NumPadEnter ||
                            event.key == Key.Spacebar
                        if (isActivationKey) {
                            if (event.type == KeyEventType.KeyUp) activateTab(index)
                            true
                        } else {
                            false
                        }
                    }
                    .focusable()
                    .weight(1f)
                    .fillMaxHeight()
                    .graphicsLayer {
                        val s = tabScale()
                        scaleX = s
                        scaleY = s
                    },
                verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
                horizontalAlignment = CenterHorizontally,
            ) {
                Icon(
                    modifier = Modifier.size(22.dp),
                    imageVector = item.second.first,
                    contentDescription = null,
                )
                Text(
                    text = item.second.second,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .then(if (floating) Modifier else Modifier.background(dockedContainer)),
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = bottomPaddingValue, start = barHorizontalPadding, end = barHorizontalPadding)
                .then(if (floating) Modifier else Modifier.padding(top = 6.dp, bottom = 4.dp))
                .fillMaxWidth(),
            contentAlignment = Alignment.CenterStart,
        ) {
            CompositionLocalProvider(LocalContentColor provides tabContentColor) {
                Row(
                    modifier = Modifier
                        .selectableGroup()
                        .onSizeChanged { coords ->
                            totalWidthPx = coords.width.toFloat()
                            val contentWidthPx = totalWidthPx - with(density) { 8.dp.toPx() }
                            tabWidthPx = (contentWidthPx / tabsCount).coerceAtLeast(0f)
                        }
                        .graphicsLayer { translationX = panelOffset }
                        .then(
                            if (floating) {
                                Modifier.dropShadow(
                                    shape = pillShape,
                                    shadow = Shadow(
                                        radius = 16.dp,
                                        color = Color.Black,
                                        alpha = if (dark) 0.28f else 0.05f,
                                    ),
                                )
                            } else {
                                Modifier
                            },
                        )
                        .then(
                            when {
                                floating && blurEnabled -> Modifier.drawBackdrop(
                                    backdrop = backdrop,
                                    shape = { pillShape },
                                    effects = {
                                        padding = maxOf(padding, 28.dp.toPx())
                                        vibrancy()
                                        blur(4.dp.toPx(), 4.dp.toPx())
                                        lens(
                                            refractionHeight = 20.dp.toPx(),
                                            refractionAmount = 20.dp.toPx(),
                                        )
                                    },
                                    highlight = { iosIndicatorSpecular.copy(alpha = 0.75f) },
                                    layerBlock = {
                                        val width = size.width.coerceAtLeast(1f)
                                        val s = lerp(1f, 1f + 16.dp.toPx() / width, dampedDrag.pressProgress)
                                        scaleX = s
                                        scaleY = s
                                    },
                                    onDrawSurface = { drawRect(containerColor) },
                                )
                                floating -> Modifier
                                    .clip(pillShape)
                                    .background(
                                        if (dark) Color(0xFF2C2C2C).copy(alpha = 0.92f)
                                        else Color.White.copy(alpha = 0.92f),
                                    )
                                else -> Modifier.clip(dockedShape)
                            },
                        )
                        .then(interactiveHighlight.modifier)
                        .then(interactiveHighlight.gestureModifier)
                        .then(dampedDrag.modifier)
                        .height(if (floating) 64.dp else 60.dp)
                        .then(
                            if (floating) Modifier.padding(4.dp)
                            else Modifier.padding(horizontal = 4.dp),
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    content = tabsContent,
                )
            }

            CompositionLocalProvider(
                LocalIosTabScale provides { lerp(1f, 1.2f, dampedDrag.pressProgress) },
                LocalContentColor provides accentColor,
            ) {
                Row(
                    modifier = Modifier
                        .clearAndSetSemantics {}
                        .alpha(0f)
                        .layerBackdrop(tabsBackdrop)
                        .graphicsLayer { translationX = panelOffset }
                        .then(
                            if (floating && blurEnabled) {
                                Modifier.drawBackdrop(
                                    backdrop = backdrop,
                                    shape = { pillShape },
                                    effects = {
                                        vibrancy()
                                        blur(4.dp.toPx(), 4.dp.toPx())
                                        lens(
                                            refractionHeight = 20.dp.toPx(),
                                            refractionAmount = 20.dp.toPx(),
                                        )
                                    },
                                    onDrawSurface = { drawRect(containerColor) },
                                )
                            } else {
                                Modifier
                            },
                        )
                        .then(interactiveHighlight.modifier)
                        .height(if (floating) 56.dp else 52.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = tabsContent,
                )
            }

            if (tabWidthPx > 0f) {
                val tabWidthDp = with(density) { tabWidthPx.toDp() }
                val indicatorBg = if (floating) {
                    Modifier.drawBackdrop(
                        backdrop = combinedBackdrop,
                        shape = { pillShape },
                        effects = {
                            val progress = dampedDrag.pressProgress
                            lens(
                                refractionHeight = 8.dp.toPx() * progress,
                                refractionAmount = 10.dp.toPx() * progress,
                            )
                        },
                        highlight = { iosIndicatorSpecular.copy(alpha = dampedDrag.pressProgress) },
                        layerBlock = {
                            scaleX = dampedDrag.scaleX
                            scaleY = dampedDrag.scaleY
                            val v = dampedDrag.velocity / 10f
                            scaleX /= 1f - (v * 0.75f).coerceIn(-0.2f, 0.2f)
                            scaleY *= 1f - (v * 0.25f).coerceIn(-0.2f, 0.2f)
                        },
                        onDrawSurface = {
                            val progress = dampedDrag.pressProgress
                            drawRect(
                                color = if (dark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.48f),
                                alpha = 1f - progress,
                            )
                            drawRect(Color.White.copy(alpha = (if (dark) 0.06f else 0.10f) * progress))
                        },
                    )
                } else {
                    // docked：轻量 accent 胶囊，不做 backdrop 变形
                    Modifier
                        .clip(pillShape)
                        .background(accentColor.copy(alpha = if (dark) 0.22f else 0.14f))
                }
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .graphicsLayer {
                            val progressOffset = dampedDrag.value * tabWidthPx
                            translationX = if (isLtr) progressOffset + panelOffset else -progressOffset + panelOffset
                        }
                        .then(indicatorBg)
                        .then(
                            if (floating) {
                                Modifier.innerShadow(shape = pillShape) {
                                    InnerShadow(
                                        radius = 8.dp * dampedDrag.pressProgress,
                                        color = Color.Black.copy(alpha = 0.15f),
                                        alpha = dampedDrag.pressProgress,
                                    )
                                }
                            } else {
                                Modifier
                            },
                        )
                        .height(if (floating) 56.dp else 52.dp)
                        .width(tabWidthDp),
                )
            }
        }
    }
}
