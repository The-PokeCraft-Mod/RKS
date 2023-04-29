package com.thepokecraftmod.renderer;

import java.util.List;

public class EngineUtils {

    private EngineUtils() {
        // Utility class
    }

    public static float[] listFloatToArray(List<Float> list) {
        var size = list != null ? list.size() : 0;
        var floatArr = new float[size];
        for (var i = 0; i < size; i++) floatArr[i] = list.get(i);
        return floatArr;
    }

    public static int[] listIntToArray(List<Integer> list) {
        return list.stream().mapToInt((Integer v) -> v).toArray();
    }
}
