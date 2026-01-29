package com.kishan.attendmate.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.*
import com.google.firebase.firestore.FirebaseFirestore
import com.kishan.attendmate.MainActivity
import com.kishan.attendmate.R
import com.kishan.attendmate.ui.setup.SubjectSetupActivity
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class LoginActivity : ComponentActivity() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private lateinit var googleClient: GoogleSignInClient

    private val googleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK || result.data == null) {
            Toast.makeText(this, "Google sign-in cancelled", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }

        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            handleGoogleCredential(credential, account.email)
        } catch (e: Exception) {
            Toast.makeText(this, "Google sign-in failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        googleClient = GoogleSignIn.getClient(
            this,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
        )

        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            ) {
                EnhancedLoginScreen()
            }
        }
    }

    @Composable
    private fun EnhancedLoginScreen() {
        var input by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var passwordVisible by remember { mutableStateOf(false) }
        var loading by remember { mutableStateOf(false) }
        var inputError by remember { mutableStateOf<String?>(null) }
        var passwordError by remember { mutableStateOf<String?>(null) }

        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current
        val isDark = isSystemInDarkTheme()
        val scope = rememberCoroutineScope()
        val configuration = LocalConfiguration.current

        // Responsive calculations
        val screenWidth = configuration.screenWidthDp.dp
        val screenHeight = configuration.screenHeightDp.dp
        val isCompact = screenWidth < 600.dp
        val isLandscape = screenWidth > screenHeight

        val horizontalPadding = when {
            screenWidth < 360.dp -> 16.dp
            screenWidth < 600.dp -> 24.dp
            screenWidth < 840.dp -> 48.dp
            else -> min(screenWidth.value * 0.15f, 120f).dp
        }

        val contentMaxWidth = when {
            screenWidth < 600.dp -> screenWidth
            screenWidth < 840.dp -> 600.dp
            else -> 480.dp
        }

        val iconSize = when {
            screenWidth < 360.dp -> 90.dp
            screenWidth < 600.dp -> 110.dp
            else -> 130.dp
        }

        val verticalSpacingMultiplier = if (isLandscape) 0.6f else 1f

        // Advanced Animations
        val infiniteTransition = rememberInfiniteTransition(label = "background")

        val gradientOffset by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1000f,
            animationSpec = infiniteRepeatable(
                animation = tween(15000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "gradient"
        )

        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(20000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        )

        val scale by animateFloatAsState(
            targetValue = if (loading) 0.97f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "scale"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isDark) {
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF0F0C29),
                                Color(0xFF302B63),
                                Color(0xFF24243E)
                            ),
                            start = Offset(gradientOffset, gradientOffset),
                            end = Offset(gradientOffset + 1000f, gradientOffset + 1000f)
                        )
                    } else {
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFF8F9FF),
                                Color(0xFFEEF2FF),
                                Color(0xFFE0E7FF)
                            )
                        )
                    }
                )
        ) {
            // Advanced Floating Orbs
            AdvancedFloatingOrbs(isDark, isCompact, rotation)

            // Animated particles
            AnimatedParticles(isDark)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = horizontalPadding)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (isLandscape) Arrangement.Top else Arrangement.Center
            ) {
                Spacer(Modifier.height((24 * verticalSpacingMultiplier).dp))

                // Premium App Icon with Glow
                AnimatedVisibility(
                    visible = !isLandscape || screenHeight > 500.dp,
                    enter = fadeIn(tween(800)) + scaleIn(tween(800, easing = FastOutSlowInEasing)),
                    exit = fadeOut() + scaleOut()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.scale(scale)
                    ) {
                        // Rotating glow rings
                        Canvas(modifier = Modifier.size(iconSize + 60.dp)) {
                            val centerX = size.width / 2
                            val centerY = size.height / 2

                            for (i in 0..2) {
                                val angle = (rotation + i * 120) * Math.PI / 180
                                val radius = size.width / 3
                                val x = (centerX + cos(angle) * radius * 0.3).toFloat()
                                val y = (centerY + sin(angle) * radius * 0.3).toFloat()

                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            Color(0xFF6366F1).copy(alpha = 0.3f),
                                            Color.Transparent
                                        )
                                    ),
                                    radius = 50f,
                                    center = Offset(x, y)
                                )
                            }
                        }

                        // Outer pulsing ring
                        val pulseScale by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(2000, easing = EaseInOutCubic),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulse"
                        )

                        Box(
                            modifier = Modifier
                                .size((iconSize + 40.dp) * pulseScale)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            Color(0xFF6366F1).copy(alpha = 0.3f),
                                            Color.Transparent
                                        )
                                    )
                                )
                                .blur(25.dp)
                        )

                        // Main icon container
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            tonalElevation = 12.dp,
                            shadowElevation = 20.dp,
                            modifier = Modifier
                                .size(iconSize)
                                .shadow(
                                    elevation = 30.dp,
                                    shape = CircleShape,
                                    ambientColor = Color(0xFF6366F1).copy(alpha = 0.5f),
                                    spotColor = Color(0xFF6366F1).copy(alpha = 0.5f)
                                )
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            MaterialTheme.colorScheme.secondaryContainer
                                        )
                                    )
                                )
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(iconSize * 0.5f)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height((if (isLandscape) 20.dp else 32.dp) * verticalSpacingMultiplier))

                // Animated Title
                AnimatedText(
                    text = "Welcome Back",
                    isDark = isDark,
                    isCompact = isCompact
                )

                Spacer(Modifier.height((12 * verticalSpacingMultiplier).dp))

                Text(
                    text = "Sign in to continue tracking your attendance",
                    style = if (isCompact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .widthIn(max = contentMaxWidth)
                        .animateContentSize()
                )

                Spacer(Modifier.height((if (isLandscape) 20.dp else 32.dp) * verticalSpacingMultiplier))

                // Premium Login Form Card
                Surface(
                    shape = RoundedCornerShape(if (isCompact) 28.dp else 32.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.8f else 0.95f),
                    tonalElevation = 8.dp,
                    shadowElevation = 16.dp,
                    modifier = Modifier
                        .widthIn(max = contentMaxWidth)
                        .fillMaxWidth()
                        .animateContentSize()
                        .shadow(
                            elevation = 24.dp,
                            shape = RoundedCornerShape(if (isCompact) 28.dp else 32.dp),
                            ambientColor = Color(0xFF6366F1).copy(alpha = 0.2f),
                            spotColor = Color(0xFF6366F1).copy(alpha = 0.2f)
                        )
                ) {
                    Box {
                        // Gradient overlay
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFF6366F1),
                                            Color(0xFF8B5CF6),
                                            Color(0xFFEC4899)
                                        )
                                    )
                                )
                        )

                        Column(
                            modifier = Modifier.padding(
                                horizontal = if (isCompact) 24.dp else 28.dp,
                                vertical = if (isCompact) 28.dp else 32.dp
                            )
                        ) {
                            // Email/Username Input with animation
                            AnimatedTextField(
                                value = input,
                                onValueChange = {
                                    input = it
                                    inputError = null
                                },
                                label = "Email or Username",
                                placeholder = "Enter your email or username",
                                leadingIcon = Icons.Default.Person,
                                error = inputError,
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Next,
                                    keyboardType = KeyboardType.Email
                                ),
                                isCompact = isCompact
                            )

                            Spacer(Modifier.height(20.dp))

                            // Password Input with animation
                            AnimatedPasswordField(
                                value = password,
                                onValueChange = {
                                    password = it
                                    passwordError = null
                                },
                                error = passwordError,
                                passwordVisible = passwordVisible,
                                onVisibilityToggle = { passwordVisible = !passwordVisible },
                                onDone = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    validateAndLogin(input, password, { inputError = it }, { passwordError = it }) {
                                        loading = it
                                    }
                                },
                                isCompact = isCompact
                            )

                            Spacer(Modifier.height(12.dp))

                            // Forgot Password
                            TextButton(
                                onClick = {
                                    startActivity(Intent(this@LoginActivity, ForgotPasswordActivity::class.java))
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text(
                                    "Forgot Password?",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = if (isCompact) 13.sp else 14.sp
                                )
                            }

                            Spacer(Modifier.height(24.dp))

                            // Premium Sign In Button
                            PremiumButton(
                                text = "Sign In",
                                icon = Icons.AutoMirrored.Filled.Login,
                                loading = loading,
                                onClick = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    validateAndLogin(input, password, { inputError = it }, { passwordError = it }) {
                                        loading = it
                                    }
                                },
                                isCompact = isCompact
                            )

                            Spacer(Modifier.height(24.dp))

                            // Modern Divider
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(1.dp)
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                                )
                                            )
                                        )
                                )
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ) {
                                    Text(
                                        "OR",
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        letterSpacing = 1.sp
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(1.dp)
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(
                                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                                    Color.Transparent
                                                )
                                            )
                                        )
                                )
                            }

                            Spacer(Modifier.height(24.dp))

                            // Google Sign In Button
                            GoogleSignInButton(
                                loading = loading,
                                onClick = {
                                    scope.launch {
                                        googleClient.signOut().addOnCompleteListener {
                                            googleLauncher.launch(googleClient.signInIntent)
                                        }
                                    }
                                },
                                isCompact = isCompact
                            )
                        }
                    }
                }

                Spacer(Modifier.height((if (isLandscape) 20.dp else 32.dp) * verticalSpacingMultiplier))

                // Sign Up Link with animation
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .widthIn(max = contentMaxWidth)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            startActivity(Intent(this@LoginActivity, RegisterActivity::class.java))
                        }
                        .animateContentSize()
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = if (isCompact) 24.dp else 28.dp,
                            vertical = if (isCompact) 16.dp else 18.dp
                        ),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Don't have an account? ",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = if (isCompact) 14.sp else 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Sign Up",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isCompact) 14.sp else 15.sp
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(Modifier.height((if (isLandscape) 24.dp else 32.dp) * verticalSpacingMultiplier))
            }
        }
    }

    // Continue with helper composables in next part...

    @Composable
    private fun AdvancedFloatingOrbs(isDark: Boolean, isCompact: Boolean, rotation: Float) {
        val infiniteTransition = rememberInfiniteTransition(label = "orbs")

        val offset1 by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 150f,
            animationSpec = infiniteRepeatable(
                animation = tween(4000, easing = EaseInOutCubic),
                repeatMode = RepeatMode.Reverse
            ),
            label = "orb1"
        )

        val offset2 by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -120f,
            animationSpec = infiniteRepeatable(
                animation = tween(5000, easing = EaseInOutCubic),
                repeatMode = RepeatMode.Reverse
            ),
            label = "orb2"
        )

        val orbSize1 = if (isCompact) 200.dp else 280.dp
        val orbSize2 = if (isCompact) 250.dp else 350.dp

        Box(modifier = Modifier.fillMaxSize()) {
            // Orb 1 - Top Left
            Box(
                modifier = Modifier
                    .size(orbSize1)
                    .offset(x = (-80).dp + offset1.dp, y = 80.dp + offset1.dp)
                    .rotate(rotation)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                if (isDark) Color(0xFF6366F1).copy(alpha = 0.15f)
                                else Color(0xFF6366F1).copy(alpha = 0.08f),
                                Color.Transparent
                            )
                        )
                    )
                }
            }

            // Orb 2 - Bottom Right
            Box(
                modifier = Modifier
                    .size(orbSize2)
                    .align(Alignment.BottomEnd)
                    .offset(x = 80.dp + offset2.dp, y = (-60).dp)
                    .rotate(-rotation * 0.5f)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                if (isDark) Color(0xFFEC4899).copy(alpha = 0.15f)
                                else Color(0xFFEC4899).copy(alpha = 0.08f),
                                Color.Transparent
                            )
                        )
                    )
                }
            }

            // Orb 3 - Center
            Box(
                modifier = Modifier
                    .size(if (isCompact) 180.dp else 220.dp)
                    .align(Alignment.Center)
                    .offset(y = offset1.dp)
                    .rotate(rotation * 0.3f)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                if (isDark) Color(0xFF8B5CF6).copy(alpha = 0.12f)
                                else Color(0xFF8B5CF6).copy(alpha = 0.06f),
                                Color.Transparent
                            )
                        )
                    )
                }
            }
        }
    }

    @Composable
    private fun AnimatedParticles(isDark: Boolean) {
        val infiniteTransition = rememberInfiniteTransition(label = "particles")

        Box(modifier = Modifier.fillMaxSize()) {
            repeat(8) { index ->
                val offsetY by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 800f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = 8000 + (index * 500),
                            easing = LinearEasing
                        ),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "particle_$index"
                )

                val offsetX = (index * 100).dp
                val size = (8 + (index % 3) * 4).dp

                Box(
                    modifier = Modifier
                        .offset(x = offsetX, y = offsetY.dp)
                        .size(size)
                        .clip(CircleShape)
                        .background(
                            if (isDark) Color.White.copy(alpha = 0.05f)
                            else Color(0xFF6366F1).copy(alpha = 0.04f)
                        )
                        .blur(4.dp)
                )
            }
        }
    }

    @Composable
    private fun AnimatedText(text: String, isDark: Boolean, isCompact: Boolean) {
        var visible by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            visible = true
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(1000)) + slideInVertically(tween(1000)) { -50 }
        ) {
            Text(
                text = text,
                fontSize = when {
                    isCompact -> 32.sp
                    else -> 42.sp
                },
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.animateContentSize()
            )
        }
    }

