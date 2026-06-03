package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.theme.*
import kotlin.math.sqrt

@Composable
fun TopologyCanvas(
    devices: List<Device>,
    links: List<Link>,
    packets: List<PacketInfo>,
    selectedDeviceIds: Set<String>,
    scale: Float,
    panX: Float,
    panY: Float,
    lockMode: Boolean,
    onDeviceSelected: (Device) -> Unit,
    onDeviceMoved: (String, Float, Float) -> Unit,
    onCanvasTransformed: (Float, Float, Float) -> Unit,
    onLinkLongClick: (Link) -> Unit,
    onBackgroundTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    var areaSize by remember { mutableStateOf(IntSize.Zero) }

    // Gesture detector that processes panning and pinch zooming
    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { areaSize = it }
            .background(PremiumBlack)
            .pointerInput(lockMode) {
                if (lockMode) return@pointerInput
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(0.2f, 4.0f)
                    val newPanX = panX + pan.x
                    val newPanY = panY + pan.y
                    onCanvasTransformed(newScale, newPanX, newPanY)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onBackgroundTap() })
            }
    ) {
        // 1. Dotted Blueprint Blueprint Grid & Cable Links Custom Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = panX,
                    translationY = panY
                )
        ) {
            val width = 3000f
            val height = 3000f
            val gridSpacing = 80f

            // Draw background grid lines/dots
            for (x in 0..(width / gridSpacing).toInt()) {
                for (y in 0..(height / gridSpacing).toInt()) {
                    drawCircle(
                        color = NetCyan.copy(alpha = 0.08f),
                        radius = 2f,
                        center = Offset(x * gridSpacing, y * gridSpacing)
                    )
                }
            }

            // Draw Cabling Lines between device centers
            links.forEach { link ->
                val devA = devices.find { it.id == link.deviceIdA }
                val devB = devices.find { it.id == link.deviceIdB }
                if (devA != null && devB != null) {
                    val pA = Offset(devA.x + 48f, devB.y + 48f) // Approx node center offset
                    val startX = devA.x + 70f
                    val startY = devA.y + 70f
                    val endX = devB.x + 70f
                    val endY = devB.y + 70f

                    // Draw connection path line (different colors per CableType)
                    val lineColor = when (link.cableType) {
                        CableType.COPPER_CROSSOVER -> WarningAmber
                        CableType.FIBER -> NetCyan
                        CableType.SERIAL -> PortRed
                        CableType.CONSOLE -> Color.LightGray
                        else -> Color.White.copy(alpha = 0.4f) // Copper Straight
                    }

                    drawLine(
                        color = lineColor,
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = 3f * (1 / scale).coerceIn(0.5f, 2f)
                    )

                    // Draw Port LED statuses along the lines: up (green) vs down (red)
                    val midPointX = (startX + endX) / 2
                    val midPointY = (startY + endY) / 2

                    // Green LED dots
                    drawRingLed(
                        color = if (devA.ports.any { it.connectedLinkId == link.id && it.isUp }) PortGreen else PortRed,
                        center = Offset(startX + (endX - startX) * 0.2f, startY + (endY - startY) * 0.2f)
                    )

                    drawRingLed(
                        color = if (devB.ports.any { it.connectedLinkId == link.id && it.isUp }) PortGreen else PortRed,
                        center = Offset(startX + (endX - startX) * 0.8f, startY + (endY - startY) * 0.8f)
                    )
                }
            }

            // Draw traveling Packet Animations
            packets.forEach { pkt ->
                if (pkt.path.size >= 2) {
                    // Compute exact interpolated dynamic coordinate point depending on progress
                    val totalHops = pkt.path.size - 1
                    val segmentFloat = pkt.progress * totalHops
                    val segmentIdx = segmentFloat.toInt().coerceIn(0, totalHops - 1)
                    val segProgress = segmentFloat - segmentIdx

                    val pStart = pkt.path[segmentIdx]
                    val pEnd = pkt.path[segmentIdx + 1]

                    val curX = pStart.x + (pEnd.x - pStart.x) * segProgress
                    val curY = pStart.y + (pEnd.y - pStart.y) * segProgress

                    // Draw neon glowing pulse dot representing packet serialization
                    drawCircle(
                        color = Color(pkt.type.colorHex),
                        radius = 10f,
                        center = Offset(curX, curY)
                    )
                    drawCircle(
                        color = Color(pkt.type.colorHex).copy(alpha = 0.4f),
                        radius = 18f,
                        center = Offset(curX, curY)
                    )
                }
            }
        }

        // 2. Interactive Devices Group Renderings (HTML-like Absolute offsets)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = panX,
                    translationY = panY
                )
        ) {
            devices.forEach { device ->
                key(device.id) {
                    val isSelected = selectedDeviceIds.contains(device.id)

                    DeviceNode(
                        device = device,
                        isSelected = isSelected,
                        lockMode = lockMode,
                        onSelect = { onDeviceSelected(device) },
                        onDrag = { dx, dy ->
                            if (!lockMode) {
                                val currentX = device.x + dx
                                val currentY = device.y + dy
                                onDeviceMoved(device.id, currentX, currentY)
                            }
                        },
                        modifier = Modifier
                            .offset(device.x.dp, device.y.dp)
                            .testTag("device_node_${device.name}")
                    )
                }
            }
        }
    }
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRingLed(color: Color, center: Offset) {
    drawCircle(
        color = color,
        radius = 6f,
        center = center
    )
    drawCircle(
        color = color.copy(alpha = 0.3f),
        radius = 12f,
        center = center
    )
}

@Composable
fun DeviceNode(
    device: Device,
    isSelected: Boolean,
    lockMode: Boolean,
    onSelect: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = when (device.type) {
        DeviceType.ROUTER -> Icons.Default.Share
        DeviceType.SWITCH -> Icons.Default.List
        DeviceType.FIREWALL -> Icons.Default.Lock
        DeviceType.WIRELESS -> Icons.Default.Refresh
        DeviceType.END_DEVICE -> Icons.Default.Home
        DeviceType.INFRASTRUCTURE -> Icons.Default.Settings
    }

    val glowColor = if (isSelected) NetCyan else Color.White.copy(alpha = 0.15f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(132.dp)
            .pointerInput(lockMode) {
                detectDragGestures(
                    onDragStart = { onSelect() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onSelect() }
                )
            }
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .size(76.dp)
                .border(
                    if (isSelected) 2.dp else 1.dp,
                    glowColor,
                    RoundedCornerShape(12.dp)
                )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        icon,
                        contentDescription = device.name,
                        tint = if (isSelected) NetCyan else TextWhite,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = device.model,
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Text labels inside colored pill
        Box(
            modifier = Modifier
                .background(PremiumBlack.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = device.name,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                // Render configured IP label automatically to make scanning easy on mobile screens
                val activeIp = device.ports.firstOrNull { it.ipAddress != null }?.ipAddress
                if (activeIp != null) {
                    Text(
                        text = activeIp,
                        color = NetCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
