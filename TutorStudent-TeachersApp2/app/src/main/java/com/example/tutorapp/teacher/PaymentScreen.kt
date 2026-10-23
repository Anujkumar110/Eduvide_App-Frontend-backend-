package com.example.tutorapp.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.tutorapp.ui.theme.EduBlue
import com.example.tutorapp.ui.theme.EduBlueLight
import com.example.tutorapp.ui.theme.EduBorder
import com.example.tutorapp.ui.theme.EduBlueDark
import com.example.tutorapp.ui.theme.EduGray
import com.example.tutorapp.ui.theme.EduTextDark
import com.example.tutorapp.ui.theme.EduBackground

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PaymentScreenPreview() {
    PaymentScreen(navController = rememberNavController())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreen(navController: NavController) {

    var upiId         by remember { mutableStateOf("") }
    var accountHolder by remember { mutableStateOf("") }
    var accountNumber by remember { mutableStateOf("") }
    var ifscCode      by remember { mutableStateOf("") }
    var bankName      by remember { mutableStateOf("") }
    var showErrors    by remember { mutableStateOf(false) }

    // ── Validation ────────────────────────────────────────────────────────────
    val upiError           = showErrors && upiId.isBlank()
    val accountHolderError = showErrors && accountHolder.isBlank()
    val accountNumberError = showErrors && (accountNumber.isBlank() || accountNumber.length < 9)
    val accountNumberMsg   = when {
        accountNumber.isBlank()   -> "Account number is required"
        accountNumber.length < 9  -> "Enter a valid account number"
        else                      -> ""
    }
    val ifscError = showErrors && (ifscCode.isBlank() || ifscCode.length < 11)
    val ifscMsg   = when {
        ifscCode.isBlank()   -> "IFSC code is required"
        ifscCode.length < 11 -> "Enter a valid 11-character IFSC"
        else                 -> ""
    }
    val bankError = showErrors && bankName.isBlank()

    fun validate() = upiId.isNotBlank() && accountHolder.isNotBlank() &&
            accountNumber.length >= 9 && ifscCode.length >= 11 && bankName.isNotBlank()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text       = "Payment",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 16.sp,
                        color      = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = EduBlue
                )
            )
        },
        containerColor = EduBackground
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Earnings summary ──────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EarningCard(
                    label  = "Total Earned",
                    amount = "₹0",
                    color  = Color(0xFF43A047),
                    modifier = Modifier.weight(1f)
                )
                EarningCard(
                    label  = "Pending",
                    amount = "₹0",
                    color  = Color(0xFFF57C00),
                    modifier = Modifier.weight(1f)
                )
            }

            // ── UPI Section ───────────────────────────────────────────────────
            PaymentSectionHeader(
                icon  = Icons.Filled.CreditCard,
                title = "UPI Details"
            )

            EduTextField(
                value         = upiId,
                onValueChange = { upiId = it },
                label         = "UPI ID (e.g. name@upi)",
                isError       = upiError,
                errorMessage  = "UPI ID is required"
            )

            // ── Bank account ──────────────────────────────────────────────────
            PaymentSectionHeader(
                icon  = Icons.Filled.AccountBalance,
                title = "Bank Account Details"
            )

            EduTextField(
                value         = accountHolder,
                onValueChange = { accountHolder = it },
                label         = "Account Holder Name",
                isError       = accountHolderError,
                errorMessage  = "Account holder name is required"
            )

            EduTextField(
                value         = accountNumber,
                onValueChange = { if (it.all { c -> c.isDigit() }) accountNumber = it },
                label         = "Account Number",
                isError       = accountNumberError,
                errorMessage  = accountNumberMsg
            )

            EduTextField(
                value         = ifscCode,
                onValueChange = { ifscCode = it.uppercase() },
                label         = "IFSC Code",
                isError       = ifscError,
                errorMessage  = ifscMsg
            )

            EduTextField(
                value         = bankName,
                onValueChange = { bankName = it },
                label         = "Bank Name",
                isError       = bankError,
                errorMessage  = "Bank name is required"
            )

            // ── Info box ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(EduBlueLight)
                    .border(1.dp, EduBlue.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint     = EduBlue,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text     = "Payments are processed within 3–5 business days after a session is completed and confirmed.",
                    fontSize = 12.sp,
                    color    = EduBlueDark
                )
            }

            Spacer(Modifier.height(4.dp))

            EduButton(
                text    = "Save Payment Details",
                onClick = {
                    showErrors = true
                    if (validate()) {
                        navController.popBackStack()
                    }
                }
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun EarningCard(
    label:    String,
    amount:   String,
    color:    Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier  = modifier,
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier            = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text       = amount,
                fontWeight = FontWeight.ExtraBold,
                fontSize   = 22.sp,
                color      = color
            )
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 12.sp, color = EduGray)
        }
    }
}

@Composable
private fun PaymentSectionHeader(
    icon:  androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier          = Modifier.padding(top = 4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = EduBlue, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text       = title,
            fontWeight = FontWeight.SemiBold,
            fontSize   = 15.sp,
            color      = EduTextDark
        )
    }
    HorizontalDivider(color = EduBorder)
}