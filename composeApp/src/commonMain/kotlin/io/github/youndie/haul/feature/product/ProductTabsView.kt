package io.github.youndie.haul.feature.product

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.shell.LocalTreeCommands
import io.github.youndie.haul.shell.TreeCommand
import io.github.youndie.haul.shell.vote
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulType
import io.github.youndie.haul.theme.LocalHaulCompact
import io.github.youndie.haul.ui.HaulButton
import io.github.youndie.haul.ui.Highlight
import io.github.youndie.haul.ui.LocalHaulActions
import io.github.youndie.haul.ui.ProductDescription
import io.github.youndie.haul.ui.ProductQuestions
import io.github.youndie.haul.ui.ProductReviews
import io.github.youndie.haul.ui.ProductTabs
import io.github.youndie.haul.ui.Question
import io.github.youndie.haul.ui.Review
import io.github.youndie.haul.ui.SpecificationList
import io.github.youndie.haul.ui.Text
import io.github.youndie.haul.ui.accented
import io.github.youndie.haul.ui.following
import io.github.youndie.haul.ui.follows
import io.github.youndie.haul.ui.gutter
import io.github.youndie.haul.ui.pressable
import io.github.youndie.haul.ui.toneColor
import kotlinx.coroutines.launch

// The lower half of the product page: the tab row and the four tabs' content, each with the margins
// the page gives it (Product_Description, _Specifications, _Reviews, _Questions and their _Phone twins).

/**
 * The tab row: 36 px apart under a hairline at 1440, 24 apart and running off the right edge on a
 * phone, the selected tab underlined 3 px over the hairline.
 */
@Composable
public fun ProductTabsView(tabs: ProductTabs) {
    val compact = LocalHaulCompact.current
    val gutter = gutter()
    // The hairline runs the row's full width — to the page's right edge on a phone, where the row
    // scrolls (`margin-right: -16px`).
    val hairline =
        Modifier.drawBehind {
            drawRect(HaulColors.outlineVariant, Offset(0f, size.height - 1f), Size(size.width, 1f))
        }
    val row =
        if (compact) {
            Modifier
                .fillMaxWidth()
                .padding(
                    start = gutter,
                    top = 48.dp,
                ).then(hairline)
                .horizontalScroll(rememberScrollState())
        } else {
            Modifier.fillMaxWidth().padding(start = gutter, end = gutter, top = 72.dp).then(hairline)
        }
    Box(row) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(if (compact) 24.dp else 36.dp),
        ) {
            tabs.tabs.forEach { tab ->
                val title = if (compact) tab.compactTitle ?: tab.title else tab.title
                Column(Modifier.width(androidx.compose.foundation.layout.IntrinsicSize.Max).follows(tab.action)) {
                    Text(
                        buildAnnotatedString {
                            append(title)
                            tab.count?.let {
                                append(" ")
                                withStyle(
                                    SpanStyle(fontWeight = FontWeight(500), color = HaulColors.outline),
                                ) { append(it) }
                            }
                        },
                        style =
                            HaulType
                                .text(if (compact) 16f else 17f, 600)
                                .copy(color = if (tab.selected) HaulColors.onSurface else HaulColors.outline),
                        softWrap = false,
                    )
                    Spacer(Modifier.height(16.dp))
                    // The selected tab's 3 px border, 1 px of it over the row's hairline.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .then(if (tab.selected) Modifier.background(HaulColors.inverseSurface) else Modifier),
                    )
                }
            }
        }
    }
}

/** «Write a review», which presents the review dialog (a customer) or signs in (a guest). */
internal const val WRITE_REVIEW_TAG: String = "write-review"

/** «Ask a question», likewise. */
internal const val ASK_TAG: String = "ask-question"

/** «Helpful» on a review: a customer's vote, or the way to sign in for a guest (B-43). */
internal const val HELPFUL_TAG: String = "review-helpful"

/** The bottom of the page under the tab's content. */
@Composable
private fun pageEnd(): Dp = if (LocalHaulCompact.current) 64.dp else 80.dp

