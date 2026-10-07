package cn.edu.gzus.qingke

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import cn.edu.gzus.qingke.data.decodeImageBytes
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
actual fun QingkeBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

@Composable
actual fun ApplySystemBars() = Unit

@Composable
actual fun QingkeBackgroundPickerButton(
    label: String,
    onPicked: (String) -> Unit,
) {
    Button(onClick = {
        val chooser = JFileChooser().apply {
            dialogTitle = label
            fileFilter = FileNameExtensionFilter(
                "图片文件",
                "png", "jpg", "jpeg", "webp", "bmp",
            )
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile?.absolutePath?.let(onPicked)
        }
    }) {
        Text(label)
    }
}

@Composable
actual fun rememberQingkeBackgroundImage(source: String): ImageBitmap? = remember(source) {
    runCatching {
        val file = File(source)
        if (!file.isFile) return@runCatching null
        decodeImageBytes(file.readBytes())
    }.getOrNull()
}
