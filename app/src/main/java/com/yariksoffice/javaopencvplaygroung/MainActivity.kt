package com.yariksoffice.javaopencvplaygroung

import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.squareup.picasso.MemoryPolicy
import com.squareup.picasso.Picasso
import com.yariksoffice.javaopencvplaygroung.StitcherOutput.Failure
import com.yariksoffice.javaopencvplaygroung.StitcherOutput.Success
import com.yariksoffice.javaopencvplaygroung.databinding.ActivityMainBinding
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bytedeco.opencv.opencv_stitching.Stitcher

/**
 * Tela principal do exemplo de composição de imagens com OpenCV.
 *
 * Responsabilidades desta Activity:
 * - abrir o seletor de imagens;
 * - descobrir o modo escolhido pelo usuário;
 * - iniciar o processamento fora da thread principal;
 * - exibir o resultado ou o erro na interface.
 */
class MainActivity: AppCompatActivity() {

    private lateinit var imageStitcher: ImageStitcher
    private lateinit var processingDialog: ProgressDialog
    private var stitchJob: Job? = null

    /**
     * Escopo da Activity. O Job pai permite cancelar todo processamento quando
     * a Activity é destruída.
     */
    private val activityScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate
    )
    lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.button.setOnClickListener { chooseImages() }
        initializeStitcher()
    }

    /** Cria os objetos usados para iniciar e acompanhar o processamento. */
    private fun initializeStitcher() {
        imageStitcher = ImageStitcher(FileUtil(applicationContext))
        processingDialog = ProgressDialog(this).apply {
            setMessage(getString(R.string.processing_images))
            setCancelable(false)
        }
    }

    /** Abre o seletor do Android permitindo escolher uma ou mais imagens. */
    private fun chooseImages() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
            .setType(INTENT_IMAGE_TYPE)
            .putExtra(EXTRA_ALLOW_MULTIPLE, true)

        startActivityForResult(intent, CHOOSE_IMAGES)
    }

    /** Recebe as imagens escolhidas e inicia a composição. */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != CHOOSE_IMAGES || resultCode != RESULT_OK || data == null) {
            return
        }
        val imageUris = extractImageUris(data)
        if (imageUris.isNotEmpty()) {
            processImages(imageUris)
        }
    }

    /**
     * Extrai as URIs do resultado do seletor.
     *
     * O Android usa [Intent.clipData] quando há múltiplas imagens e
     * [Intent.data] quando há apenas uma.
     */
    private fun extractImageUris(data: Intent): List<Uri> {
        val clipData = data.clipData

        return if (clipData != null) {
            List(clipData.itemCount) { index ->
                clipData.getItemAt(index).uri
            }
        } else {
            data.data?.let(::listOf)
                .orEmpty()
        }
    }

    /**
     * Limpa o preview, identifica o modo escolhido e inicia uma nova coroutine.
     *
     * Se outro processamento estiver em andamento, ele é cancelado para que
     * apenas a seleção mais recente continue sendo processada.
     */
    private fun processImages(uris: List<Uri>) {
        binding.image.setImageDrawable(null)
        val stitchMode = selectedStitchMode()
        stitchJob?.cancel()
        processingDialog.show()

        stitchJob = activityScope.launch {
            runStitching(uris, stitchMode)
        }
    }

    /** Retorna o modo do OpenCV selecionado no RadioGroup da tela. */
    private fun selectedStitchMode(): Int {
        return if (binding.radioGroup.checkedRadioButtonId == R.id.radio_scan) {
            Stitcher.SCANS
        } else {
            Stitcher.PANORAMA
        }
    }

    /**
     * Executa o trabalho pesado em [Dispatchers.IO] e volta para a thread
     * principal automaticamente ao exibir o resultado.
     */
    private suspend fun runStitching(uris: List<Uri>, stitchMode: Int) {
        try {
            val output = withContext(Dispatchers.IO) {
                imageStitcher.stitchImages(StitcherInput(uris, stitchMode))
            }

            processResult(output)
        } catch (exception: CancellationException) {
            // Cancelamento é esperado quando uma nova seleção substitui a anterior.
            throw exception
        } catch (exception: Throwable) {
            processError(exception)
        } finally {
            // Uma coroutine cancelada não pode fechar o diálogo de uma nova execução.
            if (currentCoroutineContext().isActive) {
                processingDialog.dismiss()
            }
        }
    }

    /** Exibe o resultado gerado ou encaminha a falha para o tratamento de erro. */
    private fun processResult(output: StitcherOutput) {
        when (output) {
            is Success -> showImage(output.file)
            is Failure -> processError(output.exception)
        }
    }

    /** Registra o erro e informa o usuário através de uma mensagem temporária. */
    private fun processError(exception: Throwable) {
        Log.e(TAG, "Image stitching failed", exception)
        Toast.makeText(this, exception.message.orEmpty(), Toast.LENGTH_LONG)
            .show()
    }

    /** Carrega o arquivo final no ImageView, sem reutilizar uma imagem antiga. */
    private fun showImage(file: File) {
        Picasso.get()
            .load(file)
            .memoryPolicy(MemoryPolicy.NO_STORE, MemoryPolicy.NO_CACHE)
            .into(binding.image)
    }

    override fun onDestroy() {
        stitchJob?.cancel()
        activityScope.cancel()

        if (::processingDialog.isInitialized && processingDialog.isShowing) {
            processingDialog.dismiss()
        }

        super.onDestroy()
    }

    companion object {

        private const val TAG = "MainActivity"
        private const val EXTRA_ALLOW_MULTIPLE = "android.intent.extra.ALLOW_MULTIPLE"
        private const val INTENT_IMAGE_TYPE = "image/*"
        private const val CHOOSE_IMAGES = 777
    }
}
