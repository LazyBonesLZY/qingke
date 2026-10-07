package cn.edu.gzus.qingke.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * 下拉刷新容器。
 *
 * 指示器的视觉、阻尼曲线、文案节奏和触觉反馈都照搬 compose-miuix-ui/miuix 的
 * PullToRefresh（Apache-2.0，见文件末尾说明），保证与 miuix 原生观感逐帧一致：
 * 下拉画圆环 → 过阈值画"拉伸胶囊" → 刷新中圆环 + 轨道点匀速旋转 → 完成时缩放淡出。
 *
 * 与 miuix 原版唯一的行为差异：**刷新期间不消费嵌套滚动**。
 * miuix 在 RefreshState.Refreshing / RefreshComplete 时 onPreScroll 与 onPostScroll
 * 直接 `return available`，把滚动全部吃掉，同步期间列表被锁死、手指滑不动。
 * 这里改成返回 Offset.Zero，把滚动原样交还 content。
 */
private const val MAX_DRAWRATIO = 1 / 6f
private const val THRESHOLD_RADIO = 1 / 4f
private const val REFRESH_THRESHOLD = 0.25f

/** 与 miuix [top.yukonga.miuix.kmp.basic.RefreshState] 对应的视觉状态。 */
private enum class PullPhase { Idle, Pulling, ThresholdReached, Refreshing, RefreshComplete }

private class PullState {
    var maxDragDistancePx = 0f
    var fullDragRangePx = 0f
    var refreshThresholdOffset = 0f
    var triggerProgressOffset = 0f

    var dragOffset by mutableFloatStateOf(0f)
    var currentTouch by mutableFloatStateOf(0f)
    var phase by mutableStateOf(PullPhase.Idle)
    var isRebounding by mutableStateOf(false)
    var refreshCompleteAnimProgress by mutableFloatStateOf(1f)

    var isGestureActive = false
    var isProcessingRelease = false
    val springEngine = QingkeSpringEngine()
    var animationJob: Job? = null
}

