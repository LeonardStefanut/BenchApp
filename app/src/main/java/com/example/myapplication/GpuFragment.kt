package com.example.myapplication

import android.graphics.Color
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet

private const val BASELINE_GPU_FPS = 60.0
private const val BASELINE_GPU_SCORE = 1000.0
private const val NUM_CUBES = 500
private const val TEST_DURATION_SECONDS = 30

class GpuFragment : Fragment() {

    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var startGpuTestButton: Button
    private lateinit var gpuStatusTextView: TextView
    private lateinit var gpuScoreTextView: TextView
    private lateinit var gpuFpsTextView: TextView
    private lateinit var gpuBenchmarkChart: LineChart

    private lateinit var renderer: GpuBenchmarkRenderer

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_gpu, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        glSurfaceView = view.findViewById(R.id.gpuSurfaceView)
        startGpuTestButton = view.findViewById(R.id.startGpuTestButton)
        gpuStatusTextView = view.findViewById(R.id.gpuStatusTextView)
        gpuScoreTextView = view.findViewById(R.id.gpuScoreTextView)
        gpuFpsTextView = view.findViewById(R.id.gpuFpsTextView)
        gpuBenchmarkChart = view.findViewById(R.id.gpuBenchmarkChart)

        glSurfaceView.setEGLContextClientVersion(1)
        renderer = GpuBenchmarkRenderer(NUM_CUBES)
        glSurfaceView.setRenderer(renderer)
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        startGpuTestButton.setOnClickListener {
            runGpuBenchmark()
        }
    }

    private fun setupChart(fpsData: List<Float>) {
        val entries = fpsData.mapIndexed { index, fps -> Entry(index.toFloat(), fps) }
        val dataSet = LineDataSet(entries, "FPS Over Time").apply {
            color = Color.rgb(3, 169, 244)
            setDrawCircles(false)
            setDrawValues(false)
            lineWidth = 2f
            mode = LineDataSet.Mode.CUBIC_BEZIER
            fillColor = Color.rgb(3, 169, 244)
            setDrawFilled(true)
        }

        gpuBenchmarkChart.apply {
            data = LineData(dataSet)
            description.isEnabled = false
            legend.isEnabled = false
            axisRight.isEnabled = false
            axisLeft.axisMinimum = 0f
            xAxis.setDrawLabels(false)
            invalidate()
        }
    }

    private fun runGpuBenchmark() {
        startGpuTestButton.isEnabled = false
        gpuScoreTextView.text = getString(R.string.loading_text)
        gpuFpsTextView.text = "Avg FPS: --"
        gpuBenchmarkChart.clear()
        glSurfaceView.visibility = View.VISIBLE

        renderer.onProgressUpdate = { remainingSeconds ->
            activity?.runOnUiThread {
                gpuStatusTextView.text = "Test running... ($remainingSeconds seconds left)"
            }
        }

        renderer.onTestCompleteListener = { averageFps, allFps ->
            activity?.runOnUiThread {
                val score = calculateScore(averageFps.toDouble())
                gpuScoreTextView.text = getString(R.string.score, score.toInt())
                gpuFpsTextView.text = "Avg FPS: %.2f".format(averageFps)
                gpuStatusTextView.text = "Test Completed!"
                setupChart(allFps)
                startGpuTestButton.isEnabled = true
                glSurfaceView.visibility = View.GONE
            }
        }

        renderer.startTest(TEST_DURATION_SECONDS)
    }

    private fun calculateScore(fps: Double): Double {
        if (fps <= 0) return 0.0
        return (fps / BASELINE_GPU_FPS) * BASELINE_GPU_SCORE
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView.onResume()
    }

    override fun onPause() {
        super.onPause()
        glSurfaceView.onPause()
    }
}
