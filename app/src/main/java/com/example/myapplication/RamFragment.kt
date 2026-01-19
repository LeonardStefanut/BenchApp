package com.example.myapplication

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RamFragment : Fragment() {

    private lateinit var startRamTestButton: Button
    private lateinit var ramStatusTextView: TextView
    private lateinit var ramScoreTextView: TextView
    private lateinit var ramBenchmarkChart: BarChart

    companion object {
        private const val BASELINE_BANDWIDTH_GBs = 1.87
        private const val BASELINE_RAM_SCORE = 1000.0
        private const val MEMORY_SIZE_MB = 64
        private const val MEMORY_SIZE_IN_BYTES = MEMORY_SIZE_MB * 1024 * 1024
        private const val NUM_RUNS = 20
        private const val WARM_UP_RUNS = 3
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_ram, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        startRamTestButton = view.findViewById(R.id.startRamTestButton)
        ramStatusTextView = view.findViewById(R.id.ramStatusTextView)
        ramScoreTextView = view.findViewById(R.id.ramScoreTextView)
        ramBenchmarkChart = view.findViewById(R.id.ramBenchmarkChart)

        startRamTestButton.setOnClickListener {
            runRamBenchmark()
        }

        ramStatusTextView.text = getString(R.string.status_ready)
        ramScoreTextView.text = getString(R.string.initial_text)
    }

    private fun setupChart(bandwidths: List<Double>) {
        val entries = bandwidths.mapIndexed { index, bandwidth ->
            BarEntry(index.toFloat(), bandwidth.toFloat())
        }

        val dataSet = BarDataSet(entries, "Bandwidth (GB/s)").apply {
            color = Color.rgb(63, 81, 181)
            setDrawValues(false)
        }

        ramBenchmarkChart.apply {
            data = BarData(dataSet)
            description.isEnabled = false
            legend.isEnabled = false
            axisRight.isEnabled = false
            axisLeft.setDrawGridLines(false)
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
            }
            animateY(1000)
            invalidate()
        }
    }

    private fun runRamBenchmark() {
        startRamTestButton.isEnabled = false
        ramScoreTextView.text = getString(R.string.loading_text)
        ramBenchmarkChart.data = null
        ramBenchmarkChart.invalidate()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val bandwidths = performBenchmark { progressMessage ->
                    withContext(Dispatchers.Main) {
                        ramStatusTextView.text = progressMessage
                    }
                }

                val medianBandwidth = calculateMedian(bandwidths)
                val ramScore = calculateScore(medianBandwidth)

                ramScoreTextView.text = getString(R.string.score, ramScore.toInt())
                ramStatusTextView.text = "Test Completed!"
                setupChart(bandwidths)
            } catch (t: Throwable) {
                ramStatusTextView.text = "Error: Not enough memory"
                Toast.makeText(requireContext(), "Test failed. Try closing other apps.", Toast.LENGTH_LONG).show()
            } finally {
                startRamTestButton.isEnabled = true
            }
        }
    }

    private suspend fun performBenchmark(onProgress: suspend (String) -> Unit): List<Double> =
        withContext(Dispatchers.Default) {
            onProgress("Allocating memory...")

            for (i in 1..WARM_UP_RUNS) {
                onProgress("Warming up... ($i/$WARM_UP_RUNS)")
                BenchmarkAlgorithms.runRamPass(MEMORY_SIZE_IN_BYTES)
            }

            (1..NUM_RUNS).map { i ->
                onProgress("Running Pass $i/$NUM_RUNS")

                val time = BenchmarkAlgorithms.runRamPass(MEMORY_SIZE_IN_BYTES)

                val totalBytesInRun = (MEMORY_SIZE_IN_BYTES.toLong() * 2)
                val dataGB = totalBytesInRun / (1024.0 * 1024.0 * 1024.0)
                val timeSeconds = time / 1000.0
                if (timeSeconds > 0) dataGB / timeSeconds else 0.0
            }
        }

    private fun calculateMedian(data: List<Double>): Double {
        if (data.isEmpty()) return 0.0
        val sortedData = data.sorted()
        val middle = sortedData.size / 2
        return if (sortedData.size % 2 == 1) {
            sortedData[middle]
        } else {
            (sortedData[middle - 1] + sortedData[middle]) / 2.0
        }
    }

    private fun calculateScore(userBandwidth: Double): Double {
        if (userBandwidth <= 0.0) return 0.0
        return (userBandwidth / BASELINE_BANDWIDTH_GBs) * BASELINE_RAM_SCORE
    }
}