package com.example.imagelearning

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.example.imagelearning.graphics.GraphicOverlay
import com.example.imagelearning.processors.ObjectDetectorProcessor
import com.example.imagelearning.processors.VisionImageProcessor
import com.example.imagelearning.utils.PreferenceUtils
import com.example.imagelearning.utils.SelectModelSpinner
import com.example.imagelearning.viewmodels.CameraXViewModel
import com.google.mlkit.common.MlKitException
import com.google.mlkit.common.model.LocalModel

class MainActivity : AppCompatActivity(),
        ActivityCompat.OnRequestPermissionsResultCallback,
        AdapterView.OnItemSelectedListener,
        CompoundButton.OnCheckedChangeListener {

    private var previewView: PreviewView? = null
    private var graphicOverlay: GraphicOverlay? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var previewUseCase: Preview? = null
    private var analysisUseCase: ImageAnalysis? = null
    private var imageProcessor: VisionImageProcessor? = null
    private var needUpdateGraphicOverlayImageSourceInfo = false
    private var selectedModel = OBJECT_DETECTION_CUSTOM
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var cameraSelector: CameraSelector? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            selectedModel =
                    savedInstanceState.getString(
                            STATE_SELECTED_MODEL,
                            OBJECT_DETECTION_CUSTOM
                    )
            lensFacing =
                    savedInstanceState.getInt(
                            STATE_LENS_FACING,
                            CameraSelector.LENS_FACING_BACK
                    )
        }
        cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        setContentView(R.layout.activity_main)
        previewView = findViewById(R.id.preview_view)

        graphicOverlay = findViewById(R.id.graphic_overlay)

        val spinner = findViewById<Spinner>(R.id.mainactivity_mlmodel_spinner) as SelectModelSpinner
        val options: MutableList<String> = ArrayList()
        options.add(OBJECT_DETECTION_CUSTOM)

        // Creating adapter for spinner
        val dataAdapter = ArrayAdapter(this, R.layout.spinner_style, options)
        // Drop down layout style - list view with radio button
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        // attaching data adapter to spinner
        spinner.adapter = dataAdapter
        spinner.onItemSelectedListener = this
        spinner.icon = findViewById<ImageView>(R.id.imageview_mainactivity_spinnericon)

        val facingSwitch = findViewById<ToggleButton>(R.id.togglebutton_mainactivity_facingswitch)
        facingSwitch.setOnCheckedChangeListener(this)
        ViewModelProvider(
                this,
                ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )
                .get(CameraXViewModel::class.java)
                .processCameraProvider
                .observe(
                        this,
                        Observer { provider: ProcessCameraProvider? ->
                            cameraProvider = provider
                            if (allPermissionsGranted()) {
                                bindAllCameraUseCases()
                            }
                        }
                )

        if (!allPermissionsGranted()) {
            runtimePermissions
        }
    }

    override fun onSaveInstanceState(bundle: Bundle) {
        super.onSaveInstanceState(bundle)
        bundle.putString(STATE_SELECTED_MODEL, selectedModel)
        bundle.putInt(STATE_LENS_FACING, lensFacing)
    }

    @Synchronized
    override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
        // An item was selected. You can retrieve the selected item using
        // parent.getItemAtPosition(pos)
        selectedModel = parent?.getItemAtPosition(pos).toString()
        Log.d(TAG, "Selected model: $selectedModel")
        bindAnalysisUseCase()
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
        // Do nothing.
        Log.d(TAG, "No model selected")
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        Log.d(TAG, "Set facing")
        if (cameraProvider == null) {
            return
        }
        val newLensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }
        val newCameraSelector =
                CameraSelector.Builder().requireLensFacing(newLensFacing).build()
        try {
            if (cameraProvider!!.hasCamera(newCameraSelector)) {
                lensFacing = newLensFacing
                cameraSelector = newCameraSelector
                bindAllCameraUseCases()
                return
            }
        } catch (e: CameraInfoUnavailableException) {
            // Falls through
        }
        Toast.makeText(
                applicationContext, "This device does not have lens with facing: $newLensFacing",
                Toast.LENGTH_SHORT
        )
                .show()
    }

    public override fun onResume() {
        super.onResume()
        bindAllCameraUseCases()
    }

    override fun onPause() {
        super.onPause()

        imageProcessor?.run {
            this.stop()
        }
    }

    public override fun onDestroy() {
        super.onDestroy()
        imageProcessor?.run {
            this.stop()
        }
    }

    private fun bindAllCameraUseCases() {
        if (cameraProvider != null) {
            // As required by CameraX API, unbinds all use cases before trying to re-bind any of them.
            cameraProvider!!.unbindAll()
            bindPreviewUseCase()
            bindAnalysisUseCase()
        }
    }

    private fun bindPreviewUseCase() {
        if (!PreferenceUtils.isCameraLiveViewportEnabled(this)) {
            return
        }
        if (cameraProvider == null) {
            return
        }
        if (previewUseCase != null) {
            cameraProvider!!.unbind(previewUseCase)
        }

        previewUseCase = Preview.Builder().build()
        previewUseCase!!.setSurfaceProvider(previewView!!.surfaceProvider)
        cameraProvider!!.bindToLifecycle(/* lifecycleOwner= */this,
                cameraSelector!!,
                previewUseCase
        )
    }

    @SuppressLint("NewApi")
    private fun bindAnalysisUseCase() {
        if (cameraProvider == null) {
            return
        }
        if (analysisUseCase != null) {
            cameraProvider!!.unbind(analysisUseCase)
        }
        if (imageProcessor != null) {
            imageProcessor!!.stop()
        }
        imageProcessor = try {
            when (selectedModel) {
                OBJECT_DETECTION_CUSTOM -> {
                    Log.i(
                            TAG,
                            "Using Custom Object Detector (Bird) Processor"
                    )
                    val localModel = LocalModel.Builder()
                            .setAssetFilePath("custom_models/generic_object_detection.tflite") //TODO: put here the model!
                            .build()
                    val customObjectDetectorOptions =
                            PreferenceUtils.getCustomObjectDetectorOptionsForLivePreview(
                                    this,
                                    localModel
                            )
                    ObjectDetectorProcessor(
                            this, customObjectDetectorOptions
                    )
                }
                else -> return //TODO: what is going on here?
            }
        } catch (e: Exception) {
            Log.e(
                    TAG,
                    "Can not create image processor: $selectedModel",
                    e
            )
            Toast.makeText(
                    applicationContext,
                    "Can not create image processor: " + e.localizedMessage,
                    Toast.LENGTH_LONG
            )
                    .show()
            return
        }

        val builder = ImageAnalysis.Builder()
        val targetAnalysisSize = PreferenceUtils.getCameraXTargetAnalysisSize(this)
        if (targetAnalysisSize != null) {
            Log.e(TAG, "here")
            builder.setTargetResolution(targetAnalysisSize)
        }
        analysisUseCase = builder.build()

        needUpdateGraphicOverlayImageSourceInfo = true

        analysisUseCase?.setAnalyzer(
                // imageProcessor.processImageProxy will use another thread to run the detection underneath,
                // thus we can just run the analyzer itself on main thread.
                ContextCompat.getMainExecutor(this),
                ImageAnalysis.Analyzer { imageProxy: ImageProxy ->
                    if (needUpdateGraphicOverlayImageSourceInfo) {
                        val isImageFlipped =
                                lensFacing == CameraSelector.LENS_FACING_FRONT
                        val rotationDegrees =
                                imageProxy.imageInfo.rotationDegrees
                        if (rotationDegrees == 0 || rotationDegrees == 180) {
                            graphicOverlay!!.setImageSourceInfo(
                                    imageProxy.width, imageProxy.height, isImageFlipped
                            )
                        } else {
                            graphicOverlay!!.setImageSourceInfo(
                                    imageProxy.height, imageProxy.width, isImageFlipped
                            )
                        }
                        needUpdateGraphicOverlayImageSourceInfo = false
                    }
                    try {
                        imageProcessor!!.processImageProxy(imageProxy, graphicOverlay)
                    } catch (e: MlKitException) {
                        Log.e(
                                TAG,
                                "Failed to process image. Error: " + e.localizedMessage
                        )
                        Toast.makeText(
                                applicationContext,
                                e.localizedMessage,
                                Toast.LENGTH_SHORT
                        )
                                .show()
                    }
                }
        )
        cameraProvider!!.bindToLifecycle( /* lifecycleOwner= */this,
                cameraSelector!!,
                analysisUseCase
        )
    }

    private val requiredPermissions: Array<String?>
        get() = try {
            val info = this.packageManager
                    .getPackageInfo(this.packageName, PackageManager.GET_PERMISSIONS)
            val ps = info.requestedPermissions
            if (ps != null && ps.isNotEmpty()) {
                ps
            } else {
                arrayOfNulls(0)
            }
        } catch (e: Exception) {
            arrayOfNulls(0)
        }

    private fun allPermissionsGranted(): Boolean {
        for (permission in requiredPermissions) {
            if (!isPermissionGranted(this, permission)) {
                return false
            }
        }
        return true
    }

    private val runtimePermissions: Unit
        get() {
            val allNeededPermissions: MutableList<String?> = ArrayList()
            for (permission in requiredPermissions) {
                if (!isPermissionGranted(this, permission)) {
                    allNeededPermissions.add(permission)
                }
            }
            if (allNeededPermissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(
                        this,
                        allNeededPermissions.toTypedArray(),
                        PERMISSION_REQUESTS
                )
            }
        }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>,
            grantResults: IntArray
    ) {
        Log.i(TAG, "Permission granted!")
        if (allPermissionsGranted()) {
            bindAllCameraUseCases()
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    companion object {
        private const val TAG = "CameraXLivePreview"
        private const val PERMISSION_REQUESTS = 1
        private const val OBJECT_DETECTION_CUSTOM = "Custom Object Detection"
        private const val STATE_SELECTED_MODEL = "selected_model"
        private const val STATE_LENS_FACING = "lens_facing"

        private fun isPermissionGranted(
                context: Context,
                permission: String?
        ): Boolean {
            if (ContextCompat.checkSelfPermission(context, permission!!)
                    == PackageManager.PERMISSION_GRANTED
            ) {
                Log.i(TAG, "Permission granted: $permission")
                return true
            }
            Log.i(TAG, "Permission NOT granted: $permission")
            return false
        }
    }
}