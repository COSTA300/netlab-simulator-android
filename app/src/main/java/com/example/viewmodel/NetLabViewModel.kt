package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cli.CliEngine
import com.example.database.AppDatabase
import com.example.database.SavedProject
import com.example.model.*
import com.example.repository.ProjectRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID

class NetLabViewModel(application: Application) : AndroidViewModel(application) {

    private val projectDao = AppDatabase.getDatabase(application).projectDao()
    private val repository = ProjectRepository(projectDao)

    val savedProjects: StateFlow<List<SavedProject>> = repository.allProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // App core State variables
    private val _currentTab = MutableStateFlow("dashboard") // "dashboard" or "editor"
    val currentTab: StateFlow<String> = _currentTab.asStateFlow()

    private val _activeLabTitle = MutableStateFlow("Free Lab Editor")
    val activeLabTitle: StateFlow<String> = _activeLabTitle.asStateFlow()

    private val _activeLabDesc = MutableStateFlow("Design any topology from scratch with zero restrictions.")
    val activeLabDesc: StateFlow<String> = _activeLabDesc.asStateFlow()

    private val _selectedLabId = MutableStateFlow<String?>(null)
    val selectedLabId: StateFlow<String?> = _selectedLabId.asStateFlow()

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    private val _links = MutableStateFlow<List<Link>>(emptyList())
    val links: StateFlow<List<Link>> = _links.asStateFlow()

    private val _activePackets = MutableStateFlow<List<PacketInfo>>(emptyList())
    val activePackets: StateFlow<List<PacketInfo>> = _activePackets.asStateFlow()

    private val _activeTerminalDeviceId = MutableStateFlow<String?>(null)
    val activeTerminalDeviceId: StateFlow<String?> = _activeTerminalDeviceId.asStateFlow()

    private val _terminalOutputs = MutableStateFlow<Map<String, String>>(emptyMap()) // deviceId -> text logs
    val terminalOutputs: StateFlow<Map<String, String>> = _terminalOutputs.asStateFlow()

    private val _terminalBuffer = MutableStateFlow("")
    val terminalBuffer: StateFlow<String> = _terminalBuffer.asStateFlow()

    private val _eventLogs = MutableStateFlow<List<String>>(listOf("System Initialized.", "Ready for network simulation."))
    val eventLogs: StateFlow<List<String>> = _eventLogs.asStateFlow()

    private val _lockMode = MutableStateFlow(false)
    val lockMode: StateFlow<Boolean> = _lockMode.asStateFlow()

    private val _snapToGrid = MutableStateFlow(false)
    val snapToGrid: StateFlow<Boolean> = _snapToGrid.asStateFlow()

    private val _scale = MutableStateFlow(1.0f)
    val scale: StateFlow<Float> = _scale.asStateFlow()

    private val _panX = MutableStateFlow(0f)
    val panX: StateFlow<Float> = _panX.asStateFlow()

    private val _panY = MutableStateFlow(0f)
    val panY: StateFlow<Float> = _panY.asStateFlow()

    private val _simulationSpeed = MutableStateFlow("Normal") // "Slow", "Normal", "Fast", "Instant"
    val simulationSpeed: StateFlow<String> = _simulationSpeed.asStateFlow()

    // Undo / Redo Stacks
    private val undoStack = mutableListOf<Pair<List<Device>, List<Link>>>()
    private val redoStack = mutableListOf<Pair<List<Device>, List<Link>>>()

    // Moshi for Local Saving Serialization
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val listDeviceType = Types.newParameterizedType(List::class.java, Device::class.java)
    private val listLinkType = Types.newParameterizedType(List::class.java, Link::class.java)
    private val deviceAdapter = moshi.adapter<List<Device>>(listDeviceType)
    private val linkAdapter = moshi.adapter<List<Link>>(listLinkType)

