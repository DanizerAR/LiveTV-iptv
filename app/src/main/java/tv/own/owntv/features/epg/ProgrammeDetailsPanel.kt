package tv.own.owntv.features.epg

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.format.rememberSystemTimeFormatter
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/**
 * The programme details, in the preview panel: the channel's day, under the picture.
 *
 * It used to be a full-screen page that took the whole screen away from the live picture. Here it
 * sits in the third column, under the channel preview, and Back puts the normal preview back.
 */
@Composable
internal fun ProgrammeDetailsPanel(
    vm: EpgViewModel,
    channel: ChannelEntity,
    onWatch: () -> Unit,
    onPlayFromStart: (EpgProgrammeEntity) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    val today = remember { localMidnight(System.currentTimeMillis()) }
    val loaded by produceState<List<EpgProgrammeEntity>?>(null, today) {
        value = null
        value = runCatching { vm.dayProgrammes(channel, today) }.getOrDefault(emptyList())
    }
    val list = loaded.orEmpty()
    val formatTime = rememberSystemTimeFormatter()
    val first = remember { FocusRequester() }
    val listState = rememberLazyListState()
    BackHandler { onClose() }
    LaunchedEffect(loaded) {
        if (loaded == null) return@LaunchedEffect
        val nowIndex = list.indexOfFirst { it.startMs <= now && it.stopMs > now }
        if (nowIndex >= 0) listState.scrollToItem(nowIndex.coerceAtLeast(0))
    }

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.mpx),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.mpx),
        ) {
            OwnTVIcon(OwnTVIcon.EPG, stageAccent.accent, Modifier.size(20.mpx))
            Text(
                stringResource(R.string.content_programme_details),
                style = stageText(20, 800),
                color = StageColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (loaded == null) {
            Box(Modifier.fillMaxWidth().height(120.mpx), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.settings_loading), style = stageText(17, 400), color = StageColors.Muted)
            }
            return@Column
        }
        if (list.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(120.mpx), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.content_epg_no_programme), style = stageText(17, 400), color = StageColors.Muted)
            }
            return@Column
        }
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f, fill = false),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.mpx),
        ) {
            itemsIndexed(list, key = { _, p -> p.id }) { index, p ->
                val onNow = p.startMs <= now && p.stopMs > now
                StageSurface(
                    onClick = {
                        // A channel with an archive plays from the start; without one, it plays live.
                        if (channel.catchup) onPlayFromStart(p) else onWatch()
                    },
                    radius = 12.mpx,
                    focusStyle = tv.own.owntv.ui.stage.StageFocus.FX,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                        .then(if (onNow) Modifier.background(stageAccent.accent.copy(alpha = 0.12f), RoundedCornerShape(12.mpx)) else Modifier),
                ) { focused ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.mpx, vertical = 8.mpx),
                        horizontalArrangement = Arrangement.spacedBy(12.mpx),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            formatTime(p.startMs),
                            style = stageText(16, 700),
                            color = if (onNow) stageAccent.accent else StageColors.Muted,
                            maxLines = 1,
                        )
                        Text(
                            p.title,
                            style = stageText(17, if (onNow) 700 else 500),
                            color = if (focused) StageColors.FxText else StageColors.Text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
