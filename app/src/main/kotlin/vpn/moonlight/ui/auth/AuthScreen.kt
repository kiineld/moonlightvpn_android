package vpn.moonlight.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vpn.moonlight.R
import vpn.moonlight.design.MlIcon
import vpn.moonlight.design.MlIconSpec
import vpn.moonlight.design.MlIcons
import vpn.moonlight.design.MlShape
import vpn.moonlight.design.components.MlIconTile
import vpn.moonlight.design.components.MlLogo
import vpn.moonlight.design.components.MlPrimaryButton
import vpn.moonlight.design.components.MlSecondaryButton
import vpn.moonlight.design.components.MlText
import vpn.moonlight.design.components.MlTextField
import vpn.moonlight.design.ml
import vpn.moonlight.ui.common.openUrl

/**
 * Sign-in, laid out as the cabinet's: the mark, a greeting, Telegram first, then
 * email and password. One screen with four states — sign in, sign up, the
 * emailed code, and the password reset — so a person never loses their typing
 * moving between them.
 */
@Composable
fun AuthScreen(viewModel: AuthViewModel, language: String) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 440.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                MlLogo(size = 64.dp, cornerRadius = 18.dp)
                Spacer(Modifier.height(22.dp))
                AnimatedContent(
                    targetState = state.mode,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "authMode",
                ) { mode ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        when (mode) {
                            AuthMode.SignIn, AuthMode.SignUp -> Credentials(state, viewModel)
                            AuthMode.Code -> CodeStep(state, viewModel)
                            AuthMode.Forgot -> ForgotStep(state, viewModel)
                        }
                    }
                }
                if (state.mode == AuthMode.SignIn) {
                    Spacer(Modifier.height(28.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(ml.colors.hairlineSoft))
                    Spacer(Modifier.height(18.dp))
                    Features()
                }
            }
        }

        state.telegram?.let { challenge ->
            TelegramLoginSheet(
                challenge = challenge,
                language = language,
                onToken = viewModel::completeTelegram,
                onFailed = viewModel::telegramFailed,
                onClose = viewModel::cancelTelegram,
            )
        }
    }
}

@Composable
private fun Heading(title: String, subtitle: String) {
    MlText(title, ml.type.title, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    MlText(subtitle, ml.type.body, color = ml.colors.text2, textAlign = TextAlign.Center)
    Spacer(Modifier.height(26.dp))
}

@Composable
private fun Credentials(state: AuthUiState, viewModel: AuthViewModel) {
    val signUp = state.mode == AuthMode.SignUp
    Heading(
        stringResource(if (signUp) R.string.auth_create else R.string.auth_welcome),
        stringResource(if (signUp) R.string.auth_create_sub else R.string.auth_welcome_sub),
    )

    MlSecondaryButton(
        label = stringResource(R.string.auth_telegram),
        onClick = viewModel::startTelegram,
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.busy,
        leadingIcon = MlIcons.Send,
        contentColor = ml.colors.text,
    )
    OrDivider()

    MlTextField(
        value = state.email,
        onValueChange = viewModel::setEmail,
        label = stringResource(R.string.auth_email),
        placeholder = "you@example.com",
        icon = MlIcons.Mail,
        keyboardType = KeyboardType.Email,
        enabled = !state.busy,
    )
    Spacer(Modifier.height(16.dp))
    MlTextField(
        value = state.password,
        onValueChange = viewModel::setPassword,
        label = stringResource(R.string.auth_password),
        placeholder = stringResource(R.string.auth_password_placeholder),
        icon = MlIcons.Lock,
        password = true,
        imeAction = ImeAction.Done,
        onImeAction = viewModel::submit,
        enabled = !state.busy,
    )

    if (signUp) {
        Spacer(Modifier.height(16.dp))
        val terms = state.legal.firstOrNull { it.key == "offer" } ?: state.legal.firstOrNull { it.key == "terms" }
        Consent(stringResource(R.string.auth_consent_terms), state.acceptTerms, viewModel::setAcceptTerms, terms?.url)
        Spacer(Modifier.height(10.dp))
        Consent(
            stringResource(R.string.auth_consent_data),
            state.acceptData,
            viewModel::setAcceptData,
            state.legal.firstOrNull { it.key == "consent" }?.url,
        )
    } else {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            LinkText(stringResource(R.string.auth_forgot)) { viewModel.setMode(AuthMode.Forgot) }
        }
    }

    Messages(state)
    Spacer(Modifier.height(20.dp))
    MlPrimaryButton(
        label = stringResource(if (signUp) R.string.auth_sign_up else R.string.auth_sign_in),
        onClick = viewModel::submit,
        enabled = state.canSubmit,
    )
    Spacer(Modifier.height(18.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MlText(
            stringResource(if (signUp) R.string.auth_have_account else R.string.auth_no_account),
            ml.type.bodySm,
            color = ml.colors.textMuted,
        )
        LinkText(stringResource(if (signUp) R.string.auth_sign_in else R.string.auth_sign_up)) {
            viewModel.setMode(if (signUp) AuthMode.SignIn else AuthMode.SignUp)
        }
    }
}

@Composable
private fun CodeStep(state: AuthUiState, viewModel: AuthViewModel) {
    Heading(stringResource(R.string.auth_code_title), stringResource(R.string.auth_code_sub, state.email.trim()))
    MlTextField(
        value = state.code,
        onValueChange = viewModel::setCode,
        label = stringResource(R.string.auth_code),
        placeholder = "000000",
        icon = MlIcons.Lock,
        keyboardType = KeyboardType.NumberPassword,
        imeAction = ImeAction.Done,
        onImeAction = viewModel::submit,
        enabled = !state.busy,
        textStyle = ml.type.mono,
    )
    Messages(state)
    Spacer(Modifier.height(20.dp))
    MlPrimaryButton(stringResource(R.string.auth_code_confirm), viewModel::submit, enabled = state.canSubmit)
    Spacer(Modifier.height(18.dp))
    LinkText(stringResource(R.string.auth_code_resend), viewModel::resendCode)
    Spacer(Modifier.height(14.dp))
    LinkText(stringResource(R.string.auth_back_to_login)) { viewModel.setMode(AuthMode.SignIn) }
}

@Composable
private fun ForgotStep(state: AuthUiState, viewModel: AuthViewModel) {
    Heading(stringResource(R.string.auth_forgot_title), stringResource(R.string.auth_forgot_sub))
    MlTextField(
        value = state.email,
        onValueChange = viewModel::setEmail,
        label = stringResource(R.string.auth_email),
        placeholder = "you@example.com",
        icon = MlIcons.Mail,
        keyboardType = KeyboardType.Email,
        imeAction = ImeAction.Done,
        onImeAction = viewModel::submit,
        enabled = !state.busy,
    )
    Messages(state)
    Spacer(Modifier.height(20.dp))
    MlPrimaryButton(stringResource(R.string.auth_forgot_send), viewModel::submit, enabled = state.canSubmit)
    Spacer(Modifier.height(18.dp))
    LinkText(stringResource(R.string.auth_back_to_login)) { viewModel.setMode(AuthMode.SignIn) }
}

@Composable
private fun Messages(state: AuthUiState) {
    state.error?.let {
        Spacer(Modifier.height(14.dp))
        MlText(it.resolve(), ml.type.bodySm, color = ml.colors.danger, textAlign = TextAlign.Center)
    }
    state.notice?.let {
        Spacer(Modifier.height(14.dp))
        MlText(it.resolve(), ml.type.bodySm, color = ml.colors.success, textAlign = TextAlign.Center)
    }
}

@Composable
private fun OrDivider() {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(ml.colors.hairlineSoft))
        MlText(stringResource(R.string.auth_or), ml.type.meta, color = ml.colors.textMuted)
        Box(Modifier.weight(1f).height(1.dp).background(ml.colors.hairlineSoft))
    }
}

