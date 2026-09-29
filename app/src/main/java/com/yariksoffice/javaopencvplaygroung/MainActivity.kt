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
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var imageStitcher: ImageStitcher
    private lateinit var processingDialog: ProgressDialog
    private var stitchJob: Job? = null

    private val activityScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate
    )

    lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.button.setOnClickListener { chooseImages() }

        setUpStitcher()
    }

    private fun setUpStitcher() {
        imageStitcher = ImageStitcher(FileUtil(applicationContext))
        processingDialog = ProgressDialog(this).apply {
            setMessage(getString(R.string.processing_images))
            setCancelable(false)
        }
    }

    private fun chooseImages() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
                .setType(INTENT_IMAGE_TYPE)
                .putExtra(EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(intent, CHOOSE_IMAGES)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CHOOSE_IMAGES && resultCode == RESULT_OK && data != null) {
            val clipData = data.clipData
            val images = if (clipData != null) {
                List(clipData.itemCount) { clipData.getItemAt(it).uri }
            } else {
                listOf(data.data!!)
            }
            processImages(images)
        }
    }

    private fun processImages(uris: List<Uri>) {
        binding.image.setImageDrawable(null) // reset preview
        val radioGroup = binding.radioGroup
        val isScansChecked = radioGroup.checkedRadioButtonId == R.id.radio_scan
        val stitchMode = if (isScansChecked) Stitcher.SCANS
            else Stitcher.PANORAMA

        stitchJob?.cancel()
        processingDialog.show()

        stitchJob = activityScope.launch {
            try {
                val output = withContext(Dispatchers.IO) {
                    imageStitcher.stitchImages(StitcherInput(uris, stitchMode))
                }
                processResult(output)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                processError(e)
            } finally {
                if (currentCoroutineContext().isActive) {
                    processingDialog.dismiss()
                }
            }
        }
    }

    private fun processError(e: Throwable) {
        Log.e(TAG, "", e)
        Toast.makeText(this, e.message + "", Toast.LENGTH_LONG).show()
    }

    private fun processResult(output: StitcherOutput) {
        when (output) {
            is Success -> showImage(output.file)
            is Failure -> processError(output.e)
        }
    }

    private fun showImage(file: File) {
        Picasso.get().load(file)
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
        private const val TAG = "TAG"
        private const val EXTRA_ALLOW_MULTIPLE = "android.intent.extra.ALLOW_MULTIPLE"
        private const val INTENT_IMAGE_TYPE = "image/*"
        private const val CHOOSE_IMAGES = 777
    }
}
