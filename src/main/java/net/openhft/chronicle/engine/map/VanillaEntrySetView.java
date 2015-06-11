package net.openhft.chronicle.engine.map;

import net.openhft.chronicle.engine.api.map.MapView;
import net.openhft.chronicle.engine.api.set.EntrySetView;
import net.openhft.chronicle.engine.api.tree.Asset;
import net.openhft.chronicle.engine.api.tree.AssetNotFoundException;
import net.openhft.chronicle.engine.api.tree.RequestContext;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Created by peter on 22/05/15.
 */
public class VanillaEntrySetView<K, MV, V> extends AbstractCollection<Map.Entry<K, V>> implements EntrySetView<K, MV, V> {
    private Asset asset;
    private MapView<K, MV, V> mapView;

    public VanillaEntrySetView(RequestContext context, Asset asset, @NotNull MapView<K, MV, V> mapView) throws AssetNotFoundException {
        this.asset = asset;
        this.mapView = mapView;
    }

    @NotNull
    @Override
    public Iterator<Map.Entry<K, V>> iterator() {
        return mapView.underlying().entrySetIterator();
    }

    @Override
    public int size() {
        return (int) Math.min(Integer.MAX_VALUE, mapView.size());
    }

    @Override
    public Asset asset() {
        return asset;
    }

    @Override
    public MapView<K, MV, V> underlying() {
        return mapView;
    }

    @Override
    public boolean keyedView() {
        return true;
    }

    @Override
    public int hashCode() {
        int h = 0;
        for (Map.Entry<K, V> entry : this) {
            h += entry.hashCode();
        }
        return h;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Set))
            return false;
        Set<Map.Entry<K, V>> set = (Set<Map.Entry<K, V>>) obj;
        if (set.size() != size()) return false;
        for (Map.Entry<K, V> entry : set) {
            if (entry == null)
                return false;
            K key = entry.getKey();
            if (key == null)
                return false;
            V value = mapView.get(key);
            if (!Objects.equals(entry.getValue(), value))
                return false;
        }
        return true;

    }
}
