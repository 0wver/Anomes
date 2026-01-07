package com.example.anomess.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.freehaven.tor.control.TorControlConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.net.Socket
import java.util.Scanner

class TorManager(private val context: Context) {

    var isTorRunning: Boolean = false
        private set
    var isRestarting: Boolean = false
        private set
    private var lastRestartTime: Long = 0 // Track last restart to prevent loops
    private var onionAddress: String? = null
    
    private var torProcess: Process? = null

    private var controlConnection: TorControlConnection? = null
    
    private val mutex = kotlinx.coroutines.sync.Mutex()

    companion object {
        private const val TAG = "TorManager"
        private const val HIDDEN_SERVICE_PORT = 8080 
        private const val VIRTUAL_PORT = 80
        private const val CONTROL_PORT = 9051
        private const val SOCKS_PORT = 9050
        
        // Simple in-memory log buffer for debugging without ADB
        private val logBuilder = StringBuilder()
        private val _logFlow = MutableStateFlow("")
        
        // Expose flow for UI observation
        val logFlow: StateFlow<String> = _logFlow.asStateFlow()
        
        // Legacy access if needed
        val debugLogs: StringBuilder 
            get() = logBuilder
            
        fun log(msg: String) {
             if (com.example.anomess.BuildConfig.DEBUG) {
                 Log.d(TAG, msg)
                 synchronized(logBuilder) {
                     logBuilder.append(msg).append("\n")
                     if (logBuilder.length > 5000) {
                         logBuilder.delete(0, logBuilder.length - 5000)
                     }
                     _logFlow.value = logBuilder.toString()
                 }
             }
        }
        
        fun error(msg: String, e: Throwable? = null) {
            if (com.example.anomess.BuildConfig.DEBUG) {
                Log.e(TAG, msg, e)
                synchronized(logBuilder) {
                    logBuilder.append("ERROR: ").append(msg).append("\n")
                    if (e != null) {
                        logBuilder.append(e.stackTraceToString()).append("\n")
                    }
                     if (logBuilder.length > 5000) {
                         logBuilder.delete(0, logBuilder.length - 5000)
                     }
                    _logFlow.value = logBuilder.toString()
                }
            }
        }
    }

    suspend fun startTor(): Boolean {
        mutex.lock()
        try {
            return withContext(Dispatchers.IO) {
                try {
                if (isTorRunning) {
                    log("Tor is already running (flag set).")
                    return@withContext true
                }

                // FORCE KILL old instances to free up lock/ports
                killOldTorProcesses()
                cleanupOrphanTor()
                
                // Allow a moment for OS cleanup
                Thread.sleep(500)
                
                // 1. Locate Tor binary in nativeLibraryDir (Do NOT extract to cache, that fails W^X)
                val libPath = context.applicationInfo.nativeLibraryDir
                val torBin = File(libPath, "libtor.so")
                
                log("Locating Tor binary at: ${torBin.absolutePath}")
                
                if (!torBin.exists() || !torBin.canExecute()) {
                    // Fallback search if exact name check slightly failed or weird split
                    val libDir = File(libPath)
                    val found = libDir.listFiles()?.find { it.name.startsWith("libtor") }
                    if (found != null && found.canExecute()) {
                         log("Found alternative binary: ${found.absolutePath}")
                         // We must use the found file
                         return@withContext launchTor(found)
                    }
                    
                    error("Tor binary not found or not executable in nativeLibraryDir.")
                    log("Files found: ${java.io.File(libPath).listFiles()?.map { it.name } }")
                    return@withContext false
                }
                
                return@withContext launchTor(torBin)

            } catch (e: Exception) {
                error("Error starting Tor", e)
                stopTor()
                false
            }
        }
        } finally {
            if (mutex.isLocked) mutex.unlock()
        }
    }

