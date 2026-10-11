package com.eu.habbo.imaging.camera;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.imaging.camera.render.CameraRender;
import com.eu.habbo.imaging.camera.render.CameraRenderImage;
import com.eu.habbo.imaging.camera.render.CameraRenderRoom;
import com.eu.habbo.imaging.camera.render.CameraUtils;
import com.eu.habbo.imaging.camera.render.JSONCamera;
import com.eu.habbo.imaging.camera.render.WallColorResolver;
import com.eu.habbo.messages.outgoing.camera.CameraStorageUrlMessageComposer;
import com.eu.habbo.messages.outgoing.camera.ThumbnailStatusMessageComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CameraRenderManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(CameraRenderManager.class);

    private final Path spritesDir;
    private final Path framesDir;
    private final Path binaryDir;
    private final Path outputDir;
    private final Path thumbnailOutputDir;
    private final String urlPrefix;
    private final CameraPaletteCache paletteCache;
    private final WallColorResolver wallColorResolver;

    public CameraRenderManager() {
        long millis = System.currentTimeMillis();
        LOGGER.info("Camera Renderer -> Loading...");
        this.spritesDir = Paths.get(requireConfigValue("camera.assets.sprites.path")).toAbsolutePath().normalize();
        this.framesDir = Paths.get(requireConfigValue("camera.assets.frames.path")).toAbsolutePath().normalize();
        this.binaryDir = Paths.get(requireConfigValue("camera.assets.binary.path")).toAbsolutePath().normalize();
        this.outputDir = Paths.get(requireConfigValue("camera.output.path")).toAbsolutePath().normalize();
        this.thumbnailOutputDir = Paths.get(requireConfigValue("camera.output.thumbnail.path")).toAbsolutePath().normalize();

        String configuredUrl = requireConfigValue("camera.output.url");
        if (!configuredUrl.endsWith("/")) {
            configuredUrl = configuredUrl + "/";
        }
        this.urlPrefix = configuredUrl;

        try {
            Files.createDirectories(this.outputDir);
            Files.createDirectories(this.thumbnailOutputDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create camera output directories at {} and {}", this.outputDir, this.thumbnailOutputDir, e);
        }

        if (!Files.isDirectory(this.spritesDir)) {
            LOGGER.warn("Camera sprites directory does not exist: {} (renders will be missing textures and decorations)", this.spritesDir);
        }
        if (!Files.isDirectory(this.framesDir)) {
            LOGGER.warn("Camera frames directory does not exist: {} (renders will be missing frame overlays)", this.framesDir);
        }

        this.paletteCache = new CameraPaletteCache(this.binaryDir);

        String wallColorsConfig = Emulator.getConfig().containsKey("camera.wall.colors.path")
                ? Emulator.getConfig().getValue("camera.wall.colors.path")
                : "./camera_wall_colors.properties";
        Path wallColorsPath = Paths.get(wallColorsConfig).toAbsolutePath().normalize();
        this.wallColorResolver = new WallColorResolver(wallColorsPath);

        LOGGER.info("Camera Renderer -> Loaded! ({} MS, {} palettes, {} wall overrides)",
                System.currentTimeMillis() - millis, this.paletteCache.size(), this.wallColorResolver.size());
        LOGGER.debug("Camera Renderer -> Paths: sprites={}, frames={}, binary={}, output={}, thumbnailOutput={}, url={}",
                this.spritesDir, this.framesDir, this.binaryDir, this.outputDir, this.thumbnailOutputDir, this.urlPrefix);
    }

    public void renderPhotoAsync(CameraRenderRequest request) {
        Emulator.getThreading().run(() -> renderPhoto(request));
    }

    public void renderThumbnailAsync(CameraRenderRequest request) {
        Emulator.getThreading().run(() -> renderThumbnail(request));
    }

    private void renderPhoto(CameraRenderRequest request) {
        try {
            CameraParser parser = new CameraParser(request.json());
            JSONCamera scene = parser.getResult();

            if (scene == null) {
                LOGGER.warn("Camera photo parse failed for user={} userId={} roomId={} timestamp={}", request.username(), request.userId(), request.roomId(), request.timestamp());
                handlePhotoRenderError(request);
                return;
            }

            LOGGER.debug("Camera photo render: user={} zoom={}", request.username(), scene.getZoom());
            CameraRender render = new CameraRenderImage(scene, request.backgroundColor(), this.spritesDir, this.framesDir, this.paletteCache, request.wallPaint(), this.wallColorResolver);
            BufferedImage image = render.render();
            if (scene.getZoom() > 1) {
                int zoom = Math.min(scene.getZoom(), 4);
                int cropW = image.getWidth() / zoom;
                int cropH = image.getHeight() / zoom;
                int cropX = (image.getWidth() - cropW) / 2;
                int cropY = (image.getHeight() - cropH) / 2;
                BufferedImage cropped = image.getSubimage(cropX, cropY, cropW, cropH);
                image = CameraUtils.resize(cropped, image.getWidth(), image.getHeight());
            }

            String relativePath = request.userId() + "/" + request.userId() + "_" + request.timestamp() + ".png";
            saveImage(image, relativePath);

            String smallRelativePath = request.userId() + "/" + request.userId() + "_" + request.timestamp() + "_small.png";
            BufferedImage smallImage = CameraUtils.resize(image, 100, 100);
            saveImage(smallImage, smallRelativePath);

            commitPhoto(request, composeUrl(relativePath));
        } catch (Exception e) {
            LOGGER.error("Failed to render camera photo for {} (roomId={} timestamp={})", request.username(), request.roomId(), request.timestamp(), e);
            handlePhotoRenderError(request);
        }
    }

    private void renderThumbnail(CameraRenderRequest request) {
        try {
            CameraParser parser = new CameraParser(request.json());
            JSONCamera scene = parser.getResult();

            if (scene == null) {
                LOGGER.warn("Camera thumbnail parse failed for user={} userId={} roomId={}", request.username(), request.userId(), request.roomId());
                sendThumbnailStatus(request);
                return;
            }

            CameraRender render = new CameraRenderRoom(scene, request.backgroundColor(), this.spritesDir, this.framesDir, this.paletteCache, request.wallPaint(), this.wallColorResolver);
            BufferedImage image = render.render();

            String relativePath = request.roomId() + ".png";
            saveImage(image, this.thumbnailOutputDir, relativePath);

            sendThumbnailStatus(request);
        } catch (Exception e) {
            LOGGER.error("Failed to render camera thumbnail for {} (roomId={})", request.username(), request.roomId(), e);
            sendThumbnailStatus(request);
        }
    }

    private void commitPhoto(CameraRenderRequest request, String url) {
        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(request.userId());
        if (habbo == null || habbo.getClient() == null) {
            return;
        }
        if (habbo.getHabboInfo().getPhotoTimestamp() != request.timestamp()) {
            return;
        }

        AchievementManager.progressAchievement(habbo, Emulator.getGameEnvironment().getAchievementManager().getAchievement("CameraPhotoCount"), 1);
        habbo.getClient().sendResponse(new CameraStorageUrlMessageComposer(url));
        habbo.getHabboInfo().setPhotoJSON(habbo.getHabboInfo().getPhotoJSON().replace("%room_id%", request.roomId() + "").replace("%url%", url));
        habbo.getHabboInfo().setPhotoURL(url);
    }

    private void handlePhotoRenderError(CameraRenderRequest request) {
        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(request.userId());
        if (habbo == null) {
            return;
        }
        if (habbo.getHabboInfo().getPhotoTimestamp() != request.timestamp()) {
            return;
        }

        habbo.getHabboInfo().setPhotoTimestamp(0);
        habbo.getHabboInfo().setPhotoJSON("");
        habbo.getHabboInfo().setPhotoURL("");
        habbo.alert(Emulator.getTexts().getValue("camera.error.creation"));
    }

    private void sendThumbnailStatus(CameraRenderRequest request) {
        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(request.userId());
        if (habbo == null || habbo.getClient() == null) {
            return;
        }
        habbo.getClient().sendResponse(new ThumbnailStatusMessageComposer());
    }

    private void saveImage(BufferedImage image, String relativePath) throws IOException {
        saveImage(image, this.outputDir, relativePath);
    }

    private void saveImage(BufferedImage image, Path baseDir, String relativePath) throws IOException {
        Path resolved = baseDir.resolve(relativePath).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new IOException("Path traversal rejected: " + resolved + " escapes " + baseDir);
        }
        File file = resolved.toFile();
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Failed to create directory: " + parent.getAbsolutePath());
        }
        ImageIO.write(image, "png", file);
    }

    private String composeUrl(String relativePath) {
        String url = this.urlPrefix + relativePath.replace("\\", "/");
        if (!Emulator.getConfig().getBoolean("camera.use.https", true)) {
            url = url.replace("https://", "http://");
        }
        return url;
    }

    private static String requireConfigValue(String key) {
        String value = Emulator.getConfig().getValue(key);
        if (value.isBlank()) {
            throw new IllegalStateException("Required camera config key is missing or blank: " + key);
        }
        return value;
    }
}
