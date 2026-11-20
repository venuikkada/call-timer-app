package com.callgalli.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.callgalli.CallGalliApp
import com.callgalli.data.AppSettings
import com.callgalli.data.NumberToday
import com.callgalli.data.ThemeMode
import com.callgalli.data.TodaySummary
import com.callgalli.rules.Direction
import com.callgalli.rules.Stage
import com.callgalli.rules.TimerBand
import com.callgalli.telecom.InCallState
import com.callgalli.telecom.LiveCall
import com.callgalli.ui.allowed.AlwaysAllowedScreen
import com.callgalli.ui.blocked.BlockedLogScreen
import com.callgalli.ui.home.HomeContent
import com.callgalli.ui.home.SetupStep
import com.callgalli.ui.onboarding.OnboardingScreen
import com.callgalli.ui.settings.SettingsScreen
import com.callgalli.ui.theme.CallGalliTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the main screens to PNG files (app/screenshots/) so the design can be checked
 * without a phone. Run: ./gradlew :app:testDebugUnitTest -Proborazzi.record=true
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h890dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val pad = PaddingValues(top = 24.dp, bottom = 16.dp)

    private fun shot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent {
            CallGalliTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
            }
        }
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    private val numbers = listOf(
        NumberToday("9848022338", "Ravi Kumar", Stage.BLOCKED, 1f, 2, 3, 15 * 60_000L),
        NumberToday("9000012345", "Lakshmi Aunty", Stage.LIMITED, 0.62f, 1, 0, 9 * 60_000L + 20_000),
        NumberToday("8106654321", null, Stage.FRESH, 0.1f, 0, 0, 90_000),
    )

    private val steps = listOf(
        SetupStep(Icons.Outlined.Security, "Call permissions", "Needed to time and end calls", true, true) {},
        SetupStep(Icons.Outlined.Layers, "Show timer over calls", "Floating timer on your call screen", false, false) {},
        SetupStep(Icons.Outlined.BatteryAlert, "No battery limits for Call Timer", "So calls are cut on time", false, false) {},
    )

    private val activeCall = InCallState(
        LiveCall("9000012345", "9000012345", "Lakshmi Aunty", Direction.INCOMING, false, true, 158_000, 600_000, 442_000, TimerBand.GREEN, null, false)
    )

    @Composable
    private fun Home(call: InCallState) = HomeContent(
        contentPadding = pad,
        summary = TodaySummary(callsTaken = 4, talkMillis = 31 * 60_000L, callsBlocked = 3, minutesSaved = 15),
        numbers = numbers,
        inCall = call,
        settings = AppSettings(),
        steps = steps,
        canEndCall = true,
        onHangUp = {},
        onOpenNumber = {},
    )

    @Test fun homeDark() = shot("home_dark", true) { Home(InCallState()) }
    @Test fun homeLight() = shot("home_light", false) { Home(InCallState()) }
    @Test fun homeActiveCall() = shot("home_active_call_dark", true) { Home(activeCall) }

    @Test fun blockedLog() {
        val c = (ApplicationProvider.getApplicationContext<CallGalliApp>()).container
        runBlocking {
            repeat(3) { c.repository.recordBlocked("9848022338", "Ravi Kumar") }
            c.repository.recordBlocked("8106654321", null)
        }
        shot("blocked_dark", true) { BlockedLogScreen(pad, onOpenNumber = {}) }
    }

    @Test fun allowed() {
        val c = (ApplicationProvider.getApplicationContext<CallGalliApp>()).container
        runBlocking {
            c.repository.setAlwaysAllowed("9123456780", "Amma", true)
            c.repository.setAlwaysAllowed("9988776655", "Dr. Reddy Clinic", true)
        }
        shot("allowed_light", false) { AlwaysAllowedScreen(pad) }
    }

    @Test fun settings() = shot("settings_dark", true) { SettingsScreen(pad) }
    @Test fun onboarding() = shot("onboarding_light", false) { OnboardingScreen(onDone = {}) }
}
