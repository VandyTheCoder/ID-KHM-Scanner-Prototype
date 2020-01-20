package com.nhean.bestframe.data

import com.google.gson.Gson
import org.opencv.core.Mat

data class ImageRaw(
    var image : Mat,
    var image_addr : Long
)

