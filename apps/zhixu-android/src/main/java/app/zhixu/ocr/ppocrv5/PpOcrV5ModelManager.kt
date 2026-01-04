package app.zhixu.ocr.ppocrv5

import android.content.Context
import android.net.Uri
import android.util.Log
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

        // Mirror to vault for cache management / portability.
        repository.ensureVaultStructure(vaultRootUri)
        val vaultDirs = listOf(VAULT_DIR_PRIMARY, VAULT_DIR_LEGACY)
        val versionJson =
            JSONObject()
                .put("engine", "ppocrv5ncnn")
                .put("model", "mobile")
                .put("version", version)
                .put("source", MODEL_BASE_URL)
                .toString(2) + "\n"

        for (dir in vaultDirs) {
            val versionUri = repository.ensureVaultFile(vaultRootUri, "$dir/version.json", "application/json")
            repository.writeText(versionUri, versionJson)
            for (file in listOf(detParam, detBin, recParam, recBin)) {
                val mime =
                    when {
                        file.name.endsWith(".param", ignoreCase = true) -> "text/plain"
                        else -> "application/octet-stream"
                    }
                val uri = repository.ensureVaultFile(vaultRootUri, "$dir/${file.name}", mime)
                repository.writeBytes(uri, file.readBytes())
            }
        }

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
        Log.i("ZhixuOcr", "ppocrv5 models downloading from network")
        downloadTo(detParam, MODEL_BASE_URL + detParam.name)
        downloadTo(detBin, MODEL_BASE_URL + detBin.name)
        downloadTo(recParam, MODEL_BASE_URL + recParam.name)
        downloadTo(recBin, MODEL_BASE_URL + recBin.name)
    }

    private suspend fun tryRestoreFromVault(
        vaultRootUri: Uri,
        detParam: File,
        detBin: File,
        recParam: File,
        recBin: File,
    ): Boolean {
        val required = listOf(detParam.name, detBin.name, recParam.name, recBin.name)
        val searchDirs = listOf(VAULT_DIR_PRIMARY, VAULT_DIR_LEGACY)

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

    private fun downloadTo(dest: File, url: String) {
        val tmp = File(dest.parentFile, dest.name + ".download")
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("Download failed ${resp.code} for $url")
            val body = resp.body ?: error("Empty response body for $url")
            tmp.sink().buffer().use { sink ->
                sink.writeAll(body.source())
            }
        }
        if (dest.exists()) dest.delete()
        if (!tmp.renameTo(dest)) {
            dest.writeBytes(tmp.readBytes())
            tmp.delete()
        }
    }

    companion object {
        // Pin to the upstream release tag so downloads are stable.
        const val MODEL_VERSION = "20251001.bd9b849"
        const val MODEL_BASE_URL =
            "https://raw.githubusercontent.com/nihui/ncnn-android-ppocrv5/" +
                MODEL_VERSION +
                "/app/src/main/assets/"
        const val VAULT_DIR_PRIMARY = ".zhixu/ocr/.models/ppocrv5"
        // Compatibility: some users may have created without leading dot folder.
        const val VAULT_DIR_LEGACY = ".zhixu/ocr/model/ppocrv5"
    }
}
