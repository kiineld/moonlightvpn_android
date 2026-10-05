package vpn.moonlight.ui.connect

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import vpn.moonlight.R
import vpn.moonlight.core.VpnController
import vpn.moonlight.data.model.ConnectionState
import vpn.moonlight.data.model.Latency
import vpn.moonlight.data.model.ServerNode
import vpn.moonlight.data.util.Formatters
import vpn.moonlight.design.MlIcon
import vpn.moonlight.design.MlIcons
import vpn.moonlight.design.MlMotion
import vpn.moonlight.design.MlShape
import vpn.moonlight.design.components.MlCard
import vpn.moonlight.design.components.MlIconButton
import vpn.moonlight.design.components.MlLogo
import vpn.moonlight.design.components.MlOverline
import vpn.moonlight.design.components.MlPillButton
import vpn.moonlight.design.components.MlPressable
import vpn.moonlight.design.components.MlStatPair
import vpn.moonlight.design.components.MlText
import vpn.moonlight.design.components.MoonButton
import vpn.moonlight.design.components.MoonButtonDefaults
import vpn.moonlight.design.ml
import vpn.moonlight.design.pulseWhile
import vpn.moonlight.design.spinWhile
import vpn.moonlight.ui.common.byteText
import vpn.moonlight.ui.common.daysText
import vpn.moonlight.ui.common.displayName
import vpn.moonlight.ui.common.errorText
import vpn.moonlight.ui.common.flagOrDerived
import vpn.moonlight.ui.common.latencyText
import vpn.moonlight.ui.common.serversCountText
import vpn.moonlight.ui.common.subtitleText

@Composable
fun ConnectScreen(
    viewModel: ConnectViewModel,
    onAddSubscription: () -> Unit,
    modifier: Modifier = Modifier,
    autoConnect: Boolean = false,
    onAutoConnectConsumed: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // VpnService.prepare must be answered by an Activity result before the first
    // connect; afterwards it returns null and this never launches again.
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.connect(context)
    }

    val onToggle: () -> Unit = {
        if (state.connection.isActive) {
            viewModel.toggleConnection(context)
        } else {
            val consent = VpnController.consentIntent(context)
            if (consent == null) viewModel.connect(context) else consentLauncher.launch(consent)
        }
    }

    // Routed through the same handler as the button, so a deep link still goes
    // through the VPN consent dialog on the first run.
    LaunchedEffect(autoConnect, state.hasSubscription) {
        if (autoConnect && state.hasSubscription && !state.connection.isActive) {
            onToggle()
            onAutoConnectConsumed()
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        // What the list may grow to once it is open: whatever the page has left
        // under the header and a compact stage. Worked out from the page rather
        // than fixed, so a tall phone shows a long list and a short one still
        // keeps the button whole above it.
        val listRoom = (maxHeight - HEADER_HEIGHT - COMPACT_STAGE - SERVERS_CHROME - TAB_BAR_CLEARANCE)
            .coerceAtLeast(MIN_LIST_HEIGHT)

        Column(Modifier.fillMaxSize()) {
            ConnectHeader()

            Stage(
                state = state,
                viewModel = viewModel,
                onToggle = onToggle,
                onAddSubscription = onAddSubscription,
            )

            if (state.hasSubscription) {
                Servers(state, viewModel, listRoom)
            }

            Spacer(Modifier.height(TAB_BAR_CLEARANCE))
        }
    }
}

private val HEADER_HEIGHT = 60.dp

/** The time, the compact button and the state pill, with the gaps between them. */
private val COMPACT_STAGE = 216.dp

/** The servers heading, the picker, and the gaps round them. */
private val SERVERS_CHROME = 124.dp
private val TAB_BAR_CLEARANCE = 104.dp

/** Two rows: a list too short to scroll in is not a list. */
private val MIN_LIST_HEIGHT = 116.dp

@Composable
private fun ConnectHeader() {
    Row(
        Modifier
            .fillMaxWidth()
            .height(HEADER_HEIGHT)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MlLogo(size = 36.dp, cornerRadius = 11.dp)
        MlText("moonlight", ml.type.lead)
    }
}

