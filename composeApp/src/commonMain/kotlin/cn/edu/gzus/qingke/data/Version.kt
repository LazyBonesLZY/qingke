package cn.edu.gzus.qingke.data

const val APP_VERSION_NAME = "1.7.1"
const val APP_VERSION_CODE = 43
const val GITHUB_REPO = "LazyBonesLZY/qingke"
const val GITHUB_RELEASES_URL = "https://github.com/$GITHUB_REPO/releases"
const val DRIVE_UPDATE_URL = "https://storage.lazzyy.cn/@s/KB"

data class AppUpdate(
    val versionName: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String,
    val newer: Boolean,
)

fun parseVersionParts(raw: String): List<Int> =
    raw.trim().trimStart('v', 'V').split('.', '-', '+', '_')
        .mapNotNull { part -> part.filter { it.isDigit() }.toIntOrNull() }

fun compareVersionName(left: String, right: String): Int {
    val a = parseVersionParts(left)
    val b = parseVersionParts(right)
    val n = maxOf(a.size, b.size)
    for (i in 0 until n) {
        val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
        if (d != 0) return d
    }
    return 0
}

fun isRemoteNewer(remoteName: String, remoteCode: Int = 0): Boolean {
    if (remoteCode > 0 && remoteCode > APP_VERSION_CODE) return true
    if (remoteCode > 0 && remoteCode < APP_VERSION_CODE) return false
    return compareVersionName(remoteName, APP_VERSION_NAME) > 0
}
