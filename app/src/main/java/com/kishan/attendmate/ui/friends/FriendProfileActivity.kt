package com.kishan.attendmate.ui.friends

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import com.kishan.attendmate.AttendanceColors
import com.kishan.attendmate.ui.theme.AttendMateTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.util.Locale

/* ══════════════════════════════════════════════════════
   DATA MODELS
══════════════════════════════════════════════════════ */
data class FriendLecture(
    val subjectName: String,
    val status: String,
    val startTime: String,
    val endTime: String
)

/* ══════════════════════════════════════════════════════
   ACTIVITY
══════════════════════════════════════════════════════ */
class FriendProfileActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val friendUid = intent.getStringExtra("uid") ?: return finish()
        setContent {
            AttendMateTheme {
                FriendProfileScreen(friendUid = friendUid, onBack = { finish() })
            }
        }
    }
}

/* ══════════════════════════════════════════════════════
   SCREEN
══════════════════════════════════════════════════════ */
@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendProfileScreen(friendUid: String, onBack: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    val todayId = remember { LocalDate.now().toString() }

    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var percentage by remember { mutableDoubleStateOf(0.0) }
    var totalClasses by remember { mutableIntStateOf(0) }
    var attendedClasses by remember { mutableIntStateOf(0) }
    var todayLectures by remember { mutableStateOf<List<FriendLecture>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    /* Animated background orbs */
    val inf = rememberInfiniteTransition(label = "bg")
    val orb1Alpha by inf.animateFloat(0.05f, 0.13f,
        infiniteRepeatable(tween(4000, easing = EaseInOutSine), RepeatMode.Reverse), "o1")
    val orb1Y by inf.animateFloat(0f, 24f,
        infiniteRepeatable(tween(5800, easing = EaseInOutSine), RepeatMode.Reverse), "o1y")
    val orb2Alpha by inf.animateFloat(0.03f, 0.09f,
        infiniteRepeatable(tween(6200, easing = EaseInOutSine), RepeatMode.Reverse), "o2")

    /* ── Data Load ── */
    LaunchedEffect(friendUid) {
        loading = true
        errorMsg = null
        try {
            val userDoc = db.collection("users").document(friendUid).get().await()
            username = userDoc.getString("username") ?: "User"
            email = userDoc.getString("email") ?: ""

            val snapDoc = db.collection("users").document(friendUid)
                .collection("dailySnapshot").document(todayId).get().await()

            if (snapDoc.exists()) {
                percentage = snapDoc.getDouble("percentage") ?: 0.0
                totalClasses = snapDoc.getLong("totalClasses")?.toInt() ?: 0
                attendedClasses = snapDoc.getLong("attendedClasses")?.toInt() ?: 0

                // Parse the nested objects correctly
                val map = snapDoc.get("lectures") as? Map<String, Any> ?: emptyMap()
                todayLectures = map.mapNotNull { (_, value) ->
                    val data = value as? Map<String, String> ?: return@mapNotNull null
                    FriendLecture(
                        subjectName = data["subjectName"] ?: "Unknown",
                        status = data["status"] ?: "ABSENT",
                        startTime = data["startTime"] ?: "--:--",
                        endTime = data["endTime"] ?: "--:--"
                    )
                }.sortedBy { it.startTime }
            }
        } catch (_: Exception) {
            errorMsg = "Unable to load profile. Check your connection."
        } finally {
            loading = false
        }
    }

    /* ── UI shell ── */
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Friend Profile", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp) },
                navigationIcon = {
                    FilledIconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->

        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            /* Orbs */
            Box(Modifier.size(360.dp).offset((-90).dp, (50 + orb1Y).dp).blur(100.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = orb1Alpha), CircleShape))
            Box(Modifier.size(280.dp).offset(210.dp, 380.dp).blur(90.dp)
                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = orb2Alpha), CircleShape))

            AnimatedContent(
                targetState = when {
                    loading -> "loading"
                    errorMsg != null -> "error"
                    else -> "content"
                },
                transitionSpec = { fadeIn(tween(400)).togetherWith(fadeOut(tween(250))) },
                label = "screen"
            ) { state ->
                when (state) {
                    "loading" -> ProfileSkeletonScreen(padding)
                    "error" -> ProfileErrorScreen(errorMsg ?: "Unknown error", onRetry = {
                        loading = true
                        errorMsg = null
                    }, padding)
                    else -> ProfileContentScreen(
                        padding = padding,
                        username = username,
                        email = email,
                        percentage = percentage.toFloat(),
                        totalClasses = totalClasses,
                        attendedClasses = attendedClasses,
                        todayLectures = todayLectures
                    )
                }
            }
        }
    }
}

