package com.example.lango.playback

import java.io.File

interface AudioPlayer {
    fun playFile(file: File)
    fun stop()
}