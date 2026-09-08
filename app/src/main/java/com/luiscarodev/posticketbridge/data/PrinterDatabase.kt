package com.luiscarodev.posticketbridge.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.luiscarodev.posticketbridge.domain.PrinterDefinition
import com.luiscarodev.posticketbridge.domain.PrinterCatalog
import com.luiscarodev.posticketbridge.domain.NativeTextPolicy
import com.luiscarodev.posticketbridge.domain.PrintProfileMode
import com.luiscarodev.posticketbridge.domain.PrinterLanguage
import com.luiscarodev.posticketbridge.domain.PrinterType
import com.luiscarodev.posticketbridge.domain.UnicodeFallback
import java.security.SecureRandom
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "printers")
data class PrinterEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val widthMm: Int,
    val opensDrawer: Boolean,
    val enabled: Boolean,
    val profileId: String,
    val profileMode: String,
    val profileLanguage: String,
    val customEncoding: String?,
    val customCodeTable: Int?,
    val customUnicodeFallback: String?,
    val customNativePolicy: String?,
    val host: String?,
    val port: Int?,
    val bluetoothAddress: String?,
    val bluetoothName: String?,
    val usbVendorId: Int?,
    val usbProductId: Int?,
    val usbSerialNumber: String?,
    val usbName: String?,
)

@Dao
interface PrinterDao {
    @Query("SELECT * FROM printers ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<PrinterEntity>>

    @Query("SELECT * FROM printers ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<PrinterEntity>

    @Query("SELECT * FROM printers WHERE id = :id LIMIT 1")
    suspend fun find(id: String): PrinterEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(printer: PrinterEntity)

    @Query("DELETE FROM printers WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM printers WHERE id = :id")
    suspend fun count(id: String): Int

    @Query("UPDATE printers SET name=:name, type=:type, widthMm=:widthMm, opensDrawer=:opensDrawer, enabled=:enabled, profileId=:profileId, profileMode=:profileMode, profileLanguage=:profileLanguage, customEncoding=:customEncoding, customCodeTable=:customCodeTable, customUnicodeFallback=:customUnicodeFallback, customNativePolicy=:customNativePolicy, host=:host, port=:port, bluetoothAddress=:bluetoothAddress, bluetoothName=:bluetoothName, usbVendorId=:usbVendorId, usbProductId=:usbProductId, usbSerialNumber=:usbSerialNumber, usbName=:usbName WHERE id=:id")
    suspend fun update(
        id: String,
        name: String,
        type: String,
        widthMm: Int,
        opensDrawer: Boolean,
        enabled: Boolean,
        profileId: String,
        profileMode: String,
        profileLanguage: String,
        customEncoding: String?,
        customCodeTable: Int?,
        customUnicodeFallback: String?,
        customNativePolicy: String?,
        host: String?,
        port: Int?,
        bluetoothAddress: String?,
        bluetoothName: String?,
        usbVendorId: Int?,
        usbProductId: Int?,
        usbSerialNumber: String?,
        usbName: String?,
    ): Int
}

@Database(entities = [PrinterEntity::class], version = 2, exportSchema = true)
abstract class PrinterDatabase : RoomDatabase() {
    abstract fun printerDao(): PrinterDao

    companion object {
        fun create(context: Context): PrinterDatabase = Room.databaseBuilder(
            context,
            PrinterDatabase::class.java,
            "bridge.db",
        ).addMigrations(MIGRATION_1_2).build()

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE printers ADD COLUMN profileMode TEXT NOT NULL DEFAULT 'AUTO'")
                db.execSQL("ALTER TABLE printers ADD COLUMN profileLanguage TEXT NOT NULL DEFAULT 'ES'")
                db.execSQL("ALTER TABLE printers ADD COLUMN customEncoding TEXT")
                db.execSQL("ALTER TABLE printers ADD COLUMN customCodeTable INTEGER")
                db.execSQL("ALTER TABLE printers ADD COLUMN customUnicodeFallback TEXT")
                db.execSQL("ALTER TABLE printers ADD COLUMN customNativePolicy TEXT")
                db.execSQL("ALTER TABLE printers ADD COLUMN usbVendorId INTEGER")
                db.execSQL("ALTER TABLE printers ADD COLUMN usbProductId INTEGER")
                db.execSQL("ALTER TABLE printers ADD COLUMN usbSerialNumber TEXT")
                db.execSQL("ALTER TABLE printers ADD COLUMN usbName TEXT")
            }
        }
    }
}

class PrinterRepository(private val dao: PrinterDao) : PrinterCatalog {
    val printers: Flow<List<PrinterDefinition>> = dao.observeAll().map { rows -> rows.map(::fromEntity) }

