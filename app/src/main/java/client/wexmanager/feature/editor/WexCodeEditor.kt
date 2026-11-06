package client.wexmanager.feature.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.StateListDrawable
import android.view.MotionEvent
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.LayoutStateChangeEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.lang.styling.line.LineSideIcon
import io.github.rosemoe.sora.util.IntPair
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorRenderer
import io.github.rosemoe.sora.widget.REGION_SIDE_ICON
import io.github.rosemoe.sora.widget.layout.Layout
import io.github.rosemoe.sora.widget.resolveTouchRegion
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * CodeEditor с полосой прокрутки в духе Material 3, как у списков файлов (FastScroller):
 * ползунок — «пилюля», при перетаскивании шире и цвета primary; номер строки — капля 56 dp
 * со скруглением 28 dp и острым углом у ползунка (вместо прямоугольной плашки Sora).
 */
class WexCodeEditor(context: Context) : CodeEditor(context) {
    val folding = FoldingState()

    /** Версия текста, уже залитая в виджет. Повтор той же версии не перечитывает файл. */
    var loadedTextVersion: Int = 0

    /** Тема уже применена к этому экземпляру. */
    var themeApplied: Boolean = false

    /** Чтобы смена вкладки не пересоздавала язык и не сбрасывала разбор. */
    var langKey: String? = null

    /** setText не должен помечать файл изменённым. */
    var applyingText: Boolean = false

    var onEdited: (() -> Unit)? = null
    var onCaret: ((line: Int, column: Int) -> Unit)? = null

    init {
        // Плашка показывает номер строки у верхнего края экрана, без «L».
        lineNumberTipTextProvider = object : io.github.rosemoe.sora.widget.style.LineNumberTipTextProvider {
            override fun getCurrentText(editor: CodeEditor): String = (editor.firstVisibleLine + 1).toString()
        }
        // createLayout зовётся из конструктора Sora, когда subclass ещё не инициализирован.
        setTag(READY, true)
        folding.onChanged = { post { syncMarkerLines(); syncFoldLayout() } }
        subscribeAlways(ContentChangeEvent::class.java) { ev ->
            when (ev.action) {
                ContentChangeEvent.ACTION_SET_NEW_TEXT -> folding.clear()
                ContentChangeEvent.ACTION_INSERT ->
                    folding.onInsert(ev.changeStart.line, ev.changeEnd.line - ev.changeStart.line)
                ContentChangeEvent.ACTION_DELETE -> folding.onDelete(ev.changeStart.line, ev.changeEnd.line)
            }
            syncMarkerLines()
            if (folding.anyCollapsed() || getLayout() is FoldLayout) syncFoldLayout()
            if (ev.action != ContentChangeEvent.ACTION_SET_NEW_TEXT && !applyingText) onEdited?.invoke()
        }
        subscribeAlways(SelectionChangeEvent::class.java) { ev ->
            onCaret?.invoke(ev.left.line, ev.left.column)
        }
        subscribeAlways(LayoutStateChangeEvent::class.java) { ev ->
            if (!ev.isLayoutBusy && folding.anyCollapsed()) syncFoldLayout()
        }
    }

    /** Язык только что создан экраном: регионы пишутся в этот же [folding]. */
    fun bindFolds(lang: WexLanguage, enabled: Boolean) {
        lang.folds = folding
        lang.foldEnabled = enabled
    }

    override fun onCreateRenderer(): EditorRenderer = M3Renderer(this)

    /** Касание началось во время прокрутки по инерции: оно её только останавливает. */
    private var stopTouch = false
    private var stopDownX = 0f
    private var stopDownY = 0f
    private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop
    private var foldDown = false
    private var foldLine = -1
    private var foldMoved = false

