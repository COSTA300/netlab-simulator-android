package com.example.cli

import com.example.model.*
import java.util.UUID

object CliEngine {

    // Helper to auto-complete commands based on current mode
    fun getCompletions(commandLine: String, mode: CliMode): List<String> {
        val trimmed = commandLine.trim()
        val tokens = trimmed.split("\\s+".toRegex())
        val lastToken = tokens.lastOrNull() ?: ""
        val commands = getAvailableCommandsForMode(mode)

        if (tokens.size <= 1) {
            return commands.filter { it.startsWith(lastToken, ignoreCase = true) }
        }

        // Sub-commands completion
        val firstCmd = tokens[0].lowercase()
        return when {
            firstCmd == "show" -> listOf("ip route", "ip interface brief", "running-config", "startup-config", "vlan brief", "spanning-tree", "dhcp lease").filter { it.startsWith(commandLine.substringAfter("show "), ignoreCase = true) }.map { "show $it" }
            firstCmd == "no" -> listOf("shutdown", "ip address").filter { it.startsWith(commandLine.substringAfter("no "), ignoreCase = true) }.map { "no $it" }
            firstCmd == "copy" -> listOf("running-config startup-config", "startup-config running-config").filter { it.startsWith(commandLine.substringAfter("copy "), ignoreCase = true) }.map { "copy $it" }
            firstCmd == "ip" -> listOf("address", "route", "dhcp pool", "nat inside", "nat outside").filter { it.startsWith(commandLine.substringAfter("ip "), ignoreCase = true) }.map { "ip $it" }
            firstCmd == "switchport" -> listOf("mode access", "mode trunk", "access vlan").filter { it.startsWith(commandLine.substringAfter("switchport "), ignoreCase = true) }.map { "switchport $it" }
            else -> emptyList()
        }
    }

    private fun getAvailableCommandsForMode(mode: CliMode): List<String> {
        return when (mode) {
            CliMode.USER_EXEC -> listOf("enable", "ping", "traceroute", "exit")
            CliMode.PRIVILEGED_EXEC -> listOf("disable", "configure terminal", "show", "ping", "traceroute", "write", "copy", "reload", "erase", "exit")
            CliMode.GLOBAL_CONFIG -> listOf("interface", "vlan", "router", "ip", "access-list", "no", "exit", "end")
            CliMode.INTERFACE_CONFIG -> listOf("ip", "no", "shutdown", "switchport", "exit", "end")
            CliMode.VLAN_CONFIG -> listOf("name", "exit", "end")
            CliMode.ROUTER_CONFIG -> listOf("network", "redistribute", "passive-interface", "exit", "end")
        }
    }

    fun getHelpText(commandLine: String, mode: CliMode): String {
        val cmd = commandLine.trim().lowercase()
        val list = getAvailableCommandsForMode(mode)
        
        return when {
            cmd.isEmpty() -> list.joinToString("\n") { "  %-18s: description for %s".format(it, it) }
            cmd.startsWith("sh") -> "Available show commands:\n  show ip route\n  show ip interface brief\n  show running-config\n  show startup-config\n  show vlan brief\n  show spanning-tree"
            cmd.startsWith("con") -> "configure terminal: Enter global configuration mode"
            cmd.startsWith("int") -> "interface <interface-name>: Select design port to configure"
            cmd.startsWith("ip") -> "ip address <ip> <mask\n  ip route <destination> <mask> <next-hop>\n  ip nat..."
            else -> "Help info for '$commandLine':\n" + list.filter { it.startsWith(cmd) }.joinToString("\n") { "  $it" }
        }
    }

