package vpn.moonlight.ui.importsub

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * A camera preview that reports the first QR code it reads.
 *
 * [onCode] fires at most once — the caller navigates away on the first hit, and a
 * scanner that kept firing during that transition would submit the same
 * subscription several times.
 */
@Composable
fun QrScanner(onCode: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient() }
    val delivered = remember { java.util.concurrent.atomic.AtomicBoolean(false) }

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            scanner.close()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            val previewView = PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            val providerFuture = ProcessCameraProvider.getInstance(viewContext)
            providerFuture.addListener(
                {
                    val provider = runCatching { providerFuture.get() }.getOrNull()
                        ?: return@addListener

                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { useCase ->
                            useCase.setAnalyzer(
                                executor,
                                barcodeAnalyzer(scanner) { value ->
                                    if (delivered.compareAndSet(false, true)) onCode(value)
                                },
                            )
                        }

                    runCatching {
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                        )
                    }.onFailure { Log.w("QrScanner", "could not bind camera", it) }
                },
                ContextCompat.getMainExecutor(viewContext),
            )

            previewView
        },
    )
}

/**
 * The frame analyser.
 *
 * Written as an explicit object with the opt-in on `analyze` itself: a lambda is
 * compiled to its own anonymous class, so an `@OptIn` on the enclosing function
 * does not cover the `ImageProxy.image` read inside it.
 */
private fun barcodeAnalyzer(
    scanner: BarcodeScanner,
    onResult: (String) -> Unit,
): ImageAnalysis.Analyzer = object : ImageAnalysis.Analyzer {

    // androidx.annotation.OptIn, not kotlin.OptIn: the UnsafeOptInUsageError
    // check is a Java lint rule and only recognises the androidx annotation.
    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(proxy: ImageProxy) {
        val image = proxy.image
        if (image == null) {
            proxy.close()
            return
        }
        val input = InputImage.fromMediaImage(image, proxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                barcodes.firstOrNull { it.valueType == Barcode.TYPE_URL || it.rawValue != null }
                    ?.rawValue
                    ?.let(onResult)
            }
            // Always close, success or failure: a leaked ImageProxy stalls the
            // analyser after a few frames.
            .addOnCompleteListener { proxy.close() }
    }
}
