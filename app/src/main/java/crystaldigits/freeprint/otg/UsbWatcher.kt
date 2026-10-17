package crystaldigits.freeprint.otg

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat

/**
 * Calls [onChange] whenever any USB device is plugged in or unplugged, so the screen can follow the printer in real time
 * instead of waiting for a manual refresh. Delivered on the main thread. Call [stop] when the owner goes away.
 */
class UsbWatcher(private val context: Context, private val onChange: () -> Unit) {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onChange()
    }

    init {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        // System broadcasts still arrive with RECEIVER_NOT_EXPORTED; other apps cannot send this one to us.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun stop() { runCatching { context.unregisterReceiver(receiver) } }
}