@Composable
fun QingkePullRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    refreshTexts: List<String> = listOf("下拉同步", "松手同步", "正在同步", "同步好了"),
    modifier: Modifier = Modifier,
    color: Color = Color.Gray,
    circleSize: Dp = 20.dp,
    refreshTextStyle: TextStyle = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = color,
    ),
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val containerHeightPx = constraints.maxHeight.toFloat()
        val state = remember { PullState() }
        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current
        val currentOnRefresh by rememberUpdatedState(onRefresh)

        // 阈值随容器尺寸刷新，与 miuix rememberPullToRefreshState 的推导一致。
        state.maxDragDistancePx = containerHeightPx
        state.fullDragRangePx = obtainDampingDistance(1f, containerHeightPx)
        state.refreshThresholdOffset = containerHeightPx * MAX_DRAWRATIO * THRESHOLD_RADIO
        state.triggerProgressOffset = REFRESH_THRESHOLD * state.fullDragRangePx

        // connection / pointerInput 都是长生命周期闭包，用 State 读最新 refreshing，避免捕获旧值。
        val refreshingState = rememberUpdatedState(refreshing)

        val connection = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    // 刷新中/收尾中：一个像素都不拦，滚动全交还 content（修掉硬控的关键）。
                    if (state.phase == PullPhase.Refreshing || state.phase == PullPhase.RefreshComplete) {
                        return Offset.Zero
                    }
                    // 指示器可见时往上推：先收回已拉出的距离。
                    if (source == NestedScrollSource.UserInput && state.isGestureActive &&
                        available.y < 0f && (state.dragOffset > 0f || state.currentTouch > 0f)
                    ) {
                        state.animationJob?.cancel()
                        applyDrag(state, available.y)
                        return Offset(0f, available.y)
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (state.phase == PullPhase.Refreshing || state.phase == PullPhase.RefreshComplete) {
                        return Offset.Zero
                    }
                    // content 已到顶、仍有剩余下拉量：带阻尼地累积成下拉距离。
                    if (source == NestedScrollSource.UserInput && state.isGestureActive && available.y > 0f) {
                        state.animationJob?.cancel()
                        applyDrag(state, available.y)
                        return Offset(0f, available.y)
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity =
                    if (state.phase == PullPhase.Idle || state.phase == PullPhase.Refreshing) {
                        Velocity.Zero
                    } else {
                        available
                    }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    if (state.phase == PullPhase.Idle || state.phase == PullPhase.Refreshing) return Velocity.Zero
                    if (
                        state.phase != PullPhase.RefreshComplete &&
                        !state.isRebounding &&
                        state.dragOffset > 0f &&
                        state.dragOffset < state.triggerProgressOffset
                    ) {
                        try {
                            state.isRebounding = true
                            animateToSpring(state, 0f)
                        } finally {
                            state.isRebounding = false
                            if (!state.isGestureActive) resetToIdle(state)
                        }
                    }
                    return Velocity.Zero
                }
            }
        }

        // 手势会话：miuix 用 PanStart/PanMove/PanEnd 判定，松手时结算。
        val pointerModifier = remember {
            Modifier.pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val active = when (event.type) {
                            PointerEventType.PanStart, PointerEventType.PanMove -> true
                            PointerEventType.PanEnd -> false
                            PointerEventType.Scroll -> state.isGestureActive
                            else -> event.changes.any { it.pressed && it.type != PointerType.Mouse }
                        }
                        if (state.isGestureActive != active) {
                            state.isGestureActive = active
                            if (
                                !active &&
                                (state.phase == PullPhase.Pulling || state.phase == PullPhase.ThresholdReached)
                            ) {
                                scope.launch { handlePointerRelease(state, currentOnRefresh, refreshingState) }
                            }
                        }
                    }
                }
            }
        }

        // 与外部 refreshing 双向同步：进入刷新时把指示器撑开，结束播放收尾动画。
        LaunchedEffect(refreshing, state.phase) {
            if (!refreshing && state.phase == PullPhase.Refreshing) {
                scope.launch { finishRefreshing(state) }
            } else if (refreshing && state.phase == PullPhase.Idle) {
                scope.launch { showRefreshing(state) }
            }
        }

        // 阈值处触觉反馈，与 miuix 一致。
        LaunchedEffect(state.phase) {
            if (state.phase == PullPhase.ThresholdReached) {
                haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            }
        }

        Box(modifier = Modifier.fillMaxSize().nestedScroll(connection).then(pointerModifier)) {
            Column {
                RefreshHeader(
                    state = state,
                    circleSize = circleSize,
                    color = color,
                    refreshTexts = refreshTexts,
                    refreshTextStyle = refreshTextStyle,
                    modifier = Modifier.offset(y = contentPadding.calculateTopPadding()),
                )
                content()
            }
        }
    }
}

/** 按 miuix 的阻尼模型累积下拉位移。 */
private fun applyDrag(state: PullState, delta: Float) {
    if (delta == 0f) return
    state.currentTouch += delta
    state.currentTouch = state.currentTouch.coerceIn(-state.maxDragDistancePx, state.maxDragDistancePx)

    val normalized = min(abs(state.currentTouch) / state.maxDragDistancePx, 1.0f)
    val damped = obtainDampingDistance(normalized, state.maxDragDistancePx)
    state.dragOffset = sign(state.currentTouch) * damped

    state.phase = when {
        state.dragOffset > 0f && state.dragOffset >= state.triggerProgressOffset -> PullPhase.ThresholdReached
        state.dragOffset > 0f -> PullPhase.Pulling
        else -> PullPhase.Idle
    }
}

private fun resetToIdle(state: PullState) {
    if (state.phase != PullPhase.Refreshing && state.phase != PullPhase.RefreshComplete) {
        state.phase = PullPhase.Idle
    }
}

private suspend fun handlePointerRelease(
    state: PullState,
    onRefresh: () -> Unit,
    refreshingState: State<Boolean>,
) {
    if (state.isProcessingRelease || state.phase == PullPhase.Refreshing || state.isRebounding) return
    state.isProcessingRelease = true
    try {
        if (state.dragOffset > 0f && state.dragOffset >= state.triggerProgressOffset) {
            startRefreshing(state, onRefresh, refreshingState)
        } else {
            try {
                state.isRebounding = true
                animateToSpring(state, 0f)
            } finally {
                state.isRebounding = false
                if (!state.isGestureActive) resetToIdle(state)
            }
        }
    } finally {
        state.isProcessingRelease = false
    }
}

