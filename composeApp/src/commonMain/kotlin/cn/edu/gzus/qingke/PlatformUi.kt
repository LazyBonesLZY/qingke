package cn.edu.gzus.qingke

import androidx.compose.runtime.Composable

@Composable
expect fun QingkeBackHandler(enabled: Boolean, onBack: () -> Unit)

@Composable
expect fun ApplySystemBars()
