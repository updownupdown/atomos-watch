// The one activity. The tile and the Nu complication open it with the "build" extra, straight into
// the next run for the atom being built.

package app.buildatomos.watch

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import app.buildatomos.watch.ui.App

class MainActivity : ComponentActivity() {
    private val launchBuild = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val model = AppModel.get(this)
        if (savedInstanceState == null) launchBuild.value = intent?.getBooleanExtra(EXTRA_BUILD, false) == true
        setContent {
            App(model, launchBuild.value, onLaunched = { launchBuild.value = false }, onExit = { finish() })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_BUILD, false)) launchBuild.value = true
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) AppModel.get(this).feel.release()
    }

    companion object {
        const val EXTRA_BUILD = "build"
    }
}
