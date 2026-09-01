package com.example

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.MainViewModel
import com.example.ui.screens.MainScreen

class MainActivity : ComponentActivity() {
    private val startTime = System.currentTimeMillis()
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("PERF", "[PERF] MainActivity start")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        enableEdgeToEdge()
        setContent {
            MainScreen(
                viewModel = viewModel,
                onFirstUIRendered = {
                    val duration = System.currentTimeMillis() - startTime
                    Log.d("PERF", "[PERF] First UI rendered: ${duration}ms")
                }
            )
        }
    }
}
