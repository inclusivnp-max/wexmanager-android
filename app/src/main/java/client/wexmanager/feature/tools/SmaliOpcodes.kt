package client.wexmanager.feature.tools

import androidx.compose.runtime.Immutable

// Статичный справочник инструкций Dalvik (DEX 035–039) и синтаксиса smali/baksmali.

enum class SmaliCategory(val title: String) {
    Move("Перемещение"),
    Const("Константы"),
    Monitor("Мониторы/проверки типов"),
    Array("Массивы"),
    Flow("Исключения/переходы"),
    Compare("Сравнения"),
    Field("Поля"),
    Invoke("Вызовы"),
    Unary("Унарные"),
    Binary("Бинарные"),
    Addr2("/2addr"),
    Lit("/lit"),
    Other("Прочее"),
}

@Immutable
class SmaliOpcode(
    val code: Int,
    val name: String,
    val format: String,
    val syntax: String,
    val description: String,
    val category: SmaliCategory,
    /** Когда инструкция бросает исключение (null — не бросает). */
    val throws: String? = null,
    /** С какой версии DEX/Android доступна (null — с самой первой). */
    val since: String? = null,
    /** Для диапазона неиспользуемых опкодов — последний код диапазона. */
    val lastCode: Int = code,
    val unused: Boolean = false,
) {
    val hex: String = if (lastCode != code) "%02X–%02X".format(code, lastCode) else "%02X".format(code)

    /** Текст для поиска (в нижнем регистре): мнемоника, формат, описание, исключения. */
    val searchText: String = buildString {
        append(name).append('\n').append(format).append('\n').append(description)
        throws?.let { append('\n').append(it) }
    }.lowercase()

    fun covers(c: Int) = c in code..lastCode
}

/** Пояснения к идентификаторам форматов (первая цифра — слов по 16 бит, вторая — регистров). */
val SmaliFormats: Map<String, String> = mapOf(
    "10x" to "1 слово, без операндов",
    "12x" to "1 слово: два регистра по 4 бита (v0–v15)",
    "11n" to "1 слово: регистр 4 бита + знаковая константа 4 бита",
    "11x" to "1 слово: регистр 8 бит (v0–v255)",
    "10t" to "1 слово: знаковое смещение перехода 8 бит",
    "20t" to "2 слова: знаковое смещение перехода 16 бит",
    "22x" to "2 слова: регистр 8 бит + регистр 16 бит",
    "21t" to "2 слова: регистр 8 бит + смещение перехода 16 бит",
    "21s" to "2 слова: регистр 8 бит + знаковая константа 16 бит",
    "21h" to "2 слова: регистр 8 бит + старшие 16 бит константы",
    "21c" to "2 слова: регистр 8 бит + индекс 16 бит (строка, тип, поле, …)",
    "23x" to "2 слова: три регистра по 8 бит",
    "22b" to "2 слова: два регистра по 8 бит + знаковая константа 8 бит",
    "22t" to "2 слова: два регистра по 4 бита + смещение перехода 16 бит",
    "22s" to "2 слова: два регистра по 4 бита + знаковая константа 16 бит",
    "22c" to "2 слова: два регистра по 4 бита + индекс 16 бит (тип или поле)",
    "30t" to "3 слова: знаковое смещение перехода 32 бита",
    "32x" to "3 слова: два регистра по 16 бит (v0–v65535)",
    "31i" to "3 слова: регистр 8 бит + константа 32 бита",
    "31t" to "3 слова: регистр 8 бит + смещение 32 бита до блока данных",
    "31c" to "3 слова: регистр 8 бит + индекс строки 32 бита",
    "35c" to "3 слова: до 5 регистров по 4 бита + индекс 16 бит",
    "3rc" to "3 слова: диапазон до 255 подряд идущих регистров + индекс 16 бит",
    "45cc" to "4 слова: до 5 регистров по 4 бита + индекс метода + индекс прототипа",
    "4rcc" to "4 слова: диапазон регистров + индекс метода + индекс прототипа",
    "51l" to "5 слов: регистр 8 бит + константа 64 бита",
)

private const val NPE = "NullPointerException — объект равен null"
private const val ARRAY_THROWS = "NullPointerException — массив равен null; ArrayIndexOutOfBoundsException — индекс вне границ"
private const val INSTANCE_FIELD_THROWS = "NullPointerException — объект равен null; NoSuchFieldError / IllegalAccessError — при разрешении поля"
private const val STATIC_FIELD_THROWS = "ExceptionInInitializerError — ошибка в <clinit> класса; NoSuchFieldError / IllegalAccessError — при разрешении поля"
private const val DIV_THROWS = "ArithmeticException — деление на ноль"
private const val ANDROID_O = "DEX 038 · Android 8.0 (API 26)"
private const val ANDROID_P = "DEX 039 · Android 9 (API 28)"

val SmaliOpcodes: List<SmaliOpcode> = buildSmaliOpcodes()

/** Опкоды по уже заданной [SmaliCategory]. Порядок категорий — как они впервые встречаются в списке. */
fun grouped(): Map<SmaliCategory, List<SmaliOpcode>> = SmaliOpcodes.groupBy { it.category }