/**
 * The page's hero: how long the tunnel has been up, the moon, and its state in
 * words. With the server list closed it is the page — large, in the middle of
 * the room it has. Opening the list shrinks the button and the list takes the
 * room that frees.
 */
@Composable
private fun ColumnScope.Stage(
    state: ConnectUiState,
    viewModel: ConnectViewModel,
    onToggle: () -> Unit,
    onAddSubscription: () -> Unit,
) {
    val connection = state.connection
    val connected = connection is ConnectionState.Connected
    val busy = connection is ConnectionState.Connecting || connection is ConnectionState.Reconnecting

    // The button is resized, not scaled: a graphicsLayer scale only shrinks what
    // is drawn, so the layout would still reserve the full size.
    val side by animateDpAsState(
        if (state.isListExpanded) MoonButtonDefaults.Compact else MoonButtonDefaults.Large,
        MlMotion.layout(Dp.VisibilityThreshold),
        label = "moonSide",
    )

    val hint = when {
        !state.hasSubscription -> stringResource(R.string.connect_no_subscription_sub)
        connection is ConnectionState.Error -> errorText(connection.reason)
        connected -> stringResource(R.string.connect_hint_tap_disconnect)
        busy -> stringResource(R.string.connect_hint_connecting)
        else -> stringResource(R.string.connect_hint_tap_connect)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        // Zero spacing, with each gap living *inside* the collapsing child below.
        // Arrangement spacing is only applied between children that are present,
        // so a gap next to an AnimatedVisibility survives the whole collapse and
        // then disappears in a single frame — and because this column is centred,
        // losing the gaps at once dropped the button instantly. Gaps that shrink
        // with their own content cannot do that.
        verticalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterVertically),
    ) {
        UptimeLabel(state.connectedSinceMillis)

        Spacer(Modifier.height(16.dp))
        MoonButton(
            connected = connected,
            busy = busy,
            onClick = { if (state.hasSubscription) onToggle() else onAddSubscription() },
            side = side,
            onClickLabel = hint,
        )

        Spacer(Modifier.height(16.dp))
        StatusPill(
            label = when {
                !state.hasSubscription -> stringResource(R.string.connect_no_subscription)
                connected -> stringResource(R.string.connect_big_connected)
                connection is ConnectionState.Reconnecting ->
                    stringResource(R.string.connect_status_reconnecting)
                busy -> stringResource(R.string.connect_status_connecting)
                connection is ConnectionState.Error -> stringResource(R.string.connect_status_error)
                else -> stringResource(R.string.connect_big_connect)
            },
            connected = connected,
            failed = connection is ConnectionState.Error,
        )

        Collapsing(visible = !state.isListExpanded) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(14.dp))
                MlText(
                    hint,
                    ml.type.meta,
                    color = if (connection is ConnectionState.Error) ml.colors.danger else ml.colors.textMuted,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Collapsing(visible = !state.isListExpanded && state.hasSubscription) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(18.dp))
                SessionStats(viewModel, state)
            }
        }

        if (!state.hasSubscription) {
            Spacer(Modifier.height(18.dp))
            MlPillButton(
                onClick = onAddSubscription,
                height = 42.dp,
                background = ml.colors.accent,
                contentColor = ml.colors.textOnAccent,
                horizontalPadding = 20.dp,
            ) {
                MlText(
                    stringResource(R.string.subscription_add),
                    ml.type.metaEmphatic,
                    color = ml.colors.textOnAccent,
                )
            }
        }
    }
}

/** Folds a part of the stage away, gap and all, on the one curve. */
@Composable
private fun ColumnScope.Collapsing(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(MlMotion.layout()) +
            expandVertically(MlMotion.layout(IntSize.VisibilityThreshold)),
        exit = fadeOut(MlMotion.paint()) +
            shrinkVertically(MlMotion.layout(IntSize.VisibilityThreshold)),
    ) {
        content()
    }
}

