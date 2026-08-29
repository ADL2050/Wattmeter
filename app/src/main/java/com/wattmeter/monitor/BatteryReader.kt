package com.wattmeter.monitor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * Reads live battery telemetry using only public, non-root Android APIs.
 *
 * Why this works the same on single-cell and dual-cell (2S) phones:
 * Android never exposes per-cell data to apps on any device, single or
 * dual-cell. The battery "fuel gauge" IC on the phone's PCB already
 * combines both cells into one logical battery before anything reaches
 * Android, and BatteryManager reports that combined pack voltage/current.
 * So there is nothing extra to "unlock" for dual-cell phones - the same
 * API call is already correct for both designs. What differs between
 * OEMs is reliability/sign-convention of the values, which is handled below.
 */
data class BatteryReading(
    val voltageVolts: Double,       // pack voltage in volts
    val currentAmps: Double,        // pack current in amps (always reported as a positive
                                     // magnitude here; charging direction is taken from
                                     // BatteryManager.EXTRA_STATUS, not from current sign,
                                     // because current sign convention is inconsistent
                                     // across OEMs - some report + while charging, others -)
    val watts: Double,              // voltage * current
    val isCharging: Boolean,
    val chargeSource: String,       // "USB", "AC / Fast Charger", "Wireless", "Not charging"
    val batteryPercent: Int,
    val temperatureCelsius: Double,
    val currentReliable: Boolean    // false if the device didn't report a usable current value
)

object BatteryReader {

    fun read(context: Context): BatteryReading {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        // Sticky broadcast: always immediately available, no registration delay.
        val intent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val chargeSource = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_AC -> "AC / Fast Charger"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "Not charging"
        }

        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPercent = if (level >= 0 && scale > 0) (level * 100) / scale else -1

        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?: Int.MIN_VALUE
        val temperatureCelsius = if (tempTenths != Int.MIN_VALUE) tempTenths / 10.0 else Double.NaN

        // --- Voltage ---
        // EXTRA_VOLTAGE is in millivolts on essentially every device.
        val voltageMilli = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, Int.MIN_VALUE)
            ?: Int.MIN_VALUE
        val voltageVolts = if (voltageMilli != Int.MIN_VALUE && voltageMilli > 0) {
            voltageMilli / 1000.0
        } else {
            0.0
        }

        // --- Current ---
        // BATTERY_PROPERTY_CURRENT_NOW is in microamps on most devices, but a small
        // number of OEMs (some MediaTek-based phones in particular) misreport it in
        // milliamps. We sanity-check the magnitude to catch that. Some devices return
        // Integer.MIN_VALUE or 0 when the property genuinely isn't supported - in that
        // case we fall back to CURRENT_AVERAGE, and if that also fails we report the
        // reading as unreliable rather than showing a fabricated number.
        var currentMicroAmpsRaw = safeGetProperty(batteryManager, BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        var currentReliable = currentMicroAmpsRaw != null && currentMicroAmpsRaw != Int.MIN_VALUE

        if (!currentReliable) {
            currentMicroAmpsRaw = safeGetProperty(batteryManager, BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
            currentReliable = currentMicroAmpsRaw != null && currentMicroAmpsRaw != Int.MIN_VALUE
        }

        var currentAmps = 0.0
        if (currentReliable && currentMicroAmpsRaw != null) {
            val magnitude = kotlin.math.abs(currentMicroAmpsRaw.toDouble())

            // Heuristic unit correction: a charging phone realistically draws
            // between ~0.05A and ~12A. If treating the raw value as microamps
            // gives an implausibly tiny number (<5mA) while treating it as
            // milliamps gives a plausible one, the device is one of the OEMs
            // that reports in mA instead of µA.
            currentAmps = if (magnitude > 0 && magnitude < 5000) {
                // magnitude in "µA" would be < 5mA total, almost certainly this
                // is actually already in mA
                magnitude / 1000.0
            } else {
                magnitude / 1_000_000.0
            }

            // Final plausibility clamp: reject absurd values (some buggy OEM firmware
            // occasionally reports garbage like -2147483648 already caught above, or
            // wildly large spikes) rather than display something misleading.
            if (currentAmps > 15.0 || currentAmps.isNaN()) {
                currentReliable = false
                currentAmps = 0.0
            }
        }

        val watts = if (currentReliable) voltageVolts * currentAmps else 0.0

        return BatteryReading(
            voltageVolts = voltageVolts,
            currentAmps = currentAmps,
            watts = watts,
            isCharging = isCharging,
            chargeSource = chargeSource,
            batteryPercent = batteryPercent,
            temperatureCelsius = temperatureCelsius,
            currentReliable = currentReliable
        )
    }

    private fun safeGetProperty(bm: BatteryManager, property: Int): Int? {
        return try {
            val value = bm.getIntProperty(property)
            if (value == Int.MIN_VALUE) null else value
        } catch (e: Exception) {
            null
        }
    }
}
