package app.zhixu.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.zhixu.R
import app.zhixu.ui.Ionicons
import app.zhixu.ui.ZhixuTopBarIconSize
import app.zhixu.ui.components.ZhixuIconButton
import app.zhixu.ui.components.ZhixuTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceholderScreen(
    title: String,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                ZhixuTopAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { Text(title, style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        ZhixuIconButton(onClick = onBack) {
                            androidx.compose.material3.Icon(
                                painter = painterResource(Ionicons.ArrowBack),
                                contentDescription = stringResource(R.string.action_back),
                                modifier = Modifier.size(ZhixuTopBarIconSize),
                            )
                        }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .padding(contentPadding)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.settings_placeholder_coming_soon),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

