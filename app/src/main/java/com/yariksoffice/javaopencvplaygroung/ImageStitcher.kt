package com.yariksoffice.javaopencvplaygroung

import android.net.Uri
import java.io.File
import java.lang.Exception
import org.bytedeco.opencv.global.opencv_imgcodecs.imread
import org.bytedeco.opencv.global.opencv_imgcodecs.imwrite
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.MatVector
import org.bytedeco.opencv.opencv_stitching.Stitcher
import org.bytedeco.opencv.opencv_stitching.Stitcher.ERR_CAMERA_PARAMS_ADJUST_FAIL
import org.bytedeco.opencv.opencv_stitching.Stitcher.ERR_HOMOGRAPHY_EST_FAIL
import org.bytedeco.opencv.opencv_stitching.Stitcher.ERR_NEED_MORE_IMGS

/** Dados necessários para executar uma composição de imagens. */
data class StitcherInput(
    val uris: List<Uri>,
    val stitchMode: Int
)

/** Resultado da composição: arquivo gerado ou falha conhecida do Stitcher. */
sealed class StitcherOutput {
    data class Success(val file: File) : StitcherOutput()
    data class Failure(val exception: Exception) : StitcherOutput()
}

/**
 * Coordena a preparação das imagens e a execução do Stitcher do OpenCV.
 *
 * Esta classe não decide como a tela deve reagir ao resultado. Ela apenas
 * transforma as entradas em um [StitcherOutput]. A Activity decide como
 * exibir esse resultado.
 */
class ImageStitcher(private val fileUtil: FileUtil) {

    /**
     * Executa uma composição de imagens.
     *
     * O método é síncrono de propósito: quem chama decide em qual dispatcher
     * o processamento deve ocorrer. A [MainActivity] o executa em
     * [kotlinx.coroutines.Dispatchers.IO].
     */
    fun stitchImages(input: StitcherInput): StitcherOutput {
        val imageFiles = fileUtil.urisToFiles(input.uris)
        val images = filesToMatVector(imageFiles)
        return stitch(images, input.stitchMode)
    }

    /** Executa o Stitcher e grava o resultado quando a composição é concluída. */
    private fun stitch(images: MatVector, stitchMode: Int): StitcherOutput {
        val result = Mat()
        val stitcher = Stitcher.create(stitchMode)
        val status = stitcher.stitch(images, result)

        fileUtil.cleanUpWorkingDirectory()

        return if (status == Stitcher.OK) {
            saveResult(result)
        } else {
            createFailure(status)
        }
    }

    /** Salva a imagem panorâmica e retorna o arquivo criado. */
    private fun saveResult(result: Mat): StitcherOutput.Success {
        val resultFile = fileUtil.createResultFile()
        imwrite(resultFile.absolutePath, result)
        return StitcherOutput.Success(resultFile)
    }

    /** Converte o código de status do OpenCV em um erro compreensível. */
    private fun createFailure(status: Int): StitcherOutput.Failure {
        val description = getStatusDescription(status)
        return StitcherOutput.Failure(
            RuntimeException("Can't stitch images: $description")
        )
    }

    private fun getStatusDescription(status: Int): String {
        return when (status) {
            ERR_NEED_MORE_IMGS -> "ERR_NEED_MORE_IMGS"
            ERR_HOMOGRAPHY_EST_FAIL -> "ERR_HOMOGRAPHY_EST_FAIL"
            ERR_CAMERA_PARAMS_ADJUST_FAIL -> "ERR_CAMERA_PARAMS_ADJUST_FAIL"
            else -> "UNKNOWN"
        }
    }

    /** Lê os arquivos no formato de coleção esperado pelo OpenCV. */
    private fun filesToMatVector(files: List<File>): MatVector {
        val images = MatVector(files.size.toLong())

        files.forEachIndexed { index, file ->
            images.put(index.toLong(), imread(file.absolutePath))
        }

        return images
    }
}