    init {
        // Run background loop to update packet simulation coordinates and state
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(50)
                updateSimulationPackets()
            }
        }
    }

    fun setTab(tab: String) {
        _currentTab.value = tab
    }

    fun setSimulationSpeed(speed: String) {
        _simulationSpeed.value = speed
    }

    fun toggleLockMode() {
        _lockMode.value = !_lockMode.value
    }

    fun toggleSnapToGrid() {
        _snapToGrid.value = !_snapToGrid.value
    }

    fun setCanvasTransforms(s: Float, px: Float, py: Float) {
        _scale.value = s.coerceIn(0.2f, 4.0f)
        _panX.value = px
        _panY.value = py
    }

    fun resetZoom() {
        _scale.value = 1.0f
        _panX.value = 0f
        _panY.value = 0f
    }

    fun addLog(message: String) {
        val current = _eventLogs.value.toMutableList()
        current.add(0, "[Live Logs] $message")
        if (current.size > 100) current.removeAt(current.size - 1)
        _eventLogs.value = current
    }

    fun saveHistory() {
        undoStack.add(_devices.value.toList() to _links.value.toList())
        if (undoStack.size > 50) undoStack.removeAt(0)
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val state = undoStack.removeAt(undoStack.size - 1)
            redoStack.add(_devices.value.toList() to _links.value.toList())
            _devices.value = state.first
            _links.value = state.second
            addLog("Undid last change.")
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val state = redoStack.removeAt(redoStack.size - 1)
            undoStack.add(_devices.value.toList() to _links.value.toList())
            _devices.value = state.first
            _links.value = state.second
            addLog("Redid change.")
        }
    }

    fun clearTopology() {
        saveHistory()
        _devices.value = emptyList()
        _links.value = emptyList()
        _activePackets.value = emptyList()
        _activeTerminalDeviceId.value = null
        _terminalOutputs.value = emptyMap()
        addLog("Cleared network topology canvas.")
    }

    // Equipment Add/Remove logic
    fun addDevice(type: DeviceType, model: String, x: Float, y: Float) {
        saveHistory()
        val uniqueId = UUID.randomUUID().toString()
        val count = _devices.value.count { it.type == type } + 1
        val namePrefix = when (type) {
            DeviceType.ROUTER -> "Router"
            DeviceType.SWITCH -> "Switch"
            DeviceType.FIREWALL -> "ASA"
            DeviceType.WIRELESS -> "AP"
            DeviceType.END_DEVICE -> "PC"
            DeviceType.INFRASTRUCTURE -> "Cloud"
        }
        val deviceName = "$namePrefix$count"

        // Generate default ports depending on device type
        val ports = generateDefaultPorts(type)

        val newDevice = Device(
            id = uniqueId,
            name = deviceName,
            model = model,
            type = type,
            x = x,
            y = y,
            ports = ports,
            runningConfig = "! Configuration for $deviceName\n!\nhostname $deviceName\n"
        )

        _devices.value = _devices.value + newDevice
        addLog("Placed new device: $deviceName ($model)")
    }

    fun updateDevicePosition(id: String, x: Float, y: Float) {
        val rawX = if (_snapToGrid.value) (x / 40).toInt() * 40f else x
        val rawY = if (_snapToGrid.value) (y / 40).toInt() * 40f else y

        _devices.value = _devices.value.map {
            if (it.id == id) it.copy(x = rawX, y = rawY) else it
        }
    }

    fun removeDevice(id: String) {
        saveHistory()
        val device = _devices.value.find { it.id == id } ?: return
        
        // Remove connected links
        _links.value = _links.value.filter { it.deviceIdA != id && it.deviceIdB != id }

        // Remove from device list
        _devices.value = _devices.value.filter { it.id != id }

        if (_activeTerminalDeviceId.value == id) {
            _activeTerminalDeviceId.value = null
        }

        addLog("Removed device ${device.name} and severed its cables.")
    }

    fun addLink(deviceIdA: String, portIdA: String, deviceIdB: String, portIdB: String, type: CableType) {
        saveHistory()
        val uniqueId = UUID.randomUUID().toString()
        val link = Link(
            id = uniqueId,
            deviceIdA = deviceIdA,
            portIdA = portIdA,
            deviceIdB = deviceIdB,
            portIdB = portIdB,
            cableType = type
        )

        // Update port connectivity statuses
        val updatePortStatus = { devices: List<Device> ->
            devices.map { d ->
                val updatedPorts = d.ports.map { p ->
                    if (d.id == deviceIdA && p.id == portIdA) {
                        p.copy(connectedLinkId = uniqueId, isUp = true)
                    } else if (d.id == deviceIdB && p.id == portIdB) {
                        p.copy(connectedLinkId = uniqueId, isUp = true)
                    } else p
                }
                d.copy(ports = updatedPorts)
            }
        }

        _devices.value = updatePortStatus(_devices.value)
        _links.value = _links.value + link

        val devA = _devices.value.find { it.id == deviceIdA }?.name ?: ""
        val devB = _devices.value.find { it.id == deviceIdB }?.name ?: ""
        addLog("Wired $devA to $devB via $type cable.")
    }

    fun removeLink(id: String) {
        saveHistory()
        val link = _links.value.find { it.id == id } ?: return
        
        // Sever ports
        _devices.value = _devices.value.map { d ->
            val updated = d.ports.map { p ->
                if (p.connectedLinkId == id) p.copy(connectedLinkId = null, isUp = false) else p
            }
            d.copy(ports = updated)
        }

        _links.value = _links.value.filter { it.id != id }
        addLog("Disconnected connection line.")
    }

    // Interactive Packet Simulation
    fun triggerCustomPacket(packet: PacketInfo) {
        _activePackets.value = _activePackets.value + packet
        addLog("Packet sent: ${packet.type.name} from ${getDeviceName(packet.sourceDeviceId)}")
    }

    private fun updateSimulationPackets() {
        val currentSpeed = _simulationSpeed.value
        val step = when (currentSpeed) {
            "Slow" -> 0.015f
            "Fast" -> 0.08f
            "Instant" -> 1.0f
            else -> 0.04f // Normal
        }

        val list = _activePackets.value.toMutableList()
        val iterator = list.listIterator()

        while (iterator.hasNext()) {
            val pkt = iterator.next()
            val nextProg = pkt.progress + step
            if (nextProg >= 1.0f) {
                // Determine completion / ping response trigger
                if (!pkt.isDropped) {
                    addLog("Packet ${pkt.type} successfully reached destination: ${getDeviceName(pkt.destDeviceId)}")
                    
                    // Trigger dynamic ICMP response ping callback
                    if (pkt.type == PacketType.ICMP && !pkt.summary.contains("Reply")) {
                        val replyPacket = createPingReplyPacket(pkt)
                        if (replyPacket != null) {
                            iterator.set(replyPacket)
                            continue
                        }
                    }
                } else {
                    addLog("Packet ${pkt.type} DROPPED. Reason: ${pkt.dropReason ?: "unreachable destination"}")
                }
                iterator.remove()
            } else {
                iterator.set(pkt.copy(progress = nextProg))
            }
        }
        _activePackets.value = list
    }

    private fun createPingReplyPacket(request: PacketInfo): PacketInfo? {
        val srcDev = _devices.value.find { it.id == request.destDeviceId } ?: return null
        val dstDev = _devices.value.find { it.id == request.sourceDeviceId } ?: return null

        val pathReply = request.path.reversed()
        val headersReply = request.headers.toMutableMap().apply {
            put("L2 DMAC", request.headers["L2 SMAC"] ?: "")
            put("L2 SMAC", request.headers["L2 DMAC"] ?: "")
            put("L3 SIP", request.headers["L3 DIP"] ?: "")
            put("L3 DIP", request.headers["L3 SIP"] ?: "")
            put("L4 Protocol", "ICMP (Type 0 Echo Reply)")
        }

        return PacketInfo(
            id = UUID.randomUUID().toString(),
            type = PacketType.ICMP,
            sourceDeviceId = srcDev.id,
            destDeviceId = dstDev.id,
            summary = "Echo Reply from ${srcDev.name}",
            path = pathReply,
            headers = headersReply,
            progress = 0f
        )
    }

    // Terminal Commands Executor
    fun openTerminalForDevice(deviceId: String) {
        _activeTerminalDeviceId.value = deviceId
        _terminalBuffer.value = ""
        val outputs = _terminalOutputs.value.toMutableMap()
        if (!outputs.containsKey(deviceId)) {
            val device = _devices.value.find { it.id == deviceId } ?: return
            outputs[deviceId] = "\n--- Cisco IOS Sandbox CLI v1.0 ---\n--- Type '?' for help, 'enable' to enter Privileged mode ---\n\n${device.name}${device.currentMode.promptSuffix} "
        }
        _terminalOutputs.value = outputs
        addLog("Opened IOS console on ${getDeviceName(deviceId)}.")
    }

    fun setTerminalBuffer(buffer: String) {
        _terminalBuffer.value = buffer
    }

    fun appendCharToTerminalBuffer(char: Char) {
        _terminalBuffer.value = _terminalBuffer.value + char
    }

    fun submitTerminalCommand() {
        val devId = _activeTerminalDeviceId.value ?: return
        val device = _devices.value.find { it.id == devId } ?: return
        val command = _terminalBuffer.value
        _terminalBuffer.value = ""

        val currentLogs = _terminalOutputs.value[devId] ?: ""
        val nextLogs = "$currentLogs$command\n"

        // Execute command
        val (updatedDevice, responseText) = CliEngine.executeCommand(
            device = device,
            commandLine = command,
            allDevices = _devices.value,
            allLinks = _links.value,
            onSimulationTrigger = { p -> triggerCustomPacket(p) }
        )

        // Update device in store
        _devices.value = _devices.value.map {
            if (it.id == devId) updatedDevice else it
        }

        val finalOutput = if (responseText.isNotEmpty()) {
            "$nextLogs$responseText\n${updatedDevice.name}${updatedDevice.currentMode.promptSuffix} "
        } else {
            "$nextLogs${updatedDevice.name}${updatedDevice.currentMode.promptSuffix} "
        }

        val outputs = _terminalOutputs.value.toMutableMap()
        outputs[devId] = finalOutput
        _terminalOutputs.value = outputs

        if (command.isNotBlank()) {
            addLog("${updatedDevice.name} executed CLI:: $command")
        }
    }

    fun executeTerminalCommandString(input: String) {
        _terminalBuffer.value = input
        submitTerminalCommand()
    }

    fun clearActiveTerminalOutput() {
        val devId = _activeTerminalDeviceId.value ?: return
        val device = _devices.value.find { it.id == devId } ?: return
        val outputs = _terminalOutputs.value.toMutableMap()
        outputs[devId] = "${device.name}${device.currentMode.promptSuffix} "
        _terminalOutputs.value = outputs
    }

    fun changeActiveTerminalDevice(deviceId: String) {
        _activeTerminalDeviceId.value = deviceId
        openTerminalForDevice(deviceId)
    }

    // Database Actions
    fun persistCurrentProject(name: String, desc: String) {
        viewModelScope.launch {
            try {
                val devJson = deviceAdapter.toJson(_devices.value)
                val inkJson = linkAdapter.toJson(_links.value)
                val proj = SavedProject(
                    id = _selectedLabId.value ?: UUID.randomUUID().toString(),
                    name = name,
                    description = desc,
                    dateModified = System.currentTimeMillis(),
                    devicesJson = devJson,
                    linksJson = inkJson
                )
                repository.saveProject(proj)
                addLog("Saved project '$name' to local storage.")
            } catch (e: IOException) {
                addLog("Failed to persist project configurations: ${e.message}")
            }
        }
    }

    fun loadSavedProject(proj: SavedProject) {
        try {
            val devList = deviceAdapter.fromJson(proj.devicesJson) ?: emptyList()
            val linkList = linkAdapter.fromJson(proj.linksJson) ?: emptyList()

            _selectedLabId.value = proj.id
            _activeLabTitle.value = proj.name
            _activeLabDesc.value = proj.description

            _devices.value = devList
            _links.value = linkList
            _activePackets.value = emptyList()
            _activeTerminalDeviceId.value = null
            _terminalOutputs.value = emptyMap()

            _currentTab.value = "editor"
            addLog("Loaded project: ${proj.name}")
        } catch (e: Exception) {
            addLog("Error restoring saved project: ${e.message}")
        }
    }

    fun deleteSavedProject(id: String) {
        viewModelScope.launch {
            repository.deleteProject(id)
            addLog("Deleted project link.")
        }
    }

    // Guided Lab Selection & Loader Templates
    fun loadLabTemplate(labId: String) {
        _selectedLabId.value = labId
        _activePackets.value = emptyList()
        _activeTerminalDeviceId.value = null
        _terminalOutputs.value = emptyMap()

        when (labId) {
            "lab1" -> {
                _activeLabTitle.value = "Lab 1: Welcome & Host Setup"
                _activeLabDesc.value = "Objective: Wire up and configure basic IP addresses.\n1. Place serial cable to connect Host PC1 (`Fa0/1`) to Router1 (`Fa0/0`).\n2. Open PC1 Terminal, configuration commands:\n   `enable` -> `configure terminal` -> `interface Fa0/1` -> `ip address 192.168.1.10 255.255.255.0`\n3. Configure Router1's interface Gig0/0 to `192.168.1.1` and type `no shutdown`!\n4. Use 'Ping' in PC1 CLI tool to check gateway reachability: `ping 192.168.1.1`."
                
                // Spawn pre-placed devices
                val host = Device(
                    id = "pc1",
                    name = "PC1",
                    model = "Standard Laptop Node",
                    type = DeviceType.END_DEVICE,
                    x = 200f,
                    y = 400f,
                    ports = generateDefaultPorts(DeviceType.END_DEVICE),
                    runningConfig = "! PC1 configuration initialized."
                )
                val r1 = Device(
                    id = "router1",
                    name = "Router1",
                    model = "Cisco 2911",
                    type = DeviceType.ROUTER,
                    x = 600f,
                    y = 400f,
                    ports = generateDefaultPorts(DeviceType.ROUTER),
                    runningConfig = "! Router1 initial config."
                )
                _devices.value = listOf(host, r1)
                _links.value = emptyList() // User wires them up
                addLog("Loaded Guide Lab 1. Wire PC1 to Router1 to build physical link topology!")
            }
            "lab2" -> {
                _activeLabTitle.value = "Lab 2: CCNA Static Routing"
                _activeLabDesc.value = "Objective: Implement static route. Build return connections.\n1. Set Router1 'ip route 172.16.1.0 255.255.255.0 192.168.12.2'\n2. Set Router2 'ip route 192.168.10.0 255.255.255.0 192.168.12.1'\n3. Test the connectivity by pinging between endpoints."
                
                // Pre-configured hosts, routers and connected link
                val pc1 = Device(
                    id = "pc1",
                    name = "PC1",
                    model = "Generic Workstation",
                    type = DeviceType.END_DEVICE,
                    x = 150f,
                    y = 400f,
                    ports = generateDefaultPorts(DeviceType.END_DEVICE).map {
                        if (it.name == "Fa0/1") it.copy(ipAddress = "192.168.10.10", subnetMask = "255.255.255.0", isUp = true) else it
                    }
                )
                val r1 = Device(
                    id = "r1",
                    name = "Router1",
                    model = "Cisco 2901",
                    type = DeviceType.ROUTER,
                    x = 400f,
                    y = 400f,
                    ports = generateDefaultPorts(DeviceType.ROUTER).map {
                        if (it.name == "Gi0/0") it.copy(ipAddress = "192.168.10.1", subnetMask = "255.255.255.0", isUp = true)
                        else if (it.name == "Gi0/1") it.copy(ipAddress = "192.168.12.1", subnetMask = "255.255.255.0", isUp = true)
                        else it
                    }
                )
                val r2 = Device(
                    id = "r2",
                    name = "Router2",
                    model = "Cisco 2901",
                    type = DeviceType.ROUTER,
                    x = 700f,
                    y = 400f,
                    ports = generateDefaultPorts(DeviceType.ROUTER).map {
                        if (it.name == "Gi0/0") it.copy(ipAddress = "172.16.1.1", subnetMask = "255.255.255.0", isUp = true)
                        else if (it.name == "Gi0/1") it.copy(ipAddress = "192.168.12.2", subnetMask = "255.255.255.0", isUp = true)
                        else it
                    }
                )
                val srv = Device(
                    id = "srv1",
                    name = "Server1",
                    model = "Data Center Server",
                    type = DeviceType.END_DEVICE,
                    x = 950f,
                    y = 400f,
                    ports = generateDefaultPorts(DeviceType.END_DEVICE).map {
                        if (it.name == "Fa0/1") it.copy(ipAddress = "172.16.1.100", subnetMask = "255.255.255.0", isUp = true) else it
                    }
                )

                _devices.value = listOf(pc1, r1, r2, srv)
                _links.value = listOf(
                    Link("l1", "pc1", "pc1-Fa0/1", "r1", "r1-Gi0/0", cableType = CableType.COPPER_STRAIGHT),
                    Link("l2", "r1", "r1-Gi0/1", "r2", "r2-Gi0/1", cableType = CableType.SERIAL),
                    Link("l3", "r2", "r2-Gi0/0", "srv1", "srv1-Fa0/1", cableType = CableType.COPPER_STRAIGHT)
                )

                // Wire internal ports up
                _devices.value = _devices.value.map { d ->
                    val updated = d.ports.map { p ->
                        if (d.id == "pc1" && p.name == "Fa0/1") p.copy(connectedLinkId = "l1")
                        else if (d.id == "r1" && p.name == "Gi0/0") p.copy(connectedLinkId = "l1")
                        else if (d.id == "r1" && p.name == "Gi0/1") p.copy(connectedLinkId = "l2")
                        else if (d.id == "r2" && p.name == "Gi0/1") p.copy(connectedLinkId = "l2")
                        else if (d.id == "r2" && p.name == "Gi0/0") p.copy(connectedLinkId = "l3")
                        else if (d.id == "srv1" && p.name == "Fa0/1") p.copy(connectedLinkId = "l3")
                        else p
                    }
                    d.copy(ports = updated)
                }

                addLog("Loaded Guide Lab 2. Route configurations missing! Enter CLI on R1 and R2 to set static routes.")
            }
            "lab3" -> {
                _activeLabTitle.value = "Lab 3: Switch VLANs & Trunking"
                _activeLabDesc.value = "Objective: Configure VLAN 10 & 20 and active port configurations on Cisco 2960 switch.\n1. Set switch mode on interface `Fa0/1` as access forwarding vlan 10.\n2. Verify state alignment."
                // Setup simpler switch
                val sw = Device(
                    id = "sw1",
                    name = "Switch1",
                    model = "Cisco 2960",
                    type = DeviceType.SWITCH,
                    x = 500f,
                    y = 350f,
                    ports = generateDefaultPorts(DeviceType.SWITCH)
                )
                _devices.value = listOf(sw)
                _links.value = emptyList()
                addLog("Loaded Guide Lab 3. VLAN configuration scenario ready.")
            }
            else -> {
                _selectedLabId.value = null
                _activeLabTitle.value = "Free Lab Editor"
                _activeLabDesc.value = "Design any topology from scratch with zero restrictions."
                _devices.value = emptyList()
                _links.value = emptyList()
            }
        }

        _currentTab.value = "editor"
    }

    // Dynamic Verification Engine
    fun runVerificationEngine(): Pair<Boolean, String> {
        val labId = _selectedLabId.value
        if (labId == null) {
            return false to "Not in a guided lab session. Save custom topologies using the Save menu."
        }

        return when (labId) {
            "lab1" -> {
                // Verify PC1 Fa0/1 IP is 192.168.1.10 and Router1 Gi0/0 is 192.168.1.1 up and linked
                val pc = _devices.value.find { it.id == "pc1" }
                val r1 = _devices.value.find { it.id == "router1" }
                val pcPort = pc?.ports?.find { it.name == "Fa0/1" }
                val r1Port = r1?.ports?.find { it.name == "Gi0/0" }

                val physicallyConnected = _links.value.any {
                    (it.deviceIdA == "pc1" && it.deviceIdB == "router1") || (it.deviceIdA == "router1" && it.deviceIdB == "pc1")
                }

                if (!physicallyConnected) {
                    return false to "Verification Failed: No physical cable connected between PC1 and Router1! Use the connection drawer."
                }
                if (pcPort?.ipAddress != "192.168.1.10") {
                    return false to "Verification Failed: PC1 interface Fa0/1 is not configured with '192.168.1.10'!"
                }
                if (r1Port?.ipAddress != "192.168.1.1") {
                    return false to "Verification Failed: Router1 interface Gig0/0 is not configured with '192.168.1.1'!"
                }
                if (r1Port.isUp == false) {
                    return false to "Verification Failed: Router1 Gig0/0 is administratively SHUTDOWN! Type 'no shutdown' in interface config."
                }

                true to "Verification SUCCESS! CCNA Lab 1 Completed. You've successfully configured local hosts, interfaces, and physical cable states."
            }
            "lab2" -> {
                // Verify static routing exists from Router1 to 172.16.1.0/24 via 192.168.12.2, and return routes
                val r1 = _devices.value.find { it.id == "r1" }
                val r2 = _devices.value.find { it.id == "r2" }

                val r1RouteOk = r1?.routingTable?.any {
                    it.destinationNetwork.startsWith("172.16.1.0") && it.nextHop == "192.168.12.2"
                } ?: false

                val r2RouteOk = r2?.routingTable?.any {
                    it.destinationNetwork.startsWith("192.168.10.0") && it.nextHop == "192.168.12.1"
                } ?: false

                if (!r1RouteOk) {
                    return false to "Verification Failed: Router1 has no static path statement for Server's network! Enter 'ip route 172.16.1.0 255.255.255.0 192.168.12.2'."
                }
                if (!r2RouteOk) {
                    return false to "Verification Failed: Router2 has no return path statement back to PC1's network! Enter 'ip route 192.168.10.0 255.255.255.0 192.168.12.1'."
                }

                true to "Verification SUCCESS! CCNA Lab 2 Static Routing completed! Gateways converged. End-to-end ping established."
            }
            "lab3" -> {
                val sw = _devices.value.find { it.id == "sw1" }
                val hasVlan10 = sw?.vlanDatabase?.containsKey(10) ?: false
                val portVlanMatch = sw?.ports?.find { it.name == "Fa0/1" }?.vlanId == 10

                if (!hasVlan10) {
                    return false to "Verification Failed: VLAN 10 database index is missing! Type 'vlan 10'."
                }
                if (!portVlanMatch) {
                    return false to "Verification Failed: Interface Fa0/1 is not active on VLAN 10! Type 'switchport access vlan 10' under interface Fa0/1."
                }

                true to "Verification SUCCESS! Switched Network VLAN configurations validated successfully on Cisco 2960 catalog simulation."
            }
            else -> false to "Standard verification complete: Configuration syntax verified."
        }
    }

    // Helper functions
    private fun generateDefaultPorts(type: DeviceType): List<Port> {
        return when (type) {
            DeviceType.ROUTER -> listOf(
                Port("r-Gi0/0", "Gi0/0", macAddress = "00e0.b03d.11c1"),
                Port("r-Gi0/1", "Gi0/1", macAddress = "00e0.b03d.11c2"),
                Port("r-Gi0/2", "Gi0/2", macAddress = "00e0.b03d.11c3")
            )
            DeviceType.SWITCH -> listOf(
                Port("s-Fa0/1", "Fa0/1", macAddress = "0010.5a3d.1aa1"),
                Port("s-Fa0/2", "Fa0/2", macAddress = "0010.5a3d.1aa2"),
                Port("s-Fa0/3", "Fa0/3", macAddress = "0010.5a3d.1aa3"),
                Port("s-Gi0/1", "Gi0/1", macAddress = "0010.5a3d.1ab1")
            )
            DeviceType.FIREWALL -> listOf(
                Port("fw-Fa0/1", "Fa0/1", macAddress = "00c0.0f31.11a1"),
                Port("fw-Fa0/2", "Fa0/2", macAddress = "00c0.0f31.11a2"),
                Port("fw-Gi0/1", "Gi0/1", macAddress = "00c0.0f31.11b1")
            )
            DeviceType.WIRELESS -> listOf(
                Port("ap-Gig0", "Gi0/1", macAddress = "0030.941c.21a1", isUp = true)
            )
            DeviceType.END_DEVICE -> listOf(
                Port("pc-Fa0/1", "Fa0/1", macAddress = "0001.c3db.e415")
            )
            DeviceType.INFRASTRUCTURE -> listOf(
                Port("c-Gig0", "Gi0/1", macAddress = "000a.e842.1da1")
            )
        }.map { p ->
            // Customize actual unique instance string references
            p.copy(id = "${UUID.randomUUID()}-${p.name}")
        }
    }

    private fun getDeviceName(id: String): String {
        return _devices.value.find { it.id == id }?.name ?: "Unknown"
    }
}
