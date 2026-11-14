package tv.own.owntv.features.shell.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import tv.own.owntv.features.live.SettingsBackdrop
import tv.own.owntv.ui.stage.stageGlass
import tv.own.owntv.ui.theme.mpx

/**
 * The Settings window: the Settings pages, over whatever is behind them.
 *
 * How much of that shows through is the user's call (Settings → Layout): [SettingsBackdrop.dim] puts
 * black over it, [SettingsBackdrop.glass] a frosted surface on top, and
 * [SettingsBackdrop.inset] keeps the content off the bezel on every side.
 *
 * Unlike the old overlay this draws no focus trap of its own: it must not sit between the remote and
 * the pages inside it, or D-pad navigation stops at the window's edge.
 */
@Composable
fun SettingsWindow(
    backdrop: SettingsBackdrop,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        // How much black sits over the picture: 100 hides it, 0 lets it through untouched.
        if (backdrop.dim > 0) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = backdrop.dim / 100f)))
        }
        if (backdrop.glass) {
            Box(Modifier.fillMaxSize().stageGlass(0.mpx, overContent = true))
        }
        // The window is a window, not a page that runs into the bezel.
        Box(
            Modifier
                .fillMaxSize()
                .padding(backdrop.inset.mpx),
        ) {
            content()
        }
    }
}