/**
 * How long the tunnel has been up.
 *
 * The page's one figure that changes every second, so the one part of it that
 * ticks: the clock lives here, not in the screen's state, and a tick redraws
 * this label and nothing else. It runs only while there is a session to time,
 * and only while the screen is actually showing — the app in the background
 * has nobody to count for.
 */
@Composable
private fun UptimeLabel(sinceMillis: Long?) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    if (sinceMillis != null) {
        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(sinceMillis, lifecycleOwner) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    now = System.currentTimeMillis()
                    // To the next whole second of the session, so the figure
                    // changes when the second does rather than somewhere in it.
                    delay(1_000L - (now - sinceMillis).mod(1_000L))
                }
            }
        }
    }

    val seconds = sinceMillis?.let { Formatters.elapsedSeconds(it, maxOf(now, it)) } ?: 0L
    val tone by animateColorAsState(
        if (sinceMillis != null) ml.colors.text else ml.colors.textMuted,
        MlMotion.paint(),
        label = "uptimeTone",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        MlText(stringResource(R.string.connect_connection_time), ml.type.metaSm, color = ml.colors.textMuted)
        Spacer(Modifier.height(2.dp))
        MlText(Formatters.formatDuration(seconds), ml.type.timer, color = tone, maxLines = 1)
    }
}

/** The state, in words. Washed with the accent while the tunnel is up. */
@Composable
private fun StatusPill(label: String, connected: Boolean, failed: Boolean) {
    val fill by animateColorAsState(
        if (connected) ml.colors.accent.copy(alpha = 0.16f) else ml.colors.surface,
        MlMotion.paint(),
        label = "statusFill",
    )
    val ink by animateColorAsState(
        when {
            failed -> ml.colors.danger
            connected -> ml.colors.accentInkStrong
            else -> ml.colors.text2
        },
        MlMotion.paint(),
        label = "statusInk",
    )
    Box(
        Modifier
            .height(34.dp)
            .clip(MlShape.Pill)
            .background(fill)
            .then(if (connected) Modifier else Modifier.border(1.dp, ml.colors.hairlineSoft, MlShape.Pill))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        MlText(label, ml.type.bodySmTitle, color = ink, maxLines = 1)
    }
}

/**
 * Traffic this session, and what is left of the subscription.
 *
 * The byte counter is collected here and nowhere above, for the same reason the
 * clock is: it moves every second, and only this block should hear about it.
 * Collecting it is also what has the service read the tunnel's counters, so
 * with this off screen that polling stops.
 */
@Composable
private fun SessionStats(viewModel: ConnectViewModel, state: ConnectUiState) {
    val sessionBytes by viewModel.sessionBytes.collectAsStateWithLifecycle()
    MlStatPair(
        leftLabel = stringResource(R.string.connect_stat_session),
        leftValue = byteText(sessionBytes),
        rightLabel = stringResource(R.string.connect_stat_remaining),
        rightValue = when {
            state.daysLeft != null -> daysText(state.daysLeft)
            state.isPerpetual -> stringResource(R.string.subscription_unlimited_short)
            else -> stringResource(R.string.subscription_unknown)
        },
        modifier = Modifier.widthIn(max = 300.dp),
    )
}

// region Servers

@Composable
private fun Servers(state: ConnectUiState, viewModel: ConnectViewModel, listRoom: Dp) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        ServersHeading(state, viewModel)
        Spacer(Modifier.height(10.dp))
        NodePicker(state, viewModel::toggleList)
        NodeList(state, viewModel, listRoom)
    }
}

/**
 * "Servers", how many, and the two things to do to all of them: measure, and
 * re-fetch. Both work whether or not the tunnel is up.
 */
