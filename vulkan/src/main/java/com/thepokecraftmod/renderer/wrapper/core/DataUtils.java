package com.thepokecraftmod.renderer.wrapper.core;

import java.util.List;

public class DataUtils {

    public static float[] toPrimitiveFloatArr(List<Float> list) {
        var size = list != null ? list.size() : 0;
        var floatArr = new float[size];
        for (var i = 0; i < size; i++) floatArr[i] = list.get(i);
        return floatArr;
    }

    public static int[] toPrimitiveIntArr(List<Integer> list) {
        return list.stream().mapToInt(v -> v).toArray();
    }
}