    fun executeCommand(
        device: Device,
        commandLine: String,
        allDevices: List<Device>,
        allLinks: List<Link>,
        onSimulationTrigger: (PacketInfo) -> Unit
    ): Pair<Device, String> {
        val line = commandLine.trim()
        if (line.isEmpty()) return device to ""

        val tokens = line.split("\\s+".toRegex())
        val cmd = tokens[0].lowercase()

        // Globally supported command for exit/end
        if (cmd == "exit") {
            return when (device.currentMode) {
                CliMode.USER_EXEC -> device to "% Connection closed.\n"
                CliMode.PRIVILEGED_EXEC -> device.copy(currentMode = CliMode.USER_EXEC) to ""
                CliMode.GLOBAL_CONFIG -> device.copy(currentMode = CliMode.PRIVILEGED_EXEC) to ""
                CliMode.INTERFACE_CONFIG -> device.copy(currentMode = CliMode.GLOBAL_CONFIG, currentInterfaceId = null) to ""
                CliMode.VLAN_CONFIG -> device.copy(currentMode = CliMode.GLOBAL_CONFIG, currentVlanId = null) to ""
                CliMode.ROUTER_CONFIG -> device.copy(currentMode = CliMode.GLOBAL_CONFIG) to ""
            }
        }
        if (cmd == "end") {
            if (device.currentMode != CliMode.USER_EXEC && device.currentMode != CliMode.PRIVILEGED_EXEC) {
                return device.copy(currentMode = CliMode.PRIVILEGED_EXEC, currentInterfaceId = null, currentVlanId = null) to ""
            }
        }

        // Shorthand mapper & logic executor depending on Mode
        return when (device.currentMode) {
            CliMode.USER_EXEC -> handleUserMode(device, cmd, tokens, line, allDevices, onSimulationTrigger)
            CliMode.PRIVILEGED_EXEC -> handlePrivilegedMode(device, cmd, tokens, line, allDevices, allLinks, onSimulationTrigger)
            CliMode.GLOBAL_CONFIG -> handleGlobalConfigMode(device, cmd, tokens, line)
            CliMode.INTERFACE_CONFIG -> handleInterfaceConfigMode(device, cmd, tokens, line)
            CliMode.VLAN_CONFIG -> handleVlanConfigMode(device, cmd, tokens, line)
            CliMode.ROUTER_CONFIG -> handleRouterConfigMode(device, cmd, tokens, line)
        }
    }

    private fun handleUserMode(
        device: Device,
        cmd: String,
        tokens: List<String>,
        fullLine: String,
        allDevices: List<Device>,
        onSimulationTrigger: (PacketInfo) -> Unit
    ): Pair<Device, String> {
        return when {
            cmd == "enable" || cmd == "en" -> {
                device.copy(currentMode = CliMode.PRIVILEGED_EXEC) to ""
            }
            cmd == "ping" -> {
                if (tokens.size < 2) {
                    device to "% Please specify an IP address or destination hostname."
                } else {
                    val destIp = tokens[1]
                    triggerPingSimulation(device, destIp, allDevices, onSimulationTrigger)
                    device to "Type escape sequence to abort.\nSending 5, 100-byte ICMP Echos to $destIp, timeout is 2 seconds:\nGenerating ping request... ICMP dot emitted to the network."
                }
            }
            cmd == "traceroute" || cmd == "trace" -> {
                if (tokens.size < 2) {
                    device to "% Please specify destination IP."
                } else {
                    val destIp = tokens[1]
                    device to "Type escape sequence to abort. Tracing route to $destIp\n  1 * * *\n  2 ICMP packets traveling topology. Trace initiated successfully."
                }
            }
            else -> device to "% Invalid or incomplete command. Type 'enable' to enter privileged mode."
        }
    }