    /**
     * Две правки жестов Sora, как в обычных полях ввода Android:
     * - тап по тексту, летящему по инерции, только останавливает его. Sora ставил курсор, и маркер-капля
     *   появлялся прямо под пальцем: следующий бросок с того же места хватал маркер и двигал курсор
     *   вместо прокрутки («листается через раз»);
     * - второй палец при удержанном маркере начинает масштаб: Sora в этом случае щипок отклонял.
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val handler = eventHandler
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Все жесты в границах редактора — его. Sora снимал запрет перехвата, едва палец
                // уходил вправо при нулевой горизонтальной прокрутке (жест «для перелистывателя
                // страниц»): настоящий палец почти всегда немного смещается вбок, Compose отдавал
                // жест предку и присылал редактору CANCEL — прокрутка и щипок обрывались.
                parent?.requestDisallowInterceptTouchEvent(true)
                stopTouch = !handler.scroller.isFinished
                stopDownX = event.x
                stopDownY = event.y
                foldMoved = false
                foldDown = !stopTouch && IntPair.getFirst(resolveTouchRegion(event)) == REGION_SIDE_ICON
                foldLine = if (foldDown) foldLineAt(event.y) else -1
            }
            MotionEvent.ACTION_MOVE -> {
                if (kotlin.math.abs(event.x - stopDownX) > touchSlop || kotlin.math.abs(event.y - stopDownY) > touchSlop) {
                    foldMoved = true
                }
                if (stopTouch && foldMoved) stopTouch = false // это уже новая прокрутка
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                stopTouch = false
                if (handler.hasAnyHeldHandle() && !handler.isHandleMoving) restartWithoutHandle(event)
            }
            MotionEvent.ACTION_UP -> {
                if (stopTouch) {
                    stopTouch = false
                    // Отмена вместо отпускания: детектор жестов не засчитает тап и не поставит курсор.
                    cancelTouch(event)
                    return true
                }
                // Тап по маркеру +/-. Свой расчёт строки: у Sora 0.24 ряд иконки считается с offsetX.
                if (foldDown && !foldMoved && folding.toggle(foldLine)) {
                    foldDown = false
                    syncMarkerLines()
                    syncFoldLayout()
                    if (folding.isHidden(cursor.leftLine)) {
                        val header = folding.headerFor(cursor.leftLine).coerceIn(0, lineCount - 1)
                        setSelection(header, text.getColumnCount(header))
                    }
                    cancelTouch(event)
                    return true
                }
                foldDown = false
            }
            MotionEvent.ACTION_CANCEL -> foldDown = false
        }
        return super.dispatchTouchEvent(event)
    }

    private fun cancelTouch(event: MotionEvent) {
        val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
        super.dispatchTouchEvent(cancel)
        cancel.recycle()
    }

    private fun foldLineAt(y: Float): Int {
        val layout = getLayout() ?: return -1
        val row = ((y + offsetY) / rowHeight).toInt().coerceIn(0, layout.rowCount - 1)
        return layout.getLineNumberForRow(row)
    }

    /**
     * [CodeEditor.layout] пакетный — из этого пакета его не записать. Свёртка подменяет карту рядов
     * поверх обычного layout (в том числе с переносом строк), не рисуя номера строк сама.
     */
    private fun editorLayout(): Layout? = layoutField.get(this) as? Layout

    private fun setEditorLayout(layout: Layout) {
        layoutField.set(this, layout)
    }

    internal fun syncFoldLayout() {
        val current = editorLayout() ?: return
        if (!folding.anyCollapsed()) {
            if (current is FoldLayout) setEditorLayout(current.inner)
            postInvalidate()
            return
        }
        val fold = current as? FoldLayout ?: FoldLayout(this, current, folding).also { setEditorLayout(it) }
        fold.rebuild()
        if (folding.isHidden(cursor.leftLine)) {
            val header = folding.headerFor(cursor.leftLine)
            // Не двигать курсор прямо из обработчика правки текста — Sora этого не любит.
            post {
                if (tag == "released" || lineCount <= 0 || !folding.isHidden(cursor.leftLine)) return@post
                val h = header.coerceIn(0, lineCount - 1)
                setSelection(h, text.getColumnCount(h))
            }
        }
        eventHandler.scrollBy(0f, 0f)
        postInvalidate()
    }

    private fun syncMarkerLines() {
        val styles = getStyles() ?: return
        val lines = styles.lineStyles?.toList() ?: return
        for (ls in lines) {
            val icon = ls.findOne(LineSideIcon::class.java) ?: continue
            (icon.drawable as? FoldMarkerDrawable)?.line = icon.line
        }
        postInvalidate()
    }

    override fun createLayout(clearWordwrapCache: Boolean) {
        if (getTag(READY) != true) {
            super.createLayout(clearWordwrapCache)
            return
        }
        val current = editorLayout()
        if (current is FoldLayout) setEditorLayout(current.inner)
        super.createLayout(clearWordwrapCache)
        if (folding.anyCollapsed()) syncFoldLayout()
    }

