package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.*
import com.example.ui.theme.*
import com.example.viewmodel.NetLabViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: NetLabViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppContent(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(viewModel: NetLabViewModel) {
    val currentTab by viewModel.currentTab.collectAsState()
    val savedProjects by viewModel.savedProjects.collectAsState()

    // Editor live states
    val devices by viewModel.devices.collectAsState()
    val links by viewModel.links.collectAsState()
    val packets by viewModel.activePackets.collectAsState()
    val activeTerminalId by viewModel.activeTerminalDeviceId.collectAsState()
    val eventLogs by viewModel.eventLogs.collectAsState()
    
    val scale by viewModel.scale.collectAsState()
    val panX by viewModel.panX.collectAsState()
    val panY by viewModel.panY.collectAsState()
    val lockMode by viewModel.lockMode.collectAsState()
    val snapToGrid by viewModel.snapToGrid.collectAsState()
    val simSpeed by viewModel.simulationSpeed.collectAsState()

    val activeLabTitle by viewModel.activeLabTitle.collectAsState()
    val activeLabDesc by viewModel.activeLabDesc.collectAsState()
    val selectedLabId by viewModel.selectedLabId.collectAsState()

    // Local UI Sheet overlays states
    var showEquipSheet by remember { mutableStateOf(false) }
    var showCablingWizard by remember { mutableStateOf(false) }
    var selectedDevice by remember { mutableStateOf<Device?>(null) }
    var selectedPacketForPdu by remember { mutableStateOf<PacketInfo?>(null) }
    
    var showSaveDialog by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("") }
    var saveDesc by remember { mutableStateOf("") }

    var verificationResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    // Clear verification output when lab template is updated/loaded
    LaunchedEffect(selectedLabId) {
        verificationResult = null
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        containerColor = PremiumBlack
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (currentTab == "dashboard") {
                // Main Dashboard
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PremiumBlack)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "NetLab Pro", tint = NetCyan, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("NetLab Pro Console", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    
                    DashboardScreen(
                        viewModel = viewModel,
                        savedProjects = savedProjects,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                // Main Interactive Simulation Editor Canvas Panel
                Column(modifier = Modifier.fillMaxSize()) {
                    // Control Top panel Toolbar (Custom responsive diagnostic controls)
                    EditorControlBar(
                        activeTitle = activeLabTitle,
                        lockMode = lockMode,
                        snapToGrid = snapToGrid,
                        simSpeed = simSpeed,
                        onBack = { viewModel.setTab("dashboard") },
                        onUndo = { viewModel.undo() },
                        onRedo = { viewModel.redo() },
                        onToggleLock = { viewModel.toggleLockMode() },
                        onToggleSnap = { viewModel.toggleSnapToGrid() },
                        onChangeSpeed = { viewModel.setSimulationSpeed(it) },
                        onClear = { viewModel.clearTopology() },
                        onSave = {
                            saveName = if (selectedLabId != null) activeLabTitle else "Custom Topology Layout"
                            saveDesc = if (selectedLabId != null) "Verified Config Design" else "Sandbox saved schematic design"
                            showSaveDialog = true
                        }
                    )

                    // Body
                    Box(modifier = Modifier.weight(1f)) {
                        // 1. The custom grid blueprint canvas
                        TopologyCanvas(
                            devices = devices,
                            links = links,
                            packets = packets,
                            selectedDeviceIds = if (selectedDevice != null) setOf(selectedDevice!!.id) else emptySet(),
                            scale = scale,
                            panX = panX,
                            panY = panY,
                            lockMode = lockMode,
                            onDeviceSelected = { dev ->
                                selectedDevice = dev
                                viewModel.addLog("Tapped ${dev.name}")
                            },
                            onDeviceMoved = { id, x, y ->
                                viewModel.updateDevicePosition(id, x, y)
                            },
                            onCanvasTransformed = { s, px, py ->
                                viewModel.setCanvasTransforms(s, px, py)
                            },
                            onLinkLongClick = { link ->
                                viewModel.removeLink(link.id)
                            },
                            onBackgroundTap = {
                                selectedDevice = null
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // 2. Guided Objectives Overlay Banner (Page 8)
                        if (selectedLabId != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                                    .align(Alignment.TopCenter)
                            ) {
                                LabVerificationBanner(
                                    title = activeLabTitle,
                                    description = activeLabDesc,
                                    onVerify = {
                                        verificationResult = viewModel.runVerificationEngine()
                                    },
                                    verificationResult = verificationResult
                                )
                            }
                        }

                        // 3. Float Toolbar: Context Actions of target selected device
                        androidx.compose.animation.AnimatedVisibility(
                            visible = selectedDevice != null,
                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = if (activeTerminalId != null) 300.dp else 90.dp, start = 16.dp, end = 16.dp)
                        ) {
                            selectedDevice?.let { dev ->
                                DeviceActionPill(
                                    device = dev,
                                    links = links,
                                    onOpenConsole = {
                                        viewModel.openTerminalForDevice(dev.id)
                                    },
                                    onPortCabling = {
                                        showCablingWizard = true
                                    },
                                    onDelete = {
                                        viewModel.removeDevice(dev.id)
                                        selectedDevice = null
                                    },
                                    onDismiss = { selectedDevice = null }
                                )
                            }
                        }

                        // 4. Floating Action Controls for placing components and aligning ports
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Zoom reset
                            FloatingActionButton(
                                onClick = { viewModel.resetZoom() },
                                containerColor = CardDark,
                                contentColor = Color.White,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reset Zoom")
                            }

                            // Port cabling linkage wizard FAB
                            FloatingActionButton(
                                onClick = { showCablingWizard = true },
                                containerColor = WarningAmber,
                                contentColor = PremiumBlack,
                                modifier = Modifier.testTag("cabling_wizard_fab")
                            ) {
                                Icon(Icons.Default.Build, contentDescription = "Cabling Linkage Panel")
                            }

                            // Equipment library adder FAB (+)
                            FloatingActionButton(
                                onClick = { showEquipSheet = true },
                                containerColor = NetCyan,
                                contentColor = PremiumBlack,
                                modifier = Modifier.testTag("equipment_library_fab")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add Cisco Device")
                            }
                        }

                        // 5. Living active packet tapping for visual PDU inspection (Taps first active packets for demo)
                        if (packets.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = if (selectedLabId != null) 140.dp else 16.dp, end = 16.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    packets.forEach { pkt ->
                                        Card(
                                            onClick = { selectedPacketForPdu = pkt },
                                            colors = CardDefaults.cardColors(containerColor = CardDark),
                                            border = BorderStroke(1.dp, Color(pkt.type.colorHex)),
                                            modifier = Modifier.widthIn(max = 200.dp)
                                        ) {
                                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(pkt.type.colorHex)))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(text = "Inspect ${pkt.type.name} Frame", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom CLI Console (survives backdrop, collapsible swipe sheet)
                    AnimatedVisibility(
                        visible = activeTerminalId != null,
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it })
                    ) {
                        activeTerminalId?.let { termId ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(310.dp)
                            ) {
                                CliTerminalSheet(
                                    viewModel = viewModel,
                                    deviceId = termId
                                )

                                // Close floating badge pin
                                IconButton(
                                    onClick = { viewModel.changeActiveTerminalDevice("") },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .offset(y = (-14).dp)
                                        .size(24.dp)
                                        .background(Color.Red, CircleShape)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Equipment Bottom BottomSheet
        if (showEquipSheet) {
            EquipmentDrawer(
                onAddDevice = { type, model ->
                    viewModel.addDevice(type, model, 150f, 250f)
                },
                onDismiss = { showEquipSheet = false }
            )
        }

        // Port connection wizard
        if (showCablingWizard) {
            PortCablingWizard(
                devices = devices,
                onConnect = { idA, portA, idB, portB, cable ->
                    viewModel.addLink(idA, portA, idB, portB, cable)
                    showCablingWizard = false
                },
                onDismiss = { showCablingWizard = false }
            )
        }

        // Project serialize Save Dialog
        if (showSaveDialog) {
            AlertDialog(
                onDismissRequest = { showSaveDialog = false },
                containerColor = SlateDark,
                title = { Text("Save Lab Topology Project", color = NetCyan, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextField(
                            value = saveName,
                            onValueChange = { saveName = it },
                            label = { Text("Project Name", color = NetCyan) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = CardDark,
                                unfocusedContainerColor = CardDark,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        TextField(
                            value = saveDesc,
                            onValueChange = { saveDesc = it },
                            label = { Text("Short Description", color = NetCyan) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = CardDark,
                                unfocusedContainerColor = CardDark,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (saveName.isNotBlank()) {
                                viewModel.persistCurrentProject(saveName, saveDesc)
                                showSaveDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NetCyan)
                    ) {
                        Text("Save Config", color = PremiumBlack)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveDialog = false }) { Text("Cancel", color = Color.White) }
                }
            )
        }

        // PDU Inspector dialog overlay
        selectedPacketForPdu?.let { packet ->
            PduInspectorDialog(
                packet = packet,
                onDismiss = { selectedPacketForPdu = null }
            )
        }
    }
}

@Composable
fun EditorControlBar(
    activeTitle: String,
    lockMode: Boolean,
    snapToGrid: Boolean,
    simSpeed: String,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onToggleLock: () -> Unit,
    onToggleSnap: () -> Unit,
    onChangeSpeed: (String) -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit
) {
    var speedMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SlateDark)
            .border(0.5.dp, Color.White.copy(alpha = 0.05f))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("back_to_dashboard")) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Dashboard", tint = NetCyan)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(
                    text = if (activeTitle.length > 20) activeTitle.take(18) + "..." else activeTitle,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Cisco Network Lab Emulator",
                    color = TextMuted,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Toolbar quick actions
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onUndo) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Undo", tint = Color.White)
            }
            IconButton(onClick = onRedo) {
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Redo", tint = Color.White)
            }
            
            // Snap to grid
            IconButton(onClick = onToggleSnap) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = "Snap Align",
                    tint = if (snapToGrid) NetCyan else TextMuted
                )
            }

            // Lock canvas
            IconButton(onClick = onToggleLock) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = if (lockMode) "Canvas locked" else "Canvas unlocked",
                    tint = if (lockMode) WarningAmber else Color.White.copy(alpha = 0.5f)
                )
            }

            // Sim Speed Selector
            Box {
                Button(
                    onClick = { speedMenuExpanded = true },
                    colors = ButtonDefaults.buttonColors(containerColor = CardDark),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(text = "Speed: $simSpeed", color = NetCyan, fontSize = 9.sp)
                }
                DropdownMenu(
                    expanded = speedMenuExpanded,
                    onDismissRequest = { speedMenuExpanded = false },
                    modifier = Modifier.background(CardDark)
                ) {
                    listOf("Slow", "Normal", "Fast", "Instant").forEach { speed ->
                        DropdownMenuItem(
                            text = { Text(speed, color = Color.White, fontSize = 12.sp) },
                            onClick = {
                                onChangeSpeed(speed)
                                speedMenuExpanded = false
                            }
                        )
                    }
                }
            }

            IconButton(onClick = onSave) {
                Icon(Icons.Default.Done, contentDescription = "Save Progress", tint = NetCyan)
            }

            IconButton(onClick = onClear) {
                Icon(Icons.Default.Delete, contentDescription = "Reset Canvas", tint = Color.Red.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
fun DeviceActionPill(
    device: Device,
    links: List<Link>,
    onOpenConsole: () -> Unit,
    onPortCabling: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SlateDark),
        border = BorderStroke(1.dp, NetCyan),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().testTag("device_context_pill")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Modify: ${device.name}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(text = "hardware specs: ${device.model}", color = TextMuted, fontSize = 9.sp)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close context bar", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Pin command terminal
                Button(
                    onClick = onOpenConsole,
                    colors = ButtonDefaults.buttonColors(containerColor = NetCyan),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = PremiumBlack, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("IOS CLI", color = PremiumBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // Add physical connection link/port mapper
                Button(
                    onClick = onPortCabling,
                    colors = ButtonDefaults.buttonColors(containerColor = CardDark),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Icon(Icons.Default.Build, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cable Port", color = Color.White, fontSize = 11.sp)
                }

                // Destroy device
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, Color.Red),
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete", color = Color.Red, fontSize = 11.sp)
                }
            }
        }
    }
}
