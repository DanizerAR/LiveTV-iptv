package tv.own.owntv.features.more

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.features.shell.components.SettingsGroup
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.StageFocus
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.stage.StageTile
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.mpxSp
import tv.own.owntv.ui.theme.stageText

// ---------------------------------------------------------------------------------------------
// Settings: the sections, and nothing else
// ---------------------------------------------------------------------------------------------

/**
 * The Settings sections, in the order of `SettingsGroup`: a card opens the group it names.
 */
private data class SettingsGroupCard(val icon: OwnTVIcon, val nameRes: Int, val summaryRes: Int, val count: Int?, val group: SettingsGroup)

private val SettingsCards = listOf(
    SettingsGroupCard(OwnTVIcon.SPARKLE, R.string.settings_group_quick, R.string.settings_group_summary_quick, null, SettingsGroup.QUICK),
    SettingsGroupCard(OwnTVIcon.LIST, R.string.settings_group_sources, R.string.settings_card_sources, 8, SettingsGroup.SOURCES),
    SettingsGroupCard(OwnTVIcon.PALETTE, R.string.settings_group_appearance, R.string.settings_card_appearance, 9, SettingsGroup.APPEARANCE),
    SettingsGroupCard(OwnTVIcon.GRID, R.string.settings_group_layout, R.string.settings_card_layout, 10, SettingsGroup.LAYOUT),
    SettingsGroupCard(OwnTVIcon.MOVIES, R.string.settings_group_content_metadata, R.string.settings_card_content, 3, SettingsGroup.CONTENT),
    SettingsGroupCard(OwnTVIcon.PLAY_CIRCLE, R.string.settings_vp_cat_player, R.string.settings_card_player, 10, SettingsGroup.PLAYER),
    SettingsGroupCard(OwnTVIcon.EXPAND, R.string.settings_vp_cat_picture, R.string.settings_card_picture, 9, SettingsGroup.PICTURE),
    SettingsGroupCard(OwnTVIcon.HEADPHONES, R.string.settings_group_sound_subtitles, R.string.settings_card_sound, 12, SettingsGroup.SOUND),
    SettingsGroupCard(OwnTVIcon.LIVE_TV, R.string.settings_live_tv, R.string.settings_card_live, 12, SettingsGroup.LIVE),
    SettingsGroupCard(OwnTVIcon.REC, R.string.settings_group_watching_recording, R.string.settings_card_watching, 11, SettingsGroup.WATCHING),
    SettingsGroupCard(OwnTVIcon.INFO, R.string.settings_group_app, R.string.settings_card_app, 10, SettingsGroup.APP),
)

/** The card last opened, so Back from its group lands on it again rather than on the search field. */
private var lastOpenedCard: Int? = null

/**
 * The Settings window's first page: the sections themselves, and a search over them.
 *
 * There is nothing else in it — no favourites, no history, no about: the window is Settings, and
 * the live picture keeps playing behind it.
 */
@Composable
internal fun SettingsPage(entry: FocusRequester, pinned: Int, onOpenGroup: (Int) -> Unit, onSearch: () -> Unit) {
    val back = lastOpenedCard
    Column(Modifier.fillMaxSize().focusGroup()) {
        PageHead(stringResource(R.string.common_nav_settings)) {
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.more_settings_pin_hint), style = stageText(17, 500), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        StageSurface(
            onClick = { lastOpenedCard = null; onSearch() }, radius = 18.mpx,
            modifier = Modifier.padding(top = 26.mpx).fillMaxWidth().height(60.mpx).then(if (back == null) Modifier.focusRequester(entry) else Modifier),
            focusStyle = StageFocus.FX,
            idle = Modifier.background(StageColors.ControlFill, RoundedCornerShape(18.mpx)),
        ) { focused ->
            Row(Modifier.padding(horizontal = 20.mpx), horizontalArrangement = Arrangement.spacedBy(14.mpx), verticalAlignment = Alignment.CenterVertically) {
                OwnTVIcon(OwnTVIcon.SEARCH, if (focused) StageColors.Text else StageColors.Dim, Modifier.size(19.mpx))
                Text(stringResource(R.string.more_settings_search), style = stageText(19, 500), color = if (focused) StageColors.Text else StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(Modifier.padding(top = 22.mpx), verticalArrangement = Arrangement.spacedBy(20.mpx)) {
            SettingsCards.chunked(3).forEach { row ->
                // 150 at the default font; a larger app font grows the row rather than cutting the
                // summary, and the row's cards keep one height.
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(20.mpx)) {
                    row.forEach { card ->
                        val index = SettingsCards.indexOf(card)
                        StageTile(
                            modifier = Modifier.weight(1f).heightIn(min = 150.mpx).fillMaxHeight().then(if (index == back) Modifier.focusRequester(entry) else Modifier),
                            onClick = { lastOpenedCard = index; onOpenGroup(card.group.ordinal) },
                            padding = 0.mpx,
                        ) { focused ->
                            Column(Modifier.padding(horizontal = 26.mpx, vertical = 24.mpx)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OwnTVIcon(card.icon, if (focused) StageColors.Text else StageColors.Muted, Modifier.size(28.mpx))
                                    Spacer(Modifier.weight(1f))
                                    Text((card.count ?: pinned).toString(), style = stageText(17, 700), color = StageColors.Dim)
                                }
                                Text(stringResource(card.nameRes), style = stageText(24, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 14.mpx))
                                Text(stringResource(card.summaryRes), style = stageText(16, 500), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.mpx))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "Live TV 2" — a section's name and its count. */
@Composable
private fun counted(nameRes: Int, n: Int) = stringResource(nameRes) + " " + n

/** The page's heading, 42/800. */
@Composable
private fun PageHead(title: String, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 60.mpx), horizontalArrangement = Arrangement.spacedBy(22.mpx), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = stageText(42, 800, (-1).mpxSp), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing()
    }
}
