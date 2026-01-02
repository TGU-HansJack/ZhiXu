package com.zhixu.android.ui.components

enum class DiffOp { Equal, Insert, Delete }

data class DiffLine(
    val op: DiffOp,
    val text: String,
)

object LineDiff {
    fun diff(oldText: String, newText: String, maxLines: Int = 5000): List<DiffLine> {
        val oldLines = oldText.split('\n')
        val newLines = newText.split('\n')
        if (oldLines.size + newLines.size > maxLines) {
            return buildList {
                for (l in oldLines) add(DiffLine(DiffOp.Delete, l))
                for (l in newLines) add(DiffLine(DiffOp.Insert, l))
            }
        }
        return diffLines(oldLines, newLines)
    }

    // Myers diff on line arrays.
    private fun diffLines(a: List<String>, b: List<String>): List<DiffLine> {
        val n = a.size
        val m = b.size
        if (n == 0) return b.map { DiffLine(DiffOp.Insert, it) }
        if (m == 0) return a.map { DiffLine(DiffOp.Delete, it) }

        val max = n + m
        val offset = max
        var v = IntArray(2 * max + 1) { 0 }
        val trace = ArrayList<IntArray>(max + 1)

        fun snake(k: Int, xStart: Int): Int {
            var x = xStart
            var y = x - k
            while (x < n && y < m && a[x] == b[y]) {
                x++
                y++
            }
            return x
        }

        for (d in 0..max) {
            trace.add(v.copyOf())
            for (k in -d..d step 2) {
                val kIdx = k + offset
                val x =
                    if (k == -d || (k != d && v[kIdx - 1] < v[kIdx + 1])) {
                        // down (insert in a -> take from b)
                        v[kIdx + 1]
                    } else {
                        // right (delete from a)
                        v[kIdx - 1] + 1
                    }
                val x2 = snake(k, x)
                v[kIdx] = x2
                val y2 = x2 - k
                if (x2 >= n && y2 >= m) {
                    return backtrack(trace, a, b)
                }
            }
        }
        return backtrack(trace, a, b)
    }

    private fun backtrack(trace: List<IntArray>, a: List<String>, b: List<String>): List<DiffLine> {
        val n = a.size
        val m = b.size
        val max = n + m
        val offset = max
        var x = n
        var y = m
        val out = ArrayList<DiffLine>(n + m)

        for (d in trace.size - 1 downTo 1) {
            val v = trace[d - 1]
            val k = x - y
            val kIdx = k + offset

            val prevK =
                if (k == -d || (k != d && v[kIdx - 1] < v[kIdx + 1])) {
                    k + 1
                } else {
                    k - 1
                }
            val prevX = v[prevK + offset]
            val prevY = prevX - prevK

            while (x > prevX && y > prevY) {
                out.add(DiffLine(DiffOp.Equal, a[x - 1]))
                x--
                y--
            }

            if (x == prevX) {
                // insert
                out.add(DiffLine(DiffOp.Insert, b[y - 1]))
                y--
            } else {
                // delete
                out.add(DiffLine(DiffOp.Delete, a[x - 1]))
                x--
            }
        }

        while (x > 0 && y > 0) {
            if (a[x - 1] == b[y - 1]) {
                out.add(DiffLine(DiffOp.Equal, a[x - 1]))
                x--
                y--
            } else {
                out.add(DiffLine(DiffOp.Delete, a[x - 1]))
                x--
            }
        }
        while (x > 0) {
            out.add(DiffLine(DiffOp.Delete, a[x - 1]))
            x--
        }
        while (y > 0) {
            out.add(DiffLine(DiffOp.Insert, b[y - 1]))
            y--
        }

        out.reverse()
        return out
    }
}

