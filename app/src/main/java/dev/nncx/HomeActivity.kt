package dev.nncx

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.nncx.ui.NncxRoot

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { NncxRoot() }
    }

    /** A home screen has nowhere to go back to. */
    override fun onUserLeaveHint() = Unit
}
