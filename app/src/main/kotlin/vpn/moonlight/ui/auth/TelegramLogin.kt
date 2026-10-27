package vpn.moonlight.ui.auth

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import vpn.moonlight.BuildConfig
import vpn.moonlight.R
import vpn.moonlight.data.logging.MoonlightLog
import vpn.moonlight.data.model.TelegramChallenge
import vpn.moonlight.design.MlIcons
import vpn.moonlight.design.MlShape
import vpn.moonlight.design.components.MlIconButton
import vpn.moonlight.design.components.MlPage
import vpn.moonlight.design.components.MlText
import vpn.moonlight.design.ml
import vpn.moonlight.ui.common.openUrl

private const val TELEGRAM_ORIGIN = "https://oauth.telegram.org"
private const val BRIDGE = "MoonlightTelegram"

/**
 * Telegram's official OAuth page — the same one the cabinet's "Войти через
 * Telegram" opens as a popup.
 *
 * That page hands its answer to the window that opened it, with
 * `opener.postMessage`. Here there is no opener, so before any of the page's own
 * script runs a stand-in is installed that forwards the message to the app, and
 * only on Telegram's origin. The page is opened for the cabinet's origin, which
 * is the one the bot allows in BotFather, so the ID token it returns is the same
 * kind the backend already verifies for the website.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TelegramLoginSheet(
    challenge: TelegramChallenge,
    language: String,
    onToken: (idToken: String?, widget: JsonObject?) -> Unit,
    onFailed: () -> Unit,
    onClose: () -> Unit,
) {
    val token by rememberUpdatedState(onToken)
    val failed by rememberUpdatedState(onFailed)
    val close by rememberUpdatedState(onClose)
    BackHandler(onBack = onClose)

    val supported = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) &&
        WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
    // shortcut: no fallback for a WebView older than 83 (2020); such a phone signs in with email.
    LaunchedEffect(supported) { if (!supported) failed() }
    if (!supported) return

    MlPage {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MlIconButton(MlIcons.X, onClose, label = stringResource(R.string.action_back))
                MlText(stringResource(R.string.auth_telegram), ml.type.titleSm)
            }
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .clip(MlShape.Card),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        WebViewCompat.addDocumentStartJavaScript(this, OPENER_SHIM, setOf(TELEGRAM_ORIGIN))
                        WebViewCompat.addWebMessageListener(this, BRIDGE, setOf(TELEGRAM_ORIGIN)) { _, message, _, _, _ ->
                            when (val answer = parse(message.data)) {
                                is Answer.Token -> token(answer.idToken, null)
                                is Answer.Widget -> token(null, answer.fields)
                                Answer.Failed -> failed()
                                Answer.Closed -> close()
                                null -> Unit
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val url = request.url
                                if (url.scheme == "https" && url.host == Uri.parse(TELEGRAM_ORIGIN).host) return false
                                // "Open Telegram" and t.me links belong to the Telegram app.
                                context.openUrl(url.toString())
                                return true
                            }
                        }
                        loadUrl(authUrl(challenge, language))
                    }
                },
                onRelease = { it.destroy() },
            )
        }
    }
}

/** The URL the cabinet's Telegram Login SDK opens, with the origin it adds. */
private fun authUrl(challenge: TelegramChallenge, language: String): String {
    val origin = BuildConfig.CABINET_URL.trimEnd('/')
    return Uri.parse("$TELEGRAM_ORIGIN/auth").buildUpon()
        .appendQueryParameter("response_type", "post_message")
        .appendQueryParameter("client_id", challenge.clientId)
        .appendQueryParameter("redirect_uri", "$origin/login")
        .appendQueryParameter("origin", origin)
        .appendQueryParameter("scope", "openid profile")
        .appendQueryParameter("nonce", challenge.nonce)
        .appendQueryParameter("lang", language)
        .build()
        .toString()
}

/** Stands in for `window.opener`, and for `window.close()`, before the page's scripts run. */
private val OPENER_SHIM = """
    (function () {
      var send = function (m) { $BRIDGE.postMessage(typeof m === 'string' ? m : JSON.stringify(m)); };
      var opener = { postMessage: send, closed: false, focus: function () {}, close: function () {} };
      try { Object.defineProperty(window, 'opener', { get: function () { return opener; }, configurable: true }); }
      catch (e) { window.opener = opener; }
      window.close = function () { send('{"event":"close"}'); };
    })();
""".trimIndent()

private sealed interface Answer {
    data class Token(val idToken: String) : Answer
    data class Widget(val fields: JsonObject) : Answer
    data object Failed : Answer
    data object Closed : Answer
}

private val json = Json { ignoreUnknownKeys = true }

/**
 * `{"event":"auth_result","result":"<id token>"}` on success — or, from some
 * Telegram versions, the classic Login Widget fields in `result`, which the
 * backend also verifies — and `{"event":"auth_result","error":…}` otherwise.
 */
private fun parse(data: String?): Answer? {
    val message = runCatching { json.parseToJsonElement(data.orEmpty()) as JsonObject }.getOrNull() ?: return null
    return when ((message["event"] as? JsonPrimitive)?.contentOrNull) {
        "close" -> Answer.Closed
        "auth_result" -> {
            val result = message["result"]
            when {
                result is JsonPrimitive && result.isString && result.content.count { it == '.' } == 2 ->
                    Answer.Token(result.content)
                result is JsonObject && result["hash"] != null && result["id"] != null -> Answer.Widget(result)
                else -> {
                    MoonlightLog.w("TelegramLogin", "no token in the answer: ${message["error"] ?: "unknown"}")
                    Answer.Failed
                }
            }
        }
        else -> null
    }
}
