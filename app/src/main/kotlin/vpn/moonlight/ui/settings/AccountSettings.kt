package vpn.moonlight.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import vpn.moonlight.R
import vpn.moonlight.data.model.Account
import vpn.moonlight.data.util.DnsServers
import vpn.moonlight.design.MlIcon
import vpn.moonlight.design.MlIcons
import vpn.moonlight.design.components.MlCard
import vpn.moonlight.design.components.MlDivider
import vpn.moonlight.design.components.MlExternalMark
import vpn.moonlight.design.components.MlIconTile
import vpn.moonlight.design.components.MlNavRow
import vpn.moonlight.design.components.MlPillButton
import vpn.moonlight.design.components.MlPrimaryButton
import vpn.moonlight.design.components.MlSegmented
import vpn.moonlight.design.components.MlText
import vpn.moonlight.design.components.MlTextField
import vpn.moonlight.design.ml

@Composable
internal fun AccountCard(account: Account?, onCabinet: () -> Unit, onSignOut: () -> Unit) {
    MlCard {
        Column {
            MlNavRow(
                title = account?.displayName ?: "—",
                subtitle = listOfNotNull(
                    account?.email,
                    account?.takeIf { it.telegramLinked }?.let { "Telegram" + (it.username?.let { u -> " · @$u" } ?: "") },
                ).joinToString(" · ").ifEmpty { null },
                onClick = null,
                trailing = null,
                leading = {
                    MlIconTile(ml.colors.highlight) { MlIcon(MlIcons.User, size = 19.dp, tint = ml.colors.onHighlight) }
                },
            )
            MlDivider(insetStart = 72.dp)
            MlNavRow(
                title = stringResource(R.string.settings_cabinet),
                subtitle = stringResource(R.string.settings_cabinet_sub),
                onClick = onCabinet,
                icon = MlIcons.Globe,
                tileFill = ml.colors.cat1,
                trailing = { MlExternalMark() },
            )
            MlDivider(insetStart = 72.dp)
            MlNavRow(
                title = stringResource(R.string.settings_sign_out),
                onClick = onSignOut,
                trailing = null,
                leading = {
                    MlIconTile(ml.colors.dangerQuiet) { MlIcon(MlIcons.LogOut, size = 19.dp, tint = ml.colors.danger) }
                },
            )
        }
    }
}

/**
 * The DNS the tunnel answers apps with: the server config's own — which routes
 * each domain to the resolver that suits it — or the user's, in its place.
 */
@Composable
internal fun DnsSetting(current: List<String>?, onSave: (List<String>?) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    var custom by rememberSaveable(current) { mutableStateOf(current != null) }
    var text by rememberSaveable(current) { mutableStateOf(current?.joinToString(", ").orEmpty()) }
    val parsed = DnsServers.parse(text)

    MlNavRow(
        title = stringResource(R.string.settings_dns),
        subtitle = current?.joinToString(", ") ?: stringResource(R.string.settings_dns_config),
        onClick = { open = !open },
        icon = MlIcons.Server,
        tileFill = ml.colors.cat3,
    )
    AnimatedVisibility(open) {
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MlSegmented(
                options = listOf(stringResource(R.string.settings_dns_config_short), stringResource(R.string.settings_dns_custom)),
                selectedIndex = if (custom) 1 else 0,
                onSelect = { custom = it == 1 },
                segmentHeight = 36.dp,
            )
            if (custom) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { (name, address) ->
                        MlPillButton(onClick = { text = address }, background = ml.colors.surface2, height = 32.dp) {
                            MlText(name, ml.type.metaEmphatic, color = ml.colors.text)
                        }
                    }
                }
                MlTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = "1.1.1.1",
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                    isError = text.isNotBlank() && parsed == null,
                    textStyle = ml.type.monoSm,
                )
                MlText(
                    stringResource(if (text.isNotBlank() && parsed == null) R.string.settings_dns_invalid else R.string.settings_dns_custom_hint),
                    ml.type.metaSm,
                    color = if (text.isNotBlank() && parsed == null) ml.colors.danger else ml.colors.textMuted,
                )
            }
            MlText(stringResource(R.string.settings_dns_hint), ml.type.metaSm, color = ml.colors.textMuted)
            MlPrimaryButton(
                label = stringResource(R.string.settings_dns_save),
                onClick = {
                    onSave(if (custom) parsed else null)
                    open = false
                },
                enabled = !custom || parsed != null,
            )
        }
    }
}

private val presets = listOf(
    "Cloudflare" to "https://1.1.1.1/dns-query",
    "Google" to "https://8.8.8.8/dns-query",
    "Yandex" to "77.88.8.8",
    "AdGuard" to "https://dns.adguard-dns.com/dns-query",
    "Quad9" to "https://9.9.9.9/dns-query",
)