@Composable
private fun contentTop(): Dp = if (LocalHaulCompact.current) 28.dp else 40.dp

/** The description: a headline and the copy beside four fact tiles; stacked on a phone. */
@Composable
public fun ProductDescriptionView(description: ProductDescription) {
    val compact = LocalHaulCompact.current
    val gutter = gutter()
    val copy =
        @Composable { modifier: Modifier ->
            Column(modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                description.title?.let {
                    Text(
                        accented(it, description.accent),
                        style = HaulType.display(if (compact) 32f else 44f, 800, letterSpacing = -0.01f),
                    )
                }
                description.text.split("\n\n").forEach {
                    Text(it, HaulType.text(16f, lineHeight = 1.6f).copy(color = HaulColors.onSurfaceVariant))
                }
            }
        }
    val facts =
        @Composable { modifier: Modifier ->
            Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                description.facts.chunked(2).forEach { pair ->
                    Row(
                        Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        pair.forEach { Fact(it, compact, Modifier.weight(1f)) }
                        if (pair.size == 1) Box(Modifier.weight(1f))
                    }
                }
            }
        }
    val outer = Modifier.fillMaxWidth().padding(start = gutter, end = gutter, top = contentTop(), bottom = pageEnd())
    if (compact) {
        Column(outer, verticalArrangement = Arrangement.spacedBy(28.dp)) {
            copy(Modifier)
            facts(Modifier)
        }
    } else {
        Row(outer, horizontalArrangement = Arrangement.spacedBy(56.dp)) {
            copy(Modifier.weight(1.2f))
            facts(Modifier.weight(1f))
        }
    }
}

@Composable
private fun Fact(
    fact: Highlight,
    compact: Boolean,
    modifier: Modifier,
) {
    Column(
        modifier
            .fillMaxHeight()
            .background(HaulColors.surfaceContainerLowest, RoundedCornerShape(24.dp))
            .padding(if (compact) 20.dp else 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            fact.title,
            HaulType.display(if (compact) 44f else 56f, 800, letterSpacing = -0.01f, lineHeight = 0.9f),
            softWrap = false,
        )
        Text(fact.text, HaulType.text(15f).copy(color = HaulColors.onSurfaceVariant))
    }
}

