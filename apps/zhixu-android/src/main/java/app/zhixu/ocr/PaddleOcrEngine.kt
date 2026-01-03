package app.zhixu.ocr

import android.graphics.Bitmap
import app.zhixu.data.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

class PaddleOcrEngine(
    private val repository: VaultRepository,
    private val modelManager: PaddleOcrModelManager,
    private val vaultRootUri: android.net.Uri,
) : OcrEngine {
    private val initLock = Mutex()
    @Volatile private var handle: Long = 0L

    private suspend fun ensureInitialized() {
        if (handle != 0L) return
        initLock.withLock {
            if (handle != 0L) return
            val model = modelManager.ensureModels(vaultRootUri)
            val detParam = repository.readBytes(model.detParamUri) ?: error("Missing det param")
            val detBin = repository.readBytes(model.detBinUri) ?: error("Missing det bin")
            val recParam = repository.readBytes(model.recParamUri) ?: error("Missing rec param")
            val recBin = repository.readBytes(model.recBinUri) ?: error("Missing rec bin")
            val dict = repository.readBytes(model.dictUri) ?: error("Missing dict")
            handle =
                PaddleOcrNative.nativeInit(
                    detParam = detParam,
                    detBin = detBin,
                    recParam = recParam,
                    recBin = recBin,
                    dictTxt = dict,
                    useVulkan = false,
                )
        }
    }

    override suspend fun recognize(bitmap: Bitmap): OcrResult =
        withContext(Dispatchers.Default) {
            ensureInitialized()
            val json = PaddleOcrNative.nativeRecognize(handle, bitmap)
            parseNativeJson(json)
        }

    private fun parseNativeJson(json: String): OcrResult {
        val obj = JSONObject(json)
        val blocksJson = obj.optJSONArray("blocks")
        val blocks =
            buildList {
                if (blocksJson == null) return@buildList
                for (i in 0 until blocksJson.length()) {
                    val b = blocksJson.optJSONObject(i) ?: continue
                    add(
                        OcrBlock(
                            text = b.optString("text"),
                            left = b.optInt("l"),
                            top = b.optInt("t"),
                            right = b.optInt("r"),
                            bottom = b.optInt("b"),
                            confidence = b.optDouble("c", -1.0).toFloat(),
                        ),
                    )
                }
            }
        return OcrResult(blocks = blocks, fullText = obj.optString("fullText"))
    }
}

