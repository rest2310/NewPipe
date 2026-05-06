/*
 * SPDX-FileCopyrightText: 2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.feed

/**
 * Filters that can be applied to feed streams. Keep [id] values stable because they are passed
 * into Room queries.
 */
enum class FeedFilter(val id: Int) {
    /** All feed items. */
    ALL(0),

    /** Only feed items from uploader/channel subscriptions marked as favorite. */
    FAVORITES(1),

    /** Feed items that have not been opened since their subscription feed was last loaded. */
    NEW(2),

    /** Feed items with saved playback progress that has not reached the completed threshold. */
    UNFINISHED(3)
}
