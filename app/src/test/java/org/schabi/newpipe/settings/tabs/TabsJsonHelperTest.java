package org.schabi.newpipe.settings.tabs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TabsJsonHelperTest {
    private static final String JSON_TABS_ARRAY_KEY = "tabs";
    private static final String JSON_TAB_ID_KEY = "tab_id";

    @Test
    public void testEmptyAndNullRead() throws TabsJsonHelper.InvalidJsonException {
        final List<Tab> defaultTabs = TabsJsonHelper.getDefaultTabs();

        final String emptyTabsJson = "{\"" + JSON_TABS_ARRAY_KEY + "\":[]}";
        List<Tab> items = TabsJsonHelper.getTabsFromJson(emptyTabsJson);
        assertEquals(defaultTabs, items);

        final String nullSource = null;
        items = TabsJsonHelper.getTabsFromJson(nullSource);
        assertEquals(defaultTabs, items);
    }

    @Test
    public void testLegacyCustomTabsAreIgnored() throws TabsJsonHelper.InvalidJsonException {
        final int blankTabId = Tab.Type.BLANK.getTabId();
        final String legacyTabsJson = "{\"" + JSON_TABS_ARRAY_KEY + "\":["
                + "{\"" + JSON_TAB_ID_KEY + "\":" + blankTabId + "},"
                + "{\"" + JSON_TAB_ID_KEY + "\":" + 12345678 + "}" + "]}";
        final List<Tab> items = TabsJsonHelper.getTabsFromJson(legacyTabsJson);

        assertEquals("Should ignore legacy saved tabs and return fixed navigation tabs",
                getTabIds(TabsJsonHelper.getDefaultTabs()), getTabIds(items));
    }

    @Test
    public void testInvalidJsonIsIgnored() throws TabsJsonHelper.InvalidJsonException {
        final List<String> invalidList = Arrays.asList(
                "{\"notTabsArray\":[]}",
                "{invalidJSON]}",
                "{}"
        );

        for (final String invalidContent : invalidList) {
            assertEquals(TabsJsonHelper.getDefaultTabs(),
                    TabsJsonHelper.getTabsFromJson(invalidContent));
        }
    }

    @Test
    public void testEmptyAndNullSave() throws JsonParserException {
        String returnedJson = TabsJsonHelper.getJsonToSave(List.of());
        assertEquals(getTabIds(TabsJsonHelper.getDefaultTabs()), getTabIdsFromJson(returnedJson));

        returnedJson = TabsJsonHelper.getJsonToSave(null);
        assertEquals(getTabIds(TabsJsonHelper.getDefaultTabs()), getTabIdsFromJson(returnedJson));
    }

    @Test
    public void testSaveIgnoresInputAndWritesFixedTabs() throws JsonParserException {
        final Tab.BlankTab blankTab = new Tab.BlankTab();
        final Tab.SubscriptionsTab subscriptionsTab = new Tab.SubscriptionsTab();
        final Tab.ChannelTab channelTab = new Tab.ChannelTab(
                666, "https://example.org", "testName");
        final Tab.FeedGroupTab feedGroupTab = new Tab.FeedGroupTab(
                1L, "x", 123);

        final List<Tab> tabs = Arrays.asList(
                blankTab, subscriptionsTab, channelTab, feedGroupTab);
        final String returnedJson = TabsJsonHelper.getJsonToSave(tabs);

        assertEquals(getTabIds(TabsJsonHelper.getDefaultTabs()), getTabIdsFromJson(returnedJson));
    }

    @Test
    public void testRemovedLegacyTabsAreFilteredOnRead()
            throws TabsJsonHelper.InvalidJsonException {
        final int feedTabId = Tab.Type.FEED.getTabId();
        final String tabsJson = "{\"" + JSON_TABS_ARRAY_KEY + "\":["
                + "{\"" + JSON_TAB_ID_KEY + "\":5,\"service_id\":0,\"kiosk_id\":\"Trending\"},"
                + "{\"" + JSON_TAB_ID_KEY + "\":5,\"service_id\":0,\"kiosk_id\":\"live\"},"
                + "{\"" + JSON_TAB_ID_KEY
                + "\":5,\"service_id\":0,\"kiosk_id\":\"legacy_games\"},"
                + "{\"" + JSON_TAB_ID_KEY + "\":7},"
                + "{\"" + JSON_TAB_ID_KEY + "\":" + feedTabId + "}"
                + "]}";

        final List<Tab> items = TabsJsonHelper.getTabsFromJson(tabsJson);

        assertEquals(getTabIds(TabsJsonHelper.getDefaultTabs()), getTabIds(items));
    }

    private List<Integer> getTabIds(final List<Tab> tabs) {
        return tabs.stream().map(Tab::getTabId).collect(Collectors.toUnmodifiableList());
    }

    private List<Integer> getTabIdsFromJson(final String returnedJson) throws JsonParserException {
        final JsonObject jsonObject = JsonParser.object().from(returnedJson);
        assertTrue(jsonObject.containsKey(JSON_TABS_ARRAY_KEY));
        final JsonArray tabsFromArray = jsonObject.getArray(JSON_TABS_ARRAY_KEY);
        return tabsFromArray.streamAsJsonObjects()
                .map(json -> json.getInt(JSON_TAB_ID_KEY))
                .collect(Collectors.toUnmodifiableList());
    }
}