    override fun setLayoutBusy(busy: Boolean) {
        if (getTag(READY) != true) {
            super.setLayoutBusy(busy)
            return
        }
        val fold = editorLayout() as? FoldLayout
        if (fold != null && isWordwrap && fold.inner is io.github.rosemoe.sora.widget.layout.WordwrapLayout) {
            // setLayoutBusy при переносе приводит layout к WordwrapLayout. На время вызова подсовываем внутренний.
            setEditorLayout(fold.inner)
            try {
                super.setLayoutBusy(busy)
            } finally {
                if (editorLayout() === fold.inner && folding.anyCollapsed()) setEditorLayout(fold)
            }
        } else {
            super.setLayoutBusy(busy)
        }
    }

    /** Сбросить захват маркера и начать жест заново первым пальцем — так Sora увидит щипок. */
    private fun restartWithoutHandle(event: MotionEvent) {
        val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
        super.dispatchTouchEvent(cancel)
        cancel.recycle()
        eventHandler.hideInsertHandle()
        val i = 1 - event.actionIndex.coerceAtMost(1) // палец, который уже лежал
        val down = MotionEvent.obtain(event.downTime, event.eventTime, MotionEvent.ACTION_DOWN, event.getX(i), event.getY(i), event.metaState)
        super.dispatchTouchEvent(down)
        down.recycle()
    }

    /** Ползунки под текущую палитру (вызывать при смене темы). */
    fun applyScrollbarColors(idle: Int, active: Int) {
        val dp = dpUnit
        verticalScrollbarThumbDrawable = thumb(idle, active, dp, vertical = true)
        horizontalScrollbarThumbDrawable = thumb(idle, active, dp, vertical = false)
        verticalScrollbarTrackDrawable = null
        horizontalScrollbarTrackDrawable = null
        invalidate()
    }

    private fun thumb(idle: Int, active: Int, dp: Float, vertical: Boolean): Drawable {
        fun pill(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = 999f * dp }
        // Полоса Sora — 10 dp у края: в покое видна узкая часть 6 dp, при захвате — 8 dp.
        fun inset(d: Drawable, near: Float, far: Float) =
            if (vertical) InsetDrawable(d, (near * dp).toInt(), 0, (far * dp).toInt(), 0)
            else InsetDrawable(d, 0, (near * dp).toInt(), 0, (far * dp).toInt())
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), inset(pill(active), 1f, 1f))
            addState(intArrayOf(), inset(pill(idle), 3f, 1f))
        }
    }

    private class M3Renderer(private val editor: CodeEditor) : EditorRenderer(editor) {
        private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        private val path = Path()
        private val rect = RectF()
        private val radii = FloatArray(8)

        /** [topY] и [length] — положение ползунка; капля стоит слева от него, как в списке файлов. */
        override fun drawLineInfoPanel(canvas: Canvas, topY: Float, length: Float, offset: Float) {
            if (!editor.isDisplayLnPanel) return
            val label = editor.lineNumberTipTextProvider.getCurrentText(editor)
            val dp = editor.dpUnit
            text.textSize = 24f * dp * editor.resources.configuration.fontScale
            val size = 56f * dp
            val w = maxOf(size, text.measureText(label) + 28f * dp)
            val right = editor.width - 40f * dp
            val top = (topY - 4f * dp).coerceIn(4f * dp, editor.height - size - 4f * dp)
            rect.set(right - w, top, right, top + size)
            val r = 28f * dp
            val sharp = 6f * dp
            // Углы: верх-лево, верх-право, низ-право (острый, к ползунку), низ-лево.
            radii[0] = r; radii[1] = r; radii[2] = r; radii[3] = r
            radii[4] = sharp; radii[5] = sharp; radii[6] = r; radii[7] = r
            path.reset()
            path.addRoundRect(rect, radii, Path.Direction.CW)
            val cs = editor.colorScheme
            bg.color = cs.getColor(EditorColorScheme.LINE_NUMBER_PANEL)
            canvas.drawPath(path, bg)
            text.color = cs.getColor(EditorColorScheme.LINE_NUMBER_PANEL_TEXT)
            val fm = text.fontMetrics
            canvas.drawText(label, rect.centerX(), rect.centerY() - (fm.ascent + fm.descent) / 2f, text)
        }
    }

    private companion object {
        const val READY = 0x57E1001
        val layoutField = CodeEditor::class.java.getDeclaredField("layout").apply { isAccessible = true }
    }
}
