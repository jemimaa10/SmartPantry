package com.example.mts.ui.scan

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mts.R
import com.example.mts.data.PantryBulk
import com.example.mts.data.PantryInput
import com.example.mts.data.ScannedIngredient
import com.example.mts.ui.common.BackBar
import com.example.mts.ui.common.BaseVm
import com.example.mts.ui.common.CollectMessages
import com.example.mts.ui.common.LoadingState
import com.example.mts.ui.common.MatchPill
import com.example.mts.ui.common.SoftCard
import com.example.mts.ui.theme.Leaf
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

private const val MAX_IMAGE_EDGE = 1600
private const val JPEG_QUALITY = 85

/** Downscales and rotates upright so uploads stay small and within the server limit. */
private fun prepareImage(context: Context, uri: Uri): ByteArray {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_EDGE) sample *= 2
    val bitmap = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: error("Unreadable image")

    val orientation = resolver.openInputStream(uri)?.use {
        ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } ?: ExifInterface.ORIENTATION_NORMAL
    val degrees = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }

    val scale = minOf(1f, MAX_IMAGE_EDGE.toFloat() / max(bitmap.width, bitmap.height))
    val matrix = Matrix().apply {
        postScale(scale, scale)
        postRotate(degrees)
    }
    val upright = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    return ByteArrayOutputStream().use {
        upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)
        it.toByteArray()
    }
}

data class ReviewItem(val name: String, val confidence: Double?, val quantity: Double?, val unit: String?)

private fun ScannedIngredient.toReview() = ReviewItem(name_id ?: name, confidence, quantity, unit)

sealed interface ScanStage {
    data object Capture : ScanStage
    data object Analyzing : ScanStage
    data class Review(val notice: String?) : ScanStage
    data object Saving : ScanStage
    data object Saved : ScanStage
}

class ScanVm(app: Application) : BaseVm(app) {
    val stage = MutableStateFlow<ScanStage>(ScanStage.Capture)
    val items = MutableStateFlow<List<ReviewItem>>(emptyList())

    fun analyze(uri: Uri, unreadable: String) = act {
        stage.value = ScanStage.Analyzing
        try {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { prepareImage(getApplication(), uri) }.getOrNull()
            }
            if (bytes == null) {
                messages.emit(unreadable)
                stage.value = ScanStage.Capture
                return@act
            }
            val part = MultipartBody.Part.createFormData(
                "image",
                "scan.jpg",
                bytes.toRequestBody("image/jpeg".toMediaType()),
            )
            val result = api.scan(part)
            items.value = result.ingredients.map { it.toReview() }
            stage.value = ScanStage.Review(result.message)
        } catch (e: Throwable) {
            // Recognition failed, so the review step doubles as manual entry.
            stage.value = ScanStage.Review(null)
            throw e
        }
    }

    fun manualEntry() {
        stage.value = ScanStage.Review(null)
    }

    fun rename(index: Int, name: String) {
        items.value = items.value.toMutableList().also { it[index] = it[index].copy(name = name) }
    }

    fun remove(index: Int) {
        items.value = items.value.toMutableList().also { it.removeAt(index) }
    }

    fun add(name: String) {
        items.value = items.value + ReviewItem(name.trim(), null, null, null)
    }

    fun save() = act {
        val names = items.value.filter { it.name.isNotBlank() }
        stage.value = ScanStage.Saving
        try {
            api.addPantryItems(PantryBulk(names.map { PantryInput(name = it.name.trim(), quantity = it.quantity) }))
            stage.value = ScanStage.Saved
        } catch (e: Throwable) {
            stage.value = ScanStage.Review(null)
            throw e
        }
    }

    fun restart() {
        items.value = emptyList()
        stage.value = ScanStage.Capture
    }
}