private fun buildSmaliOpcodes(): List<SmaliOpcode> {
    val list = ArrayList<SmaliOpcode>(232)
    fun op(
        code: Int, name: String, format: String, syntax: String, description: String, category: SmaliCategory,
        throws: String? = null, since: String? = null,
    ) {
        list += SmaliOpcode(code, name, format, syntax, description, category, throws, since)
    }
    fun unused(from: Int, to: Int, note: String) {
        list += SmaliOpcode(
            code = from, lastCode = to, name = "не используется", format = "10x", syntax = "",
            description = note, category = SmaliCategory.Other, unused = true,
        )
    }

    // ---------------------------------------------------------------- Перемещение
    op(0x00, "nop", "10x", "nop", "Пустая операция: ничего не делает. Код 0x00 также служит префиксом блоков данных (packed-switch, sparse-switch, array-data).", SmaliCategory.Other)
    op(0x01, "move", "12x", "move v0, v1", "Копирует значение (не объект) из vB в vA. Оба регистра — v0–v15.", SmaliCategory.Move)
    op(0x02, "move/from16", "22x", "move/from16 v0, v256", "Копирует значение (не объект) из vBBBB (до v65535) в vAA (до v255).", SmaliCategory.Move)
    op(0x03, "move/16", "32x", "move/16 v256, v1024", "Копирует значение (не объект) между регистрами v0–v65535.", SmaliCategory.Move)
    op(0x04, "move-wide", "12x", "move-wide v0, v2", "Копирует пару регистров (long/double) vB, vB+1 в vA, vA+1. Перекрытие пар допускается.", SmaliCategory.Move)
    op(0x05, "move-wide/from16", "22x", "move-wide/from16 v0, v256", "Копирует пару регистров (long/double) из vBBBB в vAA.", SmaliCategory.Move)
    op(0x06, "move-wide/16", "32x", "move-wide/16 v256, v1024", "Копирует пару регистров (long/double) между регистрами v0–v65535.", SmaliCategory.Move)
    op(0x07, "move-object", "12x", "move-object v0, p0", "Копирует ссылку на объект из vB в vA (v0–v15).", SmaliCategory.Move)
    op(0x08, "move-object/from16", "22x", "move-object/from16 v0, v256", "Копирует ссылку на объект из vBBBB в vAA.", SmaliCategory.Move)
    op(0x09, "move-object/16", "32x", "move-object/16 v256, v1024", "Копирует ссылку на объект между регистрами v0–v65535.", SmaliCategory.Move)
    op(0x0a, "move-result", "11x", "move-result v0", "Сохраняет в vAA одиночный (не объектный) результат последнего invoke-*. Должна идти сразу после вызова.", SmaliCategory.Move)
    op(0x0b, "move-result-wide", "11x", "move-result-wide v0", "Сохраняет в пару vAA, vAA+1 результат long/double последнего invoke-*. Должна идти сразу после вызова.", SmaliCategory.Move)
    op(0x0c, "move-result-object", "11x", "move-result-object v0", "Сохраняет в vAA ссылку-результат последнего invoke-* или filled-new-array. Должна идти сразу после него.", SmaliCategory.Move)
    op(0x0d, "move-exception", "11x", "move-exception v0", "Сохраняет в vAA только что пойманное исключение. Допустима только первой инструкцией обработчика .catch.", SmaliCategory.Move)

    // ---------------------------------------------------------------- Возврат
    op(0x0e, "return-void", "10x", "return-void", "Возврат из метода с типом V (void).", SmaliCategory.Flow)
    op(0x0f, "return", "11x", "return v0", "Возврат 32-битного значения (int, float, boolean, byte, char, short) из vAA.", SmaliCategory.Flow)
    op(0x10, "return-wide", "11x", "return-wide v0", "Возврат 64-битного значения (long/double) из пары vAA, vAA+1.", SmaliCategory.Flow)
    op(0x11, "return-object", "11x", "return-object v0", "Возврат ссылки на объект из vAA.", SmaliCategory.Flow)

    // ---------------------------------------------------------------- Константы
    op(0x12, "const/4", "11n", "const/4 v0, 0x1", "Загружает в vA (v0–v15) 4-битную знаковую константу (−8…7), расширенную до 32 бит.", SmaliCategory.Const)
    op(0x13, "const/16", "21s", "const/16 v0, 0x64", "Загружает в vAA 16-битную знаковую константу (−32768…32767), расширенную до 32 бит.", SmaliCategory.Const)
    op(0x14, "const", "31i", "const v0, 0x7f0b0012", "Загружает в vAA произвольную 32-битную константу (часто — ID ресурса R.*).", SmaliCategory.Const)
    op(0x15, "const/high16", "21h", "const/high16 v0, 0x7f030000", "Загружает в vAA 16 бит в старшую половину 32-битного значения (младшие биты — нули). Удобно для float и ID ресурсов.", SmaliCategory.Const)
    op(0x16, "const-wide/16", "21s", "const-wide/16 v0, 0x3e8", "Загружает в пару vAA, vAA+1 16-битную знаковую константу, расширенную до 64 бит.", SmaliCategory.Const)
    op(0x17, "const-wide/32", "31i", "const-wide/32 v0, 0x100000", "Загружает в пару vAA, vAA+1 32-битную знаковую константу, расширенную до 64 бит.", SmaliCategory.Const)
    op(0x18, "const-wide", "51l", "const-wide v0, 0x123456789abcdefL", "Загружает в пару vAA, vAA+1 произвольную 64-битную константу (long/double).", SmaliCategory.Const)
    op(0x19, "const-wide/high16", "21h", "const-wide/high16 v0, 0x4024000000000000L", "Загружает 16 бит в старшие биты 64-битного значения (остальные — нули). Обычно для double: 0x4024… = 10.0.", SmaliCategory.Const)
    op(0x1a, "const-string", "21c", "const-string v0, \"Hello\"", "Загружает в vAA ссылку на строку из пула строк (индекс до 65535).", SmaliCategory.Const)
    op(0x1b, "const-string/jumbo", "31c", "const-string/jumbo v0, \"Hello\"", "То же, что const-string, но с 32-битным индексом строки — для DEX с более чем 65535 строками.", SmaliCategory.Const)
    op(0x1c, "const-class", "21c", "const-class v0, Lcom/a/B;", "Загружает в vAA объект Class для указанного типа (аналог B.class).", SmaliCategory.Const, throws = "NoClassDefFoundError — класс не найден")

    // ---------------------------------------------------------------- Мониторы и проверки типов
    op(0x1d, "monitor-enter", "11x", "monitor-enter p0", "Захватывает монитор объекта vAA (начало блока synchronized).", SmaliCategory.Monitor, throws = NPE)
    op(0x1e, "monitor-exit", "11x", "monitor-exit p0", "Освобождает монитор объекта vAA. Компилятор оборачивает блок в .catchall, чтобы монитор освобождался и при исключении.", SmaliCategory.Monitor, throws = "NullPointerException — объект равен null; IllegalMonitorStateException — монитор не захвачен этим потоком")
    op(0x1f, "check-cast", "21c", "check-cast v0, Ljava/lang/String;", "Проверяет, что ссылка в vAA приводится к типу (явное приведение (String) x). null проходит проверку.", SmaliCategory.Monitor, throws = "ClassCastException — объект нельзя привести к типу")
    op(0x20, "instance-of", "22c", "instance-of v0, v1, Ljava/lang/String;", "Записывает в vA 1, если vB — экземпляр типа, иначе 0 (для null — 0). Аналог instanceof.", SmaliCategory.Monitor)

    // ---------------------------------------------------------------- Массивы и объекты
    op(0x21, "array-length", "12x", "array-length v0, v1", "Записывает в vA длину массива из vB.", SmaliCategory.Array, throws = "NullPointerException — массив равен null")
    op(0x22, "new-instance", "21c", "new-instance v0, Ljava/lang/StringBuilder;", "Создаёт объект указанного класса (не массива) в vAA. Конструктор не вызывается — далее нужен invoke-direct …-><init>.", SmaliCategory.Other, throws = "InstantiationError — класс абстрактный или интерфейс; ExceptionInInitializerError")
    op(0x23, "new-array", "22c", "new-array v0, v1, [I", "Создаёт в vA массив указанного типа длиной vB.", SmaliCategory.Array, throws = "NegativeArraySizeException — отрицательная длина")
    op(0x24, "filled-new-array", "35c", "filled-new-array {v0, v1, v2}, [I", "Создаёт массив из значений до 5 регистров. Результат забирается следующей move-result-object. Элементы только 32-битные (без long/double); на практике — массивы int и ссылок.", SmaliCategory.Array)
    op(0x25, "filled-new-array/range", "3rc", "filled-new-array/range {v0 .. v5}, [I", "То же, что filled-new-array, но значения берутся из диапазона регистров.", SmaliCategory.Array)
    op(0x26, "fill-array-data", "31t", "fill-array-data v0, :array_0", "Заполняет массив vAA данными из блока .array-data по метке.", SmaliCategory.Array, throws = "NullPointerException — массив равен null; ArrayIndexOutOfBoundsException — данных больше длины массива")

    // ---------------------------------------------------------------- Исключения и переходы
    op(0x27, "throw", "11x", "throw v0", "Бросает исключение-объект из vAA (наследник Throwable).", SmaliCategory.Flow, throws = "NullPointerException — если vAA равен null")
    op(0x28, "goto", "10t", "goto :goto_0", "Безусловный переход на 8-битное знаковое смещение (±127 слов). Смещение 0 недопустимо.", SmaliCategory.Flow)
    op(0x29, "goto/16", "20t", "goto/16 :goto_1", "Безусловный переход на 16-битное знаковое смещение. Смещение 0 недопустимо.", SmaliCategory.Flow)
    op(0x2a, "goto/32", "30t", "goto/32 :goto_2", "Безусловный переход на 32-битное смещение. Единственный goto, допускающий смещение 0 (бесконечный цикл).", SmaliCategory.Flow)
    op(0x2b, "packed-switch", "31t", "packed-switch v0, :pswitch_data_0", "Переход по таблице подряд идущих значений (блок .packed-switch). Если vAA не найден — выполнение продолжается дальше.", SmaliCategory.Flow)
    op(0x2c, "sparse-switch", "31t", "sparse-switch v0, :sswitch_data_0", "Переход по таблице разреженных пар «значение → метка» (блок .sparse-switch). Если совпадения нет — выполнение продолжается дальше.", SmaliCategory.Flow)

    // ---------------------------------------------------------------- Сравнения
    op(0x2d, "cmpl-float", "23x", "cmpl-float v0, v1, v2", "Сравнивает float vBB и vCC: результат в vAA — 0 (равны), 1 (b > c), −1 (b < c). Для NaN — −1 (смещение «меньше»).", SmaliCategory.Compare)
    op(0x2e, "cmpg-float", "23x", "cmpg-float v0, v1, v2", "Сравнивает float vBB и vCC: 0, 1 или −1. Для NaN — 1 (смещение «больше»).", SmaliCategory.Compare)
    op(0x2f, "cmpl-double", "23x", "cmpl-double v0, v2, v4", "Сравнивает double (пары регистров) vBB и vCC: 0, 1 или −1. Для NaN — −1.", SmaliCategory.Compare)
    op(0x30, "cmpg-double", "23x", "cmpg-double v0, v2, v4", "Сравнивает double (пары регистров) vBB и vCC: 0, 1 или −1. Для NaN — 1.", SmaliCategory.Compare)
    op(0x31, "cmp-long", "23x", "cmp-long v0, v1, v3", "Сравнивает long (пары регистров) vBB и vCC: результат в vAA — 0, 1 или −1.", SmaliCategory.Compare)
    val ifTests = listOf(
        "eq" to "vA == vB (int или ссылки)", "ne" to "vA != vB (int или ссылки)", "lt" to "vA < vB",
        "ge" to "vA >= vB", "gt" to "vA > vB", "le" to "vA <= vB",
    )
    ifTests.forEachIndexed { i, (k, cond) ->
        op(0x32 + i, "if-$k", "22t", "if-$k v0, v1, :cond_0", "Переход на метку, если $cond. Регистры v0–v15, смещение 16 бит.", SmaliCategory.Compare)
    }
    val ifZero = listOf(
        "eqz" to "vAA == 0 (для ссылок — null, для boolean — false)", "nez" to "vAA != 0 (для ссылок — не null, для boolean — true)",
        "ltz" to "vAA < 0", "gez" to "vAA >= 0", "gtz" to "vAA > 0", "lez" to "vAA <= 0",
    )
    ifZero.forEachIndexed { i, (k, cond) ->
        op(0x38 + i, "if-$k", "21t", "if-$k v0, :cond_0", "Переход на метку, если $cond. Сравнение с нулём, смещение 16 бит.", SmaliCategory.Compare)
    }
    unused(0x3e, 0x43, "Опкоды зарезервированы и не используются ни в одной версии DEX.")

    // ---------------------------------------------------------------- aget/aput
    class Kind(val suffix: String, val what: String, val desc: String, val sample: String)
    val kinds = listOf(
        Kind("", "int/float", "I (или F)", "count:I"),
        Kind("-wide", "long/double (пара регистров)", "J", "time:J"),
        Kind("-object", "ссылку на объект", "Ljava/lang/String;", "name:Ljava/lang/String;"),
        Kind("-boolean", "boolean", "Z", "enabled:Z"),
        Kind("-byte", "byte", "B", "flag:B"),
        Kind("-char", "char", "C", "letter:C"),
        Kind("-short", "short", "S", "port:S"),
    )
    kinds.forEachIndexed { i, k ->
        val regs = if (k.suffix == "-wide") "aget-wide v0, v2, v3" else "aget${k.suffix} v0, v1, v2"
        op(0x44 + i, "aget${k.suffix}", "23x", regs, "Читает элемент массива vBB по индексу vCC и записывает ${k.what} в vAA.", SmaliCategory.Array, throws = ARRAY_THROWS)
    }
    kinds.forEachIndexed { i, k ->
        val regs = if (k.suffix == "-wide") "aput-wide v0, v2, v3" else "aput${k.suffix} v0, v1, v2"
        val throws = if (k.suffix == "-object") "$ARRAY_THROWS; ArrayStoreException — тип объекта несовместим с массивом" else ARRAY_THROWS
        op(0x4b + i, "aput${k.suffix}", "23x", regs, "Записывает ${k.what} из vAA в элемент массива vBB по индексу vCC.", SmaliCategory.Array, throws = throws)
    }

    // ---------------------------------------------------------------- iget/iput, sget/sput
    kinds.forEachIndexed { i, k ->
        op(0x52 + i, "iget${k.suffix}", "22c", "iget${k.suffix} v0, p0, Lcom/a/B;->${k.sample}", "Читает поле экземпляра объекта vB и записывает ${k.what} в vA. Тип поля: ${k.desc}", SmaliCategory.Field, throws = INSTANCE_FIELD_THROWS)
    }
    kinds.forEachIndexed { i, k ->
        op(0x59 + i, "iput${k.suffix}", "22c", "iput${k.suffix} v0, p0, Lcom/a/B;->${k.sample}", "Записывает ${k.what} из vA в поле экземпляра объекта vB. Тип поля: ${k.desc}", SmaliCategory.Field, throws = INSTANCE_FIELD_THROWS)
    }
    kinds.forEachIndexed { i, k ->
        val name = k.sample.uppercase().substringBefore(':') + ":" + k.sample.substringAfter(':')
        op(0x60 + i, "sget${k.suffix}", "21c", "sget${k.suffix} v0, Lcom/a/B;->$name", "Читает статическое поле класса и записывает ${k.what} в vAA. При первом обращении инициализирует класс (<clinit>).", SmaliCategory.Field, throws = STATIC_FIELD_THROWS)
    }
    kinds.forEachIndexed { i, k ->
        val name = k.sample.uppercase().substringBefore(':') + ":" + k.sample.substringAfter(':')
        op(0x67 + i, "sput${k.suffix}", "21c", "sput${k.suffix} v0, Lcom/a/B;->$name", "Записывает ${k.what} из vAA в статическое поле класса. При первом обращении инициализирует класс (<clinit>).", SmaliCategory.Field, throws = STATIC_FIELD_THROWS)
    }

    // ---------------------------------------------------------------- invoke-*
    class Inv(val kind: String, val syntax: String, val rangeSyntax: String, val desc: String, val throws: String)
    val linkErr = "NoSuchMethodError / IllegalAccessError — при разрешении метода"
    val invokes = listOf(
        Inv(
            "virtual", "invoke-virtual {p0, v1}, Lcom/a/B;->m(I)V",
            "invoke-virtual/range {v0 .. v6}, Lcom/a/B;->m(IIIIII)V",
            "Вызов обычного виртуального метода (не private, не static, не конструктор). Первый регистр — объект-получатель, далее аргументы; wide-аргументы занимают два регистра. Результат — следующей move-result*.",
            "$NPE; AbstractMethodError — метод не реализован; $linkErr",
        ),
        Inv(
            "super", "invoke-super {p0, p1}, Landroid/app/Activity;->onCreate(Landroid/os/Bundle;)V",
            "invoke-super/range {p0 .. p6}, Lcom/a/Base;->m(IIIIII)V",
            "Вызов реализации метода из суперкласса (super.m()). Для интерфейса — вызов default-метода родительского интерфейса.",
            "$NPE; AbstractMethodError; $linkErr",
        ),
        Inv(
            "direct", "invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V",
            "invoke-direct/range {v0 .. v5}, Lcom/a/B;-><init>(IIIII)V",
            "Невиртуальный вызов метода экземпляра: private-методы и конструкторы <init>. Первый регистр — объект.",
            "$NPE; $linkErr",
        ),
        Inv(
            "static", "invoke-static {v0}, Ljava/lang/String;->valueOf(I)Ljava/lang/String;",
            "invoke-static/range {v0 .. v5}, Lcom/a/B;->m(IIIIII)V",
            "Вызов статического метода. Объекта-получателя нет — все регистры являются аргументами. При первом вызове инициализирует класс.",
            "ExceptionInInitializerError — ошибка в <clinit>; $linkErr",
        ),
        Inv(
            "interface", "invoke-interface {v0}, Ljava/util/List;->size()I",
            "invoke-interface/range {v0 .. v5}, Lcom/a/I;->m(IIIII)V",
            "Вызов метода интерфейса у объекта, конкретный класс которого неизвестен. Первый регистр — объект.",
            "$NPE; IncompatibleClassChangeError — объект не реализует интерфейс; AbstractMethodError",
        ),
    )
    invokes.forEachIndexed { i, v ->
        op(0x6e + i, "invoke-${v.kind}", "35c", v.syntax, v.desc, SmaliCategory.Invoke, throws = v.throws)
    }
    unused(0x73, 0x73, "Не используется в .dex. В оптимизированных odex/vdex старых версий ART мог применяться как внутренняя инструкция (return-void-no-barrier).")
    invokes.forEachIndexed { i, v ->
        op(
            0x74 + i, "invoke-${v.kind}/range", "3rc", v.rangeSyntax,
            "То же, что invoke-${v.kind}, но аргументы — непрерывный диапазон регистров {vCCCC .. vNNNN}. Нужна, когда аргументов больше 5 или номера регистров больше v15.",
            SmaliCategory.Invoke, throws = v.throws,
        )
    }
    unused(0x79, 0x7a, "Опкоды зарезервированы и не используются.")

    // ---------------------------------------------------------------- Унарные
    val unary = listOf(
        "neg-int" to "vA = −vB (int)", "not-int" to "vA = ~vB — побитовое НЕ (int)",
        "neg-long" to "vA = −vB (long, пары регистров)", "not-long" to "vA = ~vB — побитовое НЕ (long)",
        "neg-float" to "vA = −vB (float)", "neg-double" to "vA = −vB (double, пары регистров)",
        "int-to-long" to "Преобразует int vB в long vA (пара), со знаком", "int-to-float" to "Преобразует int vB в float vA",
        "int-to-double" to "Преобразует int vB в double vA (пара)", "long-to-int" to "Преобразует long vB (пара) в int vA — отбрасывает старшие биты",
        "long-to-float" to "Преобразует long vB (пара) в float vA", "long-to-double" to "Преобразует long vB в double vA (обе — пары)",
        "float-to-int" to "Преобразует float vB в int vA с округлением к нулю (NaN → 0, насыщение)", "float-to-long" to "Преобразует float vB в long vA (пара), округление к нулю",
        "float-to-double" to "Преобразует float vB в double vA (пара)", "double-to-int" to "Преобразует double vB (пара) в int vA, округление к нулю (NaN → 0)",
        "double-to-long" to "Преобразует double vB в long vA (обе — пары), округление к нулю", "double-to-float" to "Преобразует double vB (пара) в float vA",
        "int-to-byte" to "Обрезает int vB до byte (8 бит со знаком) и записывает в vA", "int-to-char" to "Обрезает int vB до char (16 бит без знака) и записывает в vA",
        "int-to-short" to "Обрезает int vB до short (16 бит со знаком) и записывает в vA",
    )
    unary.forEachIndexed { i, (name, d) ->
        val wideDst = name.endsWith("-long") || name.endsWith("-double")
        val wideSrc = name.startsWith("long-") || name.startsWith("double-")
        val syntax = "$name v0, ${if (wideDst || wideSrc) "v2" else "v1"}"
        op(0x7b + i, name, "12x", syntax, "$d.", SmaliCategory.Unary)
    }

    // ---------------------------------------------------------------- Бинарные (23x, 2addr)
    class Bin(val op: String, val sign: String, val title: String)
    val intOps = listOf(
        Bin("add", "+", "сложение"), Bin("sub", "−", "вычитание"), Bin("mul", "×", "умножение"),
        Bin("div", "/", "деление"), Bin("rem", "%", "остаток от деления"), Bin("and", "&", "побитовое И"),
        Bin("or", "|", "побитовое ИЛИ"), Bin("xor", "^", "исключающее ИЛИ"), Bin("shl", "<<", "сдвиг влево"),
        Bin("shr", ">>", "арифметический сдвиг вправо (со знаком)"), Bin("ushr", ">>>", "логический сдвиг вправо (без знака)"),
    )
    val floatOps = intOps.take(5)
    val binTable = buildList {
        intOps.forEach { add(it to "int") }
        intOps.forEach { add(it to "long") }
        floatOps.forEach { add(it to "float") }
        floatOps.forEach { add(it to "double") }
    }
    fun binNote(b: Bin, type: String): String = when {
        (b.op == "div" || b.op == "rem") && (type == "int" || type == "long") -> " Деление целочисленное, с округлением к нулю."
        b.op == "rem" -> " Остаток вычисляется как fmod (не IEEE 754 remainder)."
        b.op.startsWith("sh") || b.op == "ushr" ->
            if (type == "long") " Величина сдвига — одиночный int-регистр, используются младшие 6 бит." else " Используются младшие 5 бит величины сдвига."
        type == "long" || type == "double" -> " Операнды и результат — пары регистров."
        else -> ""
    }
    fun binThrows(b: Bin, type: String) =
        if ((b.op == "div" || b.op == "rem") && (type == "int" || type == "long")) DIV_THROWS else null
    binTable.forEachIndexed { i, (b, type) ->
        val name = "${b.op}-$type"
        val syntax = if (type == "long" || type == "double") "$name v0, v2, v4" else "$name v0, v1, v2"
        op(0x90 + i, name, "23x", syntax, "vAA = vBB ${b.sign} vCC — ${b.title} ($type).${binNote(b, type)}", SmaliCategory.Binary, throws = binThrows(b, type))
    }
    binTable.forEachIndexed { i, (b, type) ->
        val name = "${b.op}-$type/2addr"
        val syntax = if (type == "long" || type == "double") "$name v0, v2" else "$name v0, v1"
        op(0xb0 + i, name, "12x", syntax, "vA = vA ${b.sign} vB — ${b.title} ($type) «на месте»: результат записывается в первый операнд. Регистры v0–v15.${binNote(b, type)}", SmaliCategory.Addr2, throws = binThrows(b, type))
    }

    // ---------------------------------------------------------------- /lit16, /lit8
    val lit16 = listOf(intOps[0], Bin("rsub", "−", "обратное вычитание")) + intOps.subList(2, 8)
    lit16.forEachIndexed { i, b ->
        val name = if (b.op == "rsub") "rsub-int" else "${b.op}-int/lit16"
        val expr = if (b.op == "rsub") "vA = #CCCC − vB" else "vA = vB ${b.sign} #CCCC"
        op(
            0xd0 + i, name, "22s", "$name v0, v1, 0x3e8",
            "$expr — ${b.title} (int) с 16-битной знаковой константой.${binNote(b, "int")}",
            SmaliCategory.Lit, throws = binThrows(b, "int"),
        )
    }
    val lit8 = listOf(intOps[0], Bin("rsub", "−", "обратное вычитание")) + intOps.subList(2, 11)
    lit8.forEachIndexed { i, b ->
        val name = "${b.op}-int/lit8"
        val expr = if (b.op == "rsub") "vAA = #CC − vBB" else "vAA = vBB ${b.sign} #CC"
        op(
            0xd8 + i, name, "22b", "$name v0, v0, 0x1",
            "$expr — ${b.title} (int) с 8-битной знаковой константой.${binNote(b, "int")}",
            SmaliCategory.Lit, throws = binThrows(b, "int"),
        )
    }
    unused(0xe3, 0xf9, "Не используются в .dex. В odex/vdex старых версий Dalvik/ART здесь были внутренние «quick»-инструкции (iget-quick, invoke-virtual-quick, execute-inline и т.п.).")

    // ---------------------------------------------------------------- Новые инструкции (DEX 038/039)
    op(
        0xfa, "invoke-polymorphic", "45cc",
        "invoke-polymorphic {v0, v1}, Ljava/lang/invoke/MethodHandle;->invokeExact([Ljava/lang/Object;)Ljava/lang/Object;, (I)V",
        "Сигнатурно-полиморфный вызов MethodHandle.invoke/invokeExact (и VarHandle). Первый регистр — дескриптор метода, далее аргументы; последний операнд — фактический прототип вызова.",
        SmaliCategory.Invoke, throws = "NullPointerException — дескриптор равен null; WrongMethodTypeException — несовпадение типа", since = ANDROID_O,
    )
    op(
        0xfb, "invoke-polymorphic/range", "4rcc",
        "invoke-polymorphic/range {v0 .. v6}, Ljava/lang/invoke/MethodHandle;->invoke([Ljava/lang/Object;)Ljava/lang/Object;, (IIIIII)V",
        "То же, что invoke-polymorphic, но аргументы — диапазон регистров.",
        SmaliCategory.Invoke, throws = "NullPointerException; WrongMethodTypeException", since = ANDROID_O,
    )
    op(
        0xfc, "invoke-custom", "35c",
        "invoke-custom {v0}, call_site_0(\"run\", (I)V)@Lcom/a/Bsm;->bootstrap(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
        "Вызов через call site (аналог invokedynamic): при первом выполнении вызывается bootstrap-метод, возвращающий CallSite, далее вызывается его цель.",
        SmaliCategory.Invoke, throws = "BootstrapMethodError — ошибка bootstrap-метода", since = ANDROID_O,
    )
    op(
        0xfd, "invoke-custom/range", "3rc",
        "invoke-custom/range {v0 .. v5}, call_site_1(\"run\", (IIIIII)V)@Lcom/a/Bsm;->bootstrap(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
        "То же, что invoke-custom, но аргументы — диапазон регистров.",
        SmaliCategory.Invoke, throws = "BootstrapMethodError — ошибка bootstrap-метода", since = ANDROID_O,
    )
    op(
        0xfe, "const-method-handle", "21c", "const-method-handle v0, invoke-static@Lcom/a/B;->m(I)V",
        "Загружает в vAA ссылку на java.lang.invoke.MethodHandle по индексу дескриптора метода.",
        SmaliCategory.Const, since = ANDROID_P,
    )
    op(
        0xff, "const-method-type", "21c", "const-method-type v0, (II)I",
        "Загружает в vAA ссылку на java.lang.invoke.MethodType по индексу прототипа.",
        SmaliCategory.Const, since = ANDROID_P,
    )
    return list
}

