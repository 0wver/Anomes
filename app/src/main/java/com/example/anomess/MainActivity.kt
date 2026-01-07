package com.example.anomess

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.togetherWith
import com.example.anomess.ui.ChatScreen
import com.example.anomess.ui.ChatViewModel
import com.example.anomess.ui.ChatViewModelFactory
import com.example.anomess.ui.theme.AnomessTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // SECURITY: Prevent screenshots and recent apps preview
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        
        val app = application as AnomessApp
        val viewModel = androidx.lifecycle.ViewModelProvider(
            this, 
            ChatViewModelFactory(app)
        )[ChatViewModel::class.java]

        setContent {
            AnomessTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember { mutableStateOf("contacts") }
                    var selectedContact by remember { mutableStateOf<com.example.anomess.data.Contact?>(null) }
                    
                    androidx.compose.animation.AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            val animationSpec = androidx.compose.animation.core.tween<androidx.compose.ui.unit.IntOffset>(durationMillis = 300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                            val fadeSpec = androidx.compose.animation.core.tween<Float>(durationMillis = 300)
                            
                            if (targetState == "chat") {
                                androidx.compose.animation.slideInHorizontally(animationSpec) { width -> width } + androidx.compose.animation.fadeIn(fadeSpec) togetherWith
                                androidx.compose.animation.slideOutHorizontally(animationSpec) { width -> -width } + androidx.compose.animation.fadeOut(fadeSpec)
                            } else {
                                androidx.compose.animation.slideInHorizontally(animationSpec) { width -> -width } + androidx.compose.animation.fadeIn(fadeSpec) togetherWith
                                androidx.compose.animation.slideOutHorizontally(animationSpec) { width -> width } + androidx.compose.animation.fadeOut(fadeSpec)
                            }
                        },
                        label = "Navigation"
                    ) { screen ->
                        when (screen) {
                            "contacts" -> {
                                com.example.anomess.ui.ContactListScreen(
                                    viewModel = viewModel,
                                    onContactSelected = { contact ->
                                        selectedContact = contact
                                        currentScreen = "chat"
                                    }
                                )
                            }
                            "chat" -> {
                                selectedContact?.let { contact ->
                                    ChatScreen(
                                        viewModel = viewModel,
                                        contact = contact,
                                        onBack = { currentScreen = "contacts" }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
