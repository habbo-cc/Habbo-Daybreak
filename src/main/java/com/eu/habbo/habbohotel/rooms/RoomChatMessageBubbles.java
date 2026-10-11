package com.eu.habbo.habbohotel.rooms;

import java.util.HashMap;
import java.util.Map;

public class RoomChatMessageBubbles {
    private static final Map<Integer, RoomChatMessageBubbles> BUBBLES = new HashMap<>();

    public static final RoomChatMessageBubbles NORMAL = new RoomChatMessageBubbles(0, "NORMAL", "", true, true);
    public static final RoomChatMessageBubbles ALERT = new RoomChatMessageBubbles(1, "ALERT", "", true, true);
    public static final RoomChatMessageBubbles BOT = new RoomChatMessageBubbles(2, "BOT", "", true, true);
    public static final RoomChatMessageBubbles RED = new RoomChatMessageBubbles(3, "RED", "", true, true);
    public static final RoomChatMessageBubbles BLUE = new RoomChatMessageBubbles(4, "BLUE", "", true, true);
    public static final RoomChatMessageBubbles YELLOW = new RoomChatMessageBubbles(5, "YELLOW", "", true, true);
    public static final RoomChatMessageBubbles GREEN = new RoomChatMessageBubbles(6, "GREEN", "", true, true);
    public static final RoomChatMessageBubbles BLACK = new RoomChatMessageBubbles(7, "BLACK", "", true, true);
    public static final RoomChatMessageBubbles FORTUNE_TELLER = new RoomChatMessageBubbles(8, "FORTUNE_TELLER", "", false, false);
    public static final RoomChatMessageBubbles ZOMBIE_ARM = new RoomChatMessageBubbles(9, "ZOMBIE_ARM", "", true, false);
    public static final RoomChatMessageBubbles SKELETON = new RoomChatMessageBubbles(10, "SKELETON", "", true, false);
    public static final RoomChatMessageBubbles LIGHT_BLUE = new RoomChatMessageBubbles(11, "LIGHT_BLUE", "", true, true);
    public static final RoomChatMessageBubbles PINK = new RoomChatMessageBubbles(12, "PINK", "", true, true);
    public static final RoomChatMessageBubbles PURPLE = new RoomChatMessageBubbles(13, "PURPLE", "", true, true);
    public static final RoomChatMessageBubbles DARK_YELLOW = new RoomChatMessageBubbles(14, "DARK_YELLOW", "", true, true);
    public static final RoomChatMessageBubbles DARK_BLUE = new RoomChatMessageBubbles(15, "DARK_BLUE", "", true, true);
    public static final RoomChatMessageBubbles HEARTS = new RoomChatMessageBubbles(16, "HEARTS", "", true, true);
    public static final RoomChatMessageBubbles ROSES = new RoomChatMessageBubbles(17, "ROSES", "", true, true);
    public static final RoomChatMessageBubbles UNUSED = new RoomChatMessageBubbles(18, "UNUSED", "", true, true);
    public static final RoomChatMessageBubbles PIG = new RoomChatMessageBubbles(19, "PIG", "", true, true);
    public static final RoomChatMessageBubbles DOG = new RoomChatMessageBubbles(20, "DOG", "", true, true);
    public static final RoomChatMessageBubbles BLAZE_IT = new RoomChatMessageBubbles(21, "BLAZE_IT", "", true, true);
    public static final RoomChatMessageBubbles DRAGON = new RoomChatMessageBubbles(22, "DRAGON", "", true, true);
    public static final RoomChatMessageBubbles STAFF = new RoomChatMessageBubbles(23, "STAFF", "", false, true);
    public static final RoomChatMessageBubbles BATS = new RoomChatMessageBubbles(24, "BATS", "", true, false);
    public static final RoomChatMessageBubbles MESSENGER = new RoomChatMessageBubbles(25, "MESSENGER", "", true, false);
    public static final RoomChatMessageBubbles STEAMPUNK = new RoomChatMessageBubbles(26, "STEAMPUNK", "", true, false);
    public static final RoomChatMessageBubbles THUNDER = new RoomChatMessageBubbles(27, "THUNDER", "", true, true);
    public static final RoomChatMessageBubbles PARROT = new RoomChatMessageBubbles(28, "PARROT", "", false, false);
    public static final RoomChatMessageBubbles PIRATE = new RoomChatMessageBubbles(29, "PIRATE", "", false, false);
    public static final RoomChatMessageBubbles BOT_GUIDE = new RoomChatMessageBubbles(30, "BOT_GUIDE", "", true, true);
    public static final RoomChatMessageBubbles BOT_RENTABLE = new RoomChatMessageBubbles(31, "BOT_RENTABLE", "", true, true);
    public static final RoomChatMessageBubbles SCARY_THING = new RoomChatMessageBubbles(32, "SCARY_THING", "", true, false);
    public static final RoomChatMessageBubbles FRANK = new RoomChatMessageBubbles(33, "FRANK", "", true, false);
    public static final RoomChatMessageBubbles WIRED = new RoomChatMessageBubbles(34, "WIRED", "", false, true);
    public static final RoomChatMessageBubbles GOAT = new RoomChatMessageBubbles(35, "GOAT", "", true, false);
    public static final RoomChatMessageBubbles SANTA = new RoomChatMessageBubbles(36, "SANTA", "", true, false);
    public static final RoomChatMessageBubbles AMBASSADOR = new RoomChatMessageBubbles(37, "AMBASSADOR", "acc_ambassador", false, true);
    public static final RoomChatMessageBubbles RADIO = new RoomChatMessageBubbles(38, "RADIO", "", true, false);
    public static final RoomChatMessageBubbles UNKNOWN_39 = new RoomChatMessageBubbles(39, "UNKNOWN_39", "", true, false);
    public static final RoomChatMessageBubbles UNKNOWN_40 = new RoomChatMessageBubbles(40, "UNKNOWN_40", "", true, false);
    public static final RoomChatMessageBubbles UNKNOWN_41 = new RoomChatMessageBubbles(41, "UNKNOWN_41", "", true, false);
    public static final RoomChatMessageBubbles UNKNOWN_42 = new RoomChatMessageBubbles(42, "UNKNOWN_42", "", true, false);
    public static final RoomChatMessageBubbles UNKNOWN_43 = new RoomChatMessageBubbles(43, "UNKNOWN_43", "", true, false);
    public static final RoomChatMessageBubbles UNKNOWN_44 = new RoomChatMessageBubbles(44, "UNKNOWN_44", "", true, false);
    public static final RoomChatMessageBubbles UNKNOWN_45 = new RoomChatMessageBubbles(45, "UNKNOWN_45", "", true, false);

