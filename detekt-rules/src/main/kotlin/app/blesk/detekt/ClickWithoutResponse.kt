package app.blesk.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * Every tap target answers the finger: it shrinks a little and the light shows under it. That
 * comes with the app's indication, so a `clickable` (or `combinedClickable`, `selectable`,
 * `toggleable`) given `indication = null` is a dead press. A dimmed area that closes a sheet uses
 * `dismissOnTap`, a surface that only stops taps uses `blockTaps`; anything with a press response
 * of its own says so with `@Suppress("ClickWithoutResponse")`.
 */
class ClickWithoutResponse(config: Config = Config.empty) : Rule(config) {
    override val issue = Issue(
        id = "ClickWithoutResponse",
        severity = Severity.Style,
        description = "A tap target without a press response.",
        debt = Debt.FIVE_MINS,
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (expression.calleeExpression?.text !in TAPS) return
        val silenced = expression.valueArguments.any { argument ->
            argument.getArgumentName()?.asName?.asString() == "indication" && argument.getArgumentExpression()?.text == "null"
        }
        if (silenced) {
            report(CodeSmell(issue, Entity.from(expression), "This tap target gives no response to the finger."))
        }
    }

    private companion object {
        val TAPS = setOf("clickable", "combinedClickable", "selectable", "toggleable", "triStateToggleable")
    }
}
