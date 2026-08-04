package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.ui.BrowserScreen
import com.example.ui.BrowserViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val browserViewModel: BrowserViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Edge to edge immersive drawing
        enableEdgeToEdge()
        
        setContent {
            MyApplicationTheme {
                BrowserScreen(
                    viewModel = browserViewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
