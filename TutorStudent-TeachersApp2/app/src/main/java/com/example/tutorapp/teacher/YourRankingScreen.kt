package com.example.tutorapp.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.tutorapp.ui.theme.EduBackground
import com.example.tutorapp.ui.theme.EduBlue
import com.example.tutorapp.ui.theme.EduBlueLight
import com.example.tutorapp.ui.theme.EduBorder
import com.example.tutorapp.ui.theme.EduGray
import com.example.tutorapp.ui.theme.EduTextDark

// ── Model ─────────────────────────────────────────────────────────────────────

private data class RankedTutor(
    val rank:        Int,
    val name:        String,
    val subject:     String,
    val rating:      Float,
    val sessions:    Int,
    val students:    Int,
    val avatarColor: Color,
    val isMe:        Boolean = false
)

// ── Screen ────────────────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun YourRankingScreenPreview() {
    YourRankingScreen(navController = rememberNavController())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YourRankingScreen(navController: NavController) {

    val tutors = listOf(
        RankedTutor(1,  "Ananya Sharma",  "Mathematics", 4.9f, 312, 48, Color(0xFF1E88E5)),
        RankedTutor(2,  "Rohan Mehta",    "Science",     4.8f, 289, 41, Color(0xFF43A047)),
        RankedTutor(3,  "Priti Nair",     "English",     4.8f, 261, 39, Color(0xFFE53935)),
        RankedTutor(4,  "Vijay Kumar",    "Computer",    4.7f, 244, 36, Color(0xFF8E24AA)),
        RankedTutor(5,  "Pearl",          "Computer",    4.6f, 198, 28, Color(0xFF00897B), isMe = true),
        RankedTutor(6,  "Sunita Rao",     "Hindi",       4.5f, 187, 27, Color(0xFFF57C00)),
        RankedTutor(7,  "Dev Agarwal",    "Physics",     4.5f, 172, 25, Color(0xFF0288D1)),
        RankedTutor(8,  "Meena Joshi",    "Chemistry",   4.4f, 155, 22, Color(0xFFAD1457)),
        RankedTutor(9,  "Arjun Singh",    "Maths",       4.3f, 143, 21, Color(0xFF6D4C41)),
        RankedTutor(10, "Kavita Pandey",  "Biology",     4.2f, 128, 19, Color(0xFF37474F)),
    )

    val me = tutors.first { it.isMe }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                title = {
                    Text("Your Ranking", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = EduBlue)
            )
        },
        containerColor = EduBackground
    ) { innerPadding ->

        LazyColumn(
            modifier            = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding      = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {

            // ── My rank banner ────────────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(EduBlue)
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier            = Modifier.fillMaxWidth()
                    ) {
                        // Avatar with rank badge
                        Box(contentAlignment = Alignment.BottomEnd) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier         = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.25f))
                            ) {
                                Text(
                                    text       = me.name.first().toString(),
                                    color      = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize   = 30.sp
                                )
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier         = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFFC107))
                            ) {
                                Text(
                                    text       = "#${me.rank}",
                                    color      = Color.White,
                                    fontSize   = 8.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Text(me.name,    color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        Text(me.subject, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)

                        Spacer(Modifier.height(16.dp))

                        // Stats row
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            MyRankStat("#${me.rank}",           "Rank")
                            MyRankStatDivider()
                            MyRankStat("${me.rating}★",         "Rating")
                            MyRankStatDivider()
                            MyRankStat("${me.sessions}",        "Sessions")
                            MyRankStatDivider()
                            MyRankStat("${me.students}",        "Students")
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
            }

            // ── Top 3 podium ──────────────────────────────────────────────────
            item {
                Text(
                    text         = "TOP TUTORS",
                    fontSize     = 11.sp,
                    fontWeight   = FontWeight.Bold,
                    color        = EduGray,
                    letterSpacing = 1.sp,
                    modifier     = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )

                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.Bottom
                ) {
                    // 2nd place
                    PodiumItem(tutor = tutors[1], height = 90.dp,  modifier = Modifier.weight(1f))
                    // 1st place
                    PodiumItem(tutor = tutors[0], height = 120.dp, modifier = Modifier.weight(1f))
                    // 3rd place
                    PodiumItem(tutor = tutors[2], height = 70.dp,  modifier = Modifier.weight(1f))
                }

                Spacer(Modifier.height(8.dp))
            }

            // ── Full leaderboard ──────────────────────────────────────────────
            item {
                Text(
                    text         = "LEADERBOARD",
                    fontSize     = 11.sp,
                    fontWeight   = FontWeight.Bold,
                    color        = EduGray,
                    letterSpacing = 1.sp,
                    modifier     = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            itemsIndexed(tutors) { _, tutor ->
                LeaderboardRow(tutor = tutor)
                HorizontalDivider(
                    color    = EduBorder,
                    modifier = Modifier.padding(start = 72.dp)
                )
            }

            // ── Tips to improve ───────────────────────────────────────────────
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    text         = "HOW TO IMPROVE YOUR RANK",
                    fontSize     = 11.sp,
                    fontWeight   = FontWeight.Bold,
                    color        = EduGray,
                    letterSpacing = 1.sp,
                    modifier     = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )

                Column(
                    modifier            = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RankTip(Icons.Filled.Star,        "Get more 5-star reviews",       "Ask satisfied students to leave a review after each session.")
                    RankTip(Icons.Filled.DateRange,   "Complete more sessions",        "Consistently conducting sessions boosts your activity score.")
                    RankTip(Icons.Filled.Person,      "Grow your student base",        "Respond quickly to enquiries to improve your response rate.")
                    RankTip(Icons.Filled.VideoCall,   "Add an intro video",            "Tutors with intro videos get 3× more enquiries.")
                }
            }
        }
    }
}

