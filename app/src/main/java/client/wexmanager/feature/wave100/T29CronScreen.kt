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

private const val FormatHint = "Пять полей: минута час день месяц день недели"

private const val CountError =
    "Нужно ровно пять полей через пробел: минута, час, день месяца, месяц и день недели"

private const val Help =
    "Пять полей через пробел: минута, час, день месяца, месяц и день недели. " +
        "* — любое значение в своём поле. Число — одно значение, например 30. " +
        "Список — несколько чисел через запятую, например 0,15,30. " +
        "Диапазон — от и до включительно, например 1-5; такой кусок можно ставить и в список: 1-5,10. " +
        "Границы: минуты 0-59, часы 0-23, день месяца 1-31, месяц 1-12, день недели 0-7 " +
        "(0 и 7 — воскресенье, 1 — понедельник). Шаг, имена, вопросительный знак и лишние поля — ошибка."

private val monthNominative = arrayOf(
    "январь", "февраль", "март", "апрель", "май", "июнь",
    "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь",
)

private val monthGenitive = arrayOf(
    "января", "февраля", "марта", "апреля", "мая", "июня",
    "июля", "августа", "сентября", "октября", "ноября", "декабря",
)

private val monthPrepositional = arrayOf(
    "январе", "феврале", "марте", "апреле", "мае", "июне",
    "июле", "августе", "сентябре", "октябре", "ноябре", "декабре",
)

/** Пять полей cron. Принимаются только *, число, список и диапазон. */
@Composable
fun T29CronScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var text by rememberSaveable { mutableStateOf("") }
    val parsed = parseCron(text)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Выражение cron") },
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
                Help,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = parsed is CronParse.Invalid,
                label = { Text("Выражение") },
                placeholder = { Text("0 9 * * 1-5", fontFamily = FontFamily.Monospace) },
                supportingText = {
                    Text(if (parsed is CronParse.Invalid) parsed.message else FormatHint)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            )
            if (parsed is CronParse.Ok) {
                CronExplanation(parsed.fields)
            }
        }
    }
}

