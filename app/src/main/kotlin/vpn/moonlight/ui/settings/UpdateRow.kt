package vpn.moonlight.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.io.File
import vpn.moonlight.R
import vpn.moonlight.data.remote.AppRelease
import vpn.moonlight.data.remote.UpdateError
import vpn.moonlight.data.repository.UpdateState
import vpn.moonlight.design.MlIcons
import vpn.moonlight.design.components.MlNavRow
import vpn.moonlight.design.components.MlProgressBar
import vpn.moonlight.design.components.MlText
import vpn.moonlight.design.ml
import vpn.moonlight.ui.common.byteText

/**
 * The "check for updates" row.
 *
 * The whole row is the tap target and its action follows the state — check,
 * download, install — rather than nesting a button inside a clickable row,
 * which would put two overlapping targets on one intent.
 */
@Composable
fun UpdateRow(
    state: UpdateState,
    currentVersion: String,
    onCheck: () -> Unit,
    onDownload: (AppRelease) -> Unit,
    onInstall: (File) -> Unit,
) {
    val label = actionLabel(state)
    val trailing: (@Composable RowScope.() -> Unit)? = label?.let {
        { MlText(it, ml.type.metaSm, color = ml.colors.accentInk) }
    }

    // Kept across the collapse so the bar holds its last width while it animates
    // out, rather than snapping back to empty on the frame the state changes.
    var lastFraction by remember { mutableFloatStateOf(0f) }
    if (state is UpdateState.Downloading) lastFraction = state.fraction

    Column {
        MlNavRow(
            title = stringResource(R.string.update_title),
            subtitle = updateSubtitle(state, currentVersion),
            onClick = when (state) {
                is UpdateState.Available -> {
                    { onDownload(state.release) }
                }
                is UpdateState.Ready -> {
                    { onInstall(state.file) }
                }
                UpdateState.Checking, is UpdateState.Downloading -> null
                else -> onCheck
            },
            icon = MlIcons.RefreshCw,
            tileFill = if (state is UpdateState.Failed) ml.colors.dangerQuiet else ml.colors.cat5,
            trailing = trailing,
        )

        AnimatedVisibility(
            visible = state is UpdateState.Downloading,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            MlProgressBar(
                fraction = lastFraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                height = 6.dp,
                color = ml.colors.accent,
            )
        }
    }
}

@Composable
private fun updateSubtitle(state: UpdateState, currentVersion: String): String = when (state) {
    UpdateState.Idle -> stringResource(R.string.update_idle, currentVersion)
    UpdateState.Checking -> stringResource(R.string.update_checking)
    UpdateState.UpToDate -> stringResource(R.string.update_current, currentVersion)
    is UpdateState.Available -> stringResource(
        R.string.update_available,
        state.release.versionName,
        byteText(state.release.asset.sizeBytes),
    )
    is UpdateState.Downloading -> stringResource(
        R.string.update_downloading,
        (state.fraction * 100).toInt(),
    )
    is UpdateState.Ready -> stringResource(R.string.update_ready, state.release.versionName)
    is UpdateState.Failed -> errorText(state.error)
}

@Composable
private fun errorText(error: UpdateError): String = stringResource(
    when (error) {
        UpdateError.NoRelease -> R.string.update_error_no_release
        UpdateError.Storage -> R.string.update_error_storage
        UpdateError.SignatureMismatch -> R.string.update_error_signature
        UpdateError.Network, is UpdateError.Http -> R.string.update_error_network
    },
)

/** The trailing hint, and null where a tap would do nothing. */
@Composable
private fun actionLabel(state: UpdateState): String? = when (state) {
    is UpdateState.Available -> stringResource(R.string.update_action_download)
    is UpdateState.Ready -> stringResource(R.string.update_action_install)
    is UpdateState.Failed -> stringResource(R.string.update_action_retry)
    UpdateState.Idle, UpdateState.UpToDate -> stringResource(R.string.update_action_check)
    UpdateState.Checking, is UpdateState.Downloading -> null
}
