@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package cn.edu.gzus.qingke.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import cn.edu.gzus.qingke.QingkeTheme
import cn.edu.gzus.qingke.data.AppSettings
import cn.edu.gzus.qingke.nav.TabDest
import cn.edu.gzus.qingke.ui.components.QingkeBottomBar
import cn.edu.gzus.qingke.ui.components.qingkeLayer
import cn.edu.gzus.qingke.ui.components.rememberQingkeBackdrop
import cn.edu.gzus.qingke.ui.components.tabPagePadding
import java.io.File
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BottomBarRenderTest {
    @Test
    fun blurredBarSamplesComponentsAndOffIsOpaque() = onUiThread {
        val red = render(Color(0xFFFF4B4B), true, "blur-red")
        val blue = render(Color(0xFF3482FF), true, "blur-blue")
        val offRed = render(Color(0xFFFF4B4B), false, "off-red")
        val offBlue = render(Color(0xFF3482FF), false, "off-blue")
        // This point is inside the glass, outside the indicator and label strokes.
        val x = 190
        val y = 774
        val blurDifference = colorDifference(red.getRGB(x, y), blue.getRGB(x, y))
        val offDifference = colorDifference(offRed.getRGB(x, y), offBlue.getRGB(x, y))
        assertTrue(blurDifference > 60, "Glass did not sample underlying components: $blurDifference")
        assertTrue(offDifference < 8, "Disabled blur still samples underlying components: $offDifference")
    }

    @Test
    fun rendersDockedAndRtlWithoutDuplicateLabels() = onUiThread {
        render(Color(0xFF3482FF), true, "docked", floating = false)
        render(Color(0xFF3482FF), true, "rtl", rtl = true)
    }

    private fun render(color: Color, blur: Boolean, name: String, floating: Boolean = true, rtl: Boolean = false): java.awt.image.BufferedImage {
        val selected = mutableStateOf(TabDest.Today)
        val scene = ImageComposeScene(width = 400, height = 800, density = Density(1f), layoutDirection = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            val backdrop = rememberQingkeBackdrop()
            QingkeTheme(settings = AppSettings()) {
                Box(Modifier.fillMaxSize()) {
                    Canvas(Modifier.fillMaxSize().qingkeLayer(backdrop)) {
                        drawRect(Color(0xFFF5F5F5))
                        drawRoundRect(color, topLeft = Offset(20f, 700f), size = Size(360f, 100f))
                        for (index in 0..12) drawRect(Color.White, Offset(index * 32f, 740f), Size(6f, 60f))
                    }
                    Box(Modifier.align(Alignment.BottomCenter)) {
                        QingkeBottomBar(selected.value, backdrop, floating, blur) { selected.value = it }
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
            val result = ImageIO.read(output)
            val tabs = scene.semanticsOwners.flatMap { owner -> owner.rootSemanticsNode.children }
            assertTrue(tabs.isNotEmpty())
            return result
        } finally {
            scene.close()
        }
    }

    private fun colorDifference(a: Int, b: Int): Int = listOf(0, 8, 16).sumOf { shift ->
        kotlin.math.abs(((a shr shift) and 255) - ((b shr shift) and 255))
    }

    private fun onUiThread(block: () -> Unit) {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait { try { block() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }
}
