package com.gym.app

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)

private val secure: Random = SecureRandom().asKotlinRandom()

actual fun secureRandom(): Random = secure
