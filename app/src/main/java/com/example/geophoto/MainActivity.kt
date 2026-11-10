package com.example.geophoto

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { GeoPhotoScreen() } }
    }
}

/** ფოტოს კოპირება გალერეაში (Pictures/GeoPhoto). EXIF კოორდინატები ინახება. */
fun saveToGallery(context: Context, file: File): Boolean {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= 29) {
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/GeoPhoto"
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: return false
    val out = resolver.openOutputStream(uri) ?: return false
    out.use { o -> file.inputStream().use { it.copyTo(o) } }
    if (Build.VERSION.SDK_INT >= 29) {
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }
    return true
}

@SuppressLint("MissingPermission")
@Composable
fun GeoPhotoScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var photoFile by remember { mutableStateOf<File?>(null) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var info by remember { mutableStateOf("ფოტო ჯერ არ არის გადაღებული") }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val file = photoFile
        if (!success || file == null) {
            info = "გადაღება გაუქმდა"
            return@rememberLauncherForActivityResult
        }
        info = "ვიღებ კოორდინატებს..."
        fusedClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            CancellationTokenSource().token
        ).addOnSuccessListener { loc ->
            if (loc != null) {
                val exif = ExifInterface(file.absolutePath)
                exif.setLatLong(loc.latitude, loc.longitude)
                if (loc.hasAltitude()) exif.setAltitude(loc.altitude)
                exif.saveAttributes()

                val saved = saveToGallery(context, file)
                bitmap = BitmapFactory.decodeFile(file.absolutePath)
                info = "განედი (lat): ${loc.latitude}\nგრძედი (lng): ${loc.longitude}\n" +
                        "სიზუსტე: ${loc.accuracy} მ\n" +
                        if (saved) "✅ შენახულია გალერეაში: Pictures/GeoPhoto"
                        else "⚠️ გალერეაში შენახვა ვერ მოხერხდა"
            } else {
                info = "ლოკაცია ვერ მოიძებნა (ჩართე GPS)"
            }
        }.addOnFailureListener { info = "შეცდომა: ${it.message}" }
    }

    fun launchCamera() {
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        val file = File(dir, "IMG_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        photoFile = file
        photoUri = uri
        takePicture.launch(uri)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.CAMERA] == true &&
            result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        ) launchCamera() else info = "ნებართვები საჭიროა კამერისა და ლოკაციისთვის"
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(onClick = {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)
            )
        }) { Text("📷 გადაიღე ფოტო") }

        Text(info)

        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "გადაღებული ფოტო",
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
    }
}
