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

class GlobalFragment : Fragment() {

    private lateinit var btnRunAll: Button
    private lateinit var tvLog: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var gpuSurface: GLSurfaceView
    private lateinit var renderer: GpuBenchmarkRenderer

    // Scoruri temporare
    private var scoreCpu = 0.0
    private var scoreRam = 0.0
    private var scoreStorage = 0.0
    private var scoreGpu = 0.0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_global, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnRunAll = view.findViewById(R.id.btnRunAll)
        tvLog = view.findViewById(R.id.tvLog)
        progressBar = view.findViewById(R.id.globalProgressBar)
        gpuSurface = view.findViewById(R.id.globalGpuSurface)

        // Configurare GPU
        gpuSurface.setEGLContextClientVersion(1)
        renderer = GpuBenchmarkRenderer(500) // 500 cuburi
        gpuSurface.setRenderer(renderer)
        gpuSurface.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        gpuSurface.onPause() // Pauză la start ca să nu consume resurse

        btnRunAll.setOnClickListener {
            runAllTests()
        }
    }

    private fun runAllTests() {
        btnRunAll.isEnabled = false
        tvLog.text = "Starting Full Benchmark...\n"
        progressBar.progress = 0

        viewLifecycleOwner.lifecycleScope.launch {
            // 1. CPU TEST
            log(">>> Running CPU Test...")
            scoreCpu = runCpuTest()
            progressBar.progress = 25
            log("CPU Done. Score: ${scoreCpu.toInt()}")

            // 2. RAM TEST
            log("\n>>> Running RAM Test...")
            scoreRam = runRamTest()
            progressBar.progress = 50
            log("RAM Done. Score: ${scoreRam.toInt()}")

            // 3. STORAGE TEST
            log("\n>>> Running Storage Test...")
            scoreStorage = runStorageTest()
            progressBar.progress = 75
            log("Storage Done. Score: ${scoreStorage.toInt()}")

            // 4. GPU TEST
            log("\n>>> Running GPU Test (Look at the view above)...")
            gpuSurface.onResume() // Pornim randarea
            scoreGpu = runGpuTest()
            gpuSurface.onPause() // Oprim randarea
            progressBar.progress = 100
            log("GPU Done. Score: ${scoreGpu.toInt()}")

            // FINAL
            showFinalResults()
            btnRunAll.isEnabled = true
        }
    }

    // --- Funcții ajutătoare (Suspend) ---

    private suspend fun runCpuTest(): Double = withContext(Dispatchers.Default) {
        // Integer
        val timeInt = kotlin.system.measureTimeMillis {
            BenchmarkAlgorithms.calculatePrimes(1000000)
        }
        // Float
        val timeFloat = kotlin.system.measureTimeMillis {
            BenchmarkAlgorithms.calculateMatrixMultiplication(150)
        }

        // Calcul scor simplificat
        val scoreI = (2000.0 / timeInt) * 1000.0
        val scoreF = (1500.0 / timeFloat) * 1000.0
        return@withContext (scoreI + scoreF) / 2
    }

    private suspend fun runRamTest(): Double = withContext(Dispatchers.Default) {
        val size = 32 * 1024 * 1024 // 32MB
        val time = BenchmarkAlgorithms.runRamPass(size)
        // Calcul Bandwidth
        val gb = (size * 2.0) / (1024.0 * 1024.0 * 1024.0)
        val sec = time / 1000.0
        val bandwidth = gb / sec
        return@withContext (bandwidth / 1.87) * 1000.0
    }

    private suspend fun runStorageTest(): Double = withContext(Dispatchers.IO) {
        val file = File(requireContext().filesDir, "temp_bench.bin")
        val sizeMB = 50
        val bytes = sizeMB * 1024L * 1024L
        val buffer = ByteArray(1024 * 1024) // 1MB buffer

        val tWrite = BenchmarkAlgorithms.runStorageWrite(file, buffer, bytes)
        val tRead = BenchmarkAlgorithms.runStorageRead(file, buffer)

        if (file.exists()) file.delete()

        val speedWrite = (sizeMB.toDouble()) / (tWrite / 1000.0)
        val speedRead = (sizeMB.toDouble()) / (tRead / 1000.0)

        val sW = (speedWrite / 150.0) * 1000.0
        val sR = (speedRead / 500.0) * 1000.0
        return@withContext (sW + sR) / 2
    }

    // GPU e special, trebuie să așteptăm callback-ul
    private suspend fun runGpuTest(): Double = suspendCoroutine { continuation ->
        // Setăm callback-ul pe renderer
        renderer.onTestCompleteListener = { avgFps, _ ->
            val score = (avgFps / 60.0) * 1000.0
            continuation.resume(score)
        }
        // Pornim testul pentru 5 secunde
        renderer.startTest(5)
    }

    private fun log(msg: String) {
        tvLog.append("$msg\n")
        // Scroll automat jos
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