package client.wexmanager.feature.wave100

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.math.BigInteger

@Composable
fun T07AspectScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var widthText by rememberSaveable { mutableStateOf("") }
    var heightText by rememberSaveable { mutableStateOf("") }
    var ratioText by rememberSaveable { mutableStateOf("") }
    val width = parseIntField(widthText)
    val height = parseIntField(heightText)
    val ratio = parseRatio(ratioText)
    val reduced = reducedLine(width, height, ratio)
    val other = otherSideLine(width, height, ratio)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Соотношение сторон") },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.surface,
                    scrolledContainerColor = scheme.surfaceContainer,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Ширина и высота — целые числа. Две стороны сокращаются до соотношения. Одна сторона и соотношение дают вторую сторону.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = widthText,
                onValueChange = { widthText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Ширина") },
                placeholder = { Text("1920") },
                supportingText = { Text("Целое число больше нуля") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = width is IntField.Invalid,
            )
            OutlinedTextField(
                value = heightText,
                onValueChange = { heightText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Высота") },
                placeholder = { Text("1080") },
                supportingText = { Text("Целое число больше нуля") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = height is IntField.Invalid,
            )
            OutlinedTextField(
                value = ratioText,
                onValueChange = { ratioText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Соотношение") },
                placeholder = { Text("16:9") },
                supportingText = { Text("Ширина к высоте, например 16:9") },
                leadingIcon = { Icon(Icons.Rounded.AspectRatio, null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                isError = ratio is RatioField.Invalid,
            )
            if (width is IntField.Invalid || height is IntField.Invalid || ratio is RatioField.Invalid) {
                Text(
                    "Нужны целые числа больше нуля. Соотношение — два таких числа, например 16:9.",
                    color = scheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            ResultCard(title = "Сокращённое соотношение", value = reduced.text, hint = reduced.hint)
            ResultCard(title = "Другая сторона", value = other.text, hint = other.hint)
        }
    }
}

@Composable
private fun ResultCard(title: String, value: String, hint: String) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = scheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

private data class Line(val text: String, val hint: String)

private sealed interface IntField {
    data object Empty : IntField
    data object Invalid : IntField
    data class Ok(val value: Long) : IntField
}

private sealed interface RatioField {
    data object Empty : RatioField
    data object Incomplete : RatioField
    data object Invalid : RatioField
    data class Ok(val width: Long, val height: Long) : RatioField
}

private fun reducedLine(width: IntField, height: IntField, ratio: RatioField): Line {
    val widthValue = (width as? IntField.Ok)?.value
    val heightValue = (height as? IntField.Ok)?.value
    if (widthValue != null && heightValue != null) {
        val pair = reduce(widthValue, heightValue)
        val typed = ratio as? RatioField.Ok
        val hint = if (typed == null) {
            "НОД ширины и высоты"
        } else {
            val typedPair = reduce(typed.width, typed.height)
            if (typedPair == pair) {
                "Совпадает с введённым соотношением"
            } else {
                "Стороны дают ${formatRatio(pair)}, введено ${formatRatio(typedPair)}"
            }
        }
        return Line(formatRatio(pair), hint)
    }
    val typed = ratio as? RatioField.Ok ?: return Line("—", "Обе стороны или соотношение")
    return Line(formatRatio(reduce(typed.width, typed.height)), "Сокращено из соотношения")
}

private fun otherSideLine(width: IntField, height: IntField, ratio: RatioField): Line {
    val widthValue = (width as? IntField.Ok)?.value
    val heightValue = (height as? IntField.Ok)?.value
    if (widthValue != null && heightValue != null) return Line("—", "Обе стороны уже заданы")
    val typed = ratio as? RatioField.Ok ?: return Line("—", "Нужны одна сторона и соотношение")
    val (ratioWidth, ratioHeight) = reduce(typed.width, typed.height)
    if (widthValue != null && height is IntField.Empty) {
        return missingSide(widthValue, knownIsWidth = true, ratioWidth, ratioHeight)
    }
    if (heightValue != null && width is IntField.Empty) {
        return missingSide(heightValue, knownIsWidth = false, ratioWidth, ratioHeight)
    }
    return Line("—", "Нужны одна сторона и соотношение")
}

private fun missingSide(known: Long, knownIsWidth: Boolean, ratioWidth: Long, ratioHeight: Long): Line {
    val numerator = if (knownIsWidth) ratioHeight else ratioWidth
    val denominator = if (knownIsWidth) ratioWidth else ratioHeight
    val label = if (knownIsWidth) "Высота" else "Ширина"
    val product = BigInteger.valueOf(known).multiply(BigInteger.valueOf(numerator))
    val divisor = BigInteger.valueOf(denominator)
    val divided = product.divideAndRemainder(divisor)
    val quotient = divided[0]
    val remainder = divided[1]
    val exact = remainder.signum() == 0
    val rounded = if (!exact && remainder.shiftLeft(1) >= divisor) quotient.add(BigInteger.ONE) else quotient
    val expr = "$known × $numerator / $denominator"
    if (exact) return Line(rounded.toString(), "$label: $expr")
    if (rounded.signum() == 0) return Line("≈ 0", "$label меньше 1: $expr")
    return Line("≈ ${rounded.toString()}", "$label: $expr, округлено")
}

private fun parseIntField(raw: String): IntField {
    val cleaned = raw.trim().replace(" ", "").replace("\u00A0", "").replace("_", "")
    if (cleaned.isEmpty()) return IntField.Empty
    return positiveLong(cleaned)?.let { IntField.Ok(it) } ?: IntField.Invalid
}

private fun parseRatio(raw: String): RatioField {
    val cleaned = raw.trim().replace(" ", "").replace("\u00A0", "").replace("_", "")
    if (cleaned.isEmpty()) return RatioField.Empty
    val parts = cleaned.split(RatioSeparator)
    if (parts.size == 1 && parts[0].all { it in '0'..'9' }) return RatioField.Incomplete
    if (parts.size == 2 && parts[1].isEmpty() && parts[0].isNotEmpty() && parts[0].all { it in '0'..'9' }) {
        return RatioField.Incomplete
    }
    if (parts.size != 2) return RatioField.Invalid
    val width = positiveLong(parts[0]) ?: return RatioField.Invalid
    val height = positiveLong(parts[1]) ?: return RatioField.Invalid
    return RatioField.Ok(width, height)
}

private fun positiveLong(text: String): Long? {
    if (text.isEmpty() || text.any { it !in '0'..'9' }) return null
    return text.toLongOrNull()?.takeIf { it > 0L }
}

private fun reduce(width: Long, height: Long): Pair<Long, Long> {
    val divisor = gcd(width, height)
    return width / divisor to height / divisor
}

private fun gcd(a: Long, b: Long): Long {
    var x = a
    var y = b
    while (y != 0L) {
        val next = x % y
        x = y
        y = next
    }
    return x
}

private fun formatRatio(pair: Pair<Long, Long>): String = "${pair.first}:${pair.second}"

private val RatioSeparator = Regex("""[:/xX×хХ*]""")
