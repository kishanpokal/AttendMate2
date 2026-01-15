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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
                LoginScreen()
            }
        }
    }

    @Composable
    private fun LoginScreen() {
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

        // Animated values
        val infiniteTransition = rememberInfiniteTransition(label = "background")
        val animatedOffset by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1000f,
            animationSpec = infiniteRepeatable(
                animation = tween(20000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "offset"
        )

        val scale by animateFloatAsState(
            targetValue = if (loading) 0.98f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
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
                            start = androidx.compose.ui.geometry.Offset(animatedOffset, animatedOffset),
                            end = androidx.compose.ui.geometry.Offset(
                                animatedOffset + 1000f,
                                animatedOffset + 1000f
                            )
                        )
                    } else {
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFFDFBFB),
                                Color(0xFFEBEDEE),
                                Color(0xFFF0F2F5)
                            )
                        )
                    }
                )
        ) {
            // Floating orbs decoration
            FloatingOrbs(isDark)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(Modifier.height(32.dp))

                // App Icon with animated glow
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.scale(scale)
                ) {
                    // Glow effect
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        modifier = Modifier
                            .size(120.dp)
                            .blur(20.dp)
                    ) {}

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        tonalElevation = 8.dp,
                        modifier = Modifier.size(100.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(50.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                // Title with animation
                Text(
                    text = "Welcome Back",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Sign in to continue tracking your attendance",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(40.dp))

                // Login Form Card
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.7f else 1f),
                    tonalElevation = if (isDark) 4.dp else 2.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp)
                    ) {
                        // Email/Username Input
                        OutlinedTextField(
                            value = input,
                            onValueChange = {
                                input = it
                                inputError = null
                            },
                            label = { Text("Email or Username") },
                            placeholder = { Text("Enter your email or username") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Person,
                                    null,
                                    tint = if (inputError != null) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingIcon = {
                                if (input.isNotEmpty()) {
                                    IconButton(onClick = { input = "" }) {
                                        Icon(
                                            Icons.Default.Clear,
                                            contentDescription = "Clear",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            isError = inputError != null,
                            supportingText = inputError?.let { { Text(it) } },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Next,
                                keyboardType = KeyboardType.Email
                            ),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(16.dp))

                        // Password Input
                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                passwordError = null
                            },
                            label = { Text("Password") },
                            placeholder = { Text("Enter your password") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Lock,
                                    null,
                                    tint = if (passwordError != null) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
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
                            isError = passwordError != null,
                            supportingText = passwordError?.let { { Text(it) } },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Done,
                                keyboardType = KeyboardType.Password
                            ),
                            keyboardActions = KeyboardActions {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                validateAndLogin(input, password, { inputError = it }, { passwordError = it }) {
                                    loading = it
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(8.dp))

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
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(Modifier.height(24.dp))

                        // Sign In Button
                        Button(
                            onClick = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                validateAndLogin(input, password, { inputError = it }, { passwordError = it }) {
                                    loading = it
                                }
                            },
                            enabled = !loading,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            if (loading) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Icon(
                                    Icons.Default.Login,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Sign In",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        // Divider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                            Text(
                                "OR",
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp
                            )
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                        }

                        Spacer(Modifier.height(24.dp))

                        // Google Sign In Button
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    googleClient.signOut().addOnCompleteListener {
                                        googleLauncher.launch(googleClient.signInIntent)
                                    }
                                }
                            },
                            enabled = !loading,
                            shape = RoundedCornerShape(16.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.5.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
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
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                // Sign Up Link
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            startActivity(Intent(this@LoginActivity, RegisterActivity::class.java))
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Don't have an account? ",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Sign Up",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }

    @Composable
    private fun FloatingOrbs(isDark: Boolean) {
        val infiniteTransition = rememberInfiniteTransition(label = "orbs")

        val offset1 by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 100f,
            animationSpec = infiniteRepeatable(
                animation = tween(3000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "orb1"
        )

        val offset2 by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -80f,
            animationSpec = infiniteRepeatable(
                animation = tween(4000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "orb2"
        )

        Box(modifier = Modifier.fillMaxSize()) {
            // Orb 1
            Surface(
                shape = CircleShape,
                color = if (isDark) Color(0xFF6366F1).copy(alpha = 0.1f)
                else Color(0xFF6366F1).copy(alpha = 0.05f),
                modifier = Modifier
                    .size(200.dp)
                    .offset(x = (-50).dp + offset1.dp, y = 100.dp)
                    .blur(60.dp)
            ) {}

            // Orb 2
            Surface(
                shape = CircleShape,
                color = if (isDark) Color(0xFFEC4899).copy(alpha = 0.1f)
                else Color(0xFFEC4899).copy(alpha = 0.05f),
                modifier = Modifier
                    .size(250.dp)
                    .offset(x = 200.dp + offset2.dp, y = 500.dp)
                    .blur(70.dp)
            ) {}
        }
    }

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
            // Lookup username → email
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

                // 🔔 START notification worker AFTER login

                startActivity(Intent(this, nextActivity))
                finish()

            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show()
            }
    }
}