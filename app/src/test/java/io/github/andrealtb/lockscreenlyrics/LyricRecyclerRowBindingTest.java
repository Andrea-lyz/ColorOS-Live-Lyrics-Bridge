package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;

import org.junit.Test;

public final class LyricRecyclerRowBindingTest {
    private final Object item = new Object();

    @Test
    public void explicitNoPositionIdentifiesRetiringHolder() throws Exception {
        Adapter adapter = new Adapter();
        adapter.position = -1;
        assertTrue(LyricRecyclerRowBinding.isUnbound(adapter, item, positionMethod()));
    }

    @Test
    public void reboundHolderBecomesUsableWithoutChangingItsText() throws Exception {
        Adapter adapter = new Adapter();
        adapter.position = -1;
        assertTrue(LyricRecyclerRowBinding.isUnbound(adapter, item, positionMethod()));
        adapter.position = 0;
        assertFalse(LyricRecyclerRowBinding.isUnbound(adapter, item, positionMethod()));
        adapter.position = 37;
        assertFalse(LyricRecyclerRowBinding.isUnbound(adapter, item, positionMethod()));
    }

    @Test
    public void missingPositionHelperPreservesCompatibilityFallback() {
        assertFalse(LyricRecyclerRowBinding.isUnbound(new Adapter(), item, null));
    }

    @Test
    public void failingPositionHelperIsNotEvidenceOfRemovedHolder() throws Exception {
        Adapter adapter = new Adapter();
        adapter.fail = true;
        assertFalse(LyricRecyclerRowBinding.isUnbound(adapter, item, positionMethod()));
    }

    @Test
    public void missingOwnerOrItemDoesNotSuppressLyrics() throws Exception {
        assertFalse(LyricRecyclerRowBinding.isUnbound(null, item, positionMethod()));
        assertFalse(LyricRecyclerRowBinding.isUnbound(new Adapter(), null, positionMethod()));
    }

    @Test
    public void invalidReturnValuePreservesCompatibilityFallback() throws Exception {
        Method method = Adapter.class.getMethod("invalidPosition", Object.class);
        assertFalse(LyricRecyclerRowBinding.isUnbound(new Adapter(), item, method));
    }

    @Test
    public void oldChangeAnimationHolderCannotClaimTheReplacementHolderPosition() throws Exception {
        Adapter adapter = new Adapter();
        adapter.position = 4;
        adapter.owner = new Object();
        assertTrue(LyricRecyclerRowBinding.isUnbound(adapter, item, positionMethod()));
    }

    @Test
    public void currentHolderRemainsUsableAtTheSameAdapterPosition() throws Exception {
        Adapter adapter = new Adapter();
        adapter.position = 4;
        assertFalse(LyricRecyclerRowBinding.isUnbound(adapter, item, positionMethod()));
    }

    @Test
    public void layoutPositionIsNotComparedToBindingAdapterPosition() throws Exception {
        Adapter adapter = new Adapter();
        adapter.position = 4;
        adapter.owner = new Object();
        Method layout = Adapter.class.getMethod("getChildLayoutPosition", Object.class);
        assertFalse(LyricRecyclerRowBinding.isUnbound(adapter, item, layout));
    }

    @Test
    public void missingHolderHelpersPreserveTextMatching() throws Exception {
        PositionOnlyAdapter adapter = new PositionOnlyAdapter();
        Method method = PositionOnlyAdapter.class.getMethod("getChildAdapterPosition", Object.class);
        assertFalse(LyricRecyclerRowBinding.isUnbound(adapter, item, method));
    }

    @Test
    public void failingOwnerLookupIsNotEvidenceOfRetiredHolder() throws Exception {
        Adapter adapter = new Adapter();
        adapter.failOwner = true;
        assertFalse(LyricRecyclerRowBinding.isUnbound(adapter, item, positionMethod()));
    }

    @Test
    public void pendingAdapterUpdateWithoutOwnerDoesNotSuppressCurrentHolder() throws Exception {
        Adapter adapter = new Adapter();
        adapter.owner = null;
        assertFalse(LyricRecyclerRowBinding.isUnbound(adapter, item, positionMethod()));
    }

    private static Method positionMethod() throws Exception {
        return Adapter.class.getMethod("getChildAdapterPosition", Object.class);
    }

    public static final class Adapter {
        int position;
        boolean fail;
        boolean failOwner;
        final Object holder = new Object();
        Object owner = holder;

        public int getChildAdapterPosition(Object item) {
            if (fail) throw new IllegalStateException("temporary host failure");
            return position;
        }

        public Object invalidPosition(Object item) {
            return null;
        }

        public int getChildLayoutPosition(Object item) {
            return position;
        }

        public Object getChildViewHolder(Object item) {
            return holder;
        }

        public Object findViewHolderForAdapterPosition(int position) {
            if (failOwner) throw new IllegalStateException("temporary owner lookup failure");
            return owner;
        }
    }

    public static final class PositionOnlyAdapter {
        public int getChildAdapterPosition(Object item) { return 0; }
    }
}