    private fun handlePrivilegedMode(
        device: Device,
        cmd: String,
        tokens: List<String>,
        fullLine: String,
        allDevices: List<Device>,
        allLinks: List<Link>,
        onSimulationTrigger: (PacketInfo) -> Unit
    ): Pair<Device, String> {
        return when {
            cmd == "disable" || cmd == "di" -> {
                device.copy(currentMode = CliMode.USER_EXEC) to ""
            }
            cmd == "configure" || cmd == "conf" -> {
                if (tokens.size > 1 && tokens[1].lowercase().startsWith("t")) {
                    device.copy(currentMode = CliMode.GLOBAL_CONFIG) to "Enter configuration commands, one per line. End with CNTL/Z."
                } else {
                    device to "% Use 'configure terminal' to enter configuration mode."
                }
            }
            cmd == "write" || cmd == "wr" || (cmd == "copy" && tokens.size > 2 && tokens[1] == "run" && tokens[2] == "start") -> {
                val updated = device.copy(
                    startupConfig = device.runningConfig + "\n! Startup configuration saved.",
                )
                updated to "[OK]\nBuilding configuration...\nCompressed configuration size: 1024 bytes\nSaved running-config to startup-config successfully."
            }
            cmd == "erase" && tokens.size > 1 && tokens[1].contains("start") -> {
                val updated = device.copy(startupConfig = "")
                updated to "Erasing the nvram filesystem will remove all configuration files! Continue? [confirm]\n[OK]\nErase of nvram: complete"
            }
            cmd == "reload" -> {
                val activePorts = device.ports.map { it.copy(isUp = false) }
                val rebooted = device.copy(
                    currentMode = CliMode.USER_EXEC,
                    runningConfig = "! System rebooted. Configuration reset to startup.\n" + device.startupConfig,
                    ports = activePorts
                )
                rebooted to "\nProceed with reload? [confirm]\n\n*System rebooting...\n*Initializing interfaces...\n*System Ready.\n"
            }
            cmd == "show" || cmd == "sh" -> {
                if (tokens.size < 2) {
                    device to "% Incomplete show command."
                } else {
                    val subCmd = tokens[1].lowercase()
                    when {
                        subCmd == "running-config" || subCmd == "run" -> {
                            device to buildRunningConfigOutput(device)
                        }
                        subCmd == "startup-config" || subCmd == "start" -> {
                            if (device.startupConfig.isEmpty()) device to "startup-config is not present"
                            else device to device.startupConfig
                        }
                        subCmd == "ip" && tokens.size > 2 && tokens[2].lowercase().startsWith("ro") -> {
                            device to buildRoutingTableOutput(device)
                        }
                        subCmd == "ip" && tokens.size > 2 && tokens[2].lowercase().startsWith("int") -> {
                            device to buildIpInterfacesOutput(device)
                        }
                        subCmd == "vlan" -> {
                            device to buildVlanBriefOutput(device)
                        }
                        subCmd == "spanning-tree" || subCmd == "span" -> {
                            device to "VLAN0001\n  Spanning tree enabled protocol ieee\n  Root ID    Priority    32769\n             Address     0010.5a3d.1dd2\n             This bridge is the root!\n  Port ${device.ports.firstOrNull()?.name ?: "Fa0/1"} status: Forwarding (FWD)"
                        }
                        subCmd == "dhcp" -> {
                            device to "IP address      Client Hardware Address     Type       Lease expiration\n192.168.1.15    0001.c3db.e415              Automatic  3600s"
                        }
                        else -> device to "% Unsupported show sub-command: '${tokens.subList(1, tokens.size).joinToString(" ")}'"
                    }
                }
            }
            cmd == "ping" -> {
                if (tokens.size < 2) {
                    device to "% Please specify destination IP."
                } else {
                    val destIp = tokens[1]
                    triggerPingSimulation(device, destIp, allDevices, onSimulationTrigger)
                    device to "Sending 5, 100-byte ICMP Echos to $destIp, timeout is 2 seconds:\nGenerating ping request... ICMP dot emitted to the network."
                }
            }
            else -> device to "% Invalid command. Available commands: show, configure terminal, write, erase, reload, ping, traceroute, exit"
        }
    }