    // Modern purchasable/event styles from the May 2026 client chatstyles XML
    // (120-133, 1000-1026, 10000). Without these, getBubble falls back to NORMAL
    // and selecting them in the style picker silently does nothing.
    public static final RoomChatMessageBubbles STYLE_120 = new RoomChatMessageBubbles(120, "STYLE_120", "", true, false);
    public static final RoomChatMessageBubbles STYLE_121 = new RoomChatMessageBubbles(121, "STYLE_121", "", true, false);
    public static final RoomChatMessageBubbles STYLE_130 = new RoomChatMessageBubbles(130, "STYLE_130", "", true, false);
    public static final RoomChatMessageBubbles STYLE_131 = new RoomChatMessageBubbles(131, "STYLE_131", "", true, false);
    public static final RoomChatMessageBubbles STYLE_132 = new RoomChatMessageBubbles(132, "STYLE_132", "", true, false);
    public static final RoomChatMessageBubbles STYLE_133 = new RoomChatMessageBubbles(133, "STYLE_133", "", true, false);
    public static final RoomChatMessageBubbles STYLE_10000 = new RoomChatMessageBubbles(10000, "STYLE_10000", "", true, false);

    // Wired 2.0 Show Message notification styles (May 2026 chatstyles ids).
    public static final RoomChatMessageBubbles NOTIFICATION_RED = new RoomChatMessageBubbles(200, "NOTIFICATION_RED", "", false, true);
    public static final RoomChatMessageBubbles NOTIFICATION_GREEN = new RoomChatMessageBubbles(201, "NOTIFICATION_GREEN", "", false, true);
    public static final RoomChatMessageBubbles NOTIFICATION_BLUE = new RoomChatMessageBubbles(202, "NOTIFICATION_BLUE", "", false, true);
    public static final RoomChatMessageBubbles NOTIFICATION_ALERT = new RoomChatMessageBubbles(210, "NOTIFICATION_ALERT", "", false, true);
    public static final RoomChatMessageBubbles NOTIFICATION_INFO = new RoomChatMessageBubbles(211, "NOTIFICATION_INFO", "", false, true);
    public static final RoomChatMessageBubbles NOTIFICATION_WARNING = new RoomChatMessageBubbles(212, "NOTIFICATION_WARNING", "", false, true);
    public static final RoomChatMessageBubbles NOTIFICATION_WRONG = new RoomChatMessageBubbles(220, "NOTIFICATION_WRONG", "", false, true);
    public static final RoomChatMessageBubbles NOTIFICATION_WRONG_CIRCLE = new RoomChatMessageBubbles(221, "NOTIFICATION_WRONG_CIRCLE", "", false, true);
    public static final RoomChatMessageBubbles NOTIFICATION_CORRECT = new RoomChatMessageBubbles(222, "NOTIFICATION_CORRECT", "", false, true);
    public static final RoomChatMessageBubbles NOTIFICATION_CORRECT_CIRCLE = new RoomChatMessageBubbles(223, "NOTIFICATION_CORRECT_CIRCLE", "", false, true);
    public static final RoomChatMessageBubbles NOTIFICATION_QUESTION_MARK = new RoomChatMessageBubbles(224, "NOTIFICATION_QUESTION_MARK", "", false, true);
    public static final RoomChatMessageBubbles NOTIFICATION_QUESTION_MARK_CIRCLE = new RoomChatMessageBubbles(225, "NOTIFICATION_QUESTION_MARK_CIRCLE", "", false, true);
    public static final RoomChatMessageBubbles NOTIFICATION_ARROW_UP = new RoomChatMessageBubbles(226, "NOTIFICATION_ARROW_UP", "", false, true);
    public static final RoomChatMessageBubbles NOTIFICATION_ARROW_UP_CIRCLE = new RoomChatMessageBubbles(227, "NOTIFICATION_ARROW_UP_CIRCLE", "", false, true);
    public static final RoomChatMessageBubbles NOTIFICATION_ARROW_DOWN = new RoomChatMessageBubbles(228, "NOTIFICATION_ARROW_DOWN", "", false, true);
    public static final RoomChatMessageBubbles NOTIFICATION_ARROW_DOWN_CIRCLE = new RoomChatMessageBubbles(229, "NOTIFICATION_ARROW_DOWN_CIRCLE", "", false, true);
    public static final RoomChatMessageBubbles NOTIFICATION_SKULL = new RoomChatMessageBubbles(250, "NOTIFICATION_SKULL", "", false, true);
    public static final RoomChatMessageBubbles NOTIFICATION_SKULL_2 = new RoomChatMessageBubbles(251, "NOTIFICATION_SKULL_2", "", false, true);
    public static final RoomChatMessageBubbles NOTIFICATION_MAGNIFIER = new RoomChatMessageBubbles(252, "NOTIFICATION_MAGNIFIER", "", false, true);

