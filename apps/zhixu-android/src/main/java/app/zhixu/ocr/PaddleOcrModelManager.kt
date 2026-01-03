package app.zhixu.ocr

import android.content.Context
import android.net.Uri
import app.zhixu.data.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class PaddleOcrModelBundle(
    val detParamUri: Uri,
    val detBinUri: Uri,
    val recParamUri: Uri,
    val recBinUri: Uri,
    val dictUri: Uri,
    val versionJsonUri: Uri,
)

class PaddleOcrModelManager(
    private val context: Context,
    private val repository: VaultRepository,
    private val http: OkHttpClient = OkHttpClient(),
) {
    private val modelId: String = "ppocrv5_mobile_ncnn"
    private val baseDir: String = ".zhixu/ocr/.models"

    // Default sources: ncnn-assets provides ncnn-ready ppocrv5 models.
    private val detParamUrl = "https://raw.githubusercontent.com/nihui/ncnn-assets/master/models/PP_OCRv5_mobile_det.ncnn.param"
    private val detBinUrl = "https://raw.githubusercontent.com/nihui/ncnn-assets/master/models/PP_OCRv5_mobile_det.ncnn.bin"
    private val recParamUrl = "https://raw.githubusercontent.com/nihui/ncnn-assets/master/models/PP_OCRv5_mobile_rec.ncnn.param"
    private val recBinUrl = "https://raw.githubusercontent.com/nihui/ncnn-assets/master/models/PP_OCRv5_mobile_rec.ncnn.bin"
    private val dictUrl = "https://raw.githubusercontent.com/PaddlePaddle/PaddleOCR/release/2.7/ppocr/utils/ppocr_keys_v1.txt"

    suspend fun ensureModels(vaultRootUri: Uri): PaddleOcrModelBundle =
        withContext(Dispatchers.IO) {
            repository.ensureVaultDirectory(vaultRootUri, baseDir) ?: error("Failed to create $baseDir")

            val versionJsonPath = "$baseDir/version.json"
            val versionJsonFile = repository.ensureVaultFile(vaultRootUri, versionJsonPath, mimeType = "application/json")
                ?: error("Failed to create $versionJsonPath")

            val manifest =
                listOf(
                    ModelFileSpec("PP_OCRv5_mobile_det.ncnn.param", detParamUrl, "text/plain"),
                    ModelFileSpec("PP_OCRv5_mobile_det.ncnn.bin", detBinUrl, "application/octet-stream"),
                    ModelFileSpec("PP_OCRv5_mobile_rec.ncnn.param", recParamUrl, "text/plain"),
                    ModelFileSpec("PP_OCRv5_mobile_rec.ncnn.bin", recBinUrl, "application/octet-stream"),
                    ModelFileSpec("ppocr_keys_v1.txt", dictUrl, "text/plain"),
                )

            val existing = readVersionJson(versionJsonFile.uri)
            val needsDownload = existing == null || existing.optString("modelId") != modelId
            if (needsDownload) {
                downloadAll(vaultRootUri, manifest, versionJsonFile.uri)
            } else {
                // Best-effort: ensure all files exist; redownload if any missing.
                val missing = manifest.any { repository.resolveVaultFileUri(vaultRootUri, "$baseDir/${it.name}") == null }
                if (missing) downloadAll(vaultRootUri, manifest, versionJsonFile.uri)
            }

            val detParamUri = repository.resolveVaultFileUri(vaultRootUri, "$baseDir/${manifest[0].name}") ?: error("Missing det param")
            val detBinUri = repository.resolveVaultFileUri(vaultRootUri, "$baseDir/${manifest[1].name}") ?: error("Missing det bin")
            val recParamUri = repository.resolveVaultFileUri(vaultRootUri, "$baseDir/${manifest[2].name}") ?: error("Missing rec param")
            val recBinUri = repository.resolveVaultFileUri(vaultRootUri, "$baseDir/${manifest[3].name}") ?: error("Missing rec bin")
            val dictUri = repository.resolveVaultFileUri(vaultRootUri, "$baseDir/${manifest[4].name}") ?: error("Missing dict")

            PaddleOcrModelBundle(
                detParamUri = detParamUri,
                detBinUri = detBinUri,
                recParamUri = recParamUri,
                recBinUri = recBinUri,
                dictUri = dictUri,
                versionJsonUri = versionJsonFile.uri,
            )
        }

    suspend fun forceRedownload(vaultRootUri: Uri) {
        withContext(Dispatchers.IO) {
            repository.ensureVaultDirectory(vaultRootUri, baseDir) ?: error("Failed to create $baseDir")
            val versionJsonPath = "$baseDir/version.json"
            val versionJsonFile = repository.ensureVaultFile(vaultRootUri, versionJsonPath, mimeType = "application/json")
                ?: error("Failed to create $versionJsonPath")
            val manifest =
                listOf(
                    ModelFileSpec("PP_OCRv5_mobile_det.ncnn.param", detParamUrl, "text/plain"),
                    ModelFileSpec("PP_OCRv5_mobile_det.ncnn.bin", detBinUrl, "application/octet-stream"),
                    ModelFileSpec("PP_OCRv5_mobile_rec.ncnn.param", recParamUrl, "text/plain"),
                    ModelFileSpec("PP_OCRv5_mobile_rec.ncnn.bin", recBinUrl, "application/octet-stream"),
                    ModelFileSpec("ppocr_keys_v1.txt", dictUrl, "text/plain"),
                )
            downloadAll(vaultRootUri, manifest, versionJsonFile.uri)
        }
    }

    private data class ModelFileSpec(
        val name: String,
        val url: String,
        val mimeType: String,
    )

    private suspend fun downloadAll(vaultRootUri: Uri, files: List<ModelFileSpec>, versionJsonUri: Uri) {
        val fileEntries = JSONArray()
        for (spec in files) {
            val relPath = "$baseDir/${spec.name}"
            val dest = repository.ensureVaultFile(vaultRootUri, relPath, mimeType = spec.mimeType) ?: error("Failed to create $relPath")
            downloadToUri(spec.url, dest.uri)
            val bytes = repository.readBytes(dest.uri) ?: ByteArray(0)
            val sha = sha256(bytes)
            fileEntries.put(
                JSONObject()
                    .put("name", spec.name)
                    .put("url", spec.url)
                    .put("sha256", sha)
                    .put("size", bytes.size),
            )
        }

        val version =
            JSONObject()
                .put("schema", 1)
                .put("modelId", modelId)
                .put("downloadedAtMs", System.currentTimeMillis())
                .put("files", fileEntries)

        repository.writeText(versionJsonUri, version.toString(2) + "\n")
    }

    private suspend fun downloadToUri(url: String, destUri: Uri) {
        val req = Request.Builder().url(url).build()
        val call = http.newCall(req)
        val res = call.execute()
        if (!res.isSuccessful) error("Download failed: $url (${res.code})")
        val body = res.body ?: error("Empty response: $url")
        context.contentResolver.openOutputStream(destUri, "wt")?.use { out ->
            val sink = out.sink().buffer()
            body.source().use { src -> sink.writeAll(src) }
            sink.flush()
        } ?: error("Failed to open output stream for $destUri")
    }

    private suspend fun readVersionJson(uri: Uri): JSONObject? {
        val text = repository.readText(uri).trim()
        if (text.isBlank()) return null
        return runCatching { JSONObject(text) }.getOrNull()
    }

    private fun sha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}

