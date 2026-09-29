package com.yariksoffice.javaopencvplaygroung

import android.content.Context
import android.net.Uri
import android.os.Environment.DIRECTORY_PICTURES
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Centraliza a criação, cópia e limpeza dos arquivos usados pelo OpenCV.
 *
 * O seletor de imagens do Android fornece [Uri]. O OpenCV, por outro lado,
 * precisa receber caminhos de arquivos. Por isso, as imagens selecionadas
 * são copiadas temporariamente para o armazenamento privado da aplicação.
 */
class FileUtil(private val context: Context) {

    /** Copia cada [Uri] para um arquivo temporário local. */
    @Throws(IOException::class)
    fun urisToFiles(uris: List<Uri>): List<File> {
        val temporaryDirectory = requireTemporaryDirectory()
        val files = ArrayList<File>(uris.size)

        for (uri in uris) {
            val file = createTempFile(temporaryDirectory)
            writeUriToFile(uri, file)
            files.add(file)
        }

        return files
    }

    /**
     * Cria o arquivo que receberá o panorama final.
     *
     * O resultado é salvo em `Pictures/Results`, separado dos arquivos de
     * trabalho para que a limpeza temporária não o remova.
     */
    @Throws(IOException::class)
    fun createResultFile(): File {
        val picturesDirectory = requirePicturesDirectory()
        return createTempFile(File(picturesDirectory, RESULT_DIRECTORY_NAME))
    }

    /** Remove os arquivos temporários gerados durante o processamento. */
    fun cleanUpWorkingDirectory() {
        requireTemporaryDirectory().deleteRecursively()
    }

    /** Cria um arquivo JPG temporário dentro de [directory]. */
    @Throws(IOException::class)
    private fun createTempFile(directory: File): File {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create directory: ${directory.absolutePath}")
        }

        val timestamp = SimpleDateFormat(
            DATE_FORMAT_TEMPLATE,
            Locale.getDefault()
        ).format(Date())

        return File.createTempFile(
            IMAGE_NAME_TEMPLATE.format(timestamp),
            JPG_EXTENSION,
            directory
        )
    }

    /** Copia o conteúdo de [source] para [destination]. */
    @Throws(IOException::class)
    private fun writeUriToFile(source: Uri, destination: File) {
        val inputStream = context.contentResolver.openInputStream(source)
            ?: throw IOException("Could not open image URI: $source")

        inputStream.use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        }
    }

    /** Retorna o diretório privado de imagens da aplicação. */
    private fun requirePicturesDirectory(): File {
        return context.getExternalFilesDir(DIRECTORY_PICTURES)
            ?: throw IllegalStateException("Pictures directory is unavailable")
    }

    /** Retorna a pasta usada exclusivamente durante um processamento. */
    private fun requireTemporaryDirectory(): File {
        return File(requirePicturesDirectory(), TEMPORARY_DIRECTORY_NAME)
    }

    companion object {
        private const val TEMPORARY_DIRECTORY_NAME = "Temporary"
        private const val RESULT_DIRECTORY_NAME = "Results"
        private const val DATE_FORMAT_TEMPLATE = "yyyyMMdd_HHmmss"
        private const val IMAGE_NAME_TEMPLATE = "IMG_%s_"
        private const val JPG_EXTENSION = ".jpg"
    }
}
