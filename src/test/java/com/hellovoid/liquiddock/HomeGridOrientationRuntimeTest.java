package com.hellovoid.liquiddock;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class HomeGridOrientationRuntimeTest {
    private static final HomeGridDimensions D8X4 = new HomeGridDimensions(8, 4);
    private static final HomeGridDimensions D7X5 = new HomeGridDimensions(7, 5);

    @Test
    public void captureCurrentPersistsOnlyCompleteValidLayout() {
        MapStore store = new MapStore();
        HomeGridOrientationMemory memory = new HomeGridOrientationMemory(store);
        HomeGridOrientationRuntime runtime = new HomeGridOrientationRuntime(D8X4, memory);

        HomeGridLayoutSnapshot valid = runtime.captureCurrent(
                HomeGridOrientation.LANDSCAPE,
                Arrays.asList(pos(1,0,0,0,4,2), pos(2,0,7,3,1,1)));
        assertNotNull(valid);
        assertNotNull(memory.load(D8X4, HomeGridOrientation.LANDSCAPE));

        HomeGridLayoutSnapshot invalid = runtime.captureCurrent(
                HomeGridOrientation.PORTRAIT,
                Arrays.asList(pos(1,0,0,0,4,2), pos(2,0,0,1,1,1)));
        assertNull(invalid);
        assertNull(memory.load(D8X4, HomeGridOrientation.PORTRAIT));
    }

    @Test
    public void transientEmptyWorkspaceIsNeverPersistedOrRestored() {
        MapStore store = new MapStore();
        HomeGridOrientationMemory memory = new HomeGridOrientationMemory(store);
        HomeGridOrientationRuntime runtime = new HomeGridOrientationRuntime(D8X4, memory);

        assertNull(runtime.captureCurrent(
                HomeGridOrientation.PORTRAIT,
                java.util.Collections.emptyList()));
        assertNull(memory.load(D8X4, HomeGridOrientation.PORTRAIT));

        memory.save(HomeGridLayoutSnapshot.create(
                D8X4,
                HomeGridOrientation.PORTRAIT,
                java.util.Collections.emptyList()));
        assertNull(runtime.rememberedTarget(
                HomeGridOrientation.PORTRAIT,
                java.util.Collections.emptyList()));
        assertNull(memory.load(D8X4, HomeGridOrientation.PORTRAIT));
    }

    @Test
    public void rememberedTargetRequiresSameItemsScreenAndSpan() {
        MapStore store = new MapStore();
        HomeGridOrientationMemory memory = new HomeGridOrientationMemory(store);
        HomeGridOrientationRuntime runtime = new HomeGridOrientationRuntime(D8X4, memory);
        memory.save(snapshot(D8X4, HomeGridOrientation.PORTRAIT,
                pos(1,0,0,4,4,2), pos(2,0,3,7,1,1)));

        assertNotNull(runtime.rememberedTarget(
                HomeGridOrientation.PORTRAIT,
                Arrays.asList(pos(1,0,0,0,4,2), pos(2,0,7,3,1,1))));
        assertNull(runtime.rememberedTarget(
                HomeGridOrientation.PORTRAIT,
                Arrays.asList(pos(1,0,0,0,4,2), pos(3,0,7,3,1,1))));
        assertNull(runtime.rememberedTarget(
                HomeGridOrientation.PORTRAIT,
                Arrays.asList(pos(1,9,0,0,4,2), pos(2,0,7,3,1,1))));
    }

    @Test
    public void arbitraryInBoundsWidgetOriginIsRememberedOnOddGrid() {
        MapStore store = new MapStore();
        HomeGridOrientationMemory memory = new HomeGridOrientationMemory(store);
        HomeGridOrientationRuntime runtime = new HomeGridOrientationRuntime(D7X5, memory);
        memory.save(snapshot(D7X5, HomeGridOrientation.PORTRAIT,
                pos(1,0,1,3,2,2)));

        assertNotNull(runtime.rememberedTarget(
                HomeGridOrientation.PORTRAIT,
                Arrays.asList(pos(1,0,2,1,2,2))));
    }

    @Test
    public void preflightRegeneratesOtherOrientationWithoutChangingScreen() {
        MapStore store = new MapStore();
        HomeGridOrientationMemory memory = new HomeGridOrientationMemory(store);
        HomeGridOrientationRuntime runtime = new HomeGridOrientationRuntime(D7X5, memory);

        HomeGridLayoutSnapshot other = runtime.preflightOther(
                HomeGridOrientation.LANDSCAPE,
                Arrays.asList(
                        pos(1,5,1,1,2,2),
                        pos(2,5,6,4,1,1)));

        assertNotNull(other);
        assertEquals(5, other.get(1).screenId());
        assertEquals(5, other.get(2).screenId());
        assertFalse(other.get(1).overlaps(other.get(2)));
    }

    @Test
    public void impossibleOtherOrientationInvalidatesOnlyOtherMemory() {
        MapStore store = new MapStore();
        HomeGridOrientationMemory memory = new HomeGridOrientationMemory(store);
        HomeGridOrientationRuntime runtime = new HomeGridOrientationRuntime(D8X4, memory);
        memory.save(snapshot(D8X4, HomeGridOrientation.PORTRAIT, pos(99,0,0,0,1,1)));

        HomeGridLayoutSnapshot other = runtime.preflightOther(
                HomeGridOrientation.LANDSCAPE,
                Arrays.asList(pos(1,0,0,0,5,1)));

        assertNull(other);
        assertNotNull(memory.load(D8X4, HomeGridOrientation.LANDSCAPE));
        assertNull(memory.load(D8X4, HomeGridOrientation.PORTRAIT));
    }

    private static HomeGridLayoutSnapshot snapshot(HomeGridDimensions d,
            HomeGridOrientation o, HomeGridItemPosition... positions) {
        HomeGridLayoutSnapshot snapshot=HomeGridLayoutSnapshot.create(d,o,Arrays.asList(positions));
        if(snapshot==null) throw new AssertionError("invalid fixture");
        return snapshot;
    }
    private static HomeGridItemPosition pos(long id,long screen,int x,int y,int sx,int sy){
        return new HomeGridItemPosition(id,screen,x,y,sx,sy);
    }
    private static final class MapStore implements HomeGridOrientationMemoryStore {
        private final Map<String,String> values=new HashMap<>();
        public String read(String key){return values.get(key);}
        public void write(String key,String value){values.put(key,value);}
        public void remove(String key){values.remove(key);}
    }
}
