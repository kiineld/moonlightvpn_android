package vpn.moonlight.ui.importsub

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vpn.moonlight.R
import vpn.moonlight.design.MlIcon
import vpn.moonlight.design.MlIcons
import vpn.moonlight.design.MlShape
import vpn.moonlight.design.components.MlIconButton
import vpn.moonlight.design.components.MlPressable
import vpn.moonlight.design.components.MlPrimaryButton
import vpn.moonlight.design.components.MlText
import vpn.moonlight.design.ml
import vpn.moonlight.ui.common.byteText
import vpn.moonlight.ui.common.serversCountText
import vpn.moonlight.ui.common.daysText

@Composable
fun ImportScreen(
    viewModel: ImportViewModel,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    initialUrl: String? = null,
    onInitialUrlConsumed: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Runs once per entry into composition, so the form is always fresh when the
    // screen is opened rather than carrying the previous import's result.
    LaunchedEffect(initialUrl) {
        viewModel.reset()
        if (initialUrl != null) {
            // The design promises a link from the bot "adds itself", so a deep
            // link submits rather than only filling the field.
            viewModel.submit(initialUrl)
            onInitialUrlConsumed()
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MlIconButton(MlIcons.ChevronLeft, onBack)
            MlText(stringResource(R.string.import_title), ml.type.titleSm)
        }

        if (state.isDone) {
            ImportSuccess(state, onDone)
        } else {
            ImportForm(state, viewModel)
        }
    }
}

@Composable
private fun ImportSuccess(state: ImportUiState, onConnect: () -> Unit) {
    val subscription = state.imported ?: return
    val info = subscription.userInfo

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(bottom = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(82.dp)
                .clip(CircleShape)
                .background(ml.colors.accent),
            contentAlignment = Alignment.Center,
        ) {
            MlIcon(MlIcons.Check, size = 38.dp, tint = ml.colors.textOnAccent, strokeWidth = 2.6f)
        }

        Spacer(Modifier.height(22.dp))
        MlText(
            stringResource(R.string.import_success_title),
            ml.type.title,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(6.dp))
        MlText(
            subscription.title ?: stringResource(R.string.subscription_plan_fallback),
            ml.type.lead,
            color = ml.colors.accentInk,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip(serversCountText(subscription.nodes.size))
            info?.totalBytes?.takeIf { it > 0 }?.let { InfoChip(byteText(it)) }
        }

        Spacer(Modifier.height(34.dp))
        MlPrimaryButton(
            label = stringResource(R.string.import_success_connect),
            onClick = onConnect,
            modifier = Modifier.widthIn(max = 300.dp),
        )
    }
}

@Composable
private fun InfoChip(text: String) {
    Box(
        Modifier
            .clip(MlShape.Pill)
            .background(ml.colors.surface2)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        MlText(text, ml.type.metaEmphatic, color = ml.colors.text2)
    }
}

@Composable
private fun ImportForm(state: ImportUiState, viewModel: ImportViewModel) {
    val context = LocalContext.current
    var hasCamera by remember { mutableStateOf(context.hasCameraPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCamera = granted }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 6.dp, bottom = 24.dp),
    ) {
        MlText(
            stringResource(R.string.import_hint),
            ml.type.bodySm,
            color = ml.colors.text2,
        )

        Spacer(Modifier.height(18.dp))
        ScannerFrame(
            hasCameraPermission = hasCamera,
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onCode = { viewModel.submit(it) },
        )

        Spacer(Modifier.height(18.dp))
        MlPrimaryButton(
            label = stringResource(R.string.import_paste),
            onClick = { viewModel.submitFromClipboard(context.clipboardText()) },
            enabled = !state.isSubmitting,
            leadingIcon = MlIcons.Link2,
        )

        Spacer(Modifier.height(18.dp))
        LabelledDivider(stringResource(R.string.import_or_manually))

        Spacer(Modifier.height(12.dp))
        UrlField(state, viewModel)

        state.errorRes?.let { errorRes ->
            Spacer(Modifier.height(10.dp))
            MlText(stringResource(errorRes), ml.type.meta, color = ml.colors.danger)
        }
    }
}

/**
 * The scan target: a dark square with accent corner brackets and a sweeping line,
 * holding the live camera preview once permission is granted.
 */
