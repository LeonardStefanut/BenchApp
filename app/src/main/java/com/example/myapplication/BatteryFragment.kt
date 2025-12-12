package com.example.myapplication

import android.content.Context
import android.graphics.Color
import android.os.BatteryManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.*
import kotlin.math.floor
import kotlin.math.sqrt

private const val BASELINE_BATTERY_DRAIN_PERCENT = 5.0
private const val BASELINE_BATTERY_SCORE = 1000.0
private const val TEST_DURATION_MINUTES = 15 // Restored to original duration
private const val DATA_POINT_INTERVAL_MINUTES = 1

class BatteryFragment : Fragment() {

    private lateinit var startBatteryTestButton: Button
    private lateinit var batteryStatusTextView: TextView
    private lateinit var batteryScoreTextView: TextView
    private lateinit var batteryBenchmarkChart: LineChart
    private var benchmarkJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_battery, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        startBatteryTestButton = view.findViewById(R.id.startBatteryTestButton)
        batteryStatusTextView = view.findViewById(R.id.batteryStatusTextView)
        batteryScoreTextView = view.findViewById(R.id.batteryScoreTextView)
        batteryBenchmarkChart = view.findViewById(R.id.batteryBenchmarkChart)

        batteryStatusTextView.text = getString(R.string.status_ready)
        batteryScoreTextView.text = getString(R.string.initial_text)

        startBatteryTestButton.setOnClickListener {
            if (benchmarkJob?.isActive == true) {
                stopBatteryBenchmark()
            } else {
                runBatteryBenchmark()
            }
        }
    }

    private fun runBatteryBenchmark() {
        startBatteryTestButton.text = "Stop Test"
        batteryScoreTextView.text = getString(R.string.loading_text)
        batteryBenchmarkChart.clear()

        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val startBatteryLevel = getBatteryLevel()
        if (startBatteryLevel == -1) {
            batteryStatusTextView.text = "Could not get battery level."
            startBatteryTestButton.text = "Start Battery Test"
            return
        }

        val batteryHistory = mutableListOf<Entry>()

        benchmarkJob = viewLifecycleOwner.lifecycleScope.launch {
            val stressJob = launch(Dispatchers.Default) {
                while (isActive) {
                    calculatePrimes(50000)
                }
            }

            val testDurationMs = TEST_DURATION_MINUTES * 60 * 1000
            val startTime = System.currentTimeMillis()
            val endTime = startTime + testDurationMs
            val intervalMs = DATA_POINT_INTERVAL_MINUTES * 60 * 1000
            var nextDataLogTime = startTime

            // Pre-configure the chart for the test
            withContext(Dispatchers.Main) {
                setupChart(emptyList(), isLastPoint = false, startBatteryLevel)
            }

            while (isActive && System.currentTimeMillis() < endTime) {
                val currentTime = System.currentTimeMillis()

                val remainingSeconds = (endTime - currentTime) / 1000
                withContext(Dispatchers.Main) {
                    if (remainingSeconds >= 0) {
                        batteryStatusTextView.text = "Test running... (${remainingSeconds / 60}:${String.format("%02d", remainingSeconds % 60)})"
                    }
                }

                if (currentTime >= nextDataLogTime) {
                    val elapsedMinutes = (currentTime - startTime) / 60000f
                    val currentLevel = getBatteryLevel()
                    withContext(Dispatchers.Main) {
                        batteryHistory.add(Entry(elapsedMinutes, currentLevel.toFloat()))
                        setupChart(batteryHistory, isLastPoint = false, startBatteryLevel)
                    }
                    nextDataLogTime += intervalMs
                }

                delay(1000)
            }

            stressJob.cancel()

            withContext(Dispatchers.Main) {
                onBenchmarkFinished(startBatteryLevel)
                setupChart(batteryHistory, isLastPoint = true, startBatteryLevel)
            }
        }
    }

    private fun stopBatteryBenchmark() {
        benchmarkJob?.cancel()
        onBenchmarkFinished(getBatteryLevel(), stopped = true)
    }

    private fun onBenchmarkFinished(startLevel: Int, stopped: Boolean = false) {
        val endLevel = getBatteryLevel()
        val drain = if (endLevel != -1) startLevel - endLevel else 0
        val score = calculateScore(drain.toDouble())

        batteryScoreTextView.text = getString(R.string.score, score.toInt())
        batteryStatusTextView.text = if (stopped) "Test Stopped. (Drained: $drain%)" else "Test Completed! (Drained: $drain%)"
        startBatteryTestButton.text = "Start Battery Test"
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun setupChart(data: List<Entry>, isLastPoint: Boolean, startLevel: Int) {
        val dataSet = LineDataSet(data, "Battery Level").apply {
            color = Color.rgb(255, 82, 82)
            setDrawCircles(false)
            lineWidth = 2.5f
            mode = LineDataSet.Mode.CUBIC_BEZIER

            setDrawValues(false)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val entry = data.find { it.y == value }
                    val isFirst = entry == data.firstOrNull()
                    val isLast = entry == data.lastOrNull() && isLastPoint

                    return if (isFirst || isLast) "${value.toInt()}%" else ""
                }
            }
            valueTextSize = 12f
            valueTextColor = Color.DKGRAY
        }

        batteryBenchmarkChart.apply {
            isDragEnabled = false
            setScaleEnabled(false)
            setPinchZoom(false)
            isDoubleTapToZoomEnabled = false

            description.isEnabled = false
            legend.isEnabled = false
            axisRight.isEnabled = false

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                setDrawLabels(false)
                axisMinimum = 0f
                axisMaximum = TEST_DURATION_MINUTES.toFloat()
            }

            axisLeft.apply {
                setDrawGridLines(false)
                textColor = Color.BLACK
                axisMaximum = startLevel + 5f
                axisMinimum = startLevel - 5f
            }

            this.data = LineData(dataSet)
            invalidate()
        }
    }

    private fun getBatteryLevel(): Int {
        val batteryManager = requireContext().getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun calculatePrimes(upperBound: Int) {
        for (number in 2..upperBound) {
            var isPrime = true
            val limit = floor(sqrt(number.toDouble())).toInt()
            for (factor in 2..limit) {
                if (number % factor == 0) {
                    isPrime = false
                    break
                }
            }
        }
    }

    private fun calculateScore(drain: Double): Double {
        if (drain <= 0) return (BASELINE_BATTERY_DRAIN_PERCENT / 0.1) * BASELINE_BATTERY_SCORE
        return (drain / BASELINE_BATTERY_DRAIN_PERCENT) * BASELINE_BATTERY_SCORE
    }

    override fun onPause() {
        super.onPause()
        benchmarkJob?.cancel() // Stop benchmark if user navigates away
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
