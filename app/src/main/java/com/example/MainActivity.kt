package com.example

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.repository.BrowserPreferencesRepository
import com.example.data.repository.BrowserSyncRepository
import com.example.engine.TurboDownloadEngine
import com.example.engine.VistaAiSearchEngine
import com.example.engine.configureAppCheck
import com.example.ui.BrowserMainScreen
import com.example.ui.BrowserViewModel
import com.example.ui.auth.AuthScreen
import com.example.ui.theme.AegisBrowserTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI, ViewModels, or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        val initialIntentUrl = intent?.dataString

        setContent {
            val context = LocalContext.current
            val prefsRepo = remember { BrowserPreferencesRepository(context.applicationContext) }
            val prefs by prefsRepo.preferencesFlow.collectAsStateWithLifecycle(
                initialValue = com.example.data.repository.BrowserPreferences()
            )

            // Default is Light mode (prefs.isDarkTheme == false by default), user can toggle Light/Dark anytime
            AegisBrowserTheme(darkTheme = prefs.isDarkTheme) {
                VistaRootNavigation(
                    initialExternalUrl = initialIntentUrl,
                    onToggleSystemFullScreen = { fullScreen ->
                        val window = this@MainActivity.window
                        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                        insetsController.systemBarsBehavior =
                            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        if (fullScreen) {
                            insetsController.hide(WindowInsetsCompat.Type.systemBars())
                        } else {
                            insetsController.show(WindowInsetsCompat.Type.systemBars())
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun VistaRootNavigation(
    initialExternalUrl: String? = null,
    onToggleSystemFullScreen: (Boolean) -> Unit
) {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    if (currentUser == null) {
        AuthScreen(
            onAuthSuccess = {
                currentUser = Firebase.auth.currentUser
            }
        )
    } else {
        val uid = currentUser!!.uid
        key(uid) {
            AuthenticatedBrowserSession(
                currentUserId = uid,
                initialExternalUrl = initialExternalUrl,
                onToggleSystemFullScreen = onToggleSystemFullScreen
            )
        }
    }
}

@Composable
private fun AuthenticatedBrowserSession(
    currentUserId: String,
    initialExternalUrl: String?,
    onToggleSystemFullScreen: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val browserViewModel: BrowserViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                BrowserViewModel(
                    syncRepository = BrowserSyncRepository(db),
                    preferencesRepository = BrowserPreferencesRepository(app),
                    downloadEngine = TurboDownloadEngine(app),
                    aiSearchEngine = VistaAiSearchEngine(),
                    currentUserId = currentUserId
                )
            }
        }
    )

    var hasHandledInitialUrl by remember { mutableStateOf(false) }
    if (!hasHandledInitialUrl && !initialExternalUrl.isNullOrBlank()) {
        hasHandledInitialUrl = true
        browserViewModel.loadUrlOrSearchInActiveTab(initialExternalUrl)
    }

    BrowserMainScreen(
        viewModel = browserViewModel,
        onToggleSystemFullScreen = onToggleSystemFullScreen
    )
}
