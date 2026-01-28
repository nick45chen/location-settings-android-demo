package com.example.myapplicationsetting

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.content.IntentFilter
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.example.myapplicationsetting.ui.theme.SettingTestAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SettingTestAppTheme {
                SettingsEntryScreen()
            }
        }
    }
}

private enum class SettingsDestination(
    val buttonText: String,
    val dialogTitle: String,
    val dialogMessage: String
) {
    AppLocationPermission(
        buttonText = "位置權限（App）",
        dialogTitle = "請到系統設定開啟位置權限",
        dialogMessage = "已無法再次請求位置權限，請至系統設定頁手動開啟。"
    ),
    LocationAccuracy(
        buttonText = "高精準度定位",
        dialogTitle = "請到系統設定調整高精準度定位",
        dialogMessage = "無法顯示系統對話框，請至系統設定頁調整高精準度定位。"
    )
}

private enum class PermissionState(val label: String) {
    Fine("已授予（精準）"),
    Coarse("已授予（概略）"),
    Denied("未授予")
}

private enum class LocationServiceState(val label: String) {
    Enabled("已開啟"),
    Disabled("已關閉"),
    Unknown("未知")
}

private enum class AccuracyState(val label: String) {
    Enabled("已開啟"),
    Disabled("已關閉"),
    Unknown("未知")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsEntryScreen() {
    val context = LocalContext.current
    val activity = context.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasRequestedPermission by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var pendingDestination by remember { mutableStateOf<SettingsDestination?>(null) }
    var openDestinationOnGrant by remember { mutableStateOf(true) }
    var permissionState by remember { mutableStateOf(resolvePermissionState(context)) }
    var locationServiceState by remember { mutableStateOf(resolveLocationServiceState(context)) }
    var accuracyState by remember { mutableStateOf(resolveAccuracyState(context)) }

    // Refresh all UI status indicators from current system/app state.
    fun refreshStatus() {
        permissionState = resolvePermissionState(context)
        locationServiceState = resolveLocationServiceState(context)
        accuracyState = resolveAccuracyState(context)
    }

    fun openDestination(destination: SettingsDestination) {
        when (destination) {
            SettingsDestination.AppLocationPermission -> openAppLocationPermissionSettings(context)
            SettingsDestination.LocationAccuracy -> openLocationAccuracySettings(context)
        }
        pendingDestination = null
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        refreshStatus()
        // Only auto-navigate when the caller requests it.
        if (granted && openDestinationOnGrant) {
            pendingDestination?.let { openDestination(it) }
        } else if (granted) {
            pendingDestination = null
        }
    }
    val locationSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) {
        refreshStatus()
    }

    fun handleButtonClick(
        destination: SettingsDestination,
        openOnGrant: Boolean = true,
        openIfAlreadyGranted: Boolean = true
    ) {
        pendingDestination = destination
        openDestinationOnGrant = openOnGrant
        val permissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (permissionGranted) {
            if (openIfAlreadyGranted) {
                openDestination(destination)
            }
            return
        }

        val shouldShowRationale = activity?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(
                it,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } ?: false

        if (!hasRequestedPermission || shouldShowRationale) {
            hasRequestedPermission = true
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            // "Don't ask again" case: show dialog then route to settings.
            showSettingsDialog = true
        }
    }

    fun requestEnableLocationService() {
        val currentState = resolveLocationServiceState(context)
        if (currentState == LocationServiceState.Enabled) {
            // Already enabled: go straight to system settings page.
            openLocationServiceSettings(context)
            return
        }
        requestSystemLocationEnable(
            activity = activity,
            context = context,
            launcher = locationSettingsLauncher,
            onFallback = { openLocationServiceSettings(context) },
            onUpdated = { refreshStatus() }
        )
    }

    fun requestLocationAccuracy() {
        pendingDestination = SettingsDestination.LocationAccuracy
        if (!hasAnyLocationPermission(context)) {
            // Without runtime permission, SettingsClient may no-op on Android 9.
            openDestination(SettingsDestination.LocationAccuracy)
            return
        }
        val currentAccuracy = resolveAccuracyState(context)
        if (currentAccuracy == AccuracyState.Enabled) {
            // Already enabled: go straight to system settings page.
            openDestination(SettingsDestination.LocationAccuracy)
            return
        }
        requestSystemLocationEnable(
            activity = activity,
            context = context,
            launcher = locationSettingsLauncher,
            onFallback = { showSettingsDialog = true },
            onUpdated = {
                refreshStatus()
                pendingDestination = null
            }
        )
    }

