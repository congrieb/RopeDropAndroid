package com.example.ropedrop

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ropedrop.ui.theme.RopeDropTheme
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askNotificationPermission()
        setContent {
            RopeDropTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GreetingPreview()

                }
            }
        }
    }

    fun getSubStatuses() {

    }

    // Declare the launcher at the top of your Activity/Fragment:
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted: Boolean ->
        if (isGranted) {
            // FCM SDK (and your app) can post notifications.
        } else {
            // TODO: Inform user that that your app will not show notifications.
        }
    }

    private fun askNotificationPermission() {
        // This is only necessary for API level >= 33 (TIRAMISU)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Composable
fun GateList(messages: List<String>, statusMap: SnapshotStateMap<String, String>) {
    val isSubbed: MutableState<Boolean> = remember {
        mutableStateOf(false);
    }

    val subMap: SnapshotStateMap<String, Boolean> = remember {
        mutableStateMapOf()
    }

    Firebase.firestore.collection("subs").document("cgrieb9@gmail.com")
        .addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.w("FIRESTORE", "Listen failed.", e)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                Log.d("FIRESTORE", "Current data: ${snapshot.data}")
                for (gateName in snapshot.data?.keys!!) {
                    if (gateName != "subId") {
                        subMap[gateName] = snapshot.data?.get(gateName) as Boolean;
                    }
                }
            } else {
                Log.d("FIRESTORE", "Current data: null")
            }
        }

    Column (verticalArrangement = Arrangement.spacedBy(20.dp)) {
        messages.forEach { message ->
            Row (verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(text = message)
                    Text(text = statusMap[message].toString())
                }
                Switch(checked = subMap.getOrDefault(message, false), onCheckedChange = {
                    Firebase.firestore.collection("subs")
                        .document("cgrieb9@gmail.com")
                        .update(message, !subMap.getOrDefault(message, false))
                })
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    val gateList: MutableList<String> = remember {
        mutableStateListOf()
    };

    val snowMap: SnapshotStateMap<String, String> = remember {
        mutableStateMapOf()
    }

    Firebase.firestore.collection("gateStatus")
        .get()
        .addOnSuccessListener { result ->
            Log.d("FIRESTORE", "SUCCESS")
            for (document in result) {
                Log.d("FIRESTORE", "${document.data["gateName"]}")
                gateList.add(document.data["gateName"].toString());
                if (document.data["status"] != "Open") {
                    Firebase.firestore.collection("solitudeDailySnow")
                        .whereGreaterThan("date", document.data["lastOpenDate"].toString())
                        .whereLessThan("date", LocalDate.now().toString())
                        .get()
                        .addOnSuccessListener { snowResult ->
                            var totalForGate = 0;
                            for (snowDoc in snowResult) {
                                totalForGate += (snowDoc.data["totalInches"] as Int);
                            }
                            Log.d("FIRESTORE", "Setting status for ${document.data["gateName"]} Closed")
                            snowMap[document.data["gateName"].toString()] = "${document.data["status"]}. $totalForGate in of fresh";
                        }
                } else {
                    Log.d("FIRESTORE", "Setting status for ${document.data["gateName"]} Open")
                    snowMap[document.data["gateName"].toString()] = "Open as of ${(document.data["lastOpenDate"] as com.google.firebase.Timestamp).toDate()}"
                }
            }
        }
        .addOnFailureListener { Log.d("FIRESTORE", "WE FUCKED IT") }


    RopeDropTheme {
        GateList(messages = gateList, snowMap)
    }
}