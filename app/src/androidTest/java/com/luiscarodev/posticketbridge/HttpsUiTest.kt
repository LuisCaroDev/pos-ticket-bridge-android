package com.luiscarodev.posticketbridge

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.luiscarodev.posticketbridge.bridge.https.*
import com.luiscarodev.posticketbridge.ui.HttpsSetupScreen
import com.luiscarodev.posticketbridge.ui.theme.POSTicketBridgeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class HttpsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun windowsShowsManualRootStepsAndConfiguredHelp() {
        val setup = HttpsSetupConfiguration(mapOf("POS_BRIDGE_HTTPS_SETUP_TTL_MS" to "90000",
            "POS_BRIDGE_HTTPS_VIDEO_WINDOWS" to "https://example.com/windows-video",
            "POS_BRIDGE_HTTPS_GUIDE_WINDOWS" to "https://example.com/windows-guide"))
        val status = mutableStateOf(HttpsStatus(loaded = true, transport = "https", host = "https://192.168.1.10:9977"))
        compose.setContent {
            POSTicketBridgeTheme {
                HttpsSetupScreen(status.value, false, null, {}, { os ->
                    status.value = status.value.copy(enrollment = EnrollmentSession(
                        "http://192.168.1.10:9978/setup/${os.filename}", System.currentTimeMillis() + setup.durationMs, os))
                }, {}, setup)
            }
        }
        compose.onNodeWithText("Al continuar, la descarga del certificado público estará disponible durante 90 segundos.").assertExists()
        compose.onNodeWithText("Windows").performClick()
        compose.onNodeWithText("Continuar").performScrollTo().performClick()
        compose.onNodeWithText("Usuario actual", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Colocar todos los certificados", substring = true).assertExists()
        compose.onNodeWithText("Entidades de certificación raíz de confianza", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1.").assertExists()
        compose.onNodeWithText("5.").assertExists()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "https-windows-steps.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("Mira el video").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ver guía de instalación").assertExists()
        compose.onNodeWithText("Guía oficial de Windows").performScrollTo().assertIsDisplayed()
    }

    @Test fun assistantChoosesOsShowsQrAndClosesSession() {
        val status = mutableStateOf(HttpsStatus(loaded = true, enabled = true, configured = true,
            transport = "https", host = "https://192.168.1.10:9977"))
        val visible = mutableStateOf(true)
        var stops = 0
        compose.setContent {
            POSTicketBridgeTheme {
                if (visible.value) HttpsSetupScreen(status.value, false, null, { visible.value = false }, { os ->
                    status.value = status.value.copy(enrollment = EnrollmentSession(
                        "http://192.168.1.10:9978/setup/${os.filename}", System.currentTimeMillis() + ENROLLMENT_DURATION_MS, os))
                }, { stops++; status.value = status.value.copy(enrollment = null) })
            }
        }
        compose.onNodeWithText("iPhone / iPad").performClick()
        compose.onNodeWithText("Continuar").performScrollTo().performClick()
        compose.onNodeWithText("Escanea desde iPhone / iPad").assertExists()
        compose.onNodeWithText("http://192.168.1.10:9978/setup/ios.mobileconfig").assertExists()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("QR para descargar la CA pública POS Ticket Bridge mobile")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "https-setup.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.runOnIdle { assertEquals(1, stops) }
    }
}