private suspend fun startRefreshing(
    state: PullState,
    onRefresh: () -> Unit,
    refreshingState: State<Boolean>,
) {
    animateToSpring(state, state.refreshThresholdOffset)
    if (!state.isGestureActive) {
        state.phase = PullPhase.Refreshing
        if (!refreshingState.value) onRefresh()
    }
}

/** 外部把 refreshing 置 true 且当前 Idle 时，程序化撑开指示器。 */
private suspend fun showRefreshing(state: PullState) {
    if (state.phase != PullPhase.Idle) return
    state.phase = PullPhase.Refreshing
    var guard = 0
    while (state.phase == PullPhase.Refreshing &&
        state.dragOffset != state.refreshThresholdOffset &&
        guard++ < 8
    ) {
        animateToSpring(state, state.refreshThresholdOffset)
    }
}

/** 外部把 refreshing 置 false 时播放收尾动画（200ms，与 miuix 同曲线）。 */
private suspend fun finishRefreshing(state: PullState) {
    if (state.phase != PullPhase.Refreshing) return
    state.animationJob?.cancel()
    state.phase = PullPhase.RefreshComplete
    val initial = if (state.refreshThresholdOffset > 0f) {
        1f - (state.dragOffset / state.refreshThresholdOffset).coerceIn(0f, 1f)
    } else {
        1f
    }
    state.refreshCompleteAnimProgress = initial
    val anim = Animatable(initial)
    anim.animateTo(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 200, easing = CubicBezierEasing(0f, 0f, 0f, 0.37f)),
    ) {
        state.refreshCompleteAnimProgress = value
        state.dragOffset = state.refreshThresholdOffset * (1f - value)
        state.currentTouch = obtainTouchDistance(state.dragOffset, state.maxDragDistancePx)
    }
    state.phase = PullPhase.Idle
}

