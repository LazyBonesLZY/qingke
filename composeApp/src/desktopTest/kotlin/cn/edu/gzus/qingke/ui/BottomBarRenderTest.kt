@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package cn.edu.gzus.qingke.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import cn.edu.gzus.qingke.QingkeTheme
import cn.edu.gzus.qingke.data.AppSettings
import cn.edu.gzus.qingke.nav.TabDest
import cn.edu.gzus.qingke.ui.components.QingkeBottomBar
import cn.edu.gzus.qingke.ui.components.rememberQingkeBackdrop
import cn.edu.gzus.qingke.ui.components.qingkeLayer
import java.io.File
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 底栏交互与玻璃采样回归：点击切 tab、按住划过去切 tab、
 * 玻璃必须采到下层组件、关闭模糊必须完全不采样。
 * 需要 JDK 21+（miuix-blur 桌面 jar 是 Java 21 字节码）。
 */
class BottomBarRenderTest {
    private val barY = 732f // 悬浮胶囊垂直中心（800 - 36 - 64/2）

    @Test
    fun tapSwitchesTab() = onUiThread {
        val state = renderInteractive(Color(0xFF3482FF))
        state.click(270f, barY)
        assertEquals(TabDest.Jwxt, state.selected.value, "单击第四个 tab 应切换到服务页")
    }

    @Test
    fun dragAcrossSwitchesTab() = onUiThread {
        val state = renderInteractive(Color(0xFF3482FF))
        state.drag(fromX = 60f, toX = 340f, y = barY)
        assertEquals(TabDest.Mine, state.selected.value, "按住从第一个划到第五个应切到我的页")
    }

    @Test
    fun glassSamplesComponentsBehindIt() = onUiThread {
        val red = renderSnapshot(Color(0xFFFF4B4B), "orig-blur-red")
        val blue = renderSnapshot(Color(0xFF3482FF), "orig-blur-blue")
        val offRed = renderSnapshot(Color(0xFFFF4B4B), "orig-off-red", blur = false)
        val offBlue = renderSnapshot(Color(0xFF3482FF), "orig-off-blue", blur = false)
        // 胶囊内部、选中块与文字之外的点。开启模糊必须明显透出下层组件；
        // 关闭模糊后是 0.92 白色半透明底，只允许轻微混色，不能像玻璃那样采出内容。
        val x = 190
        val y = 738
        val on = diff(red.getRGB(x, y), blue.getRGB(x, y))
        val off = diff(offRed.getRGB(x, y), offBlue.getRGB(x, y))
        // on≈395（玻璃透出内容），off≈34（0.92 白半透明只轻微混色）
        assertTrue(on > 200, "玻璃未采样下层组件: $on")
        assertTrue(off < 60, "关闭模糊后仍像玻璃一样采样下层组件: $off")
    }

    private class InteractiveScene(
        val scene: ImageComposeScene,
        val selected: androidx.compose.runtime.MutableState<TabDest>,
    ) {
        fun click(x: Float, y: Float) = pointer {
            step(PointerEventType.Press, x, y)
            step(PointerEventType.Release, x, y)
        }

        fun drag(fromX: Float, toX: Float, y: Float) = pointer {
            step(PointerEventType.Press, fromX, y)
            var current = fromX
            while (current < toX) {
                current = minOf(current + 40f, toX)
                step(PointerEventType.Move, current, y)
            }
            step(PointerEventType.Release, toX, y)
        }

        private fun pointer(block: PointerSequence.() -> Unit) {
            val sequence = PointerSequence()
            sequence.block()
            repeat(12) { frame -> scene.render(frame * 16_000_000L) }
        }

        private inner class PointerSequence {
            var time = 0L
            fun step(type: PointerEventType, x: Float, y: Float) {
                time += 50L
                scene.sendPointerEvent(type, Offset(x, y), timeMillis = time)
                scene.render(time * 1_000_000L)
            }
        }
    }

    private fun renderInteractive(color: Color): InteractiveScene {
        val selected = mutableStateOf(TabDest.Today)
        var composed: ImageComposeScene? = null
        val scene = ImageComposeScene(width = 400, height = 800, density = Density(1f)) {
            val backdrop = rememberQingkeBackdrop()
            QingkeTheme(settings = AppSettings()) {
                Box(Modifier.fillMaxSize()) {
                    Canvas(Modifier.fillMaxSize().qingkeLayer(backdrop)) {
                        drawRect(Color(0xFFF5F5F5))
                        drawRoundRect(color, topLeft = Offset(0f, 680f), size = Size(400f, 120f))
                    }
                    Box(Modifier.align(Alignment.BottomCenter)) {
                        QingkeBottomBar(selected.value, backdrop) { selected.value = it }
                    }
                }
            }
        }
        composed = scene
        repeat(12) { frame -> scene.render(frame * 16_000_000L) }
        return InteractiveScene(composed, selected)
    }

    private fun renderSnapshot(color: Color, name: String, blur: Boolean = true): java.awt.image.BufferedImage {
        val selected = mutableStateOf(TabDest.Today)
        val scene = ImageComposeScene(width = 400, height = 800, density = Density(1f)) {
            val backdrop = rememberQingkeBackdrop()
            QingkeTheme(settings = AppSettings()) {
                Box(Modifier.fillMaxSize()) {
                    Canvas(Modifier.fillMaxSize().qingkeLayer(backdrop)) {
                        drawRect(Color(0xFFF5F5F5))
                        drawRoundRect(color, topLeft = Offset(0f, 680f), size = Size(400f, 120f))
                    }
                    Box(Modifier.align(Alignment.BottomCenter)) {
                        QingkeBottomBar(selected.value, backdrop, blurEnabled = blur) { selected.value = it }
                    }
                }
            }
        }
        try {
            var bytes = ByteArray(0)
            repeat(12) { frame ->
                scene.render(frame * 16_000_000L).use { image -> bytes = image.encodeToData()!!.bytes }
            }
            val output = File("build/ui-preview/$name.png")
            output.parentFile.mkdirs()
            output.writeBytes(bytes)
            return ImageIO.read(output)
        } finally {
            scene.close()
        }
    }

    private fun diff(a: Int, b: Int): Int = listOf(0, 8, 16).sumOf { shift ->
        kotlin.math.abs(((a shr shift) and 255) - ((b shr shift) and 255))
    }

    private fun onUiThread(block: () -> Unit) {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait { try { block() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }
}