    private fun handleGlobalConfigMode(
        device: Device,
        cmd: String,
        tokens: List<String>,
        fullLine: String
    ): Pair<Device, String> {
        return when {
            cmd == "hostname" && tokens.size > 1 -> {
                val newName = tokens[1]
                val currentConf = device.runningConfig + "\nhostname $newName"
                device.copy(name = newName, runningConfig = currentConf) to ""
            }
            cmd == "interface" || cmd == "int" -> {
                if (tokens.size < 2) {
                    device to "% Incomplete interface command."
                } else {
                    val targetName = tokens[1].lowercase()
                    val matchedPort = device.ports.find { it.name.lowercase().contains(targetName) || targetName.contains(it.name.lowercase()) }
                    if (matchedPort != null) {
                        device.copy(currentMode = CliMode.INTERFACE_CONFIG, currentInterfaceId = matchedPort.id) to ""
                    } else {
                        device to "% Port '$targetName' not found on this hardware."
                    }
                }
            }
            cmd == "vlan" -> {
                if (tokens.size < 2) {
                    device to "% Incomplete vlan command."
                } else {
                    val vlanId = tokens[1].toIntOrNull()
                    if (vlanId != null && vlanId in 1..4094) {
                        val newVlans = device.vlanDatabase.toMutableMap()
                        if (!newVlans.containsKey(vlanId)) {
                            newVlans[vlanId] = "VLAN$vlanId"
                        }
                        device.copy(currentMode = CliMode.VLAN_CONFIG, currentVlanId = vlanId, vlanDatabase = newVlans) to ""
                    } else {
                        device to "% Invalid VLAN ID."
                    }
                }
            }
            cmd == "ip" && tokens.size > 2 && tokens[1] == "route" -> {
                // ip route 10.0.0.0 255.255.255.0 192.168.1.1
                if (tokens.size < 5) {
                    device to "% Incomplete ip route statement."
                } else {
                    val destNetwork = tokens[2]
                    val mask = tokens[3]
                    val nextHop = tokens[4]
                    val newRoute = Route(destinationNetwork = "$destNetwork/$mask", nextHop = nextHop, protocol = "Static")
                    val updatedRoutes = device.routingTable.toMutableList()
                    updatedRoutes.add(newRoute)
                    val newConfig = device.runningConfig + "\nip route $destNetwork $mask $nextHop"
                    device.copy(routingTable = updatedRoutes, runningConfig = newConfig) to ""
                }
            }
            cmd == "ip" && tokens.size > 2 && tokens[1] == "dhcp" && tokens[2] == "pool" -> {
                val newConfig = device.runningConfig + "\nip dhcp pool ${tokens.getOrElse(3) { "POOL" }}"
                device.copy(runningConfig = newConfig, activeServices = device.activeServices + ("DHCP" to true)) to "Configuring DHCP server engine pool. Basic auto-allocation configured."
            }
            cmd == "access-list" && tokens.size > 2 -> {
                // simple ACL logging
                val newConfig = device.runningConfig + "\n$fullLine"
                device.copy(runningConfig = newConfig) to "Access-list configured successfully."
            }
            cmd == "router" && tokens.size > 1 -> {
                val proto = tokens[1].lowercase()
                if (proto == "rip" || proto == "ospf") {
                    val newConfig = device.runningConfig + "\nrouter $proto"
                    device.copy(currentMode = CliMode.ROUTER_CONFIG, runningConfig = newConfig) to ""
                } else {
                    device to "% Dynamic protocol '$proto' not loaded in iOS sandbox."
                }
            }
            cmd == "no" -> {
                device to "Command disabled/cleared successfully."
            }
            else -> device to "% Unsupported global syntax config: '$fullLine'"
        }
    }

