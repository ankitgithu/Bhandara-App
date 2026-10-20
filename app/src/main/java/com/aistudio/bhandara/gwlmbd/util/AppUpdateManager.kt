package com.aistudio.bhandara.gwlmbd.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object AppUpdateManager {

    private const val TAG = "AppUpdateManager"
    private const val APK_FILE_NAME = "bhandara_update.apk"

    /**
     * Get the currently installed versionCode.
     */
    fun getInstalledVersionCode(context: Context): Long {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching installed versionCode: ${e.message}")
            1L
        }
    }

    /**
     * Get the currently installed versionName.
     */
    fun getInstalledVersionName(context: Context): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching installed versionName: ${e.message}")
            "1.0"
        }
    }

    /**
     * Check if the app has permission to install unknown APKs (Android 8.0+).
     */
    fun canInstallUnknownApps(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Open system settings allowing the user to grant "Install unknown apps" permission.
     */
    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch unknown sources settings: ${e.message}")
                val fallbackIntent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            }
        }
    }

    /**
     * Target directory for downloading APK files.
     */
    fun getUpdateApkFile(context: Context): File {
        val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
        return File(downloadDir, APK_FILE_NAME)
    }

    /**
     * Downloads APK from a given URL, supporting HTTP redirects (301, 302, 303, 307, 308).
     *
     * @param apkUrl Direct or redirected URL pointing to the APK.
     * @param onProgress Callback delivering percent (0-100), bytesDownloaded, totalBytes.
     */
    suspend fun downloadApk(
        context: Context,
        apkUrl: String,
        onProgress: (progressPercent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            var currentUrl = apkUrl.trim()
            var redirects = 0
            val maxRedirects = 6

            // Follow HTTP/HTTPS redirects
            while (redirects < maxRedirects) {
                val url = URL(currentUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 25000
                    readTimeout = 35000
                    setRequestProperty("User-Agent", "BhandaraAndroidApp/${getInstalledVersionName(context)}")
                    setRequestProperty("Accept-Encoding", "identity") // Avoid gzip for accurate content-length
                }

                val status = connection.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_PERM ||
                    status == HttpURLConnection.HTTP_MOVED_TEMP ||
                    status == HttpURLConnection.HTTP_SEE_OTHER ||
                    status == 307 || status == 308
                ) {
                    val newUrl = connection.getHeaderField("Location")
                    if (newUrl.isNullOrBlank()) {
                        break
                    }
                    currentUrl = if (newUrl.startsWith("http://") || newUrl.startsWith("https://")) {
                        newUrl
                    } else {
                        URL(url, newUrl).toString()
                    }
                    connection.disconnect()
                    redirects++
                } else {
                    break
                }
            }

            if (connection == null) {
                return@withContext Result.failure(Exception("नेटवर्क कनेक्शन स्थापित नहीं हो सका"))
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(Exception("सर्वर से उत्तर मिला (HTTP $responseCode), कृपया दोबारा प्रयास करें"))
            }

            val totalBytes = connection.contentLengthLong
            val targetFile = getUpdateApkFile(context)

            // Remove previous incomplete/old file if exists
            if (targetFile.exists()) {
                targetFile.delete()
            }

            inputStream = connection.inputStream
            outputStream = FileOutputStream(targetFile)

            val buffer = ByteArray(8 * 1024)
            var downloadedBytes = 0L
            var read: Int
            var lastReportedPercent = -1

            while (inputStream.read(buffer).also { read = it } != -1) {
                outputStream.write(buffer, 0, read)
                downloadedBytes += read

                val percent = if (totalBytes > 0) {
                    ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                } else {
                    -1
                }

                if (percent != lastReportedPercent) {
                    lastReportedPercent = percent
                    onProgress(percent, downloadedBytes, totalBytes)
                }
            }

            outputStream.flush()
            onProgress(100, downloadedBytes, if (totalBytes > 0) totalBytes else downloadedBytes)

            Result.success(targetFile)
        } catch (e: Exception) {
            Log.e(TAG, "downloadApk error: ${e.message}", e)
            Result.failure(e)
        } finally {
            try { outputStream?.close() } catch (_: Exception) {}
            try { inputStream?.close() } catch (_: Exception) {}
            try { connection?.disconnect() } catch (_: Exception) {}
        }
    }

    /**
     * Launch the native Android package installer using FileProvider.
     */
    fun installApk(context: Context, apkFile: File): Result<Unit> {
        return try {
            if (!apkFile.exists()) {
                return Result.failure(Exception("APK फ़ाइल नहीं मिली"))
            }

            val authority = "${context.packageName}.fileprovider"
            val apkUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "installApk error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Clean up any previously downloaded APK.
     */
    fun cleanOldApk(context: Context) {
        try {
            val file = getUpdateApkFile(context)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "cleanOldApk error: ${e.message}")
        }
    }
}
