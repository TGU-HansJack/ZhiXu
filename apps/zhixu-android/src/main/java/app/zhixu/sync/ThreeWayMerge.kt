package com.zhixu.android.sync

data class ThreeWayMergeResult(
    val ok: Boolean,
    val merged: String? = null,
    val reason: String? = null,
)

object ThreeWayMerge {
    fun mergeText(base: String, local: String, remote: String): ThreeWayMergeResult {
        if (local == remote) return ThreeWayMergeResult(ok = true, merged = local)
        if (local == base) return ThreeWayMergeResult(ok = true, merged = remote)
        if (remote == base) return ThreeWayMergeResult(ok = true, merged = local)

        val baseLines = base.split('\n')
        val localLines = local.split('\n')
        val remoteLines = remote.split('\n')

        // Case 1: append-only merges (common for Inbox.md / daily logs).
        if (isPrefix(baseLines, localLines) && isPrefix(baseLines, remoteLines)) {
            if (isPrefix(remoteLines, localLines)) return ThreeWayMergeResult(ok = true, merged = local)
            if (isPrefix(localLines, remoteLines)) return ThreeWayMergeResult(ok = true, merged = remote)
            val mergedLines = ArrayList<String>(maxOf(localLines.size, remoteLines.size))
            mergedLines.addAll(baseLines)
            val localSuffix = localLines.subList(baseLines.size, localLines.size)
            val remoteSuffix = remoteLines.subList(baseLines.size, remoteLines.size)
            mergedLines.addAll(localSuffix)
            if (remoteSuffix.isNotEmpty()) {
                val overlap = commonPrefixLen(localSuffix, remoteSuffix)
                mergedLines.addAll(remoteSuffix.subList(overlap, remoteSuffix.size))
            }
            return ThreeWayMergeResult(ok = true, merged = mergedLines.joinToString("\n"))
        }

        // Case 2: single contiguous block edits that don't overlap.
        val localBlock = changedBlock(baseLines, localLines)
        val remoteBlock = changedBlock(baseLines, remoteLines)
        if (localBlock != null && remoteBlock != null) {
            val (ls, le, lrep) = localBlock
            val (rs, re, rrep) = remoteBlock
            val disjoint = le <= rs || re <= ls
            if (disjoint) {
                val merged = ArrayList<String>()
                var i = 0
                fun appendBaseUntil(until: Int) {
                    while (i < until && i < baseLines.size) {
                        merged += baseLines[i]
                        i += 1
                    }
                }
                val first = if (ls <= rs) localBlock else remoteBlock
                val second = if (ls <= rs) remoteBlock else localBlock
                appendBaseUntil(first.first)
                merged += first.third
                i = first.second
                appendBaseUntil(second.first)
                merged += second.third
                i = second.second
                appendBaseUntil(baseLines.size)
                return ThreeWayMergeResult(ok = true, merged = merged.joinToString("\n"))
            }
        }

        return ThreeWayMergeResult(ok = false, reason = "overlapping_changes")
    }

    private fun isPrefix(prefix: List<String>, full: List<String>): Boolean {
        if (prefix.size > full.size) return false
        for (i in prefix.indices) if (prefix[i] != full[i]) return false
        return true
    }

    private fun commonPrefixLen(a: List<String>, b: List<String>): Int {
        val n = minOf(a.size, b.size)
        var i = 0
        while (i < n && a[i] == b[i]) i += 1
        return i
    }

    // Returns (baseStart, baseEndExclusive, replacementLines) for a single contiguous edit block; null if edit is complex.
    private fun changedBlock(base: List<String>, variant: List<String>): Triple<Int, Int, List<String>>? {
        val min = minOf(base.size, variant.size)
        var start = 0
        while (start < min && base[start] == variant[start]) start += 1

        var endBase = base.size
        var endVar = variant.size
        while (endBase > start && endVar > start && base[endBase - 1] == variant[endVar - 1]) {
            endBase -= 1
            endVar -= 1
        }

        // If there are multiple disjoint hunks, we can't detect them here; treat as complex.
        // Heuristic: require that the changed region is "reasonably localized".
        val changedBaseLen = endBase - start
        val changedVarLen = endVar - start
        if (changedBaseLen < 0 || changedVarLen < 0) return null

        val replacement = variant.subList(start, endVar)
        return Triple(start, endBase, replacement)
    }
}