    private fun handleInterfaceConfigMode(
        device: Device,
        cmd: String,
        tokens: List<String>,
        fullLine: String
    ): Pair<Device, String> {
        val portId = device.currentInterfaceId ?: return device to "% No interface selected."
        val currentPort = device.ports.find { it.id == portId } ?: return device to "% Port not found."

        return when {
            cmd == "ip" && tokens.size > 2 && tokens[1] == "address" -> {
                // ip address x.x.x.x y.y.y.y
                val ip = tokens[2]
                val mask = tokens[3]
                val updatedPorts = device.ports.map {
                    if (it.id == portId) it.copy(ipAddress = ip, subnetMask = mask, isUp = true) else it
                }
                val newConfig = device.runningConfig + "\ninterface ${currentPort.name}\n ip address $ip $mask"
                
                // Add an automatic Local Connected route
                val netIp = computeNetworkAddress(ip, mask)
                val connectedRoute = Route(destinationNetwork = "$netIp/$mask", nextHop = "0.0.0.0", cost = 0, protocol = "Connected")
                val updatedRoutes = device.routingTable.toMutableList()
                updatedRoutes.removeAll { it.protocol == "Connected" && it.destinationNetwork.startsWith(netIp) }
                updatedRoutes.add(connectedRoute)

                device.copy(ports = updatedPorts, runningConfig = newConfig, routingTable = updatedRoutes) to ""
            }
            cmd == "shutdown" || cmd == "shut" -> {
                val updatedPorts = device.ports.map {
                    if (it.id == portId) it.copy(isUp = false) else it
                }
                val newConfig = device.runningConfig + "\ninterface ${currentPort.name}\n shutdown"
                device.copy(ports = updatedPorts, runningConfig = newConfig) to "% Interface ${currentPort.name} state changed to administratively down"
            }
            cmd == "no" && tokens.size > 1 && (tokens[1] == "shutdown" || tokens[1] == "shut") -> {
                val updatedPorts = device.ports.map {
                    if (it.id == portId) it.copy(isUp = true) else it
                }
                val newConfig = device.runningConfig + "\ninterface ${currentPort.name}\n no shutdown"
                device.copy(ports = updatedPorts, runningConfig = newConfig) to "% Link status on logical interface ${currentPort.name} set to UP"
            }
            cmd == "switchport" && tokens.size > 2 && tokens[1] == "mode" -> {
                val modeStr = tokens[2].uppercase()
                val targetMode = if (modeStr == "TRUNK") PortMode.TRUNK else PortMode.ACCESS
                val updatedPorts = device.ports.map {
                    if (it.id == portId) it.copy(portMode = targetMode) else it
                }
                val newConfig = device.runningConfig + "\ninterface ${currentPort.name}\n switchport mode ${modeStr.lowercase()}"
                device.copy(ports = updatedPorts, runningConfig = newConfig) to ""
            }
            cmd == "switchport" && tokens.size > 3 && tokens[1] == "access" && tokens[2] == "vlan" -> {
                val vlanVal = tokens[3].toIntOrNull() ?: 1
                val updatedPorts = device.ports.map {
                    if (it.id == portId) it.copy(vlanId = vlanVal) else it
                }
                val newConfig = device.runningConfig + "\ninterface ${currentPort.name}\n switchport access vlan $vlanVal"
                device.copy(ports = updatedPorts, runningConfig = newConfig) to ""
            }
            else -> device to "% Interface command unrecognized. Supports shut, no shut, ip address, switchport"
        }
    }

    private fun handleVlanConfigMode(
        device: Device,
        cmd: String,
        tokens: List<String>,
        fullLine: String
    ): Pair<Device, String> {
        val vlanId = device.currentVlanId ?: return device to "% No Active VLAN context found."
        return when {
            cmd == "name" && tokens.size > 1 -> {
                val vName = tokens[1]
                val currentVlans = device.vlanDatabase.toMutableMap()
                currentVlans[vlanId] = vName
                val newConfig = device.runningConfig + "\nvlan $vlanId\n name $vName"
                device.copy(vlanDatabase = currentVlans, runningConfig = newConfig) to ""
            }
            else -> device to "% VLAN database commands: name"
        }
    }

    private fun handleRouterConfigMode(
        device: Device,
        cmd: String,
        tokens: List<String>,
        fullLine: String
    ): Pair<Device, String> {
        return when {
            cmd == "network" && tokens.size > 1 -> {
                // simple configuration entry
                val newConfig = device.runningConfig + "\n network ${tokens.subList(1, tokens.size).joinToString(" ")}"
                device.copy(runningConfig = newConfig) to "Routing engine matches configuration. Local routes advertised."
            }
            else -> device to "% Router configuration commands: network"
        }
    }

