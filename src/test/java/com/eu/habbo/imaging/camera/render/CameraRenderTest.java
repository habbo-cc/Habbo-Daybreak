package com.eu.habbo.imaging.camera.render;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.threading.ThreadPooling;
import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CameraRenderTest {
    private static final int BLUE_BACKGROUND = 0x8BDFEF;
    private static final int BLUE_TONED_SIDE_WALL = 0x6FB2BF;

    @TempDir
    Path spritesDir;

    @TempDir
    Path framesDir;

    @BeforeAll
    static void installEmulatorConfig() throws Exception {
        Path configFile = Files.createTempFile("camera-render-test", ".ini");
        Files.writeString(configFile, """
                camera.image.fetch.max.per.render=30
                camera.image.fetch.budget.ms=8000
                camera.image.fetch.connect.timeout.ms=2000
                camera.image.fetch.read.timeout.ms=3000
                camera.image.fetch.max.bytes=2097152
                camera.allowed.image.hosts=
                """);

        Field configField = Emulator.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(null, new ConfigurationManager(configFile.toString()));

        Field threadingField = Emulator.class.getDeclaredField("threading");
        threadingField.setAccessible(true);
        threadingField.set(null, new ThreadPooling(1));
    }

    @Test
    void spriteLoaderRejectsPathTraversalAssets() {
        CameraImageLoader imageLoader = new CameraImageLoader(spritesDir, framesDir);

        assertNull(imageLoader.spriteFile("../escape"));
        assertNull(imageLoader.readSprite("../escape"));
    }

    @Test
    void rendersTexturedWallUsingPlaneColor() throws Exception {
        writeSolidSprite("wall_texture_64_3_wall_color_jagged3", 32, 16, 0xFFFFFF);

        BufferedImage image = renderScene("""
                {
                  "roomid": 1,
                  "planes": [
                    {
                      "z": 0,
                      "color": %d,
                      "cornerPoints": [{"x":0,"y":0},{"x":320,"y":0},{"x":0,"y":320},{"x":320,"y":320}]
                    },
                    {
                      "z": 1,
                      "color": %d,
                      "bottomAligned": false,
                      "cornerPoints": [{"x":20,"y":50},{"x":180,"y":130},{"x":20,"y":230},{"x":180,"y":310}],
                      "texCols": [{"assetNames": ["wall_texture_64_3_wall_color_jagged3"]}]
                    }
                  ],
                  "sprites": [],
                  "filters": []
                }
                """.formatted(BLUE_BACKGROUND, BLUE_TONED_SIDE_WALL));

        assertRgb(BLUE_TONED_SIDE_WALL, image.getRGB(80, 160));
    }

    @Test
    void keepsPlaneColorWhenWallTextureAssetIsMissing() {
        BufferedImage image = renderScene("""
                {
                  "roomid": 1,
                  "planes": [
                    {
                      "z": 0,
                      "color": %d,
                      "cornerPoints": [{"x":0,"y":0},{"x":320,"y":0},{"x":0,"y":320},{"x":320,"y":320}]
                    },
                    {
                      "z": 1,
                      "color": %d,
                      "bottomAligned": false,
                      "cornerPoints": [{"x":20,"y":50},{"x":180,"y":130},{"x":20,"y":230},{"x":180,"y":310}],
                      "texCols": [{"assetNames": ["wall_texture_64_0_missing"]}]
                    }
                  ],
                  "sprites": [],
                  "filters": []
                }
                """.formatted(BLUE_BACKGROUND, BLUE_TONED_SIDE_WALL));

        assertRgb(BLUE_TONED_SIDE_WALL, image.getRGB(80, 160));
    }

    @Test
    void wallPaintOverrideReplacesTexturedWallPlaneColor() throws Exception {
        writeSolidSprite("wall_texture_64_3_wall_color_jagged3", 32, 16, 0xFFFFFF);
        Path overrideFile = spritesDir.resolve("camera_wall_colors.properties");
        Files.writeString(overrideFile, "101=0x8844CC\n");
        WallColorResolver resolver = new WallColorResolver(overrideFile);

        assertEquals(1, resolver.size());

        BufferedImage image = renderScene("""
                {
                  "roomid": 1,
                  "planes": [
                    {
                      "z": 0,
                      "color": %d,
                      "cornerPoints": [{"x":0,"y":0},{"x":320,"y":0},{"x":0,"y":320},{"x":320,"y":320}]
                    },
                    {
                      "z": 1,
                      "color": 7320255,
                      "bottomAligned": false,
                      "cornerPoints": [{"x":20,"y":50},{"x":180,"y":130},{"x":20,"y":230},{"x":180,"y":310}],
                      "texCols": [{"assetNames": ["wall_texture_64_3_wall_color_jagged3"]}]
                    }
                  ],
                  "sprites": [],
                  "filters": []
                }
                """.formatted(BLUE_BACKGROUND), "101", resolver);

        assertRgb(0x8844CC, image.getRGB(80, 160));
    }

    @Test
    void bitmapWallMaskRevealsOnlyMaskedAvatarPixels() throws Exception {
        writeSolidSprite("wall_texture_test_white", 10, 10, 0xFFFFFF);
        writeSolidSprite("avatar_test_green", 20, 40, 0x00FF00);
        writeHalfAlphaSprite("door_mask_half", 20, 20);
        int background = 0x334422;

        BufferedImage image = renderScene("""
                {
                  "roomid": 1,
                  "planes": [
                    {
                      "z": 0,
                      "color": %d,
                      "cornerPoints": [{"x":0,"y":0},{"x":320,"y":0},{"x":0,"y":320},{"x":320,"y":320}]
                    },
                    {
                      "z": 1,
                      "color": 16711680,
                      "bottomAligned": false,
                      "cornerPoints": [{"x":60,"y":60},{"x":20,"y":60},{"x":60,"y":100},{"x":20,"y":100}],
                      "texCols": [{"assetNames": ["wall_texture_test_white"]}],
                      "masks": [{"name": "door_mask_half", "location": {"x":0,"y":0}, "flipH": false, "flipV": false}]
                    }
                  ],
                  "sprites": [
                    {"name": "avatar_test_green", "x": 20, "y": 60, "z": 0}
                  ],
                  "filters": []
                }
                """.formatted(background));

        assertRgb(0xFF0000, image.getRGB(50, 70));
        assertRgb(0x00FF00, image.getRGB(25, 90));
        assertRgb(0xFF0000, image.getRGB(35, 90));
    }

    @Test
    void untexturedWallTopCapUsesOwnPlaneColor() throws Exception {
        writeSolidSprite("wall_texture_test_white", 8, 8, 0xFFFFFF);
        int background = 0x000000;
        int wallColor = 0xC9A400;

        BufferedImage image = renderScene("""
                {
                  "roomid": 1,
                  "planes": [
                    {
                      "z": 0,
                      "color": %d,
                      "cornerPoints": [{"x":0,"y":0},{"x":320,"y":0},{"x":0,"y":320},{"x":320,"y":320}]
                    },
                    {
                      "z": 1,
                      "color": %d,
                      "bottomAligned": false,
                      "cornerPoints": [{"x":180,"y":70},{"x":20,"y":150},{"x":180,"y":10},{"x":20,"y":90}],
                      "texCols": [{"assetNames": ["wall_texture_test_white"]}]
                    },
                    {
                      "z": 1,
                      "color": 8021760,
                      "bottomAligned": false,
                      "cornerPoints": [{"x":20,"y":90},{"x":12,"y":86},{"x":180,"y":10},{"x":172,"y":6}]
                    }
                  ],
                  "sprites": [],
                  "filters": []
                }
                """.formatted(background, wallColor));

        assertRgb(0x7A6700, image.getRGB(100, 48));
    }

    @Test
    void rendersUntexturedRoomPlaneUsingItsPlaneColor() {
        BufferedImage image = renderScene("""
                {
                  "roomid": 1,
                  "planes": [
                    {
                      "z": 0,
                      "color": %d,
                      "cornerPoints": [{"x":0,"y":0},{"x":320,"y":0},{"x":0,"y":320},{"x":320,"y":320}]
                    },
                    {
                      "z": 1,
                      "color": 7320255,
                      "bottomAligned": false,
                      "cornerPoints": [{"x":20,"y":50},{"x":180,"y":130},{"x":20,"y":230},{"x":180,"y":310}]
                    }
                  ],
                  "sprites": [],
                  "filters": []
                }
                """.formatted(BLUE_BACKGROUND));

        assertRgb(BLUE_TONED_SIDE_WALL, image.getRGB(80, 160));
    }

    @Test
    void mapsFloorTextureColumnsAlongClientWidthAxis() throws Exception {
        writeSolidSprite("floor_texture_test_red", 10, 10, 0xFF0000);
        writeSolidSprite("floor_texture_test_blue", 10, 10, 0x0000FF);

        BufferedImage image = renderScene("""
                {
                  "roomid": 1,
                  "planes": [
                    {
                      "z": 0,
                      "color": 16777215,
                      "cornerPoints": [{"x":0,"y":0},{"x":320,"y":0},{"x":0,"y":320},{"x":320,"y":320}]
                    },
                    {
                      "z": 1,
                      "color": 16777215,
                      "bottomAligned": false,
                      "cornerPoints": [{"x":100,"y":40},{"x":60,"y":60},{"x":140,"y":60},{"x":100,"y":80}],
                      "texCols": [
                        {"assetNames": ["floor_texture_test_red"]},
                        {"assetNames": ["floor_texture_test_blue"]}
                      ]
                    }
                  ],
                  "sprites": [],
                  "filters": []
                }
                """);

        assertRgb(0xFF0000, image.getRGB(115, 53));
        assertRgb(0x0000FF, image.getRGB(105, 58));
    }

    @Test
    void mapsWallTextureRowsAlongWallHeightAxis() throws Exception {
        writeVerticalSplitSprite("wall_texture_test_horizontal_bands", 4, 4, 0xFF0000, 0x0000FF);

        BufferedImage image = renderScene("""
                {
                  "roomid": 1,
                  "planes": [
                    {
                      "z": 0,
                      "color": 16777215,
                      "cornerPoints": [{"x":0,"y":0},{"x":320,"y":0},{"x":0,"y":320},{"x":320,"y":320}]
                    },
                    {
                      "z": 1,
                      "color": 16777215,
                      "bottomAligned": false,
                      "cornerPoints": [{"x":60,"y":60},{"x":20,"y":60},{"x":60,"y":100},{"x":20,"y":100}],
                      "texCols": [{"assetNames": ["wall_texture_test_horizontal_bands"]}]
                    }
                  ],
                  "sprites": [],
                  "filters": []
                }
                """);

        assertRgb(0x0000FF, image.getRGB(50, 61));
        assertRgb(0x0000FF, image.getRGB(30, 61));
        assertRgb(0xFF0000, image.getRGB(50, 63));
    }

    private BufferedImage renderScene(String json) {
        JSONCamera scene = new Gson().fromJson(json, JSONCamera.class);
        WallColorResolver resolver = new WallColorResolver(null);
        return new CameraRenderImage(scene, 0x000000, spritesDir, framesDir, null, null, resolver).render();
    }

    private BufferedImage renderScene(String json, String wallPaint, WallColorResolver resolver) {
        JSONCamera scene = new Gson().fromJson(json, JSONCamera.class);
        return new CameraRenderImage(scene, 0x000000, spritesDir, framesDir, null, wallPaint, resolver).render();
    }

    private void writeSolidSprite(String name, int width, int height, int rgb) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, 0xFF000000 | rgb);
            }
        }
        ImageIO.write(image, "png", spritesDir.resolve(name + ".png").toFile());
    }

    private void writeVerticalSplitSprite(String name, int width, int height, int topRgb, int bottomRgb) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            int rgb = y < height / 2 ? topRgb : bottomRgb;
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, 0xFF000000 | rgb);
            }
        }
        ImageIO.write(image, "png", spritesDir.resolve(name + ".png").toFile());
    }

    private void writeHalfAlphaSprite(String name, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < width / 2; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, 0xFFFFFFFF);
            }
        }
        ImageIO.write(image, "png", spritesDir.resolve(name + ".png").toFile());
    }

    private void assertRgb(int expected, int actual) {
        Color actualColor = new Color(actual);
        Color expectedColor = new Color(expected);
        String message = "expected #%06X but was #%06X".formatted(expected & 0xFFFFFF, actual & 0xFFFFFF);
        assertEquals(expectedColor.getRed(), actualColor.getRed(), 2, "red mismatch: " + message);
        assertEquals(expectedColor.getGreen(), actualColor.getGreen(), 2, "green mismatch: " + message);
        assertEquals(expectedColor.getBlue(), actualColor.getBlue(), 2, "blue mismatch: " + message);
    }
}
