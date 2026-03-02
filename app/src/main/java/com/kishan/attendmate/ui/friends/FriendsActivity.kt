package com.kishan.attendmate.ui.friends

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/* ══════════════════════════════════════════════════════
   DATA MODEL
══════════════════════════════════════════════════════ */
data class Friend(val uid: String, val username: String, val email: String = "")

/* ══════════════════════════════════════════════════════
   THEME — respects system dark/light + Material You (Android 12+)
══════════════════════════════════════════════════════ */
@Composable
fun AttendMateTheme(content: @Composable () -> Unit) {
    val context   = LocalContext.current
    val darkTheme = isSystemInDarkTheme()

    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme  -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme -> darkColorScheme(
            primary             = Color(0xFF82B1FF),
            onPrimary           = Color(0xFF003060),
            primaryContainer    = Color(0xFF004787),
            onPrimaryContainer  = Color(0xFFD4E3FF),
            secondary           = Color(0xFFB0C6FF),
            tertiary            = Color(0xFFD4BBFF),
            background          = Color(0xFF0F1117),
            surface             = Color(0xFF1A1D27),
            surfaceContainerLow = Color(0xFF1E2130),
            surfaceContainerHigh= Color(0xFF252838),
            onSurface           = Color(0xFFE4E6F0),
            onSurfaceVariant    = Color(0xFF9BA1B8),
            outline             = Color(0xFF3D4155),
            outlineVariant      = Color(0xFF2C2F40),
            error               = Color(0xFFFF6E6E),
            onError             = Color(0xFF690005),
            errorContainer      = Color(0xFF93000A),
            onErrorContainer    = Color(0xFFFFDAD6)
        )
        else -> lightColorScheme(
            primary             = Color(0xFF1A6FEB),
            onPrimary           = Color.White,
            primaryContainer    = Color(0xFFD8E8FF),
            onPrimaryContainer  = Color(0xFF001B42),
            secondary           = Color(0xFF4B6291),
            tertiary            = Color(0xFF6B4C9A),
            background          = Color(0xFFF6F8FC),
            surface             = Color.White,
            surfaceContainerLow = Color(0xFFF0F3FA),
            surfaceContainerHigh= Color(0xFFE9EDF8),
            onSurface           = Color(0xFF0D1420),
            onSurfaceVariant    = Color(0xFF5A6178),
            outline             = Color(0xFFBBC3D8),
            outlineVariant      = Color(0xFFDDE3F0),
            error               = Color(0xFFBA1A1A),
            onError             = Color.White,
            errorContainer      = Color(0xFFFFDAD6),
            onErrorContainer    = Color(0xFF410002)
        )
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}

/* ══════════════════════════════════════════════════════
   ACTIVITY
══════════════════════════════════════════════════════ */
class FriendsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AttendMateTheme {
                FriendsScreen(
                    onBack = { finish() },
                    openProfile = { uid ->
                        startActivity(
                            Intent(this, FriendProfileActivity::class.java).putExtra("uid", uid)
                        )
                    }
                )
            }
        }
    }
}

