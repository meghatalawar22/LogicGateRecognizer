package com.example.courseproject

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.example.courseproject.databinding.ActivityMainBinding
import com.google.android.gms.tflite.java.TfLite
import org.tensorflow.lite.DataType
import org.tensorflow.lite.InterpreterApi
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.IOException
import java.nio.MappedByteBuffer

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var tflite: InterpreterApi? = null
    private val modelPath = "logic_gate_model.tflite"
    private val labels = listOf("AND", "NAND", "NOR", "NOT", "OR", "XNOR", "XOR")
    
    private val inputSize = 128

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) openCamera() else Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val imageBitmap = result.data?.extras?.get("data") as? Bitmap
            imageBitmap?.let {
                binding.imgInput.setImageBitmap(it)
                processImage(it)
            }
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, it)
            binding.imgInput.setImageBitmap(bitmap)
            processImage(bitmap)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize TFLite via Play Services
        TfLite.initialize(this).addOnSuccessListener {
            try {
                // IMPORTANT: When using Play Services, we must set the runtime to FROM_SYSTEM_ONLY
                val options = InterpreterApi.Options()
                    .setRuntime(InterpreterApi.Options.TfLiteRuntime.FROM_SYSTEM_ONLY)
                
                tflite = InterpreterApi.create(loadModelFile(), options)
                Toast.makeText(this, "AI Model Ready", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Error loading model: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "TFLite initialization failed", Toast.LENGTH_SHORT).show()
        }

        binding.btnCamera.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        binding.btnGallery.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        takePictureLauncher.launch(intent)
    }

    private fun loadModelFile(): MappedByteBuffer {
        return FileUtil.loadMappedFile(this, modelPath)
    }

    private fun processImage(bitmap: Bitmap) {
        val interpreter = tflite 
        if (interpreter == null) {
            Toast.makeText(this, "Please wait, AI is still loading...", Toast.LENGTH_SHORT).show()
            return
        }

        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(inputSize, inputSize, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0f, 255f)) // Adjust if your model needs different normalization
            .build()

        var tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)
        tensorImage = imageProcessor.process(tensorImage)

        val outputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, labels.size), DataType.FLOAT32)
        interpreter.run(tensorImage.buffer, outputBuffer.buffer.rewind())

        val result = outputBuffer.floatArray
        val maxIdx = result.indices.maxByOrNull { result[it] } ?: -1
        
        if (maxIdx != -1) {
            displayResult(labels[maxIdx])
        }
    }

    private fun displayResult(gateName: String) {
        binding.txtPrediction.text = "PREDICTION: $gateName"
        val basePath = "Output/$gateName"
        
        loadAssetImage("$basePath/Layout.jpeg", binding.imgLayout)
        loadAssetImage("$basePath/Schematic.jpeg", binding.imgSchematic)
        loadAssetImage("$basePath/Truthtable.png", binding.imgTruthTable)
        loadAssetImage("$basePath/Verilogcode.png", binding.imgVerilog)
    }

    private fun loadAssetImage(path: String, imageView: android.widget.ImageView) {
        try {
            val inputStream = assets.open(path)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            Glide.with(this).load(bitmap).into(imageView)
        } catch (e: IOException) {
            e.printStackTrace()
            imageView.setImageDrawable(null)
        }
    }

    override fun onDestroy() {
        tflite?.close()
        super.onDestroy()
    }
}
