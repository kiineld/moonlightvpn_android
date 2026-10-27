package vpn.moonlight.ui.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import vpn.moonlight.R
import vpn.moonlight.data.model.Transaction
import vpn.moonlight.design.MlIcons
import vpn.moonlight.design.MlShape
import vpn.moonlight.design.components.MlCard
import vpn.moonlight.design.components.MlDivider
import vpn.moonlight.design.components.MlOverline
import vpn.moonlight.design.components.MlPillButton
import vpn.moonlight.design.components.MlPrimaryButton
import vpn.moonlight.design.components.MlSecondaryButton
import vpn.moonlight.design.components.MlSegmented
import vpn.moonlight.design.components.MlText
import vpn.moonlight.design.components.MlTextField
import vpn.moonlight.design.ml
import vpn.moonlight.ui.common.currentLocale
import vpn.moonlight.ui.common.openUrl
import vpn.moonlight.ui.common.toast
import vpn.moonlight.ui.plans.fee

/** The balance, topping it up through the payment providers, promo codes, and the history. */
@Composable
fun WalletScreen(viewModel: WalletViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val opened = stringResource(R.string.wallet_payment_opened)

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    LaunchedEffect(state.paymentUrl) {
        val url = state.paymentUrl ?: return@LaunchedEffect
        context.openUrl(url)
        context.toast(opened)
        viewModel.paymentOpened()
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
        MlText(stringResource(R.string.nav_wallet), ml.type.title)

        MlCard(Modifier.fillMaxWidth(), shape = MlShape.HeroCard, background = ml.colors.highlight) {
            Column(Modifier.padding(22.dp)) {
                MlText(stringResource(R.string.wallet_balance), ml.type.bodySm, color = ml.colors.onHighlight.copy(alpha = 0.7f))
                Spacer(Modifier.height(6.dp))
                MlText(state.account?.balanceFormatted?.ifBlank { null } ?: "—", ml.type.hero, color = ml.colors.onHighlight)
            }
        }

        MlOverline(stringResource(R.string.wallet_topup))
        MlCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                MlTextField(
                    value = state.amount,
                    onValueChange = viewModel::setAmount,
                    label = stringResource(R.string.wallet_amount),
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                    onImeAction = viewModel::topUp,
                    isError = state.amount.isNotEmpty() && state.amountKopecks == null,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(300, 500, 1000, 2000).forEach { preset ->
                        MlPillButton(
                            onClick = { viewModel.setAmount(preset.toString()) },
                            background = if (state.amount == preset.toString()) ml.colors.accent else ml.colors.surface2,
                            contentColor = if (state.amount == preset.toString()) ml.colors.textOnAccent else ml.colors.text,
                        ) { MlText("$preset ₽", ml.type.bodySmTitle, color = androidx.compose.material3.LocalContentColor.current) }
                    }
                }
                MlText(stringResource(R.string.wallet_amount_hint), ml.type.metaSm, color = ml.colors.textMuted)

                val methods = state.account?.paymentMethods.orEmpty()
                if (methods.isEmpty()) {
                    MlText(stringResource(R.string.wallet_no_methods), ml.type.bodySm, color = ml.colors.warning)
                } else if (methods.size > 1) {
                    MlSegmented(
                        options = methods.map { it.name.ifBlank { it.id } },
                        selectedIndex = methods.indexOfFirst { it.id == state.selectedMethod }.coerceAtLeast(0),
                        onSelect = { viewModel.setMethod(methods[it].id) },
                        segmentHeight = 40.dp,
                    )
                }
                methods.firstOrNull { it.id == state.selectedMethod }?.takeIf { it.feePercent > 0 }?.let {
                    MlText(stringResource(R.string.wallet_fee, fee(it.feePercent)), ml.type.metaSm, color = ml.colors.textMuted)
                }
                state.error?.let { MlText(it.resolve(), ml.type.bodySm, color = ml.colors.danger) }
                MlPrimaryButton(
                    label = stringResource(R.string.wallet_pay, state.amountKopecks?.let { "${it / 100} ₽" } ?: "—"),
                    onClick = viewModel::topUp,
                    enabled = state.canTopUp,
                    leadingIcon = MlIcons.CreditCard,
                )
            }
        }

        MlOverline(stringResource(R.string.wallet_promo))
        MlCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MlTextField(
                    value = state.promo,
                    onValueChange = viewModel::setPromo,
                    modifier = Modifier.weight(1f),
                    placeholder = "MOON2026",
                    icon = MlIcons.Tag,
                    imeAction = ImeAction.Done,
                    onImeAction = viewModel::applyPromo,
                )
                MlSecondaryButton(
                    stringResource(R.string.wallet_promo_apply),
                    viewModel::applyPromo,
                    enabled = state.promo.isNotEmpty() && !state.busy,
                )
            }
        }

        MlOverline(stringResource(R.string.wallet_history))
        MlCard(Modifier.fillMaxWidth()) {
            if (state.transactions.isEmpty()) {
                MlText(
                    stringResource(R.string.wallet_history_empty),
                    ml.type.bodySm,
                    Modifier.padding(18.dp),
                    color = ml.colors.textMuted,
                )
            } else {
                Column {
                    state.transactions.take(50).forEachIndexed { index, item ->
                        if (index > 0) MlDivider()
                        TransactionRow(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(item: Transaction) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            MlText(
                item.description.ifBlank { item.type },
                ml.type.bodySmTitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            MlText(dateTime(item.createdAt), ml.type.metaSm, color = ml.colors.textMuted)
        }
        val credit = item.amount > 0
        MlText(
            (if (credit) "+" else "") + item.amountFormatted,
            ml.type.bodySmEmphatic,
            color = if (credit) ml.colors.success else ml.colors.text,
        )
    }
}

@Composable
private fun dateTime(iso: String): String {
    val locale = currentLocale()
    return runCatching {
        Instant.parse(iso).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", locale))
    }.getOrDefault(iso)
}
