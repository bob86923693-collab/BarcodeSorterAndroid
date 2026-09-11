package com.example.barcodesorter.scanner

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import kotlin.math.max

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun BarcodeCamera(
    enabled: Boolean,
    onCode: (String) -> Unit,
    scanWidthFraction: Float = 0.16f,
    scanHeightFraction: Float = 0.16f
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val onCodeState by rememberUpdatedState(onCode)
    val enabledState by rememberUpdatedState(enabled)
    val scanWidthState by rememberUpdatedState(scanWidthFraction)
    val scanHeightState by rememberUpdatedState(scanHeightFraction)

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds(),
        factory = { ctx ->
            val container = FrameLayout(ctx).apply {
                clipChildren = true
                clipToPadding = true
            }

            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            container.addView(previewView)

            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val options = BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(
                        Barcode.FORMAT_CODE_128,
                        Barcode.FORMAT_CODE_39,
                        Barcode.FORMAT_CODE_93,
                        Barcode.FORMAT_CODABAR,
                        Barcode.FORMAT_ITF,
                        Barcode.FORMAT_EAN_8,
                        Barcode.FORMAT_EAN_13,
                        Barcode.FORMAT_UPC_A,
                        Barcode.FORMAT_UPC_E
                    )
                    .build()

                val scanner = BarcodeScanning.getClient(options)
                var busy = false

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(executor) { proxy ->
                    if (!enabledState || busy) {
                        proxy.close()
                        return@setAnalyzer
                    }
                    val image = proxy.image
                    if (image == null) {
                        proxy.close()
                        return@setAnalyzer
                    }

                    busy = true
                    val rotation = proxy.imageInfo.rotationDegrees
                    val input = InputImage.fromMediaImage(image, rotation)

                    scanner.process(input)
                        .addOnSuccessListener { barcodes ->
                            val viewWidth = previewView.width.toFloat()
                            val viewHeight = previewView.height.toFloat()
                            if (viewWidth <= 0f || viewHeight <= 0f) return@addOnSuccessListener

                            val sourceWidth: Float
                            val sourceHeight: Float
                            if (rotation == 90 || rotation == 270) {
                                sourceWidth = image.height.toFloat()
                                sourceHeight = image.width.toFloat()
                            } else {
                                sourceWidth = image.width.toFloat()
                                sourceHeight = image.height.toFloat()
                            }

                            val scale = max(viewWidth / sourceWidth, viewHeight / sourceHeight)
                            val displayedWidth = sourceWidth * scale
                            val displayedHeight = sourceHeight * scale
                            val offsetX = (viewWidth - displayedWidth) / 2f
                            val offsetY = (viewHeight - displayedHeight) / 2f

                            // Detection is centered on the visible crosshair.
                            val targetWidth = viewWidth * scanWidthState
                            val targetHeight = viewHeight * scanHeightState
                            val targetLeft = (viewWidth - targetWidth) / 2f
                            val targetRight = targetLeft + targetWidth
                            val targetTop = (viewHeight - targetHeight) / 2f
                            val targetBottom = targetTop + targetHeight
                            val targetCenterX = viewWidth / 2f
                            val targetCenterY = viewHeight / 2f

                            val best = barcodes
                                .mapNotNull { barcode ->
                                    val raw = barcode.rawValue ?: return@mapNotNull null
                                    val box = barcode.boundingBox ?: return@mapNotNull null

                                    val xView = box.exactCenterX() * scale + offsetX
                                    val yView = box.exactCenterY() * scale + offsetY

                                    if (xView < targetLeft || xView > targetRight ||
                                        yView < targetTop || yView > targetBottom
                                    ) {
                                        return@mapNotNull null
                                    }

                                    val dx = xView - targetCenterX
                                    val dy = yView - targetCenterY
                                    Triple(raw, dx * dx + dy * dy, barcode)
                                }
                                .minByOrNull { it.second }

                            best?.first?.let(onCodeState)
                        }
                        .addOnCompleteListener {
                            busy = false
                            proxy.close()
                        }
                }

                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                } catch (_: Exception) {
                }
            }, ContextCompat.getMainExecutor(ctx))

            container
        }
    )
}
