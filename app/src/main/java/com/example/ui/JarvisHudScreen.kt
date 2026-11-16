package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.VoiceChat
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.components.ArcReactorCore
import com.example.ui.components.CoreReactorMode
import com.example.ui.components.DiagnosticsSheet
import com.example.ui.components.HologramForgePane
import com.example.ui.components.JarvisMessageItem
import com.example.ui.components.JarvisWelcomeOverlay
import com.example.ui.components.defaultQuickCommands
import com.example.viewmodel.JarvisTab
import com.example.viewmodel.JarvisUiState
import com.example.viewmodel.JarvisViewModel
import com.example.voice.VoiceInputState

@Composable
fun JarvisHudScreen(
    viewModel: JarvisViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showPermissionRationale by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showPermissionRationale = false
            viewModel.startVoiceListening()
        } else {
            showPermissionRationale = true
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.attachChatImage(uri)
        }
    }

    val triggerVoiceInput: () -> Unit = {
        when {
            uiState.voiceState is VoiceInputState.Listening -> {
                viewModel.stopVoiceListening()
            }
            uiState.ttsState.isSpeaking -> {
                viewModel.stopSpeaking()
            }
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                showPermissionRationale = false
                viewModel.startVoiceListening()
            }
            else -> {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    BackHandler(enabled = uiState.showSettingsSheet || uiState.activeTab != JarvisTab.COMMS) {
        when {
            uiState.showSettingsSheet -> viewModel.setSettingsSheetVisible(false)
            uiState.activeTab != JarvisTab.COMMS -> viewModel.selectTab(JarvisTab.COMMS)
        }
    }

    val reactorMode = when {
        uiState.voiceState is VoiceInputState.Listening -> CoreReactorMode.LISTENING
        uiState.isGenerating -> CoreReactorMode.THINKING
        uiState.ttsState.isSpeaking -> CoreReactorMode.SPEAKING
        uiState.voiceState is VoiceInputState.Error -> CoreReactorMode.ERROR
        else -> CoreReactorMode.IDLE
    }

    val statusLabel = when (reactorMode) {
        CoreReactorMode.LISTENING -> stringResource(R.string.status_listening)
        CoreReactorMode.THINKING -> stringResource(R.string.status_thinking)
        CoreReactorMode.SPEAKING -> stringResource(R.string.status_speaking)
        CoreReactorMode.ERROR -> stringResource(R.string.status_error)
        CoreReactorMode.IDLE -> stringResource(R.string.status_online)
    }

    val amplitude = (uiState.voiceState as? VoiceInputState.Listening)?.amplitude ?: 0.15f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Subtle generated cybernetic HUD backdrop
        Image(
            painter = painterResource(id = R.drawable.img_jarvis_hud_bg_1794825575165),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.22f)
        )

        // Deep obsidian radial vignette for sharp contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.65f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.96f)
                        )
                    )
                )
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            val isWideScreen = maxWidth >= 700.dp

            Column(modifier = Modifier.fillMaxSize()) {
                JarvisTopTelemetryBar(
                    uiState = uiState,
                    reactorMode = reactorMode,
                    statusLabel = statusLabel,
                    onSelectTab = viewModel::selectTab,
                    onToggleAutoSpeak = { viewModel.setAutoSpeak(!uiState.settings.autoSpeak) },
                    onOpenSettings = { viewModel.setSettingsSheetVisible(true) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))

                if (showPermissionRationale) {
                    DiagnosticNoticeBanner(
                        message = stringResource(R.string.mic_permission_rationale),
                        actionLabel = "Grant",
                        onAction = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        onDismiss = { showPermissionRationale = false }
                    )
                }

                if (uiState.voiceState is VoiceInputState.Error) {
                    val err = (uiState.voiceState as VoiceInputState.Error).message
                    DiagnosticNoticeBanner(
                        message = err,
                        actionLabel = null,
                        onAction = {},
                        onDismiss = viewModel::dismissVoiceError
                    )
                }

                if (isWideScreen) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Left Arc-Reactor Telemetry Console
                        WideTelemetrySideConsole(
                            uiState = uiState,
                            reactorMode = reactorMode,
                            statusLabel = statusLabel,
                            amplitude = amplitude,
                            onCoreClick = triggerVoiceInput,
                            onQuickPrompt = { prompt ->
                                viewModel.selectTab(JarvisTab.COMMS)
                                viewModel.submitQuickPrompt(prompt)
                            },
                            modifier = Modifier
                                .width(330.dp)
                                .fillMaxHeight()
                                .navigationBarsPadding()
                        )

                        VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))

                        // Right Workspace
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            JarvisMainWorkspace(
                                uiState = uiState,
                                isWideScreen = true,
                                reactorMode = reactorMode,
                                statusLabel = statusLabel,
                                amplitude = amplitude,
                                viewModel = viewModel,
                                onCoreClick = triggerVoiceInput,
                                onPickChatImage = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            )
                        }
                    }
                } else {
                    JarvisMainWorkspace(
                        uiState = uiState,
                        isWideScreen = false,
                        reactorMode = reactorMode,
                        statusLabel = statusLabel,
                        amplitude = amplitude,
                        viewModel = viewModel,
                        onCoreClick = triggerVoiceInput,
                        onPickChatImage = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                }
            }
        }
    }

    if (uiState.showSettingsSheet) {
        DiagnosticsSheet(
            settings = uiState.settings,
            appCheckStatus = uiState.appCheckStatus,
            liveDiagnosticState = uiState.liveDiagnosticState,
            messageCount = uiState.messages.size,
            lastLatencyMs = uiState.lastLatencyMs,
            onDismiss = { viewModel.setSettingsSheetVisible(false) },
            onSelectCoreMode = viewModel::setCoreMode,
            onToggleAutoSpeak = viewModel::setAutoSpeak,
            onToggleAutoSendVoice = viewModel::setAutoSendVoice,
            onSpeechRateChange = viewModel::setSpeechRate,
            onSpeechPitchChange = viewModel::setSpeechPitch,
            onTestVoice = viewModel::testVoiceCalibration,
            onRunLiveTest = viewModel::runLiveSelfTest,
            onClearSession = viewModel::clearConversation
        )
    }
}