    private suspend fun launchTor(torBin: File): Boolean {
         try {
                // 2. Prepare Config (torrc)
                val torrc = File(context.cacheDir, "torrc")
                val dataDir = File(context.cacheDir, "data")
                dataDir.mkdirs()
                
                // Hidden service directory
                val hiddenServiceDir = File(context.filesDir, "hidden_service")
                hiddenServiceDir.mkdirs()
                // Fix permissions for hidden service dir (Tor requires strict 700)
                // In generic Android app private storage, this is usually implied, 
                // but setting readable/writable only by owner is good practice.
                hiddenServiceDir.setReadable(false, false) // Deny all
                hiddenServiceDir.setReadable(true, true)   // Allow owner
                hiddenServiceDir.setWritable(false, false)
                hiddenServiceDir.setWritable(true, true)
                hiddenServiceDir.setExecutable(false, false)
                hiddenServiceDir.setExecutable(true, true) // Needed for directory traversal

                createTorrc(torrc, dataDir, hiddenServiceDir)
                log("Torrc created.")

                // 3. Launch Process
                val pb = ProcessBuilder(
                    torBin.absolutePath,
                    "-f", torrc.absolutePath
                )
                
                val env = pb.environment()
                env["HOME"] = context.cacheDir.absolutePath
                env["LD_LIBRARY_PATH"] = context.applicationInfo.nativeLibraryDir
                
                log("Executing: ${pb.command()}")
                torProcess = pb.start()
                
                // wrapper to read error stream
                Thread {
                    try {
                        val reader = Scanner(torProcess?.errorStream)
                        while (reader.hasNextLine()) {
                            val line = reader.nextLine()
                            log("Tor stderr: $line")
                        }
                    } catch (e: Exception) {}
                }.start()
                
                // 4. Connect to Control Port (Retry loop)
                log("Waiting for Control Port $CONTROL_PORT...")
                if (connectToControlPort()) {
                    isTorRunning = true
                    // 5. Get hidden service hostname
                    val hostnameFile = File(hiddenServiceDir, "hostname")
                    // Wait loop for hostname
                    for (i in 1..10) {
                        if (hostnameFile.exists()) {
                           val raw = hostnameFile.readText()
                           // Keep only alphanumeric and dot (onion addresses are base32 + .onion)
                           onionAddress = raw.filter { it.isLetterOrDigit() || it == '.' }
                           log("Onion Address generated: '$onionAddress' (${onionAddress?.length})")
                           break
                        }
                        log("Waiting for hostname file...")
                        Thread.sleep(1000)
                    }
                    if (onionAddress == null) log("Warning: Hostname file not found after waiting.")
                    
                    return true
                } else {
                     error("Failed to connect to Tor Control Port. Process might have died.")
                     stopTor()
                     return false
                }
         } catch (e: Exception) {
             error("Exception during launch", e)
             return false
         }
    }

    private fun createTorrc(torrc: File, dataDir: File, hiddenServiceDir: File) {
        val config = """
            DataDirectory ${dataDir.absolutePath}
            ControlPort $CONTROL_PORT
            SocksPort 127.0.0.1:$SOCKS_PORT
            CookieAuthentication 1
            HiddenServiceDir ${hiddenServiceDir.absolutePath}
            HiddenServicePort $VIRTUAL_PORT 127.0.0.1:$HIDDEN_SERVICE_PORT
            HiddenServicePort $VIRTUAL_PORT 127.0.0.1:$HIDDEN_SERVICE_PORT
            SafeLogging 1
            Log notice stdout
        """.trimIndent()
        
        torrc.writeText(config)
    }

    private fun connectToControlPort(): Boolean {
        var retries = 0
        while (retries < 15) {
            try {
                Thread.sleep(1000)
                val socket = Socket("127.0.0.1", CONTROL_PORT)
                controlConnection = TorControlConnection(socket)
                controlConnection?.launchThread(true) // Daemon thread
                controlConnection?.authenticate(File(context.cacheDir, "data/control_auth_cookie").readBytes())
                
                log("Connected to Tor Control Port!")
                return true
            } catch (e: Exception) {
                log("Waiting for Control Port... (${e.message})")
                retries++
            }
        }
        return false
    }

    fun stopTor() {
        try {
            controlConnection?.shutdownTor("HALT") // Ask nicely
            controlConnection = null
        } catch (e: Exception) {}
        
        torProcess?.destroy()
        torProcess = null
        isTorRunning = false
    }
    
    fun getOnionHostname(): String? {
         return onionAddress
    }
    
    fun getSocksPort(): Int {
        return SOCKS_PORT
    }
    
