package io.github.gap19.aacker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import io.github.gap19.aacker.ui.Root

class MainActivity : ComponentActivity() {
    /** Incremented when the app is opened from the player's notification or ongoing activity. */
    private val openPlayer = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
        setContent { Root(openPlayer.intValue) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.getBooleanExtra(OPEN_NOW, false) == true) {
            openPlayer.intValue++
            intent.removeExtra(OPEN_NOW)
        }
    }

    override fun onStart() {
        super.onStart()
        app.library.start()
        app.player.connect()
    }

    override fun onStop() {
        // With the app out of sight the service plays alone; a paused service may then stop.
        app.player.disconnect()
        super.onStop()
    }

    companion object {
        const val OPEN_NOW = "io.github.gap19.aacker.OPEN_NOW"
    }
}
