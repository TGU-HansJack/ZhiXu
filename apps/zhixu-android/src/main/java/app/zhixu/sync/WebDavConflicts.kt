package app.zhixu.sync

data class WebDavUnresolvedConflict(
    val path: String,
    val createdAtMs: Long,
    val reason: String,
    val localArtifactPath: String? = null,
    val remoteArtifactPath: String? = null,
)

