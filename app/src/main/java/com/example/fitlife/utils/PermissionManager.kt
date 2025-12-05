package com.example.fitlife.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Utility class for handling runtime permissions in the app.
 * Follows Material Design guidelines for permission requests.
 */
object PermissionManager {

    // Permission groups
    val LOCATION_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    val SMS_PERMISSION = arrayOf(
        Manifest.permission.SEND_SMS
    )

    val CONTACTS_PERMISSION = arrayOf(
        Manifest.permission.READ_CONTACTS
    )

    val CAMERA_PERMISSION = arrayOf(
        Manifest.permission.CAMERA
    )

    val STORAGE_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    val NOTIFICATION_PERMISSION = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        emptyArray()
    }

    /**
     * Check if all permissions in the array are granted
     */
    fun hasPermissions(context: Context, permissions: Array<String>): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Check if any permission in the array needs rationale
     */
    fun shouldShowRationale(activity: Activity, permissions: Array<String>): Boolean {
        return permissions.any {
            ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
        }
    }

    /**
     * Show a rationale dialog before requesting permission
     */
    fun showRationaleDialog(
        context: Context,
        title: String,
        message: String,
        onPositiveClick: () -> Unit,
        onNegativeClick: () -> Unit = {}
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Grant") { _, _ -> onPositiveClick() }
            .setNegativeButton("Cancel") { _, _ -> onNegativeClick() }
            .setCancelable(false)
            .show()
    }

    /**
     * Show a dialog directing user to app settings when permission is permanently denied
     */
    fun showSettingsDialog(
        context: Context,
        title: String,
        message: String
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Settings") { _, _ ->
                openAppSettings(context)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Open app settings screen
     */
    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }

    /**
     * Request permissions using the provided launcher
     */
    fun requestPermissions(
        launcher: ActivityResultLauncher<Array<String>>,
        permissions: Array<String>
    ) {
        launcher.launch(permissions)
    }

    /**
     * Get permission rationale text based on permission type
     */
    fun getRationaleTitle(permissionType: PermissionType): String {
        return when (permissionType) {
            PermissionType.LOCATION -> "Location Permission Required"
            PermissionType.SMS -> "SMS Permission Required"
            PermissionType.CONTACTS -> "Contacts Permission Required"
            PermissionType.CAMERA -> "Camera Permission Required"
            PermissionType.STORAGE -> "Storage Permission Required"
            PermissionType.NOTIFICATION -> "Notification Permission Required"
        }
    }

    fun getRationaleMessage(permissionType: PermissionType): String {
        return when (permissionType) {
            PermissionType.LOCATION -> "FitLife needs location access to show nearby gyms and save your workout locations on the map."
            PermissionType.SMS -> "FitLife needs SMS permission to send your equipment checklist to friends."
            PermissionType.CONTACTS -> "FitLife needs contacts access to select recipients for equipment delegation."
            PermissionType.CAMERA -> "FitLife needs camera access to update your profile picture."
            PermissionType.STORAGE -> "FitLife needs storage access to save and load images."
            PermissionType.NOTIFICATION -> "FitLife needs notification permission to remind you about your workouts."
        }
    }

    fun getSettingsMessage(permissionType: PermissionType): String {
        return when (permissionType) {
            PermissionType.LOCATION -> "Location permission was denied. Please enable it in Settings to use map features."
            PermissionType.SMS -> "SMS permission was denied. Please enable it in Settings to send equipment lists."
            PermissionType.CONTACTS -> "Contacts permission was denied. Please enable it in Settings to select contacts."
            PermissionType.CAMERA -> "Camera permission was denied. Please enable it in Settings to take profile photos."
            PermissionType.STORAGE -> "Storage permission was denied. Please enable it in Settings to save images."
            PermissionType.NOTIFICATION -> "Notification permission was denied. Please enable it in Settings to receive workout reminders."
        }
    }

    enum class PermissionType {
        LOCATION,
        SMS,
        CONTACTS,
        CAMERA,
        STORAGE,
        NOTIFICATION
    }
}
