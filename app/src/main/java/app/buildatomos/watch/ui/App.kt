// The screens and the way between them: Home first; Games, an atom, Settings and a run on top.
// Swipe right goes back everywhere but in a run, where the game owns the screen (a swipe in from
// the very left edge, or the back button, leaves).

package app.buildatomos.watch.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.wear.compose.material.SwipeToDismissBox
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import app.buildatomos.core.Games
import app.buildatomos.watch.AppModel

object Routes {
    const val HOME = "home"
    const val GAMES = "games"
    const val SETTINGS = "settings"
    fun play(id: String) = "play/$id"
    fun atom(z: Int) = "atom/$z"
}

@Composable
fun App(model: AppModel, launchGame: String?, onLaunched: () -> Unit, onExit: () -> Unit) {
    val nav = rememberSwipeDismissableNavController()
    val entry by nav.currentBackStackEntryAsState()
    val inRun = entry?.destination?.route?.startsWith("play/") == true

    // The tile's Play opens straight into the game.
    LaunchedEffect(launchGame) {
        if (launchGame != null && Games.byId(launchGame) != null) {
            nav.popBackStack(Routes.HOME, inclusive = false)
            nav.navigate(Routes.play(launchGame))
            onLaunched()
        }
    }

    AtomosTheme {
        SwipeDismissableNavHost(nav, startDestination = Routes.HOME, userSwipeEnabled = !inRun) {
            composable(Routes.HOME) {
                // The window doesn't swipe away on its own (a run would lose to it), so Home does.
                SwipeToDismissBox(onDismissed = onExit) { background ->
                    if (!background) HomeScreen(
                        model,
                        onNext = { nav.go(Routes.play(model.nuPick().id)) },
                        onGames = { nav.go(Routes.GAMES) },
                        onSettings = { nav.go(Routes.SETTINGS) },
                        onAtom = { nav.go(Routes.atom(it)) },
                    )
                }
            }
            composable(Routes.GAMES) {
                GamesScreen(model) { id -> nav.go(Routes.play(id)) }
            }
            composable(Routes.SETTINGS) { SettingsScreen(model) }
            composable("atom/{z}") { e ->
                AtomScreen(model, e.arguments?.getString("z")?.toIntOrNull() ?: 1)
            }
            composable("play/{id}") { e ->
                val game = e.arguments?.getString("id")?.let(Games::byId) ?: Games.all.first()
                GameScreen(model, game) { nav.popBackStack() }
            }
        }
    }
}

private fun NavHostController.go(route: String) = navigate(route) { launchSingleTop = true }
