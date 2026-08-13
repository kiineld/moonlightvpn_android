package vpn.moonlight.ui.subscription

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vpn.moonlight.core.VpnController
import vpn.moonlight.BuildConfig
import vpn.moonlight.R
import vpn.moonlight.data.model.SubscriptionUserInfo
import vpn.moonlight.data.repository.RefreshState
import vpn.moonlight.data.util.Formatters
import vpn.moonlight.design.MlIcon
import vpn.moonlight.design.MlIcons
import vpn.moonlight.design.MlShape
import vpn.moonlight.design.components.MlCard
import vpn.moonlight.design.components.MlChevron
import vpn.moonlight.design.components.MlDivider
import vpn.moonlight.design.components.MlExternalMark
import vpn.moonlight.design.components.MlIconButton
import vpn.moonlight.design.components.MlIconTile
import vpn.moonlight.design.components.MlNavRow
import vpn.moonlight.design.components.MlOverline
import vpn.moonlight.design.components.MlPillButton
import vpn.moonlight.design.components.MlPressable
import vpn.moonlight.design.components.MlProgressBar
import vpn.moonlight.design.components.MlText
import vpn.moonlight.design.ml
import vpn.moonlight.ui.common.byteNumber
import vpn.moonlight.ui.common.byteText
import vpn.moonlight.ui.common.dateText
import vpn.moonlight.ui.common.daysText

@Composable
fun SubscriptionScreen(
    viewModel: SubscriptionViewModel,
    onAddSubscription: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmingDelete by remember { mutableStateOf(false) }

    if (confirmingDelete) {
        ConfirmDeleteDialog(
            onConfirm = {
                confirmingDelete = false
                // Drop the tunnel first: staying connected through a subscription
                // that no longer exists leaves no way to see or change the node.
                VpnController.disconnect(context)
                viewModel.delete()
            },
            onDismiss = { confirmingDelete = false },
        )
    }

    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MlText(stringResource(R.string.subscription_title), ml.type.title, Modifier.weight(1f))
            MlIconButton(
                icon = MlIcons.RefreshCw,
                onClick = viewModel::refresh,
                size = 42.dp,
                iconSize = 19.dp,
                tint = ml.colors.accentInk,
                content = { SpinningRefresh(state.isRefreshing, 19.dp) },
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 10.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val subscription = state.subscription
            if (subscription == null) {
                EmptyState(onAddSubscription)
                return@Column
            }

            PlanHero(
                planName = subscription.title ?: stringResource(R.string.subscription_plan_fallback),
                isExpired = state.isExpired,
                daysLeft = state.daysLeft,
                info = subscription.userInfo,
            )

            subscription.userInfo?.let { TrafficCard(it) }

            RefreshRow(state, viewModel::refresh)

            MlCard {
                MlNavRow(
                    title = stringResource(R.string.subscription_delete),
                    subtitle = stringResource(R.string.subscription_delete_sub),
                    onClick = { confirmingDelete = true },
                    titleEmphatic = true,
                    trailing = null,
                    leading = {
                        MlIconTile(ml.colors.dangerQuiet) {
                            MlIcon(MlIcons.Trash2, size = 19.dp, tint = ml.colors.danger)
                        }
                    },
                )
            }

            MlCard {
                Column {
                    MlNavRow(
                        title = stringResource(R.string.subscription_extend),
                        subtitle = stringResource(R.string.subscription_extend_sub),
                        onClick = { context.openUrl(BuildConfig.CABINET_URL) },
                        icon = MlIcons.Sparkles,
                        tileFill = ml.colors.cat2,
                        titleEmphatic = true,
                        trailing = { MlExternalMark() },
                    )
                    MlDivider(insetStart = 72.dp)
                    MlNavRow(
                        title = stringResource(R.string.subscription_add),
                        subtitle = stringResource(R.string.subscription_add_sub),
                        onClick = onAddSubscription,
                        icon = MlIcons.Plus,
                        tileFill = ml.colors.cat4,
                        titleEmphatic = true,
                    )
                }
            }
        }
    }
}

