package com.example.myapplication

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class StorageBenchmarkResult(val writeSpeed: Double, val readSpeed: Double)

class StorageFragment : Fragment() {

    private lateinit var startStorageTestButton: Button
    private lateinit var storageStatusTextView: TextView
    private lateinit var writeScoreTextView: TextView
    private lateinit var readScoreTextView: TextView
    private lateinit var storageBenchmarkChart: BarChart

    companion object {
        private const val BASELINE_WRITE_SPEED_MBs = 150.0
        private const val BASELINE_READ_SPEED_MBs = 500.0
        private const val BASELINE_STORAGE_SCORE = 1000.0
        private const val TEST_FILE_NAME = "storage_benchmark_temp_file.bin"
        private const val FILE_SIZE_MB = 100 // Dimensiunea fișierului de test
        private const val BLOCK_SIZE_BYTES = 4 * 1024 * 1024 // 4MB buffer
        private const val NUM_RUNS = 5
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_storage, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        startStorageTestButton = view.findViewById(R.id.startStorageTestButton)
        storageStatusTextView = view.findViewById(R.id.storageStatusTextView)
        writeScoreTextView = view.findViewById(R.id.writeScoreTextView)
        readScoreTextView = view.findViewById(R.id.readScoreTextView)
        storageBenchmarkChart = view.findViewById(R.id.storageBenchmarkChart)

        storageStatusTextView.text = getString(R.string.status_ready)
        writeScoreTextView.text = getString(R.string.initial_text)
        readScoreTextView.text = getString(R.string.initial_text)

        startStorageTestButton.setOnClickListener {
            runStorageBenchmark()
        }
    }

    private fun setupChart(results: List<StorageBenchmarkResult>) {
        val writeEntries = results.mapIndexed { index, result -> BarEntry(index.toFloat(), result.writeSpeed.toFloat()) }
        val readEntries = results.mapIndexed { index, result -> BarEntry(index.toFloat(), result.readSpeed.toFloat()) }

        val writeDataSet = BarDataSet(writeEntries, "Write Speed (MB/s)").apply {
            color = Color.rgb(63, 81, 181)
            valueTextSize = 10f
        }

        val readDataSet = BarDataSet(readEntries, "Read Speed (MB/s)").apply {
            color = Color.rgb(3, 169, 244)
            valueTextSize = 10f
        }

        val barData = BarData(writeDataSet, readDataSet)
        storageBenchmarkChart.data = barData

        val groupSpace = 0.3f
        val barSpace = 0.05f
        val barWidth = 0.3f
        barData.barWidth = barWidth

        storageBenchmarkChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            axisMinimum = 0f
            granularity = 1f
            axisMaximum = results.size.toFloat()
            valueFormatter = IndexAxisValueFormatter((1..results.size).map { "Run $it" })
            setCenterAxisLabels(true)
        }

        storageBenchmarkChart.groupBars(0f, groupSpace, barSpace)
        storageBenchmarkChart.description.isEnabled = false
        storageBenchmarkChart.axisRight.isEnabled = false
        storageBenchmarkChart.axisLeft.axisMinimum = 0f
        storageBenchmarkChart.invalidate()
        storageBenchmarkChart.animateY(1000)
    }

    private fun runStorageBenchmark() {
        startStorageTestButton.isEnabled = false
        writeScoreTextView.text = getString(R.string.loading_text)
        readScoreTextView.text = getString(R.string.loading_text)
        storageBenchmarkChart.data = null
        storageBenchmarkChart.invalidate()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val results = performBenchmark { progressMessage ->
                    withContext(Dispatchers.Main) {
                        storageStatusTextView.text = progressMessage
                    }
                }

                val medianWriteSpeed = calculateMedian(results.map { it.writeSpeed })
                val medianReadSpeed = calculateMedian(results.map { it.readSpeed })

                val writeScore = calculateScore(medianWriteSpeed, BASELINE_WRITE_SPEED_MBs)
                val readScore = calculateScore(medianReadSpeed, BASELINE_READ_SPEED_MBs)

                withContext(Dispatchers.Main) {
                    writeScoreTextView.text = getString(R.string.score, writeScore.toInt())
                    readScoreTextView.text = getString(R.string.score, readScore.toInt())
                    storageStatusTextView.text = "Test Completed!"
                    setupChart(results)
                }
            } finally {
                withContext(Dispatchers.Main) {
                    startStorageTestButton.isEnabled = true
                }
            }
        }
    }

    private suspend fun performBenchmark(onProgress: suspend (String) -> Unit): List<StorageBenchmarkResult> =
        withContext(Dispatchers.IO) {
            val testFile = File(requireContext().filesDir, TEST_FILE_NAME)
            val dataChunk = ByteArray(BLOCK_SIZE_BYTES)
            val totalBytes = FILE_SIZE_MB.toLong() * 1024 * 1024

            val results = (1..NUM_RUNS).map { run ->
                onProgress("Running Pass $run/$NUM_RUNS")

                // ✅ APEL CĂTRE LOGICA COMUNĂ: Write
                val writeTime = BenchmarkAlgorithms.runStorageWrite(testFile, dataChunk, totalBytes)
                val writeSpeed = (totalBytes / (1024.0 * 1024.0)) / (writeTime / 1000.0)

                // ✅ APEL CĂTRE LOGICA COMUNĂ: Read
                val readTime = BenchmarkAlgorithms.runStorageRead(testFile, dataChunk)
                val readSpeed = (totalBytes / (1024.0 * 1024.0)) / (readTime / 1000.0)

                // Curățăm fișierul după fiecare run ca să nu umplem memoria
                if (testFile.exists()) testFile.delete()

                StorageBenchmarkResult(writeSpeed, readSpeed)
            }
            results
        }

    private fun calculateMedian(data: List<Double>): Double {
        if (data.isEmpty()) return 0.0
        val sortedData = data.sorted()
        val middle = sortedData.size / 2
        return if (sortedData.size % 2 == 1) sortedData[middle] else (sortedData[middle - 1] + sortedData[middle]) / 2.0
    }

    private fun calculateScore(userSpeed: Double, baselineSpeed: Double): Double {
        if (userSpeed <= 0.0) return 0.0
        return (userSpeed / baselineSpeed) * BASELINE_STORAGE_SCORE
    }
}