package com.wattmeter.monitor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.wattmeter.monitor.databinding.ActivityMainBinding
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val handler = Handler(Looper.getMainLooper())
    private var isServiceRunning = false

    private val updateRunnable = object : Runnable {
        override fun run() {
            refreshReading()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001
                )
            }
        }

        binding.btnToggleService.setOnClickListener {
            isServiceRunning = !isServiceRunning
            if (isServiceRunning) {
                val intent = Intent(this, ChargeMonitorService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                binding.btnToggleService.text = "Stop live notification"
            } else {
                stopService(Intent(this, ChargeMonitorService::class.java))
                binding.btnToggleService.text = "Show live wattage in notification"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(updateRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateRunnable)
    }

    private fun refreshReading() {
        val reading = BatteryReader.read(this)

        binding.tvChargeSource.text = if (reading.isCharging) {
            "Charging via ${reading.chargeSource}"
        } else {
            "Not charging"
        }

        if (reading.isCharging && reading.currentReliable) {
            binding.tvWatts.text = String.format(Locale.US, "%.1f W", reading.watts)
            binding.tvReliabilityNote.visibility = android.view.View.GONE
        } else if (reading.isCharging && !reading.currentReliable) {
            binding.tvWatts.text = "-- W"
            binding.tvReliabilityNote.visibility = android.view.View.VISIBLE
            binding.tvReliabilityNote.text =
                "This device doesn't expose live current draw through Android's public API"
        } else {
            binding.tvWatts.text = "0.0 W"
            binding.tvReliabilityNote.visibility = android.view.View.GONE
        }

        binding.tvVoltage.text = String.format(Locale.US, "%.2f V", reading.voltageVolts)
        binding.tvCurrent.text = if (reading.currentReliable) {
            String.format(Locale.US, "%.2f A", reading.currentAmps)
        } else {
            "N/A"
        }
        binding.tvBatteryPercent.text = "${reading.batteryPercent}%"
        binding.tvTemperature.text = if (!reading.temperatureCelsius.isNaN()) {
            String.format(Locale.US, "%.1f °C", reading.temperatureCelsius)
        } else {
            "N/A"
        }
    }
}
