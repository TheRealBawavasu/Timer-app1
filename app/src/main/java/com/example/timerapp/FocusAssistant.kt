package com.example.timerapp

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.Executors
import kotlin.math.abs

@OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun FocusAssistant(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    // ML Kit Clients
    val faceDetector = remember {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build()
        )
    }
    val poseDetector = remember {
        PoseDetection.getClient(
            PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .build()
        )
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor) { imageProxy ->
                            processImageProxy(imageProxy, faceDetector, poseDetector, context)
                        }
                    }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e("FocusAssistant", "Use case binding failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))
            
            previewView
        },
        modifier = modifier
    )

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            faceDetector.close()
            poseDetector.close()
        }
    }
}

@SuppressLint("UnsafeOptInUsageError")
@OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun processImageProxy(
    imageProxy: ImageProxy,
    faceDetector: com.google.mlkit.vision.face.FaceDetector,
    poseDetector: com.google.mlkit.vision.pose.PoseDetector,
    context: Context
) {
    val mediaImage = imageProxy.image ?: return
    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

    // Run detectors sequentially for simplicity in this version
    faceDetector.process(image)
        .addOnSuccessListener { faces ->
            if (faces.isEmpty()) {
                TimerState.attentivenessStatus = "No face detected"
                TimerState.isUserInattentive = true
            } else {
                val face = faces[0]
                val isLookingAway = abs(face.headEulerAngleY) > 25 || abs(face.headEulerAngleX) > 20
                val isEyesClosed = (face.leftEyeOpenProbability ?: 1.0f) < 0.4 || 
                                 (face.rightEyeOpenProbability ?: 1.0f) < 0.4
                
                TimerState.isUserInattentive = isLookingAway || isEyesClosed
                TimerState.attentivenessStatus = if (isLookingAway) "Looking away" 
                                               else if (isEyesClosed) "Drowsy?" 
                                               else "Attentive"
            }
            
            // Proceed to pose detection
            poseDetector.process(image)
                .addOnSuccessListener { pose ->
                    val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
                    val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
                    val leftEar = pose.getPoseLandmark(PoseLandmark.LEFT_EAR)
                    val rightEar = pose.getPoseLandmark(PoseLandmark.RIGHT_EAR)

                    if (leftShoulder != null && rightShoulder != null && leftEar != null && rightEar != null) {
                        // Simple posture logic: ear should be significantly above shoulder
                        // and horizontal distance between ear and shoulder should be small
                        val shoulderY = (leftShoulder.position.y + rightShoulder.position.y) / 2
                        val earY = (leftEar.position.y + rightEar.position.y) / 2
                        
                        val verticalDiff = shoulderY - earY
                        // The higher the verticalDiff, the better the posture (head is high)
                        // Sensitivity 0.5 (default) -> threshold around 150 pixels
                        val threshold = 100 + (TimerState.postureSensitivity * 100)
                        
                        TimerState.isPostureBad = verticalDiff < threshold
                    }
                    
                    checkAndTriggerNudge(context)
                }
                .addOnCompleteListener { imageProxy.close() }
        }
        .addOnFailureListener { imageProxy.close() }
}

private var lastNudgeTime = 0L
private fun checkAndTriggerNudge(context: Context) {
    if (!TimerState.isRunning || TimerState.isBreakMode) return
    
    val currentTime = System.currentTimeMillis()
    if (currentTime - lastNudgeTime < 10000) return // Max one nudge every 10 seconds

    if (TimerState.isUserInattentive || TimerState.isPostureBad) {
        lastNudgeTime = currentTime
        triggerNudge(context)
    }
}

private fun triggerNudge(context: Context) {
    // 1. Haptic feedback
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(20)
    }

    // 2. Audio feedback (subtle ding)
    try {
        val toneG = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 50)
        toneG.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
    } catch (e: Exception) {
        Log.e("FocusAssistant", "Tone failed", e)
    }

    // 3. Notification (for smartwatch sync)
    val message = if (TimerState.isPostureBad) "Straighten up!" else "Stay focused!"
    sendNotification(context, message)
}

@SuppressLint("MissingPermission")
private fun sendNotification(context: Context, message: String) {
    val builder = NotificationCompat.Builder(context, "lockout_channel")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("Focus Nudge")
        .setContentText(message)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setVibrate(longArrayOf(0, 100, 50, 100))

    with(NotificationManagerCompat.from(context)) {
        notify(1001, builder.build())
    }
}
