package com.vibecode.mobile.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.vibecode.mobile.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

@Serializable
private data class GitHubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
)

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAsset> = emptyList(),
)

data class AppUpdate(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
)

class AppUpdater(private val context: Context) {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val tagRegex = Regex("^android-v(\\d+)$")

    suspend fun checkForUpdate(): AppUpdate? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/gutbai/vibecode/releases?per_page=30")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "VibeCode-Android/${BuildConfig.VERSION_NAME}")
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("GitHub trả về HTTP ${response.code}")
            val body = response.body?.string() ?: error("GitHub không trả dữ liệu")
            val releases = json.decodeFromString<List<GitHubRelease>>(body)
            val compatible = releases.asSequence()
                .filter { !it.draft && !it.prerelease }
                .mapNotNull { release ->
                    val versionCode = tagRegex.matchEntire(release.tagName)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.toIntOrNull()
                        ?: return@mapNotNull null
                    val apk = release.assets.firstOrNull { asset ->
                        asset.name.equals("VibeCode.apk", ignoreCase = true) ||
                            asset.name.endsWith(".apk", ignoreCase = true)
                    } ?: return@mapNotNull null
                    AppUpdate(
                        versionCode = versionCode,
                        versionName = release.name?.removePrefix("VibeCode Android ")?.ifBlank { release.tagName }
                            ?: release.tagName,
                        downloadUrl = apk.browserDownloadUrl,
                    )
                }
                .toList()

            if (compatible.isEmpty()) {
                error("Chưa có GitHub Release Android hợp lệ (android-vN + APK). Không thể kết luận đây là bản mới nhất.")
            }

            val latest = compatible.maxByOrNull { it.versionCode }
                ?: error("Không đọc được phiên bản Android mới nhất")
            if (latest.versionCode > BuildConfig.VERSION_CODE) latest else null
        }
    }

    suspend fun download(update: AppUpdate): File = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(update.downloadUrl)
            .header("User-Agent", "VibeCode-Android/${BuildConfig.VERSION_NAME}")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Tải APK lỗi HTTP ${response.code}")
            val body = response.body ?: error("Không nhận được APK")
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val target = File(dir, "VibeCode-${update.versionCode}.apk")
            target.outputStream().use { output -> body.byteStream().use { it.copyTo(output) } }
            if (target.length() <= 0L) error("APK tải về bị rỗng")
            target
        }
    }

    fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
    }
}
