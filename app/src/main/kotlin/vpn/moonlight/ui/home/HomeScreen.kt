package vpn.moonlight.ui.home

import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import vpn.moonlight.R
import vpn.moonlight.data.model.AccountSubscription
import vpn.moonlight.design.MlIcon
import vpn.moonlight.design.MlIcons
import vpn.moonlight.design.MlShape
import vpn.moonlight.design.components.MlCard
import vpn.moonlight.design.components.MlDivider
import vpn.moonlight.design.components.MlLogo
import vpn.moonlight.design.components.MlPrimaryButton
import vpn.moonlight.design.components.MlProgressBar
import vpn.moonlight.design.components.MlSecondaryButton
import vpn.moonlight.design.components.MlText
import vpn.moonlight.design.ml
import vpn.moonlight.ui.common.currentLocale
import vpn.moonlight.ui.common.toast

/**
 * The account's subscriptions, as the cabinet's home shows them: days left as a
 * big number beside the moon in that phase, traffic and devices underneath, and
 * the one this phone uses marked. Choosing another loads its servers into the
 * connect screen.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onBuy: () -> Unit,
    onRenew: (AccountSubscription) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    val message = state.message?.resolve()
    LaunchedEffect(message) {
        if (message != null) {
            context.toast(message)
            viewModel.messageShown()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 18.dp)
            .padding(top = 10.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MlLogo(size = 36.dp, cornerRadius = 11.dp)
            MlText("moonlight", ml.type.lead)
        }
        val name = state.account?.displayName
        MlText(
            if (name != null) stringResource(R.string.home_hello, name) else stringResource(R.string.home_hello_anon),
            ml.type.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        val account = state.account
        when {
            account == null && state.error != null -> LoadFailed(state.error!!.resolve(), viewModel::refresh)
            account == null -> Unit
            account.subscriptions.isEmpty() -> Empty(state, onBuy, viewModel::activateTrial)
            else -> account.subscriptions
                .sortedWith(compareByDescending<AccountSubscription> { it.isActive }.thenByDescending { it.daysLeft })
                .forEach { sub ->
                    SubscriptionCard(
                        sub = sub,
                        inUse = sub.id == state.activeId,
                        switching = state.switching == sub.id,
                        onUse = { viewModel.use(sub) },
                        onRenew = { onRenew(sub) },
                    )
                }
        }
    }
}

@Composable
private fun SubscriptionCard(
    sub: AccountSubscription,
    inUse: Boolean,
    switching: Boolean,
    onUse: () -> Unit,
    onRenew: () -> Unit,
) {
    MlCard(Modifier.fillMaxWidth(), shape = MlShape.HeroCard) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MlIcon(MlIcons.Moon, size = 20.dp, tint = ml.colors.text)
                MlText(
                    sub.plan.ifBlank { "moonlight vpn" },
                    ml.type.bodyEmphatic,
                    Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                StatusChip(sub.isActive)
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MlText(sub.daysLeft.toString(), ml.type.hero)
                        MlText(
                            pluralStringResource(R.plurals.home_days_word, sub.daysLeft),
                            ml.type.lead,
                            Modifier.padding(bottom = 4.dp),
                            color = ml.colors.text2,
                        )
                    }
                    expiryText(sub.expiresAt)?.let {
                        MlText(stringResource(R.string.home_until, it), ml.type.meta, color = ml.colors.textMuted)
                    }
                    if (sub.syncPending) {
                        MlText(stringResource(R.string.home_syncing), ml.type.meta, color = ml.colors.warning)
                    }
                }
                val term = (sub.termDays ?: 30).coerceAtLeast(1)
                MoonPhase(if (sub.isActive) sub.daysLeft.toFloat() / term else 0f, Modifier.size(84.dp))
            }
            Spacer(Modifier.height(18.dp))
            MlDivider()
            Spacer(Modifier.height(14.dp))

            Meter(
                label = stringResource(R.string.home_traffic),
                value = if (sub.isUnlimitedTraffic) {
                    stringResource(R.string.home_unlimited)
                } else {
                    stringResource(R.string.plans_traffic, "${gb(sub.usedGb)} / ${gb(sub.trafficGb)}")
                },
                fraction = if (sub.isUnlimitedTraffic) null else (sub.usedGb / sub.trafficGb).toFloat(),
            )
            Spacer(Modifier.height(12.dp))
            Meter(label = stringResource(R.string.home_devices), value = sub.devices.toString(), fraction = null)

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    when {
                        inUse -> InUse()
                        sub.isActive && sub.hasLink -> MlPrimaryButton(
                            label = stringResource(R.string.home_use),
                            onClick = onUse,
                            enabled = !switching,
                        )
                        else -> Unit
                    }
                }
                MlSecondaryButton(stringResource(R.string.home_renew), onRenew)
            }
        }
    }
}

@Composable
private fun InUse() {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(MlShape.Pill)
            .background(ml.colors.highlight),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MlIcon(MlIcons.Check, size = 18.dp, tint = ml.colors.onHighlight, strokeWidth = 2.6f)
        Spacer(Modifier.size(8.dp))
        MlText(stringResource(R.string.home_in_use), ml.type.bodyEmphatic, color = ml.colors.onHighlight)
    }
}

@Composable
private fun StatusChip(active: Boolean) {
    val color = if (active) ml.colors.success else ml.colors.danger
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(MlShape.Pill).background(color))
        MlText(
            stringResource(if (active) R.string.home_active else R.string.home_expired),
            ml.type.metaEmphatic,
            color = ml.colors.text2,
        )
    }
}

@Composable
private fun Meter(label: String, value: String, fraction: Float?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            MlText(label, ml.type.bodySm, Modifier.weight(1f), color = ml.colors.text2)
            MlText(value, ml.type.bodySmTitle)
        }
        if (fraction != null) MlProgressBar(fraction, height = 6.dp, color = ml.colors.highlightFill)
    }
}

@Composable
private fun Empty(state: HomeUiState, onBuy: () -> Unit, onTrial: () -> Unit) {
    MlCard(Modifier.fillMaxWidth(), shape = MlShape.HeroCard) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MoonPhase(0.15f, Modifier.size(84.dp))
            Spacer(Modifier.height(4.dp))
            MlText(stringResource(R.string.home_empty_title), ml.type.lead)
            MlText(stringResource(R.string.home_empty_sub), ml.type.bodySm, color = ml.colors.text2)
            Spacer(Modifier.height(8.dp))
            MlPrimaryButton(stringResource(R.string.nav_buy), onBuy, leadingIcon = MlIcons.Plus)
            val trial = state.trial
            if (trial != null && trial.enabled && trial.canUse) {
                MlSecondaryButton(
                    stringResource(R.string.home_trial, trial.days),
                    onTrial,
                    Modifier.fillMaxWidth(),
                    leadingIcon = MlIcons.Sparkles,
                )
            }
        }
    }
}

@Composable
private fun LoadFailed(text: String, onRetry: () -> Unit) {
    MlCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MlText(stringResource(R.string.home_load_failed), ml.type.bodyTitle)
            MlText(text, ml.type.bodySm, color = ml.colors.text2)
            MlSecondaryButton(stringResource(R.string.action_retry), onRetry, leadingIcon = MlIcons.RefreshCw)
        }
    }
}

/**
 * The moon in the phase of [lit] (0 new, 1 full) — the cabinet draws days left
 * this way. Lit from the right, the terminator an ellipse across the disc.
 */
