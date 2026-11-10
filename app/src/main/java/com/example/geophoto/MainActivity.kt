/*
 * ფოტოს გადაღება + GPS კოორდინატების (განედი/გრძედი) ჩაწერა EXIF-ში
 *
 * ========== 1) app/build.gradle(.kts) - dependencies ==========
 * implementation("com.google.android.gms:play-services-location:21.3.0")
 * implementation("androidx.exifinterface:exifinterface:1.3.7")
 * implementation("androidx.activity:activity-compose:1.9.0")
 * (Compose-ის დანარჩენი სტანდარტული დამოკიდებულებები Android Studio-ს
 *  "Empty Activity" შაბლონში უკვე არის)
 *
 * ========== 2) AndroidManifest.xml ==========
 * <uses-permission android:name="android.permission.CAMERA" />
 * <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
 * <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
 *
 * <application ...>
 *     <provider
 *         android:name="androidx.core.content.FileProvider"
 *         android:authorities="${applicationId}.fileprovider"
 *         android:exported="false"
 *         android:grantUriPermissions="true">
 *         <meta-data
 *             android:name="android.support.FILE_PROVIDER_PATHS"
 *             android:resource="@xml/file_paths" />
 *     </provider>
 * </application>
 *
 * ========== 3) res/xml/file_paths.xml (შექმენი ახალი ფაილი) ==========
 * <?xml version="1.0" encoding="utf-8"?>
 * <paths>
 *     <files-path name="photos" path="photos/" />
 * </paths>
 *
 * ========== 4) ქვემოთ მოცემული კოდი - MainActivity.kt ==========
 * (შეცვალე package სახელი შენი პროექტის package-ით)
 */
package com.example.geophoto

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
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

@SuppressLint("MissingPermission")
@Composable
fun GeoPhotoScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var photoFile by remember { mutableStateOf<File?>(null) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var info by remember { mutableStateOf("ფოტო ჯერ არ არის გადაღებული") }

    // ფოტოს გადაღების შემდეგ: ვიღებთ ლოკაციას და ვწერთ EXIF-ში
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
                // კოორდინატების ჩაწერა თვით JPEG ფაილში
                val exif = ExifInterface(file.absolutePath)
                exif.setLatLong(loc.latitude, loc.longitude)
                if (loc.hasAltitude()) exif.setAltitude(loc.altitude)
                exif.saveAttributes()

                bitmap = BitmapFactory.decodeFile(file.absolutePath)
                info = "განედი (lat): ${loc.latitude}\nგრძედი (lng): ${loc.longitude}\n" +
                        "სიზუსტე: ${loc.accuracy} მ\nფაილი: ${file.name}"
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

    // ნებართვების მოთხოვნა
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
