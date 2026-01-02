package com.zhixu.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class UpdateInfo(
    val latestVersion: String,
    val changelog: String,
    val downloadUrl: String?,
    val sourceUrl: String,
)

sealed class UpdateCheckResult {
    data class Success(val info: UpdateInfo, val hasUpdate: Boolean) : UpdateCheckResult()

    data class Failure(val message: String) : UpdateCheckResult()
}

object UpdateClient {
    private const val BASE_URL = "https://zhixu.app/upload"

    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

    suspend fun check(currentVersion: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        val base = BASE_URL.trimEnd('/')
        val candidates =
            listOf(
                "$base/update.json",
                "$base/latest.json",
                "$base/version.json",
                base,
            )

        val attempts = mutableListOf<String>()

        for (url in candidates.distinct()) {
            attempts += url
            val result = runCatching { fetchUpdateInfo(url) }.getOrNull() ?: continue
            val hasUpdate = compareVersions(result.latestVersion, currentVersion) > 0
            return@withContext UpdateCheckResult.Success(result, hasUpdate)
        }

        UpdateCheckResult.Failure("Failed to fetch update info. Tried: ${attempts.joinToString(", ")}")
    }

    private fun fetchUpdateInfo(url: String): UpdateInfo? {
        val request =
            Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", "Zhixu-Android")
                .header("Accept", "application/json, text/plain, */*")
                .build()

        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) return null

            val trimmed = body.trim()
            if (trimmed.isBlank()) return null

            if (trimmed.startsWith("{")) {
                return parseJson(trimmed, sourceUrl = url)
            }

            // Fallback: treat as plain text changelog; best-effort extract first version-looking token.
            val latest = extractVersionFromText(trimmed) ?: return null
            return UpdateInfo(
                latestVersion = latest,
                changelog = trimmed,
                downloadUrl = null,
                sourceUrl = url,
            )
        }
    }

    private fun parseJson(json: String, sourceUrl: String): UpdateInfo? {
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val latest =
            listOf("latestVersion", "version", "versionName")
                .asSequence()
                .mapNotNull { k -> obj.optString(k).takeIf { it.isNotBlank() } }
                .firstOrNull()
                ?: return null

        val downloadUrl =
            listOf("downloadUrl", "apkUrl", "url")
                .asSequence()
                .mapNotNull { k -> obj.optString(k).takeIf { it.isNotBlank() } }
                .firstOrNull()

        val changelog =
            obj.optString("changelog").takeIf { it.isNotBlank() }
                ?: obj.optString("notes").takeIf { it.isNotBlank() }
                ?: obj.optString("log").takeIf { it.isNotBlank() }
                ?: obj.optJSONArray("changelog")?.toMarkdownList()
                ?: obj.optJSONArray("notes")?.toMarkdownList()
                ?: ""

        return UpdateInfo(
            latestVersion = latest,
            changelog = changelog,
            downloadUrl = downloadUrl,
            sourceUrl = sourceUrl,
        )
    }

    private fun JSONArray.toMarkdownList(): String {
        val lines = mutableListOf<String>()
        for (i in 0 until length()) {
            val item = optString(i).trim()
            if (item.isNotBlank()) lines += "- $item"
        }
        return lines.joinToString("\n")
    }

    private fun extractVersionFromText(text: String): String? {
        val regex = Regex("""(?i)\b(v?)(\d+)\.(\d+)\.(\d+)\b""")
        val m = regex.find(text) ?: return null
        return "${m.groupValues[2]}.${m.groupValues[3]}.${m.groupValues[4]}"
    }

    /**
     * Returns:
     *  - positive if [a] > [b]
     *  - zero if equal
     *  - negative if [a] < [b]
     */
    fun compareVersions(a: String, b: String): Int {
        val pa = parseVersionParts(a)
        val pb = parseVersionParts(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val ai = pa.getOrElse(i) { 0 }
            val bi = pb.getOrElse(i) { 0 }
            if (ai != bi) return ai - bi
        }
        return 0
    }

    private fun parseVersionParts(v: String): List<Int> =
        v.trim()
            .removePrefix("v")
            .removePrefix("V")
            .split('.', '-', '_')
            .mapNotNull { token -> token.trim().toIntOrNull() }
}