// Add these composables and functions to your LoginActivity class

    @Composable
    private fun AnimatedTextField(
        value: String,
        onValueChange: (String) -> Unit,
        label: String,
        placeholder: String,
        leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
        error: String?,
        keyboardOptions: KeyboardOptions,
        isCompact: Boolean
    ) {
        var isFocused by remember { mutableStateOf(false) }

        val borderColor by animateColorAsState(
            targetValue = when {
                error != null -> MaterialTheme.colorScheme.error
                isFocused -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            },
            label = "border"
        )

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            leadingIcon = {
                Icon(
                    leadingIcon,
                    null,
                    tint = if (error != null) MaterialTheme.colorScheme.error
                    else if (isFocused) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                androidx.compose.animation.AnimatedVisibility(
                    visible = value.isNotEmpty(),
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            isError = error != null,
            supportingText = error?.let {
                {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + expandVertically()
                    ) {
                        Text(it)
                    }
                }
            },
            singleLine = true,
            keyboardOptions = keyboardOptions,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = borderColor,
                unfocusedBorderColor = borderColor,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
        )
    }

    @Composable
    private fun AnimatedPasswordField(
        value: String,
        onValueChange: (String) -> Unit,
        error: String?,
        passwordVisible: Boolean,
        onVisibilityToggle: () -> Unit,
        onDone: () -> Unit,
        isCompact: Boolean
    ) {
        var isFocused by remember { mutableStateOf(false) }

        val borderColor by animateColorAsState(
            targetValue = when {
                error != null -> MaterialTheme.colorScheme.error
                isFocused -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            },
            label = "border"
        )

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("Password") },
            placeholder = { Text("Enter your password") },
            leadingIcon = {
                Icon(
                    Icons.Default.Lock,
                    null,
                    tint = if (error != null) MaterialTheme.colorScheme.error
                    else if (isFocused) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                IconButton(onClick = onVisibilityToggle) {
                    Icon(
                        if (passwordVisible) Icons.Default.Visibility
                        else Icons.Default.VisibilityOff,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None
            else PasswordVisualTransformation(),
            isError = error != null,
            supportingText = error?.let {
                {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + expandVertically()
                    ) {
                        Text(it)
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done,
                keyboardType = KeyboardType.Password
            ),
            keyboardActions = KeyboardActions { onDone() },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = borderColor,
                unfocusedBorderColor = borderColor,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
        )
    }

    @Composable
    private fun PremiumButton(
        text: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        loading: Boolean,
        onClick: () -> Unit,
        isCompact: Boolean
    ) {
        val scale by animateFloatAsState(
            targetValue = if (loading) 0.95f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "scale"
        )

        Button(
            onClick = onClick,
            enabled = !loading,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent
            ),
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isCompact) 56.dp else 60.dp)
                .scale(scale)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF6366F1),
                                Color(0xFF8B5CF6),
                                Color(0xFFEC4899)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = loading,
                    transitionSpec = {
                        fadeIn(tween(300)) + scaleIn() togetherWith
                                fadeOut(tween(300)) + scaleOut()
                    },
                    label = "button_content"
                ) { isLoading ->
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp
                        )
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = if (isCompact) 16.sp else 17.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun GoogleSignInButton(
        loading: Boolean,
        onClick: () -> Unit,
        isCompact: Boolean
    ) {
        val scale by animateFloatAsState(
            targetValue = if (loading) 0.95f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "scale"
        )

        OutlinedButton(
            onClick = onClick,
            enabled = !loading,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isCompact) 56.dp else 60.dp)
                .scale(scale)
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_google),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color.Unspecified
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Continue with Google",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = if (isCompact) 15.sp else 16.sp
                )
            }
        }
    }

    // Validation and Login Functions (Keep your existing logic)
    private fun validateAndLogin(
        input: String,
        password: String,
        onInputError: (String?) -> Unit,
        onPasswordError: (String?) -> Unit,
        onLoading: (Boolean) -> Unit
    ) {
        var hasError = false

        if (input.isBlank()) {
            onInputError("Email or username is required")
            hasError = true
        } else {
            onInputError(null)
        }

        if (password.isBlank()) {
            onPasswordError("Password is required")
            hasError = true
        } else if (password.length < 6) {
            onPasswordError("Password must be at least 6 characters")
            hasError = true
        } else {
            onPasswordError(null)
        }

        if (hasError) return
        login(input.trim(), password, onLoading)
    }

    private fun login(
        input: String,
        password: String,
        onLoading: (Boolean) -> Unit
    ) {
        onLoading(true)

        if (Patterns.EMAIL_ADDRESS.matcher(input).matches()) {
            signInWithEmail(input, password, onLoading)
        } else {
            db.collection("users")
                .whereEqualTo("username", input)
                .limit(1)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.isEmpty) {
                        onLoading(false)
                        Toast.makeText(this, "Username not found", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }

                    val email = snapshot.documents[0].getString("email") ?: run {
                        onLoading(false)
                        Toast.makeText(this, "Account error", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }

                    signInWithEmail(email, password, onLoading)
                }
                .addOnFailureListener {
                    onLoading(false)
                    Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun signInWithEmail(email: String, password: String, onLoading: (Boolean) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                redirectAfterLogin()
            }
            .addOnFailureListener {
                onLoading(false)
                val errorMessage = when (it) {
                    is FirebaseAuthInvalidCredentialsException -> "Invalid email or password"
                    is FirebaseAuthInvalidUserException -> "No account found with this email"
                    else -> it.message ?: "Login failed"
                }
                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
            }
    }

    @Suppress("DEPRECATION")
    private fun handleGoogleCredential(credential: AuthCredential, email: String?) {
        if (email == null) {
            auth.signInWithCredential(credential)
                .addOnSuccessListener { redirectAfterLogin() }
                .addOnFailureListener {
                    Toast.makeText(this, "Google sign-in failed", Toast.LENGTH_LONG).show()
                }
            return
        }

        auth.fetchSignInMethodsForEmail(email)
            .addOnSuccessListener { result ->
                val methods = result.signInMethods ?: emptyList()

                if (methods.contains(EmailAuthProvider.EMAIL_PASSWORD_SIGN_IN_METHOD)) {
                    val currentUser = auth.currentUser
                    if (currentUser != null && !currentUser.isAnonymous) {
                        currentUser.linkWithCredential(credential)
                            .addOnSuccessListener { redirectAfterLogin() }
                            .addOnFailureListener { redirectAfterLogin() }
                    } else {
                        auth.signInWithCredential(credential)
                            .addOnSuccessListener { redirectAfterLogin() }
                            .addOnFailureListener {
                                Toast.makeText(this, "Authentication failed", Toast.LENGTH_LONG).show()
                            }
                    }
                } else {
                    auth.signInWithCredential(credential)
                        .addOnSuccessListener { redirectAfterLogin() }
                        .addOnFailureListener {
                            Toast.makeText(this, "Google sign-in failed", Toast.LENGTH_LONG).show()
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to check account", Toast.LENGTH_LONG).show()
            }
    }

    private fun redirectAfterLogin() {
        val user = auth.currentUser ?: return

        db.collection("users")
            .document(user.uid)
            .get()
            .addOnSuccessListener { doc ->
                val setupDone = doc.getBoolean("setupDone") ?: false

                if (!doc.exists()) {
                    db.collection("users")
                        .document(user.uid)
                        .set(
                            mapOf(
                                "email" to user.email,
                                "username" to (user.displayName ?: ""),
                                "setupDone" to false
                            )
                        )
                }

                val nextActivity = if (setupDone) MainActivity::class.java
                else SubjectSetupActivity::class.java

                startActivity(Intent(this, nextActivity))
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show()
            }
    }
} // End of LoginActivity class