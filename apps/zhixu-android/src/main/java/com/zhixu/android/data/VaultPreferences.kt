package com.zhixu.android.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class VaultPreferences(
    private val context: Context,
) {
    private val vaultRootUriKey = stringPreferencesKey("vault_root_uri")
    private val docListCacheVaultRootUriKey = stringPreferencesKey("doc_list_cache_vault_root_uri")
    private val docListCacheJsonKey = stringPreferencesKey("doc_list_cache_json")
    private val docListCacheUpdatedAtMsKey = longPreferencesKey("doc_list_cache_updated_at_ms")

    val vaultRootUri: Flow<String?> =
        context.dataStore.data.map { prefs: Preferences -> prefs[vaultRootUriKey] }

    suspend fun setVaultRootUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri == null) prefs.remove(vaultRootUriKey)
            else prefs[vaultRootUriKey] = uri
        }
    }

    suspend fun getDocListCache(vaultRootUri: Uri): DocListCache? =
        withContext(Dispatchers.IO) {
            runCatching {
                val key = vaultRootUri.toString()
                val prefs: Preferences = context.dataStore.data.first()
                val cachedRoot = prefs[docListCacheVaultRootUriKey]
                if (cachedRoot.isNullOrBlank() || cachedRoot != key) return@runCatching null
                val json = prefs[docListCacheJsonKey].orEmpty()
                if (json.isBlank()) return@runCatching null
                val updatedAtMs = prefs[docListCacheUpdatedAtMsKey] ?: 0L
                val docs = decodeDocListCache(json)
                DocListCache(vaultRootUri = vaultRootUri, updatedAtMs = updatedAtMs, docs = docs)
            }.getOrNull()
        }

    suspend fun setDocListCache(
        vaultRootUri: Uri,
        docs: List<UiDoc>,
        updatedAtMs: Long = System.currentTimeMillis(),
    ) {
        withContext(Dispatchers.IO) {
            val json = runCatching { encodeDocListCache(docs) }.getOrNull().orEmpty()
            context.dataStore.edit { prefs ->
                prefs[docListCacheVaultRootUriKey] = vaultRootUri.toString()
                prefs[docListCacheJsonKey] = json
                prefs[docListCacheUpdatedAtMsKey] = updatedAtMs
            }
        }
    }

    suspend fun touchDocListCacheUpdatedAt(
        vaultRootUri: Uri,
        updatedAtMs: Long = System.currentTimeMillis(),
    ) {
        withContext(Dispatchers.IO) {
            context.dataStore.edit { prefs ->
                prefs[docListCacheVaultRootUriKey] = vaultRootUri.toString()
                prefs[docListCacheUpdatedAtMsKey] = updatedAtMs
            }
        }
    }
}

data class DocListCache(
    val vaultRootUri: Uri,
    val updatedAtMs: Long,
    val docs: List<UiDoc>,
)

private fun encodeDocListCache(docs: List<UiDoc>): String {
    val arr = JSONArray()
    for (doc in docs) {
        val obj =
            JSONObject()
                .put("name", doc.name)
                .put("uri", doc.uri.toString())
                .put("lastModified", doc.lastModified)
                .put("size", doc.size)
                .put("baseName", doc.baseName)
                .put("createdAt", doc.createdAt)
                .put("createdAtText", doc.createdAtText)
                .put("editedAtText", doc.editedAtText)
        arr.put(obj)
    }
    return arr.toString()
}

private fun decodeDocListCache(json: String): List<UiDoc> {
    val arr = JSONArray(json)
    val out = ArrayList<UiDoc>(arr.length())
    for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        val name = obj.optString("name").orEmpty()
        val uriStr = obj.optString("uri").orEmpty()
        if (name.isBlank() || uriStr.isBlank()) continue
        out +=
            UiDoc(
                name = name,
                uri = Uri.parse(uriStr),
                lastModified = obj.optLong("lastModified", 0L),
                size = obj.optLong("size", 0L),
                baseName = obj.optString("baseName").orEmpty(),
                createdAt =
                    obj.optLong("createdAt", 0L).let { created ->
                        if (created > 0L) created else obj.optLong("lastModified", 0L)
                    },
                createdAtText =
                    obj.optString("createdAtText").orEmpty().ifBlank {
                        obj.optString("editedAtText").orEmpty()
                    },
                editedAtText = obj.optString("editedAtText").orEmpty(),
            )
    }
    return out
}
