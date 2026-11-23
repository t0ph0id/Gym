package com.gym.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gym.app.ui.Appearance
import com.gym.app.ui.CustomCatalog
import com.gym.app.ui.LocalAppearance
import com.gym.app.ui.isAppDark
import com.gym.app.ui.GymTheme
import com.gym.app.ui.GymsScreen
import com.gym.app.ui.HomeScreen
import com.gym.app.ui.LocalCustomCatalog
import com.gym.app.ui.OnboardingScreen
import com.gym.app.ui.PlanEditorScreen
import com.gym.app.ui.ProgressScreen
import com.gym.app.ui.SettingsScreen
import com.gym.app.ui.SignInScreen
import com.gym.app.ui.WorkoutScreen
import kotlinx.coroutines.delay

val LocalGoogleAuth = staticCompositionLocalOf<GoogleAuth> { error("No GoogleAuth provided") }
val LocalAppPlatform = staticCompositionLocalOf<AppPlatform> { error("No AppPlatform provided") }

@Composable
fun App(firebaseReady: Boolean, googleAuth: GoogleAuth, platform: AppPlatform) {
    val appearance = remember { Appearance(platform) }
    val dark = isAppDark(appearance)
    LaunchedEffect(dark) { platform.setDarkSystemBars(dark) }
    GymTheme(appearance) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            CompositionLocalProvider(
                LocalGoogleAuth provides googleAuth,
                LocalAppPlatform provides platform,
                LocalAppearance provides appearance,
            ) {
                if (firebaseReady) Root(viewModel { GymViewModel() }) else FirebaseMissing()
            }
        }
    }
}

@Composable
private fun Root(vm: GymViewModel) {
    val session by vm.session.collectAsState()
    val message by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    val undo by vm.undoNotice.collectAsState()
    LaunchedEffect(undo) {
        val notice = undo ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(notice.text, actionLabel = "Undo", duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) notice.undo()
        if (vm.undoNotice.value === notice) vm.undoNotice.value = null
    }

    val restEndsAt by vm.rest.endsAt.collectAsState()
    val platform = LocalAppPlatform.current
    val customExercises by vm.ownExercises.list.collectAsState()
    val customPhotos by vm.ownExercises.photos.collectAsState()
    val customCatalog = remember(customExercises, customPhotos) {
        CustomCatalog(
            byKey = customExercises.associate { exerciseKey(it.name) to it.toCatalog() },
            photos = customPhotos,
            setPhoto = vm.ownExercises::setPhoto,
            delete = vm.ownExercises::delete,
            edit = vm.ownExercises::edit,
        )
    }
    LaunchedEffect(restEndsAt) {
        platform.setRestAlarm(restEndsAt) // countdown notification + buzz while in the background
        val end = restEndsAt ?: return@LaunchedEffect
        while (nowMillis() < end) delay(200)
        platform.vibrate()
        delay(5_000)
        vm.rest.stop(onlyIfEndsAt = end)
    }

    CompositionLocalProvider(LocalCustomCatalog provides customCatalog) {
    Box(Modifier.fillMaxSize()) {
        when {
            session.loading -> Connecting()
            session.uid == null -> SignInScreen(vm)
            session.profile?.partnershipId == null ->
                OnboardingScreen(vm, session.profile?.name ?: vm.googleDisplayName())
            else -> {
                PlatformBackHandler(enabled = vm.stack.size > 1) { vm.back() }
                when (val screen = vm.stack.last()) {
                    Screen.Home -> HomeScreen(vm)
                    is Screen.EditPlan -> PlanEditorScreen(vm, screen.planId)
                    is Screen.Workout -> WorkoutScreen(vm, screen.workoutId)
                    Screen.Gyms -> GymsScreen(vm)
                    Screen.Settings -> SettingsScreen(vm)
                    is Screen.Progress -> ProgressScreen(vm, screen.exerciseKey)
                }
            }
        }
        // Above the rest bar when it's showing.
        val restBarShowing = restEndsAt != null && vm.stack.lastOrNull() is Screen.Workout
        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (restBarShowing) 64.dp else 0.dp),
        )
    }
    }
}

/** Spinner that explains itself if the first answer from the server takes too long (e.g. offline). */
@Composable
private fun Connecting() {
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(8_000); slow = true }
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        if (slow) {
            Text(
                "Still connecting… The first start needs internet. After that the app works offline.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun CenteredMessage(title: String, body: String) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FirebaseMissing() = CenteredMessage(
    "Firebase isn't set up yet",
    "Put google-services.json in the composeApp folder and rebuild. The README has the steps.",
)