@Composable
private fun ScannerFrame(
    hasCameraPermission: Boolean,
    onRequestPermission: () -> Unit,
    onCode: (String) -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(MlShape.Scanner)
            .background(ml.colors.bgDeep)
            .border(1.dp, ml.colors.hairline, MlShape.Scanner),
    ) {
        if (hasCameraPermission) {
            QrScanner(onCode = onCode, modifier = Modifier.fillMaxSize())
        }

        CornerBracket(Alignment.TopStart)
        CornerBracket(Alignment.TopEnd)
        CornerBracket(Alignment.BottomStart)
        CornerBracket(Alignment.BottomEnd)

        val sweep = rememberInfiniteTransition(label = "scan")
        val offset by sweep.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "scanOffset",
        )
        // Expressed with weights so the sweep spans whatever height the square
        // resolves to, without a custom layout.
        Column(
            Modifier
                .fillMaxSize()
                .padding(26.dp),
        ) {
            Spacer(Modifier.weight(offset.coerceIn(0.001f, 0.999f)))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(ml.colors.accent.copy(alpha = 0.75f)),
            )
            Spacer(Modifier.weight((1f - offset).coerceIn(0.001f, 0.999f)))
        }

        if (hasCameraPermission) {
            MlText(
                stringResource(R.string.import_camera_hint),
                ml.type.meta,
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 74.dp),
                color = ml.colors.textMuted,
            )
        } else {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MlIcon(MlIcons.Camera, size = 28.dp, tint = ml.colors.textMuted)
                Spacer(Modifier.height(12.dp))
                MlText(
                    stringResource(R.string.import_camera_permission),
                    ml.type.meta,
                    color = ml.colors.textMuted,
                )
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .clip(MlShape.Pill)
                        .background(ml.colors.surface2),
                ) {
                    MlPressable(onClick = onRequestPermission) {
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            MlText(
                                stringResource(R.string.import_grant_camera),
                                ml.type.metaEmphatic,
                                color = ml.colors.accentInk,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.CornerBracket(alignment: Alignment) {
    val isTop = alignment == Alignment.TopStart || alignment == Alignment.TopEnd
    val isStart = alignment == Alignment.TopStart || alignment == Alignment.BottomStart
    Box(
        Modifier
            .align(alignment)
            .padding(26.dp)
            .size(44.dp)
            .drawCorner(isTop, isStart, ml.colors.accent),
    )
}

private fun Modifier.drawCorner(
    isTop: Boolean,
    isStart: Boolean,
    color: Color,
): Modifier = drawBehind {
    val stroke = 3.dp.toPx()
    val radius = 14.dp.toPx()
    // Two straight legs joined by a quarter-round at the outer corner.
    val path = Path().apply {
        when {
            isTop && isStart -> {
                moveTo(0f, size.height)
                lineTo(0f, radius)
                quadraticTo(0f, 0f, radius, 0f)
                lineTo(size.width, 0f)
            }
            isTop -> {
                moveTo(0f, 0f)
                lineTo(size.width - radius, 0f)
                quadraticTo(size.width, 0f, size.width, radius)
                lineTo(size.width, size.height)
            }
            isStart -> {
                moveTo(0f, 0f)
                lineTo(0f, size.height - radius)
                quadraticTo(0f, size.height, radius, size.height)
                lineTo(size.width, size.height)
            }
            else -> {
                moveTo(size.width, 0f)
                lineTo(size.width, size.height - radius)
                quadraticTo(size.width, size.height, size.width - radius, size.height)
                lineTo(0f, size.height)
            }
        }
    }
    drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
}

@Composable
private fun LabelledDivider(label: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(ml.colors.hairline),
        )
        MlText(label, ml.type.overline, color = ml.colors.textMuted)
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(ml.colors.hairline),
        )
    }
}

@Composable
private fun UrlField(state: ImportUiState, viewModel: ImportViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(MlShape.Input)
            .background(ml.colors.surface)
            .border(1.dp, ml.colors.hairline, MlShape.Input)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = state.url,
                onValueChange = viewModel::onUrlChange,
                singleLine = true,
                enabled = !state.isSubmitting,
                textStyle = ml.type.monoSm.copy(color = ml.colors.text),
                cursorBrush = SolidColor(ml.colors.accentLine),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { viewModel.submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.url.isEmpty()) {
                MlText(
                    stringResource(R.string.import_url_placeholder),
                    ml.type.monoSm,
                    color = ml.colors.textMuted,
                )
            }
        }
        Spacer(Modifier.size(10.dp))
        Box(
            Modifier
                .height(40.dp)
                .clip(MlShape.Pill)
                .background(if (state.canSubmit) ml.colors.surface3 else ml.colors.surface2),
        ) {
            MlPressable(onClick = { viewModel.submit() }, enabled = state.canSubmit) {
                Box(
                    Modifier
                        .height(40.dp)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MlText(
                        stringResource(R.string.import_add),
                        ml.type.metaEmphatic,
                        color = if (state.canSubmit) ml.colors.text else ml.colors.textMuted,
                    )
                }
            }
        }
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

private fun Context.clipboardText(): String? {
    val manager = getSystemService(ClipboardManager::class.java) ?: return null
    val clip = manager.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(this)?.toString()
}
