package com.zhixu.android.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhixu.android.R
import com.zhixu.android.data.AccountPreferences
import com.zhixu.android.data.AccountState
import com.zhixu.android.data.DailyContrib
import com.zhixu.android.data.VaultRepository
import com.zhixu.android.ui.Ionicons
import com.zhixu.android.ui.components.ContribCalendarDialog
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    vaultRootUri: Uri?,
    refreshToken: Long,
    repository: VaultRepository,
    onOpenDoc: (String, String?, Int?) -> Unit,
    onOpenVaultSettings: () -> Unit,
    onOpenWorkshop: () -> Unit,
    onOpenSync: () -> Unit,
) {
    val context = LocalContext.current
    val accountPrefs = remember(context) { AccountPreferences(context.applicationContext) }
    val accountState by accountPrefs.state.collectAsState(
        initial = AccountState(token = "", username = "", userId = 0L, deviceId = ""),
    )
    var showAccountDialog by remember { mutableStateOf(false) }

    if (showAccountDialog) {
        AccountManagementDialog(
            accountPrefs = accountPrefs,
            onDismiss = { showAccountDialog = false },
        )
    }

    var contribPerDay by remember { mutableStateOf<Map<LocalDate, DailyContrib>?>(null) }

    LaunchedEffect(refreshToken) {
        val year = LocalDate.now().year
        contribPerDay =
            runCatching { repository.getDailyContribForYear(year = year) }
                .getOrNull()
                ?: emptyMap()
    }

    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

    LazyColumn(
        modifier =
            Modifier
                .padding(contentPadding)
                .background(MaterialTheme.colorScheme.background)
                .fillMaxSize()
                .imePadding(),
        contentPadding = PaddingValues(0.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { showAccountDialog = true }
                        .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = "Z", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        val name =
                            if (accountState.isLoggedIn) accountState.username.ifBlank { "Zhixu" }
                            else stringResource(R.string.account_not_logged_in_short)
                        Text(text = name, style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = if (accountState.isLoggedIn) "ID: ${accountState.userId}" else "ID: -",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                }
            }
            HorizontalDivider(color = dividerColor)
        }

        item {
            ContribHeatmapCard(
                contribPerDay = contribPerDay ?: emptyMap(),
                repository = repository,
                onOpenDoc = onOpenDoc,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            HorizontalDivider(color = dividerColor)
        }

        item {
            SettingsNavRow(
                iconRes = Ionicons.Vault,
                title = stringResource(R.string.settings_section_vault),
                onClick = onOpenVaultSettings,
            )
            HorizontalDivider(color = dividerColor)
            SettingsNavRow(
                iconRes = Ionicons.Workshop,
                title = stringResource(R.string.settings_section_workshop),
                enabled = vaultRootUri != null,
                onClick = onOpenWorkshop,
            )
            HorizontalDivider(color = dividerColor)
        }

        item {
            SettingsNavRow(
                iconRes = Ionicons.Sync,
                title = stringResource(R.string.settings_section_sync),
                enabled = vaultRootUri != null,
                onClick = onOpenSync,
            )
            HorizontalDivider(color = dividerColor)
        }
    }
}

