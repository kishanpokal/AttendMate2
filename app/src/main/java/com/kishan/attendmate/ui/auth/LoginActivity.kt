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
import com.kishan.attendmate.ui.theme.RadiusLG
import com.kishan.attendmate.ui.theme.RadiusMD
import com.kishan.attendmate.ui.theme.ElevationLow
import com.kishan.attendmate.ui.theme.CardStyle
import com.kishan.attendmate.ui.theme.AttendMateTheme
import com.kishan.attendmate.ui.theme.authBackgroundBrush
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
            AttendMateTheme {
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
                .background(authBackgroundBrush())
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
                Spacer(Modifier.height((24 * verticalSpacingMultiplier).dp))

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

    @Composable
    private fun GoogleSignInButton(
        loading: Boolean,
        onClick: () -> Unit,
        isCompact: Boolean
    ) {
        com.kishan.attendmate.ui.components.SecondaryButton(
            text = "Continue with Google",
            onClick = onClick,
            isLoading = loading,
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_google),
                    contentDescription = "Google Logo",
                    modifier = Modifier.size(24.dp),
                    tint = Color.Unspecified
                )
            }
        )
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