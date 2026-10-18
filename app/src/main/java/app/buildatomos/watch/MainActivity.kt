// The one activity. The tile opens it with the game Nu picked as the "game" extra.

package app.buildatomos.watch

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import app.buildatomos.watch.ui.App

class MainActivity : ComponentActivity() {
    private val launchGame = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val model = AppModel.get(this)
        if (savedInstanceState == null) launchGame.value = intent?.getStringExtra(EXTRA_GAME)
        setContent {
            App(model, launchGame.value, onLaunched = { launchGame.value = null }, onExit = { finish() })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_GAME)?.let { launchGame.value = it }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) AppModel.get(this).feel.release()
    }

    companion object {
        const val EXTRA_GAME = "game"
    }
}
