package com.omniai.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omniai.core.designsystem.theme.LocalStatusColors
import com.omniai.core.designsystem.theme.glass
import kotlin.math.absoluteValue

enum class StatusTone { SUCCESS, WARNING, DANGER, NEUTRAL, ACCENT }

@Composable
fun toneColor(tone: StatusTone): Color {
    val s = LocalStatusColors.current
    return when (tone) {
        StatusTone.SUCCESS -> s.success
        StatusTone.WARNING -> s.warning
        StatusTone.DANGER -> s.danger
        StatusTone.NEUTRAL -> s.neutral
        StatusTone.ACCENT -> MaterialTheme.colorScheme.primary
    }
}

/** Card with an optional title row; the main surface for dashboard and list content. */
@Composable
fun OmniCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = com.omniai.core.designsystem.theme.LocalGlass.current
    val shape = MaterialTheme.shapes.large
    val colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    val glassModifier = modifier.fillMaxWidth().then(Modifier.glass(glass, shape))
    val body: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null || trailing != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (title != null) {
                        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    }
                    trailing?.invoke(this)
                }
            }
            content()
        }
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = glassModifier, colors = colors, shape = shape, content = body)
    } else {
        Card(modifier = glassModifier, colors = colors, shape = shape, content = body)
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 420.dp))
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(4.dp))
            FilledTonalButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun ErrorBanner(message: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(10.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
            if (actionLabel != null && onAction != null) TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun StatusChip(text: String, tone: StatusTone, modifier: Modifier = Modifier) {
    val color = toneColor(tone)
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

/** Small pill for metadata such as "128K ctx" or "Vision". */
@Composable
fun MetaPill(text: String, modifier: Modifier = Modifier, emphasized: Boolean = false) {
    Text(
        text,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (emphasized) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = if (emphasized) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

/** Letter avatar with a stable color per name. Provider logos are not bundled to avoid trademark issues. */
@Composable
fun ProviderAvatar(name: String, emoji: String? = null, size: Dp = 40.dp) {
    val hues = listOf(0xFF7C83FF, 0xFF2DD4BF, 0xFFFBBF24, 0xFFFB7185, 0xFF38BDF8, 0xFFA78BFA, 0xFF34D399, 0xFFF97316)
    val color = Color(hues[Math.floorMod(name.hashCode(), hues.size)])
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size / 3.2f)).background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            emoji ?: name.trim().take(1).uppercase(),
            style = if (size > 36.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            color = color,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, height: Dp = 16.dp) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(0.35f, 0.8f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha")
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(6.dp))
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .semantics { contentDescription = "Loading" },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SettingSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, subtitle: String? = null, enabled: Boolean = true) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
fun SettingClickRow(title: String, modifier: Modifier = Modifier, subtitle: String? = null, value: String? = null, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
    }
}

@Composable
fun KeyValueRow(key: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(key, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
    }
}

/**
 * API key input: masked by default, show/hide toggle and paste from clipboard. The typed value is
 * never logged; the parent should clear it after saving.
 */
@Composable
fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = supportingText?.let { { Text(it) } },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        trailingIcon = {
            Row {
                IconButton(onClick = { clipboard.getText()?.text?.trim()?.let(onValueChange) }) {
                    Icon(Icons.Outlined.ContentPaste, contentDescription = "Paste from clipboard")
                }
                IconButton(onClick = { visible = !visible }) {
                    Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = if (visible) "Hide key" else "Show key")
                }
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun rememberBoolean(initial: Boolean = false) = remember { mutableStateOf(initial) }
