package com.gym.app

import androidx.compose.runtime.Composable
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import kotlin.random.Random

/** iOS has no system Back button; screens' own back arrows (and swipe-back later) handle it. */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

/** Cryptographically strong randomness from the iOS Security framework. */
@OptIn(ExperimentalForeignApi::class)
private object SecureRandom : Random() {
    override fun nextBits(bitCount: Int): Int {
        val bytes = ByteArray(4)
        bytes.usePinned { SecRandomCopyBytes(kSecRandomDefault, 4u, it.addressOf(0)) }
        val value = (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8) or
            ((bytes[2].toInt() and 0xFF) shl 16) or ((bytes[3].toInt() and 0xFF) shl 24)
        return if (bitCount == 0) 0 else value ushr (32 - bitCount)
    }
}

actual fun secureRandom(): Random = SecureRandom
