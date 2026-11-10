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
 * The two buttons that stay when the left rail is turned off: Settings, and the other of the two
 * live sections — the Guide while Live TV is open, Live TV while the Guide is open.
 *
 * They sit at the top right, beside the categories search, where a thumb on the remote expects
 * them, and they are part of the list they sit in, so ▲ and ▼ reach them.
 */
@Composable
fun ShellTopButtons(
    /** The live section on screen: Live TV or the Guide. */
    section: MainSection,
    onOpenSettings: () -> Unit,
    onSwitchSection: (MainSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val other = if (section == MainSection.EPG) MainSection.LIVE_TV else MainSection.EPG
    Row(
        modifier.focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.mpx),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TopButton(
            icon = OwnTVIcon.SETTINGS,
            description = stringResource(tv.own.owntv.core.R.string.common_nav_settings),
            onClick = onOpenSettings,
        )
        TopButton(
            icon = other.stageIcon,
            description = stringResource(other.labelRes),
            onClick = { onSwitchSection(other) },
        )
    }
}

@Composable
private fun TopButton(icon: OwnTVIcon, description: String, onClick: () -> Unit) {
    val a = stageAccent
    StageSurface(
        onClick = onClick,
        radius = 14.mpx,
        modifier = Modifier
            .size(44.mpx)
            .semantics { contentDescription = description },
        idle = Modifier.background(a.accent.copy(alpha = 0.14f), RoundedCornerShape(14.mpx)),
        contentAlignment = Alignment.Center,
    ) { focused ->
        OwnTVIcon(icon, if (focused) a.onAccent else StageColors.NavText, Modifier.size(22.mpx))
    }
}
