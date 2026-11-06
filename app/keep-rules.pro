# jadx (DEX → Java): guava и baksmali исключены из зависимостей — они нужны только для вывода smali в jadx-gui.
-dontwarn com.google.common.**
-dontwarn com.android.tools.smali.**
-dontwarn org.slf4j.impl.**
# jadx читает имена своих пакетов (Class.getPackage) и создаёт проходы/опции через рефлексию:
# без keep R8 переносит классы в пакет по умолчанию и декомпиляция падает с NPE.
-keep class jadx.** { *; }
# Интерфейсы jadx-gui ссылаются на Swing/AWT — на Android не используются.
-dontwarn javax.swing.**
-dontwarn java.awt.**