/* ══════════════════════════════════════════════════════
   CONTENT SCREEN
══════════════════════════════════════════════════════ */
@Composable
fun ProfileContentScreen(
    padding: PaddingValues,
    username: String,
    email: String,
    percentage: Float,
    totalClasses: Int,
    attendedClasses: Int,
    todayLectures: List<FriendLecture>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        /* ── Profile Header Card ── */
        item {
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { delay(50); visible = true }
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(initialOffsetY = { -it / 2 }) + fadeIn()
            ) {
                ProfileHeaderCard(username = username, email = email, percentage = percentage)
            }
        }

        /* ── Attendance Summary Card ── */
        item {
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { delay(130); visible = true }
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(initialOffsetY = { -it / 3 }) + fadeIn()
            ) {
                FriendAttendanceSummaryCard(
                    total = totalClasses,
                    attended = attendedClasses,
                    percentage = percentage
                )
            }
        }

        /* ── Today's Lectures Section Header ── */
        item {
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { delay(200); visible = true }
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(300))
            ) {
                ProfileSectionHeader(
                    icon = Icons.AutoMirrored.Outlined.EventNote,
                    title = "Today's Lectures",
                    subtitle = "${todayLectures.size} ${if (todayLectures.size == 1) "class" else "classes"} scheduled"
                )
            }
        }

        /* ── Lectures ── */
        if (todayLectures.isEmpty()) {
            item {
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { delay(260); visible = true }
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn() + expandVertically()
                ) {
                    NoLecturesToday()
                }
            }
        } else {
            itemsIndexed(todayLectures, key = { _, lecture -> "${lecture.subjectName}_${lecture.startTime}" }) { index, lecture ->
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(lecture) { delay(260 + index * 60L); visible = true }

                AnimatedVisibility(
                    visible = visible,
                    enter = slideInHorizontally(
                        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
                        initialOffsetX = { -(it * 0.35f).toInt() }
                    ) + fadeIn(tween(220))
                ) {
                    FriendLectureCard(lecture = lecture)
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

/* ══════════════════════════════════════════════════════
   PROFILE HEADER CARD
══════════════════════════════════════════════════════ */
@Composable
fun ProfileHeaderCard(username: String, email: String, percentage: Float) {
    val statusColor = when {
        percentage >= 75 -> AttendanceColors.Present
        percentage >= 60 -> AttendanceColors.Warning
        else -> AttendanceColors.Absent
    }
    val avatarPalette = listOf(
        Color(0xFF6366F1), Color(0xFF8B5CF6), Color(0xFFEC4899),
        Color(0xFF06B6D4), Color(0xFF10B981), Color(0xFFF59E0B)
    )
    val avatarColor = avatarPalette[username.hashCode().let { if (it < 0) -it else it } % avatarPalette.size]

    Card(
        modifier = Modifier.fillMaxWidth().shadow(
            elevation = 8.dp,
            shape = RoundedCornerShape(28.dp),
            spotColor = statusColor.copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                    MaterialTheme.colorScheme.surface
                ))
            )
        ) {
            /* Decorative canvas blobs */
            Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                drawCircle(
                    color = statusColor.copy(alpha = 0.05f),
                    radius = 140f,
                    center = Offset(size.width * 0.85f, size.height * 0.3f)
                )
                drawCircle(
                    color = Color.Transparent,
                    radius = 80f,
                    center = Offset(size.width * 0.1f, size.height * 0.9f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                /* Avatar */
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .shadow(12.dp, CircleShape, spotColor = avatarColor.copy(0.4f))
                        .clip(CircleShape)
                        .background(Brush.linearGradient(
                            listOf(avatarColor.copy(0.75f), avatarColor)
                        )),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        username.firstOrNull()?.uppercase() ?: "?",
                        color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp
                    )
                }

                Column(Modifier.weight(1f)) {
                    Text(
                        username,
                        fontWeight = FontWeight.ExtraBold, fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (email.isNotEmpty()) {
                        Spacer(Modifier.height(3.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Email, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                email, fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    /* Status pill */
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = statusColor.copy(alpha = 0.13f),
                        border = BorderStroke(1.dp, statusColor.copy(0.3f))
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(statusColor))
                            Text(
                                when {
                                    percentage >= 75 -> "Excellent Attendance"
                                    percentage >= 60 -> "Decent Attendance"
                                    else -> "Low Attendance"
                                },
                                color = statusColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ══════════════════════════════════════════════════════
   ATTENDANCE SUMMARY CARD
══════════════════════════════════════════════════════ */
@Composable
fun FriendAttendanceSummaryCard(total: Int, attended: Int, percentage: Float) {
    val animPct = remember { Animatable(0f) }
    LaunchedEffect(percentage) {
        animPct.animateTo(
            percentage,
            spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)
        )
    }

    val statusColor = when {
        percentage >= 75 -> AttendanceColors.Present
        percentage >= 60 -> AttendanceColors.Warning
        else -> AttendanceColors.Absent
    }
    val statusText = when {
        percentage >= 75 -> "Excellent"
        percentage >= 60 -> "Good"
        else -> "Needs Attention"
    }
    val statusIcon = when {
        percentage >= 75 -> Icons.Default.CheckCircle
        percentage >= 60 -> Icons.Default.Warning
        else -> Icons.Default.ErrorOutline
    }
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth().shadow(
            12.dp, RoundedCornerShape(32.dp), spotColor = statusColor.copy(0.25f)
        ),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(
                statusColor.copy(0.08f),
                MaterialTheme.colorScheme.surface.copy(0.95f),
                MaterialTheme.colorScheme.surface
            )))
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(statusColor.copy(0.03f), 100f, Offset(size.width * 0.85f, size.height * 0.2f))
                drawCircle(statusColor.copy(0.04f), 150f, Offset(size.width * 0.15f, size.height * 0.8f))
            }

            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                /* Title row */
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier.size(48.dp)
                                .shadow(8.dp, RoundedCornerShape(14.dp), spotColor = statusColor.copy(0.3f))
                                .clip(RoundedCornerShape(14.dp))
                                .background(Brush.linearGradient(listOf(statusColor, statusColor.copy(0.8f)))),
                            Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.School, null, tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Column {
                            Text("Attendance", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface)
                            Text("Overall Performance", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = statusColor.copy(0.15f),
                        border = BorderStroke(1.5.dp, statusColor.copy(0.3f))
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(statusIcon, null, tint = statusColor, modifier = Modifier.size(16.dp))
                            Text(statusText, fontSize = 12.sp, color = statusColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                /* Circular progress */
                Box(Modifier.size(200.dp), Alignment.Center) {
                    repeat(3) { i ->
                        Canvas(Modifier.size((200 - i * 20).dp).alpha(0.3f - i * 0.1f)) {
                            drawCircle(Brush.radialGradient(listOf(
                                statusColor.copy(0.3f), Color.Transparent)))
                        }
                    }
                    Canvas(Modifier.size(175.dp)) {
                        drawCircle(
                            brush = Brush.radialGradient(listOf(
                                surfaceVariant.copy(0.3f), surfaceVariant.copy(0.1f))),
                            style = Stroke(18.dp.toPx())
                        )
                    }
                    CircularProgressIndicator(
                        progress = { animPct.value / 100f },
                        modifier = Modifier.size(175.dp),
                        strokeWidth = 18.dp,
                        trackColor = Color.Transparent,
                        color = statusColor,
                        strokeCap = StrokeCap.Round
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                String.format(Locale.getDefault(), "%.1f", animPct.value),
                                fontWeight = FontWeight.ExtraBold, fontSize = 50.sp,
                                color = statusColor
                            )
                            Text("%", fontWeight = FontWeight.Bold, fontSize = 28.sp,
                                color = statusColor.copy(0.7f),
                                modifier = Modifier.padding(bottom = 8.dp))
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))

                /* Stats row */
                Surface(
                    Modifier.fillMaxWidth(), RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(0.5f),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        Modifier.padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FriendStatItem("Present", attended.toString(), Icons.Default.CheckCircle, AttendanceColors.Present)
                        VerticalDivider(Modifier.height(56.dp), 2.dp, MaterialTheme.colorScheme.outlineVariant.copy(0.5f))
                        FriendStatItem("Total", total.toString(), Icons.Default.CalendarMonth, AttendanceColors.Info)
                        VerticalDivider(Modifier.height(56.dp), 2.dp, MaterialTheme.colorScheme.outlineVariant.copy(0.5f))
                        FriendStatItem("Absent", (total - attended).toString(), Icons.Default.Cancel, AttendanceColors.Absent)
                    }
                }

                Spacer(Modifier.height(20.dp))

                /* Motivational card */
                if (percentage < 75) {
                    val needed = calcLecturesNeeded(attended, total)
                    FriendMotivationalCard(
                        icon = "📚",
                        title = "Heads up!",
                        message = "Needs $needed more ${if (needed == 1) "class" else "classes"} to reach 75%",
                        color = AttendanceColors.Warning
                    )
                } else {
                    FriendMotivationalCard(
                        icon = "🏆",
                        title = "Outstanding!",
                        message = "Maintaining excellent attendance — well above 75%",
                        color = AttendanceColors.Present
                    )
                }
            }
        }
    }
}

@Composable
private fun FriendStatItem(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.size(44.dp)
                .shadow(6.dp, CircleShape, spotColor = color.copy(0.3f))
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(color.copy(0.2f), color.copy(0.1f)))),
            Alignment.Center
        ) { Icon(icon, null, tint = color, modifier = Modifier.size(22.dp)) }
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = color)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun FriendMotivationalCard(icon: String, title: String, message: String, color: Color) {
    Surface(
        Modifier.fillMaxWidth(), RoundedCornerShape(18.dp),
        color = color.copy(0.1f),
        border = BorderStroke(1.5.dp, color.copy(0.25f)),
        shadowElevation = 2.dp
    ) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(color.copy(0.3f), color.copy(0.2f)))),
                Alignment.Center
            ) { Text(icon, fontSize = 22.sp) }
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(2.dp))
                Text(message, fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
            }
        }
    }
}

