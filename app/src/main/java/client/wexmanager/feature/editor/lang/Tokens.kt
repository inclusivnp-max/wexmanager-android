package client.wexmanager.feature.editor.lang

/** Типы токенов подсветки (общие для всех языков). */
enum class Tok {
    TEXT,
    KEYWORD,
    CONTROL,        // if/for/return — отдельный оттенок ключевых слов
    MODIFIER,       // public/static/final
    TYPE,
    BUILTIN,
    FUNCTION,
    CONSTANT,
    NUMBER,
    STRING,
    ESCAPE,
    REGEX,
    COMMENT,
    DOC_COMMENT,
    DOC_TAG,        // @param в документации
    ANNOTATION,
    PREPROCESSOR,
    OPERATOR,
    PUNCTUATION,
    BRACKET,
    TAG,
    ATTRIBUTE,
    ENTITY,
    VARIABLE,
    PROPERTY,       // ключи JSON/YAML/INI
    SECTION,        // [section]
    LABEL,
    REGISTER,
    DIRECTIVE,
    OPCODE,
    HEADING,
    EMPHASIS,
    STRONG,
    LINK,
    CODE,
    QUOTE,
    LIST,
    INSERTED,
    DELETED,
    HUNK,
    META,
    INVALID,
    COLUMN_0, COLUMN_1, COLUMN_2, COLUMN_3, COLUMN_4, COLUMN_5
}

/** Токен в пределах одной строки: [start, end). */
class Token(val start: Int, val end: Int, val type: Tok)