@Composable
private fun CronExplanation(fields: List<FieldExpr>) {
    val scheme = MaterialTheme.colorScheme
    val lines = listOf(
        "Минута" to valueGloss(fields[0]),
        "Час" to valueGloss(fields[1]),
        "День месяца" to valueGloss(fields[2]),
        "Месяц" to monthGloss(fields[3]),
        "День недели" to weekGloss(fields[4]),
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = scheme.surfaceContainer,
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                summary(fields),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            lines.forEach { (title, value) ->
                Text(
                    "$title — $value",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            restrictionNote(fields[2], fields[4])?.let { note ->
                Text(
                    note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            shortMonthNote(fields[2])?.let { note ->
                Text(
                    note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

private enum class CronSlot(val title: String, val min: Int, val max: Int) {
    MINUTE("Минута", 0, 59),
    HOUR("Час", 0, 23),
    DAY("День месяца", 1, 31),
    MONTH("Месяц", 1, 12),
    WEEKDAY("День недели", 0, 7),
}

private sealed interface FieldExpr {
    data object Any : FieldExpr
    data class Parts(val parts: List<FieldPart>) : FieldExpr
}

private sealed interface FieldPart {
    data class Number(val value: Int) : FieldPart
    data class Range(val from: Int, val to: Int) : FieldPart
}

private sealed interface CronParse {
    data object Empty : CronParse
    data class Invalid(val message: String) : CronParse
    data class Ok(val fields: List<FieldExpr>) : CronParse
}

private sealed interface FieldParse {
    data class Good(val expr: FieldExpr) : FieldParse
    data class Bad(val message: String) : FieldParse
}

private data class WeekdayForm(
    val nominative: String,
    val genitive: String,
    val accusative: String,
    val dativePlural: String,
)

private fun parseCron(raw: String): CronParse {
    val text = raw.trim()
    if (text.isEmpty()) return CronParse.Empty
    val tokens = text.split(Regex("\\s+"))
    if (tokens.size != 5) return CronParse.Invalid(CountError)
    val fields = ArrayList<FieldExpr>(5)
    for (index in tokens.indices) {
        when (val parsed = parseField(tokens[index], CronSlot.entries[index])) {
            is FieldParse.Bad -> return CronParse.Invalid(parsed.message)
            is FieldParse.Good -> fields += parsed.expr
        }
    }
    return CronParse.Ok(fields)
}

private fun parseField(token: String, slot: CronSlot): FieldParse {
    if (token == "*") return FieldParse.Good(FieldExpr.Any)
    val pieces = token.split(',')
    if (pieces.any { it.isEmpty() }) {
        return FieldParse.Bad("Поле «${slot.title}»: пустой элемент списка")
    }
    val parts = ArrayList<FieldPart>(pieces.size)
    for (piece in pieces) {
        val dash = piece.indexOf('-')
        if (dash >= 0) {
            if (dash == 0 || piece.indexOf('-', dash + 1) >= 0) {
                return FieldParse.Bad(rejected(slot, piece))
            }
            val left = piece.substring(0, dash)
            val right = piece.substring(dash + 1)
            val from = parseIntStrict(left)
            val to = parseIntStrict(right)
            if (from == null || to == null) {
                return FieldParse.Bad(numberOrRejected(slot, piece, left, right))
            }
            if (from !in slot.min..slot.max || to !in slot.min..slot.max) {
                return FieldParse.Bad("Поле «${slot.title}»: диапазон $from–$to вне ${slot.min}–${slot.max}")
            }
            if (from > to) {
                return FieldParse.Bad("Поле «${slot.title}»: в диапазоне $from–$to начало больше конца")
            }
            parts += if (from == to) FieldPart.Number(from) else FieldPart.Range(from, to)
        } else {
            val value = parseIntStrict(piece)
            if (value == null) {
                return FieldParse.Bad(
                    if (piece.all { it in '0'..'9' }) {
                        "Поле «${slot.title}»: число вне диапазона ${slot.min}–${slot.max}"
                    } else {
                        rejected(slot, piece)
                    },
                )
            }
            if (value !in slot.min..slot.max) {
                return FieldParse.Bad("Поле «${slot.title}»: число $value вне диапазона ${slot.min}–${slot.max}")
            }
            parts += FieldPart.Number(value)
        }
    }
    return FieldParse.Good(FieldExpr.Parts(parts))
}

private fun numberOrRejected(slot: CronSlot, piece: String, left: String, right: String): String {
    val digits = (left.isNotEmpty() && left.all { it in '0'..'9' }) &&
        (right.isNotEmpty() && right.all { it in '0'..'9' })
    return if (digits) {
        "Поле «${slot.title}»: диапазон вне ${slot.min}–${slot.max}"
    } else {
        rejected(slot, piece)
    }
}

private fun rejected(slot: CronSlot, piece: String): String =
    "Поле «${slot.title}»: «$piece» не подходит. Можно только *, число, список и диапазон"

private fun parseIntStrict(text: String): Int? {
    if (text.isEmpty() || text.any { it !in '0'..'9' }) return null
    return text.toIntOrNull()
}

private fun summary(fields: List<FieldExpr>): String {
    val clock = clockLabel(fields[0], fields[1])
    val calendar = calendarPhrase(fields[2], fields[4])
    val monthSuffix = if (fields[3] is FieldExpr.Any) "" else ", ${monthPhrase(fields[3])}"
    return if (clock != null) {
        "В $clock, $calendar$monthSuffix."
    } else {
        "${sentence(minutePhrase(fields[0]))}, ${hourPhrase(fields[1])}, $calendar$monthSuffix."
    }
}

private fun calendarPhrase(day: FieldExpr, week: FieldExpr): String = when {
    day is FieldExpr.Any && week is FieldExpr.Any -> "каждый день"
    day is FieldExpr.Any -> weekPhrase(week)
    week is FieldExpr.Any -> dayPhrase(day)
    else -> "${dayPhrase(day)} или ${weekPhrase(week)}"
}

private fun restrictionNote(day: FieldExpr, week: FieldExpr): String? {
    if (day is FieldExpr.Any || week is FieldExpr.Any) return null
    return "День месяца и день недели заданы вместе. В обычном cron достаточно, чтобы совпал любой из них."
}

private fun shortMonthNote(day: FieldExpr): String? {
    val parts = (day as? FieldExpr.Parts)?.parts ?: return null
    val late = parts.any { part ->
        when (part) {
            is FieldPart.Number -> part.value >= 29
            is FieldPart.Range -> part.to >= 29
        }
    }
    if (!late) return null
    return "Если в месяце нет такого числа, этот день пропускается."
}

private fun minutePhrase(expr: FieldExpr): String = when (expr) {
    FieldExpr.Any -> "каждую минуту"
    is FieldExpr.Parts -> {
        val single = expr.parts.singleOrNull() as? FieldPart.Number
        if (single != null) "в минуту ${single.value}" else "в минуты ${joinRu(expr.parts.map(::span))}"
    }
}

private fun hourPhrase(expr: FieldExpr): String = when (expr) {
    FieldExpr.Any -> "каждый час"
    is FieldExpr.Parts -> {
        val single = expr.parts.singleOrNull() as? FieldPart.Number
        if (single != null) {
            "в ${single.value} ${hoursWord(single.value)}"
        } else {
            "в часы ${joinRu(expr.parts.map(::span))}"
        }
    }
}

private fun dayPhrase(expr: FieldExpr): String = when (expr) {
    FieldExpr.Any -> "каждый день месяца"
    is FieldExpr.Parts -> {
        if (expr.parts.all { it is FieldPart.Number }) {
            joinRu(expr.parts.map { "${(it as FieldPart.Number).value}-го" }) + " числа"
        } else {
            joinRu(expr.parts.map(::dayFragment))
        }
    }
}

private fun dayFragment(part: FieldPart): String = when (part) {
    is FieldPart.Number -> "${part.value}-го числа"
    is FieldPart.Range -> "с ${part.from}-го по ${part.to}-е число"
}

private fun monthPhrase(expr: FieldExpr): String = when (expr) {
    FieldExpr.Any -> "каждый месяц"
    is FieldExpr.Parts -> {
        if (expr.parts.all { it is FieldPart.Number }) {
            "в " + joinRu(expr.parts.map { monthPrepositional[(it as FieldPart.Number).value - 1] })
        } else {
            joinRu(expr.parts.map(::monthWhen))
        }
    }
}

private fun monthWhen(part: FieldPart): String = when (part) {
    is FieldPart.Number -> "в ${monthPrepositional[part.value - 1]}"
    is FieldPart.Range -> "с ${monthGenitive[part.from - 1]} по ${monthNominative[part.to - 1]}"
}

private fun weekPhrase(expr: FieldExpr): String = when (expr) {
    FieldExpr.Any -> "в любой день недели"
    is FieldExpr.Parts -> {
        if (expr.parts.all { it is FieldPart.Number }) {
            "по " + joinRu(expr.parts.map { weekday((it as FieldPart.Number).value).dativePlural })
        } else {
            joinRu(expr.parts.map(::weekWhen))
        }
    }
}

private fun weekWhen(part: FieldPart): String = when (part) {
    is FieldPart.Number -> "по ${weekday(part.value).dativePlural}"
    is FieldPart.Range -> "с ${weekday(part.from).genitive} по ${weekday(part.to).accusative}"
}

private fun valueGloss(expr: FieldExpr): String = when (expr) {
    FieldExpr.Any -> "любое значение"
    is FieldExpr.Parts -> joinRu(expr.parts.map(::span))
}

private fun monthGloss(expr: FieldExpr): String = when (expr) {
    FieldExpr.Any -> "любое значение"
    is FieldExpr.Parts -> joinRu(expr.parts.map(::monthName))
}

private fun monthName(part: FieldPart): String = when (part) {
    is FieldPart.Number -> monthNominative[part.value - 1]
    is FieldPart.Range -> "с ${monthGenitive[part.from - 1]} по ${monthNominative[part.to - 1]}"
}

private fun weekGloss(expr: FieldExpr): String = when (expr) {
    FieldExpr.Any -> "любое значение"
    is FieldExpr.Parts -> joinRu(expr.parts.map(::weekName))
}

private fun weekName(part: FieldPart): String = when (part) {
    is FieldPart.Number -> weekday(part.value).nominative
    is FieldPart.Range -> "с ${weekday(part.from).genitive} по ${weekday(part.to).accusative}"
}

private fun span(part: FieldPart): String = when (part) {
    is FieldPart.Number -> part.value.toString()
    is FieldPart.Range -> "с ${part.from} по ${part.to}"
}

private fun clockLabel(minute: FieldExpr, hour: FieldExpr): String? {
    val m = (minute as? FieldExpr.Parts)?.parts?.singleOrNull() as? FieldPart.Number ?: return null
    val h = (hour as? FieldExpr.Parts)?.parts?.singleOrNull() as? FieldPart.Number ?: return null
    return "%02d:%02d".format(h.value, m.value)
}

private fun hoursWord(n: Int): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> "часов"
        mod10 == 1 -> "час"
        mod10 in 2..4 -> "часа"
        else -> "часов"
    }
}

private fun weekday(value: Int): WeekdayForm = when (value) {
    0 -> WeekdayForm("воскресенье", "воскресенья", "воскресенье", "воскресеньям")
    1 -> WeekdayForm("понедельник", "понедельника", "понедельник", "понедельникам")
    2 -> WeekdayForm("вторник", "вторника", "вторник", "вторникам")
    3 -> WeekdayForm("среда", "среды", "среду", "средам")
    4 -> WeekdayForm("четверг", "четверга", "четверг", "четвергам")
    5 -> WeekdayForm("пятница", "пятницы", "пятницу", "пятницам")
    6 -> WeekdayForm("суббота", "субботы", "субботу", "субботам")
    else -> WeekdayForm("воскресенье (7)", "воскресенья (7)", "воскресенье (7)", "воскресеньям (7)")
}

private fun joinRu(items: List<String>): String = when (items.size) {
    0 -> ""
    1 -> items[0]
    else -> items.dropLast(1).joinToString(", ") + " и " + items.last()
}

private fun sentence(text: String): String = text.replaceFirstChar { it.uppercaseChar() }
