package app.zhixu.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.zhixu.R
import app.zhixu.data.DocSearchResult
import app.zhixu.data.SearchResult
import app.zhixu.data.TaskSearchResult
import app.zhixu.ui.ZhixuTopBarContentHeight
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultSearchDialog(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<SearchResult>,
    highlightBg: Color,
    onDismiss: () -> Unit,
    onOpenResult: (String, Int?) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }

    DisposableEffect(Unit) {
        onDispose { keyboardController?.hide() }
    }

    LaunchedEffect(Unit) {
        delay(80)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter =
                fadeIn(animationSpec = tween(durationMillis = 120)) +
                    slideInVertically(animationSpec = tween(durationMillis = 120)) { fullHeight -> fullHeight / 8 },
            exit =
                fadeOut(animationSpec = tween(durationMillis = 90)) +
                    slideOutVertically(animationSpec = tween(durationMillis = 90)) { fullHeight -> fullHeight / 8 },
        ) {
            Surface(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .windowInsetsPadding(WindowInsets.navigationBars),
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    val topBarHeight = ZhixuTopBarContentHeight
                    val fieldHeight = ZhixuTopBarContentHeight - 16.dp // 8dp top/bottom padding
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(topBarHeight)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val fieldShape = RoundedCornerShape(4.dp)
                        val containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f)

                        Surface(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(fieldHeight)
                                    .shadow(elevation = 2.dp, shape = fieldShape, clip = false),
                            color = containerColor,
                            shape = fieldShape,
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(start = 4.dp, end = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ZhixuIconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.size(fieldHeight),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_lucide_arrow_left),
                                        contentDescription = stringResource(R.string.action_cancel),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                BasicTextField(
                                    value = query,
                                    onValueChange = onQueryChange,
                                    singleLine = true,
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    textStyle =
                                        MaterialTheme.typography.bodyMedium.merge(
                                            androidx.compose.ui.text.TextStyle(
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Normal,
                                            ),
                                        ),
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .focusRequester(focusRequester),
                                    decorationBox = { innerTextField ->
                                        androidx.compose.foundation.layout.Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.CenterStart,
                                        ) {
                                            if (query.isBlank()) {
                                                Text(
                                                    text = stringResource(R.string.action_search),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 1,
                                                )
                                            }
                                            innerTextField()
                                        }
                                    },
                                )
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        if (query.isBlank()) {
                            item {
                                Text(
                                    text = "",
                                    modifier = Modifier.padding(16.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else if (results.isEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.search_empty),
                                    modifier = Modifier.padding(16.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            itemsIndexed(
                                results,
                                key = { index, result ->
                                    when (result) {
                                        is DocSearchResult -> "doc:${result.uri}#$index"
                                        is TaskSearchResult -> "task:${result.docUri}:${result.lineIndex}:${result.taskId}#$index"
                                    }
                                },
                            ) { _, result ->
                                when (result) {
                                    is DocSearchResult -> {
                                        ListItem(
                                            modifier = Modifier.clickable { onOpenResult(result.uri.toString(), null) },
                                            headlineContent = {
                                                Text(
                                                    text = highlightQuery(result.title, query, highlightBg),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            },
                                            supportingContent = {
                                                if (!result.snippet.isNullOrBlank()) {
                                                    Text(
                                                        highlightQuery(result.snippet, query, highlightBg),
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            },
                                        )
                                    }

                                    is TaskSearchResult -> {
                                        ListItem(
                                            modifier = Modifier.clickable { onOpenResult(result.docUri.toString(), result.lineIndex) },
                                            headlineContent = {
                                                Text(
                                                    text = highlightQuery(result.title, query, highlightBg),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            },
                                            supportingContent = { Text(stringResource(R.string.search_task_hit)) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun highlightQuery(text: String, query: String, highlightBg: Color): AnnotatedString {
    val q = query.trim()
    if (q.isBlank()) return AnnotatedString(text)
    val tokens = q.split(Regex("""\\s+""")).filter { it.isNotBlank() }.distinct()
    if (tokens.isEmpty()) return AnnotatedString(text)

    val lowered = text.lowercase()
    val ranges = ArrayList<IntRange>()
    for (token in tokens) {
        val t = token.lowercase()
        if (t.length < 2) continue
        var idx = lowered.indexOf(t)
        while (idx >= 0) {
            ranges += (idx until (idx + t.length))
            idx = lowered.indexOf(t, startIndex = idx + t.length)
        }
    }
    if (ranges.isEmpty()) return AnnotatedString(text)

    val merged =
        ranges
            .sortedBy { it.first }
            .fold(mutableListOf<IntRange>()) { acc, r ->
                val last = acc.lastOrNull()
                if (last == null) acc.add(r)
                else if (r.first <= last.last + 1) acc[acc.lastIndex] = (last.first..maxOf(last.last, r.last))
                else acc.add(r)
                acc
            }

    val highlight = SpanStyle(background = highlightBg, fontWeight = FontWeight.SemiBold)
    return buildAnnotatedString {
        var pos = 0
        for (r in merged) {
            if (pos < r.first) append(text.substring(pos, r.first))
            val end = (r.last + 1).coerceAtMost(text.length)
            pushStyle(highlight)
            append(text.substring(r.first, end))
            pop()
            pos = end
        }
        if (pos < text.length) append(text.substring(pos))
    }
}