@Composable
private fun SettingsNavRow(
    iconRes: Int,
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    ListItem(
        modifier =
            Modifier
                .fillMaxWidth()
                .let { m -> if (enabled) m.clickable(onClick = onClick) else m },
        leadingContent = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
        headlineContent = { Text(title) },
        supportingContent = {
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        trailingContent = {
            Icon(
                painter = painterResource(Ionicons.ChevronForward),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
    )
}

@Composable
private fun ContribHeatmapCard(
    contribPerDay: Map<LocalDate, DailyContrib>,
    repository: VaultRepository,
    onOpenDoc: (String, String?, Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = 7
    val today = LocalDate.now()
    val year = today.year
    val yearStart = LocalDate.of(year, 1, 1)
    val yearEnd = LocalDate.of(year, 12, 31)

    val totalsPerDay = remember(contribPerDay) { contribPerDay.mapValues { (_, v) -> v.total } }
    val max =
        totalsPerDay
            .asSequence()
            .filter { (d, _) -> !d.isBefore(yearStart) && !d.isAfter(yearEnd) }
            .map { it.value }
            .maxOrNull()
            ?: 0

    var showCalendar by remember { mutableStateOf(false) }
    if (showCalendar) {
        ContribCalendarDialog(
            repository = repository,
            onOpenDoc = onOpenDoc,
            onDismiss = { showCalendar = false },
        )
    }

    val cellSize = 10.dp
    val gap = 2.dp
    val weekPitch = cellSize + gap
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    @Composable
    fun HeatmapHalf(
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
        monthRange: IntRange,
    ) {
        val startSunday = rangeStart.minusDays((rangeStart.dayOfWeek.value % 7).toLong())
        val endSaturday = rangeEnd.plusDays((6 - (rangeEnd.dayOfWeek.value % 7)).toLong())
        val cols = ((ChronoUnit.DAYS.between(startSunday, endSaturday) + 1L) / 7L).toInt().coerceAtLeast(1)
        val levels =
            remember(totalsPerDay, rangeStart, rangeEnd, cols, max) {
                buildGitHubYearHeatmapLevels(
                    countsPerDay = totalsPerDay,
                    yearStart = rangeStart,
                    yearEnd = rangeEnd,
                    startSunday = startSunday,
                    rows = rows,
                    cols = cols,
                    max = max,
                )
            }

        val monthStarts =
            remember(year, cols, startSunday, monthRange) {
                monthRange.mapNotNull { month ->
                    val date = LocalDate.of(year, month, 1)
                    val col = (ChronoUnit.DAYS.between(startSunday, date) / 7L).toInt()
                    if (col !in 0 until cols) return@mapNotNull null
                    col to "${month}月"
                }
            }

        val gridWidth = (cellSize * cols) + (gap * (cols - 1))

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.width(gridWidth).height(18.dp)) {
                for ((col, label) in monthStarts) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.75f),
                        modifier = Modifier.offset(x = weekPitch * col, y = 1.dp),
                        maxLines = 1,
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier.width(gridWidth),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                for (col in 0 until cols) {
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        for (row in 0 until rows) {
                            val idx = col * rows + row
                            val level = levels.getOrNull(idx)?.coerceIn(0, 4) ?: 0
                            val date = startSunday.plusDays((col * rows + row).toLong())
                            if (date.isBefore(rangeStart) || date.isAfter(rangeEnd)) {
                                Spacer(Modifier.size(cellSize))
                            } else {
                                ContributionCell(level = level, size = cellSize)
                            }
                        }
                    }
                }
            }
        }
    }

    val (halfStart, halfEnd, monthRange) =
        if (today.monthValue <= 6) {
            Triple(yearStart, LocalDate.of(year, 6, 30), 1..6)
        } else {
            Triple(LocalDate.of(year, 7, 1), yearEnd, 7..12)
        }

    Surface(
        modifier = modifier.clickable { showCalendar = true },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            HeatmapHalf(rangeStart = halfStart, rangeEnd = halfEnd, monthRange = monthRange)

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("更少", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.75f))
                    Spacer(Modifier.width(6.dp))
                    for (level in 0..4) {
                        ContributionCell(level = level, size = 10.dp)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text("更多", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.75f))
                }
            }
        }
    }
}

