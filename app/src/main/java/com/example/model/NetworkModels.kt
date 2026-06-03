package com.example.model

enum class DeviceType(val label: String) {
    ROUTER("Router"),
    SWITCH("Switch"),
    FIREWALL("Firewall"),
    WIRELESS("Wireless"),
    END_DEVICE("End Device"),
    INFRASTRUCTURE("Infrastructure")
}

data class Port(
    val id: String,
    val name: String,
    val ipAddress: String? = null,
    val subnetMask: String? = null,
    val macAddress: String,
    val isUp: Boolean = false,
    val connectedLinkId: String? = null,
    val portMode: PortMode = PortMode.ACCESS,
    val vlanId: Int = 1
)

enum class PortMode {
    ACCESS, TRUNK
}

data class Device(
    val id: String,
    val name: String,
    val model: String,
    val type: DeviceType,
    val x: Float,
    val y: Float,
    val ports: List<Port>,
    val runningConfig: String = "",
    val startupConfig: String = "",
    val currentMode: CliMode = CliMode.USER_EXEC,
    val currentInterfaceId: String? = null, // Store active interface config route
    val currentVlanId: Int? = null,
    val vlanDatabase: Map<Int, String> = mapOf(1 to "default"),
    val routingTable: List<Route> = emptyList(),
    val dhcpLeases: List<DhcpLease> = emptyList(),
    val activeServices: Map<String, Boolean> = mapOf("DHCP" to false, "DNS" to false, "HTTP" to false, "NAT" to false),
    val aclRules: List<AclRule> = emptyList(),
    val natTranslations: List<NatTranslation> = emptyList(),
    val stpState: StpState = StpState.FORWARDING
)

enum class StpState {
    BLOCKING, LISTENING, LEARNING, FORWARDING, DISABLED
}

data class Route(
    val destinationNetwork: String, // e.g., 10.0.0.0/24
    val nextHop: String,          // e.g., 192.168.12.2
    val cost: Int = 1,
    val protocol: String = "Static" // "Static", "Connected", "OSPF", "RIP"
)

data class DhcpLease(
    val ipAddress: String,
    val macAddress: String,
    val leaseTimeMs: Long
)

data class AclRule(
    val id: String,
    val action: String, // "permit" / "deny"
    val protocol: String, // "ip", "icmp", "tcp", "udp"
    val sourceNetwork: String, // "any" or subnet
    val destNetwork: String // "any" or subnet
)

data class NatTranslation(
    val insideLocal: String,
    val insideGlobal: String,
    val outsideGlobal: String,
    val protocol: String = "tcp"
)

enum class CliMode(val promptSuffix: String) {
    USER_EXEC(">"),
    PRIVILEGED_EXEC("#"),
    GLOBAL_CONFIG("(config)#"),
    INTERFACE_CONFIG("(config-if)#"),
    VLAN_CONFIG("(config-vlan)#"),
    ROUTER_CONFIG("(config-router)#")
}

data class Link(
    val id: String,
    val deviceIdA: String,
    val portIdA: String,
    val deviceIdB: String,
    val portIdB: String,
    val bandwidthBps: Long = 100_000_000L,
    val delayMs: Long = 10L,
    val lossPct: Int = 0,
    val cableType: CableType = CableType.COPPER_STRAIGHT
)

enum class CableType(val label: String) {
    COPPER_STRAIGHT("Copper Straight-Through"),
    COPPER_CROSSOVER("Copper Crossover"),
    FIBER("Fiber (SM/MM)"),
    SERIAL("Serial DCE"),
    CONSOLE("Console"),
    USB("USB")
}

enum class PacketType(val colorHex: Long) {
    ICMP(0xFF00FFCC),   // Neon cyan
    ARP(0xFFFF66E6),    // Light magenta
    OSPF(0xFFFFFF33),   // Bright yellow
    DHCP(0xFF3399FF),   // Azure blue
    TCP(0xFFFF9933)     // Soft orange
}

data class PacketInfo(
    val id: String,
    val type: PacketType,
    val sourceDeviceId: String,
    val destDeviceId: String,
    val summary: String,
    val path: List<OffsetPoint>, // Coordinates of path transit points
    val headers: Map<String, String>, // L2, L3, L4 data for the PDU Inspector
    val progress: Float = 0f,
    val currentDeviceIndex: Int = 0,
    val isDropped: Boolean = false,
    val dropReason: String? = null
)

data class OffsetPoint(val x: Float, val y: Float)
