package com.example.myapplication

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.*
import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.random.Random

private const val BASELINE_INTEGER_TIME_MS = 2000.0
private const val BASELINE_INTEGER_SCORE = 1000.0
private const val BASELINE_FLOATING_POINT_TIME_MS = 1500.0
private const val BASELINE_FLOATING_POINT_SCORE = 1000.0

class CpuFragment : Fragment() {

    private lateinit var integerTestButton: Button
    private lateinit var floatingPointTestButton: Button
    private lateinit var integerScoreTextView: TextView
    private lateinit var floatingPointScoreTextView: TextView
    private lateinit var rawTimeTextView: TextView
    private lateinit var statusTextView: TextView
    private lateinit var cpuBenchmarkChart: BarChart

    private val integerWorkSize = 1500000
    private val matrixSize = 200
    private val numRuns = 20

    enum class TestType { INTEGER, FLOATING_POINT }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_cpu, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        integerTestButton = view.findViewById(R.id.integerTestButton)
        floatingPointTestButton = view.findViewById(R.id.floatingPointTestButton)
        integerScoreTextView = view.findViewById(R.id.integerScoreTextView)
        floatingPointScoreTextView = view.findViewById(R.id.floatingPointScoreTextView)
        rawTimeTextView = view.findViewById(R.id.rawTimeTextView)
        statusTextView = view.findViewById(R.id.statusTextView)
        cpuBenchmarkChart = view.findViewById(R.id.cpuBenchmarkChart)

        integerScoreTextView.text = getString(R.string.initial_text)
        floatingPointScoreTextView.text = getString(R.string.initial_text)
        rawTimeTextView.text = getString(R.string.initial_text)
        statusTextView.text = getString(R.string.status_ready)

        integerTestButton.setOnClickListener {
            runFullBenchmark(TestType.INTEGER)
        }

        floatingPointTestButton.setOnClickListener {
            runFullBenchmark(TestType.FLOATING_POINT)
        }
    }

    private fun setupChart(times: List<Double>) {
        val entries = ArrayList<BarEntry>()
        for ((index, time) in times.withIndex()) {
            entries.add(BarEntry(index.toFloat(), time.toFloat()))
        }

        val dataSet = BarDataSet(entries, "Run Times (ms)")
        dataSet.color = Color.rgb(63, 81, 181)
        dataSet.setDrawValues(false)

        val barData = BarData(dataSet)
        cpuBenchmarkChart.data = barData

        cpuBenchmarkChart.description.isEnabled = false
        cpuBenchmarkChart.legend.isEnabled = false
        cpuBenchmarkChart.axisRight.isEnabled = false
        cpuBenchmarkChart.axisLeft.setDrawGridLines(false)

        val xAxis = cpuBenchmarkChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)

        cpuBenchmarkChart.animateY(1000)
        cpuBenchmarkChart.invalidate()
    }

    private fun calculatePrimes(): Int {
        var primeCount = 0
        for (number in 2..integerWorkSize) {
            var isPrime = true
            val limit = floor(sqrt(number.toDouble())).toInt()
            for (factor in 2..limit) {
                if (number % factor == 0) {
                    isPrime = false
                    break
                }
            }
            if (isPrime) {
                primeCount++
            }
        }
        return primeCount
    }

    private fun calculateMatrixMultiplication(): Array<Array<Double>> {
        val matrixA = Array(matrixSize) { Array(matrixSize) { Random.nextDouble() } }
        val matrixB = Array(matrixSize) { Array(matrixSize) { Random.nextDouble() } }
        val resultMatrix = Array(matrixSize) { Array(size = matrixSize, init = { 0.0 }) }

        for (i in 0 until matrixSize) {
            for (j in 0 until matrixSize) {
                var sum = 0.0
                for (k in 0 until matrixSize) {
                    sum += matrixA[i][k] * matrixB[k][j]
                }
                resultMatrix[i][j] = sum
            }
        }
        return resultMatrix
    }

    private fun runFullBenchmark(testType: TestType) {
        integerTestButton.isEnabled = false
        floatingPointTestButton.isEnabled = false
        rawTimeTextView.text = getString(R.string.loading_text)

        CoroutineScope(Dispatchers.Default).launch {
            val warmUpRuns = 5
            for (i in 1..warmUpRuns) {
                withContext(Dispatchers.Main) {
                    statusTextView.text = getString(R.string.status_warmup, i, warmUpRuns)
                }
                if (testType == TestType.INTEGER) calculatePrimes() else calculateMatrixMultiplication()
            }

            val times = mutableListOf<Double>()
            for (i in 1..numRuns) {
                withContext(Dispatchers.Main) {
                    statusTextView.text = getString(R.string.status_measuring, i, numRuns)
                }
                val timeMs = measureTimeMillis {
                    if (testType == TestType.INTEGER) calculatePrimes() else calculateMatrixMultiplication()
                }
                times.add(timeMs.toDouble())
            }

            val medianTime = calculateMedian(times)
            val finalScore = calculateScore(medianTime, testType)

            withContext(Dispatchers.Main) {
                rawTimeTextView.text = getString(R.string.raw_time, medianTime)
                if (testType == TestType.INTEGER) {
                    integerScoreTextView.text = getString(R.string.score, finalScore.toInt())
                } else {
                    floatingPointScoreTextView.text = getString(R.string.score, finalScore.toInt())
                }

                statusTextView.text = getString(R.string.status_completed)
                setupChart(times)
                integerTestButton.isEnabled = true
                floatingPointTestButton.isEnabled = true
            }
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

    private fun calculateScore(userTime: Double, testType: TestType): Double {
        if (userTime == 0.0) return 0.0

        return if (testType == TestType.INTEGER) {
            (BASELINE_INTEGER_TIME_MS / userTime) * BASELINE_INTEGER_SCORE
        } else {
            (BASELINE_FLOATING_POINT_TIME_MS / userTime) * BASELINE_FLOATING_POINT_SCORE
        }
    }
}
