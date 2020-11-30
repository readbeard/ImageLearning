package com.example.imagelearning.utils

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import com.example.imagelearning.MainActivity
import com.example.imagelearning.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions


class TranslationBottomSheetDialog constructor(
    private val activity: MainActivity,
    private val detectedLabel: String
) : BottomSheetDialog(activity) {

    private var modelDownloaded: Boolean = false
    private lateinit var langTranslator: Translator
    private lateinit var translatorOptions: TranslatorOptions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view: View = View.inflate(activity, R.layout.bottomsheetdialog, null)
        this.setContentView(view)
        findViewById<TextView>(R.id.textview_bottomsheet_detectedword)?.text = detectedLabel
        this.setOnDismissListener { activity.imageProcessor?.run { activity.bindAllCameraUseCases() } }
    }

    override fun show() {
        super.show()
        initializeTranslator()
        if (!modelDownloaded) {
            downloadModel()
        } else {
            translateLabel(detectedLabel)
        }
    }

    private fun initializeTranslator() {
        translatorOptions = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.ITALIAN) .build()

        langTranslator = Translation.getClient(translatorOptions)
    }

    private fun downloadModel() {
        val conditions = DownloadConditions.Builder()
            .requireWifi()
            .build()
        //TODO: show spinner
        langTranslator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                modelDownloaded = true
                translateLabel(detectedLabel)
            }
            .addOnFailureListener { exception ->
                Log.e("TranslationBottomSheetDialog", "Failed to download model: $exception" )
            }
    }

    private fun translateLabel(detectedLabel: String) {
        langTranslator.translate(detectedLabel)
                .addOnSuccessListener { translatedText ->
                    activity.imageProcessor?.run { this.stop() }
                    findViewById<TextView>(R.id.textview_bottomsheet_translatedword)?.text = translatedText
                }
                .addOnFailureListener { exception ->
                    Log.e("TranslationBottomSheetDialog", "Failed to translate: $exception" )
                }
    }


}