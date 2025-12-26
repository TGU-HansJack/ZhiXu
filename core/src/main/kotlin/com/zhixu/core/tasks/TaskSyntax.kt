package com.zhixu.core.tasks

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class TaskItem(
    val lineIndex: Int,
    val checked: Boolean,
    val title: String,
    val id: String?,
    val due: String?,
    val done: String?,
    val tags: List<String>,
    val rawLine: String,
)

data class NormalizeResult(
    val markdown: String,
    val changed: Boolean,
    val insertedIds: Int,
)

object TaskSyntax {
    private val taskLineRegex = Regex("""^(\s*-\s*\[)([ xX])(\]\s+)(.*)$""")
    private val fieldRegex = Regex("""@([a-zA-Z][a-zA-Z0-9_-]*)\(([^)]*)\)""")
    private val idRegex = Regex("""@id\(([^)]*)\)""")
    private val doneRegex = Regex("""@done\(([^)]*)\)""")
    private val dueRegex = Regex("""@due\(([^)]*)\)""")
    private val tagRegex = Regex("""@tag\(([^)]*)\)""")
    private val nowFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun parseTasks(markdown: String): List<TaskItem> {
        val lines = markdown.split('\n')
        val tasks = ArrayList<TaskItem>()
        for ((index, line) in lines.withIndex()) {
            val match = taskLineRegex.matchEntire(line) ?: continue
            val checked = match.groupValues[2].trim().equals("x", ignoreCase = true)
            val rest = match.groupValues[4]
            val id = idRegex.find(rest)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            val due = dueRegex.find(rest)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            val done = doneRegex.find(rest)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            val tags = tagRegex.findAll(rest).mapNotNull { it.groupValues[1].takeIf(String::isNotBlank) }.toList()
            val title = rest.replace(fieldRegex, "").replace(Regex("""\s{2,}"""), " ").trim()
            tasks += TaskItem(
                lineIndex = index,
                checked = checked,
                title = title,
                id = id,
                due = due,
                done = done,
                tags = tags,
                rawLine = line,
            )
        }
        return tasks
    }

    /**
     * MVP: insert missing `@id(...)` for task lines and write back.
     */
    fun normalizeMarkdown(markdown: String): NormalizeResult {
        val lines = markdown.split('\n').toMutableList()
        var inserted = 0

        for (i in lines.indices) {
            val line = lines[i]
            val match = taskLineRegex.matchEntire(line) ?: continue
            val rest = match.groupValues[4]
            if (idRegex.containsMatchIn(rest)) continue

            val newId = Ulid.next()
            lines[i] = line.trimEnd() + " @id($newId)"
            inserted++
        }

        val out = lines.joinToString("\n")
        return NormalizeResult(
            markdown = out,
            changed = inserted > 0 || out != markdown,
            insertedIds = inserted,
        )
    }

    fun toggleTaskAtLine(
        markdown: String,
        lineIndex: Int,
        now: LocalDateTime = LocalDateTime.now(),
    ): String {
        val lines = markdown.split('\n').toMutableList()
        if (lineIndex !in lines.indices) return markdown

        val line = lines[lineIndex]
        val match = taskLineRegex.matchEntire(line) ?: return markdown

        val prefix = match.groupValues[1]
        val currentMark = match.groupValues[2]
        val mid = match.groupValues[3]
        var rest = match.groupValues[4]

        val currentlyChecked = currentMark.trim().equals("x", ignoreCase = true)
        val nextChecked = !currentlyChecked
        val nextMark = if (nextChecked) "x" else " "

        if (!idRegex.containsMatchIn(rest)) {
            rest = rest.trimEnd() + " @id(${Ulid.next()})"
        }

        rest = if (nextChecked) {
            if (doneRegex.containsMatchIn(rest)) rest
            else rest.trimEnd() + " @done(${now.format(nowFormatter)})"
        } else {
            rest.replace(doneRegex, "").replace(Regex("""\s{2,}"""), " ").trimEnd()
        }

        lines[lineIndex] = prefix + nextMark + mid + rest
        return lines.joinToString("\n")
    }
}