// ------------------------------------------------------------------ Справочные разделы

@Immutable
class SmaliRefEntry(val term: String, val description: String, val example: String? = null) {
    val searchText: String = "$term\n$description\n${example.orEmpty()}".lowercase()
}

@Immutable
class SmaliRefSection(val id: String, val title: String, val intro: String, val entries: List<SmaliRefEntry>)

private fun e(term: String, description: String, example: String? = null) = SmaliRefEntry(term, description, example)

val SmaliReferenceSections: List<SmaliRefSection> = listOf(
    SmaliRefSection(
        id = "types",
        title = "Типы и дескрипторы",
        intro = "Типы в smali записываются дескрипторами JVM: примитивы — одной буквой, классы — L…;, массивы — префиксом [.",
        entries = listOf(
            e("V", "void — «нет значения». Допустим только как тип возврата метода.", "->run()V"),
            e("Z", "boolean (false = 0, true = 1).", "->enabled:Z"),
            e("B", "byte — 8 бит со знаком.", "->flag:B"),
            e("S", "short — 16 бит со знаком.", "->port:S"),
            e("C", "char — 16 бит без знака (UTF-16).", "->letter:C"),
            e("I", "int — 32 бита со знаком.", "->count:I"),
            e("J", "long — 64 бита. Занимает пару регистров (wide).", "->time:J"),
            e("F", "float — 32 бита IEEE 754.", "->ratio:F"),
            e("D", "double — 64 бита IEEE 754. Занимает пару регистров (wide).", "->value:D"),
            e("L<класс>;", "Ссылка на объект. Полное имя класса, где точки заменены на /, и обязательная ; в конце. Вложенные классы — через \$.", "Ljava/lang/String;  Lcom/a/Outer\$Inner;"),
            e("[<тип>", "Массив. Каждая [ добавляет измерение (до 255).", "[I → int[]   [[Ljava/lang/String; → String[][]"),
            e("Сигнатура метода", "Класс->имя(параметры)тип_возврата. Параметры пишутся подряд без разделителей.", "Lcom/a/B;->sum(IJLjava/lang/String;)Z"),
            e("Ссылка на поле", "Класс->имя:тип.", "Lcom/a/B;->name:Ljava/lang/String;"),
            e("<init> / <clinit>", "<init> — конструктор (вызывается invoke-direct), <clinit> — статический инициализатор класса.", "invoke-direct {p0}, Ljava/lang/Object;-><init>()V"),
        ),
    ),
    SmaliRefSection(
        id = "registers",
        title = "Регистры",
        intro = "Dalvik — регистровая машина. Регистры 32-битные и не типизированы: тип значения отслеживает верификатор.",
        entries = listOf(
            e("v0, v1, …", "Локальные регистры метода. Нумерация начинается с v0.", "const/4 v0, 0x0"),
            e("p0, p1, …", "Регистры параметров — последние регистры метода. В нестатическом методе p0 — это this, параметры начинаются с p1.", "iget-object v0, p0, Lcom/a/B;->name:Ljava/lang/String;"),
            e(".locals N", "Количество локальных регистров (без параметров). Удобнее: добавление переменной не сдвигает p-регистры.", ".locals 3"),
            e(".registers N", "Общее количество регистров = локальные + параметры (включая this и вторые половины wide).", ".registers 5"),
            e("Пример раскладки", "Нестатический foo(IJ)V с .locals 2: v0, v1 — локальные; p0 = v2 (this); p1 = v3 (int); p2 = v4, v5 (long).", ".method public foo(IJ)V\n    .locals 2"),
            e("Wide-регистры", "long и double занимают пару соседних регистров vN, vN+1. В инструкции указывается первый из пары, второй использовать отдельно нельзя.", "move-result-wide v0   # занимает v0 и v1"),
            e("Ограничения разрядности", "Форматы 12x/22c/35c адресуют только v0–v15 (4 бита), 11x/21c/23x — v0–v255, 22x/32x — до v65535. Для больших номеров используйте move/from16 или /range-варианты.", "move/from16 v0, v20"),
            e("Диапазоны {vX .. vY}", "Варианты /range принимают непрерывный диапазон регистров — используются при 6+ аргументах или регистрах больше v15.", "invoke-static/range {v0 .. v6}, Lcom/a/B;->m(IIIIIII)V"),
        ),
    ),
    SmaliRefSection(
        id = "directives",
        title = "Директивы",
        intro = "Директивы описывают структуру класса и метаданные. Метки начинаются с двоеточия (:cond_0), комментарии — с #.",
        entries = listOf(
            e(".class", "Объявление класса: модификаторы и дескриптор. Первая строка файла.", ".class public final Lcom/a/B;"),
            e(".super", "Суперкласс. Для классов без явного родителя — Ljava/lang/Object;.", ".super Ljava/lang/Object;"),
            e(".source", "Имя исходного файла (для стектрейсов). Необязательна.", ".source \"B.java\""),
            e(".implements", "Реализуемый интерфейс; по одной директиве на интерфейс.", ".implements Ljava/lang/Runnable;"),
            e(".field", "Поле класса: модификаторы, имя:тип и, для статических констант, начальное значение.", ".field private static final TAG:Ljava/lang/String; = \"App\""),
            e(".method / .end method", "Начало и конец метода: модификаторы, имя и сигнатура.", ".method public onCreate(Landroid/os/Bundle;)V\n    .locals 1\n    return-void\n.end method"),
            e(".locals", "Число локальных регистров метода (без параметров).", ".locals 2"),
            e(".registers", "Общее число регистров метода (альтернатива .locals).", ".registers 4"),
            e(".param", "Отладочная информация о параметре: имя и аннотации. Может содержать блок .annotation и закрываться .end param.", ".param p1, \"savedInstanceState\"    # Landroid/os/Bundle;"),
            e(".line", "Номер строки исходника для последующих инструкций (для стектрейсов и отладчика).", ".line 42"),
            e(".local / .end local", "Отладочная информация: имя и тип локальной переменной в регистре.", ".local v0, \"name\":Ljava/lang/String;"),
            e(".annotation / .end annotation", "Аннотация с видимостью build, runtime или system и значениями элементов.", ".annotation runtime Ljava/lang/Deprecated;\n.end annotation"),
            e(".packed-switch / .end packed-switch", "Таблица для packed-switch: первое значение и подряд идущие метки переходов.", ":pswitch_data_0\n.packed-switch 0x1\n    :pswitch_0\n    :pswitch_1\n.end packed-switch"),
            e(".sparse-switch / .end sparse-switch", "Таблица для sparse-switch: пары «значение -> метка», отсортированные по значению.", ":sswitch_data_0\n.sparse-switch\n    0xa -> :sswitch_0\n    0x64 -> :sswitch_1\n.end sparse-switch"),
            e(".array-data / .end array-data", "Данные для fill-array-data: размер элемента в байтах и список значений.", ":array_0\n.array-data 4\n    0x1\n    0x2\n    0x3\n.end array-data"),
            e(".catch", "Обработчик исключения заданного типа для диапазона меток try.", ".catch Ljava/io/IOException; {:try_start_0 .. :try_end_0} :catch_0"),
            e(".catchall", "Обработчик любых исключений (finally, synchronized) для диапазона меток.", ".catchall {:try_start_0 .. :try_end_0} :catchall_0"),
        ),
    ),
    SmaliRefSection(
        id = "modifiers",
        title = "Модификаторы доступа",
        intro = "Флаги доступа (access_flags) у классов, полей и методов. Без public/private/protected — доступ в пределах пакета.",
        entries = listOf(
            e("public", "Доступен отовсюду. Флаг 0x1.", ".method public run()V"),
            e("private", "Доступен только внутри класса. Вызов — через invoke-direct. Флаг 0x2."),
            e("protected", "Доступен в пакете и наследникам. Флаг 0x4."),
            e("static", "Принадлежит классу, а не экземпляру; у метода нет this (p0 — первый параметр). Флаг 0x8.", ".method public static main([Ljava/lang/String;)V"),
            e("final", "Класс нельзя наследовать, метод — переопределять, поле — изменять после инициализации. Флаг 0x10."),
            e("synchronized", "Метод выполняется под монитором (в DEX реально действует флаг declared-synchronized). Флаг 0x20."),
            e("volatile", "Поле: чтение/запись без кэширования между потоками. Флаг 0x40."),
            e("bridge", "Метод-мост, сгенерированный компилятором для обобщений. Флаг 0x40 (у методов)."),
            e("transient", "Поле не сериализуется. Флаг 0x80."),
            e("varargs", "Последний параметр — переменное число аргументов. Флаг 0x80 (у методов)."),
            e("native", "Реализация в нативном коде (JNI), тела в smali нет. Флаг 0x100."),
            e("interface", "Класс является интерфейсом. Флаг 0x200."),
            e("abstract", "Абстрактный класс или метод без тела. Флаг 0x400."),
            e("strictfp", "Строгая арифметика с плавающей точкой. Флаг 0x800."),
            e("synthetic", "Сгенерирован компилятором, отсутствует в исходнике (например, access\$000). Флаг 0x1000."),
            e("annotation", "Класс является аннотацией. Флаг 0x2000."),
            e("enum", "Класс-перечисление или его элемент. Флаг 0x4000."),
            e("constructor", "Метод — конструктор <init> или <clinit>. Флаг 0x10000.", ".method public constructor <init>()V"),
            e("declared-synchronized", "Метод объявлен как synchronized (монитор захватывает среда выполнения). Флаг 0x20000."),
        ),
    ),
    SmaliRefSection(
        id = "formats",
        title = "Форматы инструкций",
        intro = "Идентификатор формата: первая цифра — длина в 16-битных словах, вторая — число регистров (r — диапазон), буква — вид дополнительных данных.",
        entries = listOf(
            e("x", "Нет дополнительных данных."),
            e("n / b / s", "Знаковая константа 4, 8 или 16 бит (#+B, #+CC, #+BBBB)."),
            e("h", "Старшие биты 32- или 64-битной константы (const/high16)."),
            e("i / l", "Константа 32 или 64 бита."),
            e("t", "Знаковое смещение перехода (goto, if-*, switch, fill-array-data)."),
            e("c / cc", "Индекс в пулах DEX: строка, тип, поле, метод, call site; cc — два индекса (метод + прототип)."),
        ) + SmaliFormats.map { (id, d) -> e(id, d) },
    ),
)
