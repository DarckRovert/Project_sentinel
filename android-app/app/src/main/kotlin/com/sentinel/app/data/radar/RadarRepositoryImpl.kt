package com.sentinel.app.data.radar

import android.content.Context
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import com.sentinel.app.domain.model.RadarTarget
import com.sentinel.app.domain.repository.RadarRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import timber.log.Timber
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación concreta de [RadarRepository] para el radar HLK-LD2450.
 *
 * Usa `usb-serial-for-android` (Felhr85) para comunicación USB OTG.
 * Los bytes se acumulan en [Ld2450FrameParser] y los frames válidos se
 * emiten como [Flow<List<RadarTarget>>].
 *
 * ## Parámetros del puerto serial del LD2450:
 * - Baud rate:  256000
 * - Data bits:  8
 * - Stop bits:  1
 * - Parity:     None
 */
@Singleton
class RadarRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : RadarRepository, SerialInputOutputManager.Listener {

    companion object {
        private const val BAUD_RATE = 256_000
        private const val READ_TIMEOUT_MS = 200
        private const val WRITE_TIMEOUT_MS = 200
    }

    private val parser = Ld2450FrameParser()
    private val _targetChannel = Channel<List<RadarTarget>>(Channel.CONFLATED)
    private var ioManager: SerialInputOutputManager? = null
    private val ioExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "sentinel-radar-io")
    }

    override val isConnected: Boolean
        get() = ioManager?.state == SerialInputOutputManager.State.RUNNING

    override fun radarTargetsFlow(): Flow<List<RadarTarget>> =
        _targetChannel.receiveAsFlow()

    override suspend fun connect() {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

        if (availableDrivers.isEmpty()) {
            Timber.e("RadarRepository: No se encontró ningún driver USB compatible")
            throw IllegalStateException("No hay dispositivo USB-Serial conectado")
        }

        val driver = availableDrivers.first()
        val connection = usbManager.openDevice(driver.device)
            ?: throw SecurityException("Sin permiso USB para el dispositivo. El usuario debe aprobar el diálogo.")

        val port = driver.ports.first()
        port.open(connection)
        port.setParameters(BAUD_RATE, 8, 1, com.hoho.android.usbserial.driver.UsbSerialPort.PARITY_NONE)

        parser.reset()

        ioManager = SerialInputOutputManager(port, this).also {
            ioExecutor.submit(it)
        }
        Timber.i("RadarRepository: Radar HLK-LD2450 conectado @ ${BAUD_RATE} baud")
    }

    override suspend fun disconnect() {
        ioManager?.stop()
        ioManager = null
        parser.reset()
        Timber.i("RadarRepository: Radar desconectado")
    }

    // ----------------------------------------------------------------
    // SerialInputOutputManager.Listener
    // ----------------------------------------------------------------

    override fun onNewData(data: ByteArray) {
        val frames = parser.feed(data)
        frames.forEach { targets ->
            _targetChannel.trySend(targets)
        }
    }

    override fun onRunError(e: Exception) {
        Timber.e(e, "RadarRepository: Error en el IO del radar")
        // El ForegroundService detectará isConnected == false y re-intentará
    }
}
