package app.blesk.feature.settings

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
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
    DocumentScreen(R.string.policy_title, R.string.policy_intro, R.array.policy_titles, R.array.policy_bodies, onBack, modifier)
}

/** The rules, the same text as `docs/TERMS.md` and the site's `/terms`, readable without a network. */
@Composable
fun TermsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    DocumentScreen(R.string.terms_title, R.string.terms_intro, R.array.terms_titles, R.array.terms_bodies, onBack, modifier)
}

/** A document from resources: an intro, then sections whose titles TalkBack reads as headings. */
@Composable
private fun DocumentScreen(
    @StringRes title: Int,
    @StringRes intro: Int,
    @ArrayRes titles: Int,
    @ArrayRes bodies: Int,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val sectionTitles = stringArrayResource(titles)
    val sectionBodies = stringArrayResource(bodies)
    ScrollScreen(title = stringResource(title), onBack = onBack, modifier = modifier) {
        Column(Modifier.padding(horizontal = Blesk.dimens.gutter + 4.dp).padding(bottom = Blesk.dimens.sectionGap)) {
            Text(stringResource(intro), color = Blesk.colors.textSecondary)
            sectionTitles.zip(sectionBodies).forEach { (sectionTitle, body) ->
                Text(
                    text = sectionTitle,
                    style = Blesk.type.bodyStrong,
                    modifier = Modifier.padding(top = Blesk.dimens.sectionGap, bottom = 6.dp).semantics { heading() },
                )
                Text(body, color = Blesk.colors.textSecondary)
            }
        }
    }
}
