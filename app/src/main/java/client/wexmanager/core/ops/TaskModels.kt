package client.wexmanager.core.ops

import androidx.compose.runtime.Immutable
import client.wexmanager.core.fs.FileItem
import kotlinx.coroutines.CompletableDeferred

enum class TaskKind(val verb: String, val noun: String) {
    Copy("Копирование", "Скопировано"),
    Move("Перемещение", "Перемещено"),
    Delete("Удаление", "Удалено"),
    Trash("Перемещение в корзину", "Перемещено в корзину"),
    Restore("Восстановление", "Восстановлено"),
    Compress("Сжатие", "Создан архив"),
    Extract("Распаковка", "Распаковано"),
    Sign("Подпись APK", "APK подписан"),
    Verify("Проверка архива", "Архив проверен"),
}

enum class TaskStatus { Preparing, Running, WaitingConflict, Done, Failed, Cancelled }

enum class ConflictChoice { Replace, Skip, KeepBoth, Merge }

data class ConflictDecision(val choice: ConflictChoice, val applyToAll: Boolean)

@Immutable
class ConflictRequest(
    val source: FileItem,
    val target: FileItem,
    internal val deferred: CompletableDeferred<ConflictDecision>,
) {
    fun resolve(decision: ConflictDecision) {
        deferred.complete(decision)
    }
}

@Immutable
data class TaskState(
    val id: Long,
    val kind: TaskKind,
    val title: String,
    val destination: String? = null,
    val status: TaskStatus = TaskStatus.Preparing,
    val currentName: String = "",
    val processedBytes: Long = 0,
    val totalBytes: Long = 0,
    val processedItems: Int = 0,
    val totalItems: Int = 0,
    val bytesPerSecond: Long = 0,
    val error: String? = null,
    val failedItems: List<String> = emptyList(),
    val conflict: ConflictRequest? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long = 0,
    val resultPath: String? = null,
    /** Архив требует пароль (или введён неверный) — можно повторить с паролем. */
    val needsPassword: Boolean = false,
    val passwordWrong: Boolean = false,
    /** Что задача переместила в корзину — для «Отменить». */
    val trashed: List<TrashManager.TrashEntry> = emptyList(),
) {
    val isActive: Boolean get() = status == TaskStatus.Preparing || status == TaskStatus.Running || status == TaskStatus.WaitingConflict
    val progress: Float
        get() = when {
            totalBytes > 0 -> (processedBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)
            totalItems > 0 -> (processedItems.toFloat() / totalItems).coerceIn(0f, 1f)
            else -> 0f
        }
    val etaMillis: Long
        get() = if (bytesPerSecond > 0 && totalBytes > processedBytes) (totalBytes - processedBytes) * 1000 / bytesPerSecond else -1
}

sealed interface TaskEvent {
    val task: TaskState

    /** Задача завершилась — папки, затронутые операцией, нужно обновить. */
    data class Finished(override val task: TaskState, val touchedDirs: Set<String>) : TaskEvent
}
