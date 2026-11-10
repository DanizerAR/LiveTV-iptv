package tv.own.owntv.features.shell.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import tv.own.owntv.core.nav.MainSection
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent

/**
 * The section switcher shown when the left rail is turned off. Four icon buttons — Search, Live TV,
 * Guide, More — plus an optional menu button that brings the rail back. On the Live screen it sits
 * in the categories panel's top-right corner; on every other screen the shell places it at the top
 * right, so the sections never become unreachable.
 */
@Composable
fun ShellNavDock(
    selected: MainSection,
    visibleSections: Set<MainSection>,
    onSelect: (MainSection) -> Unit,
    modifier: Modifier = Modifier,
    onShowRail: (() -> Unit)? = null,
) {
    val a = stageAccent
    val sections = listOf(MainSection.SEARCH, MainSection.LIVE_TV, MainSection.EPG, MainSection.MORE)
        .filter { it in visibleSections || it == MainSection.SEARCH || it == MainSection.MORE }
    Row(modifier.focusGroup(), horizontalArrangement = Arrangement.spacedBy(6.mpx), verticalAlignment = Alignment.CenterVertically) {
        sections.forEach { section ->
            val active = section == selected
            val description = stringResource(section.labelRes)
            StageSurface(
                onClick = { onSelect(section) },
                radius = 14.mpx,
                modifier = Modifier
                    .size(44.mpx)
                    .semantics { contentDescription = description },
                idle = if (active) Modifier.background(a.accent.copy(alpha = 0.14f), RoundedCornerShape(14.mpx)) else Modifier,
                contentAlignment = Alignment.Center,
            ) { focused ->
                OwnTVIcon(
                    section.stageIcon,
                    if (focused) a.onAccent else if (active) a.accent else StageColors.NavText,
                    Modifier.size(22.mpx),
                )
            }
        }
        if (onShowRail != null) {
            val description = stringResource(tv.own.owntv.R.string.settings_nav_rail_show)
            StageSurface(
                onClick = onShowRail,
                radius = 14.mpx,
                modifier = Modifier
                    .size(44.mpx)
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) { focused ->
                OwnTVIcon(OwnTVIcon.MENU, if (focused) a.onAccent else StageColors.NavText, Modifier.size(22.mpx))
            }
        }
    }
}
