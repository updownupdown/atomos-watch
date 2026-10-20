// The screens and the way between them. Home first; from it the next shell's run, the Build
// screen and each atom's Build page, the Atlas and each atom's Atlas page (each page links to the
// other), every game for free play (a tap on a game opens its difficulties; a tap on one plays
// it), and Settings. Swipe right goes back
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
import app.buildatomos.core.Game
import app.buildatomos.core.Games
import app.buildatomos.watch.AppModel

object Routes {
    const val HOME = "home"
    const val GAMES = "games"
    const val SETTINGS = "settings"
    /** The run for the next shell of the atom being built. */
    const val RUN = "run"
    /** The Build screen: the whole collection. */
    const val COLLECTION = "collection"
    fun build(z: Int) = "build/$z"
    fun atlas(z: Int, table: Boolean = false) = "atlas/$z/${if (table) "table" else "list"}"
    fun atom(z: Int) = "atom/$z"
    fun free(id: String, level: Int) = "free/$id/$level"
    fun game(id: String) = "game/$id"
}

@Composable
fun App(model: AppModel, launchBuild: Boolean, onLaunched: () -> Unit, onExit: () -> Unit) {
    val nav = rememberSwipeDismissableNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: ""
    val inRun = route == Routes.RUN || route.startsWith("free/")
    val run = { if (model.buildRun() != null) nav.go(Routes.RUN) }
    /** Free play of `game` at difficulty `level`, kept as the game's pick for next time. */
    val play = { game: Game, level: Int ->
        model.pick(game, level)
        nav.go(Routes.free(game.id, level))
    }

    // The tile's and the complication's Play open straight into the next run for the atom.
    LaunchedEffect(launchBuild) {
        if (launchBuild) {
            nav.popBackStack(Routes.HOME, inclusive = false)
            if (model.buildRun() != null) nav.navigate(Routes.RUN)
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
                        onRun = run,
                        onCollection = { nav.go(Routes.COLLECTION) },
                        onBuildAtom = { nav.go(Routes.build(it)) },
                        onAtlas = { nav.go(Routes.atlas(model.buildingZ)) },
                        onAtom = { nav.go(Routes.atom(it)) },
                        onPlay = play,
                        onGames = { nav.go(Routes.GAMES) },
                        onSettings = { nav.go(Routes.SETTINGS) },
                    )
                }
            }
            composable(Routes.RUN) {
                val spec = remember { model.buildRun() }
                if (spec == null) LaunchedEffect(Unit) { nav.popBackStack() }
                else GameScreen(model, spec, onLeave = { nav.popBackStack() }, onNextRun = {
                    nav.popBackStack()
                    run()
                })
            }
            composable("free/{id}/{level}") { e ->
                val game = e.arguments?.getString("id")?.let(Games::byId) ?: Games.all.first()
                val level = e.arguments?.getString("level")?.toIntOrNull() ?: 0
                val spec = remember { model.freeRun(game, level) }
                GameScreen(model, spec, onLeave = { nav.leaveFreeRun() }, onNextRun = { nav.leaveFreeRun() })
            }
            composable(Routes.COLLECTION) {
                CollectionScreen(model, onAtom = { nav.go(Routes.build(it)) })
            }
            composable("build/{z}") { e ->
                AtomBuildScreen(
                    model, e.arguments?.getString("z")?.toIntOrNull() ?: 1,
                    onBuild = run,
                    onPlay = play,
                    onAtlas = { nav.across("atom/{z}", it) },
                    onAtom = { z -> nav.swap(Routes.build(z)) },
                )
            }
            composable(Routes.GAMES) { GamesScreen(model) { nav.go(Routes.game(it.id)) } }
            composable("game/{id}") { e ->
                val game = e.arguments?.getString("id")?.let(Games::byId) ?: Games.all.first()
                DifficultyScreen(model, game, onPlay = { play(game, it) }, onBack = { nav.popBackStack() })
            }
            composable("atlas/{z}/{view}") { e ->
                AtlasScreen(model, e.arguments?.getString("z")?.toIntOrNull() ?: 1, e.arguments?.getString("view") == "table") { nav.go(Routes.atom(it)) }
            }
            composable("atom/{z}") { e ->
                AtomScreen(
                    model, e.arguments?.getString("z")?.toIntOrNull() ?: 1,
                    onBuild = { nav.across("build/{z}", it) },
                    onTable = { nav.go(Routes.atlas(it, table = true)) },
                    onAtom = { z -> nav.swap(Routes.atom(z)) },
                )
            }
            composable(Routes.SETTINGS) { SettingsScreen(model) }
        }
    }
}

private fun NavHostController.go(route: String) = navigate(route) { launchSingleTop = true }

/** Leaving a free run goes back to wherever the game was chosen: its difficulties, or Home for
 *  Nu's pick. Does nothing once the run has gone, so a second tap on Exit can't pop further. */
private fun NavHostController.leaveFreeRun() {
    if (currentBackStackEntry?.destination?.route != "free/{id}/{level}") return
    popBackStack()
}

/** Stepping to a neighbor replaces this page rather than stacking another. */
private fun NavHostController.swap(route: String) {
    popBackStack()
    go(route)
}

/** From an atom's Build page to its Atlas page or back: if the page under this one is already
 *  that atom's other page, goes back to it, so the two don't pile up. */
private fun NavHostController.across(pattern: String, z: Int) {
    val under = previousBackStackEntry
    if (under?.destination?.route == pattern && under.arguments?.getString("z")?.toIntOrNull() == z) popBackStack()
    else go(pattern.replace("{z}", "$z"))
}
