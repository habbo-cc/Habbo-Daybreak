package com.eu.habbo;

import ch.qos.logback.classic.Level;
import com.eu.habbo.core.*;
import com.eu.habbo.core.consolecommands.ConsoleCommand;
import com.eu.habbo.database.Database;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.imaging.camera.CameraRenderManager;
import com.eu.habbo.networking.gameserver.GameServer;
import com.eu.habbo.networking.rconserver.RCONServer;
import com.eu.habbo.plugin.PluginManager;
import com.eu.habbo.plugin.events.emulator.EmulatorConfigUpdatedEvent;
import com.eu.habbo.plugin.events.emulator.EmulatorLoadedEvent;
import com.eu.habbo.plugin.events.emulator.EmulatorStartShutdownEvent;
import com.eu.habbo.plugin.events.emulator.EmulatorStoppedEvent;
import com.eu.habbo.threading.ThreadPooling;
import com.eu.habbo.util.imager.badges.BadgeImager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Emulator {

    private static final Logger LOGGER = LoggerFactory.getLogger(Emulator.class);

    public final static int MAJOR = 4;
    public final static int MINOR = 0;
    public final static int BUILD = 3;
    public final static String PREVIEW = "beta";

    public static final String version = "Habbo Daybreak Developer Preview";
    private static final String[] LOGO = {
            "       __            __                    __  ",
            "  ____/ /___ ___  __/ /_  ________  ____ _/ /__",
            " / __  / __ `/ / / / __ \\/ ___/ _ \\/ __ `/ //_/",
            "/ /_/ / /_/ / /_/ / /_/ / /  /  __/ /_/ / ,<   ",
            "\\__,_/\\__,_/\\__, /_.___/_/   \\___/\\__,_/_/|_|  ",
            "           /____/                              ",
            " \\____________________________________________/"
    };

    private static final String TAGLINE = "Welcome to 2026.";

    // Sunrise gradient stops (violet -> pink -> amber -> pale gold), left to right.
    private static final int[][] DAWN = {
            {138, 96, 255},
            {255, 108, 145},
            {255, 176, 64},
            {255, 238, 160}
    };

    public static String build = "";
    public static boolean isReady = false;
    public static boolean isShuttingDown = false;
    public static boolean stopped = false;
    public static boolean debugging = false;
    private static int timeStarted = 0;
    private static Runtime runtime;
    private static ConfigurationManager config;
    private static CryptoConfig crypto;
    private static TextsManager texts;
    private static GameServer gameServer;
    private static RCONServer rconServer;
    private static CameraRenderManager cameraRenderManager;
    private static Logging logging;
    private static Database database;
    private static DatabaseLogger databaseLogger;
    private static ThreadPooling threading;
    private static GameEnvironment gameEnvironment;
    private static PluginManager pluginManager;
    private static BadgeImager badgeImager;

    static {
        Thread hook = new Thread(new Runnable() {
            public synchronized void run() {
                Emulator.dispose();
            }
        });
        hook.setPriority(10);
        Runtime.getRuntime().addShutdownHook(hook);
    }

    public static void main(String[] args) throws Exception {
        try {
            // Disable Netty's use of sun.misc.Unsafe to avoid JVM warnings
            System.setProperty("io.netty.noUnsafe", "true");
            System.setProperty("io.netty.noPreferDirect", "true");

            Locale.setDefault(Locale.of("en"));
            setBuild();
            Emulator.stopped = false;
            ConsoleCommand.load();
            Emulator.logging = new Logging();

            printLogo();
            printBanner();

            long startTime = System.nanoTime();

            Emulator.runtime = Runtime.getRuntime();
            Emulator.config = new ConfigurationManager("config.ini");
            Emulator.crypto = new CryptoConfig(
                    Emulator.getConfig().getBoolean("enc.enabled", false),
                    Emulator.getConfig().getValue("enc.e"),
                    Emulator.getConfig().getValue("enc.n"),
                    Emulator.getConfig().getValue("enc.d"));
            Emulator.database = new Database(Emulator.getConfig());
            Emulator.databaseLogger = new DatabaseLogger();
            Emulator.config.loaded = true;
            Emulator.config.loadFromDatabase();
            Emulator.threading = new ThreadPooling(Emulator.getConfig().getInt("runtime.threads"));
            Emulator.getDatabase().getDataSource().setMaximumPoolSize(Emulator.getConfig().getInt("runtime.threads") * 2);
            Emulator.getDatabase().getDataSource().setMinimumIdle(10);
            Emulator.pluginManager = new PluginManager();
            Emulator.pluginManager.reload();
            Emulator.getPluginManager().fireEvent(new EmulatorConfigUpdatedEvent());
            Emulator.texts = new TextsManager();
            new CleanerThread();
            Emulator.gameServer = new GameServer(getConfig().getValue("game.host", "127.0.0.1"), getConfig().getInt("game.port", 30000));
            Emulator.rconServer = new RCONServer(getConfig().getValue("rcon.host", "127.0.0.1"), getConfig().getInt("rcon.port", 30001));
            Emulator.gameEnvironment = new GameEnvironment();
            Emulator.gameEnvironment.load();
            Emulator.gameServer.initializePipeline();
            Emulator.gameServer.connect();
            Emulator.rconServer.initializePipeline();
            Emulator.rconServer.connect();
            Emulator.badgeImager = new BadgeImager();
            if (Emulator.getConfig().getInt("camera.enabled", 1) == 0) {
                LOGGER.info("Camera disabled by configuration.");
            } else {
                try {
                    Emulator.cameraRenderManager = new CameraRenderManager();
                } catch (IllegalStateException e) {
                    LOGGER.warn("Camera disabled: {}", e.getMessage());
                }
            }

            Emulator.debugging = Emulator.getConfig().getBoolean("debug.mode");

            if (debugging) {
                ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
                root.setLevel(Level.DEBUG);
                LOGGER.debug("Debugging enabled.");
            }

            Emulator.getPluginManager().fireEvent(new EmulatorLoadedEvent());
            Emulator.isReady = true;
            Emulator.timeStarted = getIntUnixTimestamp();

            long usedHeapMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
            long maxHeapMb = runtime.maxMemory() / (1024 * 1024);
            LOGGER.info("Habbo Daybreak -> Loaded! ({} MS, {} worker threads, heap {}/{} MB)",
                    (System.nanoTime() - startTime) / 1_000_000,
                    Emulator.getConfig().getInt("runtime.threads"), usedHeapMb, maxHeapMb);

            Emulator.getThreading().run(() -> {
            }, 1500);

            // Check if console mode is true or false, default is true
            if (Emulator.getConfig().getBoolean("console.mode", true)) {

                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

                while (!isShuttingDown && isReady) {
                    try {
                        String line = reader.readLine();

                        if (line != null) {
                            ConsoleCommand.handle(line);
                        }
                        System.out.println("Waiting for command: ");
                    } catch (Exception e) {
                        if (!(e instanceof IOException && e.getMessage().equals("Bad file descriptor"))) {
                            LOGGER.error("Error while reading command", e);
                        }
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void printLogo() {
        final int rows = LOGO.length;
        final int width = LOGO[0].length();

        // No real console (redirected to file / piped) or NO_COLOR set -> plain, no ANSI garbage.
        boolean fancy = System.console() != null && System.getenv("NO_COLOR") == null;

        System.out.println();

        if (!fancy) {
            for (String row : LOGO) {
                System.out.println(row);
            }
            System.out.println("      " + TAGLINE);
            System.out.println();
            return;
        }

        // Sunrise sweep: reveal the word column-by-column, a bright dawn edge leading the way.
        System.out.print(logoFrame(0));
        for (int reveal = 1; reveal <= width; reveal++) {
            System.out.print("[" + rows + "F");
            System.out.print(logoFrame(reveal));
            System.out.flush();
            sleep(14);
        }

        // Glint: a single bright highlight slides across the finished word.
        for (int glint = -3; glint <= width + 3; glint += 2) {
            System.out.print("[" + rows + "F");
            System.out.print(logoFrame(width, glint));
            System.out.flush();
            sleep(10);
        }
        System.out.print("[" + rows + "F");
        System.out.print(logoFrame(width, Integer.MIN_VALUE));

        // Tagline fades up from ember to gold.
        String tag = "      " + TAGLINE;
        for (int step = 0; step <= 6; step++) {
            int r = 120 + step * 22;
            int g = 70 + step * 26;
            int b = 40 + step * 18;
            System.out.print("\r[38;2;" + clamp(r) + ";" + clamp(g) + ";" + clamp(b) + "m" + tag + "[0m");
            System.out.flush();
            sleep(28);
        }
        System.out.println();
        System.out.println();
    }

    private static void printBanner() {
        boolean fancy = System.console() != null && System.getenv("NO_COLOR") == null;
        String shortBuild = (build != null && build.length() >= 8) ? build.substring(0, 8) : build;
        String tail = "   build " + shortBuild;

        if (!fancy) {
            System.out.println("   " + version + tail);
            System.out.println("   open-source fork of Arcturus by TheGeneral");
            System.out.println("   github.com/habbo-cc/Habbo-Daybreak");
            System.out.println();
            return;
        }

        String dim = "[38;2;120;124;134m";
        String rst = "[0m";
        int n = version.length();

        // Version name catches the same sunrise, typed out to echo the logo sweep.
        for (int reveal = 1; reveal <= n; reveal++) {
            System.out.print("\r   " + tintText(version, reveal, Integer.MIN_VALUE));
            System.out.flush();
            sleep(16);
        }
        // Single glint slides across the finished name.
        for (int glint = -2; glint <= n + 2; glint += 2) {
            System.out.print("\r   " + tintText(version, n, glint));
            System.out.flush();
            sleep(12);
        }
        System.out.println("\r   " + tintText(version, n, Integer.MIN_VALUE) + dim + tail + rst);
        System.out.println("   " + dim + "open-source fork of Arcturus by TheGeneral" + rst);
        System.out.println("   " + dim + "github.com/habbo-cc/Habbo-Daybreak" + rst);
        System.out.println();
    }

    // Same dawn gradient as the logo, mapped across a single line of text.
    private static String tintText(String s, int reveal, int glint) {
        int n = s.length();
        StringBuilder sb = new StringBuilder(n * 12);
        for (int i = 0; i < n; i++) {
            char ch = s.charAt(i);
            if (i >= reveal || ch == ' ') {
                sb.append(' ');
                continue;
            }
            int[] c = gradient(i, n);
            if (reveal - i <= 2) {
                c = new int[]{clamp(c[0] + 70), clamp(c[1] + 70), clamp(c[2] + 70)};
            } else if (glint != Integer.MIN_VALUE && Math.abs(i - glint) <= 1) {
                c = new int[]{clamp(c[0] + 90), clamp(c[1] + 90), clamp(c[2] + 90)};
            }
            sb.append("[38;2;").append(c[0]).append(';').append(c[1]).append(';').append(c[2]).append('m').append(ch);
        }
        sb.append("[0m");
        return sb.toString();
    }

    private static String logoFrame(int reveal) {
        return logoFrame(reveal, Integer.MIN_VALUE);
    }

    private static String logoFrame(int reveal, int glint) {
        final int width = LOGO[0].length();
        StringBuilder sb = new StringBuilder(width * LOGO.length * 12);
        for (String row : LOGO) {
            for (int x = 0; x < row.length(); x++) {
                char ch = row.charAt(x);
                if (x >= reveal || ch == ' ') {
                    sb.append(' ');
                    continue;
                }
                int[] c = gradient(x, width);
                // Leading dawn edge glows white-hot; the passing glint sparks too.
                if (reveal - x <= 2) {
                    c = new int[]{clamp(c[0] + 70), clamp(c[1] + 70), clamp(c[2] + 70)};
                } else if (glint != Integer.MIN_VALUE && Math.abs(x - glint) <= 1) {
                    c = new int[]{clamp(c[0] + 90), clamp(c[1] + 90), clamp(c[2] + 90)};
                }
                sb.append("[38;2;").append(c[0]).append(';').append(c[1]).append(';').append(c[2]).append('m').append(ch);
            }
            sb.append("[0m\n");
        }
        return sb.toString();
    }

    private static int[] gradient(int x, int width) {
        double t = width <= 1 ? 0 : (double) x / (width - 1);
        double seg = t * (DAWN.length - 1);
        int i = (int) Math.floor(seg);
        if (i >= DAWN.length - 1) {
            i = DAWN.length - 2;
        }
        double f = seg - i;
        int[] a = DAWN[i];
        int[] b = DAWN[i + 1];
        return new int[]{
                (int) Math.round(a[0] + (b[0] - a[0]) * f),
                (int) Math.round(a[1] + (b[1] - a[1]) * f),
                (int) Math.round(a[2] + (b[2] - a[2]) * f)
        };
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : Math.min(v, 255);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void setBuild() {
        if (Emulator.class.getProtectionDomain().getCodeSource() == null) {
            build = "UNKNOWN";
            return;
        }

        StringBuilder sb = new StringBuilder();
        try {
            String filepath = new File(Emulator.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getAbsolutePath();
            MessageDigest md = MessageDigest.getInstance("MD5");// MD5
            try (FileInputStream fis = new FileInputStream(filepath)) {
                byte[] dataBytes = new byte[1024];
                int nread = 0;
                while ((nread = fis.read(dataBytes)) != -1)
                    md.update(dataBytes, 0, nread);
                byte[] mdbytes = md.digest();
                for (int i = 0; i < mdbytes.length; i++)
                    sb.append(Integer.toString((mdbytes[i] & 0xff) + 0x100, 16).substring(1));
            }
        } catch (Exception e) {
            build = "UNKNOWN";
            return;
        }

        build = sb.toString();
    }

    private static void dispose() {
        Emulator.getThreading().setCanAdd(false);
        Emulator.isShuttingDown = true;
        Emulator.isReady = false;

        LOGGER.info("Stopping Habbo Daybreak {}", version);

        try {
            if (Emulator.getPluginManager() != null)
                Emulator.getPluginManager().fireEvent(new EmulatorStartShutdownEvent());
        } catch (Exception e) {
        }

        try {
            if (Emulator.rconServer != null)
                Emulator.rconServer.stop();
        } catch (Exception e) {
        }

        try {
            if (Emulator.gameEnvironment != null)
                Emulator.gameEnvironment.dispose();
        } catch (Exception e) {
        }

        try {
            if (Emulator.getPluginManager() != null)
                Emulator.getPluginManager().fireEvent(new EmulatorStoppedEvent());
        } catch (Exception e) {
        }

        try {
            if (Emulator.pluginManager != null)
                Emulator.pluginManager.dispose();
        } catch (Exception e) {
        }

        try {
            if (Emulator.config != null) {
                Emulator.config.saveToDatabase();
            }
        } catch (Exception e) {
        }

        try {
            if (Emulator.gameServer != null)
                Emulator.gameServer.stop();
        } catch (Exception e) {
        }

        LOGGER.info("Stopped Habbo Daybreak {}", version);

        if (Emulator.database != null) {
            Emulator.getDatabase().dispose();
        }
        Emulator.stopped = true;

        // if (osName.startsWith("Windows") && (!classPath.contains("idea_rt.jar"))) {
        //     AnsiConsole.systemUninstall();
        // }
        try {
            if (Emulator.threading != null)

                Emulator.threading.shutDown();
        } catch (Exception e) {
        }
    }

    public static ConfigurationManager getConfig() {
        return config;
    }

    public static CryptoConfig getCrypto() {
        return crypto;
    }

    public static TextsManager getTexts() {
        return texts;
    }

    public static Database getDatabase() {
        return database;
    }

    public static DatabaseLogger getDatabaseLogger() {
        return databaseLogger;
    }

    public static Runtime getRuntime() {
        return runtime;
    }

    public static GameServer getGameServer() {
        return gameServer;
    }

    public static RCONServer getRconServer() {
        return rconServer;
    }

    /**
     * @deprecated Do not use. Please use LoggerFactory.getLogger(YourClass.class) to log.
     */
    @Deprecated
    public static Logging getLogging() {
        return logging;
    }

    public static ThreadPooling getThreading() {
        return threading;
    }

    public static GameEnvironment getGameEnvironment() {
        return gameEnvironment;
    }

    public static PluginManager getPluginManager() {
        return pluginManager;
    }

    public static Random getRandom() {
        return ThreadLocalRandom.current();
    }

    public static BadgeImager getBadgeImager() {
        return badgeImager;
    }

    public static CameraRenderManager getCameraRenderManager() {
        return cameraRenderManager;
    }

    public static int getTimeStarted() {
        return timeStarted;
    }

    public static int getOnlineTime() {
        return getIntUnixTimestamp() - timeStarted;
    }

    public static void prepareShutdown() {
        System.exit(0);
    }

    public static int timeStringToSeconds(String timeString) {
        int totalSeconds = 0;

        Matcher m = Pattern.compile("(([0-9]*) (second|minute|hour|day|week|month|year))").matcher(timeString);
        Map<String,Integer> map = new HashMap<String,Integer>() {
            {
                put("second", 1);
                put("minute", 60);
                put("hour", 3600);
                put("day", 86400);
                put("week", 604800);
                put("month", 2628000);
                put("year", 31536000);
            }
        };

        while (m.find()) {
            try {
                int amount = Integer.parseInt(m.group(2));
                String what = m.group(3);
                totalSeconds += amount * map.get(what);
            }
            catch (Exception ignored) { }
        }

        return totalSeconds;
    }

    public static Date modifyDate(Date date, String timeString) {
        Calendar c = Calendar.getInstance();
        c.setTime(date);

        Matcher m = Pattern.compile("(([0-9]*) (second|minute|hour|day|week|month|year))").matcher(timeString);
        Map<String, Integer> map = new HashMap<String, Integer>() {
            {
                put("second", Calendar.SECOND);
                put("minute", Calendar.MINUTE);
                put("hour", Calendar.HOUR);
                put("day", Calendar.DAY_OF_MONTH);
                put("week", Calendar.WEEK_OF_MONTH);
                put("month", Calendar.MONTH);
                put("year", Calendar.YEAR);
            }
        };

        while (m.find()) {
            try {
                int amount = Integer.parseInt(m.group(2));
                String what = m.group(3);
                c.add(map.get(what), amount);
            }
            catch (Exception ignored) { }
        }

        return c.getTime();
    }

    private static String dateToUnixTimestamp(Date date) {
        String res = "";
        Date aux = stringToDate("1970-01-01 00:00:00");
        Timestamp aux1 = dateToTimeStamp(aux);
        Timestamp aux2 = dateToTimeStamp(date);
        long difference = aux2.getTime() - aux1.getTime();
        long seconds = difference / 1000L;
        return res + seconds;
    }

    public static Date stringToDate(String date) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date res = null;
        try {
            res = format.parse(date);
        } catch (Exception e) {
            LOGGER.error("Error parsing date", e);
        }
        return res;
    }

    public static Timestamp dateToTimeStamp(Date date) {
        return new Timestamp(date.getTime());
    }

    public static Date getDate() {
        return new Date(System.currentTimeMillis());
    }

    public static String getUnixTimestamp() {
        return dateToUnixTimestamp(getDate());
    }

    public static int getIntUnixTimestamp() {
        return (int) (System.currentTimeMillis() / 1000);
    }

    public static boolean isNumeric(String string)
            throws IllegalArgumentException {
        boolean isnumeric = false;
        if ((string != null) && (!string.equals(""))) {
            isnumeric = true;
            char[] chars = string.toCharArray();
            for (char aChar : chars) {
                isnumeric = Character.isDigit(aChar);
                if (!isnumeric) {
                    break;
                }
            }
        }
        return isnumeric;
    }

    public int getUserCount() {
        return gameEnvironment.getHabboManager().getOnlineCount();
    }

    public int getRoomCount() {
        return gameEnvironment.getRoomManager().getActiveRooms().size();
    }
}
