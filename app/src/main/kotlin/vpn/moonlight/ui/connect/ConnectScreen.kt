package vpn.moonlight.ui.connect

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vpn.moonlight.R
import vpn.moonlight.core.VpnController
import vpn.moonlight.data.model.ConnectionState
import vpn.moonlight.data.model.Latency
import vpn.moonlight.data.model.ServerNode
import vpn.moonlight.design.MlIcon
import vpn.moonlight.design.MlIcons
import vpn.moonlight.design.MlMotion
import vpn.moonlight.design.MlShape
import vpn.moonlight.design.components.ConnectDial
import vpn.moonlight.design.components.DefaultDiameter as ConnectDialDefaults
import vpn.moonlight.design.components.MlCard
import vpn.moonlight.design.components.MlLogo
import vpn.moonlight.design.components.MlPillButton
import vpn.moonlight.design.components.MlPressable
import vpn.moonlight.design.components.MlStatPair
import vpn.moonlight.design.components.MlText
import vpn.moonlight.design.ml
import vpn.moonlight.ui.common.byteText
import vpn.moonlight.ui.common.daysText
import vpn.moonlight.ui.common.errorText
import vpn.moonlight.ui.common.displayName
import vpn.moonlight.ui.common.flagOrDerived
import vpn.moonlight.ui.common.latencyText
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

    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        ConnectHeader()

        Stage(state = state, onToggle = onToggle, onAddSubscription = onAddSubscription)

        if (state.hasSubscription) {
            QuickPicks(state, viewModel)
            Spacer(Modifier.height(12.dp))
            NodeSelector(state, viewModel)
        }

        // Clearance for the floating tab bar.
        Spacer(Modifier.height(104.dp))
    }
}

/** Dial size while the node list is open, chosen to fit the remaining stage. */
private val COMPACT_DIAL = 140.dp

@Composable
private fun ConnectHeader() {
    Row(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MlLogo(size = 36.dp, cornerRadius = 11.dp)
        MlText("moonlight", ml.type.lead)
    }
}

/** The refresh glyph, rotating while a fetch is in flight. */
@Composable
private fun SpinningIcon(spinning: Boolean, size: androidx.compose.ui.unit.Dp = 17.dp) {
    val transition = rememberInfiniteTransition(label = "spin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "spinAngle",
    )
    MlIcon(
        MlIcons.RefreshCw,
        Modifier.rotate(if (spinning) angle else 0f),
        size = size,
        tint = ml.colors.accentInk,
        strokeWidth = 2.2f,
    )
}

@Composable
private fun ColumnScope.Stage(
    state: ConnectUiState,
    onToggle: () -> Unit,
    onAddSubscription: () -> Unit,
) {
    // The dial is resized, not scaled: a graphicsLayer scale only shrinks what is
    // drawn, so the layout still reserved the full 244dp and the ring was clipped.
    val diameter by animateDpAsState(
        if (state.isListExpanded) COMPACT_DIAL else ConnectDialDefaults,
        MlMotion.layout(),
        label = "dialDiameter",
    )

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
        // losing 36dp of gaps at once dropped the dial ~18dp instantly. Gaps that
        // shrink with their own content cannot do that.
        verticalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterVertically),
    ) {
        val connection = state.connection
        val statusLabel = stringResource(
            when (connection) {
                is ConnectionState.Connected -> R.string.connect_status_protected
                is ConnectionState.Connecting -> R.string.connect_status_connecting
                is ConnectionState.Reconnecting -> R.string.connect_status_reconnecting
                is ConnectionState.Error -> R.string.connect_status_error
                ConnectionState.Disconnected -> R.string.connect_status_disconnected
            },
        )
        val bigLabel = stringResource(
            when (connection) {
                is ConnectionState.Connected -> R.string.connect_big_connected
                is ConnectionState.Connecting, is ConnectionState.Reconnecting ->
                    R.string.connect_big_connecting
                else -> R.string.connect_big_connect
            },
        )

        ConnectDial(
            statusLabel = statusLabel,
            bigLabel = bigLabel,
            timer = state.timerText,
            progress = state.progress,
            active = connection.isActive,
            onClick = { if (state.hasSubscription) onToggle() else onAddSubscription() },
            diameter = diameter,
        )

        val hint = when {
            !state.hasSubscription -> stringResource(R.string.connect_no_subscription_sub)
            connection is ConnectionState.Error -> errorText(connection.reason)
            connection is ConnectionState.Connected -> stringResource(R.string.connect_hint_tap_disconnect)
            connection is ConnectionState.Connecting || connection is ConnectionState.Reconnecting ->
                stringResource(R.string.connect_hint_connecting)
            else -> stringResource(R.string.connect_hint_tap_connect)
        }
        AnimatedVisibility(
            visible = !state.isListExpanded,
            enter = fadeIn(MlMotion.layout()) + expandVertically(MlMotion.layout()),
            exit = fadeOut(MlMotion.layout()) + shrinkVertically(MlMotion.layout()),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(18.dp))
                MlText(
                    hint,
                    ml.type.meta,
                    color = if (connection is ConnectionState.Error) {
                        ml.colors.danger
                    } else {
                        ml.colors.textMuted
                    },
                    maxLines = 2,
                )
            }
        }

        AnimatedVisibility(
            visible = !state.isListExpanded,
            enter = fadeIn(MlMotion.layout()) + expandVertically(MlMotion.layout()),
            exit = fadeOut(MlMotion.layout()) + shrinkVertically(MlMotion.layout()),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(18.dp))
                MlStatPair(
                    leftLabel = stringResource(R.string.connect_stat_session),
                    leftValue = byteText(state.sessionBytes),
                    rightLabel = stringResource(R.string.connect_stat_remaining),
                    rightValue = when {
                        state.daysLeft != null -> daysText(state.daysLeft)
                        state.isPerpetual -> stringResource(R.string.subscription_unlimited_short)
                        else -> stringResource(R.string.subscription_unknown)
                    },
                    modifier = Modifier.widthIn(max = 300.dp),
                )
            }
        }
    }
}

