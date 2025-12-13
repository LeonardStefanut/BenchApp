package com.example.myapplication

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.random.Random

object BenchmarkAlgorithms {

    // --- LOGICA PENTRU CPU ---

    // Calcul numere prime (pentru testul Integer)
    fun calculatePrimes(workSize: Int): Int {
        var primeCount = 0
        for (number in 2..workSize) {
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

    // Înmulțire matrici (pentru testul Float)
    fun calculateMatrixMultiplication(matrixSize: Int): Array<Array<Double>> {
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

    // --- LOGICA PENTRU RAM ---

    // Scriere și citire rapidă în memorie
    fun runRamPass(memorySizeBytes: Int): Long {
        val memoryBlock = ByteArray(memorySizeBytes)
        // Măsurăm timpul pentru scriere și citire
        return kotlin.system.measureTimeMillis {
            var ignoreSum: Byte = 0
            // Scriere
            for (j in 0 until memorySizeBytes) {
                memoryBlock[j] = 1
            }
            // Citire
            for (j in 0 until memorySizeBytes) {
                ignoreSum = memoryBlock[j]
            }
        }
    }

    // --- LOGICA PENTRU STORAGE ---

    // Scriere în fișier
    fun runStorageWrite(file: File, buffer: ByteArray, totalBytes: Long): Long {
        return kotlin.system.measureTimeMillis {
            FileOutputStream(file).use { fos ->
                var bytesWritten = 0L
                while (bytesWritten < totalBytes) {
                    fos.write(buffer)
                    bytesWritten += buffer.size
                }
            }
        }
    }

    // Citire din fișier
    fun runStorageRead(file: File, buffer: ByteArray): Long {
        return kotlin.system.measureTimeMillis {
            FileInputStream(file).use { fis ->
                while (fis.read(buffer) != -1) {
                    // Citim continuu
                }
            }
        }
    }
}