package vpn.moonlight.ui.logs

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import vpn.moonlight.R
import vpn.moonlight.data.logging.LogEntry
import vpn.moonlight.data.logging.LogLevel
import vpn.moonlight.design.MlIcons
import vpn.moonlight.design.MlShape
import vpn.moonlight.design.components.MlCard
import vpn.moonlight.design.components.MlIconButton
import vpn.moonlight.design.components.MlPillButton
import vpn.moonlight.design.components.MlSegmented
import vpn.moonlight.design.components.MlSwitch
import vpn.moonlight.design.components.MlText
import vpn.moonlight.design.ml

@Composable
fun LogsScreen(
    viewModel: LogsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Resolved here rather than inside the click lambda: reading resources through
    // LocalContext is not observable, so it would not follow a language change.
    val shareLabel = stringResource(R.string.logs_share)

    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MlIconButton(MlIcons.ChevronLeft, onBack)
            MlText(stringResource(R.string.logs_title), ml.type.titleSm, Modifier.weight(1f))
            MlIconButton(
                icon = MlIcons.X,
                onClick = viewModel::clear,
                size = 38.dp,
                iconSize = 18.dp,
            )
        }

        Column(
            Modifier.padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MlText(
                stringResource(R.string.logs_hint),
                ml.type.meta,
                color = ml.colors.textMuted,
            )

            MlCard {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        MlText(stringResource(R.string.logs_verbose), ml.type.bodyTitle)
                        Spacer(Modifier.height(1.dp))
                        MlText(
                            stringResource(R.string.logs_verbose_sub),
                            ml.type.metaSm,
                            color = ml.colors.textMuted,
                        )
                    }
                    MlSwitch(checked = state.verbose, onCheckedChange = viewModel::setVerbose)
                }
            }

            MlPillButton(
                onClick = {
                    scope.launch {
                        val report = viewModel.buildReport(context)
                        val intent = LogExport.shareIntent(context, report)
                        runCatching {
                            // NEW_TASK because LocalContext is a configuration
                            // context (see WithAppLanguage), not the Activity.
                            context.startActivity(
                                Intent.createChooser(intent, shareLabel)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                },
                height = 46.dp,
                background = ml.colors.accent,
                contentColor = ml.colors.textOnAccent,
                horizontalPadding = 18.dp,
            ) {
                vpn.moonlight.design.MlIcon(
                    MlIcons.ExternalLink,
                    size = 17.dp,
                    tint = ml.colors.textOnAccent,
                )
                MlText(
                    stringResource(R.string.logs_share),
                    ml.type.bodySmEmphatic,
                    color = ml.colors.textOnAccent,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        MlSegmented(
            options = listOf(
                stringResource(R.string.logs_source_app),
                stringResource(R.string.logs_source_core),
            ),
            selectedIndex = if (state.source == LogSource.Core) 1 else 0,
            onSelect = { index ->
                viewModel.setSource(if (index == 1) LogSource.Core else LogSource.App)
            },
            modifier = Modifier.padding(horizontal = 18.dp),
        )

        Spacer(Modifier.height(12.dp))

        if (state.source == LogSource.Core) {
            // Loaded per selection: the file can be megabytes, so it is not held
            // in a flow alongside the app's buffer.
            val coreLines by produceState(initialValue = emptyList<String>(), state.source) {
                value = viewModel.coreLines(context)
            }
            if (coreLines.isEmpty()) {
                EmptyLogs(stringResource(R.string.logs_empty_core))
            } else {
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 110.dp),
                ) {
                    items(coreLines) { line -> CoreLogLine(line) }
                }
            }
        } else if (state.entries.isEmpty()) {
            EmptyLogs(stringResource(R.string.logs_empty))
        } else {
            // Lazy, and each line scrolls sideways rather than wrapping: a stack
            // trace is unreadable when wrapped at phone width.
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 110.dp),
            ) {
                items(state.entries) { entry -> LogLine(entry) }
            }
        }
    }
}

@Composable
private fun EmptyLogs(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        MlText(text, ml.type.bodySm, color = ml.colors.textMuted, textAlign = TextAlign.Center)
    }
}

/** One line straight from the core's file, coloured by its own level marker. */
@Composable
private fun CoreLogLine(line: String) {
    val accent = when {
        line.contains("[Error]") -> ml.colors.danger
        line.contains("[Warning]") -> ml.colors.statusDegradedInk
        line.contains("[Debug]") -> ml.colors.textMuted
        else -> ml.colors.text2
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(16.dp)
                .clip(MlShape.Pill)
                .background(accent),
        )
        MlText(
            line,
            ml.type.monoSm,
            Modifier.horizontalScroll(rememberScrollState()),
            color = ml.colors.text2,
            maxLines = 1,
        )
    }
}

@Composable
private fun LogLine(entry: LogEntry) {
    val accent = when (entry.level) {
        LogLevel.Error -> ml.colors.danger
        LogLevel.Warn -> ml.colors.statusDegradedInk
        LogLevel.Info -> ml.colors.text2
        LogLevel.Debug -> ml.colors.textMuted
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(16.dp)
                .clip(MlShape.Pill)
                .background(accent),
        )
        MlText(
            entry.format(),
            ml.type.monoSm,
            Modifier.horizontalScroll(rememberScrollState()),
            color = if (entry.level == LogLevel.Error) ml.colors.danger else ml.colors.text2,
            maxLines = 1,
        )
    }
}
