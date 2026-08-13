package vpn.moonlight.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import vpn.moonlight.R
import vpn.moonlight.design.MlIcon
import vpn.moonlight.design.MlIconSpec
import vpn.moonlight.design.MlIcons
import vpn.moonlight.design.MlShape
import vpn.moonlight.design.components.MlGhostButton
import vpn.moonlight.design.components.MlIconTile
import vpn.moonlight.design.components.MlLogo
import vpn.moonlight.design.components.MlPrimaryButton
import vpn.moonlight.design.components.MlText
import vpn.moonlight.design.ml

@Composable
fun OnboardingScreen(
    onAddSubscription: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 56.dp, bottom = 32.dp),
    ) {
        MlLogo(size = 64.dp, cornerRadius = 20.dp)

        Spacer(Modifier.height(26.dp))
        MlText(stringResource(R.string.onboarding_title), ml.type.plan)

        Spacer(Modifier.height(10.dp))
        MlText(
            stringResource(R.string.onboarding_subtitle),
            ml.type.body,
            Modifier.widthIn(max = 300.dp),
            color = ml.colors.text2,
        )

        Spacer(Modifier.height(30.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FeatureRow(
                icon = MlIcons.Zap,
                fill = ml.colors.cat1,
                title = stringResource(R.string.onboarding_feature_tap_title),
                subtitle = stringResource(R.string.onboarding_feature_tap_sub),
            )
            FeatureRow(
                icon = MlIcons.Smartphone,
                fill = ml.colors.cat2,
                title = stringResource(R.string.onboarding_feature_devices_title),
                subtitle = stringResource(R.string.onboarding_feature_devices_sub),
            )
            FeatureRow(
                icon = MlIcons.Lock,
                fill = ml.colors.cat3,
                title = stringResource(R.string.onboarding_feature_reality_title),
                subtitle = stringResource(R.string.onboarding_feature_reality_sub),
            )
        }

        Spacer(Modifier.weight(1f))

        MlPrimaryButton(
            label = stringResource(R.string.onboarding_add_subscription),
            onClick = onAddSubscription,
        )
        Spacer(Modifier.height(10.dp))
        MlGhostButton(stringResource(R.string.onboarding_later), onSkip)
    }
}

@Composable
private fun FeatureRow(
    icon: MlIconSpec,
    fill: Color,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MlIconTile(fill, size = 44.dp, shape = MlShape.IconTileLg) {
            MlIcon(icon, size = 20.dp, tint = ml.colors.textOnAccent)
        }
        Column(Modifier.weight(1f)) {
            MlText(title, ml.type.bodyEmphatic)
            Spacer(Modifier.height(2.dp))
            MlText(subtitle, ml.type.meta, color = ml.colors.textMuted)
        }
    }
}
