package org.schabi.newpipe.settings.tabs;

import androidx.annotation.Nullable;

import com.grack.nanojson.JsonStringWriter;
import com.grack.nanojson.JsonWriter;

import java.util.List;

/**
 * Class to get a JSON representation of the fixed set of main navigation tabs.
 */
public final class TabsJsonHelper {
    private static final String JSON_TABS_ARRAY_KEY = "tabs";

    private static final List<Tab> FIXED_TABS_LIST = List.of(
            Tab.Type.FEED.getTab(),
            Tab.Type.SUBSCRIPTIONS.getTab(),
            Tab.Type.BOOKMARKS.getTab(),
            Tab.Type.DOWNLOADS.getTab());

    private TabsJsonHelper() { }

    /**
     * Return the fixed main navigation tabs.
     * <p>
     * Saved JSON is ignored intentionally: the app no longer supports user-customized main tabs,
     * kiosk/trending tabs, history tabs, or arbitrary channel/playlist/feed-group tabs here.
     *
     * @param tabsJson ignored legacy JSON string got from {@link #getJsonToSave(List)}.
     * @return the fixed list of {@link Tab tabs}.
     */
    public static List<Tab> getTabsFromJson(@Nullable final String tabsJson)
            throws InvalidJsonException {
        return getDefaultTabs();
    }

    /**
     * Get a JSON representation of the fixed main navigation tabs.
     *
     * @param tabList ignored; custom main tabs are no longer supported.
     * @return a JSON string representing the fixed list of tabs
     */
    public static String getJsonToSave(@Nullable final List<Tab> tabList) {
        final JsonStringWriter jsonWriter = JsonWriter.string();
        jsonWriter.object();

        jsonWriter.array(JSON_TABS_ARRAY_KEY);
        for (final Tab tab : FIXED_TABS_LIST) {
            tab.writeJsonOn(jsonWriter);
        }
        jsonWriter.end();

        jsonWriter.end();
        return jsonWriter.done();
    }

    public static List<Tab> getDefaultTabs() {
        return FIXED_TABS_LIST;
    }

    public static final class InvalidJsonException extends Exception {
        private InvalidJsonException() {
            super();
        }

        private InvalidJsonException(final String message) {
            super(message);
        }

        private InvalidJsonException(final Throwable cause) {
            super(cause);
        }
    }
}