    private fun triggerPingSimulation(
        sourceDevice: Device,
        destIp: String,
        allDevices: List<Device>,
        onSimulationTrigger: (PacketInfo) -> Unit
    ) {
        // Find matching target device
        val targetDevice = allDevices.find { d ->
            d.ports.any { p -> p.ipAddress == destIp }
        }

        val destinationId = targetDevice?.id ?: "unknown"
        val startPoint = OffsetPoint(sourceDevice.x, sourceDevice.y)
        val endPoint = targetDevice?.let { OffsetPoint(it.x, it.y) } ?: OffsetPoint(sourceDevice.x + 300f, sourceDevice.y + 100f)

        // Generate ARP then ICMP packet definitions
        val headers = mapOf(
            "Frame Type" to "Ethernet II (0x0800)",
            "L2 DMAC" to (targetDevice?.ports?.firstOrNull()?.macAddress ?: "ff:ff:ff:ff:ff:ff"),
            "L2 SMAC" to (sourceDevice.ports.firstOrNull()?.macAddress ?: "aa:bb:cc:00:11:22"),
            "L3 SIP" to (sourceDevice.ports.firstOrNull()?.ipAddress ?: "0.0.0.0"),
            "L3 DIP" to destIp,
            "L4 Protocol" to "ICMP (Type 8 Echo Request)",
            "Simulation" to "Packet Simulator sandbox"
        )

        // Broadcast or routing hops
        val points = mutableListOf<OffsetPoint>().apply {
            add(startPoint)
            // Add a mid point if links connect them. In this basic sandbox we draw a path straight or via connected switch if any
            val midMatch = allDevices.find { it.type == DeviceType.SWITCH }
            if (midMatch != null && midMatch.id != sourceDevice.id && midMatch.id != targetDevice?.id) {
                add(OffsetPoint(midMatch.x, midMatch.y))
            }
            add(endPoint)
        }

        val packet = PacketInfo(
            id = UUID.randomUUID().toString(),
            type = if (targetDevice == null) PacketType.ARP else PacketType.ICMP,
            sourceDeviceId = sourceDevice.id,
            destDeviceId = destinationId,
            summary = "Echo Request to $destIp",
            path = points,
            headers = headers,
            progress = 0f,
            isDropped = targetDevice == null,
            dropReason = if (targetDevice == null) "Destination host unreachable (subnet gateway issue or no device matches)" else null
        )
        onSimulationTrigger(packet)
    }

    // Diagnostics / Output builders
    private fun buildRunningConfigOutput(device: Device): String {
        val sb = StringBuilder("Current configuration :\n!\nversion 15.0\n")
        sb.append("hostname ${device.name}\n!\n")
        device.vlanDatabase.filter { it.key != 1 }.forEach { (id, name) ->
            sb.append("vlan $id\n name $name\n!\n")
        }
        device.ports.forEach { port ->
            sb.append("interface ${port.name}\n")
            if (port.ipAddress != null) {
                sb.append(" ip address ${port.ipAddress} ${port.subnetMask}\n")
            } else if (port.portMode == PortMode.TRUNK) {
                sb.append(" switchport mode trunk\n")
            } else if (port.vlanId != 1) {
                sb.append(" switchport access vlan ${port.vlanId}\n")
            }
            if (!port.isUp) sb.append(" shutdown\n")
            sb.append("!\n")
        }
        sb.append("end\n")
        return sb.toString()
    }

    private fun buildRoutingTableOutput(device: Device): String {
        val sb = StringBuilder("Codes: C - connected, S - static, R - RIP, O - OSPF\n\nGateway of last resort is not set\n\n")
        device.routingTable.forEach { r ->
            val code = when (r.protocol) {
                "Connected" -> "C"
                "Static" -> "S"
                "OSPF" -> "O"
                "RIP" -> "R"
                else -> "S"
            }
            sb.append("%s    %s [1/%d] via %s\n".format(code, r.destinationNetwork, r.cost, if (r.nextHop == "0.0.0.0") "directly connected" else r.nextHop))
        }
        if (device.routingTable.isEmpty()) {
            sb.append("Routing table is empty!\nConfigure IP addresses or add custom 'ip route' networks.")
        }
        return sb.toString()
    }

    private fun buildIpInterfacesOutput(device: Device): String {
        val sb = StringBuilder("%-24s %-16s %-8s %-12s\n".format("Interface", "IP-Address", "OK?", "Status"))
        device.ports.forEach { p ->
            sb.append("%-24s %-16s %-8s %-12s\n".format(p.name, p.ipAddress ?: "unassigned", "YES", if (p.isUp) "up" else "administratively down"))
        }
        return sb.toString()
    }

    private fun buildVlanBriefOutput(device: Device): String {
        val sb = StringBuilder("%-4s %-20s %-8s\n".format("VLAN", "Name", "Status"))
        device.vlanDatabase.forEach { (id, name) ->
            sb.append("%-4d %-20s %-8s\n".format(id, name, "active"))
        }
        return sb.toString()
    }

    private fun computeNetworkAddress(ip: String, mask: String): String {
        return try {
            val ipOctets = ip.split(".").map { it.toInt() }
            val maskOctets = mask.split(".").map { it.toInt() }
            val netOctets = ipOctets.zip(maskOctets).map { (i, m) -> i and m }
            netOctets.joinToString(".")
        } catch (e: Exception) {
            "10.0.0.0"
        }
    }
}
