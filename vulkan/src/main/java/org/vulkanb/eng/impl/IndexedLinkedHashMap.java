package org.vulkanb.eng.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class IndexedLinkedHashMap<K, V> extends LinkedHashMap<K, V> {

    private final List<K> indexList = new ArrayList<>();

    public int getIndexOf(K key) {
        return this.indexList.indexOf(key);
    }

    public V getValueAtIndex(int i) {
        return super.get(this.indexList.get(i));
    }

    @Override
    public V put(K key, V val) {
        if (!super.containsKey(key)) this.indexList.add(key);
        return super.put(key, val);
    }
}
