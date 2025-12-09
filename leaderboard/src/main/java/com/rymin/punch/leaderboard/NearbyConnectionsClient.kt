package com.rymin.punch.leaderboard

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

class NearbyConnectionsClient(private val context: Context) {
    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val json = Json { ignoreUnknownKeys = true }
    private var connectedEndpointId: String? = null

    private val _leaderboardFlow = MutableStateFlow<List<LeaderboardEntry>>(emptyList())
    val leaderboardFlow: StateFlow<List<LeaderboardEntry>> = _leaderboardFlow.asStateFlow()

    private val _connectionStatus = MutableStateFlow(false)
    val connectionStatus: StateFlow<Boolean> = _connectionStatus.asStateFlow()

    companion object {
        private const val TAG = "NearbyClient"
        private const val SERVICE_ID = "com.rymin.punch.leaderboard"
        private  val STRATEGY = Strategy.P2P_STAR
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "Endpoint found: ${info.endpointName}")
            // Automatically connect to the discovered game
            connectionsClient.requestConnection(
                "Leaderboard Display",
                endpointId,
                connectionLifecycleCallback
            )
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Endpoint lost: $endpointId")
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Log.d(TAG, "Connection initiated with: ${connectionInfo.endpointName}")
            // Automatically accept the connection
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Log.d(TAG, "Connection established with: $endpointId")
                    connectedEndpointId = endpointId
                    _connectionStatus.value = true
                    // Stop discovery once connected
                    stopDiscovery()
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    Log.d(TAG, "Connection rejected")
                    _connectionStatus.value = false
                }
                ConnectionsStatusCodes.STATUS_ERROR -> {
                    Log.d(TAG, "Connection error")
                    _connectionStatus.value = false
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Disconnected from: $endpointId")
            if (connectedEndpointId == endpointId) {
                connectedEndpointId = null
                _connectionStatus.value = false
                // Restart discovery
                startDiscovery()
            }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            payload.asBytes()?.let { bytes ->
                try {
                    val jsonString = String(bytes)
                    val leaderboard = json.decodeFromString<List<LeaderboardEntry>>(jsonString)
                    _leaderboardFlow.value = leaderboard
                    Log.d(TAG, "Received leaderboard: ${leaderboard.size} entries")
                } catch (e: Exception) {
                    Log.e(TAG, "Error decoding leaderboard", e)
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Handle transfer updates if needed
        }
    }

    /**
     * Start discovering hosts (Game app)
     */
    fun startDiscovery() {
        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(STRATEGY)
            .build()

        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            discoveryOptions
        ).addOnSuccessListener {
            Log.d(TAG, "Started discovery")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to start discovery", e)
        }
    }

    /**
     * Stop discovering
     */
    fun stopDiscovery() {
        connectionsClient.stopDiscovery()
    }

    /**
     * Disconnect from host
     */
    fun disconnect() {
        connectionsClient.stopAllEndpoints()
        connectedEndpointId = null
        _connectionStatus.value = false
    }

    /**
     * Check if connected to host
     */
    fun isConnected(): Boolean = connectedEndpointId != null
}
