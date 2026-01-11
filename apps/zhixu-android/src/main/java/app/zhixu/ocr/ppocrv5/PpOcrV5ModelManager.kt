package app.zhixu.ocr.ppocrv5

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import app.zhixu.data.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import org.json.JSONObject
import java.io.File

class PpOcrV5ModelManager(
    private val context: Context,
    private val repository: VaultRepository,
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    data class ModelPaths(
        val detParam: File,
        val detBin: File,
        val recParam: File,
        val recBin: File,
        val version: String,
    )

    suspend fun ensureMobileModels(vaultRootUri: Uri): ModelPaths = withContext(Dispatchers.IO) {
        val version = MODEL_VERSION
        val localDir = File(context.filesDir, "zhixu/ocr/models/ppocrv5/$version/mobile")
        localDir.mkdirs()

        val detParam = File(localDir, "PP_OCRv5_mobile_det.ncnn.param")
        val detBin = File(localDir, "PP_OCRv5_mobile_det.ncnn.bin")
        val recParam = File(localDir, "PP_OCRv5_mobile_rec.ncnn.param")
        val recBin = File(localDir, "PP_OCRv5_mobile_rec.ncnn.bin")

        ensureFilesPresent(vaultRootUri, detParam, detBin, recParam, recBin)
        validateModelFilesOrThrow(detParam, detBin, recParam, recBin)

        mirrorToVaultIfNeeded(
            vaultRootUri = vaultRootUri,
            version = version,
            files = listOf(detParam, detBin, recParam, recBin),
        )

        ModelPaths(
            detParam = detParam,
            detBin = detBin,
            recParam = recParam,
            recBin = recBin,
            version = version,
        )
    }

    suspend fun refreshMobileModels(vaultRootUri: Uri): ModelPaths = withContext(Dispatchers.IO) {
        val version = MODEL_VERSION
        val localDir = File(context.filesDir, "zhixu/ocr/models/ppocrv5/$version/mobile")
        if (localDir.exists()) {
            localDir.listFiles()?.forEach { runCatching { it.delete() } }
        }
        ensureMobileModels(vaultRootUri)
    }

    private suspend fun ensureFilesPresent(
        vaultRootUri: Uri,
        detParam: File,
        detBin: File,
        recParam: File,
        recBin: File,
    ) {
        val allPresent = listOf(detParam, detBin, recParam, recBin).all { it.exists() && it.length() > 0L }
        if (allPresent) return
        if (tryRestoreFromVault(vaultRootUri, detParam, detBin, recParam, recBin)) {
            Log.i("ZhixuOcr", "ppocrv5 models restored from vault")
            return
        }
        Log.i("ZhixuOcr", "ppocrv5 models downloading from network primary=$MODEL_BASE_URL fallback=$MODEL_FALLBACK_BASE_URL")

        fun urlsFor(name: String): List<String> = listOf(MODEL_BASE_URL + name, MODEL_FALLBACK_BASE_URL + name)

        downloadTo(detParam, urlsFor(detParam.name))
        downloadTo(detBin, urlsFor(detBin.name))
        downloadTo(recParam, urlsFor(recParam.name))
        downloadTo(recBin, urlsFor(recBin.name))
    }

    private suspend fun tryRestoreFromVault(
        vaultRootUri: Uri,
        detParam: File,
        detBin: File,
        recParam: File,
        recBin: File,
    ): Boolean {
        val required = listOf(detParam.name, detBin.name, recParam.name, recBin.name)
        val searchDirs = listOf(VAULT_DIR_PRIMARY)

        val bytesByName = HashMap<String, ByteArray>(required.size)
        for (name in required) {
            var uri: Uri? = null
            for (dir in searchDirs) {
                val candidate = runCatching { repository.resolveVaultFileUri(vaultRootUri, "$dir/$name") }.getOrNull()
                if (candidate != null) {
                    uri = candidate
                    break
                }
            }
            val resolved = uri ?: return false
            val bytes = runCatching { repository.readBytes(resolved) }.getOrNull() ?: return false
            if (bytes.isEmpty()) return false
            bytesByName[name] = bytes
        }

        runCatching { detParam.writeBytes(bytesByName.getValue(detParam.name)) }.getOrElse { return false }
        runCatching { detBin.writeBytes(bytesByName.getValue(detBin.name)) }.getOrElse { return false }
        runCatching { recParam.writeBytes(bytesByName.getValue(recParam.name)) }.getOrElse { return false }
        runCatching { recBin.writeBytes(bytesByName.getValue(recBin.name)) }.getOrElse { return false }
        return true
    }

    private fun validateModelFilesOrThrow(
        detParam: File,
        detBin: File,
        recParam: File,
        recBin: File,
    ) {
        fun requireSize(file: File, minBytes: Long) {
            require(file.exists() && file.length() >= minBytes) { "模型文件异常：${file.name} size=${file.length()}" }
        }

        // Expected sizes (mobile) are roughly: det.bin~2.3MB, rec.bin~8.2MB, params~20-25KB.
        requireSize(detParam, 10_000)
        requireSize(recParam, 10_000)
        requireSize(detBin, 1_000_000)
        requireSize(recBin, 3_000_000)

        // ncnn param files usually start with magic "7767517"
        val head = detParam.inputStream().bufferedReader().use { it.readLine().orEmpty().trim() }
        if (!head.startsWith("7767517")) {
            Log.w("ZhixuOcr", "det param magic unexpected: '$head'")
        }
    }

    private fun downloadTo(dest: File, urls: List<String>) {
        require(urls.isNotEmpty()) { "urls is empty" }
        val tmp = File(dest.parentFile, dest.name + ".download")
        runCatching { tmp.delete() }

        var lastError: Throwable? = null
        for (url in urls) {
            val request = Request.Builder().url(url).build()
            try {
                httpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) error("Download failed ${resp.code} for $url")
                    val body = resp.body ?: error("Empty response body for $url")
                    tmp.sink().buffer().use { sink ->
                        sink.writeAll(body.source())
                    }
                }
                if (tmp.exists() && tmp.length() > 0L) {
                    lastError = null
                    break
                }
            } catch (e: Exception) {
                lastError = e
                runCatching { tmp.delete() }
            }
        }

        if (!tmp.exists() || tmp.length() <= 0L) {
            val last = lastError?.message ?: lastError?.javaClass?.simpleName ?: "unknown"
            error("Download failed for ${dest.name}; tried=${urls.joinToString()} last=$last")
        }
        if (dest.exists()) dest.delete()
        if (!tmp.renameTo(dest)) {
            dest.writeBytes(tmp.readBytes())
            tmp.delete()
        }
    }

    private suspend fun mirrorToVaultIfNeeded(
        vaultRootUri: Uri,
        version: String,
        files: List<File>,
    ) {
        val versionJson =
            JSONObject()
                .put("engine", "ppocrv5ncnn")
                .put("model", "mobile")
                .put("version", version)
                .put("source", MODEL_BASE_URL)
                .toString(2) + "\n"

        for (dir in listOf(VAULT_DIR_PRIMARY)) {
            val upToDate = isVaultCopyUpToDate(vaultRootUri = vaultRootUri, dir = dir, version = version, files = files)
            if (upToDate) continue

            Log.i("ZhixuOcr", "ppocrv5 models mirroring to vault dir=$dir version=$version")
            val versionUri = repository.ensureVaultFile(vaultRootUri, "$dir/version.json", "application/json")
            repository.writeText(versionUri, versionJson)

            for (file in files) {
                val rel = "$dir/${file.name}"
                val expectedSize = file.length()
                if (expectedSize > 0 && vaultFileSizeMatches(vaultRootUri, rel, expectedSize)) continue

                val mime =
                    when {
                        file.name.endsWith(".param", ignoreCase = true) -> "text/plain"
                        else -> "application/octet-stream"
                    }
                val uri = repository.ensureVaultFile(vaultRootUri, rel, mime)
                repository.writeBytes(uri, file.readBytes())
            }
        }
    }

    private suspend fun isVaultCopyUpToDate(
        vaultRootUri: Uri,
        dir: String,
        version: String,
        files: List<File>,
    ): Boolean {
        val versionUri = repository.resolveVaultFileUri(vaultRootUri, "$dir/version.json") ?: return false
        val preview = repository.readTextPreview(versionUri, maxChars = 2000).trim()
        if (preview.isBlank()) return false
        val json = runCatching { JSONObject(preview) }.getOrNull() ?: return false
        if (json.optString("engine") != "ppocrv5ncnn") return false
        if (json.optString("model") != "mobile") return false
        if (json.optString("version") != version) return false

        for (file in files) {
            val expectedSize = file.length()
            if (expectedSize <= 0) return false
            if (!vaultFileSizeMatches(vaultRootUri, "$dir/${file.name}", expectedSize)) return false
        }
        return true
    }

    private suspend fun vaultFileSizeMatches(
        vaultRootUri: Uri,
        relativePath: String,
        expectedSize: Long,
    ): Boolean {
        val uri = repository.resolveVaultFileUri(vaultRootUri, relativePath) ?: return false
        val actualSize = readSize(uri) ?: return false
        return actualSize == expectedSize && actualSize > 0L
    }

    private fun readSize(uri: Uri): Long? {
        if (uri.scheme.equals("file", ignoreCase = true)) {
            val path = uri.path ?: return null
            return File(path).length()
        }
        return DocumentFile.fromSingleUri(context, uri)?.length()
    }

    companion object {
        // Pin to the upstream release tag so downloads are stable.
        const val MODEL_VERSION = "20251001.bd9b849"
        // Prefer self-hosted mirror; fall back to upstream GitHub if needed.
        const val MODEL_BASE_URL = "https://zhixu.app/ocr/"
        const val MODEL_FALLBACK_BASE_URL =
            "https://raw.githubusercontent.com/nihui/ncnn-android-ppocrv5/" +
                MODEL_VERSION +
                "/app/src/main/assets/"
        const val VAULT_DIR_PRIMARY = ".zhixu/ocr/.models/ppocrv5"
    }
}
