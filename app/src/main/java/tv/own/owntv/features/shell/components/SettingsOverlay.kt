package tv.own.owntv.features.shell.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import tv.own.owntv.core.R
import tv.own.owntv.features.live.SettingsBackdrop
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.stage.stageGlass
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent

/**
 * The Settings window: an overlay on the live picture, not a page that replaces it.
 *
 * The picture keeps playing behind it, and how much of it shows through is the user's call — a
 * dimming from opaque black to nothing at all, with the window's frosted surface on top. Back
 * closes the window, and so does the Back button in its corner, so there is always a way out that
 * does not depend on a gesture.
 */
@Composable
fun SettingsOverlay(
    backdrop: SettingsBackdrop,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        // How much black sits over the picture: 100 hides it, 0 lets it through untouched.
        if (backdrop.dim > 0) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = backdrop.dim / 100f)))
        }
        if (backdrop.glass) {
            Box(
                Modifier
                    .fillMaxSize()
                    .stageGlass(0.mpx, overContent = true),
            )
        }
        // A window that lets the D-pad wander into the picture behind it is not a window.
        Box(Modifier.fillMaxSize().trapAllFocusExit()) {
            BackHandler { onBack() }
            // The window is inset on every side: it is a window over the picture, not a page that
            // runs into the bezel.
            Column(Modifier.fillMaxSize().padding(48.mpx)) {
                BackButton(onBack)
                Box(Modifier.weight(1f).fillMaxWidth()) { content() }
            }
        }
    }
}

/** The window's own way out: a Back button in the top left, over the picture. */
@Composable
private fun BackButton(onBack: () -> Unit) {
    val a = stageAccent
    Row(
        Modifier.padding(start = 24.mpx, top = 24.mpx),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StageSurface(
            onClick = onBack,
            radius = 14.mpx,
            modifier = Modifier.size(44.mpx),
            contentAlignment = Alignment.Center,
        ) { focused ->
            OwnTVIcon(
                OwnTVIcon.CHEVRON_LEFT,
                if (focused) a.onAccent else StageColors.Text,
                Modifier.size(22.mpx),
            )
        }
        StageSurface(
            onClick = onBack,
            radius = 14.mpx,
            modifier = Modifier.padding(start = 10.mpx),
            idle = Modifier.background(StageColors.ControlFill, RoundedCornerShape(14.mpx)),
        ) { focused ->
            androidx.tv.material3.Text(
                stringResource(R.string.common_back),
                style = tv.own.owntv.ui.theme.stageText(17, 700),
                color = if (focused) a.onAccent else StageColors.Text,
            )
        }
    }
}
