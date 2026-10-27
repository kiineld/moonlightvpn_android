package vpn.moonlight.ui.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vpn.moonlight.R
import vpn.moonlight.data.model.Pricing
import vpn.moonlight.data.model.Tariff
import vpn.moonlight.design.MlIcon
import vpn.moonlight.design.MlIcons
import vpn.moonlight.design.MlShape
import vpn.moonlight.design.components.MlCard
import vpn.moonlight.design.components.MlDivider
import vpn.moonlight.design.components.MlIconButton
import vpn.moonlight.design.components.MlOverline
import vpn.moonlight.design.components.MlPressable
import vpn.moonlight.design.components.MlPrimaryButton
import vpn.moonlight.design.components.MlSegmented
import vpn.moonlight.design.components.MlText
import vpn.moonlight.design.ml
import vpn.moonlight.ui.common.openUrl
import vpn.moonlight.ui.common.toast

/** Buying a plan, or renewing one: what, for how long, and how to pay. */
@Composable
fun PlansScreen(viewModel: PlansViewModel, onBack: () -> Unit, onDone: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val opened = stringResource(R.string.wallet_payment_opened)
    val done = stringResource(R.string.plans_done)

    LaunchedEffect(state.outcome) {
        when (val outcome = state.outcome) {
            is PlansOutcome.OpenPayment -> {
                context.openUrl(outcome.url)
                context.toast(opened)
            }
            PlansOutcome.Done -> {
                context.toast(done)
                onDone()
            }
            null -> return@LaunchedEffect
        }
        viewModel.outcomeHandled()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp)
            .padding(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MlIconButton(MlIcons.ChevronLeft, onBack, label = stringResource(R.string.action_back))
            val renewing = state.renewing
            MlText(
                if (renewing != null) stringResource(R.string.plans_renew_title, renewing.plan) else stringResource(R.string.plans_title),
                ml.type.titleSm,
                maxLines = 1,
            )
        }

        if (state.renewing == null) {
            state.tariffs.forEach { tariff ->
                TariffCard(tariff, selected = tariff.id == state.tariffId) { viewModel.selectTariff(tariff.id) }
            }
        }

        MlOverline(stringResource(R.string.plans_period))
        val periods = Pricing.periods
        MlSegmented(
            options = periods.map { periodLabel(it) },
            selectedIndex = periods.indexOf(state.days).coerceAtLeast(0),
            onSelect = { viewModel.selectDays(periods[it]) },
            segmentHeight = 40.dp,
        )

        MlOverline(stringResource(R.string.wallet_method))
        MlCard(Modifier.fillMaxWidth()) {
            Column {
                MethodRow(
                    title = stringResource(R.string.plans_pay_balance, state.account?.balanceFormatted.orEmpty()),
                    selected = state.method == PlansUiState.BALANCE,
                ) { viewModel.selectMethod(PlansUiState.BALANCE) }
                state.account?.paymentMethods.orEmpty().forEach { method ->
                    MlDivider()
                    MethodRow(
                        title = method.name.ifBlank { method.id },
                        subtitle = if (method.feePercent > 0) stringResource(R.string.wallet_fee, fee(method.feePercent)) else null,
                        selected = state.method == method.id,
                    ) { viewModel.selectMethod(method.id) }
                }
            }
        }

        state.account?.discount?.percent?.takeIf { it > 0 }?.let {
            MlText(stringResource(R.string.plans_discount, it), ml.type.bodySmTitle, color = ml.colors.success)
        }
        if (state.balanceShort) {
            MlText(stringResource(R.string.plans_not_enough), ml.type.bodySm, color = ml.colors.warning)
        }
        state.error?.let { MlText(it.resolve(), ml.type.bodySm, color = ml.colors.danger) }

        Spacer(Modifier.height(4.dp))
        MlPrimaryButton(
            label = stringResource(R.string.plans_pay, state.price?.let(Pricing::rubles) ?: "—"),
            onClick = viewModel::pay,
            enabled = state.canPay,
            leadingIcon = MlIcons.CreditCard,
        )
    }
}

@Composable
private fun TariffCard(tariff: Tariff, selected: Boolean, onClick: () -> Unit) {
    MlPressable(onClick, Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(MlShape.Card)
                .background(if (selected) ml.colors.accentQuiet else ml.colors.surface)
                .border(if (selected) 2.dp else 1.dp, if (selected) ml.colors.highlightFill else ml.colors.hairline, MlShape.Card)
                .padding(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MlText(tariff.name, ml.type.lead, Modifier.weight(1f))
                    if (tariff.isPopular) Chip(stringResource(R.string.plans_popular))
                }
                if (tariff.description.isNotBlank()) {
                    MlText(tariff.description, ml.type.bodySm, color = ml.colors.text2)
                }
                val traffic = if (tariff.trafficGb <= 0) {
                    stringResource(R.string.plans_unlimited)
                } else {
                    stringResource(R.string.plans_traffic, tariff.trafficGb.toLong().toString())
                }
                MlText(
                    pluralStringResource(R.plurals.plans_devices, tariff.devices, tariff.devices) + " · " + traffic,
                    ml.type.meta,
                    color = ml.colors.textMuted,
                )
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MlText(Pricing.rubles(tariff.pricePer30), ml.type.titleSm)
                    MlText("/ " + periodLabel(30), ml.type.meta, Modifier.padding(bottom = 3.dp), color = ml.colors.textMuted)
                    if (tariff.hasSale && tariff.originalPricePer30 > tariff.pricePer30) {
                        MlText(
                            Pricing.rubles(tariff.originalPricePer30),
                            ml.type.meta.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough),
                            Modifier.padding(bottom = 3.dp),
                            color = ml.colors.textMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String) {
    MlText(
        text,
        ml.type.overlineSm,
        Modifier
            .clip(MlShape.Pill)
            .background(ml.colors.highlight)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        color = ml.colors.onHighlight,
    )
}

@Composable
private fun MethodRow(title: String, selected: Boolean, subtitle: String? = null, onClick: () -> Unit) {
    MlPressable(onClick, Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                MlText(title, ml.type.bodyTitle)
                if (subtitle != null) MlText(subtitle, ml.type.metaSm, color = ml.colors.textMuted)
            }
            Box(
                Modifier
                    .size(22.dp)
                    .clip(MlShape.Pill)
                    .background(if (selected) ml.colors.accent else Color.Transparent)
                    .border(1.5.dp, if (selected) ml.colors.accent else ml.colors.textMuted, MlShape.Pill),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) MlIcon(MlIcons.Check, size = 13.dp, tint = ml.colors.textOnAccent, strokeWidth = 3f)
            }
        }
    }
}

@Composable
internal fun periodLabel(days: Int): String = stringResource(
    when (days) {
        90 -> R.string.plans_period_90
        180 -> R.string.plans_period_180
        365 -> R.string.plans_period_365
        else -> R.string.plans_period_30
    },
)

internal fun fee(percent: Double): String =
    if (percent == Math.floor(percent)) percent.toLong().toString() else percent.toString()
