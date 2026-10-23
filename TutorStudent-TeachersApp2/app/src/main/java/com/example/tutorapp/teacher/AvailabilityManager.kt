package com.example.tutorapp.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tutorapp.ui.theme.*

/**
 * Represents a single availability slot for a day
 */
data class AvailabilitySlot(
    val dayOfWeek: Int,          // 0=Sunday, 1=Monday, ..., 6=Saturday
    val startTime: String,       // Format: "HH:MM" (24-hour)
    val endTime: String          // Format: "HH:MM" (24-hour)
)

/**
 * Day names for display
 */
val dayNames = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
val dayShortNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

/**
 * Time slots for selection (every 30 minutes from 6:00 AM to 10:00 PM)
 */
val timeSlots = listOf(
    "06:00", "06:30", "07:00", "07:30", "08:00", "08:30", "09:00", "09:30",
    "10:00", "10:30", "11:00", "11:30", "12:00", "12:30", "13:00", "13:30",
    "14:00", "14:30", "15:00", "15:30", "16:00", "16:30", "17:00", "17:30",
    "18:00", "18:30", "19:00", "19:30", "20:00", "20:30", "21:00", "21:30", "22:00"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvailabilityManager(
    currentAvailability: List<AvailabilitySlot>,
    onAvailabilityChanged: (List<AvailabilitySlot>) -> Unit
) {
    var selectedDay by remember { mutableStateOf<Int?>(null) }
    var showTimePicker by remember { mutableStateOf(false) }
    var tempStartTime by remember { mutableStateOf("09:00") }
    var tempEndTime by remember { mutableStateOf("10:00") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Weekly Availability",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = EduTextDark
                )
                Text(
                    "Set your available hours for each day",
                    fontSize = 11.sp,
                    color = EduGray
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Day selector buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                dayShortNames.forEachIndexed { index, dayName ->
                    val isSelected = selectedDay == index
                    val hasSlots = currentAvailability.any { it.dayOfWeek == index }
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    isSelected -> EduBlue
                                    hasSlots -> EduBlue.copy(alpha = 0.15f)
                                    else -> EduBorder
                                }
                            )
                            .clickable { selectedDay = if (selectedDay == index) null else index }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = dayName,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    isSelected -> Color.White
                                    hasSlots -> EduBlue
                                    else -> EduGray
                                }
                            )
                            if (hasSlots) {
                                Text(
                                    text = "●",
                                    fontSize = 8.sp,
                                    color = if (isSelected) Color.White else EduBlue
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Selected day slots
            selectedDay?.let { day ->
                Text(
                    "${dayNames[day]} Schedule",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = EduTextDark
                )

                Spacer(modifier = Modifier.height(8.dp))

                val daySlots = currentAvailability.filter { it.dayOfWeek == day }
                
                if (daySlots.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = EduBorder,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No availability set for this day",
                            fontSize = 12.sp,
                            color = EduGray
                        )
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        daySlots.forEach { slot ->
                            AvailabilitySlotCard(
                                slot = slot,
                                onRemove = {
                                    val updated = currentAvailability.toMutableList()
                                    updated.remove(slot)
                                    onAvailabilityChanged(updated.toList())
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Add time slot button
                Button(
                    onClick = {
                        tempStartTime = "09:00"
                        tempEndTime = "10:00"
                        showTimePicker = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EduBlue)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Time Slot")
                }
            }

            if (selectedDay == null) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Select a day above to set your availability",
                    fontSize = 12.sp,
                    color = EduGray,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }

    // Time picker dialog
    if (showTimePicker && selectedDay != null) {
        TimePickerDialog(
            selectedDay = selectedDay!!,
            startTime = tempStartTime,
            endTime = tempEndTime,
            onStartTimeChanged = { tempStartTime = it },
            onEndTimeChanged = { tempEndTime = it },
            onConfirm = {
                val newSlot = AvailabilitySlot(
                    dayOfWeek = selectedDay!!,
                    startTime = tempStartTime,
                    endTime = tempEndTime
                )
                val updated = currentAvailability.toMutableList()
                // Check for overlapping slots
                val hasOverlap = updated.any { existing ->
                    existing.dayOfWeek == newSlot.dayOfWeek &&
                    timesOverlap(existing.startTime, existing.endTime, newSlot.startTime, newSlot.endTime)
                }
                
                if (!hasOverlap) {
                    updated.add(newSlot)
                    onAvailabilityChanged(updated.toList())
                }
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }
}

@Composable
private fun AvailabilitySlotCard(
    slot: AvailabilitySlot,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(EduBorder.copy(alpha = 0.2f))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Schedule,
                contentDescription = null,
                tint = EduBlue,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "${slot.startTime} - ${slot.endTime}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = EduTextDark
                )
                Text(
                    text = "${calculateHours(slot.startTime, slot.endTime)} hours",
                    fontSize = 11.sp,
                    color = EduGray
                )
            }
        }
        
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove",
                tint = Color(0xFFE53935),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable

private fun TimePickerDialog(
    selectedDay: Int,
    startTime: String,
    endTime: String,
    onStartTimeChanged: (String) -> Unit,
    onEndTimeChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Set Availability for ${dayNames[selectedDay]}",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Start time
                Column {
                    Text("Start Time", fontSize = 12.sp, color = EduGray, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    var startExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = startExpanded,
                        onExpandedChange = { startExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = startTime,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = startExpanded) },
                            shape = RoundedCornerShape(8.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = startExpanded,
                            onDismissRequest = { startExpanded = false }
                        ) {
                            timeSlots.forEach { time ->
                                DropdownMenuItem(
                                    text = { Text(time) },
                                    onClick = {
                                        onStartTimeChanged(time)
                                        startExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // End time
                Column {
                    Text("End Time", fontSize = 12.sp, color = EduGray, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    var endExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = endExpanded,
                        onExpandedChange = { endExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = endTime,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = endExpanded) },
                            shape = RoundedCornerShape(8.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = endExpanded,
                            onDismissRequest = { endExpanded = false }
                        ) {
                            timeSlots.filter { it > startTime }.forEach { time ->
                                DropdownMenuItem(
                                    text = { Text(time) },
                                    onClick = {
                                        onEndTimeChanged(time)
                                        endExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Duration preview
                val hours = calculateHours(startTime, endTime)
                if (hours > 0) {
                    Text(
                        text = "Duration: $hours hours",
                        fontSize = 12.sp,
                        color = EduBlue,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = endTime > startTime,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Add Slot")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Helper functions
private fun timesOverlap(start1: String, end1: String, start2: String, end2: String): Boolean {
    return start2 < end1 && end2 > start1
}

private fun calculateHours(startTime: String, endTime: String): Double {
    val (startHour, startMin) = startTime.split(":").map { it.toInt() }
    val (endHour, endMin) = endTime.split(":").map { it.toInt() }
    val startMinutes = startHour * 60 + startMin
    val endMinutes = endHour * 60 + endMin
    return (endMinutes - startMinutes) / 60.0
}