    static {
        registerBubble(NORMAL);
        registerBubble(ALERT);
        registerBubble(BOT);
        registerBubble(RED);
        registerBubble(BLUE);
        registerBubble(YELLOW);
        registerBubble(GREEN);
        registerBubble(BLACK);
        registerBubble(FORTUNE_TELLER);
        registerBubble(ZOMBIE_ARM);
        registerBubble(SKELETON);
        registerBubble(LIGHT_BLUE);
        registerBubble(PINK);
        registerBubble(PURPLE);
        registerBubble(DARK_YELLOW);
        registerBubble(DARK_BLUE);
        registerBubble(HEARTS);
        registerBubble(ROSES);
        registerBubble(UNUSED);
        registerBubble(PIG);
        registerBubble(DOG);
        registerBubble(BLAZE_IT);
        registerBubble(DRAGON);
        registerBubble(STAFF);
        registerBubble(BATS);
        registerBubble(MESSENGER);
        registerBubble(STEAMPUNK);
        registerBubble(THUNDER);
        registerBubble(PARROT);
        registerBubble(PIRATE);
        registerBubble(BOT_GUIDE);
        registerBubble(BOT_RENTABLE);
        registerBubble(SCARY_THING);
        registerBubble(FRANK);
        registerBubble(WIRED);
        registerBubble(GOAT);
        registerBubble(SANTA);
        registerBubble(AMBASSADOR);
        registerBubble(RADIO);
        registerBubble(UNKNOWN_39);
        registerBubble(UNKNOWN_40);
        registerBubble(UNKNOWN_41);
        registerBubble(UNKNOWN_42);
        registerBubble(UNKNOWN_43);
        registerBubble(UNKNOWN_44);
        registerBubble(UNKNOWN_45);
        registerBubble(STYLE_120);
        registerBubble(STYLE_121);
        registerBubble(STYLE_130);
        registerBubble(STYLE_131);
        registerBubble(STYLE_132);
        registerBubble(STYLE_133);
        registerBubble(STYLE_10000);
        for (int id = 1000; id <= 1026; id++) {
            registerBubble(new RoomChatMessageBubbles(id, "STYLE_" + id, "", true, false));
        }
        registerBubble(NOTIFICATION_RED);
        registerBubble(NOTIFICATION_GREEN);
        registerBubble(NOTIFICATION_BLUE);
        registerBubble(NOTIFICATION_ALERT);
        registerBubble(NOTIFICATION_INFO);
        registerBubble(NOTIFICATION_WARNING);
        registerBubble(NOTIFICATION_WRONG);
        registerBubble(NOTIFICATION_WRONG_CIRCLE);
        registerBubble(NOTIFICATION_CORRECT);
        registerBubble(NOTIFICATION_CORRECT_CIRCLE);
        registerBubble(NOTIFICATION_QUESTION_MARK);
        registerBubble(NOTIFICATION_QUESTION_MARK_CIRCLE);
        registerBubble(NOTIFICATION_ARROW_UP);
        registerBubble(NOTIFICATION_ARROW_UP_CIRCLE);
        registerBubble(NOTIFICATION_ARROW_DOWN);
        registerBubble(NOTIFICATION_ARROW_DOWN_CIRCLE);
        registerBubble(NOTIFICATION_SKULL);
        registerBubble(NOTIFICATION_SKULL_2);
        registerBubble(NOTIFICATION_MAGNIFIER);
    }

