package vpn.moonlight.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.os.ConfigurationCompat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import vpn.moonlight.R
import vpn.moonlight.data.model.ConnectionError
import vpn.moonlight.data.model.Latency
import vpn.moonlight.data.model.ServerNode
import vpn.moonlight.data.util.ByteUnit
import vpn.moonlight.data.util.Formatters

/**
 * The locale the UI is currently rendered in, which drives number and date shapes.
 *
 * Read from the Configuration rather than `Locale.getDefault()`: only the former
 * is observable, so switching language in Settings actually recomposes the
 * formatted numbers and dates.
 */
@Composable
@ReadOnlyComposable
fun currentLocale(): Locale =
    ConfigurationCompat.getLocales(LocalConfiguration.current).get(0) ?: Locale.ENGLISH

@Composable
@ReadOnlyComposable
private fun unitLabel(unit: ByteUnit): String = stringResource(
    when (unit) {
        ByteUnit.Bytes -> R.string.unit_bytes
        ByteUnit.Kilobytes -> R.string.unit_kilobytes
        ByteUnit.Megabytes -> R.string.unit_megabytes
        ByteUnit.Gigabytes -> R.string.unit_gigabytes
        ByteUnit.Terabytes -> R.string.unit_terabytes
    },
)

/** "24,8 ГБ" — number formatted for the locale, unit from resources. */
@Composable
@ReadOnlyComposable
fun byteText(bytes: Long): String {
    val size = Formatters.formatSize(bytes, currentLocale())
    return "${size.text} ${unitLabel(size.unit)}"
}

/** Just the number, for pairing with a shared unit as in "24,8 из 100 ГБ". */
@Composable
@ReadOnlyComposable
fun byteNumber(bytes: Long): String = Formatters.formatSize(bytes, currentLocale()).text

@Composable
@ReadOnlyComposable
fun daysText(days: Int): String = pluralStringResource(R.plurals.days_remaining, days, days)

@Composable
@ReadOnlyComposable
fun appsCountText(count: Int): String = pluralStringResource(R.plurals.apps_selected, count, count)

@Composable
@ReadOnlyComposable
fun dateText(epochSeconds: Long): String {
    val formatter = DateTimeFormatter
        .ofLocalizedDate(FormatStyle.LONG)
        .withLocale(currentLocale())
    return runCatching {
        Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).format(formatter)
    }.getOrDefault("—")
}

/**
 * The flag shown for a node: the emoji from its remark when the panel supplied
 * one, otherwise derived from the country code so a node is never blank.
 */
fun ServerNode.flagOrDerived(): String? =
    flag ?: countryCode?.let { code ->
        if (code.length != 2) return@let null
        code.uppercase()
            .map { Character.toChars(0x1F1E6 + (it - 'A')).concatToString() }
            .joinToString("")
    }

/** Localised country name from the node's ISO code, e.g. "Нидерланды". */
@Composable
@ReadOnlyComposable
fun ServerNode.countryName(): String? {
    val code = countryCode ?: return null
    val locale = currentLocale()
    return runCatching {
        Locale.Builder().setRegion(code).build().getDisplayCountry(locale).takeIf { it != code }
    }.getOrNull()
}

/** The row sub-line: "Нидерланды · VLESS Reality · 24 ms". */
@Composable
@ReadOnlyComposable
fun ServerNode.subtitleText(latency: Latency): String = buildList {
    countryName()?.let { add(it) }
    squad?.let { if (it != countryName()) add(it) }
    add(protocolLabel)
    when (latency) {
        Latency.Measuring -> add(stringResource(R.string.connect_measuring))
        is Latency.Value -> add(stringResource(R.string.connect_latency_ms, latency.ms))
        Latency.Unknown, is Latency.Failed -> Unit
    }
}.joinToString(" · ")

@Composable
@ReadOnlyComposable
fun latencyText(latency: Latency): String = when (latency) {
    Latency.Unknown -> "—"
    Latency.Measuring -> "…"
    is Latency.Value -> stringResource(R.string.connect_latency_ms, latency.ms)
    is Latency.Failed -> "—"
}

@Composable
@ReadOnlyComposable
fun errorText(error: ConnectionError): String = stringResource(
    when (error) {
        ConnectionError.NoSubscription -> R.string.error_no_subscription
        ConnectionError.NoNodes -> R.string.error_no_nodes
        ConnectionError.PermissionDenied -> R.string.error_permission_denied
        ConnectionError.SubscriptionExpired -> R.string.error_subscription_expired
        ConnectionError.TrafficExhausted -> R.string.error_traffic_exhausted
        ConnectionError.DeviceLimitReached -> R.string.error_device_limit
        ConnectionError.CoreStartFailed -> R.string.error_core_start_failed
        ConnectionError.TunnelStartFailed -> R.string.error_tunnel_start_failed
        ConnectionError.Network -> R.string.error_network
        ConnectionError.Unknown -> R.string.error_unknown
    },
)
