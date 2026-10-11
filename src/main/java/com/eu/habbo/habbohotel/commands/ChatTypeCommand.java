package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.messages.outgoing.users.AccountPreferencesMessageComposer;

public class ChatTypeCommand extends Command {
    public ChatTypeCommand() {
        super("cmd_chatcolor", Emulator.getTexts().getValue("commands.keys.cmd_chatcolor").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {

        if (params.length >= 2) {
            int chatColor;
            try {
                chatColor = Integer.parseInt(params[1]);
            } catch (Exception e) {
                gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_chatcolor.numbers"), RoomChatMessageBubbles.ALERT);
                return true;
            }

            // Resolve by real bubble id. Ids are sparse (0-45, 120-133, 200-252, ...), so an unknown
            // or out-of-range id resolves to NORMAL via getBubble's fallback — reject any non-zero id
            // that does not map to a real style instead of inferring validity from values().length.
            RoomChatMessageBubbles bubble = RoomChatMessageBubbles.getBubble(chatColor);
            if (chatColor != RoomChatMessageBubbles.NORMAL.getType() && bubble == RoomChatMessageBubbles.NORMAL) {
                gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_chatcolor.numbers"), RoomChatMessageBubbles.ALERT);
                return true;
            }

            if (!gameClient.getHabbo().hasPermission(Permission.ACC_ANYCHATCOLOR)) {
                for (String s : Emulator.getConfig().getValue("commands.cmd_chatcolor.banned_numbers").split(";")) {
                    if (Integer.parseInt(s) == chatColor) {
                        gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_chatcolor.banned"), RoomChatMessageBubbles.ALERT);
                        return true;
                    }
                }

                // When commands.cmd_chatcolor.require_ownership = 1, catalog chat bubbles must be owned
                // (a users_chat_styles row); acc_allchatbubbles lets staff bypass that. Set the config
                // to 0 to restore the legacy behaviour where anyone can :chat any bubble for free.
                if (Emulator.getConfig().getBoolean("commands.cmd_chatcolor.require_ownership", true)
                        && !gameClient.getHabbo().hasPermission(Permission.ACC_ALLCHATBUBBLES)
                        && Emulator.getGameEnvironment().getCatalogManager().isPurchasableChatStyle(chatColor)
                        && !Emulator.getGameEnvironment().getCatalogManager().habboOwnsChatStyle(gameClient.getHabbo(), chatColor)) {
                    gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_chatcolor.not_owned"), RoomChatMessageBubbles.ALERT);
                    return true;
                }
            }

            gameClient.getHabbo().getHabboStats().chatColor = bubble;
            gameClient.getHabbo().getHabboStats().run();
            gameClient.sendResponse(new AccountPreferencesMessageComposer(gameClient.getHabbo()));
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.succes.cmd_chatcolor.set").replace("%chat%", bubble.name().replace("_", " ").toLowerCase()), RoomChatMessageBubbles.ALERT);
            return true;
        } else {
            gameClient.getHabbo().getHabboStats().chatColor = RoomChatMessageBubbles.NORMAL;
            gameClient.getHabbo().getHabboStats().run();
            gameClient.sendResponse(new AccountPreferencesMessageComposer(gameClient.getHabbo()));
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.succes.cmd_chatcolor.reset"), RoomChatMessageBubbles.ALERT);
            return true;
        }
    }
}
