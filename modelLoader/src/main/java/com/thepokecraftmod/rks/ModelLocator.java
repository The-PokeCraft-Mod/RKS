package com.thepokecraftmod.rks;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Method to find and locate files based on the name of the file.
 */
public interface ModelLocator {

    byte[] getFile(String name);

    BufferedImage readImage(List<String> layers);

    /**
     * Expects a Native Byte Buffer
     */
    BufferedImage readImage(String name);
}