    DisposableEffect(lifecycleOwner, context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (context == null) return
                refreshStatus()
            }
        }
        val filter = IntentFilter().apply {
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            addAction(LocationManager.MODE_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Re-check status after returning from system settings.
                refreshStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            context.unregisterReceiver(receiver)
        }
    }

    if (showSettingsDialog) {
        SettingsDialog(
            destination = pendingDestination,
            onDismiss = {
                showSettingsDialog = false
                pendingDestination?.let { openDestination(it) }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "設定項目") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "請選擇要調整的設定",
                style = MaterialTheme.typography.titleMedium
            )
            StatusCard(
                title = "位置服務（系統）",
                value = locationServiceState.label,
                color = locationServiceState.toStatusColor()
            )
            StatusCard(
                title = "高精準度定位",
                value = accuracyState.label,
                color = accuracyState.toStatusColor()
            )
            StatusCard(
                title = "位置權限（App）",
                value = permissionState.label,
                color = permissionState.toStatusColor()
            )
            Text(
                text = "系統設定",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { requestEnableLocationService() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Text(text = "開啟位置服務（系統）")
            }
            Button(
                onClick = { requestLocationAccuracy() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Text(text = SettingsDestination.LocationAccuracy.buttonText)
            }
            Text(
                text = "應用程式設定",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = {
                    handleButtonClick(
                        destination = SettingsDestination.AppLocationPermission,
                        openOnGrant = false,
                        openIfAlreadyGranted = true
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = SettingsDestination.AppLocationPermission.buttonText)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsDialog(
    destination: SettingsDestination?,
    onDismiss: () -> Unit
) {
    val title = destination?.dialogTitle ?: "請到系統設定調整"
    val message = destination?.dialogMessage ?: "已無法再次請求權限，請至系統設定頁手動開啟。"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "前往設定")
            }
        }
    )
}

@Composable
private fun StatusCard(title: String, value: String, color: Color) {
    val cardColor = color.copy(alpha = 0.12f)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(color, CircleShape)
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    color = color
                )
            }
        }
    }
}

private fun openAppLocationPermissionSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun openLocationAccuracySettings(context: Context) {
    val candidates = listOf(
        Intent("com.google.android.gms.location.settings.GOOGLE_LOCATION_ACCURACY"),
        Intent("android.settings.LOCATION_SCANNING_SETTINGS"),
        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
    )
    val packageManager = context.packageManager
    val intent = candidates.firstOrNull { it.resolveActivity(packageManager) != null }
    if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

private fun resolvePermissionState(context: Context): PermissionState {
    val fineGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    if (fineGranted) {
        return PermissionState.Fine
    }
    val coarseGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    return if (coarseGranted) PermissionState.Coarse else PermissionState.Denied
}

private fun hasAnyLocationPermission(context: Context): Boolean {
    val fineGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val coarseGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    return fineGranted || coarseGranted
}

private fun resolveLocationServiceState(context: Context): LocationServiceState {
    val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return LocationServiceState.Unknown
    return try {
        if (LocationManagerCompat.isLocationEnabled(locationManager)) {
            LocationServiceState.Enabled
        } else {
            LocationServiceState.Disabled
        }
    } catch (_: SecurityException) {
        LocationServiceState.Unknown
    }
}

private fun requestSystemLocationEnable(
    activity: Activity?,
    context: Context,
    launcher: ManagedActivityResultLauncher<IntentSenderRequest, androidx.activity.result.ActivityResult>,
    onFallback: () -> Unit,
    onUpdated: () -> Unit
) {
    if (activity == null) {
        onFallback()
        return
    }
    val settingsClient = LocationServices.getSettingsClient(activity)
    val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        10_000L
    ).build()
    val settingsRequest = LocationSettingsRequest.Builder()
        .addLocationRequest(locationRequest)
        .setAlwaysShow(true)
        .build()
    val task = settingsClient.checkLocationSettings(settingsRequest)
    task.addOnSuccessListener {
        onUpdated()
    }
    task.addOnFailureListener { exception ->
        when (exception) {
            is ResolvableApiException -> {
                val intentSenderRequest = IntentSenderRequest.Builder(exception.resolution).build()
                launcher.launch(intentSenderRequest)
            }
            is ApiException -> {
                onFallback()
            }
            else -> {
                onFallback()
            }
        }
    }
}

private fun openLocationServiceSettings(context: Context) {
    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun resolveAccuracyState(context: Context): AccuracyState {
    val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return AccuracyState.Unknown
    return try {
        val locationEnabled = LocationManagerCompat.isLocationEnabled(locationManager)
        if (!locationEnabled) {
            AccuracyState.Disabled
        } else {
            val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            if (gpsEnabled && networkEnabled) AccuracyState.Enabled else AccuracyState.Disabled
        }
    } catch (_: SecurityException) {
        AccuracyState.Unknown
    }
}

private fun LocationServiceState.toStatusColor(): Color = when (this) {
    LocationServiceState.Enabled -> Color(0xFF2E7D32)
    LocationServiceState.Disabled -> Color(0xFFC62828)
    LocationServiceState.Unknown -> Color(0xFF6E6E6E)
}

private fun AccuracyState.toStatusColor(): Color = when (this) {
    AccuracyState.Enabled -> Color(0xFF2E7D32)
    AccuracyState.Disabled -> Color(0xFFC62828)
    AccuracyState.Unknown -> Color(0xFF6E6E6E)
}

private fun PermissionState.toStatusColor(): Color = when (this) {
    PermissionState.Fine -> Color(0xFF2E7D32)
    PermissionState.Coarse -> Color(0xFF6E6E6E)
    PermissionState.Denied -> Color(0xFFC62828)
}

private fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}

@Preview(showBackground = true)
@Composable
fun SettingsEntryScreenPreview() {
    SettingTestAppTheme {
        SettingsEntryScreen()
    }
}
