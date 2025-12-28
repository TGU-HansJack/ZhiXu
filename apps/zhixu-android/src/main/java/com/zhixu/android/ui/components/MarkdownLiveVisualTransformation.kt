package com.zhixu.android.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

/**
 * Cursor-aware "live preview" for Markdown:
 * - When selection/cursor is inside a token, show the raw Markdown syntax.
 * - Otherwise, hide most syntax markers and render basic inline styles.
 */
class MarkdownLiveVisualTransformation(
    private val selectionStart: Int,
    private val selectionEnd: Int,
    private val baseFontSizeSp: Float,
    private val linkColor: Color,
    private val codeBackground: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text
        val n = original.length

        val origToTrans = IntArray(n + 1)
        val transToOrig = ArrayList<Int>(n + 1)

        val styles = ArrayList<Triple<SpanStyle, Int, Int>>()
        val borderStyles = ArrayList<Pair<IntRange, SpanStyle>>()

        var transIndex = 0
        var pendingLineBg: Pair<Int, Int>? = null // startTrans to endOrigExclusive
        var pendingLinePrefixStyle: Pair<Int, Int>? = null // startTrans to endOrigExclusive (e.g. quote bar / list marker)

        fun selectionOverlaps(rangeStart: Int, rangeEndExclusive: Int): Boolean {
            val selStart = minOf(selectionStart, selectionEnd).coerceIn(0, n)
            val selEnd = maxOf(selectionStart, selectionEnd).coerceIn(0, n)
            return if (selStart == selEnd) {
                selStart in rangeStart..rangeEndExclusive
            } else {
                selStart < rangeEndExclusive && rangeStart < selEnd
            }
        }

        fun emitChar(c: Char, origIndex: Int, out: StringBuilder) {
            out.append(c)
            transToOrig.add(origIndex)
            transIndex++
        }

        fun emitVirtualChar(c: Char, anchorOrigIndex: Int, out: StringBuilder) {
            out.append(c)
            transToOrig.add(anchorOrigIndex.coerceIn(0, n))
            transIndex++
        }

        fun emitRange(start: Int, endExclusive: Int, out: StringBuilder): Pair<Int, Int> {
            val startTrans = transIndex
            var i = start
            while (i < endExclusive && i < n) {
                origToTrans[i] = transIndex
                emitChar(original[i], i, out)
                i++
            }
            return startTrans to transIndex
        }

        fun emitRangeReplace(
            start: Int,
            endExclusive: Int,
            out: StringBuilder,
            replace: (Char) -> Char,
        ): Pair<Int, Int> {
            val startTrans = transIndex
            var idx = start
            while (idx < endExclusive && idx < n) {
                origToTrans[idx] = transIndex
                emitChar(replace(original[idx]), idx, out)
                idx++
            }
            return startTrans to transIndex
        }

        fun skipRange(start: Int, endExclusive: Int) {
            var i = start
            while (i < endExclusive && i < n) {
                origToTrans[i] = transIndex
                i++
            }
        }

        fun findClosing(marker: String, from: Int, lineEnd: Int): Int {
            val idx = original.indexOf(marker, startIndex = from)
            return if (idx >= 0 && idx < lineEnd) idx else -1
        }

        fun isTableSeparatorCell(cell: String): Boolean {
            val t = cell.trim()
            if (t.isEmpty()) return false
            if (!t.any { it == '-' }) return false
            return t.all { it == '-' || it == ':' }
        }

        fun findLineEnd(from: Int): Int =
            original.indexOf('\n', startIndex = from).let { if (it < 0) n else it }

        fun hasPipeInRange(start: Int, endExclusive: Int): Boolean =
            original.indexOf('|', startIndex = start).let { it in start until endExclusive }

        fun isSeparatorLine(lineStart: Int, lineEnd: Int): Boolean {
            val s = original.substring(lineStart, lineEnd).trim().trim('|')
            if (s.isBlank()) return false
            val parts = s.split('|')
            if (parts.isEmpty()) return false
            return parts.all { part ->
                val t = part.trim().replace(" ", "").replace("\t", "")
                if (t.isEmpty()) false else isTableSeparatorCell(t)
            }
        }

        data class TableLine(
            val start: Int,
            val end: Int,
            val isSeparator: Boolean,
            val pipes: IntArray,
        )

        fun collectPipes(lineStart: Int, lineEnd: Int): IntArray {
            val xs = ArrayList<Int>()
            var idx = original.indexOf('|', startIndex = lineStart)
            while (idx >= 0 && idx < lineEnd) {
                xs.add(idx)
                idx = original.indexOf('|', startIndex = idx + 1)
            }
            return xs.toIntArray()
        }

        fun computeCellBounds(
            lineStart: Int,
            lineEnd: Int,
            pipes: IntArray,
        ): List<Pair<Int, Int>> {
            if (pipes.isEmpty()) return listOf(lineStart to lineEnd)

            val hasLeading = pipes.first() == lineStart
            val hasTrailing = pipes.last() == lineEnd - 1

            val boundaries = ArrayList<Int>(pipes.size + 2)
            if (hasLeading) boundaries.add(lineStart) else boundaries.add(lineStart - 1)
            for (p in pipes) boundaries.add(p)
            if (hasTrailing) boundaries.add(lineEnd - 1) else boundaries.add(lineEnd)

            val cells = ArrayList<Pair<Int, Int>>()
            for (k in 0 until boundaries.size - 1) {
                val left = boundaries[k]
                val right = boundaries[k + 1]
                val start = (left + 1).coerceAtLeast(lineStart)
                val end = right.coerceAtMost(lineEnd)
                if (start <= end) cells.add(start to end)
            }
            return cells
        }

        fun isTableBlockStart(lineStart: Int, lineEnd: Int): Boolean {
            if (!hasPipeInRange(lineStart, lineEnd)) return false
            val nextStart = (lineEnd + 1).coerceAtMost(n)
            if (nextStart >= n) return false
            val nextEnd = findLineEnd(nextStart)
            return hasPipeInRange(nextStart, nextEnd) && isSeparatorLine(nextStart, nextEnd)
        }

        fun cursorOnTableSyntax(lineStart: Int, lineEnd: Int, pipes: IntArray): Boolean {
            val selStart = minOf(selectionStart, selectionEnd).coerceIn(0, n)
            val selEnd = maxOf(selectionStart, selectionEnd).coerceIn(0, n)
            if (selStart == selEnd) {
                val c = selStart.coerceIn(lineStart, lineEnd.coerceAtLeast(lineStart))
                if (c in lineStart until lineEnd) {
                    val ch = original[c]
                    if (ch == '|' || ch == '-' || ch == ':') return true
                }
                return false
            }
            // Range selection: if it includes any structural char, keep raw.
            for (p in pipes) {
                if (p >= lineStart && p < lineEnd && selectionOverlaps(p, p + 1)) return true
            }
            val idxDash = original.indexOf('-', startIndex = lineStart).takeIf { it in lineStart until lineEnd } ?: -1
            if (idxDash >= 0 && selectionOverlaps(idxDash, idxDash + 1)) return true
            val idxColon = original.indexOf(':', startIndex = lineStart).takeIf { it in lineStart until lineEnd } ?: -1
            if (idxColon >= 0 && selectionOverlaps(idxColon, idxColon + 1)) return true
            return false
        }

        fun flushPendingLineStyles(atOrigIndex: Int) {
            val pendingBg = pendingLineBg
            if (pendingBg != null && atOrigIndex >= pendingBg.second) {
                val start = pendingBg.first
                val end = transIndex
                if (start < end) {
                    styles.add(Triple(SpanStyle(background = codeBackground), start, end))
                }
                pendingLineBg = null
            }
            val pendingPrefix = pendingLinePrefixStyle
            if (pendingPrefix != null && atOrigIndex >= pendingPrefix.second) {
                val start = pendingPrefix.first
                val end = transIndex
                if (start < end) {
                    styles.add(Triple(SpanStyle(color = linkColor, fontWeight = FontWeight.Bold), start, end))
                }
                pendingLinePrefixStyle = null
            }
        }

        val out = StringBuilder(n)
        var i = 0
        while (i < n) {
            flushPendingLineStyles(i)
            origToTrans[i] = transIndex
            val c = original[i]
            if (c == '\n') {
                emitChar(c, i, out)
                i++
                continue
            }

            val lineEnd = original.indexOf('\n', startIndex = i).let { if (it < 0) n else it }
            val atLineStart = i == 0 || original[i - 1] == '\n'

            // Tables: render a whole block (header + separator + rows) with aligned columns and box drawing chars.
            if (atLineStart && isTableBlockStart(i, lineEnd)) {
                val lines = ArrayList<TableLine>()
                var scanStart = i
                var scanEnd = lineEnd
                while (scanStart < n) {
                    val end = findLineEnd(scanStart)
                    if (!hasPipeInRange(scanStart, end)) break
                    val pipes = collectPipes(scanStart, end)
                    lines.add(
                        TableLine(
                            start = scanStart,
                            end = end,
                            isSeparator = isSeparatorLine(scanStart, end),
                            pipes = pipes,
                        ),
                    )
                    if (end >= n || original.getOrNull(end) != '\n') break
                    scanStart = end + 1
                    scanEnd = findLineEnd(scanStart)
                }

                val cellBoundsByLine =
                    lines.map { tl -> computeCellBounds(tl.start, tl.end, tl.pipes) }
                val colCount = cellBoundsByLine.maxOfOrNull { it.size } ?: 0
                val colWidths = IntArray(colCount) { 0 }

                for (li in lines.indices) {
                    val tl = lines[li]
                    if (tl.isSeparator) continue
                    val cells = cellBoundsByLine[li]
                    for (col in 0 until colCount) {
                        val (cs, ce) = cells.getOrNull(col) ?: (tl.end to tl.end)
                        var s = cs
                        var e = ce
                        while (s < e && original[s].isWhitespace()) s++
                        while (e > s && original[e - 1].isWhitespace()) e--
                        val len = (e - s).coerceAtLeast(0)
                        if (len > colWidths[col]) colWidths[col] = len
                    }
                }

                // Keep tables readable on small screens: cap column widths and use ellipsis.
                // This avoids long single-line rows that wrap and visually break the grid.
                val minColWidth = 6
                val maxTotalChars = 44
                val overhead = 3 * colCount + 1 // approximated for "│ " + cells + " │" + inter-col spacing
                val maxSum =
                    (maxTotalChars - overhead)
                        .coerceAtLeast(colCount * minColWidth)
                        .coerceAtLeast(0)
                val colCaps = IntArray(colCount) { (maxSum / colCount).coerceAtLeast(minColWidth) }
                if (colCount == 2) {
                    val firstCap = minOf(12, colCaps[0]).coerceAtLeast(minColWidth)
                    colCaps[0] = firstCap
                    colCaps[1] = (maxSum - firstCap).coerceAtLeast(minColWidth)
                }
                for (col in 0 until colCount) {
                    colWidths[col] = minOf(colWidths[col], colCaps[col])
                }

                fun renderAlignedRow(tl: TableLine, cells: List<Pair<Int, Int>>) {
                    val rowStartTrans = transIndex
                    // Ensure every original offset in the row has a valid mapping (pipes/spaces included).
                    skipRange(tl.start, tl.end)

                    // Gray background + monospace for whole row.
                    styles.add(
                        Triple(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = codeBackground,
                            ),
                            rowStartTrans,
                            rowStartTrans, // end filled later
                        ),
                    )
                    val borderStyle = SpanStyle(color = linkColor, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)

                    fun emitBorder(ch: Char, anchor: Int) {
                        val s = transIndex
                        emitVirtualChar(ch, anchor, out)
                        borderStyles.add((s until transIndex) to borderStyle)
                    }

                    fun emitSpaces(count: Int, anchor: Int) {
                        repeat(count.coerceAtLeast(0)) { emitVirtualChar(' ', anchor, out) }
                    }

                    if (tl.isSeparator) {
                        emitBorder('├', tl.start)
                        for (col in 0 until colCount) {
                            val w = colWidths[col] + 2
                            repeat(w.coerceAtLeast(0)) { emitVirtualChar('─', tl.start, out) }
                            if (col != colCount - 1) emitBorder('┼', tl.start) else emitBorder('┤', tl.start)
                        }
                    } else {
                        // Render with borders: │ cell... │
                        // Map pipe chars (if any) to nearest border.
                        emitBorder('│', tl.start)
                        emitSpaces(1, tl.start)
                        for (col in 0 until colCount) {
                            val (cellStart, cellEnd) = cells.getOrNull(col) ?: (tl.end to tl.end)
                            var contentStart = cellStart
                            var contentEnd = cellEnd
                            while (contentStart < contentEnd && original[contentStart].isWhitespace()) contentStart++
                            while (contentEnd > contentStart && original[contentEnd - 1].isWhitespace()) contentEnd--

                            // Skip original cell region up to content, then emit content (preserves mapping).
                            skipRange(cellStart, contentStart)
                            val actualLen = (contentEnd - contentStart).coerceAtLeast(0)
                            val cap = colWidths[col].coerceAtLeast(0)
                            val showEllipsis = actualLen > cap && cap >= 2
                            val takeLen =
                                when {
                                    cap <= 0 -> 0
                                    showEllipsis -> cap - 1
                                    else -> minOf(actualLen, cap)
                                }
                            val takeEnd = (contentStart + takeLen).coerceAtMost(contentEnd)
                            if (contentStart < takeEnd) {
                                emitRange(contentStart, takeEnd, out)
                            }
                            if (showEllipsis) {
                                val ellipsisStart = transIndex
                                emitVirtualChar('…', contentEnd, out)
                                styles.add(Triple(SpanStyle(color = Color.Gray), ellipsisStart, transIndex))
                            }
                            skipRange(takeEnd, contentEnd)
                            skipRange(contentEnd, cellEnd)

                            val displayedLen =
                                when {
                                    cap <= 0 -> 0
                                    showEllipsis -> cap
                                    else -> (takeEnd - contentStart).coerceAtMost(cap)
                                }
                            val pad = (cap - displayedLen).coerceAtLeast(0)
                            emitSpaces(pad, contentEnd)
                            emitSpaces(1, contentEnd)
                            emitBorder('│', cellEnd.coerceAtMost(tl.end))
                            if (col != colCount - 1) emitSpaces(1, cellEnd.coerceAtMost(tl.end))
                        }
                    }

                    // Fix the row style end.
                    styles[styles.lastIndex] = styles.last().copy(third = transIndex)
                }

                // Top/bottom borders around the block for better visual stability.
                val borderStyle = SpanStyle(color = linkColor, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                fun emitBorderSpan(ch: Char, anchor: Int) {
                    val s = transIndex
                    emitVirtualChar(ch, anchor, out)
                    borderStyles.add((s until transIndex) to borderStyle)
                }
                fun emitBorderLine(left: Char, mid: Char, right: Char, anchor: Int) {
                    emitBorderSpan(left, anchor)
                    for (col in 0 until colCount) {
                        val w = (colWidths[col] + 2).coerceAtLeast(0)
                        repeat(w) { emitVirtualChar('─', anchor, out) }
                        if (col != colCount - 1) emitBorderSpan(mid, anchor) else emitBorderSpan(right, anchor)
                    }
                    emitVirtualChar('\n', anchor, out)
                }

                if (lines.isNotEmpty() && colCount > 0) {
                    emitBorderLine('┌', '┬', '┐', lines.first().start)
                }

                for (li in lines.indices) {
                    val tl = lines[li]
                    val active = selectionOverlaps(tl.start, tl.end) && cursorOnTableSyntax(tl.start, tl.end, tl.pipes)
                    if (active) {
                        emitRange(tl.start, tl.end, out)
                    } else {
                        renderAlignedRow(tl, cellBoundsByLine[li])
                    }
                    if (tl.end < n && original[tl.end] == '\n') {
                        origToTrans[tl.end] = transIndex
                        emitChar('\n', tl.end, out)
                    }
                }

                if (lines.isNotEmpty() && colCount > 0) {
                    emitBorderLine('└', '┴', '┘', lines.last().start)
                }

                i = (lines.lastOrNull()?.end ?: lineEnd)
                if (i < n && original.getOrNull(i) == '\n') i++
                continue
            }

            // Headings: "# Title" -> hide leading "# " unless cursor is on the marker; render content as bold.
            if (atLineStart && c == '#') {
                var level = 0
                var j = i
                while (j < lineEnd && original[j] == '#' && level < 6) {
                    level++
                    j++
                }
                if (level > 0 && j < lineEnd && original[j] == ' ') {
                    val markerStart = i
                    val markerEnd = j + 1 // include trailing space
                    val markerActive = selectionOverlaps(markerStart, markerEnd)

                    if (markerActive) {
                        emitRange(markerStart, markerEnd, out)
                    } else {
                        skipRange(markerStart, markerEnd)
                    }
                    val (s, e) = emitRange(markerEnd, lineEnd, out)
                    val mult =
                        when (level) {
                            1 -> 1.55f
                            2 -> 1.35f
                            3 -> 1.20f
                            4 -> 1.10f
                            5 -> 1.02f
                            else -> 0.96f
                        }
                    styles.add(
                        Triple(
                            SpanStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = (baseFontSizeSp * mult).sp,
                            ),
                            s,
                            e,
                        ),
                    )
                    i = lineEnd
                    continue
                }
            }

            // Blockquotes: "> quote" -> hide leading "> " unless cursor is on the marker; add left bar + gray bg.
            if (atLineStart && c == '>' && i + 1 < lineEnd && original[i + 1] == ' ') {
                val markerStart = i
                val markerEnd = (i + 2).coerceAtMost(lineEnd)
                val markerActive = selectionOverlaps(markerStart, markerEnd)
                if (markerActive) {
                    emitRange(markerStart, markerEnd, out)
                } else {
                    skipRange(markerStart, markerEnd)
                    val barStart = transIndex
                    emitVirtualChar('│', markerStart, out)
                    emitVirtualChar(' ', markerStart, out)
                    pendingLinePrefixStyle = barStart to lineEnd
                    pendingLineBg = transIndex to lineEnd
                }
                i = markerEnd
                continue
            }

            // Unordered list: "- item" / "* item" / "+ item" (and task list markers).
            if (atLineStart) {
                val line = original.substring(i, lineEnd)
                val task =
                    Regex("""^(\s*)-\s+\[( |x|X)\]\s+""").find(line)
                if (task != null) {
                    val indent = task.groupValues[1]
                    val markerStart = i + indent.length
                    val markerEnd = i + task.value.length
                    val markerActive = selectionOverlaps(markerStart, markerEnd)
                    if (markerActive) {
                        emitRange(i, markerEnd, out)
                    } else {
                        emitRange(i, markerStart, out) // indent
                        skipRange(markerStart, markerEnd)
                        val boxStart = transIndex
                        val checked = task.groupValues[2].equals("x", ignoreCase = true)
                        emitVirtualChar(if (checked) '■' else '□', markerStart, out)
                        emitVirtualChar(' ', markerStart, out)
                        styles.add(Triple(SpanStyle(color = linkColor), boxStart, transIndex))
                    }
                    i = markerEnd
                    continue
                }

                val unordered = Regex("""^(\s*)([-*+])\s+""").find(line)
                if (unordered != null) {
                    val indent = unordered.groupValues[1]
                    val markerStart = i + indent.length
                    val markerEnd = i + unordered.value.length
                    val markerActive = selectionOverlaps(markerStart, markerEnd)
                    if (markerActive) {
                        emitRange(i, markerEnd, out)
                    } else {
                        emitRange(i, markerStart, out) // indent
                        skipRange(markerStart, markerEnd)
                        val bulletStart = transIndex
                        emitVirtualChar('•', markerStart, out)
                        emitVirtualChar(' ', markerStart, out)
                        styles.add(Triple(SpanStyle(color = linkColor), bulletStart, transIndex))
                    }
                    i = markerEnd
                    continue
                }

                val ordered = Regex("""^(\s*)(\d+)\.\s+""").find(line)
                if (ordered != null) {
                    val indent = ordered.groupValues[1]
                    val markerStart = i + indent.length
                    val markerEnd = i + ordered.value.length
                    val markerActive = selectionOverlaps(markerStart, markerEnd)
                    if (markerActive) {
                        emitRange(i, markerEnd, out)
                    } else {
                        emitRange(i, markerStart, out) // indent
                        skipRange(markerStart, markerEnd)
                        val numStart = transIndex
                        val num = ordered.groupValues[2]
                        for (ch in (num + ".")) emitVirtualChar(ch, markerStart, out)
                        emitVirtualChar(' ', markerStart, out)
                        styles.add(Triple(SpanStyle(color = linkColor), numStart, transIndex))
                    }
                    i = markerEnd
                    continue
                }
            }

            // Images: ![alt](url) -> display "alt" (hide marker+url) unless cursor is inside token.
            if (c == '!' && i + 1 < n && original[i + 1] == '[') {
                val closeBracket = original.indexOf(']', startIndex = i + 2).takeIf { it in (i + 2) until lineEnd } ?: -1
                if (closeBracket > 0 && closeBracket + 1 < n && original[closeBracket + 1] == '(') {
                    val closeParen =
                        original.indexOf(')', startIndex = closeBracket + 2).takeIf { it in (closeBracket + 2) until lineEnd } ?: -1
                    if (closeParen > 0) {
                        val tokenStart = i
                        val tokenEnd = closeParen + 1
                        val active = selectionOverlaps(tokenStart, tokenEnd)
                        if (active) {
                            emitRange(tokenStart, tokenEnd, out)
                        } else {
                            val alt = original.substring(i + 2, closeBracket).trim().ifBlank { "image" }
                            skipRange(tokenStart, tokenEnd)
                            val chipStart = transIndex
                            val label = "[img: $alt]"
                            for (ch in label) emitVirtualChar(ch, tokenStart, out)
                            styles.add(
                                Triple(
                                    SpanStyle(
                                        color = linkColor,
                                        background = codeBackground,
                                    ),
                                    chipStart,
                                    transIndex,
                                ),
                            )
                        }
                        i = tokenEnd
                        continue
                    }
                }
            }

            // Link: [text](url) -> display "text" (hide brackets + url) unless cursor is inside the token.
            if (c == '[') {
                val closeBracket = original.indexOf(']', startIndex = i + 1).takeIf { it in (i + 1) until lineEnd } ?: -1
                if (closeBracket > 0 && closeBracket + 1 < n && original[closeBracket + 1] == '(') {
                    val closeParen =
                        original.indexOf(')', startIndex = closeBracket + 2).takeIf { it in (closeBracket + 2) until lineEnd } ?: -1
                    if (closeParen > 0) {
                        val tokenStart = i
                        val tokenEnd = closeParen + 1
                        val active = selectionOverlaps(tokenStart, tokenEnd)

                        if (active) {
                            emitRange(tokenStart, tokenEnd, out)
                        } else {
                            skipRange(tokenStart, i + 1) // '['
                            val (s, e) = emitRange(i + 1, closeBracket, out)
                            styles.add(Triple(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline), s, e))
                            skipRange(closeBracket, tokenEnd) // ']' + '(url)'
                        }
                        i = tokenEnd
                        continue
                    }
                }
            }

            // Inline code: `code`
            if (c == '`') {
                val close = findClosing("`", i + 1, lineEnd)
                if (close > i + 1) {
                    val tokenStart = i
                    val tokenEnd = close + 1
                    val active = selectionOverlaps(tokenStart, tokenEnd)
                    if (active) {
                        val (s, e) = emitRange(tokenStart, tokenEnd, out)
                        // Apply code style to content only (exclude backticks).
                        val contentStart = origToTrans[i + 1]
                        val contentEnd = origToTrans[close]
                        styles.add(
                            Triple(
                                SpanStyle(
                                    fontFamily = FontFamily.Monospace,
                                    background = codeBackground,
                                ),
                                contentStart,
                                contentEnd,
                            ),
                        )
                    } else {
                        skipRange(i, i + 1) // '`'
                        val (s, e) = emitRange(i + 1, close, out)
                        styles.add(
                            Triple(
                                SpanStyle(
                                    fontFamily = FontFamily.Monospace,
                                    background = codeBackground,
                                ),
                                s,
                                e,
                            ),
                        )
                        skipRange(close, tokenEnd) // '`'
                    }
                    i = tokenEnd
                    continue
                }
            }

            // Strong: **text**
            if (c == '*' && i + 1 < n && original[i + 1] == '*') {
                val close = findClosing("**", i + 2, lineEnd)
                if (close > i + 2) {
                    val tokenStart = i
                    val tokenEnd = close + 2
                    val active = selectionOverlaps(tokenStart, tokenEnd)
                    if (active) {
                        emitRange(tokenStart, tokenEnd, out)
                        val contentStart = origToTrans[i + 2]
                        val contentEnd = origToTrans[close]
                        styles.add(Triple(SpanStyle(fontWeight = FontWeight.Bold), contentStart, contentEnd))
                    } else {
                        skipRange(i, i + 2) // '**'
                        val (s, e) = emitRange(i + 2, close, out)
                        styles.add(Triple(SpanStyle(fontWeight = FontWeight.Bold), s, e))
                        skipRange(close, tokenEnd) // '**'
                    }
                    i = tokenEnd
                    continue
                }
            }

            // Strike: ~~text~~
            if (c == '~' && i + 1 < n && original[i + 1] == '~') {
                val close = findClosing("~~", i + 2, lineEnd)
                if (close > i + 2) {
                    val tokenStart = i
                    val tokenEnd = close + 2
                    val active = selectionOverlaps(tokenStart, tokenEnd)
                    if (active) {
                        emitRange(tokenStart, tokenEnd, out)
                        val contentStart = origToTrans[i + 2]
                        val contentEnd = origToTrans[close]
                        styles.add(Triple(SpanStyle(textDecoration = TextDecoration.LineThrough), contentStart, contentEnd))
                    } else {
                        skipRange(i, i + 2) // '~~'
                        val (s, e) = emitRange(i + 2, close, out)
                        styles.add(Triple(SpanStyle(textDecoration = TextDecoration.LineThrough), s, e))
                        skipRange(close, tokenEnd) // '~~'
                    }
                    i = tokenEnd
                    continue
                }
            }

            // Emphasis: *text*
            if (c == '*') {
                val close = findClosing("*", i + 1, lineEnd)
                if (close > i + 1) {
                    val tokenStart = i
                    val tokenEnd = close + 1
                    val active = selectionOverlaps(tokenStart, tokenEnd)
                    if (active) {
                        emitRange(tokenStart, tokenEnd, out)
                        val contentStart = origToTrans[i + 1]
                        val contentEnd = origToTrans[close]
                        styles.add(Triple(SpanStyle(fontStyle = FontStyle.Italic), contentStart, contentEnd))
                    } else {
                        skipRange(i, i + 1) // '*'
                        val (s, e) = emitRange(i + 1, close, out)
                        styles.add(Triple(SpanStyle(fontStyle = FontStyle.Italic), s, e))
                        skipRange(close, tokenEnd) // '*'
                    }
                    i = tokenEnd
                    continue
                }
            }

            // Default: pass-through.
            emitChar(c, i, out)
            i++
        }

        flushPendingLineStyles(n)
        origToTrans[n] = transIndex
        transToOrig.add(n)

        val transformed =
            buildAnnotatedString {
                append(out.toString())
                for ((style, start, end) in styles) {
                    if (start in 0..length && end in 0..length && start < end) {
                        addStyle(style, start, end)
                    }
                }
                for ((range, style) in borderStyles) {
                    val start = range.first.coerceIn(0, length)
                    val end = (range.last + 1).coerceAtMost(length)
                    if (start < end) addStyle(style, start, end)
                }
            }

        val transLen = transformed.length
        val transToOrigArray = IntArray(transLen + 1)
        for (t in 0 until transLen) {
            transToOrigArray[t] = transToOrig.getOrElse(t) { n }
        }
        transToOrigArray[transLen] = n

        val mapping =
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int =
                    origToTrans[offset.coerceIn(0, n)].coerceIn(0, transLen)

                override fun transformedToOriginal(offset: Int): Int =
                    transToOrigArray[offset.coerceIn(0, transLen)].coerceIn(0, n)
            }

        return TransformedText(transformed, mapping)
    }

    companion object {
        fun from(
            value: TextFieldValue,
            baseFontSizeSp: Float,
            linkColor: Color,
            codeBackground: Color,
        ): MarkdownLiveVisualTransformation =
            MarkdownLiveVisualTransformation(
                selectionStart = value.selection.start,
                selectionEnd = value.selection.end,
                baseFontSizeSp = baseFontSizeSp,
                linkColor = linkColor,
                codeBackground = codeBackground,
            )
    }
}