/** Kept on-style rather than using a Material dialog, which would not match. */
@Composable
private fun ConfirmDeleteDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        MlCard(Modifier.fillMaxWidth(), shape = MlShape.HeroCard) {
            Column(Modifier.padding(22.dp)) {
                MlText(stringResource(R.string.subscription_delete_confirm), ml.type.lead)
                Spacer(Modifier.height(8.dp))
                MlText(
                    stringResource(R.string.subscription_delete_confirm_body),
                    ml.type.bodySm,
                    color = ml.colors.text2,
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MlPillButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        height = 46.dp,
                        background = ml.colors.surface2,
                        contentColor = ml.colors.text,
                    ) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            MlText(
                                stringResource(R.string.action_cancel),
                                ml.type.bodySmEmphatic,
                                color = ml.colors.text,
                            )
                        }
                    }
                    MlPillButton(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        height = 46.dp,
                        background = ml.colors.danger,
                        contentColor = ml.colors.text,
                    ) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            MlText(
                                stringResource(R.string.action_delete),
                                ml.type.bodySmEmphatic,
                                color = androidx.compose.ui.graphics.Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpinningRefresh(
    spinning: Boolean,
    size: androidx.compose.ui.unit.Dp,
    tint: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
) {
    val transition = rememberInfiniteTransition(label = "spin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "angle",
    )
    MlIcon(
        MlIcons.RefreshCw,
        Modifier.rotate(if (spinning) angle else 0f),
        size = size,
        tint = if (tint == androidx.compose.ui.graphics.Color.Unspecified) ml.colors.accentInk else tint,
        strokeWidth = 2.2f,
    )
}

/** The accent plan card, with the soft circular wash bleeding off its lower-right. */
@Composable
private fun PlanHero(
    planName: String,
    isExpired: Boolean,
    daysLeft: Int?,
    info: SubscriptionUserInfo?,
) {
    val wash = ml.colors.inkWashSoft
    MlCard(shape = MlShape.HeroCard, background = ml.colors.accent) {
        Box(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawCircle(
                        color = wash,
                        radius = 115.dp.toPx(),
                        center = Offset(size.width + 70.dp.toPx() - 115.dp.toPx(), size.height + 5.dp.toPx()),
                    )
                }
                .padding(horizontal = 22.dp, vertical = 20.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        MlText(
                            stringResource(R.string.subscription_plan),
                            ml.type.metaEmphatic,
                            color = ml.colors.textOnAccent.copy(alpha = 0.6f),
                        )
                        Spacer(Modifier.height(3.dp))
                        MlText(
                            planName,
                            ml.type.plan,
                            color = ml.colors.textOnAccent,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(
                        Modifier
                            .clip(MlShape.Pill)
                            .background(ml.colors.inkWash)
                            .padding(horizontal = 11.dp, vertical = 5.dp),
                    ) {
                        MlText(
                            stringResource(
                                if (isExpired) R.string.subscription_expired else R.string.subscription_active,
                            ),
                            ml.type.overline.copy(letterSpacing = ml.type.metaEmphatic.letterSpacing),
                            color = ml.colors.textOnAccent,
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth()) {
                    HeroStat(
                        stringResource(R.string.subscription_label_remaining),
                        when {
                            daysLeft != null -> daysText(daysLeft)
                            // expire=0 means no expiry, which is infinite, not unknown.
                            info?.isPerpetual == true ->
                                stringResource(R.string.subscription_unlimited_short)
                            else -> stringResource(R.string.subscription_unknown)
                        },
                        Modifier.weight(1f),
                    )
                    HeroStat(
                        stringResource(R.string.subscription_label_traffic),
                        info?.usedBytes?.let { byteText(it) }
                            ?: stringResource(R.string.subscription_unknown),
                        Modifier.weight(1f),
                    )
                    HeroStat(
                        stringResource(R.string.subscription_label_devices),
                        formatDevices(info),
                        Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun formatDevices(info: SubscriptionUserInfo?): String {
    val limit = info?.deviceLimit
    val used = info?.devicesUsed
    return when {
        // Remnawave omits the device fields entirely when no HWID cap is set, so
        // absent means unlimited here rather than unknown.
        limit == null -> stringResource(R.string.subscription_unlimited_short)
        used == null -> "$limit"
        else -> "$used / $limit"
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        MlText(
            label,
            ml.type.overlineSm,
            color = ml.colors.textOnAccent.copy(alpha = 0.65f),
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        MlText(
            value,
            ml.type.lead.copy(fontSize = ml.type.body.fontSize),
            color = ml.colors.textOnAccent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TrafficCard(info: SubscriptionUserInfo) {
    MlCard {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                MlOverline(stringResource(R.string.subscription_label_traffic), Modifier.weight(1f))
                val used = info.usedBytes
                val total = info.totalBytes
                val text = when {
                    used == null -> stringResource(R.string.subscription_unknown)
                    info.isUnlimitedTraffic ->
                        "${byteText(used)} · ${stringResource(R.string.subscription_unlimited_short)}"
                    else -> stringResource(
                        R.string.subscription_traffic_of,
                        byteNumber(used),
                        byteText(total!!),
                    )
                }
                MlText(text, ml.type.metaEmphatic, color = ml.colors.text2, maxLines = 1)
            }

            Spacer(Modifier.height(12.dp))
            MlProgressBar(fraction = info.usedFraction ?: 0f)

            info.expiresAtEpochSeconds?.takeIf { it > 0 }?.let { expiry ->
                Spacer(Modifier.height(10.dp))
                MlText(
                    stringResource(R.string.subscription_valid_until, dateText(expiry)),
                    ml.type.meta,
                    color = ml.colors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun RefreshRow(state: SubscriptionUiState, onRefresh: () -> Unit) {
    val meta = when (val refresh = state.refresh) {
        RefreshState.Refreshing -> stringResource(R.string.subscription_refresh_running)
        is RefreshState.Done -> stringResource(R.string.subscription_refresh_done)
        is RefreshState.Failed -> stringResource(R.string.subscription_refresh_failed)
        RefreshState.Idle -> stringResource(R.string.subscription_refresh_sub)
        else -> stringResource(R.string.subscription_refresh_sub)
    }
    MlCard {
        MlNavRow(
            title = stringResource(R.string.subscription_refresh),
            subtitle = meta,
            onClick = onRefresh,
            titleEmphatic = true,
            trailing = null,
            leading = {
                MlIconTile(ml.colors.cat1) {
                    // Ink, not accent: the tile is already the accent colour, so
                    // an accent glyph on it disappears entirely in dark mode.
                    SpinningRefresh(state.isRefreshing, 19.dp, ml.colors.textOnAccent)
                }
            },
        )
    }
}

@Composable
private fun EmptyState(onAddSubscription: () -> Unit) {
    MlCard {
        Column(Modifier.padding(20.dp)) {
            MlText(stringResource(R.string.subscription_empty_title), ml.type.lead)
            Spacer(Modifier.height(6.dp))
            MlText(
                stringResource(R.string.subscription_empty_sub),
                ml.type.bodySm,
                color = ml.colors.textMuted,
            )
            Spacer(Modifier.height(16.dp))
            vpn.moonlight.design.components.MlPrimaryButton(
                label = stringResource(R.string.subscription_add),
                onClick = onAddSubscription,
            )
        }
    }
}

private fun Context.openUrl(url: String) {
    runCatching {
        startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
