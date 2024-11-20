package com.example.scansense10

import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.scansense10.databinding.ActivityMainBinding
import android.Manifest
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import kotlin.math.pow
import kotlin.math.sqrt
import org.tensorflow.lite.task.core.BaseOptions
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var textToSpeech: TextToSpeech
    private val cameraPermissionCode = 1001
    private var previousDetections = mutableListOf<DetectionResult>()

    private var lastDetectionTime = 0L
    private val detectionInterval = 125

    companion object {
        const val TAG = "MainActivity"
        const val SMOOTHING_FACTOR = 0.8f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)



        textToSpeech = TextToSpeech(this, this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                cameraPermissionCode
            )
        }

        binding.captureButton.setOnClickListener {
            captureAndDescribe()
            describeDetectedObjects()


        }

    }


    private fun captureAndDescribe() {

        if (previousDetections.isNotEmpty()) {

            val description = previousDetections.joinToString(separator = ", ") { detection ->
                "${detection.label} com confiança de ${(detection.confidence * 100).toInt()}%"
            }


            textToSpeech.speak(description, TextToSpeech.QUEUE_FLUSH, null, null)
        } else {

            textToSpeech.speak("Nenhum objeto detectado.", TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == cameraPermissionCode) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissão da câmera necessária.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {

            textToSpeech.language = Locale("pt", "BR")
        } else {
            Toast.makeText(this, "Falha ao inicializar TTS", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = androidx.camera.core.Preview.Builder().build().also {
                it.surfaceProvider = binding.previewView.surfaceProvider
            }
            val overlayView = binding.overlayView
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(
                        ContextCompat.getMainExecutor(this),
                        ObjectDetectionAnalyzer(this)
                    )
                }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalyzer
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    fun runObjectDetection(bitmap: Bitmap) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastDetectionTime < detectionInterval) {
            return
        }
        lastDetectionTime = currentTime

        val image = TensorImage.fromBitmap(bitmap)

        val baseOptions = BaseOptions.builder()
            .setNumThreads(2)
            .build()

        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(baseOptions)
            .setMaxResults(5)
            .setScoreThreshold(0.3f)
            .build()

        val detector = ObjectDetector.createFromFileAndOptions(
            this,
            "model.tflite",
            options
        )

        val results = detector.detect(image)
        debugPrint(results)

        val scaleX = binding.previewView.width.toFloat() / bitmap.width
        val scaleY = binding.previewView.height.toFloat() / bitmap.height

        val detectionResults = results.map { detection ->
            val boundingBox = detection.boundingBox
            val scaledRect = RectF(
                boundingBox.left * scaleX,
                boundingBox.top * scaleY,
                boundingBox.right * scaleX,
                boundingBox.bottom * scaleY
            )

            DetectionResult(
                label = detection.categories.firstOrNull()?.label ?: "Unknown",
                confidence = detection.categories.firstOrNull()?.score ?: 0f,
                rect = scaledRect
            )
        }

        val filteredResults = detectionResults.map { newResult ->
            val similarDetection = previousDetections.find { isSimilar(it, newResult) }
            if (similarDetection != null) {
                smoothTransition(similarDetection, newResult)
                similarDetection
            } else {
                newResult
            }
        }

        previousDetections.clear()
        previousDetections.addAll(filteredResults)

        binding.overlayView.setResults(filteredResults)
        binding.overlayView.invalidate()
    }

    private fun isSimilar(oldResult: DetectionResult, newResult: DetectionResult): Boolean {
        val threshold = 50
        val distance = calculateDistance(oldResult.rect, newResult.rect)
        return oldResult.label == newResult.label && distance < threshold
    }

    private fun calculateDistance(rect1: RectF, rect2: RectF): Float {
        val centerX1 = (rect1.left + rect1.right) / 2
        val centerY1 = (rect1.top + rect1.bottom) / 2
        val centerX2 = (rect2.left + rect2.right) / 2
        val centerY2 = (rect2.top + rect2.bottom) / 2
        return sqrt((centerX1 - centerX2).pow(2) + (centerY1 - centerY2).pow(2))
    }

    private fun smoothTransition(oldResult: DetectionResult, newResult: DetectionResult) {
        oldResult.rect.left += (newResult.rect.left - oldResult.rect.left) * SMOOTHING_FACTOR
        oldResult.rect.top += (newResult.rect.top - oldResult.rect.top) * SMOOTHING_FACTOR
        oldResult.rect.right += (newResult.rect.right - oldResult.rect.right) * SMOOTHING_FACTOR
        oldResult.rect.bottom += (newResult.rect.bottom - oldResult.rect.bottom) * SMOOTHING_FACTOR
    }

    private fun describeDetectedObjects() {
        val description = StringBuilder("Objetos detectados: ")

        if (previousDetections.isEmpty()) {
            description.append("Nenhum objeto detectado.")
        } else {
            previousDetections.forEach { detection ->
                val label = detection.label
                val confidence = (detection.confidence * 100).toInt()
                description.append("$label com $confidence por cento de confiança. ")
            }
        }

        textToSpeech.speak(description.toString(), TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun debugPrint(results: List<Detection>) {
        for ((i, obj) in results.withIndex()) {
            val box = obj.boundingBox
            Log.d(TAG, "Detected object: $i ")
            Log.d(TAG, "  boundingBox: (${box.left}, ${box.top}) - (${box.right},${box.bottom})")
            for ((j, category) in obj.categories.withIndex()) {
                Log.d(TAG, "    Label $j: ${category.label}")
                val confidence: Int = category.score.times(100).toInt()
                Log.d(TAG, "    Confidence: ${confidence}%")
            }
        }
    }

    override fun onDestroy() {
        textToSpeech.stop()
        textToSpeech.shutdown()
        super.onDestroy()
    }
}