@Composable
private fun ServersHeading(state: ConnectUiState, viewModel: ConnectViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MlOverline(stringResource(R.string.connect_servers))
        MlText(serversCountText(state.nodes.size), ml.type.metaSm, color = ml.colors.textMuted)
        Spacer(Modifier.weight(1f))

        MlIconButton(
            icon = MlIcons.Activity,
            onClick = viewModel::measureLatency,
            size = 40.dp,
            background = ml.colors.surface,
            enabled = !state.isMeasuring,
            label = stringResource(if (state.isMeasuring) R.string.connect_pinging else R.string.connect_ping),
        ) {
            MlIcon(
                MlIcons.Activity,
                Modifier.pulseWhile(state.isMeasuring),
                size = 17.dp,
                tint = ml.colors.accentInk,
                strokeWidth = 2.2f,
            )
        }
        MlIconButton(
            icon = MlIcons.RefreshCw,
            onClick = viewModel::refresh,
            size = 40.dp,
            background = ml.colors.surface,
            enabled = !state.isRefreshing,
            label = stringResource(
                if (state.isRefreshing) R.string.connect_refreshing else R.string.connect_refresh_subscription,
            ),
        ) {
            MlIcon(
                MlIcons.RefreshCw,
                Modifier.spinWhile(state.isRefreshing),
                size = 17.dp,
                tint = ml.colors.accentInk,
                strokeWidth = 2.2f,
            )
        }
    }
}

/** The server in use, and the way into the rest: one row until it is opened. */
@Composable
private fun NodePicker(state: ConnectUiState, onToggle: () -> Unit) {
    val chevronRotation by animateFloatAsState(
        if (state.isListExpanded) 180f else 0f,
        MlMotion.layout(),
        label = "chevron",
    )

    Box(
        Modifier
            .fillMaxWidth()
            .clip(MlShape.Pill)
            .background(ml.colors.surface)
            .border(1.dp, ml.colors.hairlineSoft, MlShape.Pill),
    ) {
        MlPressable(onClick = onToggle) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(ml.colors.surface2),
                    contentAlignment = Alignment.Center,
                ) {
                    // Lightning for the Auto selection, and equally for the
                    // panel's own balancing node — pinning it by hand does not
                    // make it a country.
                    if (state.isAuto || state.activeNode?.isAutoNode == true) {
                        MlIcon(MlIcons.Zap, size = 19.dp, tint = ml.colors.accentInk, strokeWidth = 2.2f)
                    } else {
                        MlText(state.activeNode?.flagOrDerived().orEmpty(), ml.type.lead)
                    }
                }
                Column(Modifier.weight(1f)) {
                    val node = state.activeNode
                    val title = when {
                        node == null -> stringResource(R.string.connect_no_subscription)
                        // The panel's own Auto node already says so; prefixing
                        // it again would read "Авто · Auto".
                        state.isAuto && !node.isAutoNode ->
                            stringResource(R.string.connect_auto_prefix, node.name)
                        else -> node.displayName()
                    }
                    MlText(title, ml.type.bodySmEmphatic, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (node != null) {
                        MlText(
                            node.subtitleText(state.activeLatency),
                            ml.type.metaSm,
                            color = ml.colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                MlIcon(
                    MlIcons.ChevronDown,
                    Modifier.rotate(chevronRotation),
                    size = 20.dp,
                    tint = ml.colors.text2,
                    strokeWidth = 2.2f,
                )
            }
        }
    }
}

/** Every server, under the picker. It may use [listRoom]; past that it scrolls. */
@Composable
private fun ColumnScope.NodeList(state: ConnectUiState, viewModel: ConnectViewModel, listRoom: Dp) {
    AnimatedVisibility(
        visible = state.isListExpanded,
        // The same curve as the button and the stats, so the page settles
        // together. Closing, it fades faster than it folds, so an empty card
        // is never left shrinking after its rows have gone.
        enter = expandVertically(MlMotion.layout(IntSize.VisibilityThreshold), expandFrom = Alignment.Top) +
            fadeIn(MlMotion.layout()),
        exit = shrinkVertically(MlMotion.layout(IntSize.VisibilityThreshold), shrinkTowards = Alignment.Top) +
            fadeOut(MlMotion.paint()),
    ) {
        Column {
            Spacer(Modifier.height(8.dp))
            MlCard(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = listRoom),
                shape = RoundedCornerShape(22.dp),
            ) {
                LazyColumn(contentPadding = PaddingValues(8.dp)) {
                    if (state.showsAutoRow) {
                        item(key = AUTO_ROW_KEY) {
                            AutoRowItem(state, viewModel::selectAuto)
                        }
                    }
                    items(state.nodes, key = { it.node.id }) { row ->
                        NodeRowItem(row) { viewModel.selectNode(row.node.id) }
                    }
                }
            }
        }
    }
}

