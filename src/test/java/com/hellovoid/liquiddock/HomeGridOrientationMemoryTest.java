package com.hellovoid.liquiddock;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class HomeGridOrientationMemoryTest {
    private static final HomeGridDimensions D8X4 = new HomeGridDimensions(8, 4);
    private static final HomeGridDimensions D10X6 = new HomeGridDimensions(10, 6);

    @Test
    public void snapshotRoundTripsWithoutChangingCoordinates() {
        MapStore store = new MapStore();
        HomeGridOrientationMemory memory = new HomeGridOrientationMemory(store);
        HomeGridLayoutSnapshot original = snapshot(
                D10X6, HomeGridOrientation.PORTRAIT,
                pos(1,11,0,6,4,2), pos(2,11,5,9,1,1));
        memory.save(original);

        HomeGridLayoutSnapshot loaded = memory.load(D10X6, HomeGridOrientation.PORTRAIT);
        assertNotNull(loaded);
        assertEquals(0, loaded.get(1).cellX());
        assertEquals(6, loaded.get(1).cellY());
        assertEquals(5, loaded.get(2).cellX());
    }

    @Test
    public void dimensionsAndOrientationAreIndependentNamespaces() {
        MapStore store = new MapStore();
        HomeGridOrientationMemory memory = new HomeGridOrientationMemory(store);
        memory.save(snapshot(D8X4, HomeGridOrientation.LANDSCAPE, pos(1,0,7,3,1,1)));

        assertNotNull(memory.load(D8X4, HomeGridOrientation.LANDSCAPE));
        assertNull(memory.load(D8X4, HomeGridOrientation.PORTRAIT));
        assertNull(memory.load(D10X6, HomeGridOrientation.LANDSCAPE));
    }

    @Test
    public void corruptPayloadNeverProducesPartialSnapshot() {
        MapStore store = new MapStore();
        HomeGridOrientationMemory memory = new HomeGridOrientationMemory(store);
        memory.save(snapshot(D8X4, HomeGridOrientation.PORTRAIT, pos(1,0,0,0,1,1)));
        store.values.put(store.lastWrittenKey, "v2|8x4|PORTRAIT\n1,0,0,0,1,1\nBROKEN");
        assertNull(memory.load(D8X4, HomeGridOrientation.PORTRAIT));
    }

    @Test
    public void invalidateRemovesOnlyRequestedOrientation() {
        MapStore store = new MapStore();
        HomeGridOrientationMemory memory = new HomeGridOrientationMemory(store);
        memory.save(snapshot(D8X4, HomeGridOrientation.LANDSCAPE, pos(1,0,0,0,1,1)));
        memory.save(snapshot(D8X4, HomeGridOrientation.PORTRAIT, pos(1,0,0,0,1,1)));
        memory.invalidate(D8X4, HomeGridOrientation.PORTRAIT);
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
        final Map<String,String> values=new HashMap<>();
        String lastWrittenKey;
        public String read(String key){return values.get(key);}
        public void write(String key,String value){lastWrittenKey=key;values.put(key,value);}
        public void remove(String key){values.remove(key);}
    }
}
