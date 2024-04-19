package com.example.lango.record
import java.io.File

interface AudioRecorder {
    fun start(outputFile: File )
    fun stop()

}