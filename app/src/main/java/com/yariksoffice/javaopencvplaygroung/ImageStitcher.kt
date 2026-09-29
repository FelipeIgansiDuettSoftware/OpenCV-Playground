package com.yariksoffice.javaopencvplaygroung

import android.net.Uri

import org.bytedeco.opencv.opencv_stitching.Stitcher

import java.io.File

import org.bytedeco.opencv.global.opencv_imgcodecs.imread
import org.bytedeco.opencv.global.opencv_imgcodecs.imwrite
import org.bytedeco.opencv.opencv_stitching.Stitcher.ERR_CAMERA_PARAMS_ADJUST_FAIL
import org.bytedeco.opencv.opencv_stitching.Stitcher.ERR_HOMOGRAPHY_EST_FAIL
import org.bytedeco.opencv.opencv_stitching.Stitcher.ERR_NEED_MORE_IMGS
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.MatVector
import java.lang.Exception

class StitcherInput(val uris: List<Uri>, val stitchMode: Int)

sealed class StitcherOutput {
    class Success(val file: File) : StitcherOutput()
    class Failure(val e: Exception) : StitcherOutput()
}

class ImageStitcher(private val fileUtil: FileUtil) {

    fun stitchImages(input: StitcherInput): StitcherOutput {
        val files = fileUtil.urisToFiles(input.uris)
        val vector = filesToMatVector(files)
        return stitch(vector, input.stitchMode)
    }

    private fun stitch(vector: MatVector, stitchMode: Int): StitcherOutput {
        val result = Mat()
        val stitcher = Stitcher.create(stitchMode)
        val status = stitcher.stitch(vector, result)

        fileUtil.cleanUpWorkingDirectory()
        return when (status) {
            Stitcher.OK -> {
                val resultFile = fileUtil.createResultFile()
                imwrite(resultFile.absolutePath, result)
                StitcherOutput.Success(resultFile)
            }
            else        -> {
                val statusDescription = getStatusDescription(status)
                val e = RuntimeException("Can't stitch images: $statusDescription")
                StitcherOutput.Failure(e)
            }
        }
    }

    private fun getStatusDescription(status: Int): String {
        return when (status) {
            ERR_NEED_MORE_IMGS -> "ERR_NEED_MORE_IMGS"
            ERR_HOMOGRAPHY_EST_FAIL -> "ERR_HOMOGRAPHY_EST_FAIL"
            ERR_CAMERA_PARAMS_ADJUST_FAIL -> "ERR_CAMERA_PARAMS_ADJUST_FAIL"
            else -> "UNKNOWN"
        }
    }

    private fun filesToMatVector(files: List<File>): MatVector {
        val images = MatVector(files.size.toLong())
        for (i in files.indices) {
            images.put(i.toLong(), imread(files[i].absolutePath))
        }
        return images
    }
}
