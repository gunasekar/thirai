package com.thirai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thirai.R
import com.thirai.ui.theme.Bricolage
import com.thirai.ui.theme.PlexMono

/**
 * Thirai's shared UI vocabulary.
 *
 * Every card and button in the app is built from the primitives here, so corner
 * radius, elevation, borders, padding and colour roles stay identical across
 * the screen. Restyle the app by editing this file — never re-declare a shape
 * or border at a call site.
 */

/**
 * The Thirai lockup: the amber play mark + the Tamil wordmark "திரை" with the
 * accent dot. Used in the top bar. The mark is decorative; the "Thirai"
 * content description carries the accessible name. The dot keeps the title's
 * styling (Bricolage); the Tamil glyphs fall back to the platform Tamil face at
 * the same size and weight.
 */
@Composable
fun ThiraiWordmark(
    modifier: Modifier = Modifier,
    logoSize: Dp = 28.dp,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.ic_logo),
            contentDescription = "Thirai",
            modifier = Modifier.size(logoSize),
        )
        Spacer(Modifier.width(10.dp))
        // English wordmark with the brand's amber accent dot.
        Text(
            text = "Thirai",
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = Bricolage),
            maxLines = 1,
            softWrap = false,
        )
        Text(
            text = ".",
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = Bricolage),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * A small mono, uppercase, wide-tracked label — the "eyebrow". Sits above a
 * section as a quiet, technical caption. Defaults to the accent colour.
 */
@Composable
fun Eyebrow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Text(
        text = text.uppercase(),
        color = color,
        style = TextStyle(
            fontFamily = PlexMono,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            letterSpacing = 2.sp,
        ),
        modifier = modifier,
    )
}

/** The surface role a [ThiraiCard] paints itself with. */
enum class CardTone { Neutral, Accent, Alert }

/**
 * The one card style: theme `large` radius, flat (no elevation), a [tone]-driven
 * colour pair, and a consistent inner padding. Content is laid out in a [Column].
 */
@Composable
fun ThiraiCard(
    modifier: Modifier = Modifier,
    tone: CardTone = CardTone.Neutral,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    val container = when (tone) {
        CardTone.Neutral -> MaterialTheme.colorScheme.surface
        CardTone.Accent -> MaterialTheme.colorScheme.primaryContainer
        CardTone.Alert -> MaterialTheme.colorScheme.errorContainer
    }
    val onContainer = when (tone) {
        CardTone.Neutral -> MaterialTheme.colorScheme.onSurface
        CardTone.Accent -> MaterialTheme.colorScheme.onPrimaryContainer
        CardTone.Alert -> MaterialTheme.colorScheme.onErrorContainer
    }
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = onContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
    }
}

/**
 * The single filled call-to-action — one per screen. Full width by default.
 * Pass a [leadingIcon] to prefix an icon before the label.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier) {
        ButtonContent(text, leadingIcon)
    }
}

/**
 * Medium-high emphasis: a soft-amber filled action that sits between the single
 * [PrimaryButton] and the outlined [SecondaryButton]. For a recurring, real
 * action that deserves more weight than a plain secondary without competing with
 * the screen's one primary CTA.
 */
@Composable
fun TonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        ButtonContent(text, leadingIcon)
    }
}

/**
 * Every non-primary action: outlined with a consistent 1.5dp border in
 * [contentColor] (defaults to the accent; pass e.g. `onErrorContainer` when the
 * button sits inside an [CardTone.Alert] card). Full width by default; pass a
 * plain [Modifier] to let it wrap.
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.primary,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        border = BorderStroke(1.5.dp, contentColor),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
    ) {
        ButtonContent(text, leadingIcon)
    }
}

@Composable
private fun ButtonContent(text: String, leadingIcon: (@Composable () -> Unit)?) {
    if (leadingIcon != null) {
        leadingIcon()
        Spacer(Modifier.width(8.dp))
    }
    Text(text)
}

/**
 * A wordless status indicator — a tinted dot in a soft halo. Colour alone
 * carries the state. Exposed to screen readers via [contentDescription].
 *
 * @param color the dot's colour (accent when good, error when failing, muted
 *   when unknown / not yet tested).
 */
@Composable
fun StatusDot(
    color: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .semantics { this.contentDescription = contentDescription }
            .size(20.dp)
            .background(color.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(color, CircleShape),
        )
    }
}
