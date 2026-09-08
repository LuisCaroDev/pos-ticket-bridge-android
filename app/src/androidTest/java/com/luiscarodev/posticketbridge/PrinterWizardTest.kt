package com.luiscarodev.posticketbridge

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test

class PrinterWizardTest {
    @get:Rule(order = 0)
    val notificationPermission: GrantPermissionRule =
        GrantPermissionRule.grant(
            Manifest.permission.POST_NOTIFICATIONS,
            *if (android.os.Build.VERSION.SDK_INT >= 37) {
                arrayOf(Manifest.permission.ACCESS_LOCAL_NETWORK)
            } else emptyArray(),
        )

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun manualNetworkFlowReachesCompactSecondStep() {
        compose.onNodeWithContentDescription("Agregar impresora").performClick()
        compose.onNodeWithText("¿Cómo está conectada?").assertIsDisplayed()
        compose.onNodeWithText("Red", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Ingresar IP manualmente").assertIsDisplayed()
        compose.onNodeWithTag("network_host").performTextInput("192.168.1.50")
        compose.onNodeWithText("Usar esta dirección").performClick()
        compose.onNodeWithText("Paso 2 de 2").assertIsDisplayed()
        compose.onNodeWithText("Perfil de impresión").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Automático (recomendado)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("CP437").assertDoesNotExist()
        compose.onNodeWithTag("profile_selector").performScrollTo().performClick()
        compose.onNodeWithText("Personalizado").performClick()
        compose.onNodeWithTag("custom_profile_settings").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("CP437").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("test_printer_configuration").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Prueba enviada al transporte.").assertDoesNotExist()
    }
}
