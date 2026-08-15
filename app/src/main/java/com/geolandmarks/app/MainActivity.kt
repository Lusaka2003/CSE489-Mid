package com.geolandmarks.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.geolandmarks.app.ui.LandmarkViewModel
import com.geolandmarks.app.ui.MainScreen
import com.geolandmarks.app.ui.theme.LandmarksTheme

class MainActivity : ComponentActivity() {
    private val viewModel: LandmarkViewModel by viewModels {
        LandmarkViewModel.factory(application as LandmarkApplication)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LandmarksTheme {
                MainScreen(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshOnlineFlag()
        viewModel.refresh(showToast = false)
    }
}
