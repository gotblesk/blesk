package app.blesk.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.Text
import app.blesk.core.design.theme.Blesk

/** The privacy policy, the same text as `docs/PRIVACY.md`, readable without a network. */
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val titles = stringArrayResource(R.array.policy_titles)
    val bodies = stringArrayResource(R.array.policy_bodies)
    ScrollScreen(title = stringResource(R.string.policy_title), onBack = onBack, modifier = modifier) {
        Column(Modifier.padding(horizontal = Blesk.dimens.gutter + 4.dp).padding(bottom = Blesk.dimens.sectionGap)) {
            Text(stringResource(R.string.policy_intro), color = Blesk.colors.textSecondary)
            titles.zip(bodies).forEach { (title, body) ->
                Text(
                    text = title,
                    style = Blesk.type.bodyStrong,
                    modifier = Modifier.padding(top = Blesk.dimens.sectionGap, bottom = 6.dp).semantics { heading() },
                )
                Text(body, color = Blesk.colors.textSecondary)
            }
        }
    }
}