/* ══════════════════════════════════════════════════════
   SECTION HEADER
══════════════════════════════════════════════════════ */
@Composable
fun ProfileSectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            Alignment.Center
        ) {
            Icon(
                icon, null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)
            )
        }
        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/* ══════════════════════════════════════════════════════
   LECTURE CARD (Updated for nested object)
══════════════════════════════════════════════════════ */
@Composable
fun FriendLectureCard(lecture: FriendLecture) {
    val normalized = lecture.status.uppercase()
    val isPresent = normalized == "PRESENT"
    val statusColor = if (isPresent) AttendanceColors.Present else AttendanceColors.Absent
    val statusBg = statusColor.copy(alpha = 0.08f)

    Card(
        modifier = Modifier.fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(20.dp), spotColor = statusColor.copy(0.1f)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, statusColor.copy(0.2f))
    ) {
        Box(
            Modifier.fillMaxWidth().background(
                Brush.horizontalGradient(
                    listOf(statusBg, MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface),
                    startX = 0f, endX = 700f
                )
            )
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                /* Status icon box */
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                        .background(statusColor.copy(0.15f)),
                    Alignment.Center
                ) {
                    Icon(
                        if (isPresent) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                        null, tint = statusColor, modifier = Modifier.size(28.dp)
                    )
                }

                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        lecture.subjectName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        /* Time Display */
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Schedule, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                "${lecture.startTime} - ${lecture.endTime}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        /* Status badge */
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = statusColor.copy(0.13f)
                        ) {
                            Text(
                                if (isPresent) "Present" else "Absent",
                                Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ══════════════════════════════════════════════════════
   NO LECTURES TODAY EMPTY STATE
══════════════════════════════════════════════════════ */
@Composable
fun NoLecturesToday() {
    val float by rememberInfiniteTransition("float").animateFloat(
        0f, 14f,
        infiniteRepeatable(tween(2000, easing = EaseInOutCubic), RepeatMode.Reverse), "fl"
    )

    Card(
        Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(28.dp),
            spotColor = MaterialTheme.colorScheme.primary.copy(0.15f)),
        RoundedCornerShape(28.dp),
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(
                MaterialTheme.colorScheme.primaryContainer.copy(0.3f),
                MaterialTheme.colorScheme.surface
            )))
        ) {
            Column(
                Modifier.fillMaxWidth().padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    Modifier.size(100.dp).offset(y = float.dp)
                        .shadow(12.dp, CircleShape,
                            spotColor = MaterialTheme.colorScheme.primary.copy(0.3f))
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer
                        ))),
                    Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.EventBusy, null,
                        Modifier.size(50.dp), tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text("No Lectures Today",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface)
                Text("This friend has no recorded attendance for today yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center)
            }
        }
    }
}