/**
 * Two actions, pushed to opposite ends: measure latency, and re-fetch the
 * subscription. Node choice lives in the selector below, including the panel's own
 * Auto entry, so it does not need a chip of its own.
 */
@Composable
private fun QuickPicks(state: ConnectUiState, viewModel: ConnectViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MlPillButton(
            onClick = viewModel::measureLatency,
            height = 42.dp,
            background = ml.colors.surface,
            contentColor = ml.colors.accentInk,
            bordered = true,
            horizontalPadding = 16.dp,
        ) {
            BlinkingIcon(state.isMeasuring)
            MlText(
                stringResource(if (state.isMeasuring) R.string.connect_pinging else R.string.connect_ping),
                ml.type.metaEmphatic,
                color = ml.colors.accentInk,
            )
        }

        MlPillButton(
            onClick = viewModel::refresh,
            height = 42.dp,
            background = ml.colors.surface,
            contentColor = ml.colors.accentInk,
            bordered = true,
            horizontalPadding = 16.dp,
        ) {
            SpinningIcon(state.isRefreshing)
            MlText(
                stringResource(
                    if (state.isRefreshing) R.string.connect_refreshing else R.string.connect_refresh_subscription,
                ),
                ml.type.metaEmphatic,
                color = ml.colors.accentInk,
            )
        }
    }
}

/** The ping glyph pulses while a measurement runs. */
@Composable
private fun BlinkingIcon(blinking: Boolean) {
    val transition = rememberInfiniteTransition(label = "blink")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            tween(900, easing = MlMotion.Ease),
            RepeatMode.Reverse,
        ),
        label = "blinkAlpha",
    )
    MlIcon(
        MlIcons.Activity,
        Modifier.graphicsLayer { this.alpha = if (blinking) alpha else 1f },
        size = 14.dp,
        tint = ml.colors.accentInk,
        strokeWidth = 2.4f,
    )
}

@Composable
private fun NodeSelector(state: ConnectUiState, viewModel: ConnectViewModel) {
    val chevronRotation by animateFloatAsState(
        if (state.isListExpanded) 180f else 0f,
        MlMotion.layout(),
        label = "chevron",
    )

    Column(Modifier.padding(horizontal = 16.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(MlShape.Pill)
                .background(ml.colors.surface)
                .border(1.dp, ml.colors.hairline, MlShape.Pill),
        ) {
            MlPressable(onClick = viewModel::toggleList) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
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
                            MlIcon(
                                MlIcons.Zap,
                                size = 19.dp,
                                tint = ml.colors.accentInk,
                                strokeWidth = 2.2f,
                            )
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
                        MlText(
                            title,
                            ml.type.bodySmEmphatic,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (node != null) {
                            val latency = state.nodes.firstOrNull { it.node.id == node.id }?.latency
                                ?: Latency.Unknown
                            MlText(
                                node.subtitleText(latency),
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
                        tint = ml.colors.textMuted,
                        strokeWidth = 2.2f,
                    )
                }
            }
        }

        NodeList(state, viewModel)
    }
}

/** The expandable node list, below the selector. */
@Composable
private fun NodeList(state: ConnectUiState, viewModel: ConnectViewModel) {
    AnimatedVisibility(
        visible = state.isListExpanded,
        // Same spec as the dial and the stats, so the whole screen settles together.
        enter = expandVertically(MlMotion.layout(), expandFrom = Alignment.Top) +
            fadeIn(MlMotion.layout()),
        exit = shrinkVertically(MlMotion.layout(), shrinkTowards = Alignment.Top) +
            fadeOut(MlMotion.layout()),
    ) {
        Column {
            Spacer(Modifier.height(8.dp))
            MlCard(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 270.dp),
                shape = RoundedCornerShape(22.dp),
            ) {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                ) {
                    state.nodes.forEach { row -> NodeRowItem(row, viewModel) }
                }
            }
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

@Composable
private fun NodeRowItem(row: NodeRow, viewModel: ConnectViewModel) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MlShape.Row)
            .background(if (row.isSelected) ml.colors.surface2 else androidx.compose.ui.graphics.Color.Transparent),
    ) {
        MlPressable(onClick = { viewModel.selectNode(row.node.id) }) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NodeGlyph(row.node)
                Column(Modifier.weight(1f)) {
                    MlText(
                        row.node.displayName(),
                        ml.type.bodySmTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    MlText(
                        row.node.subtitleText(Latency.Unknown),
                        ml.type.metaSm,
                        color = ml.colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val dotColor = when (val latency = row.latency) {
                        is Latency.Value -> ml.colors.latencyColor(latency.ms)
                        else -> ml.colors.textMuted
                    }
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(dotColor),
                    )
                    MlText(latencyText(row.latency), ml.type.monoSm, color = ml.colors.text2)
                }
            }
        }
    }
}
