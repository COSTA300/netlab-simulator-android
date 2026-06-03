package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cli.CliEngine
import com.example.model.*
import com.example.ui.theme.*
import com.example.viewmodel.NetLabViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EquipmentDrawer(
    onAddDevice: (DeviceType, String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeCategory by remember { mutableStateOf(DeviceType.ROUTER) }

    val models = when (activeCategory) {
        DeviceType.ROUTER -> listOf("Cisco 2911", "Cisco 2901", "Cisco 4331", "ISR 4000")
        DeviceType.SWITCH -> listOf("Cisco 2960", "Cisco 2950", "Cisco 3560 L3", "Cisco 3750")
        DeviceType.FIREWALL -> listOf("Cisco ASA 5510", "ASA 5505", "Cisco FTD")
        DeviceType.WIRELESS -> listOf("Cisco WLC", "Lightweight AP", "Autonomous AP")
        DeviceType.END_DEVICE -> listOf("PC", "Laptop", "Web Server", "Printer", "Smartphone")
        DeviceType.INFRASTRUCTURE -> listOf("Hub", "Unmanaged Switch", "Cloud WAN")
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SlateDark,
        dragHandle = { BottomSheetDefaults.DragHandle(color = NetCyan) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
        ) {
            Text(
                text = "Cisco Equipment Library",
                color = NetCyan,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Category selector tabs (scrollable)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                items(DeviceType.values()) { type ->
                    val isSelected = activeCategory == type
                    FilterChip(
                        selected = isSelected,
                        onClick = { activeCategory = type },
                        label = { Text(type.label, color = if (isSelected) PremiumBlack else TextWhite) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NetCyan,
                            containerColor = CardDark
                        )
                    )
                }
            }

            Divider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(bottom = 16.dp))

            // Models select row
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                models.forEach { model ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardDark),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.05f)),
                        onClick = {
                            onAddDevice(activeCategory, model)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth().testTag("add_eq_$model")
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                when (activeCategory) {
                                    DeviceType.ROUTER -> Icons.Default.Share
                                    DeviceType.SWITCH -> Icons.Default.List
                                    DeviceType.FIREWALL -> Icons.Default.Lock
                                    DeviceType.WIRELESS -> Icons.Default.Refresh
                                    DeviceType.END_DEVICE -> Icons.Default.Home
                                    else -> Icons.Default.Settings
                                },
                                contentDescription = model,
                                tint = NetCyan,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(text = model, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(text = "Cisco IOS emulation profile", color = TextMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PortCablingWizard(
    devices: List<Device>,
    onConnect: (deviceIdA: String, portIdA: String, deviceIdB: String, portIdB: String, CableType) -> Unit,
    onDismiss: () -> Unit
) {
    var devA by remember { mutableStateOf<Device?>(null) }
    var devB by remember { mutableStateOf<Device?>(null) }
    var portA by remember { mutableStateOf<Port?>(null) }
    var portB by remember { mutableStateOf<Port?>(null) }
    var cableType by remember { mutableStateOf<CableType?>(null) }

    // Helper to filter vacant ports
    val getVacantPorts = { d: Device? ->
        d?.ports?.filter { it.connectedLinkId == null } ?: emptyList()
    }

    // Auto-Suggest correct cable based on Page 6 specs
    LaunchedEffect(devA, devB) {
        if (devA != null && devB != null) {
            val typeA = devA!!.type
            val typeB = devB!!.type
            cableType = when {
                (typeA == DeviceType.SWITCH && typeB == DeviceType.ROUTER) ||
                (typeA == DeviceType.ROUTER && typeB == DeviceType.SWITCH) ||
                (typeA == DeviceType.END_DEVICE && typeB == DeviceType.SWITCH) ||
                (typeA == DeviceType.SWITCH && typeB == DeviceType.END_DEVICE) -> CableType.COPPER_STRAIGHT

                (typeA == DeviceType.SWITCH && typeB == DeviceType.SWITCH) ||
                (typeA == DeviceType.ROUTER && typeB == DeviceType.ROUTER) ||
                (typeA == DeviceType.END_DEVICE && typeB == DeviceType.ROUTER) ||
                (typeA == DeviceType.ROUTER && typeB == DeviceType.END_DEVICE) -> CableType.COPPER_CROSSOVER

                (typeA == DeviceType.INFRASTRUCTURE || typeB == DeviceType.INFRASTRUCTURE) -> CableType.FIBER
                else -> CableType.COPPER_STRAIGHT
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SlateDark,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = NetCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cable Link Configurator", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Step 1: Select Device A
                Column {
                    Text("Select Device A", color = NetCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Box(modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(8.dp)).background(CardDark).clickable { }) {
                        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(text = devA?.name ?: "Tap to choose first endpoint", color = if (devA != null) Color.White else TextMuted, fontSize = 13.sp)
                        }
                    }
                    LazyRow(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(devices) { d ->
                            if (d != devB) {
                                Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(if (devA == d) NetCyan else CardDark).clickable { devA = d; portA = null }.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                    Text(text = d.name, color = if (devA == d) PremiumBlack else Color.White, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                // Step 2: Select Vacant Port A
                if (devA != null) {
                    Column {
                        Text("Select ${devA!!.name}'s Port", color = NetCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        val vacant = getVacantPorts(devA)
                        if (vacant.isEmpty()) {
                            Text("No interfaces vacant on ${devA!!.name}!", color = Color.Red, fontSize = 12.sp)
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(vacant) { p ->
                                    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(if (portA == p) NetCyan else CardDark).clickable { portA = p }.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                        Text(text = p.name, color = if (portA == p) PremiumBlack else Color.White, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // Step 3: Select Device B
                if (devA != null && portA != null) {
                    Column {
                        Text("Select Device B", color = NetCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Box(modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(8.dp)).background(CardDark)) {
                            Row(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(text = devB?.name ?: "Choose response partner", color = if (devB != null) Color.White else TextMuted, fontSize = 13.sp)
                            }
                        }
                        LazyRow(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(devices) { d ->
                                if (d != devA) {
                                    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(if (devB == d) NetCyan else CardDark).clickable { devB = d; portB = null }.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                        Text(text = d.name, color = if (devB == d) PremiumBlack else Color.White, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // Step 4: Select Vacant Port B
                if (devB != null) {
                    Column {
                        Text("Select ${devB!!.name}'s Port", color = NetCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        val vacant = getVacantPorts(devB)
                        if (vacant.isEmpty()) {
                            Text("No interfaces vacant on ${devB!!.name}!", color = Color.Red, fontSize = 12.sp)
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(vacant) { p ->
                                    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(if (portB == p) NetCyan else CardDark).clickable { portB = p }.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                        Text(text = p.name, color = if (portB == p) PremiumBlack else Color.White, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // Auto-Suggest Cable Indicator
                if (cableType != null && devA != null && devB != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(NetCyan.copy(alpha = 0.05f))
                            .border(0.5.dp, NetCyan.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text("Recommended Cable Link:", color = NetCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(cableType!!.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("App automatically configures standards.", color = TextMuted, fontSize = 9.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (devA != null && devB != null && portA != null && portB != null && cableType != null) {
                        onConnect(devA!!.id, portA!!.id, devB!!.id, portB!!.id, cableType!!)
                    }
                },
                enabled = devA != null && devB != null && portA != null && portB != null && cableType != null,
                colors = ButtonDefaults.buttonColors(containerColor = NetCyan)
            ) {
                Text("Align Cable", color = PremiumBlack, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White)
            }
        }
    )
}

@Composable
fun CliTerminalSheet(
    viewModel: NetLabViewModel,
    deviceId: String,
    modifier: Modifier = Modifier
) {
    val outputs by viewModel.terminalOutputs.collectAsState()
    val textState = outputs[deviceId] ?: ""
    val keyboardController = LocalSoftwareKeyboardController.current

    var currentInput by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    // Auto-scroll CLI to bottom whenever terminal output text changes
    LaunchedEffect(textState) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = PremiumBlack),
        border = BorderStroke(1.dp, NetCyan.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(PortGreen))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "IOS Console - Active System Session Node",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Row {
                    TextButton(onClick = { viewModel.clearActiveTerminalOutput() }) {
                        Text("Clear Logs", color = NetCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // CLI Log Text Feed Block
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(PremiumBlack)
                    .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                    .verticalScroll(scrollState)
                    .padding(8.dp)
            ) {
                Text(
                    text = textState,
                    color = NetCyan,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.testTag("cli_logs_area")
                )
            }

            // Custom On-Screen Keyboard Bar (Page 7 & 9 Cisco Quick Commands Keybars)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        Button(
                            onClick = {
                                val comps = CliEngine.getCompletions(currentInput, CliMode.GLOBAL_CONFIG)
                                if (comps.isNotEmpty()) {
                                    currentInput = comps.first()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CardDark),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Tab Autocomplete", color = NetCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    item {
                        Button(
                            onClick = {
                                val help = CliEngine.getHelpText(currentInput, CliMode.GLOBAL_CONFIG)
                                viewModel.executeTerminalCommandString("?")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CardDark),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("?", color = NetCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    item {
                        Button(
                            onClick = { viewModel.executeTerminalCommandString("exit") },
                            colors = ButtonDefaults.buttonColors(containerColor = CardDark),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Ctrl+Z", color = WarningAmber, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    item {
                        Button(
                            onClick = { viewModel.executeTerminalCommandString("en") },
                            colors = ButtonDefaults.buttonColors(containerColor = CardDark),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("enable", color = Color.White, fontSize = 10.sp)
                        }
                    }

                    item {
                        Button(
                            onClick = { viewModel.executeTerminalCommandString("conf t") },
                            colors = ButtonDefaults.buttonColors(containerColor = CardDark),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("conf t", color = Color.White, fontSize = 10.sp)
                        }
                    }

                    item {
                        Button(
                            onClick = { viewModel.executeTerminalCommandString("sh ip route") },
                            colors = ButtonDefaults.buttonColors(containerColor = CardDark),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("show ip route", color = Color.White, fontSize = 10.sp)
                        }
                    }
                }
            }

            // Command input field
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = currentInput,
                    onValueChange = { currentInput = it },
                    placeholder = { Text("Type IOS command here...", color = TextMuted, fontSize = 12.sp) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = CardDark,
                        unfocusedContainerColor = CardDark,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedIndicatorColor = NetCyan,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (currentInput.isNotBlank()) {
                                viewModel.executeTerminalCommandString(currentInput)
                                currentInput = ""
                            }
                            keyboardController?.hide()
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("cli_text_field")
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (currentInput.isNotBlank()) {
                            viewModel.executeTerminalCommandString(currentInput)
                            currentInput = ""
                        }
                        keyboardController?.hide()
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(NetCyan)
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = PremiumBlack)
                }
            }
        }
    }
}

@Composable
fun PduInspectorDialog(
    packet: PacketInfo,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SlateDark,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(packet.type.colorHex)))
                Spacer(modifier = Modifier.width(8.dp))
                Text("PDU Inspector (Visualizer Frame)", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Packet Type: ${packet.type.name} Packet Profile",
                    color = NetCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Text(
                    text = "Summary: ${packet.summary}",
                    color = Color.White,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Divider(color = Color.White.copy(alpha = 0.05f))

                // Encapsulated header rows
                Text("OSI Layer Encapsulation:", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                
                packet.headers.forEach { (layer, data) ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(CardDark)
                            .padding(8.dp)
                    ) {
                        Text(text = layer, color = NetCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(text = data, color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = NetCyan, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun LabVerificationBanner(
    title: String,
    description: String,
    onVerify: () -> Unit,
    verificationResult: Pair<Boolean, String>?
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = BorderStroke(1.dp, NetCyan.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CCNA GUIDED CHALLENGE PROGRESS",
                    color = NetCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                
                Button(
                    onClick = onVerify,
                    colors = ButtonDefaults.buttonColors(containerColor = NetCyan),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp).testTag("verify_lab_btn")
                ) {
                    Text("Verify Lab", color = PremiumBlack, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = description, color = TextMuted, fontSize = 11.sp, lineHeight = 15.sp)

            // Show result banner
            if (verificationResult != null) {
                val isSuccess = verificationResult.first
                val text = verificationResult.second

                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSuccess) PortGreen.copy(alpha = 0.15f) else PortRed.copy(alpha = 0.15f))
                        .border(
                            1.dp,
                            if (isSuccess) PortGreen else PortRed,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        text = if (isSuccess) "LAB COMPLETED!" else "CHECK FEEDBACK:",
                        color = if (isSuccess) PortGreen else PortRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = text,
                        color = Color.White,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}
