package app.zhixu.ocr

import android.net.Uri

class OcrEngineCache(
    private val factory: (Uri) -> OcrEngine,
    private val releaser: (OcrEngine) -> Unit = {},
) {
    private val lock = Any()
    private var cachedKey: String? = null
    private var cachedEngine: OcrEngine? = null

    fun get(vaultRootUri: Uri): OcrEngine {
        val key = vaultRootUri.toString()
        synchronized(lock) {
            val existing = cachedEngine
            if (existing != null && cachedKey == key) return existing
            if (existing != null) {
                runCatching { releaser(existing) }
            }
            val engine = factory(vaultRootUri)
            cachedKey = key
            cachedEngine = engine
            return engine
        }
    }
}

