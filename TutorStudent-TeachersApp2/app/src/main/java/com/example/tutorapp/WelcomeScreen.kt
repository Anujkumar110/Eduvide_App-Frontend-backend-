package com.example.tutorapp

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.tutorapp.ui.theme.*
import kotlinx.coroutines.delay

private data class BannerSlide(
    val topLabel: String, val title: String, val subtitle: String,
    val badgeLine1: String, val badgeLine2: String, val badgeLine3: String,
    val gradientColors: List<Color>
)

private val bannerSlides = listOf(
    BannerSlide("Book Your Slot!", "Learn from\nbest", "One-on-one sessions\nwith expert tutors",
        "One-On-One", "LIVE", "CLASSES", listOf(Color(0xFF1A1A2E), Color(0xFF16213E))),
    BannerSlide("Limited Seats!", "Expert\nMentors", "Learn at your own\npace, anytime",
        "Personalised", "STUDY", "PLANS", listOf(Color(0xFF0F3460), Color(0xFF533483))),
    BannerSlide("Start Today!", "Achieve\nyour Goals", "Top-rated teachers\nacross all subjects",
        "100%", "RESULT", "FOCUSED", listOf(Color(0xFF134E5E), Color(0xFF71B280)))
)

@Composable
fun AutoSlidingBanner() {
    var currentIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { delay(3000); currentIndex = (currentIndex + 1) % bannerSlides.size }
    }
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Box(modifier = Modifier.fillMaxWidth().height(175.dp)) {
            Crossfade(targetState = currentIndex, animationSpec = tween(500), label = "banner") { index ->
                val slide = bannerSlides[index]
                Box(
                    modifier = Modifier.fillMaxWidth().height(175.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Brush.linearGradient(slide.gradientColors))
                ) {
                    Box(modifier = Modifier.size(140.dp).offset(x = (-40).dp, y = (-40).dp).clip(CircleShape).background(Color(0x18FFFFFF)))
                    Box(modifier = Modifier.size(90.dp).align(Alignment.BottomStart).offset(x = 10.dp, y = 35.dp).clip(CircleShape).background(Color(0x10FFFFFF)))
                    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(slide.topLabel, fontSize = 11.sp, color = Color(0xFFBBCCDD), fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(slide.title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, lineHeight = 30.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(slide.subtitle, fontSize = 11.sp, color = Color(0xAAFFFFFF), lineHeight = 16.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(OrangeBadge).padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(slide.badgeLine1, fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text(slide.badgeLine2, fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.ExtraBold, lineHeight = 24.sp)
                                Text(slide.badgeLine3, fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            bannerSlides.indices.forEach { i ->
                val isActive = i == currentIndex
                Box(modifier = Modifier.padding(horizontal = 3.dp).height(6.dp)
                    .width(if (isActive) 20.dp else 6.dp).clip(CircleShape)
                    .background(if (isActive) PrimaryBlue else Color(0xFFCCCCCC))
                    .animateContentSize(tween(300)))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen(navController: NavHostController) {

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardWhite),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(PrimaryBlue), contentAlignment = Alignment.Center) {
                            Text("E", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(text = "Welcome to", fontSize = 11.sp, color = TextGray)
                            Text(text = "EduVibe", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().background(LightBg).padding(innerPadding).verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            AutoSlidingBanner()

            // ── I am a ─────────────────────────────────────────────────────────
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "I am a", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    //  Student click → LoginScreen
                    Card(
                        modifier  = Modifier.weight(1f).height(140.dp).shadow(6.dp, RoundedCornerShape(16.dp))
                            .clickable { navController.navigate("LoginScreen") },
                        shape     = RoundedCornerShape(16.dp),
                        colors    = CardDefaults.cardColors(containerColor = StudentBg),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(painter = painterResource(id = R.drawable.student), contentDescription = "Student", modifier = Modifier.size(70.dp), contentScale = ContentScale.Fit)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Student", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        }
                    }
                    Card(
                        modifier  = Modifier.weight(1f).height(140.dp).shadow(6.dp, RoundedCornerShape(16.dp))
                            .clickable { navController.navigate("TeacherSplashScreen") },
                        shape     = RoundedCornerShape(16.dp),
                        colors    = CardDefaults.cardColors(containerColor = TeacherBg),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(painter = painterResource(id = R.drawable.teacher), contentDescription = "Teacher", modifier = Modifier.size(70.dp), contentScale = ContentScale.Fit)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Teacher", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        }
                    }
                }
            }

            // ── Our Mentors ────────────────────────────────────────────────────
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎓", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Our Mentors", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardWhite), elevation = CardDefaults.cardElevation(2.dp)) {
                    Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MentorCard("Tanish Jaswal", "Physician", "Mentor who guides me in understanding mathematical concepts and improving my analytical thinking.", R.drawable.ajay, Modifier.weight(1f))
                        MentorCard("tejas ghosh", "Mathematician", "Expert tutor who simplifies complex topics and helps build strong conceptual foundations.", R.drawable.priya, Modifier.weight(1f))
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextGray, modifier = Modifier.align(Alignment.CenterVertically).size(20.dp))
                    }
                }
            }

            // ── Student Testimonial ────────────────────────────────────────────
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⭐", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Student Testimonial", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardWhite), elevation = CardDefaults.cardElevation(2.dp)) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Inspiring Success Stories from Focus Online Tutors", fontSize = 10.sp, color = TextGray, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Sadhir kumar", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            Text("Class 12th maths student", fontSize = 11.sp, color = PrimaryBlue, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("\"I had a great learning experience with Focus Online Tutors. The teachers explain concepts very clearly and are always supportive. My confidence and academic performance have improved a lot.\"",
                                fontSize = 11.sp, color = TextGray, textAlign = TextAlign.Center, fontStyle = FontStyle.Italic, lineHeight = 16.sp, modifier = Modifier.fillMaxWidth())
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextGray, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun MentorCard(name: String, role: String, desc: String, imageRes: Int, modifier: Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painter = painterResource(id = imageRes), contentDescription = name, modifier = Modifier.size(38.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(role, fontSize = 10.sp, color = TextGray)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(desc, fontSize = 10.sp, color = TextGray, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp)
    }
}