package example.mobilegpsapp

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import okhttp3.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import androidx.compose.ui.viewinterop.AndroidView
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class MainActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 60000L)
        .setMinUpdateIntervalMillis(5000L)
        .build()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(applicationContext, getPreferences(MODE_PRIVATE))
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setContent {
            MaterialTheme {
                GpsMapScreen(fusedLocationClient, locationRequest)
            }
        }
    }
}

@Composable
fun GpsMapScreen(fusedLocationClient: FusedLocationProviderClient, locationRequest: LocationRequest) {
    val context = LocalContext.current
    var lastLocation by remember { mutableStateOf<Location?>(null) }

    Column(Modifier.fillMaxSize()) {
        AndroidView(factory = {
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
            }
        }, modifier = Modifier
            .fillMaxWidth()
            .weight(1f), update = { mapView ->
            lastLocation?.let { loc ->
                val point = GeoPoint(loc.latitude, loc.longitude)
                mapView.controller.setZoom(15.0)
                mapView.controller.setCenter(point)
                val marker = Marker(mapView).apply {
                    position = point
                    title = "Here"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                mapView.overlays.clear()
                mapView.overlays.add(marker)
                mapView.invalidate()
            }
        })

        Button(
            onClick = {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        (context as ComponentActivity),
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        1
                    )
                } else {
                    fusedLocationClient.requestLocationUpdates(
                        locationRequest,
                        object : LocationCallback() {
                            override fun onLocationResult(result: LocationResult) {
                                for (location in result.locations) {
                                    lastLocation = location
                                    sendToInflux(location)
                                }
                            }
                        },
                        Looper.getMainLooper()
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("Start Tracking")
        }
    }
}

fun sendToInflux(location: Location) {
    val influxUrl = "https://eu-central-1-1.aws.cloud2.influxdata.com/api/v2/write?org=SoloDev10xProgramer&bucket=MobileGPSapp&precision=s\n"
    val token = "Your api token"

    val body = "gps,device=android latitude=${location.latitude},longitude=${location.longitude}"

    val client = OkHttpClient()
    val request = Request.Builder()
        .url(influxUrl)
        .addHeader("Authorization", "Token $token")
        .post(body.toRequestBody("text/plain".toMediaType()))
        .build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            e.printStackTrace()
        }

        override fun onResponse(call: Call, response: Response) {
            response.use {
                println("Influx response: ${response.code} ${response.message}")
            }
        }
    })
}
