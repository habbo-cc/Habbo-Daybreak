package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonUsernamePlaceholder;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonFurniNamePlaceholder;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonVariablePlaceholder;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonGlobalPlaceholder;
import com.eu.habbo.habbohotel.wired.WiredAddonType;

/** Applies bounded stack-scoped placeholder add-ons to text-producing effects. */
public final class WiredTextTransform {
    public static final int MAX_INPUT_LENGTH = 32000;
    public static final int MAX_OUTPUT_LENGTH = 32000;
    public static final int MAX_TRANSFORMS = 16;
    private WiredTextTransform() { }

    /** Identity until a stack add-on supplies an explicit transform; never affects legacy stacks. */
    public static String apply(WiredContext context, String text) {
        if (text == null || context == null || context.stack() == null) return text;
        WiredAddonUsernamePlaceholder addon = context.stack().addon(WiredAddonType.USERNAME_PLACEHOLDER) instanceof WiredAddonUsernamePlaceholder value ? value : null;
        if (addon != null) {
            String users = context.targets().users().stream().sorted(java.util.Comparator.comparingInt(com.eu.habbo.habbohotel.rooms.RoomUnit::getId))
                    .map(unit -> context.room().getHabbo(unit)).filter(java.util.Objects::nonNull).map(habbo -> habbo.getHabboInfo().getUsername())
                    .limit(addon.multiple() ? MAX_TRANSFORMS : 1).collect(java.util.stream.Collectors.joining(addon.delimiter()));
            if (!users.isEmpty()) {
                String output = text.replace(addon.placeholder(), users);
                text = output.length() <= MAX_OUTPUT_LENGTH ? output : output.substring(0, MAX_OUTPUT_LENGTH);
            }
        }
        WiredAddonFurniNamePlaceholder furni = context.stack().addon(WiredAddonType.FURNI_NAME_PLACEHOLDER) instanceof WiredAddonFurniNamePlaceholder value ? value : null;
        if (furni != null) {
            String names = context.targets().items().stream().sorted(java.util.Comparator.comparingInt(com.eu.habbo.habbohotel.users.HabboItem::getId))
                    .map(item -> item.getBaseItem() == null ? "" : item.getBaseItem().getName()).filter(name -> !name.isEmpty())
                    .limit(furni.multiple() ? MAX_TRANSFORMS : 1).collect(java.util.stream.Collectors.joining(furni.delimiter()));
            if (!names.isEmpty()) {
                String output = text.replace(furni.placeholder(), names);
                text = output.length() <= MAX_OUTPUT_LENGTH ? output : output.substring(0, MAX_OUTPUT_LENGTH);
            }
        }
        WiredAddonVariablePlaceholder variable = context.stack().addon(WiredAddonType.VARIABLE_PLACEHOLDER) instanceof WiredAddonVariablePlaceholder value ? value : null;
        if (variable != null) {
            String output = variable.apply(context, text);
            text = output.length() <= MAX_OUTPUT_LENGTH ? output : output.substring(0, MAX_OUTPUT_LENGTH);
        }
        WiredAddonGlobalPlaceholder global = context.stack().addon(WiredAddonType.GLOBAL_PLACEHOLDER) instanceof WiredAddonGlobalPlaceholder value ? value : null;
        if (global != null) {
            String output = global.apply(context, text);
            text = output.length() <= MAX_OUTPUT_LENGTH ? output : output.substring(0, MAX_OUTPUT_LENGTH);
        }
        return text;
    }
}
