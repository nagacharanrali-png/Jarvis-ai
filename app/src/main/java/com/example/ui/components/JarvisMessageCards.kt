package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.viewmodel.JarvisMessage
import com.example.viewmodel.JarvisRole

data class QuickCommandPreset(
    val label: String,
    val prompt: String,
    val icon: ImageVector
)

val defaultQuickCommands = listOf(
    QuickCommandPreset(
        label = "System Briefing",
        prompt = "Run a concise tactical briefing on what capabilities you have online right now.",
        icon = Icons.Rounded.Hub
    ),
    QuickCommandPreset(
        label = "Explain Quantum Spin",
        prompt = "Explain quantum superposition and spin in three crisp, intuitive analogies.",
        icon = Icons.Rounded.Psychology
    ),
    QuickCommandPreset(
        label = "Kotlin Coroutine Pattern",
        prompt = "Show a production-ready Kotlin StateFlow retry pattern with exponential backoff.",
        icon = Icons.Rounded.Code
    ),
    QuickCommandPreset(
        label = "Mission Checklist",
        prompt = "Help me structure a focused 90-minute deep work sprint for maximum output.",
        icon = Icons.Rounded.AutoAwesome
    )
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JarvisWelcomeOverlay(
    onSelectPrompt: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "NEURAL INTERFACE READY",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Speak directly into the Arc-Reactor core, attach an image for visual inspection, or select a tactical directive below.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 460.dp)
        )
        Spacer(modifier = Modifier.height(18.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            defaultQuickCommands.forEachIndexed { index, preset ->
                AssistChip(
                    onClick = { onSelectPrompt(preset.prompt) },
                    label = {
                        Text(
                            text = preset.label,
                            style = MaterialTheme.typography.titleSmall
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = preset.icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        leadingIconContentColor = MaterialTheme.colorScheme.primary
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("quick_prompt_chip_$index")
                )
            }
        }
    }
}

@Composable
fun JarvisMessageItem(
    message: JarvisMessage,
    isSpeakingThisMessage: Boolean,
    onToggleSpeak: () -> Unit,
    onRetry: (() -> Unit)? = null,
    onRetryWithRapid: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == JarvisRole.USER
    val context = LocalContext.current
    var copied by remember(message.id) { mutableStateOf(false) }

    AnimatedVisibility(visible = true, enter = fadeIn()) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            if (isUser) {
                Surface(
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 4.dp,
                        bottomStart = 18.dp,
                        bottomEnd = 18.dp
                    ),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                    modifier = Modifier.widthIn(max = 520.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "OPERATOR",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (message.attachedImage != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Image(
                                bitmap = message.attachedImage.asImageBitmap(),
                                contentDescription = "Operator attached visual",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(170.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else {
                val railColor = when {
                    message.isError -> MaterialTheme.colorScheme.error
                    message.isStreaming -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                }
                val containerColor = if (message.isError) {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
                }
                val borderColor = if (message.isError) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.outline
                }

                Surface(
                    shape = RoundedCornerShape(
                        topStart = 4.dp,
                        topEnd = 18.dp,
                        bottomStart = 18.dp,
                        bottomEnd = 18.dp
                    ),
                    color = containerColor,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.widthIn(max = 600.dp)
                ) {
                    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(railColor)
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (message.isError) {
                                        Icon(
                                            imageVector = Icons.Rounded.WarningAmber,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Text(
                                        text = if (message.isError) "SYSTEM ALERT" else "J.A.R.V.I.S.",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = railColor
                                    )
                                    message.modelUsed?.let { badge ->
                                        Text(
                                            text = "· $badge",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (message.latencyMs != null && !message.isStreaming) {
                                    Text(
                                        text = String.format("%.1fs", message.latencyMs / 1000f),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else if (message.isStreaming) {
                                    Text(
                                        text = "STREAMING…",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (message.text.isEmpty() && message.isStreaming) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .testTag("loading_indicator_${message.id}"),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = message.statusHint ?: "Synthesizing neural response…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.dp)
                                )
                            } else {
                                Text(
                                    text = formatJarvisRichText(
                                        text = message.text,
                                        highlightColor = MaterialTheme.colorScheme.primary,
                                        codeBgColor = MaterialTheme.colorScheme.background
                                    ),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (message.isError) {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                if (message.isStreaming) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(2.dp)
                                    )
                                }
                            }

                            if (message.isError && message.retryPrompt != null && onRetry != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = onRetry,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("retry_button_${message.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Retry", style = MaterialTheme.typography.labelLarge)
                                    }
                                    if (onRetryWithRapid != null && message.modelUsed != "3.5-LITE") {
                                        OutlinedButton(
                                            onClick = onRetryWithRapid,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("fallback_lite_button_${message.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Speed,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Use 3.5-LITE", style = MaterialTheme.typography.labelLarge)
                                        }
                                    }
                                }
                            }

                            if (!message.isStreaming && message.text.isNotBlank() && !message.isError) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = onToggleSpeak,
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("speak_button_${message.id}")
                                    ) {
                                        Icon(
                                            imageVector = if (isSpeakingThisMessage) {
                                                Icons.Rounded.StopCircle
                                            } else {
                                                Icons.AutoMirrored.Rounded.VolumeUp
                                            },
                                            contentDescription = if (isSpeakingThisMessage) {
                                                stringResource(R.string.cd_stop_speaking)
                                            } else {
                                                stringResource(R.string.cd_speak_message)
                                            },
                                            tint = if (isSpeakingThisMessage) {
                                                MaterialTheme.colorScheme.tertiary
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            },
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            copyToClipboard(context, message.text)
                                            copied = true
                                        },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("copy_button_${message.id}")
                                    ) {
                                        Icon(
                                            imageVector = if (copied) {
                                                Icons.Rounded.ContentCopy
                                            } else {
                                                Icons.Outlined.ContentCopy
                                            },
                                            contentDescription = stringResource(R.string.cd_copy_message),
                                            tint = if (copied) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("JARVIS Response", text))
}

private fun formatJarvisRichText(
    text: String,
    highlightColor: androidx.compose.ui.graphics.Color,
    codeBgColor: androidx.compose.ui.graphics.Color
): AnnotatedString {
    return buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            if (text.startsWith("**", index)) {
                val end = text.indexOf("**", index + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = highlightColor)) {
                        append(text.substring(index + 2, end))
                    }
                    index = end + 2
                    continue
                }
            }
            if (text[index] == '`') {
                val end = text.indexOf('`', index + 1)
                if (end != -1 && !text.substring(index + 1, end).contains('\n')) {
                    withStyle(
                        SpanStyle(
                            fontFamily = JetBrainsMonoFontFamily,
                            color = highlightColor,
                            background = codeBgColor.copy(alpha = 0.7f)
                        )
                    ) {
                        append(text.substring(index + 1, end))
                    }
                    index = end + 1
                    continue
                }
            }
            append(text[index])
            index++
        }
    }
}
