package os.tessera.calculator

import androidx.lifecycle.ViewModel
import java.math.BigDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import os.tessera.calculator.logic.CalculatorEngine
import os.tessera.calculator.logic.InputEditor
import os.tessera.calculator.logic.ResultFormat

data class CalcUiState(
    val expression: String = "",
    val output: String = "0",
    val showError: Boolean = false,
    val justEvaluated: Boolean = false,
    val history: List<HistoryLine> = emptyList(),
)

data class HistoryLine(val expression: String, val result: String)

/**
 * Стан калькулятора. Compose лише показує ці дані й надсилає натискання
 * клавіш, тож усю арифметику можна перевірити тестом без пристрою.
 */
class CalculatorViewModel : ViewModel() {

    private var input = ""
    private var lastResult: BigDecimal? = null

    private val _state = MutableStateFlow(CalcUiState())
    val state: StateFlow<CalcUiState> = _state

    fun digit(d: Char) = edit(InputEditor.pressDigit(fresh(), d))

    fun dot() = edit(InputEditor.pressDot(fresh()))

    fun operator(op: Char) {
        val base = if (_state.value.justEvaluated) carryResult() else input
        edit(InputEditor.pressOperator(base, op))
    }

    fun percent() = edit(InputEditor.pressPercent(fresh()))

    fun openParen() = edit(InputEditor.pressOpenParen(fresh()))

    fun closeParen() = edit(InputEditor.pressCloseParen(fresh()))

    /** Одна клавіша на дужки: після числа закриваємо, інакше — відкриваємо. */
    fun toggleParen() {
        val last = input.lastOrNull()
        if (last != null && (last.isDigit() || last == '%' || last == ')')) closeParen() else openParen()
    }

    fun backspace() {
        if (_state.value.justEvaluated) {
            resetInput()
            show(input, error = false)
            return
        }
        edit(InputEditor.backspace(input))
    }

    fun clear() {
        resetInput()
        show("", error = false)
    }

    fun equals() {
        val completed = InputEditor.complete(input)
        if (completed.isEmpty()) return
        val value = try {
            CalculatorEngine.evaluate(completed)
        } catch (_: CalculatorEngine.CalcException) {
            _state.update { it.copy(showError = true) }
            return
        }
        lastResult = value
        input = value.toPlainString()
        val pretty = ResultFormat.prettyExpression(completed)
        val formatted = ResultFormat.format(value)
        _state.update {
            it.copy(
                expression = pretty,
                output = formatted,
                showError = false,
                justEvaluated = true,
                history = (listOf(HistoryLine(pretty, formatted)) + it.history).take(12),
            )
        }
    }

    /** Натиснули цифру після «=»: рахуємо з чистого рядка, як Pixel. */
    private fun fresh(): String {
        if (_state.value.justEvaluated) resetInput()
        return input
    }

    /** Натиснули оператор після «=»: продовжуємо від попереднього результату. */
    private fun carryResult(): String = lastResult?.toPlainString().orEmpty()

    private fun resetInput() {
        input = ""
        lastResult = null
        _state.update { it.copy(justEvaluated = false) }
    }

    private fun edit(next: String) {
        input = next
        val completed = InputEditor.complete(next)
        val preview = if (completed.isEmpty()) null else CalculatorEngine.tryEvaluate(completed)
        show(next, error = false, value = preview)
    }

    private fun show(raw: String, error: Boolean, value: BigDecimal? = null) {
        _state.update {
            it.copy(
                expression = ResultFormat.prettyExpression(raw),
                output = value?.let(ResultFormat::format) ?: if (raw.isEmpty()) "0" else it.output,
                showError = error,
            )
        }
    }
}
