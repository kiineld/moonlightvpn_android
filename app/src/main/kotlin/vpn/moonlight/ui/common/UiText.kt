package vpn.moonlight.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import vpn.moonlight.R
import vpn.moonlight.data.remote.AccountApiException

/** A message for the screen: the server's own words, or one of ours. */
sealed interface UiText {
    data class Server(val text: String) : UiText
    data class Res(@StringRes val id: Int) : UiText

    @Composable
    fun resolve(): String = when (this) {
        is Server -> text
        is Res -> stringResource(id)
    }
}

/** The server words its errors for people; a failure to reach it gets ours. */
fun Throwable.toUiText(): UiText = when {
    this is AccountApiException && status != 0 -> UiText.Server(message.orEmpty())
    else -> UiText.Res(R.string.auth_error_network)
}
