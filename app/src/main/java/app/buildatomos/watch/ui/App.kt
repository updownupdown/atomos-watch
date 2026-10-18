// The screens and the way between them. Home first; from it the next shell's run (Build), the
// Atlas and each atom's page, every game for free play, and Settings. Swipe right goes back
// everywhere but in a run, where the game owns the screen and the mark at the top (or the back
// button) opens the run's menu.

package app.buildatomos.watch.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
    const val BUILD = "build"
    fun atlas(z: Int) = "atlas/$z"
    fun atom(z: Int) = "atom/$z"
    fun game(id: String) = "game/$id"
    fun free(id: String, level: Int) = "free/$id/$level"
}

@Composable
fun App(model: AppModel, launchBuild: Boolean, onLaunched: () -> Unit, onExit: () -> Unit) {
    val nav = rememberSwipeDismissableNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: ""
    val inRun = route == Routes.BUILD || route.startsWith("free/")

    // The tile's and the complication's Play open straight into the next run for the atom.
    LaunchedEffect(launchBuild) {
        if (launchBuild) {
            nav.popBackStack(Routes.HOME, inclusive = false)
            if (model.buildRun() != null) nav.navigate(Routes.BUILD)
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
                        onBuild = { if (model.buildRun() != null) nav.go(Routes.BUILD) },
                        onAtlas = { nav.go(Routes.atlas(model.buildingZ)) },
                        onGames = { nav.go(Routes.GAMES) },
                        onSettings = { nav.go(Routes.SETTINGS) },
                        onAtom = { nav.go(Routes.atom(it)) },
                    )
                }
            }
            composable(Routes.BUILD) {
                val spec = remember { model.buildRun() }
                if (spec == null) LaunchedEffect(Unit) { nav.popBackStack() }
                else GameScreen(model, spec, onLeave = { nav.popBackStack() }, onNextRun = {
                    nav.popBackStack()
                    if (model.buildRun() != null) nav.go(Routes.BUILD)
                })
            }
            composable("free/{id}/{level}") { e ->
                val game = e.arguments?.getString("id")?.let(Games::byId) ?: Games.all.first()
                val level = e.arguments?.getString("level")?.toIntOrNull() ?: 1
                val spec = remember { model.freeRun(game, level) }
                GameScreen(model, spec, onLeave = { nav.popBackStack() }, onNextRun = { nav.popBackStack() })
            }
            composable(Routes.GAMES) { GamesScreen(model) { id -> nav.go(Routes.game(id)) } }
            composable("game/{id}") { e ->
                val game = e.arguments?.getString("id")?.let(Games::byId) ?: Games.all.first()
                FreePlayScreen(model, game) { level -> nav.go(Routes.free(game.id, level)) }
            }
            composable("atlas/{z}") { e ->
                AtlasScreen(model, e.arguments?.getString("z")?.toIntOrNull() ?: 1) { nav.go(Routes.atom(it)) }
            }
            composable("atom/{z}") { e ->
                AtomScreen(model, e.arguments?.getString("z")?.toIntOrNull() ?: 1) { z ->
                    // Stepping to a neighbor replaces this page rather than stacking another.
                    nav.popBackStack()
                    nav.go(Routes.atom(z))
                }
            }
            composable(Routes.SETTINGS) { SettingsScreen(model) }
        }
    }
}

private fun NavHostController.go(route: String) = navigate(route) { launchSingleTop = true }