/** The cabinet's text link: ink, with a yellow stroke under it. */
@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    val line = ml.colors.highlightFill
    MlText(
        text,
        ml.type.bodySmTitle,
        Modifier
            .clip(MlShape.IconTile)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .drawBehind {
                val y = size.height - 1.dp.toPx()
                drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
            },
    )
}

@Composable
private fun Consent(text: String, checked: Boolean, onChange: (Boolean) -> Unit, documentUrl: String?) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MlShape.Row)
            .clickable(role = Role.Checkbox) { onChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(CheckboxShape)
                .background(if (checked) ml.colors.highlight else ml.colors.surface2)
                .border(1.dp, if (checked) ml.colors.highlightFill else ml.colors.textMuted.copy(alpha = 0.4f), CheckboxShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) MlIcon(MlIcons.Check, size = 15.dp, tint = ml.colors.onHighlight, strokeWidth = 3f)
        }
        MlText(text, ml.type.bodySm, Modifier.weight(1f), color = ml.colors.text2)
        if (documentUrl != null) {
            MlIcon(
                MlIcons.ExternalLink,
                Modifier.clip(MlShape.Pill).clickable { context.openUrl(documentUrl) }.padding(6.dp),
                size = 16.dp,
                tint = ml.colors.textMuted,
            )
        }
    }
}

private val CheckboxShape = androidx.compose.foundation.shape.RoundedCornerShape(7.dp)

@Composable
private fun Features() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Feature(MlIcons.Zap, R.string.auth_feature_speed, R.string.auth_feature_speed_sub)
        Feature(MlIcons.ShieldCheck, R.string.auth_feature_privacy, R.string.auth_feature_privacy_sub)
        Feature(MlIcons.Globe, R.string.auth_feature_servers, R.string.auth_feature_servers_sub)
    }
}

@Composable
private fun Feature(icon: MlIconSpec, title: Int, subtitle: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        MlIconTile(ml.colors.surface, size = 40.dp) { MlIcon(icon, size = 18.dp, tint = ml.colors.text) }
        Column {
            MlText(stringResource(title), ml.type.bodyTitle)
            MlText(stringResource(subtitle), ml.type.metaSm, color = ml.colors.textMuted)
        }
    }
}
