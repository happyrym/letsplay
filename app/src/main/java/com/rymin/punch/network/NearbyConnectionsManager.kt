package com.rymin.punch.network

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.rymin.punch.data.LeaderboardEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class NearbyConnectionsManager(private val context: Context) {
    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val json = Json { ignoreUnknownKeys = true }
    private var connectedEndpointId: String? = null

    private val _connectionStatus = kotlinx.coroutines.flow.MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: kotlinx.coroutines.flow.StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    enum class ConnectionStatus {
        DISCONNECTED,
        ADVERTISING,
        CONNECTING,
        CONNECTED
    }

    companion object {
        private const val TAG = "NearbyConnections"
        private const val SERVICE_ID = "com.rymin.punch.leaderboard"
        private  val STRATEGY = Strategy.P2P_STAR
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Log.d(TAG, "Connection initiated with: ${connectionInfo.endpointName}")
            _connectionStatus.value = ConnectionStatus.CONNECTING
            // Automatically accept the connection
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Log.d(TAG, "Connection established with: $endpointId")
                    connectedEndpointId = endpointId
                    _connectionStatus.value = ConnectionStatus.CONNECTED
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    Log.d(TAG, "Connection rejected")
                    _connectionStatus.value = ConnectionStatus.ADVERTISING
                }
                ConnectionsStatusCodes.STATUS_ERROR -> {
                    Log.d(TAG, "Connection error")
                    _connectionStatus.value = ConnectionStatus.ADVERTISING
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Disconnected from: $endpointId")
            if (connectedEndpointId == endpointId) {
                connectedEndpointId = null
                _connectionStatus.value = ConnectionStatus.ADVERTISING
            }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            // Game app (host) doesn't need to receive data, only send
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Handle transfer updates if needed
        }
    }

    /**
     * Start advertising as a host (Game app on tablet)
     */
    fun startAdvertising(deviceName: String = "Punch Game") {
        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(STRATEGY)
            .build()

        connectionsClient.startAdvertising(
            deviceName,
            SERVICE_ID,
            connectionLifecycleCallback,
            advertisingOptions
        ).addOnSuccessListener {
            Log.d(TAG, "Started advertising")
            _connectionStatus.value = ConnectionStatus.ADVERTISING
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to start advertising", e)
        }
    }

    /**
     * Stop advertising
     */
    fun stopAdvertising() {
        connectionsClient.stopAdvertising()
    }

    /**
     * Send leaderboard data to connected client
     */
    fun sendLeaderboard(leaderboard: List<LeaderboardEntry>) {
        connectedEndpointId?.let { endpointId ->
            try {
                val jsonData = json.encodeToString(leaderboard)
                val payload = Payload.fromBytes(jsonData.toByteArray())

                connectionsClient.sendPayload(endpointId, payload)
                    .addOnSuccessListener {
                        Log.d(TAG, "Leaderboard sent successfully")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to send leaderboard", e)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error encoding leaderboard", e)
            }
        } ?: Log.w(TAG, "No connected endpoint to send data")
    }

    /**
     * Disconnect all connections
     */
    fun disconnect() {
        connectionsClient.stopAllEndpoints()
        connectedEndpointId = null
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
    }

    /**
     * Check if connected to a client
     */
    fun isConnected(): Boolean = connectedEndpointId != null
}
