package com.luiscarodev.posticketbridge

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luiscarodev.posticketbridge.data.PrinterDatabase
import com.luiscarodev.posticketbridge.data.PrinterRepository
import com.luiscarodev.posticketbridge.domain.PrinterDefinition
import com.luiscarodev.posticketbridge.domain.PrinterType
import com.luiscarodev.posticketbridge.domain.PrintProfileMode
import com.luiscarodev.posticketbridge.domain.UnicodeFallback
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrinterRepositoryTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PrinterDatabase::class.java,
    )

    @Test
    fun createsUpdatesAndDeletesPrinters() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), PrinterDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            val repository = PrinterRepository(database.printerDao())
            val initial = PrinterDefinition(
                "caja", "Caja", PrinterType.NETWORK, 80, true, true,
                host = "192.168.1.20", port = 9100,
            )
            repository.create(initial)
            assertEquals(initial, repository.find("caja"))
            val duplicate = repository.create(initial.copy(id = "manual", nombre = "Caja"))
            assertEquals("caja-2", duplicate.id)
            assertEquals("caja-2", repository.find("caja-2")?.id)
            repository.update(initial.copy(nombre = "Caja principal", enabled = false))
            assertEquals("Caja principal", repository.find("caja")?.nombre)
            repository.delete("caja")
            assertNull(repository.find("caja"))
            val usb = repository.create(
                PrinterDefinition(
                    "", "USB Caja", PrinterType.USB, 58, false, true,
                    profileMode = PrintProfileMode.CUSTOM,
                    customEncoding = "CP858",
                    customCodeTable = 19,
                    customUnicodeFallback = UnicodeFallback.AUTO,
                    usbVendorId = 0x04b8,
                    usbProductId = 0x0202,
                    usbSerialNumber = "ABC123",
                    usbName = "TM-T20",
                ),
            )
            assertEquals(usb, repository.find("usb-caja"))
        } finally { database.close() }
    }

    @Test
    fun printerSurvivesDatabaseReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val databaseName = "printer-persistence-test.db"
        context.deleteDatabase(databaseName)
        val printer = PrinterDefinition(
            "cocina", "Cocina", PrinterType.BLUETOOTH, 58, false, true,
            bluetoothAddress = "00:11:22:33:44:55", bluetoothName = "POS-58",
        )
        var database = Room.databaseBuilder(context, PrinterDatabase::class.java, databaseName)
            .allowMainThreadQueries().build()
        PrinterRepository(database.printerDao()).create(printer)
        database.close()
        database = Room.databaseBuilder(context, PrinterDatabase::class.java, databaseName)
            .allowMainThreadQueries().build()
        try {
            assertEquals(printer, PrinterRepository(database.printerDao()).find("cocina"))
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun migratesVersionOneWithoutChangingExistingPrinter() {
        val databaseName = "printer-migration-test.db"
        migrationHelper.createDatabase(databaseName, 1).apply {
            execSQL(
                "INSERT INTO printers (id,name,type,widthMm,opensDrawer,enabled,profileId,host,port,bluetoothAddress,bluetoothName) " +
                    "VALUES ('caja','Caja','NETWORK',80,1,1,'epson-escpos-usb','192.168.1.20',9100,NULL,NULL)",
            )
            close()
        }
        migrationHelper.runMigrationsAndValidate(
            databaseName,
            2,
            true,
            PrinterDatabase.MIGRATION_1_2,
        ).use { migrated ->
            migrated.query("SELECT id,profileMode,profileLanguage FROM printers").use { cursor ->
                cursor.moveToFirst()
                assertEquals("caja", cursor.getString(0))
                assertEquals("AUTO", cursor.getString(1))
                assertEquals("ES", cursor.getString(2))
            }
        }
    }
}
