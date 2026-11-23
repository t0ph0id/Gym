package com.gym.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.gym.app.GymViewModel
import com.gym.app.LocalAppPlatform
import com.gym.app.LocalGoogleAuth

@Composable
fun OnboardingScreen(vm: GymViewModel, initialName: String) {
    val busy by vm.busy.collectAsState()
    var name by rememberSaveable { mutableStateOf(initialName) }
    var code by rememberSaveable { mutableStateOf("") }
    var confirmCreate by rememberSaveable { mutableStateOf(false) }
    val googleAuth = LocalGoogleAuth.current
    val platform = LocalAppPlatform.current

    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Icon(Icons.Default.FitnessCenter, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Text("Pair up", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text(
            "You train as a pair: one of you starts it, the other joins with a code.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(24) },
            label = { Text("Your first name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )

        // Joining comes first: starting a second pair by mistake was the easy wrong turn.
        Text(
            "Has your partner already started? Enter their code",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp),
        )
        OutlinedTextField(
            value = code,
            onValueChange = { v -> code = v.uppercase().filter { it.isLetterOrDigit() }.take(6) },
            label = { Text("Pair code (6 letters)") },
            supportingText = { Text("It's on their Home screen and in Settings.") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            textStyle = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { vm.joinPair(name, code) },
            enabled = name.isNotBlank() && code.length == 6 && !busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Join my partner") }

        Row(verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f))
            Text(
                "  first of you to set up?  ",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.weight(1f))
        }

        OutlinedButton(
            onClick = { confirmCreate = true },
            enabled = name.isNotBlank() && !busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Start a new pair") }

        if (confirmCreate) {
            AlertDialog(
                onDismissRequest = { confirmCreate = false },
                title = { Text("Start a new pair?") },
                text = {
                    Text(
                        "Only one of you does this. You'll get a code for your partner to join with. " +
                            "If they've already started, go back and enter their code instead."
                    )
                },
                confirmButton = { TextButton(onClick = { confirmCreate = false; vm.createPair(name) }) { Text("Start") } },
                dismissButton = { TextButton(onClick = { confirmCreate = false }) { Text("Back") } },
            )
        }

        if (busy) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        }

        TextButton(
            onClick = { vm.signOut(googleAuth, platform) },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) { Text("Use a different Google account") }
    }
}
