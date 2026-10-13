package app.blesk.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

class BleskRuleSetProvider : RuleSetProvider {
    override val ruleSetId = "blesk"

    override fun instance(config: Config) = RuleSet(ruleSetId, listOf(ClickWithoutResponse(config)))
}