@Composable
private fun ContribHeatmapCardLegacy(
    contribPerDay: Map<LocalDate, DailyContrib>,
    modifier: Modifier = Modifier,
) {
    val rows = 7
    val today = LocalDate.now()
    val year = today.year
    val yearStart = LocalDate.of(year, 1, 1)
    val yearEnd = LocalDate.of(year, 12, 31)

    val startSunday = yearStart.minusDays((yearStart.dayOfWeek.value % 7).toLong())
    val endSaturday = yearEnd.plusDays((6 - (yearEnd.dayOfWeek.value % 7)).toLong())
    val cols = ((ChronoUnit.DAYS.between(startSunday, endSaturday) + 1L) / 7L).toInt().coerceAtLeast(1)

    val totalsPerDay = remember(contribPerDay) { contribPerDay.mapValues { (_, v) -> v.total } }

    val max =
        totalsPerDay
            .asSequence()
            .filter { (d, _) -> !d.isBefore(yearStart) && !d.isAfter(yearEnd) }
            .map { it.value }
            .maxOrNull()
            ?: 0

    val levels =
        remember(totalsPerDay, year) {
            buildGitHubYearHeatmapLevels(
                countsPerDay = totalsPerDay,
                yearStart = yearStart,
                yearEnd = yearEnd,
                startSunday = startSunday,
                rows = rows,
                cols = cols,
                max = max,
            )
        }

    val dayLabelWidth = 44.dp
    val cellSize = 11.dp
    val gap = 4.dp
    val weekPitch = cellSize + gap
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dayLabelStyle =
        MaterialTheme.typography.labelSmall.copy(
            fontSize = 9.sp,
            lineHeight = 9.sp,
        )

    val monthStarts =
        remember(year, cols, startSunday) {
            (1..12).mapNotNull { month ->
                val date = LocalDate.of(year, month, 1)
                val col = (ChronoUnit.DAYS.between(startSunday, date) / 7L).toInt()
                if (col !in 0 until cols) return@mapNotNull null
                col to "${month}月"
            }
        }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            val scrollState = rememberScrollState()
            LaunchedEffect(levels.size) {
                scrollState.scrollTo(scrollState.maxValue)
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Spacer(Modifier.width(dayLabelWidth))
                Box(modifier = Modifier.horizontalScroll(scrollState)) {
                    Box(modifier = Modifier.width(weekPitch * cols).height(18.dp)) {
                        for ((col, label) in monthStarts) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = textColor.copy(alpha = 0.75f),
                                modifier = Modifier.offset(x = weekPitch * col, y = 1.dp),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.width(dayLabelWidth),
                    verticalArrangement = Arrangement.spacedBy(gap),
                ) {
                    for (row in 0 until rows) {
                        val label =
                            when (row) {
                                1 -> "周一"
                                3 -> "周三"
                                5 -> "周五"
                                else -> ""
                            }
                        Box(
                            modifier = Modifier.height(cellSize).fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (label.isNotBlank()) {
                                Text(
                                    text = label,
                                    style = dayLabelStyle,
                                    color = textColor.copy(alpha = 0.75f),
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.horizontalScroll(scrollState)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        for (col in 0 until cols) {
                            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                                for (row in 0 until rows) {
                                    val idx = col * rows + row
                                    val level = levels.getOrNull(idx)?.coerceIn(0, 4) ?: 0
                                    val date = startSunday.plusDays((col * rows + row).toLong())
                                    if (date.isBefore(yearStart) || date.isAfter(yearEnd)) {
                                        Spacer(Modifier.size(cellSize))
                                    } else {
                                        ContributionCell(level = level, size = cellSize)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("更少的", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.75f))
                    Spacer(Modifier.width(6.dp))
                    for (level in 0..4) {
                        ContributionCell(level = level, size = 10.dp)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text("更多的", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.75f))
                }
            }
        }
    }
}

@Composable
private fun ContributionCell(
    level: Int,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(3.dp)
    val colors =
        listOf(
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.42f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.90f),
        )
    val safeLevel = level.coerceIn(0, 4)
    Box(
        modifier =
            modifier
                .size(size)
                .background(color = colors[safeLevel], shape = shape),
    )
}

private fun buildGitHubYearHeatmapLevels(
    countsPerDay: Map<LocalDate, Int>,
    yearStart: LocalDate,
    yearEnd: LocalDate,
    startSunday: LocalDate,
    rows: Int,
    cols: Int,
    max: Int,
): IntArray {
    val out = IntArray(rows * cols) { 0 }
    if (rows <= 0 || cols <= 0) return out

    fun levelFor(v: Int): Int {
        if (v <= 0) return 0
        if (max <= 1) return 1
        return (1 + ((v - 1) * 3 / (max - 1))).coerceIn(1, 4)
    }

    for (col in 0 until cols) {
        val weekStart = startSunday.plusDays((col * rows).toLong())
        for (row in 0 until rows) {
            val date = weekStart.plusDays(row.toLong())
            if (date.isBefore(yearStart) || date.isAfter(yearEnd)) continue
            val v = countsPerDay[date] ?: 0
            out[col * rows + row] = levelFor(v)
        }
    }

    return out
}
