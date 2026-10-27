package vpn.moonlight.ui.logs

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import vpn.moonlight.R
import vpn.moonlight.data.logging.LogEntry
import vpn.moonlight.data.logging.LogLevel
import vpn.moonlight.design.MlIcons
import vpn.moonlight.design.MlShape
import vpn.moonlight.design.components.MlCard
import vpn.moonlight.design.components.MlIconButton
import vpn.moonlight.design.components.MlPrimaryButton
import vpn.moonlight.design.components.MlSecondaryButton
import vpn.moonlight.design.components.MlSegmented
import vpn.moonlight.design.components.MlSwitch
import vpn.moonlight.design.components.MlText
import vpn.moonlight.design.ml
import vpn.moonlight.ui.common.copyToClipboard
import vpn.moonlight.ui.common.toast

/**
 * The app's and the core's logs, set as a terminal pane: JetBrains Mono, the
 * time dimmed, the level as a coloured tag, the message wrapped so nothing hides
 * off the edge. Any of it can be selected and copied, and "Copy" puts the whole
 * report — the same redacted one "Share" sends — on the clipboard.
 */
@Composable
fun LogsScreen(
    viewModel: LogsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Resolved here: a composition local can only be read during composition.
    val shareLabel = stringResource(R.string.logs_share)
    val copied = stringResource(R.string.logs_copied)
    val title = stringResource(R.string.logs_title)

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
            MlIconButton(MlIcons.ChevronLeft, onBack, label = stringResource(R.string.action_back))
            MlText(title, ml.type.titleSm, Modifier.weight(1f))
            MlIconButton(icon = MlIcons.Trash2, onClick = viewModel::clear, size = 38.dp, iconSize = 18.dp)
        }

        Column(
            Modifier.padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MlText(stringResource(R.string.logs_hint), ml.type.meta, color = ml.colors.textMuted)

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
                        MlText(stringResource(R.string.logs_verbose_sub), ml.type.metaSm, color = ml.colors.textMuted)
                    }
                    MlSwitch(checked = state.verbose, onCheckedChange = viewModel::setVerbose)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MlPrimaryButton(
                    label = stringResource(R.string.logs_copy),
                    onClick = {
                        scope.launch {
                            context.copyToClipboard(title, viewModel.buildReport(context))
                            context.toast(copied)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    leadingIcon = MlIcons.Copy,
                )
                MlSecondaryButton(
                    label = shareLabel,
                    onClick = {
                        scope.launch {
                            val intent = LogExport.shareIntent(context, viewModel.buildReport(context))
                            runCatching { context.startActivity(Intent.createChooser(intent, shareLabel)) }
                        }
                    },
                    leadingIcon = MlIcons.ExternalLink,
                )
            }

            MlSegmented(
                options = listOf(stringResource(R.string.logs_source_app), stringResource(R.string.logs_source_core)),
                selectedIndex = if (state.source == LogSource.Core) 1 else 0,
                onSelect = { index -> viewModel.setSource(if (index == 1) LogSource.Core else LogSource.App) },
            )
        }

        Spacer(Modifier.height(12.dp))

        val lines: List<AnnotatedString> = if (state.source == LogSource.Core) {
            // Loaded per selection: the file can be megabytes, so it is not held
            // in a flow alongside the app's buffer.
            val core by produceState(initialValue = emptyList<String>(), state.source) {
                value = viewModel.coreLines(context)
            }
            core.map(::coreLine)
        } else {
            state.entries.map(::appLine)
        }

        MlCard(
            Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
                .navigationBarsPadding()
                .fillMaxSize(),
            shape = MlShape.Card,
            background = if (ml.colors.isLight) Color(0xFF16160F) else Color.Black.copy(alpha = 0.45f),
        ) {
            if (lines.isEmpty()) {
                MlText(
                    stringResource(if (state.source == LogSource.Core) R.string.logs_empty_core else R.string.logs_empty),
                    ml.type.bodySm,
                    Modifier.fillMaxWidth().padding(24.dp),
                    color = Color(0xFF8D8D7E),
                    textAlign = TextAlign.Center,
                )
            } else {
                SelectionContainer {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp)) {
                        items(lines) { line ->
                            androidx.compose.material3.Text(
                                line,
                                style = ml.type.monoSm.copy(fontSize = 12.sp, lineHeight = 17.sp),
                                color = TerminalText,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The pane is dark in both themes, as a terminal is; these colours are for it alone. */
private val TerminalText = Color(0xFFE8E8DF)
private val TerminalDim = Color(0xFF7C7C72)

private val ErrorInk = Color(0xFFFF6B61)
private val WarnInk = Color(0xFFF5B740)
private val InfoInk = Color(0xFF3DD68C)

private val timeFormat = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue() = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
}

private fun appLine(entry: LogEntry): AnnotatedString {
    val (tag, color) = when (entry.level) {
        LogLevel.Error -> "ERR" to ErrorInk
        LogLevel.Warn -> "WRN" to WarnInk
        LogLevel.Info -> "INF" to InfoInk
        LogLevel.Debug -> "DBG" to TerminalDim
    }
    return buildAnnotatedString {
        withStyle(SpanStyle(color = TerminalDim)) { append(timeFormat.get()!!.format(Date(entry.atEpochMillis))) }
        append(' ')
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) { append(tag) }
        append(' ')
        withStyle(SpanStyle(color = TerminalDim)) { append(entry.tag) }
        append("  ")
        withStyle(SpanStyle(color = if (entry.level == LogLevel.Error) ErrorInk else TerminalText)) {
            append(entry.message)
        }
    }
}

/** `2026/10/27 22:45:23.893860 [Warning] core: message`, as the core writes it. */
private val coreFormat = Regex("""^(\d{4}/\d\d/\d\d )?(\d\d:\d\d:\d\d)(\.\d+)? \[(\w+)] (.*)$""")

private fun coreLine(line: String): AnnotatedString {
    val match = coreFormat.find(line) ?: return AnnotatedString(line)
    val (_, time, _, level, message) = match.destructured
    val (tag, color) = when (level) {
        "Error" -> "ERR" to ErrorInk
        "Warning" -> "WRN" to WarnInk
        "Info" -> "INF" to InfoInk
        else -> "DBG" to TerminalDim
    }
    return buildAnnotatedString {
        withStyle(SpanStyle(color = TerminalDim)) { append(time) }
        append(' ')
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) { append(tag) }
        append("  ")
        withStyle(SpanStyle(color = if (level == "Error") ErrorInk else TerminalText)) { append(message) }
    }
}
