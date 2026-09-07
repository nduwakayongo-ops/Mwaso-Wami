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

    override fun onStart() {
        super.onStart()
        viewModel.playbackController.visualizerEngine.setScreenVisible(true)
    }

    override fun onStop() {
        super.onStop()
        viewModel.playbackController.visualizerEngine.setScreenVisible(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        val controller = com.example.service.PlaybackController.getInstance(applicationContext)
        if (controller.playbackState.value.isPlaying || controller.transitionManager.isCrossfadeActive) {
            Log.i(com.example.service.MediaPlaybackService.LOG_TAG, "MWASO_ACTIVITY_DESTROYED_KEEPING_PLAYBACK")
        }
    }
}