/* ══════════════════════════════════════════════════════
   SCREEN
══════════════════════════════════════════════════════ */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(onBack: () -> Unit, openProfile: (String) -> Unit) {
    val auth       = FirebaseAuth.getInstance()
    val db         = FirebaseFirestore.getInstance()
    val currentUid = auth.currentUser?.uid ?: return
    val isDark     = isSystemInDarkTheme()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope             = rememberCoroutineScope()

    var friends       by remember { mutableStateOf<List<Friend>>(emptyList()) }
    var loading       by remember { mutableStateOf(true) }
    var searchQuery   by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    /* ── Floating orb animations ── */
    val inf = rememberInfiniteTransition(label = "bg")
    val orb1Alpha  by inf.animateFloat(0.05f, 0.13f,
        infiniteRepeatable(tween(4000, easing = EaseInOutSine), RepeatMode.Reverse), "o1a")
    val orb2Alpha  by inf.animateFloat(0.04f, 0.10f,
        infiniteRepeatable(tween(5500, easing = EaseInOutSine), RepeatMode.Reverse), "o2a")
    val orb1Y      by inf.animateFloat(0f, 28f,
        infiniteRepeatable(tween(6200, easing = EaseInOutSine), RepeatMode.Reverse), "o1y")
    val orb2Y      by inf.animateFloat(0f, -22f,
        infiniteRepeatable(tween(7100, easing = EaseInOutSine), RepeatMode.Reverse), "o2y")

    /* ── Load friends ── */
    suspend fun loadFriends() {
        loading = true
        try {
            val snap = db.collection("users").document(currentUid)
                .collection("friends").get().await()
            val list = mutableListOf<Friend>()
            for (doc in snap.documents) {
                val uid   = doc.id
                val uDoc  = db.collection("users").document(uid).get().await()
                list.add(Friend(uid, uDoc.getString("username") ?: "User", uDoc.getString("email") ?: ""))
            }
            friends = list.sortedBy { it.username.lowercase() }
        } catch (_: Exception) {
            snackbarHostState.showSnackbar("❌ Failed to load friends. Please try again.")
        } finally { loading = false }
    }

    LaunchedEffect(Unit) { loadFriends() }

    fun removeFriend(friend: Friend) = scope.launch {
        try {
            db.collection("users").document(currentUid)
                .collection("friends").document(friend.uid).delete().await()
            loadFriends()
            snackbarHostState.showSnackbar("✓ ${friend.username} removed")
        } catch (_: Exception) {
            snackbarHostState.showSnackbar("❌ Could not remove friend. Try again.")
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData   = data,
                    modifier       = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    shape          = RoundedCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor   = MaterialTheme.colorScheme.inverseOnSurface,
                    actionColor    = MaterialTheme.colorScheme.inversePrimary
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Friends", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(10.dp))
                        AnimatedContent(
                            targetState = friends.size,
                            transitionSpec = {
                                (slideInVertically { -it } + fadeIn())
                                    .togetherWith(slideOutVertically { it } + fadeOut())
                            }, label = "count"
                        ) { count ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    "$count / 10",
                                    Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold, fontSize = 12.sp
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    FilledIconButton(
                        onClick  = onBack,
                        colors   = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                            tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = !loading,
                enter = scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) + fadeIn(),
                exit  = scaleOut(tween(150)) + fadeOut(tween(150))
            ) {
                ExtendedFloatingActionButton(
                    onClick        = { showAddDialog = true },
                    icon           = { Icon(Icons.Default.PersonAdd, null) },
                    text           = { Text("Add Friend", fontWeight = FontWeight.Bold) },
                    expanded       = searchQuery.isEmpty(),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor   = MaterialTheme.colorScheme.onPrimary,
                    elevation      = FloatingActionButtonDefaults.elevation(8.dp)
                )
            }
        },
        containerColor = Color.Transparent
    ) { padding ->

        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

            /* ── Background orbs — themed ── */
            Box(Modifier.size(380.dp).offset((-100).dp, (60 + orb1Y).dp).blur(110.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = orb1Alpha), CircleShape))
            Box(Modifier.size(300.dp).offset(220.dp, (420 + orb2Y).dp).blur(100.dp)
                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = orb2Alpha), CircleShape))
            Box(Modifier.size(200.dp).offset(140.dp, 200.dp).blur(80.dp)
                .background(MaterialTheme.colorScheme.secondary.copy(
                    alpha = if (isDark) 0.05f else 0.04f), CircleShape))

            PullToRefreshBox(
                isRefreshing = loading,
                onRefresh    = { scope.launch { loadFriends() } },
                modifier     = Modifier.fillMaxSize().padding(padding)
            ) {
                Column(Modifier.fillMaxSize()) {
                    FriendsSearchBar(
                        query    = searchQuery,
                        onQuery  = { searchQuery = it },
                        onClear  = { searchQuery = "" },
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )

                    val filtered = friends.filter {
                        it.username.contains(searchQuery, ignoreCase = true) ||
                                it.email.contains(searchQuery, ignoreCase = true)
                    }

                    AnimatedContent(
                        targetState = when {
                            loading            -> "loading"
                            filtered.isEmpty() -> "empty"
                            else               -> "list"
                        },
                        transitionSpec = { fadeIn(tween(350)).togetherWith(fadeOut(tween(250))) },
                        label          = "content"
                    ) { state ->
                        when (state) {
                            "loading" -> FriendsLoadingState()
                            "empty"   -> FriendsEmptyState(hasSearch = searchQuery.isNotEmpty())
                            else      -> FriendList(
                                friends       = filtered,
                                onOpenProfile = openProfile,
                                onDelete      = { removeFriend(it) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddFriendDialog(
            currentFriendsCount = friends.size,
            existingUids        = friends.map { it.uid }.toSet(),
            currentUid          = currentUid,
            db                  = db,
            onDismiss           = { showAddDialog = false },
            onSuccess           = { username ->
                showAddDialog = false
                scope.launch {
                    snackbarHostState.showSnackbar("✓ $username added as a friend!")
                    loadFriends()
                }
            },
            onError = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
        )
    }
}

/* ══════════════════════════════════════════════════════
   SEARCH BAR
══════════════════════════════════════════════════════ */
@Composable
fun FriendsSearchBar(
    query: String, onQuery: (String) -> Unit,
    onClear: () -> Unit, modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value           = query,
        onValueChange   = onQuery,
        modifier        = modifier,
        placeholder     = {
            Text("Search by name or email…",
                color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f),
                fontSize = 14.sp)
        },
        singleLine    = true,
        shape         = RoundedCornerShape(20.dp),
        leadingIcon   = {
            Icon(Icons.Default.Search, null,
                tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp))
        },
        trailingIcon  = {
            AnimatedVisibility(query.isNotEmpty(),
                enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit  = scaleOut(tween(120)) + fadeOut(tween(100))
            ) {
                IconButton(onClick = { onClear(); focusManager.clearFocus() }) {
                    Icon(Icons.Default.Close, "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, keyboardType = KeyboardType.Text),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor    = MaterialTheme.colorScheme.outlineVariant,
            focusedBorderColor      = MaterialTheme.colorScheme.primary,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedContainerColor   = MaterialTheme.colorScheme.surfaceContainerLow,
            cursorColor             = MaterialTheme.colorScheme.primary
        ),
        textStyle = LocalTextStyle.current.copy(
            fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
    )
}

/* ══════════════════════════════════════════════════════
   FRIENDS LIST
══════════════════════════════════════════════════════ */
@Composable
fun FriendList(
    friends: List<Friend>,
    onOpenProfile: (String) -> Unit,
    onDelete: (Friend) -> Unit
) {
    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(friends, key = { _, f -> f.uid }) { index, friend ->
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(friend.uid) { delay(index * 55L); visible = true }

            AnimatedVisibility(
                visible = visible,
                enter = slideInHorizontally(
                    animationSpec = tween(300),
                    initialOffsetX = { -(it * 0.4f).toInt() }
                ) + fadeIn(tween(220))
            ) {
                SwipeToDeleteFriend(onDelete = { onDelete(friend) }) {
                    FriendCard(friend = friend, onClick = { onOpenProfile(friend.uid) })
                }
            }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

/* ══════════════════════════════════════════════════════
   FRIEND CARD
══════════════════════════════════════════════════════ */
private val AvatarPalette = listOf(
    Color(0xFF6366F1), Color(0xFF8B5CF6), Color(0xFFEC4899),
    Color(0xFF06B6D4), Color(0xFF10B981), Color(0xFFF59E0B),
    Color(0xFFEF4444), Color(0xFF3B82F6)
)

@Composable
fun FriendCard(friend: Friend, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed           by interactionSource.collectIsPressedAsState()
    val isDark = isSystemInDarkTheme()

    val scale by animateFloatAsState(
        if (pressed) 0.965f else 1f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "scale")
    val elevation by animateFloatAsState(
        if (pressed) 8f else 2f, tween(150), label = "elev")

    val avatarColor = AvatarPalette[
        friend.uid.hashCode().let { if (it < 0) -it else it } % AvatarPalette.size
    ]

    Card(
        modifier  = Modifier.fillMaxWidth().scale(scale).clickable(
            interactionSource = interactionSource,
            indication        = LocalIndication.current,
            onClick           = onClick
        ),
        shape     = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(elevation.dp),
        colors    = CardDefaults.cardColors(
            containerColor = if (isDark)
                MaterialTheme.colorScheme.surfaceContainerHigh
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(50.dp).clip(CircleShape)
                    .background(Brush.linearGradient(
                        listOf(avatarColor.copy(0.75f), avatarColor, avatarColor.copy(0.90f))
                    )),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    friend.username.firstOrNull()?.uppercase() ?: "?",
                    color = Color.White, fontWeight = FontWeight.ExtraBold,
                    fontSize = 19.sp, letterSpacing = (-0.5).sp
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(friend.username, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (friend.email.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(friend.email, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
                modifier = Modifier.size(20.dp))
        }
    }
}

/* ══════════════════════════════════════════════════════
   LOADING STATE — structural shimmer skeletons
══════════════════════════════════════════════════════ */
@Composable
fun FriendsLoadingState() {
    val isDark = isSystemInDarkTheme()
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(6) { i ->
            val shimmer by rememberInfiniteTransition("s$i").animateFloat(
                if (isDark) 0.18f else 0.45f, if (isDark) 0.40f else 0.75f,
                infiniteRepeatable(tween(900, i * 110, EaseInOutSine), RepeatMode.Reverse), "sa$i"
            )
            Card(
                Modifier.fillMaxWidth().height(78.dp).alpha(shimmer),
                shape  = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark)
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    else
                        MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outline.copy(0.25f)))
                    Spacer(Modifier.width(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.width(120.dp).height(13.dp).clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.outline.copy(0.20f)))
                        Box(Modifier.width(80.dp).height(10.dp).clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.outline.copy(0.15f)))
                    }
                }
            }
        }
    }
}

/* ══════════════════════════════════════════════════════
   EMPTY STATE
══════════════════════════════════════════════════════ */
@Composable
fun FriendsEmptyState(hasSearch: Boolean) {
    val pulse by rememberInfiniteTransition("pulse").animateFloat(
        0.93f, 1.07f,
        infiniteRepeatable(tween(1800, easing = EaseInOutSine), RepeatMode.Reverse), "ps"
    )
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier            = Modifier.padding(40.dp)
        ) {
            Box(
                Modifier.size(96.dp).scale(pulse).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                Alignment.Center
            ) {
                Icon(
                    if (hasSearch) Icons.Default.SearchOff else Icons.Default.Group,
                    null, Modifier.size(46.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Text(
                if (hasSearch) "No Results Found" else "No Friends Yet",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                if (hasSearch) "Try searching with a different name or email"
                else "Tap the + button to find and add your first friend",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
            )
        }
    }
}

/* ══════════════════════════════════════════════════════
   SWIPE TO DELETE
══════════════════════════════════════════════════════ */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDeleteFriend(onDelete: () -> Unit, content: @Composable () -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            if (v == SwipeToDismissBoxValue.EndToStart) { showConfirm = true; false } else false
        }
    )

    SwipeToDismissBox(
        state                       = state,
        enableDismissFromStartToEnd = false,
        backgroundContent           = {
            val progress by animateFloatAsState(
                if (state.targetValue == SwipeToDismissBoxValue.EndToStart) 1f else 0f,
                spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium), label = "dp"
            )
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp))
                    .background(Brush.horizontalGradient(
                        0f   to MaterialTheme.colorScheme.errorContainer.copy(0f),
                        0.4f to MaterialTheme.colorScheme.error.copy(progress * 0.5f),
                        1f   to MaterialTheme.colorScheme.error.copy(progress)
                    ))
                    .padding(end = 26.dp),
                Alignment.CenterEnd
            ) {
                Icon(Icons.Default.DeleteOutline, "Delete",
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(28.dp).graphicsLayer {
                        alpha  = progress
                        scaleX = 0.6f + progress * 0.4f
                        scaleY = 0.6f + progress * 0.4f
                    })
            }
        },
        content = { content() }
    )

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            shape            = RoundedCornerShape(28.dp),
            containerColor   = MaterialTheme.colorScheme.surface,
            icon = {
                Box(Modifier.size(56.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer), Alignment.Center) {
                    Icon(Icons.Default.PersonRemove, null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(26.dp))
                }
            },
            title = {
                Text("Remove Friend?", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            },
            text = {
                Text("They'll be removed from your list. You can always add them back later.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            confirmButton = {
                Button(
                    onClick  = { showConfirm = false; onDelete() },
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor   = MaterialTheme.colorScheme.onError),
                    shape    = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PersonRemove, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Remove", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick  = { showConfirm = false },
                    shape    = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Keep Friend") }
            }
        )
    }
}

