package io.github.andrealtb.lockscreenlyrics.management.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.andrealtb.lockscreenlyrics.AppearanceSnapshot
import io.github.andrealtb.lockscreenlyrics.R
import io.github.andrealtb.lockscreenlyrics.management.Spacing

/**
 * Appearance (plan §8.1, §14.18): a read-only summary of the saved settings. Tapping a card opens
 * the existing appearance page, which keeps its own preview and draft / save / discard flow;
 * this tab never writes the configuration, so switching tabs cannot commit or lose an edit.
 */
@Composable
internal fun AppearanceContent(
    snapshot: AppearanceSnapshot?,
    onOpen: (AppearanceTarget) -> Unit,
    /** Called with the newly picked language; the current one comes from the configuration. */
    onLanguageChange: (english: Boolean) -> Unit,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.widthIn(max = Spacing.maxContentWidth),
        contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, top = Spacing.xl, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        item {
            Column(Modifier.padding(horizontal = Spacing.xs)) {
                Text(stringResource(R.string.mgmt_tab_appearance), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.appearance_subtitle), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (snapshot == null) {
            item { Text(stringResource(R.string.home_value_checking), Modifier.padding(horizontal = Spacing.xs)) }
            return@LazyColumn
        }
        item { LyricAppearanceCard(snapshot) { onOpen(AppearanceTarget.SETTINGS) } }
        item {
            SummaryCard(stringResource(R.string.artwork_settings_title), onClick = { onOpen(AppearanceTarget.ARTWORK) }) {
                Text(valueText(artworkValue(snapshot)), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Text(stringResource(R.string.appearance_section_more), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xs))
        }
        items(MORE_LINKS, key = { it.first }) { (target, title, sub) ->
            SummaryCard(stringResource(title), onClick = { onOpen(target) }) {
                Text(stringResource(sub), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { LanguageCard(onLanguageChange) }
    }
}

private val MORE_LINKS = listOf(
    Triple(AppearanceTarget.TRANSLATION, R.string.link_player_translation, R.string.link_player_translation_sub),
    Triple(AppearanceTarget.CLEANUP, R.string.link_opening_cleanup, R.string.link_opening_cleanup_sub),
    Triple(AppearanceTarget.BACKUP, R.string.link_config_backup, R.string.link_config_backup_sub),
)

/**
 * Everything the old appearance page edits, in one card with one way in (PJZ110 r1: separate
 * cards that all opened the same page read as duplicate entries).
 */
@Composable
private fun LyricAppearanceCard(snapshot: AppearanceSnapshot, onOpen: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Column(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(stringResource(R.string.appearance_lyric_card), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(stringResource(R.string.appearance_preset_value, stringResource(presetLabel(snapshot.preset))),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(stringResource(presetSummary(snapshot.preset)), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                appearanceGroups(snapshot).forEachIndexed { index, group ->
                    if (index > 0) HorizontalDivider(Modifier.padding(vertical = Spacing.xs), color = MaterialTheme.colorScheme.outlineVariant)
                    Text(stringResource(group.title), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                    group.lines.forEach { LineRow(stringResource(it.label), it.value) }
                }
                Button(onClick = onOpen, modifier = Modifier.fillMaxWidth().padding(top = Spacing.s)) {
                    Text(stringResource(R.string.mgmt_appearance_open))
                }
            }
        }
    }
}

/** Moved from the old page's status card (§14.18); applies to every settings page. */
@Composable
private fun LanguageCard(onLanguageChange: (english: Boolean) -> Unit) {
    val english = LocalConfiguration.current.locales[0].language == "en"
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(Spacing.l), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.appearance_language), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                FilterChip(selected = !english, onClick = { if (english) onLanguageChange(false) },
                    label = { Text(stringResource(R.string.appearance_language_zh)) })
                FilterChip(selected = english, onClick = { if (!english) onLanguageChange(true) },
                    label = { Text(stringResource(R.string.appearance_language_en)) })
            }
        }
    }
}

@Composable
private fun SummaryCard(title: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun LineRow(label: String, value: AppearanceValue) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (value is AppearanceValue.Color) {
            swatchColor(value.hex)?.let { color ->
                Box(
                    Modifier.padding(end = Spacing.s).size(16.dp).clearAndSetSemantics { }
                        .background(color, CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                )
            }
        }
        Text(valueText(value), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun valueText(value: AppearanceValue): String = when (value) {
    is AppearanceValue.Res -> stringResource(value.id)
    is AppearanceValue.Format -> stringResource(value.id, value.arg)
    is AppearanceValue.Color -> value.hex.uppercase()
}

private fun swatchColor(hex: String): Color? =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
