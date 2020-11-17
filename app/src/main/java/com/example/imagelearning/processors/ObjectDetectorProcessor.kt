package com.example.imagelearning.processors

import android.content.Context
import android.util.Log
import com.example.imagelearning.utils.FrameMetadata
import com.example.imagelearning.graphics.GraphicOverlay
import com.example.imagelearning.graphics.ObjectGraphic
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.ObjectDetectorOptionsBase
import java.io.IOException
import java.nio.ByteBuffer

/** A processor to run object detector.  */
class ObjectDetectorProcessor(context: Context, options: ObjectDetectorOptionsBase) :
        VisionProcessorBase<List<DetectedObject>>(context) {

    private val detector: ObjectDetector = ObjectDetection.getClient(options)
    private val imageLabeler: ImageLabeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)


    override fun stop() {
        super.stop()
        try {
            detector.close()
        } catch (e: IOException) {
            Log.e(
                    TAG,
                    "Exception thrown while trying to close object detector!",
                    e
            )
        }
    }

    override fun detectInImage(image: InputImage): Task<List<DetectedObject>> {
        imageLabeler.process(image)
        return detector.process(image)
    }

    override fun onSuccess(results: List<DetectedObject>, graphicOverlay: GraphicOverlay) {
        for (result in results) {
            graphicOverlay.add(ObjectGraphic(overlay = graphicOverlay, result))
        }
    }

    override fun onFailure(e: Exception) {
        Log.e(TAG, "Object detection failed!", e)
    }

    override fun processByteBuffer(
            data: ByteBuffer?,
            frameMetadata: FrameMetadata?,
            graphicOverlay: GraphicOverlay?
    ) {
        TODO("Not yet implemented")
    }

    companion object {
        private const val TAG = "ObjectDetectorProcessor"
    }
}