package vpn.moonlight.ui.split

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vpn.moonlight.R
import vpn.moonlight.data.model.SplitMode
import vpn.moonlight.design.MlIcon
import vpn.moonlight.design.MlIcons
import vpn.moonlight.design.MlShape
import vpn.moonlight.design.components.MlCard
import vpn.moonlight.design.components.MlIconButton
import vpn.moonlight.design.components.MlIconTile
import vpn.moonlight.design.components.MlSegmented
import vpn.moonlight.design.components.MlSwitch
import vpn.moonlight.design.components.MlText
import vpn.moonlight.design.ml

private val modes = listOf(SplitMode.All, SplitMode.OnlySelected, SplitMode.ExceptSelected)

@Composable
fun SplitTunnelScreen(
    viewModel: SplitTunnelViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val iconLoader = remember(context) { AppIconLoader(context) }

    // In "all traffic" mode the switches have no effect, so the list dims rather
    // than disappearing — the user keeps their choices.
    val listAlpha by animateFloatAsState(
        if (state.mode == SplitMode.All) 0.4f else 1f,
        label = "listAlpha",
    )

    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MlIconButton(MlIcons.ChevronLeft, onBack)
            MlText(stringResource(R.string.split_title), ml.type.titleSm)
        }

        Column(
            Modifier.padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MlSegmented(
                options = listOf(
                    stringResource(R.string.split_mode_all),
                    stringResource(R.string.split_mode_only),
                    stringResource(R.string.split_mode_except),
                ),
                selectedIndex = modes.indexOf(state.mode).coerceAtLeast(0),
                onSelect = { index -> viewModel.setMode(modes[index]) },
            )

            MlText(
                stringResource(
                    when (state.mode) {
                        SplitMode.All -> R.string.split_hint_all
                        SplitMode.OnlySelected -> R.string.split_hint_only
                        SplitMode.ExceptSelected -> R.string.split_hint_except
                    },
                ),
                ml.type.bodySm,
                Modifier.padding(horizontal = 2.dp),
                color = ml.colors.textMuted,
            )

            SearchField(state.query, viewModel::setQuery)
        }

        Spacer(Modifier.height(14.dp))

        when {
            state.isLoading -> Message(stringResource(R.string.split_loading))

            state.apps.isEmpty() && state.isFiltered ->
                Message(stringResource(R.string.split_no_matches, state.query))

            else -> LazyColumn(
                Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .graphicsLayer { alpha = listAlpha },
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 110.dp),
            ) {
                // Keyed by package so rows keep their loaded icon while filtering.
                items(state.apps, key = { it.app.packageName }) { row ->
                    MlCard(Modifier.fillMaxWidth(), shape = MlShape.Row) {
                        AppRowItem(
                            row = row,
                            enabled = state.mode != SplitMode.All,
                            iconLoader = iconLoader,
                            onToggle = { viewModel.toggleApp(row.app.packageName) },
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(MlShape.Pill)
            .background(ml.colors.surface)
            .border(1.dp, ml.colors.hairline, MlShape.Pill)
            .padding(start = 16.dp, end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MlIcon(MlIcons.Search, size = 17.dp, tint = ml.colors.textMuted)
        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = ml.type.bodySm.copy(color = ml.colors.text),
                cursorBrush = SolidColor(ml.colors.accentLine),
                modifier = Modifier.fillMaxWidth(),
            )
            if (query.isEmpty()) {
                MlText(
                    stringResource(R.string.split_search_hint),
                    ml.type.bodySm,
                    color = ml.colors.textMuted,
                )
            }
        }
        if (query.isNotEmpty()) {
            MlIconButton(
                icon = MlIcons.X,
                onClick = { onQueryChange("") },
                size = 32.dp,
                iconSize = 16.dp,
                background = ml.colors.surface2,
            )
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        MlText(text, ml.type.bodySm, color = ml.colors.textMuted)
    }
}

@Composable
private fun AppRowItem(
    row: AppRow,
    enabled: Boolean,
    iconLoader: AppIconLoader,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(row, iconLoader)
        Column(Modifier.weight(1f)) {
            MlText(row.app.label, ml.type.bodyTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(1.dp))
            MlText(
                row.app.packageName,
                ml.type.metaSm,
                color = ml.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        MlSwitch(checked = row.isSelected, onCheckedChange = { onToggle() }, enabled = enabled)
    }
}

/**
 * The app's real launcher icon, with the initial-on-a-tile as the fallback while
 * it decodes and for packages whose icon cannot be read.
 */
@Composable
private fun AppIcon(row: AppRow, loader: AppIconLoader) {
    val icon by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        row.app.packageName,
    ) {
        value = loader.load(row.app.packageName)
    }

    val bitmap = icon
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier
                .size(42.dp)
                .clip(MlShape.IconTile),
        )
    } else {
        val palette = ml.colors.categories
        val fill = palette[(row.app.packageName.hashCode().let { if (it < 0) -it else it }) % palette.size]
        MlIconTile(fill) {
            MlText(
                row.app.label.take(1).uppercase(),
                ml.type.lead.copy(fontSize = ml.type.bodyTitle.fontSize),
                color = ml.colors.textOnAccent,
            )
        }
    }
}
