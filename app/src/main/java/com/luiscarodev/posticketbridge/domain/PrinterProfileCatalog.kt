package com.luiscarodev.posticketbridge.domain

data class CatalogText(val es: String, val en: String) {
    fun resolve(language: PrinterLanguage): String = when (language) {
        PrinterLanguage.ES -> es
        PrinterLanguage.EN -> en
    }
}

data class CatalogEncoding(
    val encoding: String,
    val charsetName: String,
    val codeTable: Int,
    val nativePolicy: NativeTextPolicy,
)

data class UsbProfileMatch(val vendorId: Int, val productId: Int? = null)

data class CatalogPrinterProfile(
    val id: String,
    val name: CatalogText,
    val description: CatalogText,
    val selectable: Boolean,
    val paperWidths: Set<Int>? = null,
    val version: Int,
    val ascii: CatalogEncoding,
    val spanishLatin: CatalogEncoding? = null,
    val usbMatches: List<UsbProfileMatch> = emptyList(),
) {
    fun supportsWidth(widthMm: Int): Boolean = paperWidths?.contains(widthMm) != false

    fun encodingFor(language: PrinterLanguage): CatalogEncoding =
        spanishLatin?.takeIf { language == PrinterLanguage.ES } ?: ascii
}

/** Counterpart of desktop's printer-profile-catalog.ts; shared by UI and encoder. */
object PrinterProfileCatalog {
    const val VERSION = 1
    const val DEFAULT_PROFILE_ID = "unlisted-safe"

    val profiles: List<CatalogPrinterProfile> = listOf(
        CatalogPrinterProfile(
            id = DEFAULT_PROFILE_ID,
            name = CatalogText("Automático (recomendado)", "Automatic (recommended)"),
            description = CatalogText(
                "Perfil seguro; rasteriza caracteres incompatibles.",
                "Safe profile; incompatible characters are rasterized.",
            ),
            selectable = true,
            version = VERSION,
            ascii = CatalogEncoding("CP437", "IBM437", 0, NativeTextPolicy.ASCII),
        ),
        CatalogPrinterProfile(
            id = "epson-escpos-usb",
            name = CatalogText("Epson ESC/POS", "Epson ESC/POS"),
            description = CatalogText(
                "Perfil para impresoras Epson ESC/POS.",
                "Profile for Epson ESC/POS printers.",
            ),
            selectable = true,
            version = VERSION,
            ascii = CatalogEncoding("CP437", "IBM437", 0, NativeTextPolicy.ASCII),
            spanishLatin = CatalogEncoding("CP850", "IBM850", 2, NativeTextPolicy.ENCODING),
            usbMatches = listOf(UsbProfileMatch(vendorId = 0x04b8)),
        ),
        CatalogPrinterProfile(
            id = "xprinter-xp-e260l",
            name = CatalogText("XPrinter XP-E260L", "XPrinter XP-E260L"),
            description = CatalogText(
                "Perfil para XPrinter XP-E260L.",
                "Profile for XPrinter XP-E260L.",
            ),
            selectable = true,
            paperWidths = setOf(80),
            version = VERSION,
            ascii = CatalogEncoding("CP437", "IBM437", 0, NativeTextPolicy.ASCII),
            spanishLatin = CatalogEncoding("CP858", "IBM00858", 19, NativeTextPolicy.ENCODING),
        ),
    )

    private val profilesById = profiles.associateBy(CatalogPrinterProfile::id)

    fun get(id: String?): CatalogPrinterProfile =
        profilesById[id] ?: checkNotNull(profilesById[DEFAULT_PROFILE_ID])

    fun selectable(widthMm: Int): List<CatalogPrinterProfile> =
        profiles.filter { it.selectable && it.supportsWidth(widthMm) }

    fun suggestedForUsb(vendorId: Int, productId: Int): CatalogPrinterProfile? =
        profiles.firstOrNull { profile ->
            profile.selectable && profile.usbMatches.any { match ->
                match.vendorId == vendorId && (match.productId == null || match.productId == productId)
            }
        }
}
