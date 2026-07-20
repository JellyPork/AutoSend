package com.autosend

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autosend.ui.OnboardingScreen
import com.autosend.ui.ScheduleEditScreen
import com.autosend.ui.ScheduleListScreen
import com.autosend.ui.ScheduleViewModel
import com.autosend.ui.theme.AutoSendTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AutoSendTheme { AppRoot() }
        }
    }
}

private sealed interface Screen {
    data object List : Screen
    data class Edit(val messageId: Long?) : Screen
    data object Onboarding : Screen
}

@Composable
private fun AppRoot(vm: ScheduleViewModel = viewModel()) {
    var screen by remember { mutableStateOf<Screen>(Screen.List) }

    when (val s = screen) {
        is Screen.List -> ScheduleListScreen(
            vm = vm,
            onAdd = { screen = Screen.Edit(null) },
            onEdit = { id -> screen = Screen.Edit(id) },
            onOpenOnboarding = { screen = Screen.Onboarding },
        )
        is Screen.Edit -> ScheduleEditScreen(
            vm = vm,
            messageId = s.messageId,
            onDone = { screen = Screen.List },
        )
        is Screen.Onboarding -> OnboardingScreen(
            onBack = { screen = Screen.List },
        )
    }
}