    private final int type;
    private final String name;
    private final String permission;
    private final boolean overridable;
    private final boolean triggersTalkingFurniture;

    private RoomChatMessageBubbles(int type, String name, String permission, boolean overridable, boolean triggersTalkingFurniture) {
        this.type = type;
        this.name = name;
        this.permission = permission;
        this.overridable = overridable;
        this.triggersTalkingFurniture = triggersTalkingFurniture;
    }

    public static RoomChatMessageBubbles getBubble(int id) {
        return BUBBLES.getOrDefault(id, NORMAL);
    }

    private static void registerBubble(RoomChatMessageBubbles bubble) {
        BUBBLES.put(bubble.getType(), bubble);
    }

    public int getType() {
        return type;
    }

    public String name() {
        return name;
    }

    public String getPermission() {
        return permission;
    }

    public boolean isOverridable() {
        return overridable;
    }

    public boolean triggersTalkingFurniture() {
        return triggersTalkingFurniture;
    }

    public static void addDynamicBubble(int type, String name, String permission, boolean overridable, boolean triggersTalkingFurniture) {
        registerBubble(new RoomChatMessageBubbles(type, name, permission, overridable, triggersTalkingFurniture));
    }

    public static void removeDynamicBubbles() {
        synchronized (BUBBLES) {
            BUBBLES.entrySet().removeIf(entry -> entry.getKey() > 45 && !isWiredNotificationBubble(entry.getKey()));
        }
    }

    private static boolean isWiredNotificationBubble(int type) {
        return (type >= 200 && type <= 202)
                || (type >= 210 && type <= 212)
                || (type >= 220 && type <= 229)
                || (type >= 250 && type <= 252);
    }

    public static RoomChatMessageBubbles[] values() {
        return BUBBLES.values().toArray(new RoomChatMessageBubbles[0]);
    }
}