    /**
     * Fully restart Tor when network conditions have changed.
     * This is more reliable than just requesting a new circuit when the network was lost.
     * Includes a 30-second stabilization period for hidden services to republish.
     * Has a 5-minute cooldown to prevent infinite restart loops.
     */
    suspend fun restartTor(): Boolean {
        // Prevent restart loops - 5 minute cooldown
        val timeSinceLastRestart = System.currentTimeMillis() - lastRestartTime
        val cooldownMs = 5 * 60 * 1000L // 5 minutes
        
        if (lastRestartTime > 0 && timeSinceLastRestart < cooldownMs) {
            val remainingSeconds = (cooldownMs - timeSinceLastRestart) / 1000
            log("Restart blocked - cooldown active. ${remainingSeconds}s remaining until next restart allowed.")
            log("The target user's hidden service may be offline. Please wait and retry later.")
            return false
        }
        
        isRestarting = true
        lastRestartTime = System.currentTimeMillis()
        log("Restarting Tor due to network change...")
        stopTor()
        Thread.sleep(2000) // Wait for process cleanup
        val result = startTor()
        
        if (result) {
            // Wait for hidden services to republish BEFORE clearing isRestarting
            log("Tor restarted. Waiting 30s for hidden services to stabilize...")
            kotlinx.coroutines.delay(30_000)
            log("Stabilization complete. Resuming normal operation.")
        }
        
        isRestarting = false
        return result
    }
    
    /**
     * Request a new circuit from Tor. This is useful when connections to a hidden service
     * fail after the service went offline and came back online.
     * Sends SIGNAL NEWNYM to the Tor control connection.
     */
    /**
     * Request a new circuit from Tor. Also checks if control connection is alive.
     */
    fun requestNewCircuit() {
        try {
            // Ensure connection is alive before trying to signal
            reconnectControlPortIfNeeded()
            
            if (controlConnection != null) {
                log("Sending SIGNAL NEWNYM to request new Tor circuits...")
                controlConnection?.signal("NEWNYM")
                log("New circuit requested successfully")
            } else {
                log("Cannot request new circuit - no control connection")
            }
        } catch (e: Exception) {
            error("Failed to request new circuit", e)
            // If it failed, force null to retry next time
            controlConnection = null
        }
    }
    
    private fun reconnectControlPortIfNeeded() {
        if (controlConnection == null || torProcess == null) {
            // Tor not running or explicit stop
            if (isTorRunning) {
                 log("Control connection lost but Tor supposed to be running. Attempting reconnect...")
                 connectToControlPort()
            }
            return
        }
        
        try {
            // Simple liveness check or just rely on variable?
            // Sending a cheap command like 'GETINFO version' confirms liveness
            controlConnection?.getInfo("version")
        } catch (e: Exception) {
             log("Control connection appears dead: ${e.message}. Reconnecting...")
             controlConnection = null
             connectToControlPort()
        }
    }
    
    private fun killOldTorProcesses() {
        try {
             log("Attempting to kill lingering Tor processes...")
             
             // 1. Destroy our managed process
             torProcess?.destroy()
             torProcess = null
             
             // Avoid shell commands as they can hang or fail on unrooted devices
        } catch (e: Exception) {
            // Ignore errors here
        }
    }

    private fun cleanupOrphanTor() {
        try {
            log("Checking for orphan Tor processes on port $CONTROL_PORT...")
            // Try to connect to an existing Tor instance
            val socket = Socket("127.0.0.1", CONTROL_PORT)
            
            // If we connected, something is running. Try to kill it nicely.
            log("Found running Tor instance. Attempting to shutdown via Control Protocol...")
            val conn = TorControlConnection(socket)
            
            // Try to read auth cookie if it exists from previous run
            val authFile = File(context.cacheDir, "data/control_auth_cookie")
            if (authFile.exists()) {
                 conn.authenticate(authFile.readBytes())
                 conn.shutdownTor("HALT")
                 log("Orphan Tor shutdown command sent.")
            } else {
                log("Orphan Tor found but no auth cookie. Cannot kill via control port.")
            }
            socket.close()
        } catch (e: Exception) {
            // Failure to connect means no Tor is running on that port. Good.
            log("No orphan Tor found on control port.")
        }
    }
}