@Composable
fun ScanScreen(onBack: () -> Unit, onDone: () -> Unit, onFindRecipes: (List<String>) -> Unit, vm: ScanVm = viewModel()) {
    CollectMessages(vm)
    val stage by vm.stage.collectAsState()
    val items by vm.items.collectAsState()
    val unreadable = stringResource(R.string.scan_unreadable)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BackBar(stringResource(R.string.scan_ingredients), onBack) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
            when (val current = stage) {
                ScanStage.Capture -> CaptureStep(
                    onImage = { vm.analyze(it, unreadable) },
                    onManual = vm::manualEntry,
                )
                ScanStage.Analyzing -> LoadingState(label = stringResource(R.string.scan_analyzing))
                ScanStage.Saving -> LoadingState(label = stringResource(R.string.saving))
                is ScanStage.Review -> ReviewStep(current.notice, items, vm)
                ScanStage.Saved -> SavedStep(
                    onFindRecipes = { onFindRecipes(items.map { it.name }) },
                    onScanAgain = vm::restart,
                    onDone = onDone,
                )
            }
        }
    }
}

@Composable
private fun CaptureStep(onImage: (Uri) -> Unit, onManual: () -> Unit) {
    val context = LocalContext.current
    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val requestCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onImage(uri)
    }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }

    LaunchedEffect(Unit) { if (!hasCamera) requestCamera.launch(Manifest.permission.CAMERA) }

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(MaterialTheme.shapes.large)
                .background(Color(0xFF1C1B18)),
            contentAlignment = Alignment.Center,
        ) {
            if (hasCamera) {
                CameraPreview(capture)
            } else {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.camera_permission),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { requestCamera.launch(Manifest.permission.CAMERA) }) {
                        Text(stringResource(R.string.allow_camera))
                    }
                }
            }
        }
        Text(
            stringResource(R.string.scan_tip),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            IconButton(onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Icon(Icons.Filled.PhotoLibrary, stringResource(R.string.from_gallery), tint = Leaf)
            }
            Box(
                Modifier
                    .size(72.dp)
                    .border(4.dp, Leaf, CircleShape)
                    .padding(7.dp)
                    .background(if (hasCamera) Leaf else MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable(enabled = hasCamera) { takePhoto(context, capture, onImage) },
            )
            IconButton(onClick = onManual) {
                Icon(Icons.Filled.Edit, stringResource(R.string.manual), tint = Leaf)
            }
        }
    }
}

private fun takePhoto(context: Context, capture: ImageCapture, onImage: (Uri) -> Unit) {
    val file = File.createTempFile("scan", ".jpg", context.cacheDir)
    val options = ImageCapture.OutputFileOptions.Builder(file).build()
    capture.takePicture(
        options,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) = onImage(Uri.fromFile(file))
            override fun onError(exception: ImageCaptureException) {
                file.delete()
            }
        },
    )
}

@Composable
private fun CameraPreview(capture: ImageCapture) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }

    DisposableEffect(owner) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                provider.unbindAll()
                runCatching {
                    provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
        onDispose { runCatching { future.get().unbindAll() } }
    }
    AndroidView({ previewView }, Modifier.fillMaxSize())
}

@Composable
private fun ReviewStep(notice: String?, items: List<ReviewItem>, vm: ScanVm) {
    var newName by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(stringResource(R.string.review_title), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    notice ?: stringResource(R.string.review_body),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            itemsIndexed(items) { index, item ->
                SoftCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            item.name,
                            { vm.rename(index, it) },
                            Modifier.weight(1f),
                            singleLine = true,
                        )
                        Spacer(Modifier.width(8.dp))
                        item.confidence?.let { MatchPill((it * 100).roundToInt()) }
                        IconButton(onClick = { vm.remove(index) }) {
                            Icon(Icons.Filled.Close, stringResource(R.string.remove))
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.add_missing_ingredient)) },
                    trailingIcon = {
                        IconButton(
                            enabled = newName.isNotBlank(),
                            onClick = {
                                vm.add(newName)
                                newName = ""
                            },
                        ) { Icon(Icons.Filled.Add, stringResource(R.string.add)) }
                    },
                )
            }
        }
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(vm::restart, Modifier.weight(1f).height(52.dp)) { Text(stringResource(R.string.scan_again)) }
            Button(
                vm::save,
                Modifier.weight(1f).height(52.dp),
                enabled = items.any { it.name.isNotBlank() },
            ) { Text(stringResource(R.string.save_to_pantry)) }
        }
    }
}

@Composable
private fun SavedStep(onFindRecipes: () -> Unit, onScanAgain: () -> Unit, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.saved_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.saved_body),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onFindRecipes, Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(R.string.find_recipes)) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onScanAgain, Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(R.string.scan_again)) }
        TextButton(onDone) { Text(stringResource(R.string.done)) }
    }
}
