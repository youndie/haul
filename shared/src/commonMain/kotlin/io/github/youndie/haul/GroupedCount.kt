package io.github.youndie.haul

/**
 * «1,926»: a count the way the pages write one, in threes. Here once because several features write
 * counts themselves rather than taking the server's text: the review and question dialogs their limits
 * («122 / 5,000», «10 – 1,000 characters»), the return dialog the points the ticked lines give back
 * («+ 1,926 points back», B-50) and its sums to the cent, the filter panel its option counts. Two copies of
 * it lived in `feature/reviews` and `feature/returns` until B-60; a copy that drifted would write the same
 * count two ways on one page.
 */
public fun groupedCount(value: Int): String =
    value
        .toString()
        .reversed()
        .chunked(3)
        .joinToString(",")
        .reversed()
