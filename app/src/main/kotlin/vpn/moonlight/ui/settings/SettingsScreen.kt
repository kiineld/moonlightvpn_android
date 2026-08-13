package vpn.moonlight.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import kotlinx.coroutines.launch
import vpn.moonlight.BuildConfig
import vpn.moonlight.R
import vpn.moonlight.data.model.AppLanguage
import vpn.moonlight.data.model.SplitMode
import vpn.moonlight.data.model.ThemeMode
import vpn.moonlight.design.MlIcons
import vpn.moonlight.design.components.MlCard
import vpn.moonlight.design.components.MlDivider
import vpn.moonlight.design.components.MlExternalMark
import vpn.moonlight.design.components.MlIconTile
import vpn.moonlight.design.components.MlNavRow
import vpn.moonlight.design.components.MlOverline
import vpn.moonlight.design.components.MlSegmented
import vpn.moonlight.design.components.MlSwitch
import vpn.moonlight.design.components.MlText
import vpn.moonlight.design.MlIcon
import vpn.moonlight.design.ml
import vpn.moonlight.ui.common.appsCountText
import vpn.moonlight.update.ApkInstaller

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenSplitTunnel: () -> Unit,
    onShowOnboarding: () -> Unit,
    onOpenLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coreVersion = remember { runCatching { viewModel.coreVersion() }.getOrDefault("—") }

    // Copying tens of megabytes into an install session would block the frame,
    // so it runs off the main thread. The system takes over from the commit:
    // it shows the confirmation, and offers to allow this app as an install
    // source when it is not one yet.
    val scope = rememberCoroutineScope()
    val install: (File) -> Unit = { file -> scope.launch { ApkInstaller.install(context, file) } }

    // Turning alerts on is meaningless without POST_NOTIFICATIONS on API 33+, so
    // ask at the moment the user opts in rather than up front.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.setNotifications(granted) }

    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 4.dp)) {
            MlText(stringResource(R.string.settings_title), ml.type.title)
        }

        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 12.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MlOverline(stringResource(R.string.settings_section_connection))
            MlCard {
                MlNavRow(
                    title = stringResource(R.string.settings_split),
                    subtitle = splitSummary(state.settings.splitMode, state.selectedAppCount),
                    onClick = onOpenSplitTunnel,
                    icon = MlIcons.Layers,
                    tileFill = ml.colors.cat2,
                )
            }

            MlOverline(stringResource(R.string.settings_section_app))
            MlCard {
                Column {
                    SegmentedRow(
                        label = stringResource(R.string.settings_theme),
                        options = listOf(
                            stringResource(R.string.settings_theme_dark),
                            stringResource(R.string.settings_theme_light),
                        ),
                        selectedIndex = if (state.settings.theme == ThemeMode.Light) 1 else 0,
                        onSelect = { index ->
                            viewModel.setTheme(if (index == 1) ThemeMode.Light else ThemeMode.Dark)
                        },
                    )
                    MlDivider(insetStart = 16.dp)
                    SegmentedRow(
                        label = stringResource(R.string.settings_language),
                        options = listOf("RU", "EN"),
                        selectedIndex = if (state.settings.language == AppLanguage.English) 1 else 0,
                        onSelect = { index ->
                            viewModel.setLanguage(
                                if (index == 1) AppLanguage.English else AppLanguage.Russian,
                            )
                        },
                    )
                    MlDivider(insetStart = 16.dp)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            MlText(stringResource(R.string.settings_notifications), ml.type.bodyTitle)
                            Spacer(Modifier.height(1.dp))
                            MlText(
                                stringResource(R.string.settings_notifications_sub),
                                ml.type.metaSm,
                                color = ml.colors.textMuted,
                            )
                        }
                        MlSwitch(
                            checked = state.settings.notificationsEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && Build.VERSION.SDK_INT >= 33) {
                                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    viewModel.setNotifications(enabled)
                                }
                            },
                        )
                    }
                }
            }

            MlOverline(stringResource(R.string.settings_section_support))
            MlCard {
                Column {
                    MlNavRow(
                        title = stringResource(R.string.settings_channel),
                        subtitle = stringResource(R.string.settings_channel_sub),
                        onClick = { context.openUrl(BuildConfig.TELEGRAM_CHANNEL_URL) },
                        icon = MlIcons.MessageCircle,
                        tileFill = ml.colors.cat1,
                        trailing = { MlExternalMark() },
                    )
                    MlDivider(insetStart = 72.dp)
                    MlNavRow(
                        title = stringResource(R.string.settings_support),
                        subtitle = stringResource(R.string.settings_support_sub),
                        // The panel tells us where support lives; the BuildConfig
                        // value is only a fallback for panels that do not.
                        onClick = { context.openUrl(state.supportUrl ?: BuildConfig.SUPPORT_URL) },
                        icon = MlIcons.Headphones,
                        tileFill = ml.colors.cat4,
                        trailing = { MlExternalMark() },
                    )
                    MlDivider(insetStart = 72.dp)
                    MlNavRow(
                        title = stringResource(R.string.logs_title),
                        subtitle = stringResource(R.string.logs_subtitle),
                        onClick = onOpenLogs,
                        icon = MlIcons.Activity,
                        tileFill = ml.colors.cat3,
                    )
                    MlDivider(insetStart = 72.dp)
                    UpdateRow(
                        state = updateState,
                        currentVersion = BuildConfig.VERSION_NAME,
                        onCheck = viewModel::checkForUpdate,
                        onDownload = viewModel::downloadUpdate,
                        onInstall = install,
                    )
                    MlDivider(insetStart = 72.dp)
                    MlNavRow(
                        title = stringResource(R.string.settings_about),
                        subtitle = stringResource(R.string.settings_about_sub, BuildConfig.VERSION_NAME),
                        onClick = {
                            viewModel.resetOnboarding()
                            onShowOnboarding()
                        },
                        leading = {
                            MlIconTile(ml.colors.surface2) {
                                MlIcon(MlIcons.Info, size = 19.dp, tint = ml.colors.accentInk)
                            }
                        },
                    )
                }
            }

            MlText(
                stringResource(R.string.settings_core_version, coreVersion),
                ml.type.metaSm,
                Modifier.padding(horizontal = 2.dp),
                color = ml.colors.textMuted,
            )
        }
    }
}

@Composable
private fun splitSummary(mode: SplitMode, count: Int): String = when (mode) {
    SplitMode.All -> stringResource(R.string.split_summary_all)
    SplitMode.OnlySelected -> stringResource(
        R.string.split_summary_format,
        appsCountText(count),
        stringResource(R.string.split_mode_only).lowercase(),
    )
    SplitMode.ExceptSelected -> stringResource(
        R.string.split_summary_format,
        appsCountText(count),
        stringResource(R.string.split_mode_except).lowercase(),
    )
}

@Composable
private fun SegmentedRow(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MlText(label, ml.type.bodyTitle, Modifier.weight(1f))
        MlSegmented(
            options = options,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            segmentHeight = 28.dp,
            fillWidth = false,
        )
    }
}

private fun Context.openUrl(url: String) {
    runCatching {
        startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
