package client.wexmanager.feature.wave100

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

private data class WaveTool(val id: String, val title: String, val screen: @Composable (onBack: () -> Unit) -> Unit)

private val tools = listOf(
    WaveTool("T01LineEndingsScreen", "Окончания строк") { T01LineEndingsScreen(it) },
    WaveTool("T02WhitespaceScreen", "Отступы") { T02WhitespaceScreen(it) },
    WaveTool("T03SlugScreen", "Имя файла") { T03SlugScreen(it) },
    WaveTool("T04RandomNameScreen", "Случайное имя") { T04RandomNameScreen(it) },
    WaveTool("T05BytesScreen", "Байты") { T05BytesScreen(it) },
    WaveTool("T06PercentScreen", "Проценты") { T06PercentScreen(it) },
    WaveTool("T07AspectScreen", "Соотношение сторон") { T07AspectScreen(it) },
    WaveTool("T08DurationScreen", "Длительность") { T08DurationScreen(it) },
    WaveTool("T09ChmodScreen", "Права chmod") { T09ChmodScreen(it) },
    WaveTool("T100LineCrcScreen", "CRC32 строк") { T100LineCrcScreen(it) },
    WaveTool("T10MimeScreen", "MIME") { T10MimeScreen(it) },
    WaveTool("T11ExtCountScreen", "Расширения") { T11ExtCountScreen(it) },
    WaveTool("T12PathJoinScreen", "Склейка путей") { T12PathJoinScreen(it) },
    WaveTool("T13DupLinesScreen", "Повторы строк") { T13DupLinesScreen(it) },
    WaveTool("T14SortLinesScreen", "Сортировка строк") { T14SortLinesScreen(it) },
    WaveTool("T15UniqueLinesScreen", "Уникальные строки") { T15UniqueLinesScreen(it) },
    WaveTool("T16ReverseLinesScreen", "Порядок строк") { T16ReverseLinesScreen(it) },
    WaveTool("T17NumberLinesScreen", "Нумерация строк") { T17NumberLinesScreen(it) },
    WaveTool("T18WrapScreen", "Перенос по колонкам") { T18WrapScreen(it) },
    WaveTool("T19PrefixScreen", "Префикс и суффикс") { T19PrefixScreen(it) },
    WaveTool("T20TrimScreen", "Обрезка строк") { T20TrimScreen(it) },
    WaveTool("T21CsvColumnScreen", "Столбец CSV") { T21CsvColumnScreen(it) },
    WaveTool("T22TsvCsvScreen", "TSV и CSV") { T22TsvCsvScreen(it) },
    WaveTool("T23HexDumpScreen", "HEX-дамп") { T23HexDumpScreen(it) },
    WaveTool("T24UrlEncodeScreen", "URL") { T24UrlEncodeScreen(it) },
    WaveTool("T25HtmlEscapeScreen", "HTML") { T25HtmlEscapeScreen(it) },
    WaveTool("T26QueryScreen", "Параметры запроса") { T26QueryScreen(it) },
    WaveTool("T27SemverScreen", "Сравнение версий") { T27SemverScreen(it) },
    WaveTool("T28Ipv4Screen", "IPv4") { T28Ipv4Screen(it) },
    WaveTool("T29CronScreen", "Выражение cron") { T29CronScreen(it) },
    WaveTool("T30SizeSumScreen", "Сумма размеров") { T30SizeSumScreen(it) },
    WaveTool("T31DistanceScreen", "Расстояние Левенштейна") { T31DistanceScreen(it) },
    WaveTool("T32LoremScreen", "Заполнитель") { T32LoremScreen(it) },
    WaveTool("T33PasswordScreen", "Пароль") { T33PasswordScreen(it) },
    WaveTool("T34DiceScreen", "Кости") { T34DiceScreen(it) },
    WaveTool("T35ShuffleScreen", "Перемешать строки") { T35ShuffleScreen(it) },
    WaveTool("T36WeekdayScreen", "День недели") { T36WeekdayScreen(it) },
    WaveTool("T37DaysBetweenScreen", "Дней между датами") { T37DaysBetweenScreen(it) },
    WaveTool("T38AddDaysScreen", "Прибавить дни") { T38AddDaysScreen(it) },
    WaveTool("T39RomanScreen", "Римские числа") { T39RomanScreen(it) },
    WaveTool("T40BaseConvertScreen", "Системы счисления") { T40BaseConvertScreen(it) },
    WaveTool("T41AverageScreen", "Среднее") { T41AverageScreen(it) },
    WaveTool("T42MedianScreen", "Медиана") { T42MedianScreen(it) },
    WaveTool("T43BitwiseScreen", "Побитовые") { T43BitwiseScreen(it) },
    WaveTool("T44CodepointsScreen", "Кодовые точки") { T44CodepointsScreen(it) },
    WaveTool("T45Utf8Screen", "UTF-8") { T45Utf8Screen(it) },
    WaveTool("T46ZeroWidthScreen", "Невидимые знаки") { T46ZeroWidthScreen(it) },
    WaveTool("T47HomoglyphScreen", "Омоглифы") { T47HomoglyphScreen(it) },
    WaveTool("T48SnakeScreen", "Snake-регистр") { T48SnakeScreen(it) },
    WaveTool("T49CamelScreen", "camelCase") { T49CamelScreen(it) },
    WaveTool("T50KebabScreen", "Kebab-case") { T50KebabScreen(it) },
    WaveTool("T51TranslitScreen", "Транслит") { T51TranslitScreen(it) },
    WaveTool("T52DiacriticsScreen", "Диакритика") { T52DiacriticsScreen(it) },
    WaveTool("T53CountSubScreen", "Вхождения") { T53CountSubScreen(it) },
    WaveTool("T54ReplaceScreen", "Замена текста") { T54ReplaceScreen(it) },
    WaveTool("T55ExtractUrlScreen", "Ссылки") { T55ExtractUrlScreen(it) },
    WaveTool("T56EmailsScreen", "Адреса почты") { T56EmailsScreen(it) },
    WaveTool("T57HeadTailScreen", "Начало и конец") { T57HeadTailScreen(it) },
    WaveTool("T58NatoScreen", "НАТО") { T58NatoScreen(it) },
    WaveTool("T59ContrastScreen", "Контраст") { T59ContrastScreen(it) },
    WaveTool("T60DpPxScreen", "px и dp") { T60DpPxScreen(it) },
    WaveTool("T61JsonMinScreen", "Мини JSON") { T61JsonMinScreen(it) },
    WaveTool("T62XmlEscapeScreen", "XML") { T62XmlEscapeScreen(it) },
    WaveTool("T63SqlEscapeScreen", "SQL-литерал") { T63SqlEscapeScreen(it) },
    WaveTool("T64EnvScreen", "Переменные") { T64EnvScreen(it) },
    WaveTool("T65HostsScreen", "Hosts") { T65HostsScreen(it) },
    WaveTool("T66SplitScreen", "Разбить на строки") { T66SplitScreen(it) },
    WaveTool("T67JoinScreen", "Склейка строк") { T67JoinScreen(it) },
    WaveTool("T68PadNumbersScreen", "Нули слева") { T68PadNumbersScreen(it) },
    WaveTool("T69RenamePreviewScreen", "Предпросмотр переименования") { T69RenamePreviewScreen(it) },
    WaveTool("T70QuoteScreen", "Кавычки") { T70QuoteScreen(it) },
    WaveTool("T71MixColorScreen", "Смешение 50/50") { T71MixColorScreen(it) },
    WaveTool("T72PercentChangeScreen", "Изменение в процентах") { T72PercentChangeScreen(it) },
    WaveTool("T73FractionScreen", "Дробь") { T73FractionScreen(it) },
    WaveTool("T74JwtScreen", "JWT") { T74JwtScreen(it) },
    WaveTool("T75IniScreen", "Разбор INI") { T75IniScreen(it) },
    WaveTool("T76UuidCheckScreen", "Проверка UUID") { T76UuidCheckScreen(it) },
    WaveTool("T77MacScreen", "MAC-адрес") { T77MacScreen(it) },
    WaveTool("T78PortScreen", "Порты") { T78PortScreen(it) },
    WaveTool("T79TempScreen", "Температура") { T79TempScreen(it) },
    WaveTool("T80AngleScreen", "Углы") { T80AngleScreen(it) },
    WaveTool("T81TextHashScreen", "Хеш текста") { T81TextHashScreen(it) },
    WaveTool("T82Rot13Screen", "ROT13") { T82Rot13Screen(it) },
    WaveTool("T83MorseScreen", "Морзе") { T83MorseScreen(it) },
    WaveTool("T84ColumnIndexScreen", "Индекс столбца") { T84ColumnIndexScreen(it) },
    WaveTool("T85RwxScreen", "Права rwx") { T85RwxScreen(it) },
    WaveTool("T86IndentScreen", "Замена отступа") { T86IndentScreen(it) },
    WaveTool("T87BlankScreen", "Пустые строки") { T87BlankScreen(it) },
    WaveTool("T88AlignScreen", "Ровные столбцы") { T88AlignScreen(it) },
    WaveTool("T89JsonKeysScreen", "Ключи JSON") { T89JsonKeysScreen(it) },
    WaveTool("T90StampListScreen", "Метки времени") { T90StampListScreen(it) },
    WaveTool("T91RepeatScreen", "Повтор строки") { T91RepeatScreen(it) },
    WaveTool("T92TruncateScreen", "Обрезка текста") { T92TruncateScreen(it) },
    WaveTool("T93CenterScreen", "Центр строк") { T93CenterScreen(it) },
    WaveTool("T94WordsPerLineScreen", "Слова по строкам") { T94WordsPerLineScreen(it) },
    WaveTool("T95PalindromeScreen", "Палиндром") { T95PalindromeScreen(it) },
    WaveTool("T96AnagramScreen", "Анаграммы") { T96AnagramScreen(it) },
    WaveTool("T97RleScreen", "RLE") { T97RleScreen(it) },
    WaveTool("T98BracketsScreen", "Баланс скобок") { T98BracketsScreen(it) },
    WaveTool("T99EscapesScreen", "Escape") { T99EscapesScreen(it) },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaveHubScreen(onBack: () -> Unit) {
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    val tool = tools.firstOrNull { it.id == page }
    if (tool != null) {
        tool.screen { page = null }
        return
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Мастерская") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(tools, key = { it.id }) { item ->
                ListItem(
                    headlineContent = { Text(item.title) },
                    modifier = Modifier.fillMaxWidth().clickable { page = item.id },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