/** The specifications in white cards: two side by side at 1440 (the first takes the odd row), one on a phone. */
@Composable
public fun SpecificationListView(list: SpecificationList) {
    val compact = LocalHaulCompact.current
    val gutter = gutter()
    val outer = Modifier.fillMaxWidth().padding(start = gutter, end = gutter, top = contentTop(), bottom = pageEnd())
    if (compact) {
        Box(outer) { SpecCard(list.rows, labelWidth = 130.dp, horizontal = 20.dp, Modifier.fillMaxWidth()) }
    } else {
        val half = (list.rows.size + 1) / 2
        Row(outer, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SpecCard(list.rows.take(half), labelWidth = 200.dp, horizontal = 28.dp, Modifier.weight(1f))
            SpecCard(list.rows.drop(half), labelWidth = 200.dp, horizontal = 28.dp, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SpecCard(
    rows: List<Highlight>,
    labelWidth: Dp,
    horizontal: Dp,
    modifier: Modifier,
) {
    Column(
        modifier
            .background(HaulColors.surfaceContainerLowest, RoundedCornerShape(24.dp))
            .padding(horizontal = horizontal, vertical = 8.dp),
    ) {
        rows.forEachIndexed { index, row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (index < rows.size - 1) Modifier.bottomRule() else Modifier)
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(row.title, HaulType.text(15f).copy(color = HaulColors.outline), Modifier.width(labelWidth))
                Text(row.text, HaulType.text(15f, 500), Modifier.weight(1f))
            }
        }
    }
}

/** A hairline along the bottom edge, inside the box: CSS's `border-bottom: 1px solid`. */
private fun Modifier.bottomRule(): Modifier =
    drawBehind { drawRect(HaulColors.outlineVariant, Offset(0f, size.height - 1f), Size(size.width, 1f)) }
        .padding(bottom = 1.dp)

/**
 * The reviews: the rating, its histogram and «Write a review» in a 360-wide column beside the reviews,
 * two to a row; stacked on a phone.
 */
@Composable
public fun ProductReviewsView(reviews: ProductReviews) {
    val compact = LocalHaulCompact.current
    val gutter = gutter()
    val outer = Modifier.fillMaxWidth().padding(start = gutter, end = gutter, top = contentTop(), bottom = pageEnd())
    if (compact) {
        Column(outer, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            RatingSummary(reviews, compact = true)
            reviews.reviews.forEach { ReviewCard(it, compact = true, Modifier.fillMaxWidth()) }
        }
    } else {
        Row(outer, horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            Box(Modifier.width(360.dp)) { RatingSummary(reviews, compact = false) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(40.dp)) {
                reviews.reviews.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                        pair.forEach { ReviewCard(it, compact = false, Modifier.weight(1f)) }
                        if (pair.size == 1) Box(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun BigNumber(
    value: String,
    caption: String,
    compact: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
        Text(
            value,
            HaulType.display(if (compact) 96f else 128f, 800, letterSpacing = -0.04f, lineHeight = 0.8f),
            softWrap = false,
        )
        Text(caption, HaulType.text(15f).copy(color = HaulColors.outline), Modifier.padding(bottom = 6.dp))
    }
}

@Composable
private fun RatingSummary(
    reviews: ProductReviews,
    compact: Boolean,
) {
    Column(Modifier.fillMaxWidth()) {
        BigNumber(reviews.rating, reviews.caption, compact)
        Column(Modifier.padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val style = HaulType.label(13f, 500, 0f)
            reviews.histogram.forEach { bar ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(bar.stars.toString(), style, Modifier.width(20.dp))
                    Box(
                        Modifier
                            .weight(1f)
                            .height(8.dp)
                            .background(HaulColors.outlineVariant, RoundedCornerShape(4.dp))
                            .drawBehind {
                                drawRect(
                                    HaulColors.inverseSurface,
                                    size = Size(size.width * bar.percent / 100f, size.height),
                                )
                            },
                    )
                    Text("${bar.percent}%", style.copy(textAlign = TextAlign.End), Modifier.width(40.dp))
                }
            }
        }
        HaulButton(
            reviews.actionLabel,
            Modifier.padding(top = 28.dp).fillMaxWidth().testTag(WRITE_REVIEW_TAG),
            height = 56.dp,
            radius = 18.dp,
            border = HaulColors.onSurface,
            textSize = 16f,
            onClick = following(reviews.action),
        )
    }
}

@Composable
private fun ReviewCard(
    review: Review,
    compact: Boolean,
    modifier: Modifier,
) {
    Column(
        modifier
            .background(HaulColors.surfaceContainerLowest, RoundedCornerShape(24.dp))
            .padding(if (compact) 22.dp else 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).background(toneColor(review.tone), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(review.initial, HaulType.text(16f, 700))
            }
            Column(Modifier.weight(1f)) {
                Text(review.author, HaulType.text(14f, 600))
                Text(review.meta, HaulType.text(14f).copy(color = HaulColors.outline))
            }
            Text(review.score, HaulType.display(24f, 800, letterSpacing = -0.01f), softWrap = false)
        }
        Text(review.title, HaulType.display(24f, 700, letterSpacing = -0.01f, lineHeight = 1.15f))
        Text(review.text, HaulType.text(15f, lineHeight = 1.55f).copy(color = HaulColors.onSurfaceVariant))
        if (review.photos.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                review.photos.forEach { Box(Modifier.size(72.dp).background(toneColor(it), RoundedCornerShape(12.dp))) }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            review.helpful?.let {
                Text(it, HaulType.text(14f).copy(color = HaulColors.outline), Modifier.weight(1f))
            }
            Text(
                review.helpfulLabel,
                HaulType.text(14f, 600),
                Modifier
                    .testTag(HELPFUL_TAG)
                    .pressable(helpfulPress(review))
                    .border(1.dp, HaulColors.outlineVariant, CircleShape)
                    .padding(horizontal = 15.dp, vertical = 9.dp),
                softWrap = false,
            )
        }
    }
}

/**
 * What pressing «Helpful» on [review] does (B-43): a customer's vote — the review's `HelpfulCommand` —
 * sent through [LocalTreeCommands], its answer, `refresh`, handed to the screen's handler; a guest's
 * `navigate` to sign in followed as it is. Nothing on the customer's own review, which carries neither.
 */
@Composable
private fun helpfulPress(review: Review): (() -> Unit)? {
    val command = review.helpfulCommand ?: return following(review.helpfulAction)
    val commands = LocalTreeCommands.current
    val actions = LocalHaulActions.current
    val scope = rememberCoroutineScope()
    return if (commands == null || actions == null) {
        null
    } else {
        { scope.launch { commands.vote(TreeCommand.Vote(command.url, command.vote))?.let(actions::handle) } }
    }
}

/** The questions: the count, who answers and «Ask a question» beside the questions; stacked on a phone. */
@Composable
public fun ProductQuestionsView(questions: ProductQuestions) {
    val compact = LocalHaulCompact.current
    val gutter = gutter()
    val outer = Modifier.fillMaxWidth().padding(start = gutter, end = gutter, top = contentTop(), bottom = pageEnd())
    val summary =
        @Composable { modifier: Modifier ->
            Column(modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                BigNumber(questions.count, questions.caption, compact)
                Text(questions.text, HaulType.text(15f, lineHeight = 1.5f).copy(color = HaulColors.onSurfaceVariant))
                HaulButton(
                    questions.actionLabel,
                    Modifier.fillMaxWidth().testTag(ASK_TAG),
                    height = 56.dp,
                    radius = 18.dp,
                    border = HaulColors.onSurface,
                    textSize = 16f,
                    onClick = following(questions.action),
                )
            }
        }
    val list =
        @Composable { modifier: Modifier ->
            Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                questions.questions.forEach { QuestionCard(it, compact) }
            }
        }
    if (compact) {
        Column(outer, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            summary(Modifier.fillMaxWidth())
            list(Modifier.fillMaxWidth())
        }
    } else {
        Row(outer, horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            summary(Modifier.width(360.dp))
            list(Modifier.weight(1f))
        }
    }
}

@Composable
private fun QuestionCard(
    question: Question,
    compact: Boolean,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(HaulColors.surfaceContainerLowest, RoundedCornerShape(24.dp))
            .padding(if (compact) 22.dp else 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Q", HaulType.display(28f, 800, letterSpacing = -0.01f).copy(color = HaulColors.primary))
            Column(Modifier.weight(1f)) {
                Text(question.question, HaulType.text(17f, 600, lineHeight = 1.35f))
                Text(question.asked, HaulType.text(13f).copy(color = HaulColors.outline), Modifier.padding(top = 4.dp))
            }
        }
        val answer = question.answer
        if (answer != null) {
            Row(
                Modifier.fillMaxWidth().topRule().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("A", HaulType.display(28f, 800, letterSpacing = -0.01f))
                Column(Modifier.weight(1f)) {
                    Text(answer, HaulType.text(15f, lineHeight = 1.55f).copy(color = HaulColors.onSurfaceVariant))
                    question.answeredBy?.let {
                        Text(it, HaulType.text(13f).copy(color = HaulColors.outline), Modifier.padding(top = 6.dp))
                    }
                }
            }
        } else {
            question.pendingLabel?.let {
                Text(
                    it.uppercase(),
                    HaulType.label(11f, 600, 0.06f).copy(color = HaulColors.outline),
                    Modifier
                        .background(HaulColors.background, CircleShape)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
}
