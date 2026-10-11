package com.eu.habbo.imaging.camera;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class CameraPaletteCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(CameraPaletteCache.class);

    private final Map<String, CameraPalette> palettes = new HashMap<>();

    public CameraPaletteCache(Path binaryDir) {
        File binaryFolder = binaryDir.toFile();

        if (!binaryFolder.isDirectory()) {
            LOGGER.warn("Camera binary directory does not exist: {} (palette-driven sprite recolouring will be skipped)", binaryFolder.getAbsolutePath());
            return;
        }

        DocumentBuilder dBuilder;
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dBuilder = dbFactory.newDocumentBuilder();
        } catch (Exception e) {
            LOGGER.error("Failed to create XML document builder for camera palettes", e);
            return;
        }

        File[] files = binaryFolder.listFiles((dir, name) -> name.endsWith("_assets.xml"));
        if (files == null || files.length == 0) {
            LOGGER.warn("Camera Palette Cache -> No *_assets.xml files found in {}", binaryFolder.getAbsolutePath());
            return;
        }

        int loaded = 0;
        for (File file : files) {
            try {
                Document doc = dBuilder.parse(file);
                doc.getDocumentElement().normalize();

                NodeList nList = doc.getElementsByTagName("palette");

                for (int i = 0; i < nList.getLength(); i++) {
                    try {
                        Node nNode = nList.item(i);
                        String name = ((Element) nNode).getAttribute("source");

                        if (name.isEmpty() || this.palettes.containsKey(name)) {
                            continue;
                        }

                        Path path = binaryDir.resolve(name + ".bin");
                        if (!Files.exists(path)) {
                            continue;
                        }

                        int[] color = new int[256];
                        ByteBuffer buffer = ByteBuffer.wrap(Files.readAllBytes(path));
                        int index = 0;

                        while (buffer.remaining() >= 3 && index < 256) {
                            color[index] =
                                    (0xFF << 24) |
                                    ((buffer.get() & 0xFF) << 16) |
                                    ((buffer.get() & 0xFF) << 8) |
                                    (buffer.get() & 0xFF);
                            index++;
                        }

                        this.palettes.put(name, new CameraPalette(name, color));
                        loaded++;
                    } catch (Exception inner) {
                        LOGGER.debug("Failed to load palette entry from {}", file.getName(), inner);
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("Failed to parse {} as a camera assets XML", file.getName(), e);
            }
        }

        LOGGER.debug("Camera Palette Cache -> Loaded {} palettes from {}.", loaded, binaryFolder.getAbsolutePath());
    }

    public CameraPalette getPalette(String name) {
        return this.palettes.get(name);
    }

    public int size() {
        return this.palettes.size();
    }
}