/* ══════════════════════════════════════════════════════
   SKELETON LOADING
══════════════════════════════════════════════════════ */
@Composable
fun ProfileSkeletonScreen(padding: PaddingValues) {
    val inf = rememberInfiniteTransition("sk")
    val alpha by inf.animateFloat(0.25f, 0.65f,
        infiniteRepeatable(tween(900, easing = EaseInOutSine), RepeatMode.Reverse), "ska")

    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        /* Header skeleton */
        item {
            Card(
                Modifier.fillMaxWidth().height(130.dp).alpha(alpha),
                RoundedCornerShape(28.dp),
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Box(Modifier.size(72.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outline.copy(0.25f)))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.width(130.dp).height(18.dp).clip(RoundedCornerShape(9.dp))
                            .background(MaterialTheme.colorScheme.outline.copy(0.20f)))
                        Box(Modifier.width(90.dp).height(13.dp).clip(RoundedCornerShape(7.dp))
                            .background(MaterialTheme.colorScheme.outline.copy(0.15f)))
                        Box(Modifier.width(110.dp).height(22.dp).clip(RoundedCornerShape(11.dp))
                            .background(MaterialTheme.colorScheme.outline.copy(0.18f)))
                    }
                }
            }
        }

        /* Summary card skeleton */
        item {
            Card(
                Modifier.fillMaxWidth().height(380.dp).alpha(alpha * 0.85f),
                RoundedCornerShape(32.dp),
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {}
        }

        /* Lecture skeletons */
        items(3) { i ->
            Card(
                Modifier.fillMaxWidth().height(76.dp).alpha(alpha * (0.9f - i * 0.12f)),
                RoundedCornerShape(20.dp),
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.outline.copy(0.2f)))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.width(120.dp).height(14.dp).clip(RoundedCornerShape(7.dp))
                            .background(MaterialTheme.colorScheme.outline.copy(0.18f)))
                        Box(Modifier.width(80.dp).height(11.dp).clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.outline.copy(0.13f)))
                    }
                }
            }
        }
    }
}