/** 用 miuix 的 SpringEngine 跑回弹，保证与原生同一套物理。 */
private suspend fun animateToSpring(state: PullState, targetValue: Float) {
    state.animationJob?.cancel()
    val engine = state.springEngine
    engine.start(state.dragOffset, targetValue, 0f)

    val currentJob = currentCoroutineContext()[Job]
    state.animationJob = currentJob

    var lastFrameTimeNanos = -1L
    var isFinished = false
    try {
        while (currentCoroutineContext().isActive) {
            isFinished = withFrameNanos { frameTimeNanos ->
                if (lastFrameTimeNanos == -1L) {
                    lastFrameTimeNanos = frameTimeNanos
                    return@withFrameNanos false
                }
                val dt = (frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f
                lastFrameTimeNanos = frameTimeNanos
                val finished = engine.step(dt)
                state.dragOffset = engine.currentPos.toFloat()
                state.currentTouch = obtainTouchDistance(state.dragOffset, state.maxDragDistancePx)
                finished
            }
            if (isFinished) break
        }
    } finally {
        if (state.animationJob == currentJob) state.animationJob = null
        if (isFinished) {
            state.dragOffset = targetValue
            state.currentTouch = obtainTouchDistance(targetValue, state.maxDragDistancePx)
        }
    }
}

@Composable
private fun RefreshHeader(
    state: PullState,
    circleSize: Dp,
    color: Color,
    refreshTexts: List<String>,
    refreshTextStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    // 缩放阶段：阈值以下按 visualProgress 长大。
    val visualProgress by remember(state) {
        derivedStateOf {
            val scaleEndOffset = min(state.refreshThresholdOffset, state.triggerProgressOffset)
            when {
                scaleEndOffset > 0f -> (state.dragOffset / scaleEndOffset).coerceIn(0f, 1f)
                state.dragOffset > 0f -> 1f
                else -> 0f
            }
        }
    }

    val refreshText by remember(state, refreshTexts) {
        derivedStateOf {
            when (state.phase) {
                PullPhase.Idle -> ""
                PullPhase.Pulling -> if (visualProgress > 0.5f) refreshTexts.getOrElse(0) { "" } else ""
                PullPhase.ThresholdReached -> refreshTexts.getOrElse(1) { "" }
                PullPhase.Refreshing -> refreshTexts.getOrElse(2) { "" }
                PullPhase.RefreshComplete -> refreshTexts.getOrElse(3) { "" }
            }
        }
    }

    val refreshTextAlpha by remember(state) {
        derivedStateOf {
            when (state.phase) {
                PullPhase.Idle -> 0f
                PullPhase.Pulling -> if (visualProgress > 0.6f) (visualProgress - 0.5f) * 2f else 0f
                PullPhase.ThresholdReached -> 1f
                PullPhase.Refreshing -> ((visualProgress - 0.5f) * 2f).coerceIn(0f, 1f)
                PullPhase.RefreshComplete ->
                    (1f - state.refreshCompleteAnimProgress * 1.95f).coerceAtLeast(0f)
            }
        }
    }

    // 超出固定视觉阈值的部分（缩放阶段为负）。
    val stretchExtraDp by remember(state, density) {
        derivedStateOf {
            with(density) { (state.dragOffset - state.refreshThresholdOffset).toDp() }
        }
    }

    val indicatorHeight by remember(state, circleSize, density) {
        derivedStateOf {
            if (state.phase == PullPhase.Idle) {
                0.dp
            } else if (stretchExtraDp <= 0.dp) {
                circleSize * visualProgress
            } else {
                circleSize + stretchExtraDp
            }
        }
    }

    val headerHeight by remember(state, circleSize, density) {
        derivedStateOf {
            if (state.phase == PullPhase.Idle) {
                0.dp
            } else if (stretchExtraDp <= 0.dp) {
                (circleSize + 36.dp) * visualProgress
            } else {
                (circleSize + 36.dp) + stretchExtraDp
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth().height(headerHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        RefreshIndicator(
            state = state,
            circleSize = circleSize,
            color = color,
            modifier = Modifier.height(indicatorHeight),
        )
        Text(
            text = refreshText,
            style = refreshTextStyle,
            modifier = Modifier.padding(top = 6.dp).graphicsLayer { alpha = refreshTextAlpha },
        )
    }
}

@Composable
private fun RefreshIndicator(
    state: PullState,
    circleSize: Dp,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val rotationState = if (state.phase == PullPhase.Refreshing) animateRotation() else null
        Canvas(modifier = Modifier.size(circleSize)) {
            val ringStrokeWidthPx = circleSize.toPx() / 11
            val indicatorRadiusPx = max(size.minDimension / 2, circleSize.toPx() / 3.5f)
            val center = Offset(circleSize.toPx() / 2, circleSize.toPx() / 1.8f)

            when (state.phase) {
                PullPhase.Idle -> return@Canvas

                PullPhase.Pulling -> {
                    if (state.dragOffset > state.refreshThresholdOffset) {
                        drawThresholdIndicator(
                            center, indicatorRadiusPx, ringStrokeWidthPx, color,
                            state.dragOffset, state.refreshThresholdOffset, state.maxDragDistancePx,
                        )
                    } else {
                        val alpha = (state.visualProgressForDraw - 0.2f).coerceAtLeast(0f)
                        drawPullingIndicator(center, indicatorRadiusPx, ringStrokeWidthPx, color, alpha)
                    }
                }

                PullPhase.ThresholdReached -> drawThresholdIndicator(
                    center, indicatorRadiusPx, ringStrokeWidthPx, color,
                    state.dragOffset, state.refreshThresholdOffset, state.maxDragDistancePx,
                )

                PullPhase.Refreshing -> {
                    val expansionAlpha = ((state.visualProgressForDraw - 0.2f) / 0.8f).coerceIn(0f, 1f)
                    drawRefreshingIndicator(
                        center, indicatorRadiusPx, ringStrokeWidthPx,
                        color.copy(alpha = color.alpha * expansionAlpha),
                        rotationState?.value ?: 0f,
                    )
                }

                PullPhase.RefreshComplete -> drawRefreshCompleteIndicator(
                    center, indicatorRadiusPx, ringStrokeWidthPx, color,
                    state.refreshCompleteAnimProgress,
                )
            }
        }
    }
}

/** 与 RefreshHeader 中 visualProgress 同式，绘制时同步取值。 */
private val PullState.visualProgressForDraw: Float
    get() {
        val scaleEndOffset = min(refreshThresholdOffset, triggerProgressOffset)
        return when {
            scaleEndOffset > 0f -> (dragOffset / scaleEndOffset).coerceIn(0f, 1f)
            dragOffset > 0f -> 1f
            else -> 0f
        }
    }

@Composable
private fun animateRotation(): State<Float> {
    val infiniteTransition = rememberInfiniteTransition()
    val initialRotation = remember { 0f }
    return infiniteTransition.animateFloat(
        initialValue = initialRotation,
        targetValue = initialRotation + 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
    )
}

private fun DrawScope.drawPullingIndicator(
    center: Offset,
    radius: Float,
    strokeWidth: Float,
    color: Color,
    alpha: Float,
) {
    drawCircle(
        color = color.copy(alpha = alpha),
        radius = radius,
        center = center,
        style = Stroke(strokeWidth, cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawThresholdIndicator(
    center: Offset,
    radius: Float,
    strokeWidth: Float,
    color: Color,
    dragOffset: Float,
    thresholdOffset: Float,
    maxDrag: Float,
) {
    val lineLength = (dragOffset - thresholdOffset).coerceIn(0f, maxDrag - thresholdOffset)
    val topY = center.y
    val bottomY = center.y + lineLength
    drawArc(
        color = color,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(center.x - radius, topY - radius),
        size = Size(radius * 2, radius * 2),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
    )
    drawArc(
        color = color,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(center.x - radius, bottomY - radius),
        size = Size(radius * 2, radius * 2),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
    )
    drawLine(
        color = color,
        start = Offset(center.x - radius, topY),
        end = Offset(center.x - radius, bottomY),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = color,
        start = Offset(center.x + radius, topY),
        end = Offset(center.x + radius, bottomY),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawRefreshingIndicator(
    center: Offset,
    radius: Float,
    strokeWidth: Float,
    color: Color,
    rotation: Float,
) {
    drawCircle(
        color = color,
        radius = radius,
        center = center,
        style = Stroke(strokeWidth, cap = StrokeCap.Round),
    )
    val orbitRadius = radius - 2 * strokeWidth
    val angle = rotation * PI / 180.0
    val dotCenter = center + Offset(
        x = (orbitRadius * cos(angle)).toFloat(),
        y = (orbitRadius * sin(angle)).toFloat(),
    )
    drawCircle(color = color, radius = strokeWidth, center = dotCenter)
}

private fun DrawScope.drawRefreshCompleteIndicator(
    center: Offset,
    radius: Float,
    strokeWidth: Float,
    color: Color,
    progress: Float,
) {
    val animatedRadius = radius * (1f - progress).coerceAtLeast(0.9f)
    val alphaColor = color.copy(alpha = (1f - progress - 0.35f).coerceAtLeast(0f))
    val y = center.y - radius - strokeWidth + animatedRadius
    drawCircle(
        color = alphaColor,
        radius = animatedRadius,
        center = Offset(center.x, y),
        style = Stroke(strokeWidth, cap = StrokeCap.Round),
    )
}

// ---------------------------------------------------------------- 阻尼与弹簧
// miuix 的 SpringMath / SpringEngine 是 internal，外部模块调不到，这里按同一公式重实现。

private const val MAX_FRAME_DELTA_SECONDS = 0.016f
private const val MIN_FRAME_DELTA_SECONDS = 0.001f
private const val HIGH_VELOCITY_THRESHOLD = 5000.0
private const val CRITICAL_DAMPING_RATIO = 1.0f
private const val STANDARD_SPRING_PERIOD = 0.4f
private const val SLOWER_SPRING_PERIOD_FOR_HIGH_VELOCITY = 0.55f

/** 阻尼公式：x - x² + x³/3 */
private fun obtainDampingDistance(normalizedInput: Float, range: Float): Float {
    val x = max(0.0f, min(normalizedInput, 1.0f)).toDouble()
    val dampedFactor = x - x.pow(2.0) + (x.pow(3.0) / 3.0)
    return (dampedFactor * range).toFloat()
}

/** 反解公式：range - range^(2/3) * (range - 3·|offset|)^(1/3) */
private fun obtainTouchDistance(currentPixelOffset: Float, range: Float): Float {
    var absPixelOffset = abs(currentPixelOffset)
    val absMaxOffset = abs(obtainDampingDistance(1.0f, range))

    if (absPixelOffset <= 0f) return 0f
    if (absPixelOffset >= absMaxOffset) {
        absPixelOffset = absMaxOffset
    }

    val base = range - (3.0 * absPixelOffset)
    val part2 = range.toDouble().pow(2.0 / 3.0) * sign(base) * abs(base).pow(1.0 / 3.0)
    return (range - part2).toFloat()
}

private class SpringOperator(dampingRatio: Float, naturalPeriod: Float) {
    private val dampingCoefficient: Double
    private val stiffnessOverMass: Double

    init {
        val angularFrequency = (2.0 * PI) / naturalPeriod
        stiffnessOverMass = angularFrequency * angularFrequency // k/m = ω²
        dampingCoefficient = 2.0 * dampingRatio * angularFrequency // c/m = 2ζω
    }

    fun updateVelocity(
        currentVelocity: Double,
        deltaTime: Float,
        currentPosition: Double,
        targetPosition: Double,
    ): Double {
        val velocityDecayFactor = 1.0 - dampingCoefficient * deltaTime
        val velocityIncreaseFromSpring = stiffnessOverMass * (targetPosition - currentPosition) * deltaTime
        return currentVelocity * velocityDecayFactor + velocityIncreaseFromSpring
    }
}

/** 临界阻尼弹簧，欧拉积分。 */
private class QingkeSpringEngine {
    private var springOperator: SpringOperator? = null
    var velocity: Double = 0.0
    var currentPos: Double = 0.0
    private var targetPos: Double = 0.0
    private var initialPos: Double = 0.0
    private var initialVelocity: Double = 0.0

    private fun isAtEquilibrium(): Boolean {
        if (initialPos < targetPos && currentPos > targetPos) return true
        if (initialPos <= targetPos || currentPos >= targetPos) {
            return (initialPos == targetPos && sign(initialVelocity) != sign(currentPos)) ||
                abs(currentPos - targetPos) < 1.0
        }
        return true
    }

    fun start(startValue: Float, targetValue: Float, initialVel: Float) {
        currentPos = startValue.toDouble()
        initialPos = startValue.toDouble()
        targetPos = targetValue.toDouble()
        velocity = initialVel.toDouble()
        initialVelocity = initialVel.toDouble()
        springOperator = SpringOperator(
            CRITICAL_DAMPING_RATIO,
            if (abs(initialVel) > HIGH_VELOCITY_THRESHOLD) {
                SLOWER_SPRING_PERIOD_FOR_HIGH_VELOCITY
            } else {
                STANDARD_SPRING_PERIOD
            },
        )
    }

    fun step(deltaTime: Float): Boolean {
        val operator = springOperator ?: return false
        val dt = deltaTime.coerceIn(MIN_FRAME_DELTA_SECONDS, MAX_FRAME_DELTA_SECONDS)
        velocity = operator.updateVelocity(velocity, dt, currentPos, targetPos)
        currentPos += dt * velocity

        if (isAtEquilibrium()) {
            currentPos = targetPos
            velocity = 0.0
            return true
        }
        return false
    }
}

/*
 * 本文件的指示器绘制、阻尼参数与状态节奏移植自 compose-miuix-ui/miuix 的
 * miuix-ui/src/commonMain/kotlin/top.yukonga/miuix/kmp/basic/PullToRefresh.kt
 * Copyright 2025, compose-miuix-ui contributors
 * SPDX-License-Identifier: Apache-2.0
 * 修改点：刷新期间不消费嵌套滚动（原版 return available 会锁死列表）。
 */