@Composable
private fun JarvisTopTelemetryBar(
    uiState: JarvisUiState,
    reactorMode: CoreReactorMode,
    statusLabel: String,
    onSelectTab: (JarvisTab) -> Unit,
    onToggleAutoSpeak: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val statusColor = when (reactorMode) {
        CoreReactorMode.ERROR -> MaterialTheme.colorScheme.error
        CoreReactorMode.THINKING -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Column {
                    Text(
                        text = stringResource(R.string.jarvis_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$statusLabel · ${uiState.settings.coreMode.shortBadge}",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = onToggleAutoSpeak,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("toggle_auto_speak_button")
                ) {
                    Icon(
                        imageVector = if (uiState.settings.autoSpeak) {
                            Icons.AutoMirrored.Rounded.VolumeUp
                        } else {
                            Icons.AutoMirrored.Rounded.VolumeOff
                        },
                        contentDescription = stringResource(R.string.cd_toggle_tts),
                        tint = if (uiState.settings.autoSpeak) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("open_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = stringResource(R.string.cd_open_settings),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Compact Mode Tabs: COMMS & HOLOGRAM
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HudModePill(
                label = stringResource(R.string.tab_comms),
                icon = Icons.Rounded.VoiceChat,
                selected = uiState.activeTab == JarvisTab.COMMS,
                onClick = { onSelectTab(JarvisTab.COMMS) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("tab_comms")
            )
            HudModePill(
                label = stringResource(R.string.tab_hologram),
                icon = Icons.Rounded.ViewInAr,
                selected = uiState.activeTab == JarvisTab.HOLOGRAM,
                onClick = { onSelectTab(JarvisTab.HOLOGRAM) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("tab_hologram")
            )
        }
    }
}

@Composable
private fun HudModePill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        modifier = modifier
            .height(40.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun WideTelemetrySideConsole(
    uiState: JarvisUiState,
    reactorMode: CoreReactorMode,
    statusLabel: String,
    amplitude: Float,
    onCoreClick: () -> Unit,
    onQuickPrompt: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ArcReactorCore(
            mode = reactorMode,
            amplitude = amplitude,
            statusLabel = "CORE: $statusLabel",
            coreSize = 196.dp,
            onCoreClick = onCoreClick
        )

        if (uiState.voiceState is VoiceInputState.Listening) {
            val partial = uiState.voiceState.partialTranscript
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = partial.ifBlank { "Listening for voice command…" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "TELEMETRY",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                TelemetryRow("BRAIN", uiState.settings.coreMode.displayLabel)
                TelemetryRow(
                    "VOICE INPUT",
                    if (uiState.isSpeechRecognitionAvailable) "READY" else "TEXT ONLY"
                )
                TelemetryRow(
                    "TTS AUDIO",
                    if (uiState.ttsState.isReady) {
                        if (uiState.settings.autoSpeak) "AUTO-SPEAK" else "MANUAL"
                    } else {
                        "STANDBY"
                    }
                )
                TelemetryRow(
                    "LATENCY",
                    uiState.lastLatencyMs?.let { "${it} ms" } ?: "—"
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "QUICK DIRECTIVES",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            defaultQuickCommands.forEach { preset ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onQuickPrompt(preset.prompt) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = preset.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = preset.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun JarvisMainWorkspace(
    uiState: JarvisUiState,
    isWideScreen: Boolean,
    reactorMode: CoreReactorMode,
    statusLabel: String,
    amplitude: Float,
    viewModel: JarvisViewModel,
    onCoreClick: () -> Unit,
    onPickChatImage: () -> Unit
) {
    if (uiState.activeTab == JarvisTab.HOLOGRAM) {
        HologramForgePane(
            prompt = uiState.hologramPrompt,
            sourceImage = uiState.hologramSourceImage,
            selectedRatio = uiState.selectedAspectRatio,
            hologramState = uiState.hologramState,
            hologramAnalysisState = uiState.hologramAnalysisState,
            onPromptChange = viewModel::updateHologramPrompt,
            onSelectRatio = viewModel::selectAspectRatio,
            onAttachSourceImage = viewModel::attachHologramSourceImage,
            onClearSourceImage = viewModel::clearHologramSourceImage,
            onGenerateClick = viewModel::generateHologram,
            onAnalyzeVisualClick = viewModel::analyzeHologramVisual,
            onSpeakAnalysis = { analysis -> viewModel.speakText("hologram_analysis", analysis) },
            modifier = Modifier.navigationBarsPadding()
        )
        return
    }

    val listState = rememberLazyListState()
    LaunchedEffect(uiState.messages.size, uiState.messages.lastOrNull()?.text?.length) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Compact phone header strip when conversation is active
        if (!isWideScreen && uiState.messages.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ArcReactorCore(
                        mode = reactorMode,
                        amplitude = amplitude,
                        statusLabel = statusLabel,
                        coreSize = 54.dp,
                        showLabel = false,
                        onCoreClick = onCoreClick
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (reactorMode) {
                                CoreReactorMode.LISTENING -> "VOICE CAPTURE ACTIVE"
                                CoreReactorMode.THINKING -> "PROCESSING NEURAL QUERY"
                                CoreReactorMode.SPEAKING -> "AUDIO OUTPUT TRANSMITTING"
                                CoreReactorMode.ERROR -> "DIAGNOSTIC ALERT"
                                CoreReactorMode.IDLE -> "TAP CORE TO SPEAK"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val subtext = (uiState.voiceState as? VoiceInputState.Listening)
                            ?.partialTranscript
                            ?.takeIf { it.isNotBlank() }
                            ?: "Core: ${uiState.settings.coreMode.displayLabel}"
                        Text(
                            text = subtext,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (uiState.ttsState.isSpeaking) {
                        IconButton(
                            onClick = viewModel::stopSpeaking,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Stop,
                                contentDescription = stringResource(R.string.cd_stop_speaking),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }
        }

        // Conversation feed or welcome core
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (uiState.messages.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (!isWideScreen) {
                        ArcReactorCore(
                            mode = reactorMode,
                            amplitude = amplitude,
                            statusLabel = "TAP CORE FOR VOICE · $statusLabel",
                            coreSize = 176.dp,
                            onCoreClick = onCoreClick
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    if (uiState.voiceState is VoiceInputState.Listening) {
                        val partial = uiState.voiceState.partialTranscript
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .padding(horizontal = 24.dp, vertical = 8.dp)
                                .widthIn(max = 460.dp)
                        ) {
                            Text(
                                text = partial.ifBlank { "Listening… speak naturally" },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                    JarvisWelcomeOverlay(
                        onSelectPrompt = viewModel::submitQuickPrompt
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 820.dp)
                ) {
                    items(
                        items = uiState.messages,
                        key = { it.id }
                    ) { message ->
                        JarvisMessageItem(
                            message = message,
                            isSpeakingThisMessage = uiState.ttsState.isSpeaking &&
                                uiState.ttsState.speakingMessageId == message.id,
                            onToggleSpeak = { viewModel.toggleSpeechForMessage(message) },
                            onRetry = { viewModel.retryFailedMessage(message, useRapidFallback = false) },
                            onRetryWithRapid = { viewModel.retryFailedMessage(message, useRapidFallback = true) }
                        )
                    }
                }
            }
        }

        // Bottom Glowing Cyan Command Bar
        JarvisCommandBar(
            inputText = uiState.inputText,
            pendingAttachment = uiState.pendingAttachment,
            isGenerating = uiState.isGenerating,
            isListening = uiState.voiceState is VoiceInputState.Listening,
            onInputChange = viewModel::updateInputText,
            onAttachClick = onPickChatImage,
            onClearAttachment = viewModel::clearChatAttachment,
            onVoiceClick = onCoreClick,
            onSendClick = { viewModel.sendMessage() }
        )
    }
}

@Composable
private fun JarvisCommandBar(
    inputText: String,
    pendingAttachment: Bitmap?,
    isGenerating: Boolean,
    isListening: Boolean,
    onInputChange: (String) -> Unit,
    onAttachClick: () -> Unit,
    onClearAttachment: () -> Unit,
    onVoiceClick: () -> Unit,
    onSendClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(
            visible = pendingAttachment != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            pendingAttachment?.let { bitmap ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .widthIn(max = 760.dp)
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Attached visual preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Text(
                            text = "Visual attached for Gemini multimodal analysis",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onClearAttachment,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.cd_remove_attachment),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f),
            border = BorderStroke(
                width = 1.dp,
                color = if (isListening) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                }
            ),
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onAttachClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("attach_image_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AddPhotoAlternate,
                        contentDescription = stringResource(R.string.cd_attach_image),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                TextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.input_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (!isGenerating && (inputText.isNotBlank() || pendingAttachment != null)) {
                                onSendClick()
                            }
                        }
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("command_input")
                )

                IconButton(
                    onClick = onVoiceClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .background(
                            if (isListening) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                            } else {
                                Color.Transparent
                            }
                        )
                        .testTag("voice_mic_button")
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Rounded.GraphicEq else Icons.Rounded.Mic,
                        contentDescription = if (isListening) {
                            stringResource(R.string.cd_stop_listening)
                        } else {
                            stringResource(R.string.cd_voice_mic)
                        },
                        tint = if (isListening) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }

                val canSend = !isGenerating && (inputText.isNotBlank() || pendingAttachment != null)
                IconButton(
                    onClick = onSendClick,
                    enabled = canSend,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .background(
                            if (canSend) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                            }
                        )
                        .testTag("send_button")
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = stringResource(R.string.cd_send_command),
                            tint = if (canSend) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticNoticeBanner(
    message: String,
    actionLabel: String?,
    onAction: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            if (actionLabel != null) {
                TextButton(onClick = onAction) {
                    Text(
                        text = actionLabel,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Dismiss notice",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}
