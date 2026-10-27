package vpn.moonlight.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import vpn.moonlight.design.MlIcon
import vpn.moonlight.design.MlIconSpec
import vpn.moonlight.design.MlIcons
import vpn.moonlight.design.MlShape
import vpn.moonlight.design.ml

/**
 * The cabinet's input: a label above, and a field set into the page — the
 * sunken white with a light edge — with an optional glyph in front. A password
 * field carries an eye that shows what was typed.
 */
@Composable
fun MlTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    icon: MlIconSpec? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    password: Boolean = false,
    enabled: Boolean = true,
    isError: Boolean = false,
    textStyle: TextStyle = ml.type.body,
) {
    var revealed by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (label != null) MlText(label, ml.type.bodySmTitle, color = ml.colors.text2)
        Row(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(MlShape.Input)
                .background(ml.colors.surface2)
                .border(1.dp, if (isError) ml.colors.danger else ml.colors.hairline, MlShape.Input)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) MlIcon(icon, size = 19.dp, tint = ml.colors.textMuted)
            Box(Modifier.weight(1f)) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    enabled = enabled,
                    textStyle = textStyle.copy(color = ml.colors.text),
                    cursorBrush = SolidColor(ml.colors.text),
                    visualTransformation = if (password && !revealed) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (password) KeyboardType.Password else keyboardType,
                        imeAction = imeAction,
                        autoCorrectEnabled = false,
                    ),
                    keyboardActions = KeyboardActions(onAny = { onImeAction() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (value.isEmpty()) MlText(placeholder, textStyle, color = ml.colors.textMuted, maxLines = 1)
            }
            if (password) {
                MlIconButton(
                    icon = MlIcons.Eye,
                    onClick = { revealed = !revealed },
                    size = 34.dp,
                    iconSize = 18.dp,
                    background = androidx.compose.ui.graphics.Color.Transparent,
                    tint = if (revealed) ml.colors.text else ml.colors.textMuted,
                )
            }
        }
    }
}
