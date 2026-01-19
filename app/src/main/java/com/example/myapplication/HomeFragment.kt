package com.example.myapplication

import android.app.AlertDialog
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class HomeFragment : Fragment() {

    private lateinit var btnRunAll: Button
    private lateinit var tvLog: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var gpuSurface: GLSurfaceView
    private lateinit var renderer: GpuBenchmarkRenderer

    private var scoreCpu = 0.0
    private var scoreRam = 0.0
    private var scoreStorage = 0.0
    private var scoreGpu = 0.0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnRunAll = view.findViewById(R.id.btn_run_global_benchmark)
        tvLog = view.findViewById(R.id.tvLog)
        progressBar = view.findViewById(R.id.globalProgressBar)
        gpuSurface = view.findViewById(R.id.globalGpuSurface)

        gpuSurface.setEGLContextClientVersion(1)
        renderer = GpuBenchmarkRenderer(500)
        gpuSurface.setRenderer(renderer)
        gpuSurface.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        gpuSurface.onPause()

        btnRunAll.setOnClickListener {
            runAllTests()
        }
    }

    private fun runAllTests() {
        btnRunAll.isEnabled = false
        tvLog.text = "Starting Full Benchmark...\n"
        progressBar.progress = 0

        viewLifecycleOwner.lifecycleScope.launch {
            log(">>> Running CPU Test...")
            scoreCpu = runCpuTest()
            progressBar.progress = 25
            log("CPU Done. Score: ${scoreCpu.toInt()}")

            log("\n>>> Running RAM Test...")
            scoreRam = runRamTest()
            progressBar.progress = 50
            log("RAM Done. Score: ${scoreRam.toInt()}")

            log("\n>>> Running Storage Test...")
            scoreStorage = runStorageTest()
            progressBar.progress = 75
            log("Storage Done. Score: ${scoreStorage.toInt()}")

            log("\n>>> Running GPU Test (Look at the view above)...")
            gpuSurface.onResume()
            scoreGpu = runGpuTest()
            gpuSurface.onPause()
            progressBar.progress = 100
            log("GPU Done. Score: ${scoreGpu.toInt()}")

            showFinalResults()
            btnRunAll.isEnabled = true
        }
    }


    private suspend fun runCpuTest(): Double = withContext(Dispatchers.Default) {
        val timeInt = kotlin.system.measureTimeMillis {
            BenchmarkAlgorithms.calculatePrimes(1000000)
        }
        val timeFloat = kotlin.system.measureTimeMillis {
            BenchmarkAlgorithms.calculateMatrixMultiplication(150)
        }

        val scoreI = (2000.0 / timeInt) * 1000.0
        val scoreF = (1500.0 / timeFloat) * 1000.0
        return@withContext (scoreI + scoreF) / 2
    }

    private suspend fun runRamTest(): Double = withContext(Dispatchers.Default) {
        val size = 32 * 1024 * 1024
        val time = BenchmarkAlgorithms.runRamPass(size)
        val gb = (size * 2.0) / (1024.0 * 1024.0 * 1024.0)
        val sec = time / 1000.0
        val bandwidth = gb / sec
        return@withContext (bandwidth / 1.87) * 1000.0
    }

    private suspend fun runStorageTest(): Double = withContext(Dispatchers.IO) {
        val file = File(requireContext().filesDir, "temp_bench.bin")
        val sizeMB = 50
        val bytes = sizeMB * 1024L * 1024L
        val buffer = ByteArray(1024 * 1024)

        val tWrite = BenchmarkAlgorithms.runStorageWrite(file, buffer, bytes)
        val tRead = BenchmarkAlgorithms.runStorageRead(file, buffer)

        if (file.exists()) file.delete()

        val speedWrite = (sizeMB.toDouble()) / (tWrite / 1000.0)
        val speedRead = (sizeMB.toDouble()) / (tRead / 1000.0)

        val sW = (speedWrite / 150.0) * 1000.0
        val sR = (speedRead / 500.0) * 1000.0
        return@withContext (sW + sR) / 2
    }

    private suspend fun runGpuTest(): Double = suspendCoroutine { continuation ->
        renderer.onTestCompleteListener = { avgFps, _ ->
            val score = (avgFps / 60.0) * 1000.0
            continuation.resume(score)
        }
        renderer.startTest(5)
    }

    private fun log(msg: String) {
        tvLog.append("$msg\n")
        tvLog.post {
            val scrollAmount = tvLog.layout.getLineTop(tvLog.lineCount) - tvLog.height
            if (scrollAmount > 0)
                tvLog.scrollTo(0, scrollAmount)
            else
                tvLog.scrollTo(0, 0)
        }
    }

    private fun showFinalResults() {
        val total = (scoreCpu + scoreRam + scoreStorage + scoreGpu).toInt()
        val msg = """
            GLOBAL SCORE: $total
            
            Detailed Breakdown:
            ----------------
            CPU: ${scoreCpu.toInt()}
            GPU: ${scoreGpu.toInt()}
            RAM: ${scoreRam.toInt()}
            Storage: ${scoreStorage.toInt()}
        """.trimIndent()

        AlertDialog.Builder(requireContext())
            .setTitle("Benchmark Completed! 🏆")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
    }
}