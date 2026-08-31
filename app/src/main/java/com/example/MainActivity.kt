package com.example

import android.os.Bundle
import android.util.Log
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
