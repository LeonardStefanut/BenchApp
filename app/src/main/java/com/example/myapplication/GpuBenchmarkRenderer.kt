package com.example.myapplication

import android.opengl.GLSurfaceView
import android.opengl.GLU
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.Random
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GpuBenchmarkRenderer(private val numCubes: Int) : GLSurfaceView.Renderer {

    private val cubes = mutableListOf<Cube>()
    private val fpsHistory = mutableListOf<Float>()
    private var framesThisSecond = 0
    private var startTime = 0L
    private var isTesting = false
    private var testDurationMs: Long = 0
    private var lastUpdateTime = 0L

    var onTestCompleteListener: ((averageFps: Float, allFps: List<Float>) -> Unit)? = null
    var onProgressUpdate: ((remainingSeconds: Int) -> Unit)? = null
    var onFpsUpdate: ((fps: Float) -> Unit)? = null

    private class Cube {
        private val vertexBuffer: FloatBuffer
        private val colorBuffer: FloatBuffer
        private val indexBuffer: ByteBuffer

        var angleX = 0f
        var angleY = 0f
        var posX = 0f
        var posY = 0f
        var posZ = 0f
        private val rotSpeedX: Float = (Random().nextFloat() - 0.5f) * 4
        private val rotSpeedY: Float = (Random().nextFloat() - 0.5f) * 4

        init {
            val vertices = floatArrayOf(
                -1.0f, -1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, -1.0f, -1.0f, 1.0f, -1.0f,
                -1.0f, -1.0f, 1.0f, 1.0f, -1.0f, 1.0f, 1.0f, 1.0f, 1.0f, -1.0f, 1.0f, 1.0f
            )
            val colors = floatArrayOf(
                0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.5f, 0.0f, 1.0f, 1.0f, 0.5f, 0.0f, 1.0f,
                1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f, 1.0f, 1.0f
            )
            val indices = byteArrayOf(
                0, 4, 5, 0, 5, 1, 1, 5, 6, 1, 6, 2, 2, 6, 7, 2, 7, 3,
                3, 7, 4, 3, 4, 0, 4, 7, 6, 4, 6, 5, 0, 3, 2, 0, 2, 1
            )
            vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            vertexBuffer.put(vertices).position(0)
            colorBuffer = ByteBuffer.allocateDirect(colors.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            colorBuffer.put(colors).position(0)
            indexBuffer = ByteBuffer.allocateDirect(indices.size).order(ByteOrder.nativeOrder())
            indexBuffer.put(indices).position(0)
        }

        fun draw(gl: GL10) {
            gl.glEnableClientState(GL10.GL_VERTEX_ARRAY)
            gl.glEnableClientState(GL10.GL_COLOR_ARRAY)
            gl.glVertexPointer(3, GL10.GL_FLOAT, 0, vertexBuffer)
            gl.glColorPointer(4, GL10.GL_FLOAT, 0, colorBuffer)
            gl.glDrawElements(GL10.GL_TRIANGLES, 36, GL10.GL_UNSIGNED_BYTE, indexBuffer)
            gl.glDisableClientState(GL10.GL_VERTEX_ARRAY)
            gl.glDisableClientState(GL10.GL_COLOR_ARRAY)
        }

        fun update() {
            angleX += rotSpeedX
            angleY += rotSpeedY
        }
    }

    override fun onSurfaceCreated(gl: GL10, config: EGLConfig?) {
        gl.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)
        gl.glEnable(GL10.GL_DEPTH_TEST)
        cubes.clear()
        val random = Random()
        for (i in 0 until numCubes) {
            val cube = Cube()
            cube.posX = (random.nextFloat() - 0.5f) * 20
            cube.posY = (random.nextFloat() - 0.5f) * 20
            cube.posZ = -20 - random.nextFloat() * 20
            cubes.add(cube)
        }
    }

    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        gl.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / height
        gl.glMatrixMode(GL10.GL_PROJECTION)
        gl.glLoadIdentity()
        GLU.gluPerspective(gl, 45.0f, aspect, 0.1f, 100.0f)
        gl.glMatrixMode(GL10.GL_MODELVIEW)
        gl.glLoadIdentity()
    }

    override fun onDrawFrame(gl: GL10) {
        gl.glClear(GL10.GL_COLOR_BUFFER_BIT or GL10.GL_DEPTH_BUFFER_BIT)

        if (isTesting) {
            framesThisSecond++
            val currentTime = System.currentTimeMillis()

            if (currentTime > lastUpdateTime + 1000) {
                val elapsedSeconds = (currentTime - lastUpdateTime) / 1000.0f
                val fps = framesThisSecond / elapsedSeconds
                onFpsUpdate?.invoke(fps)
                fpsHistory.add(fps)

                framesThisSecond = 0
                lastUpdateTime = currentTime

                val remainingSeconds = ((startTime + testDurationMs) - currentTime) / 1000
                onProgressUpdate?.invoke(remainingSeconds.toInt())
            }

            if (currentTime > startTime + testDurationMs) {
                val averageFps = if (fpsHistory.isNotEmpty()) fpsHistory.average().toFloat() else 0f
                onTestCompleteListener?.invoke(averageFps, fpsHistory.toList())
                isTesting = false
            }
        }

        for (cube in cubes) {
            gl.glLoadIdentity()
            gl.glTranslatef(cube.posX, cube.posY, cube.posZ)
            gl.glRotatef(cube.angleX, 1f, 0f, 0f)
            gl.glRotatef(cube.angleY, 0f, 1f, 0f)
            cube.draw(gl)
            cube.update()
        }
    }

    fun startTest(durationSeconds: Int) {
        fpsHistory.clear()
        framesThisSecond = 0
        startTime = System.currentTimeMillis()
        lastUpdateTime = startTime
        testDurationMs = durationSeconds * 1000L
        isTesting = true
    }
}