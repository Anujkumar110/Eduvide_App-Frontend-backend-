package com.example.tutorapp.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tutorapp.ui.theme.EduBlue
import com.example.tutorapp.ui.theme.EduBlueLight
import com.example.tutorapp.ui.theme.EduBorder
import com.example.tutorapp.ui.theme.EduChipBg
import com.example.tutorapp.ui.theme.EduGray
import com.example.tutorapp.ui.theme.EduTextDark

// ── Step Progress Indicator ───────────────────────────────────────────────────

@Composable
fun StepIndicator(currentStep: Int, totalSteps: Int = 3) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        repeat(totalSteps) { index ->
            val step        = index + 1
            val isCompleted = step < currentStep
            val isCurrent   = step == currentStep

            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (isCompleted || isCurrent) EduBlue else Color.White.copy(alpha = 0.4f))
                    .border(2.dp, if (isCompleted || isCurrent) Color.White else Color.White.copy(alpha = 0.5f), CircleShape)
            ) {
                Text(
                    text       = step.toString(),
                    color      = Color.White,
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (step < totalSteps) {
                HorizontalDivider(
                    modifier  = Modifier.width(28.dp),
                    color     = if (isCompleted) Color.White else Color.White.copy(alpha = 0.4f),
                    thickness = 2.dp
                )
            }
        }
    }
}

// ── Primary Button ────────────────────────────────────────────────────────────

@Composable
fun EduButton(
    text:     String,
    onClick:  () -> Unit,
    modifier: Modifier = Modifier,
    enabled:  Boolean  = true
) {
    Button(
        onClick  = onClick,
        enabled  = enabled,
        modifier = modifier.fillMaxWidth().height(50.dp),
        shape    = RoundedCornerShape(10.dp),
        colors   = ButtonDefaults.buttonColors(
            containerColor = EduBlue,
            contentColor   = Color.White
        )
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ── Outlined Text Field ───────────────────────────────────────────────────────

@Composable
fun EduTextField(
    value:         String,
    onValueChange: (String) -> Unit,
    label:         String,
    modifier:      Modifier = Modifier,
    isError:       Boolean  = false,
    errorMessage:  String   = "",
    singleLine:    Boolean  = true
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value         = value,
            onValueChange = onValueChange,
            label         = { Text(label, fontSize = 13.sp) },
            singleLine    = singleLine,
            isError       = isError,
            modifier      = Modifier.fillMaxWidth(),
            shape         = RoundedCornerShape(10.dp),
            colors        = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = EduBlue,
                unfocusedBorderColor = EduBorder,
                errorBorderColor     = MaterialTheme.colorScheme.error,
                focusedLabelColor    = EduBlue,
                unfocusedLabelColor  = EduGray,
                errorLabelColor      = MaterialTheme.colorScheme.error,
                focusedTextColor     = EduTextDark,
                unfocusedTextColor   = EduTextDark,
                cursorColor          = EduBlue
            )
        )
        if (isError && errorMessage.isNotEmpty()) {
            Text(
                text     = errorMessage,
                color    = MaterialTheme.colorScheme.error,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }
    }
}

// ── Dropdown ──────────────────────────────────────────────────────────────────

@Composable
fun EduDropdown(
    label:           String,
    selectedValue:   String,
    options:         List<String>,
    onValueSelected: (String) -> Unit,
    modifier:        Modifier = Modifier,
    isError:         Boolean  = false,
    errorMessage:    String   = ""
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value         = selectedValue,
                onValueChange = {},
                readOnly      = true,
                label         = { Text(label, fontSize = 13.sp) },
                trailingIcon  = {
                    Icon(
                        imageVector        = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint               = if (isError) MaterialTheme.colorScheme.error else EduGray
                    )
                },
                isError  = isError,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(10.dp),
                colors   = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = EduBlue,
                    unfocusedBorderColor = EduBorder,
                    errorBorderColor     = MaterialTheme.colorScheme.error,
                    focusedLabelColor    = EduBlue,
                    unfocusedLabelColor  = EduGray,
                    errorLabelColor      = MaterialTheme.colorScheme.error,
                    focusedTextColor     = EduTextDark,
                    unfocusedTextColor   = EduTextDark
                )
            )
            //Invisible overlay captures click — fixes spinner not opening
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { expanded = true }
            )
            DropdownMenu(
                expanded         = expanded,
                onDismissRequest = { expanded = false },
                modifier         = Modifier.background(Color.White)
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text    = { Text(option, color = EduTextDark) },
                        onClick = { onValueSelected(option); expanded = false }
                    )
                }
            }
        }
        if (isError && errorMessage.isNotEmpty()) {
            Text(
                text     = errorMessage,
                color    = MaterialTheme.colorScheme.error,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }
    }
}

// ── Chip ──────────────────────────────────────────────────────────────────────

@Composable
fun EduChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) EduBlue else EduChipBg)
            .border(1.dp, if (selected) EduBlue else EduBorder, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text       = text,
            color      = if (selected) Color.White else EduTextDark,
            fontSize   = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

// ── Radio Option Row ──────────────────────────────────────────────────────────

@Composable
fun EduRadioOption(
    text:     String,
    selected: Boolean,
    onClick:  () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier          = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.5.dp, if (selected) EduBlue else EduBorder, RoundedCornerShape(10.dp))
            .background(if (selected) EduBlueLight else Color.White)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        RadioButton(
            selected = selected,
            onClick  = onClick,
            colors   = RadioButtonDefaults.colors(selectedColor = EduBlue)
        )
        Spacer(Modifier.width(8.dp))
        Text(text, color = EduTextDark, fontSize = 15.sp)
    }
}

// ── Validation Error Text ─────────────────────────────────────────────────────

@Composable
fun ErrorText(message: String, visible: Boolean) {
    if (visible) {
        Text(
            text     = message,
            color    = MaterialTheme.colorScheme.error,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
        )
    }
}