private const val AUTO_ROW_KEY = "auto"

/**
 * The automatic choice, when the panel does not publish one of its own: the
 * fastest node by the last measurement.
 */
@Composable
private fun AutoRowItem(state: ConnectUiState, onClick: () -> Unit) {
    val picked = state.activeNode?.takeIf { state.isAuto }
    SelectableRow(selected = state.isAuto, onClick = onClick) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (state.isAuto) ml.colors.accent else ml.colors.surface2),
            contentAlignment = Alignment.Center,
        ) {
            MlIcon(
                MlIcons.Zap,
                size = 18.dp,
                tint = if (state.isAuto) ml.colors.textOnAccent else ml.colors.accentInk,
                strokeWidth = 2.2f,
            )
        }
        Column(Modifier.weight(1f)) {
            MlText(stringResource(R.string.connect_auto), ml.type.bodySmTitle, maxLines = 1)
            MlText(
                // While it is the active choice, say which node it landed on —
                // that is the thing the row cannot otherwise tell you.
                if (picked != null) {
                    stringResource(R.string.connect_auto_picked, picked.name)
                } else {
                    stringResource(R.string.connect_auto_sub)
                },
                ml.type.metaSm,
                color = ml.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (picked != null) LatencyCell(state.activeLatency, state.isActiveMeasuring)
    }
}

@Composable
private fun NodeRowItem(row: NodeRow, onClick: () -> Unit) {
    SelectableRow(selected = row.isSelected, onClick = onClick) {
        NodeGlyph(row.node)
        Column(Modifier.weight(1f)) {
            MlText(
                row.node.displayName(),
                ml.type.bodySmTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MlText(
                row.node.subtitleText(),
                ml.type.metaSm,
                color = ml.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LatencyCell(row.latency, row.isMeasuring)
    }
}

@Composable
private fun SelectableRow(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val fill by animateColorAsState(
        if (selected) ml.colors.surface2 else Color.Transparent,
        MlMotion.paint(),
        label = "rowFill",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MlShape.Row)
            .background(fill),
    ) {
        MlPressable(onClick = onClick) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

/**
 * The mark at the head of a node row: the country flag, or the lightning bolt for
 * a panel's balancing node — which has no country to show, and reads as the same
 * thing the Auto selector does.
 *
 * A fixed width so every row's text starts on the same line, which flag emoji of
 * differing widths would not manage on their own.
 */
@Composable
private fun NodeGlyph(node: ServerNode) {
    Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
        if (node.isAutoNode) {
            MlIcon(MlIcons.Zap, size = 18.dp, tint = ml.colors.accentInk, strokeWidth = 2.2f)
        } else {
            MlText(node.flagOrDerived().orEmpty(), ml.type.lead)
        }
    }
}

/**
 * A node's latency: a dot in the signal's colour and the figure beside it.
 *
 * The figure is the last one measured and stays until the next one lands. While
 * a probe is out for the node it is dimmed, not blanked — a list that flashed to
 * dashes on every pass was useless at exactly the moment it was being read.
 */
@Composable
private fun LatencyCell(latency: Latency, measuring: Boolean) {
    val dot = when (latency) {
        is Latency.Value -> ml.colors.latencyColor(latency.ms)
        is Latency.Failed -> ml.colors.danger
        Latency.Unknown -> ml.colors.textMuted
    }
    Row(
        Modifier.alpha(if (measuring && latency != Latency.Unknown) 0.45f else 1f),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dot),
        )
        MlText(latencyText(latency, measuring), ml.type.monoSm, color = ml.colors.text2)
    }
}

// endregion
