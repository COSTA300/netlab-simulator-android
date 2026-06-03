package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.database.SavedProject
import com.example.ui.theme.*
import com.example.viewmodel.NetLabViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(
    viewModel: NetLabViewModel,
    savedProjects: List<SavedProject>,
    modifier: Modifier = Modifier
) {
    var showStackDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(PremiumBlack)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Welcome Header Banner (Page 1 Vibe)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(SlateDark)
                    .border(1.dp, NetCyan.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .background(NetCyan.copy(alpha = 0.15f), RoundedCornerShape(50.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "APP CONCEPT PROMPT v1.0",
                            color = NetCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.testTag("app_version_badge")
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "NetLab Pro",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Text(
                        text = "Android Network Lab Simulator",
                        color = NetCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.SansSerif,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Text(
                        text = "Cisco Packet Tracer + EVE-NG — built for Android-only users",
                        color = TextMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Stats Display (Page 1 Stat Circles)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    StatMeter(number = "12", label = "Sections")
                    StatMeter(number = "50+", label = "Protocols")
                    StatMeter(number = "100+", label = "Exercises")
                    StatMeter(number = "0", label = "Root Req")
                }
            }
        }

        // Action Options Card
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "SANDBOX LABS",
                    color = NetCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Button(
                    onClick = { viewModel.loadLabTemplate("free") },
                    colors = ButtonDefaults.buttonColors(containerColor = NetCyan),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("free_lab_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Free Lab", tint = PremiumBlack)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Start Free Sandbox Topology", color = PremiumBlack, fontWeight = FontWeight.Bold)
                }
                
                OutlinedButton(
                    onClick = { showStackDialog = true },
                    border = BorderStroke(1.dp, NetCyan),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = "Specs", tint = NetCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "View Sandbox Specifications", color = NetCyan, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Guided Certification Labs List (Page 8 & 9)
        item {
            Text(
                text = "GUIDED CISCO CCNA LABS",
                color = NetCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LabRowItem(
                    title = "Lab 1: Welcome & Host Setup",
                    desc = "Learn local IP configuring & wiring. Wire Host PC1 to Cisco Gateway. Enter CLI commands.",
                    cert = "CCNA 200-301",
                    difficulty = "Introductory",
                    icon = Icons.Default.Settings,
                    onClick = { viewModel.loadLabTemplate("lab1") }
                )
                
                LabRowItem(
                    title = "Lab 2: CCNA Static Routing",
                    desc = "Configure multi-gateway hop routers. Route 'ip route 172.16.1.0' and active return static paths.",
                    cert = "CCNA / CCNP ENCOR",
                    difficulty = "Intermediate",
                    icon = Icons.Default.Share,
                    onClick = { viewModel.loadLabTemplate("lab2") }
                )

                LabRowItem(
                    title = "Lab 3: Switch VLANs & Trunking",
                    desc = "Configure VLAN databases and Access membership assignments on a Cisco Catalyst switch.",
                    cert = "CCNA 200-301",
                    difficulty = "Intermediate",
                    icon = Icons.Default.Build,
                    onClick = { viewModel.loadLabTemplate("lab3") }
                )
            }
        }

        // Saved Designs list
        item {
            Text(
                text = "SAVED TOPOLOGIES & EXPORTS",
                color = NetCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (savedProjects.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardDark)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "No Saved Labs",
                            tint = TextMuted,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No saved topologies yet.",
                            color = TextWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Designs you compile in Free Lab can be saved to local storage.",
                            color = TextMuted,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        } else {
            items(savedProjects) { project ->
                SavedProjectListItem(
                    project = project,
                    onLoad = { viewModel.loadSavedProject(project) },
                    onDelete = { viewModel.deleteSavedProject(project.id) }
                )
            }
        }
    }

    // Modal dialog to view sandbox capabilities specs (Page 4, 5, 10 visual specs)
    if (showStackDialog) {
        AlertDialog(
            onDismissRequest = { showStackDialog = false },
            containerColor = SlateDark,
            title = {
                Text(
                    text = "Supported Cisco Hardware Sandbox",
                    color = NetCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SpecSection(
                        title = "Cisco Routers Sandbox",
                        details = "Models: Cisco 1841, 1921, 2901, 2911, 4321, 4331\nConfigurable interfaces, static/dynamic routing, OSPF, RIP, ACLs."
                    )
                    SpecSection(
                        title = "Catalyst Switches",
                        details = "Models: Cisco 2950, 2960, 3560 L3, 3750\nVLAN creation, trunk routing (802.1Q), inter-VLAN, STP alignment."
                    )
                    SpecSection(
                        title = "End Devices & Servers",
                        details = "PC, Laptop, Web Servers, Smart Devices.\nActive client-side services: DHCP Client/Server, DNS queries, Ping, Traceroute."
                    )
                    SpecSection(
                        title = "Edge-to-Edge IOS Terminal",
                        details = "Direct console mirroring Cisco EXEC mode, Global config, Interface configurations with autocompletion list."
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showStackDialog = false }) {
                    Text("Close", color = NetCyan, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun StatMeter(number: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = number,
            color = NetCyan,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Serif
        )
        Text(
            text = label,
            color = TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun LabRowItem(
    title: String,
    desc: String,
    cert: String,
    difficulty: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        modifier = Modifier.fillMaxWidth().testTag("lab_item_${title.replace(" ", "_")}")
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(NetCyan.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = NetCyan, modifier = Modifier.size(24.dp))
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = cert,
                        style = MaterialTheme.typography.labelSmall,
                        color = NetCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = difficulty,
                        style = MaterialTheme.typography.labelSmall,
                        color = WarningAmber,
                        fontSize = 10.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = desc,
                    color = TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
fun SavedProjectListItem(
    project: SavedProject,
    onLoad: () -> Unit,
    onDelete: () -> Unit
) {
    val dateString = remember(project.dateModified) {
        val formatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        formatter.format(Date(project.dateModified))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        modifier = Modifier.fillMaxWidth().testTag("saved_project_${project.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                if (project.description.isNotEmpty()) {
                    Text(
                        text = project.description,
                        color = TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
                Text(
                    text = "Modified: $dateString",
                    color = TextMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 4.dp),
                    fontFamily = FontFamily.Monospace
                )
            }
            
            IconButton(onClick = onLoad) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Load Lab", tint = NetCyan)
            }
            
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
fun SpecSection(title: String, details: String) {
    Column {
        Text(text = title, color = NetCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(text = details, color = TextWhite.copy(alpha = 0.85f), fontSize = 12.sp, lineHeight = 16.sp)
        Spacer(modifier = Modifier.height(6.dp))
    }
}
