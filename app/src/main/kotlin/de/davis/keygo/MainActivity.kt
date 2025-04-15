package de.davis.keygo

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import de.davis.keygo.auth.presentation.AuthScreen
import de.davis.keygo.core.presentation.theme.KeyGoTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KeyGoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .consumeWindowInsets(innerPadding)
                            .fillMaxSize()
                    ) {
                        AuthScreen(
                            navigate = {
                                Log.i("MainActivity", "Auth done!")
                            }
                        )
                    }
                }
            }
        }
    }
}