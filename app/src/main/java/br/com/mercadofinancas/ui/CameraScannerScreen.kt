package br.com.mercadofinancas.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import br.com.mercadofinancas.ocr.NotaExtraidaResult
import br.com.mercadofinancas.ocr.NotaFiscalParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

@Composable
fun CameraScannerScreen(
    centroCustoPadrao: String,
    onNotaExtraida: (NotaExtraidaResult) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPerm by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var statusMsg by remember { mutableStateOf("Aponte a câmera para o cupom fiscal ou escolha da galeria") }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasCameraPerm = it
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            statusMsg = "Lendo cupom fiscal via ML Kit..."
            val image = InputImage.fromFilePath(context, it)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val parsed = NotaFiscalParser.parseTextoOcr(visionText.text, centroCustoPadrao)
                    onNotaExtraida(parsed)
                }
                .addOnFailureListener { statusMsg = "Falha ao ler imagem: ${it.localizedMessage}" }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPerm) permLauncher.launch(Manifest.permission.CAMERA)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Scanner de Cupom Fiscal (OCR Nativo)", style = MaterialTheme.typography.titleMedium)
        Text(statusMsg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)

        if (hasCameraPerm) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                            val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
                            imageCapture = capture
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Autorizar Câmera")
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { galleryLauncher.launch("image/*") },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Galeria")
            }

            Button(
                onClick = {
                    val cap = imageCapture ?: return@Button
                    statusMsg = "Capturando e processando nota..."
                    val photoFile = File(context.cacheDir, "nota_${System.currentTimeMillis()}.jpg")
                    val outputOpts = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                    cap.takePicture(outputOpts, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            val image = InputImage.fromFilePath(context, Uri.fromFile(photoFile))
                            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                            recognizer.process(image)
                                .addOnSuccessListener { visionText ->
                                    val parsed = NotaFiscalParser.parseTextoOcr(visionText.text, centroCustoPadrao)
                                    onNotaExtraida(parsed)
                                }
                                .addOnFailureListener { statusMsg = "Erro OCR: ${it.localizedMessage}" }
                        }
                        override fun onError(exc: ImageCaptureException) {
                            statusMsg = "Erro na captura: ${exc.localizedMessage}"
                        }
                    })
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Fotografar Nota")
            }
        }
    }
}