/* ══════════════════════════════════════════════════════
   ADD FRIEND DIALOG
══════════════════════════════════════════════════════ */
@Composable
fun AddFriendDialog(
    currentFriendsCount: Int, existingUids: Set<String>,
    currentUid: String, db: FirebaseFirestore,
    onDismiss: () -> Unit, onSuccess: (String) -> Unit, onError: (String) -> Unit
) {
    val scope      = rememberCoroutineScope()
    var input      by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
    var searching  by remember { mutableStateOf(false) }
    val slotsLeft  = 10 - currentFriendsCount

    fun validate() = when {
        input.isBlank()  -> { inputError = "Please enter a username or email"; false }
        input.length < 2 -> { inputError = "Must be at least 2 characters"; false }
        input.contains("@") &&
                !android.util.Patterns.EMAIL_ADDRESS.matcher(input).matches() ->
        { inputError = "Enter a valid email address"; false }
        else -> { inputError = null; true }
    }

    fun doAdd() {
        if (!validate()) return
        if (currentFriendsCount >= 10) { onError("⚠️ Friend limit reached (10 max)."); return }
        scope.launch {
            searching = true
            try {
                val query  = if (input.contains("@"))
                    db.collection("users").whereEqualTo("email", input.trim().lowercase())
                else
                    db.collection("users").whereEqualTo("username_lower", input.trim().lowercase())
                val result = query.get().await()
                if (result.isEmpty) { inputError = "No user found with that username or email"; return@launch }
                val doc       = result.documents.first()
                val friendUid = doc.id
                val username  = doc.getString("username") ?: "User"
                when {
                    friendUid == currentUid           -> inputError = "You can't add yourself 😄"
                    existingUids.contains(friendUid)  -> inputError = "$username is already your friend"
                    else -> {
                        db.collection("users").document(currentUid)
                            .collection("friends").document(friendUid)
                            .set(mapOf("addedAt" to System.currentTimeMillis())).await()
                        onSuccess(username)
                    }
                }
            } catch (_: Exception) {
                onError("❌ Something went wrong. Check your connection.")
            } finally { searching = false }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!searching) onDismiss() },
        shape            = RoundedCornerShape(28.dp),
        containerColor   = MaterialTheme.colorScheme.surface,
        icon = {
            Box(Modifier.size(56.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer), Alignment.Center) {
                Icon(Icons.Default.PersonAdd, null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(27.dp))
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Add a Friend", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (slotsLeft <= 2) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        "$slotsLeft slot${if (slotsLeft == 1) "" else "s"} remaining",
                        Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        style      = MaterialTheme.typography.labelSmall,
                        color      = if (slotsLeft <= 2) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value           = input,
                    onValueChange   = { input = it; inputError = null },
                    label           = { Text("Username or Email") },
                    singleLine      = true,
                    isError         = inputError != null,
                    supportingText  = {
                        AnimatedVisibility(inputError != null) {
                            Text(inputError ?: "", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    },
                    leadingIcon  = {
                        Icon(
                            if (input.contains("@")) Icons.Default.Email else Icons.Default.AlternateEmail,
                            null,
                            tint = if (inputError != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (input.isNotEmpty()) {
                            IconButton(onClick = { input = ""; inputError = null }) {
                                Icon(Icons.Default.Close, "Clear", Modifier.size(18.dp))
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Text),
                    keyboardActions = KeyboardActions(onDone = { doAdd() }),
                    shape    = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors   = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor    = MaterialTheme.colorScheme.outlineVariant,
                        focusedBorderColor      = MaterialTheme.colorScheme.primary,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedContainerColor   = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                )

                AnimatedVisibility(
                    visible = searching,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary)
                        Text("Looking up user…", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = { doAdd() },
                enabled  = !searching,
                shape    = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = searching,
                    transitionSpec = { fadeIn().togetherWith(fadeOut()) },
                    label = "btn"
                ) { busy ->
                    if (busy) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary)
                            Text("Adding…", fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.PersonAdd, null, Modifier.size(17.dp))
                            Text("Add Friend", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick  = { if (!searching) onDismiss() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    )
}