@Composable
private fun MoonPhase(lit: Float, modifier: Modifier = Modifier) {
    val light = ml.colors.highlightFill
    val dark = lerp(ml.colors.bg, ml.colors.text, 0.1f)
    val f = lit.coerceIn(0f, 1f)
    Canvas(modifier) {
        val r = size.minDimension / 2
        val c = Offset(size.width / 2, size.height / 2)
        val disc = Path().apply { addOval(Rect(c, r)) }
        clipPath(disc) {
            drawCircle(dark, r, c)
            drawRect(light, Offset(c.x, c.y - r), Size(r, 2 * r))
            // Under half lit, the terminator eats into the lit half; over half, the lit spills past it.
            val w = r * kotlin.math.abs(2 * f - 1)
            drawOval(if (f < 0.5f) dark else light, Offset(c.x - w, c.y - r), Size(2 * w, 2 * r))
        }
        drawCircle(Color.White.copy(alpha = 0.12f), r, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
    }
}

private fun gb(value: Double): String =
    if (value >= 10 || value == Math.floor(value)) value.toLong().toString() else "%.1f".format(value)

@Composable
private fun expiryText(iso: String?): String? {
    iso ?: return null
    val locale = currentLocale()
    // The server sends UTC, with or without the trailing Z.
    val instant = runCatching { Instant.parse(iso) }
        .recoverCatching { java.time.LocalDateTime.parse(iso).toInstant(java.time.ZoneOffset.UTC) }
        .getOrNull() ?: return null
    return instant.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMMM", locale))
}
