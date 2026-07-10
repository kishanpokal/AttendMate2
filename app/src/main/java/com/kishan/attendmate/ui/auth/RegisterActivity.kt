package com.kishan.attendmate.ui.auth

import com.kishan.attendmate.ui.theme.statusColors

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kishan.attendmate.ui.theme.RadiusLG
import com.kishan.attendmate.ui.theme.RadiusMD
import com.kishan.attendmate.ui.theme.ElevationLow
import com.kishan.attendmate.ui.theme.CardStyle
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class RegisterActivity : ComponentActivity() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            ) {
                EnhancedRegisterScreen()
            }
        }
    }

    @Composable
    private fun EnhancedRegisterScreen() {
        var email by remember { mutableStateOf("") }
        var username by remember { mutableStateOf("") }
        var fullName by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var confirmPassword by remember { mutableStateOf("") }

        var passwordVisible by remember { mutableStateOf(false) }
        var confirmVisible by remember { mutableStateOf(false) }
        var loading by remember { mutableStateOf(false) }

        var emailError by remember { mutableStateOf<String?>(null) }
        var usernameError by remember { mutableStateOf<String?>(null) }
        var fullNameError by remember { mutableStateOf<String?>(null) }
        var passwordError by remember { mutableStateOf<String?>(null) }
        var confirmPasswordError by remember { mutableStateOf<String?>(null) }

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
            screenWidth < 360.dp -> 80.dp
            screenWidth < 600.dp -> 100.dp
            else -> 120.dp
        }

        val verticalSpacingMultiplier = if (isLandscape) 0.5f else 1f

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
                                Color(0xFF1F1C3F),
                                Color(0xFF14142B)
                            )
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
                Spacer(Modifier.height((if (isLandscape) 80.dp else 24.dp) * verticalSpacingMultiplier))

                // Premium App Icon
                AnimatedVisibility(
                    visible = !isLandscape || screenHeight > 500.dp,
                    enter = fadeIn(tween(800)) + scaleIn(tween(800, easing = FastOutSlowInEasing)),
                    exit = fadeOut() + scaleOut()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.scale(scale)
                    ) {
                        // Main icon container
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            tonalElevation = ElevationLow,
                            shadowElevation = ElevationLow,
                            modifier = Modifier
                                .size(iconSize)
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
                                    Icons.Default.PersonAdd,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(iconSize * 0.5f)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height((if (isLandscape) 16.dp else 28.dp) * verticalSpacingMultiplier))

                // Animated Title
                AnimatedText(
                    text = "Create Account",
                    isDark = isDark,
                    isCompact = isCompact
                )

                Spacer(Modifier.height((12 * verticalSpacingMultiplier).dp))

                Text(
                    text = "Join us and start tracking your attendance",
                    style = if (isCompact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .widthIn(max = contentMaxWidth)
                        .animateContentSize()
                )

                Spacer(Modifier.height((if (isLandscape) 16.dp else 28.dp) * verticalSpacingMultiplier))

                // Premium Registration Form Card
                Surface(
                    shape = CardStyle.shape,
                    color = CardStyle.containerColor(),
                    border = CardStyle.border(),
                    shadowElevation = CardStyle.elevation,
                    modifier = Modifier
                        .widthIn(max = contentMaxWidth)
                        .fillMaxWidth()
                        .animateContentSize()
                ) {
                    Box {

                        Column(
                            modifier = Modifier.padding(
                                horizontal = if (isCompact) 24.dp else 28.dp,
                                vertical = if (isCompact) 24.dp else 28.dp
                            )
                        ) {
                            // Full Name Input
                            AnimatedTextField(
                                value = fullName,
                                onValueChange = {
                                    fullName = it
                                    fullNameError = null
                                },
                                label = "Full Name",
                                placeholder = "Enter your full name",
                                leadingIcon = Icons.Default.Badge,
                                error = fullNameError,
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Next,
                                    keyboardType = KeyboardType.Text
                                ),
                                isCompact = isCompact
                            )

                            Spacer(Modifier.height(18.dp))

                            // Username Input
                            AnimatedTextField(
                                value = username,
                                onValueChange = {
                                    username = it
                                    usernameError = null
                                },
                                label = "Username",
                                placeholder = "Choose a username",
                                leadingIcon = Icons.Default.Person,
                                error = usernameError,
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Next,
                                    keyboardType = KeyboardType.Text
                                ),
                                isCompact = isCompact
                            )

                            Spacer(Modifier.height(18.dp))

                            // Email Input
                            AnimatedTextField(
                                value = email,
                                onValueChange = {
                                    email = it
                                    emailError = null
                                },
                                label = "Email Address",
                                placeholder = "Enter your email",
                                leadingIcon = Icons.Default.Email,
                                error = emailError,
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Next,
                                    keyboardType = KeyboardType.Email
                                ),
                                isCompact = isCompact
                            )

                            Spacer(Modifier.height(18.dp))

                            // Password Input
                            AnimatedPasswordField(
                                value = password,
                                onValueChange = {
                                    password = it
                                    passwordError = null
                                },
                                error = passwordError,
                                passwordVisible = passwordVisible,
                                onVisibilityToggle = { passwordVisible = !passwordVisible },
                                label = "Password",
                                placeholder = "Create a password",
                                onNext = { },
                                isCompact = isCompact
                            )

                            Spacer(Modifier.height(18.dp))

                            // Confirm Password Input
                            AnimatedPasswordField(
                                value = confirmPassword,
                                onValueChange = {
                                    confirmPassword = it
                                    confirmPasswordError = null
                                },
                                error = confirmPasswordError,
                                passwordVisible = confirmVisible,
                                onVisibilityToggle = { confirmVisible = !confirmVisible },
                                label = "Confirm Password",
                                placeholder = "Re-enter your password",
                                onNext = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    validateAndRegister(
                                        email, username, fullName, password, confirmPassword,
                                        { emailError = it }, { usernameError = it },
                                        { fullNameError = it }, { passwordError = it },
                                        { confirmPasswordError = it }
                                    ) { loading = it }
                                },
                                isCompact = isCompact,
                                isDone = true
                            )

                            Spacer(Modifier.height(20.dp))

                            // Password Requirements Info
                            PasswordRequirements(password, isDark, isCompact)

                            Spacer(Modifier.height(24.dp))

                            // Premium Create Account Button
                            PremiumButton(
                                text = "Create Account",
                                icon = Icons.Default.PersonAdd,
                                loading = loading,
                                onClick = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    validateAndRegister(
                                        email, username, fullName, password, confirmPassword,
                                        { emailError = it }, { usernameError = it },
                                        { fullNameError = it }, { passwordError = it },
                                        { confirmPasswordError = it }
                                    ) { loading = it }
                                },
                                isCompact = isCompact
                            )
                        }
                    }
                }

                Spacer(Modifier.height((if (isLandscape) 16.dp else 28.dp) * verticalSpacingMultiplier))

                // Sign In Link with animation
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .widthIn(max = contentMaxWidth)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            startActivity(Intent(this@RegisterActivity, LoginActivity::class.java))
                            finish()
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
                            "Already have an account? ",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = if (isCompact) 14.sp else 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Sign In",
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

    @Composable
    private fun PasswordRequirements(password: String, isDark: Boolean, isCompact: Boolean) {
        val hasMinLength = password.length >= 6
        val hasUpperCase = password.any { it.isUpperCase() }
        val hasNumber = password.any { it.isDigit() }

        Surface(
            shape = RoundedCornerShape(RadiusLG),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = if (isCompact) 16.dp else 18.dp,
                    vertical = if (isCompact) 14.dp else 16.dp
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Password Requirements",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(12.dp))

                PasswordRequirementItem("At least 6 characters", hasMinLength)
                PasswordRequirementItem("One uppercase letter (recommended)", hasUpperCase)
                PasswordRequirementItem("One number (recommended)", hasNumber)
            }
        }
    }

    @Composable
    private fun PasswordRequirementItem(text: String, met: Boolean) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            AnimatedContent(
                targetState = met,
                transitionSpec = {
                    fadeIn() + scaleIn() togetherWith fadeOut() + scaleOut()
                },
                label = "check"
            ) { isMet ->
                Icon(
                    if (isMet) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isMet) statusColors().success else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = if (met) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
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
            shape = RoundedCornerShape(RadiusLG),
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
        label: String,
        placeholder: String,
        onNext: () -> Unit,
        isCompact: Boolean,
        isDone: Boolean = false
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
                imeAction = if (isDone) ImeAction.Done else ImeAction.Next,
                keyboardType = KeyboardType.Password
            ),
            keyboardActions = if (isDone) KeyboardActions { onNext() } else KeyboardActions.Default,
            shape = RoundedCornerShape(RadiusLG),
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
        com.kishan.attendmate.ui.components.PrimaryButton(
            text = text,
            onClick = onClick,
            isLoading = loading,
            icon = {
                Icon(
                    icon,
                    contentDescription = text,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        )
    }

    // Validation and Registration Functions
    private fun validateAndRegister(
        email: String,
        username: String,
        fullName: String,
        password: String,
        confirmPassword: String,
        onEmailError: (String?) -> Unit,
        onUsernameError: (String?) -> Unit,
        onFullNameError: (String?) -> Unit,
        onPasswordError: (String?) -> Unit,
        onConfirmPasswordError: (String?) -> Unit,
        onLoading: (Boolean) -> Unit
    ) {
        var hasError = false

        if (fullName.isBlank()) {
            onFullNameError("Full name is required")
            hasError = true
        } else {
            onFullNameError(null)
        }

        if (username.isBlank()) {
            onUsernameError("Username is required")
            hasError = true
        } else if (username.length < 3) {
            onUsernameError("Username must be at least 3 characters")
            hasError = true
        } else {
            onUsernameError(null)
        }

        if (email.isBlank()) {
            onEmailError("Email is required")
            hasError = true
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            onEmailError("Invalid email address")
            hasError = true
        } else {
            onEmailError(null)
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

        if (confirmPassword.isBlank()) {
            onConfirmPasswordError("Please confirm your password")
            hasError = true
        } else if (password != confirmPassword) {
            onConfirmPasswordError("Passwords do not match")
            hasError = true
        } else {
            onConfirmPasswordError(null)
        }

        if (hasError) return

        registerUser(email.trim(), username.trim(), fullName.trim(), password, onLoading)
    }

    private fun registerUser(
        email: String,
        username: String,
        fullName: String,
        password: String,
        onLoading: (Boolean) -> Unit
    ) {
        onLoading(true)

        // First check if username already exists
        db.collection("users")
            .whereEqualTo("username", username.lowercase())
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    onLoading(false)
                    Toast.makeText(this, "Username already taken", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                // Create Firebase Auth account
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener { authResult ->
                        val user = authResult.user ?: return@addOnSuccessListener
                        val uid = user.uid

                        // Save user profile in Firestore
                        val userData = hashMapOf(
                            "uid" to uid,
                            "email" to email.lowercase(),
                            "username" to username.lowercase(),
                            "fullName" to fullName,
                            "createdAt" to System.currentTimeMillis(),
                            "setupDone" to false
                        )

                        db.collection("users").document(uid)
                            .set(userData)
                            .addOnSuccessListener {
                                // Send verification email
                                user.sendEmailVerification()
                                    .addOnCompleteListener {
                                        auth.signOut()
                                        onLoading(false)

                                        Toast.makeText(
                                            this,
                                            "Verification email sent! Please verify and login.",
                                            Toast.LENGTH_LONG
                                        ).show()

                                        startActivity(
                                            Intent(this@RegisterActivity, LoginActivity::class.java)
                                        )
                                        finish()
                                    }
                            }
                            .addOnFailureListener { e ->
                                onLoading(false)
                                Toast.makeText(
                                    this,
                                    "Failed to save user data: ${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                    }
                    .addOnFailureListener { e ->
                        onLoading(false)
                        val errorMessage = when {
                            e.message?.contains("email address is already in use") == true ->
                                "Email already registered. Please sign in."
                            e.message?.contains("network") == true ->
                                "Network error. Please check your connection."
                            else -> e.message ?: "Registration failed"
                        }
                        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                onLoading(false)
                Toast.makeText(
                    this,
                    "Error checking username: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }
}