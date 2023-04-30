package com.thepokecraftmod.vulkan.util;

import com.thebombzen.jxlatte.JXLDecoder;
import com.thebombzen.jxlatte.JXLOptions;
import com.thepokecraftmod.rks.ModelLocator;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

public class TestModelLocator implements ModelLocator {

    private final Map<String, byte[]> fileCache = new HashMap<>();
    private final String root;

    public TestModelLocator(String root) {
        this.root = root;
    }

    public BufferedImage read(byte[] imageBytes) throws IOException {
        var options = new JXLOptions();
        options.hdr = JXLOptions.HDR_OFF;
        options.threads = 2;
        var reader = new JXLDecoder(new ByteArrayInputStream(imageBytes), options);
        var image = reader.decode();
        return image.asBufferedImage();
    }

    @Override
    public BufferedImage readImage(String name) {
        try {
            var cleanString = root + "/" + name.replace("\\", "/").replace("//", "/");
            var is = Objects.requireNonNull(TestModelLocator.class.getResourceAsStream(cleanString), "Texture InputStream is null");
            var image = cleanString.endsWith(".jxl") ? read(is.readAllBytes()) : ImageIO.read(is);
            var height = image.getHeight();
            var width = image.getWidth();
            var needMirror = height / width == 2;

            if (needMirror) {
                var mirror = new BufferedImage(width * 2, height, BufferedImage.TYPE_INT_ARGB);
                for (var y = 0; y < height; y++)
                    for (int lx = 0, rx = width * 2 - 1; lx < width; lx++, rx--) {
                        var p = image.getRGB(lx, y);
                        mirror.setRGB(lx, y, p);
                        mirror.setRGB(rx, y, p);
                    }

                image = mirror;
            }

            return image;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public BufferedImage readImage(List<String> layers) {
        var imageReferences = layers.stream().map(s -> "textures/" + s).map(this::getFile).toList();

        var loadedImages = imageReferences.stream().map(bytes -> {
            try {
                var options = new JXLOptions();
                options.hdr = JXLOptions.HDR_OFF;
                options.threads = 2;
                var reader = new JXLDecoder(new ByteArrayInputStream(bytes), options);
                var image = reader.decode();
                return image.fillColor().asBufferedImage();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).toList();
        var processedImages = new ArrayList<BufferedImage>();

        for (var image : loadedImages) {
            var width = image.getWidth();
            var height = image.getHeight();
            var needMirror = height / width == 2;

            if (needMirror) {
                var mirror = new BufferedImage(width * 2, height, BufferedImage.TYPE_INT_ARGB);
                for (var y = 0; y < height; y++)
                    for (int lx = 0, rx = width * 2 - 1; lx < width; lx++, rx--) {
                        var p = image.getRGB(lx, y);
                        mirror.setRGB(lx, y, p);
                        mirror.setRGB(rx, y, p);
                    }

                processedImages.add(mirror);
            } else processedImages.add(image);
        }

        if (processedImages.size() == 0) return new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);

        var baseImage = processedImages.get(0);
        var topLayers = processedImages.subList(1, layers.size());
        for (var topLayer : topLayers)
            for (var x = 0; x < baseImage.getWidth(); x++)
                for (var y = 0; y < baseImage.getHeight(); y++) {
                    var p = topLayer.getRGB(x, y);
                    var alpha = 0xFF & (p >> 24);
                    var red = 0xFF & (p >> 16);
                    var green = 0xFF & (p >> 8);
                    var blue = 0xFF & (p);
                    // TODO: option to set bg color
                    if (green < 200) baseImage.setRGB(x, y, p);
                }

        return baseImage;
    }

    @Override
    public byte[] getFile(String name) {
        return fileCache.computeIfAbsent(name, s -> {
            try {
                var internalPath = "/" + root + "/" + name;
                var is = Objects.requireNonNull(TestModelLocator.class.getResourceAsStream("/" + root + "/" + name), internalPath + " doesnt exist");
                return is.readAllBytes();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
