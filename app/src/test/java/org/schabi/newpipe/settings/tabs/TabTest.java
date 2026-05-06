package org.schabi.newpipe.settings.tabs;

import org.junit.Test;
import org.schabi.newpipe.download.DownloadRootFragment;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertTrue;

public class TabTest {
    @Test
    public void checkIdDuplication() {
        final Set<Integer> usedIds = new HashSet<>();

        for (final Tab.Type type : Tab.Type.values()) {
            final boolean added = usedIds.add(type.getTabId());
            assertTrue("Id was already used: " + type.getTabId(), added);
        }
    }

    @Test
    public void downloadsTabUsesEmbeddedRootFragment() throws Exception {
        assertTrue(Tab.Type.DOWNLOADS.getTab().getFragment(null) instanceof DownloadRootFragment);
    }
}
