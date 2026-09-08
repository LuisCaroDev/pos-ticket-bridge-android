package com.luiscarodev.posticketbridge.printing

import com.luiscarodev.posticketbridge.contract.CutBlock
import com.luiscarodev.posticketbridge.contract.FeedBlock
import com.luiscarodev.posticketbridge.contract.PrintJobV1
import com.luiscarodev.posticketbridge.contract.SeparatorBlock
import com.luiscarodev.posticketbridge.contract.TextBlock
import com.luiscarodev.posticketbridge.domain.PrinterDefinition
import com.luiscarodev.posticketbridge.domain.PrinterLanguage
import java.text.DateFormat
import java.util.Date

internal fun mobileTestPrintJob(
    printer: PrinterDefinition,
    printedAt: String = localizedTestTimestamp(),
): PrintJobV1 {
    val spanish = printer.profileLanguage == PrinterLanguage.ES
    val subtitle = if (spanish) "Prueba de impresión" else "Print test"
    val printerLabel = if (spanish) "Impresora" else "Printer"
    val characterGroups = buildList {
        add("ASCII: ABCDEFGHIJKLMNOPQRSTUVWXYZ 0123456789 .,:;!?+-*/")
        if (spanish) add("Español: áéíóúüñÑ ÁÉÍÓÚÜ ¿¡")
        add(
            if (spanish) "Símbolos: € $ S/ % # @ & / \\ ( ) [ ] { }"
            else "Symbols: € $ S/ % # @ & / \\ ( ) [ ] { }",
        )
    }.joinToString("\n")

    return PrintJobV1(
        version = 1,
        widthMm = printer.anchoMm,
        reason = "test",
        blocks = listOf(
            TextBlock(
                content = "POS TICKET BRIDGE",
                align = "center",
                bold = true,
                font = "standard",
                width = 2,
                height = 2,
            ),
            TextBlock(content = "mobile", align = "center", font = "compact"),
            TextBlock(content = subtitle, align = "center", font = "compact"),
            SeparatorBlock(style = "solid"),
            TextBlock(content = "$printerLabel: ${printer.nombre}"),
            TextBlock(content = characterGroups),
            TextBlock(content = printedAt),
            FeedBlock(lines = 3.0),
            CutBlock(),
        ),
    )
}

private fun localizedTestTimestamp(): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date())