    override suspend fun getAll(): List<PrinterDefinition> = dao.getAll().map(::fromEntity)
    override suspend fun find(id: String): PrinterDefinition? = dao.find(id)?.let(::fromEntity)

    suspend fun create(printer: PrinterDefinition): PrinterDefinition {
        val baseId = printerIdSlug(printer.nombre)
        var id = baseId
        var suffix = 2
        while (dao.count(id) != 0) id = "$baseId-${suffix++}"
        val created = printer.copy(id = id)
        validate(created)
        dao.insert(created.toEntity())
        return created
    }

    suspend fun update(printer: PrinterDefinition) {
        validate(printer)
        check(dao.update(
            printer.id, printer.nombre.trim(), printer.tipo.name, printer.anchoMm,
            printer.abreCajon, printer.enabled, printer.profileId, printer.profileMode.name,
            printer.profileLanguage.name, printer.customEncoding, printer.customCodeTable,
            printer.customUnicodeFallback?.name, printer.customNativePolicy?.name,
            printer.host?.trim(), printer.port, printer.bluetoothAddress, printer.bluetoothName,
            printer.usbVendorId, printer.usbProductId, printer.usbSerialNumber, printer.usbName,
        ) == 1) { "printer_not_found" }
    }

    suspend fun delete(id: String) = dao.delete(id)

    private fun validate(printer: PrinterDefinition) {
        require(printer.id.isNotEmpty()) { "invalid_printer_id" }
        require(printer.nombre.trim().isNotEmpty()) { "invalid_printer_name" }
        require(printer.anchoMm == 58 || printer.anchoMm == 80) { "invalid_printer_width" }
        when (printer.tipo) {
            PrinterType.NETWORK -> {
                require(!printer.host.isNullOrBlank()) { "invalid_printer_host" }
                require(printer.port in 1..65535) { "invalid_printer_port" }
            }
            PrinterType.BLUETOOTH -> require(
                printer.bluetoothAddress?.matches(Regex("(?i)[0-9A-F]{2}(:[0-9A-F]{2}){5}")) == true,
            ) { "invalid_bluetooth_address" }
            PrinterType.USB -> {
                require(printer.usbVendorId in 0..65535) { "invalid_usb_device" }
                require(printer.usbProductId in 0..65535) { "invalid_usb_device" }
            }
        }
        if (printer.profileMode == PrintProfileMode.CUSTOM) {
            require(!printer.customEncoding.isNullOrBlank()) { "invalid_profile_encoding" }
            require(printer.customCodeTable in 0..255) { "invalid_profile_code_table" }
            require(printer.customUnicodeFallback != null) { "invalid_unicode_fallback" }
        }
    }

    private fun PrinterDefinition.toEntity() = PrinterEntity(
        id, nombre.trim(), tipo.name, anchoMm, abreCajon, enabled, profileId,
        profileMode.name, profileLanguage.name, customEncoding, customCodeTable,
        customUnicodeFallback?.name, customNativePolicy?.name,
        host?.trim(), port, bluetoothAddress, bluetoothName,
        usbVendorId, usbProductId, usbSerialNumber, usbName,
    )

    private fun fromEntity(row: PrinterEntity) = PrinterDefinition(
        id = row.id,
        nombre = row.name,
        tipo = PrinterType.valueOf(row.type),
        anchoMm = row.widthMm,
        abreCajon = row.opensDrawer,
        enabled = row.enabled,
        profileId = row.profileId,
        profileMode = PrintProfileMode.valueOf(row.profileMode),
        profileLanguage = PrinterLanguage.valueOf(row.profileLanguage),
        customEncoding = row.customEncoding,
        customCodeTable = row.customCodeTable,
        customUnicodeFallback = row.customUnicodeFallback?.let(UnicodeFallback::valueOf),
        customNativePolicy = row.customNativePolicy?.let(NativeTextPolicy::valueOf),
        host = row.host,
        port = row.port,
        bluetoothAddress = row.bluetoothAddress,
        bluetoothName = row.bluetoothName,
        usbVendorId = row.usbVendorId,
        usbProductId = row.usbProductId,
        usbSerialNumber = row.usbSerialNumber,
        usbName = row.usbName,
    )
}

internal fun printerIdSlug(value: String): String {
    val slug = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(Regex("[^\\w\\s-]"), "")
        .trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("[\\s_-]+"), "-")
        .trim('-')
    if (slug.isNotEmpty()) return slug
    val random = ByteArray(3).also(SecureRandom()::nextBytes)
    return "printer-${random.joinToString("") { "%02x".format(it.toInt() and 0xff) }}"
}
