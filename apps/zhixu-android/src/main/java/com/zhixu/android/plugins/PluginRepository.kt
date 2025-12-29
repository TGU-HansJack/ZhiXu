package com.zhixu.android.plugins

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.zhixu.android.data.vaultRootToDocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class PluginManifest(
    val id: String,
    val name: String?,
    val version: String?,
    val description: String?,
    val actions: List<PluginActionSpec> = emptyList(),
)

data class PluginActionSpec(
    val id: String,
    val label: String,
    val icon: String? = null,
    val place: String? = null,
    val ringIndex: Int? = null,
)

data class InstalledPlugin(
    val manifest: PluginManifest,
    val enabled: Boolean,
)

data class InstallResult(
    val ok: Boolean,
    val message: String,
    val pluginId: String? = null,
)

class PluginRepository(
    private val context: Context,
) {
    suspend fun listInstalled(rootUri: Uri): List<InstalledPlugin> = withContext(Dispatchers.IO) {
        val pluginsDir = ensurePluginsDir(rootUri) ?: return@withContext emptyList()
        val enabledIds = readEnabledSet(pluginsDir)

        pluginsDir.listFiles()
            .filter { it.isDirectory }
            .mapNotNull { dir ->
                val manifest = readManifest(dir) ?: return@mapNotNull null
                InstalledPlugin(
                    manifest = manifest,
                    enabled = enabledIds.contains(manifest.id),
                )
            }
            .sortedBy { (it.manifest.name ?: it.manifest.id).lowercase() }
    }

    suspend fun setEnabled(rootUri: Uri, pluginId: String, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        val pluginsDir = ensurePluginsDir(rootUri) ?: return@withContext false
        val stateFile = ensurePluginStateFile(pluginsDir) ?: return@withContext false
        val state = readJsonObject(stateFile.uri) ?: JSONObject()
        val enabledArray = state.optJSONArray("enabled") ?: JSONArray()
        val set = enabledArray.toStringList().toMutableSet()
        if (enabled) set.add(pluginId) else set.remove(pluginId)
        state.put("enabled", JSONArray(set.toList().sorted()))
        writeJsonObject(stateFile.uri, state)
        true
    }

    suspend fun removePlugin(rootUri: Uri, pluginId: String): Boolean = withContext(Dispatchers.IO) {
        val pluginsDir = ensurePluginsDir(rootUri) ?: return@withContext false
        val pluginDir = pluginsDir.findFile(pluginId) ?: return@withContext false
        val removed = pluginDir.delete()
        if (removed) {
            setEnabled(rootUri, pluginId, enabled = false)
        }
        removed
    }

    suspend fun installBundledPlugin(rootUri: Uri, pluginId: String): InstallResult = withContext(Dispatchers.IO) {
        val pluginsDir = ensurePluginsDir(rootUri) ?: return@withContext InstallResult(false, "Missing plugins directory")
        val already = pluginsDir.findFile(pluginId)
        if (already != null) return@withContext InstallResult(false, "Plugin already installed", pluginId)

        val assetRoot = "bundled-plugins/$pluginId"
        val manifestText = readAssetTextOrNull("$assetRoot/manifest.json")
            ?: return@withContext InstallResult(false, "Bundled plugin manifest not found: $pluginId")
        val manifest = parseManifestFromText(manifestText)
            ?: return@withContext InstallResult(false, "Invalid bundled plugin manifest.json")

        val destDir = pluginsDir.createDirectory(pluginId)
            ?: return@withContext InstallResult(false, "Failed to create plugin folder: $pluginId")

        val ok = copyAssetsDirRecursively(assetRoot, destDir)
        if (!ok) {
            destDir.delete()
            return@withContext InstallResult(false, "Failed to copy bundled plugin files")
        }

        InstallResult(true, "Installed: ${manifest.name ?: manifest.id}", manifest.id)
    }

    suspend fun installFromLocalFolder(rootUri: Uri, folderUri: Uri): InstallResult = withContext(Dispatchers.IO) {
        val pluginsDir = ensurePluginsDir(rootUri) ?: return@withContext InstallResult(false, "Missing plugins directory")
        val sourceDir = DocumentFile.fromTreeUri(context, folderUri)
            ?.takeIf { it.isDirectory }
            ?: return@withContext InstallResult(false, "Invalid folder")

        val sourceManifestFile = sourceDir.findFile("manifest.json")
            ?.takeIf { it.isFile }
            ?: return@withContext InstallResult(false, "manifest.json not found in selected folder")
        val manifestText = readText(sourceManifestFile.uri) ?: return@withContext InstallResult(false, "Failed to read manifest.json")
        val manifest = parseManifestFromText(manifestText) ?: return@withContext InstallResult(false, "Invalid manifest.json")

        val pluginId = manifest.id
        if (pluginsDir.findFile(pluginId) != null) return@withContext InstallResult(false, "Plugin already installed", pluginId)
        val destDir = pluginsDir.createDirectory(pluginId)
            ?: return@withContext InstallResult(false, "Failed to create plugin folder: $pluginId", pluginId)

        val ok = copyDocumentDirRecursively(sourceDir, destDir)
        if (!ok) {
            destDir.delete()
            return@withContext InstallResult(false, "Failed to copy plugin files", pluginId)
        }
        InstallResult(true, "Installed: ${manifest.name ?: manifest.id}", pluginId)
    }

    suspend fun installFromGitUrl(rootUri: Uri, gitUrl: String): InstallResult = withContext(Dispatchers.IO) {
        val pluginsDir = ensurePluginsDir(rootUri) ?: return@withContext InstallResult(false, "Missing plugins directory")
        val tmp = File(context.cacheDir, "plugins-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            runCatching {
                Git.cloneRepository()
                    .setURI(gitUrl.trim())
                    .setDirectory(tmp)
                    .call()
            }.getOrElse { e ->
                return@withContext InstallResult(false, "Git clone failed: ${e.message ?: e.javaClass.simpleName}")
            }

            val manifestFile = File(tmp, "manifest.json")
            if (!manifestFile.exists()) return@withContext InstallResult(false, "manifest.json not found at repo root")
            val manifestText = runCatching { manifestFile.readText(Charsets.UTF_8) }
                .getOrElse { return@withContext InstallResult(false, "Failed to read manifest.json") }
            val manifest = parseManifestFromText(manifestText)
                ?: return@withContext InstallResult(false, "Invalid manifest.json")

            val pluginId = manifest.id
            if (pluginsDir.findFile(pluginId) != null) return@withContext InstallResult(false, "Plugin already installed", pluginId)
            val destDir = pluginsDir.createDirectory(pluginId)
                ?: return@withContext InstallResult(false, "Failed to create plugin folder: $pluginId", pluginId)

            val ok = copyFileDirRecursively(tmp, destDir) { file ->
                val name = file.name
                name != ".git" && name != ".github"
            }
            if (!ok) {
                destDir.delete()
                return@withContext InstallResult(false, "Failed to copy plugin files", pluginId)
            }

            InstallResult(true, "Installed: ${manifest.name ?: manifest.id}", pluginId)
        } finally {
            tmp.deleteRecursively()
        }
    }

    suspend fun readPluginConfig(rootUri: Uri, pluginId: String, fileName: String = "config.json"): JSONObject? =
        withContext(Dispatchers.IO) {
            val file = ensurePluginConfigFile(rootUri, pluginId, fileName) ?: return@withContext null
            readJsonObject(file.uri)
        }

    suspend fun writePluginConfig(rootUri: Uri, pluginId: String, json: JSONObject, fileName: String = "config.json"): Boolean =
        withContext(Dispatchers.IO) {
            val file = ensurePluginConfigFile(rootUri, pluginId, fileName) ?: return@withContext false
            writeJsonObject(file.uri, json)
            true
        }

    suspend fun readPluginFileText(rootUri: Uri, pluginId: String, fileName: String): String? =
        withContext(Dispatchers.IO) {
            val pluginsDir = ensurePluginsDir(rootUri) ?: return@withContext null
            val pluginDir = pluginsDir.findFile(pluginId) ?: return@withContext null
            val file = pluginDir.findFile(fileName)?.takeIf { it.isFile } ?: return@withContext null
            readText(file.uri)
        }

    suspend fun readPluginReadme(rootUri: Uri, pluginId: String): String? =
        readPluginFileText(rootUri, pluginId, "README.md") ?: readPluginFileText(rootUri, pluginId, "readme.md")

    private fun ensurePluginsDir(rootUri: Uri): DocumentFile? {
        val root = vaultRootToDocumentFile(context, rootUri) ?: return null
        val zhixu = root.findFile(".zhixu") ?: root.createDirectory(".zhixu") ?: return null
        return zhixu.findFile("plugins") ?: zhixu.createDirectory("plugins")
    }

    private fun ensurePluginConfigFile(rootUri: Uri, pluginId: String, fileName: String): DocumentFile? {
        val pluginsDir = ensurePluginsDir(rootUri) ?: return null
        val pluginDir = pluginsDir.findFile(pluginId) ?: return null
        return pluginDir.findFile(fileName) ?: createFileExact(pluginDir, "application/json", fileName)
    }

    private fun ensurePluginStateFile(pluginsDir: DocumentFile): DocumentFile? {
        val state = pluginsDir.findFile("state.json")
            ?: createFileExact(pluginsDir, "application/json", "state.json")
            ?: return null
        if (state.length() == 0L) {
            writeJsonObject(state.uri, JSONObject().put("enabled", JSONArray()))
        }
        return state
    }

    private fun createFileExact(parent: DocumentFile, mimeType: String, displayName: String): DocumentFile? {
        if (parent.uri.scheme.equals("file", ignoreCase = true)) {
            val dir = parent.uri.path?.let(::File) ?: return null
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, displayName)
            runCatching { if (!file.exists()) file.createNewFile() }
            return DocumentFile.fromFile(file)
        }
        return parent.createFile(mimeType, displayName)
    }

    private fun readEnabledSet(pluginsDir: DocumentFile): Set<String> {
        val stateFile = ensurePluginStateFile(pluginsDir) ?: return emptySet()
        val obj = readJsonObject(stateFile.uri) ?: return emptySet()
        val enabled = obj.optJSONArray("enabled") ?: return emptySet()
        return enabled.toStringList().toSet()
    }

    private fun readManifest(pluginDir: DocumentFile): PluginManifest? {
        val manifestFile = pluginDir.findFile("manifest.json")?.takeIf { it.isFile } ?: return null
        val text = readText(manifestFile.uri) ?: return null
        return parseManifestFromText(text)
    }

    private fun parseManifestFromText(text: String): PluginManifest? {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val id = json.optString("id").trim()
        if (id.isBlank()) return null
        val actions = parseActions(json.optJSONArray("actions"))
        return PluginManifest(
            id = id,
            name = json.optString("name").takeIf { it.isNotBlank() },
            version = json.optString("version").takeIf { it.isNotBlank() },
            description = json.optString("description").takeIf { it.isNotBlank() },
            actions = actions,
        )
    }

    private fun parseActions(arr: JSONArray?): List<PluginActionSpec> {
        if (arr == null || arr.length() == 0) return emptyList()
        val out = ArrayList<PluginActionSpec>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val actionId = obj.optString("id").trim()
            val label = obj.optString("label").trim()
            if (actionId.isBlank() || label.isBlank()) continue
            out += PluginActionSpec(
                id = actionId,
                label = label,
                icon = obj.optString("icon").takeIf { it.isNotBlank() },
                place = obj.optString("place").takeIf { it.isNotBlank() },
                ringIndex = obj.optInt("ringIndex").takeIf { obj.has("ringIndex") },
            )
        }
        return out
    }

    private fun readText(uri: Uri): String? {
        val resolver: ContentResolver = context.contentResolver
        return runCatching {
            resolver.openInputStream(uri)?.use { input -> input.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull()
    }

    private fun readJsonObject(uri: Uri): JSONObject? =
        runCatching {
            val text = readText(uri) ?: return@runCatching null
            JSONObject(text)
        }.getOrNull()

    private fun writeJsonObject(uri: Uri, json: JSONObject) {
        val resolver: ContentResolver = context.contentResolver
        runCatching {
            resolver.openOutputStream(uri, "wt")?.use { out ->
                out.write((json.toString(2) + "\n").toByteArray(Charsets.UTF_8))
            }
        }.onFailure { e ->
            Log.e("Zhixu", "writeJsonObject failed: $uri", e)
        }
    }

    private fun copyDocumentDirRecursively(srcDir: DocumentFile, destDir: DocumentFile): Boolean {
        srcDir.listFiles().forEach { src ->
            val name = src.name ?: return@forEach
            if (src.isDirectory) {
                val childDest = destDir.findFile(name) ?: destDir.createDirectory(name) ?: return false
                if (!copyDocumentDirRecursively(src, childDest)) return false
            } else if (src.isFile) {
                val bytes = readBytes(src.uri) ?: return false
                val destFile = destDir.findFile(name) ?: destDir.createFile("application/octet-stream", name) ?: return false
                if (!writeBytes(destFile.uri, bytes)) return false
            }
        }
        return true
    }

    private fun readBytes(uri: Uri): ByteArray? {
        val resolver: ContentResolver = context.contentResolver
        return runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
    }

    private fun writeBytes(uri: Uri, bytes: ByteArray): Boolean {
        val resolver: ContentResolver = context.contentResolver
        return runCatching {
            resolver.openOutputStream(uri, "wt")?.use { out -> out.write(bytes) } ?: return@runCatching false
            true
        }.getOrDefault(false)
    }

    private fun copyFileDirRecursively(src: File, destDir: DocumentFile, accept: (File) -> Boolean): Boolean {
        val children = src.listFiles() ?: return true
        for (child in children) {
            if (!accept(child)) continue
            if (child.isDirectory) {
                val sub = destDir.findFile(child.name) ?: destDir.createDirectory(child.name) ?: return false
                if (!copyFileDirRecursively(child, sub, accept)) return false
            } else if (child.isFile) {
                val bytes = runCatching { child.readBytes() }.getOrNull() ?: return false
                val destFile =
                    destDir.findFile(child.name)
                        ?: destDir.createFile("application/octet-stream", child.name)
                        ?: return false
                if (!writeBytes(destFile.uri, bytes)) return false
            }
        }
        return true
    }

    private fun readAssetTextOrNull(path: String): String? =
        runCatching {
            context.assets.open(path).use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull()

    private fun copyAssetsDirRecursively(assetPath: String, destDir: DocumentFile): Boolean {
        val assets = context.assets
        val children = runCatching { assets.list(assetPath)?.toList().orEmpty() }.getOrNull() ?: return false
        for (name in children) {
            val childPath = "$assetPath/$name"
            val grandChildren = runCatching { assets.list(childPath) }.getOrNull()
            val isDir = grandChildren != null && grandChildren.isNotEmpty()
            if (isDir) {
                val sub = destDir.findFile(name) ?: destDir.createDirectory(name) ?: return false
                if (!copyAssetsDirRecursively(childPath, sub)) return false
            } else {
                val bytes = runCatching { assets.open(childPath).use { it.readBytes() } }.getOrNull() ?: return false
                val destFile = destDir.findFile(name) ?: destDir.createFile("application/octet-stream", name) ?: return false
                if (!writeBytes(destFile.uri, bytes)) return false
            }
        }
        return true
    }
}

private fun JSONArray.toStringList(): List<String> =
    (0 until length())
        .mapNotNull { idx -> optString(idx).takeIf { it.isNotBlank() } }