// ── Podium item ───────────────────────────────────────────────────────────────

@Composable
private fun PodiumItem(
    tutor:    RankedTutor,
    height:   androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val medalColor = when (tutor.rank) {
        1    -> Color(0xFFFFC107)
        2    -> Color(0xFFB0BEC5)
        else -> Color(0xFFFF8A65)
    }

    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        // Avatar
        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(tutor.avatarColor)
        ) {
            Text(tutor.name.first().toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text       = tutor.name.split(" ").first(),
            fontSize   = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color      = EduTextDark,
            maxLines   = 1,
            textAlign  = TextAlign.Center,
            modifier   = Modifier.fillMaxWidth()
        )

        Text(
            text      = "${tutor.rating}★",
            fontSize  = 10.sp,
            color     = Color(0xFFFFC107),
            textAlign = TextAlign.Center,
            modifier  = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(4.dp))

        // Podium block
        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(medalColor.copy(alpha = 0.2f))
        ) {
            Text(
                text       = "#${tutor.rank}",
                fontSize   = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color      = medalColor
            )
        }
    }
}

// ── Leaderboard row ───────────────────────────────────────────────────────────

@Composable
private fun LeaderboardRow(tutor: RankedTutor) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .background(if (tutor.isMe) EduBlueLight else Color.White)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rank number
        Text(
            text       = "#${tutor.rank}",
            fontSize   = 13.sp,
            fontWeight = FontWeight.Bold,
            color      = if (tutor.rank <= 3) Color(0xFFFFC107) else EduGray,
            modifier   = Modifier.width(36.dp)
        )

        // Avatar
        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(tutor.avatarColor)
        ) {
            Text(tutor.name.first().toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(tutor.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = EduTextDark)
                if (tutor.isMe) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(EduBlue)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text("You", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text(tutor.subject, fontSize = 12.sp, color = EduGray)
        }

        Column(horizontalAlignment = Alignment.End) {
            Text("${tutor.rating}★", fontSize = 13.sp, color = Color(0xFFFFC107), fontWeight = FontWeight.Bold)
            Text("${tutor.sessions} sessions", fontSize = 10.sp, color = EduGray)
        }
    }
}

// ── Rank tip row ──────────────────────────────────────────────────────────────

@Composable
private fun RankTip(
    icon:     androidx.compose.ui.graphics.vector.ImageVector,
    title:    String,
    subtitle: String
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier          = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(EduBlueLight)
            ) {
                Icon(icon, contentDescription = null, tint = EduBlue, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title,    fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = EduTextDark)
                Text(subtitle, fontSize = 12.sp, color = EduGray, lineHeight = 17.sp)
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun MyRankStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
    }
}

@Composable
private fun MyRankStatDivider() {
    Box(modifier = Modifier.width(1.dp).height(28.dp).background(Color.White.copy(alpha = 0.3f)))
}