/* ══════════════════════════════════════════════════════
   ERROR STATE
══════════════════════════════════════════════════════ */
@Composable
fun ProfileErrorScreen(errorMessage: String, onRetry: () -> Unit, padding: PaddingValues) {
    Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp)
                .shadow(8.dp, RoundedCornerShape(28.dp),
                    spotColor = MaterialTheme.colorScheme.error.copy(0.2f)),
            RoundedCornerShape(28.dp),
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Box(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(
                    MaterialTheme.colorScheme.errorContainer.copy(0.3f),
                    MaterialTheme.colorScheme.surface
                )))
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Box(
                        Modifier.size(96.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.errorContainer.copy(0.5f)),
                        Alignment.Center
                    ) {
                        Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(52.dp),
                            tint = MaterialTheme.colorScheme.error)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Oops!", fontWeight = FontWeight.ExtraBold, fontSize = 26.sp,
                            color = MaterialTheme.colorScheme.error)
                        Text(errorMessage, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                    Button(
                        onClick = onRetry,
                        Modifier.fillMaxWidth(0.75f).height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Try Again", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/* ══════════════════════════════════════════════════════
   HELPER
══════════════════════════════════════════════════════ */
private fun calcLecturesNeeded(attended: Int, total: Int): Int {
    if (total == 0) return 0
    var ta = attended; var tt = total; var n = 0
    while ((ta.toFloat() / tt * 100) < 75f && n < 100) { ta++; tt++; n++ }
    return n
}