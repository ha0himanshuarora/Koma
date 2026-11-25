package app.koma.launcher.data

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** A connected Bluetooth device. [battery] is a percentage, or -1 when the device does not report one. */
data class BtDevice(val name: String, val battery: Int, val kind: String) {
    val typeLabel: String
        get() = when (kind) {
            "earbuds" -> "Earbuds"
            "headphones" -> "Headphones"
            "headset" -> "Headset"
            "speaker" -> "Speaker"
            "car" -> "Car"
            "phone" -> "Phone"
            "watch" -> "Watch"
            "computer" -> "Computer"
            "tv" -> "TV"
            "keyboard" -> "Keyboard"
            "mouse" -> "Mouse"
            else -> "Device"
        }
}

object Bluetooth {
    fun granted(ctx: Context) =
        Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun adapter(ctx: Context): BluetoothAdapter? = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    @Suppress("MissingPermission")
    fun enabled(ctx: Context): Boolean = try { granted(ctx) && adapter(ctx)?.isEnabled == true } catch (e: Exception) { false }

    /** What the device is: earbuds, headphones, headset, speaker, car, phone, watch, computer, tv, keyboard, mouse or other. */
    @Suppress("MissingPermission")
    private fun kindOf(d: BluetoothDevice, name: String): String = try {
        val n = name.lowercase()
        val c = d.bluetoothClass
        val minor = c?.deviceClass ?: 0
        when {
            listOf("airpods", "buds", "earbud", "earphone", "pods", "wf-", "liberty", "elite 7", "elite 8").any { n.contains(it) } -> "earbuds"
            listOf("headphone", "wh-", "quietcomfort", "momentum", "px7", "px8").any { n.contains(it) } -> "headphones"
            listOf("speaker", "soundbar", "boombox", "flip", "charge ", "echo", "homepod").any { n.contains(it) } -> "speaker"
            listOf("watch", "band", "fit ").any { n.contains(it) } && c?.majorDeviceClass != BluetoothClass.Device.Major.AUDIO_VIDEO -> "watch"
            minor == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET -> "earbuds"
            minor == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES -> "headphones"
            minor == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE -> "headset"
            minor == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO || n.contains("car") -> "car"
            minor == BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER || minor == BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO ||
                minor == BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO -> "speaker"
            minor == BluetoothClass.Device.AUDIO_VIDEO_VIDEO_MONITOR || minor == BluetoothClass.Device.AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER -> "tv"
            c?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO -> "speaker"
            c?.majorDeviceClass == BluetoothClass.Device.Major.PHONE -> "phone"
            c?.majorDeviceClass == BluetoothClass.Device.Major.COMPUTER -> "computer"
            c?.majorDeviceClass == BluetoothClass.Device.Major.WEARABLE -> "watch"
            c?.majorDeviceClass == BluetoothClass.Device.Major.PERIPHERAL -> if ((minor and 0x40) != 0) "keyboard" else "mouse"
            else -> "other"
        }
    } catch (e: Exception) {
        "other"
    }

    /** The battery level, through the platform call that most phones still allow. -1 when unknown. */
    private fun batteryOf(d: BluetoothDevice): Int = try {
        val v = d.javaClass.getMethod("getBatteryLevel").invoke(d) as? Int ?: -1
        if (v in 0..100) v else -1
    } catch (e: Throwable) {
        -1
    }

    @Suppress("MissingPermission")
    private suspend fun fromProfile(ctx: Context, ad: BluetoothAdapter, profile: Int): List<BluetoothDevice> {
        return withTimeoutOrNull(2000L) {
            suspendCancellableCoroutine { cont ->
                val ok = ad.getProfileProxy(ctx, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                        val list = try { proxy.connectedDevices } catch (e: Exception) { emptyList() }
                        try { ad.closeProfileProxy(p, proxy) } catch (e: Exception) { }
                        if (cont.isActive) cont.resume(list)
                    }

                    override fun onServiceDisconnected(p: Int) {}
                }, profile)
                if (!ok && cont.isActive) cont.resume(emptyList())
            }
        } ?: emptyList()
    }

    /** Every device that is connected right now, audio gear first. Empty without permission or when Bluetooth is off. */
    @Suppress("MissingPermission")
    suspend fun connected(ctx: Context): List<BtDevice> {
        if (!enabled(ctx)) return emptyList()
        val ad = adapter(ctx) ?: return emptyList()
        val seen = LinkedHashMap<String, BluetoothDevice>()
        for (profile in listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET, BluetoothProfile.GATT)) {
            try {
                fromProfile(ctx, ad, profile).forEach { seen.putIfAbsent(it.address, it) }
            } catch (e: Exception) {
            }
        }
        return seen.values.map { d ->
            val name = try { d.name } catch (e: Exception) { null } ?: "Device"
            BtDevice(name, batteryOf(d), kindOf(d, name))
        }.sortedBy { if (it.kind in listOf("earbuds", "headphones", "headset", "speaker", "car")) 0 else 1 }
    }
}
