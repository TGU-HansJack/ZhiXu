package com.zhixu.android.ui.components

import android.net.Uri
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin

@Composable
fun MarkdownPreview(
    markdown: String,
    onOpenWikiLink: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val markwon =
        remember(context, onOpenWikiLink) {
            Markwon.builder(context)
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(TablePlugin.create(context))
                .usePlugin(TaskListPlugin.create(context))
                .usePlugin(JLatexMathPlugin.create(16f))
                .usePlugin(
                    object : AbstractMarkwonPlugin() {
                        override fun configureTheme(builder: MarkwonTheme.Builder) {
                            builder.linkColor(0xFF1565C0.toInt())
                        }

                        override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                            builder.linkResolver { _, link ->
                                if (link.startsWith("zhixu://doc/")) {
                                    val name = Uri.parse(link).lastPathSegment ?: return@linkResolver
                                    onOpenWikiLink?.invoke(name)
                                    return@linkResolver
                                }
                            }
                        }
                    },
                )
                .build()
        }

    AndroidView(
        factory = {
            TextView(it).apply {
                textSize = 16f
                setTextIsSelectable(true)
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { view ->
            val preprocessed = preprocessWikiLinks(markdown)
            runCatching {
                markwon.setMarkdown(view, preprocessed)
            }.onFailure {
                // Never crash the app on malformed markdown/latex/etc; fallback to plain text.
                view.text = markdown
            }
        },
    )
}

private fun preprocessWikiLinks(markdown: String): String {
    // Convert [[Name]] -> [Name](zhixu://doc/Name)
    val regex = Regex("""\[\[([^\]]+)\]\]""")
    return markdown.replace(regex) { m ->
        val name = m.groupValues[1].trim()
        if (name.isBlank()) m.value else "[$name](zhixu://doc/${Uri.encode(name)})"
    }
}
