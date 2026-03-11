package com.kishan.attendmate.ui.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kishan.attendmate.ui.theme.AttendMateTheme

class AiChatActivity : ComponentActivity() {

    private val viewModel: AiChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AttendMateTheme { AiChatScreen(viewModel = viewModel, onNavigateBack = { finish() }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(viewModel: AiChatViewModel, onNavigateBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val messages = viewModel.messages
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()


    Scaffold(
            topBar = {
                TopAppBar(
                        title = {
                            Column {
                                Text("AttendMate AI Assistant", fontWeight = FontWeight.Bold)
                                Text(
                                        text = "Always available • No API needed",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back"
                                )
                            }
                        }
                )
            }
    ) { padding ->
        Column(
                modifier =
                        Modifier.fillMaxSize()
                                .padding(padding)
                                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message -> ChatBubble(message) }

                if (uiState is AiChatUiState.Loading) {
                    item {
                        Box(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                contentAlignment = Alignment.CenterStart
                        ) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
                    }
                }

                if (uiState is AiChatUiState.Error) {
                    item {
                        Text(
                                text = (uiState as AiChatUiState.Error).message,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            // Scroll to bottom when new messages arrive
            LaunchedEffect(messages.size, uiState) {
                if (messages.isNotEmpty()) {
                    listState.animateScrollToItem(messages.size - 1)
                }
            }

            // Input Area
            Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    shadowElevation = 4.dp
            ) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Ask something...") },
                            shape = RoundedCornerShape(24.dp),
                            singleLine = false,
                            maxLines = 4
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                            onClick = {
                                if (inputText.isNotBlank() &&
                                                uiState !is AiChatUiState.Loading
                                ) {
                                    viewModel.sendMessage(inputText)
                                    inputText = ""
                                }
                            },
                            enabled =
                                    inputText.isNotBlank() &&
                                            uiState !is AiChatUiState.Loading,
                            modifier =
                                    Modifier.size(48.dp)
                                            .clip(RoundedCornerShape(24.dp))
                                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.isUser
    val backgroundColor =
            if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }

    val textColor =
            if (isUser) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            }

    val shape =
            if (isUser) {
                RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
            } else {
                RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
            }

    Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
                modifier =
                        Modifier.widthIn(max = 300.dp)
                                .clip(shape)
                                .background(backgroundColor)
                                .padding(12.dp)
        ) {
            Text(text = message.text, color = textColor, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
