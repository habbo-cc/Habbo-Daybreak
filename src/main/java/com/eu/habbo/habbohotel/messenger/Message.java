package com.eu.habbo.habbohotel.messenger;

import com.eu.habbo.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

public class Message implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Message.class);
    public static final int TYPE_TEXT = 0;
    public static final int TYPE_HABBICON = 1;

    private final int fromId;
    private final int toId;
    private final int timestamp;
    private final int messageType;
    private final int habbiconId;
    private final int confirmationId;
    private final String messageId;
    private String message;

    public Message(int fromId, int toId, String message) {
        this(fromId, toId, message, TYPE_TEXT, 0, 0);
    }

    public Message(int fromId, int toId, int habbiconId, int confirmationId) {
        this(fromId, toId, "", TYPE_HABBICON, habbiconId, confirmationId);
    }

    private Message(int fromId, int toId, String message, int messageType, int habbiconId, int confirmationId) {
        this.fromId = fromId;
        this.toId = toId;
        this.message = message;
        this.messageType = messageType;
        this.habbiconId = habbiconId;
        this.confirmationId = confirmationId;

        this.timestamp = Emulator.getIntUnixTimestamp();
        this.messageId = UUID.randomUUID().toString();
    }

    @Override
    public void run() {
        //TODO Turn into scheduler
        if (Messenger.SAVE_PRIVATE_CHATS) {
            if (this.messageType == TYPE_HABBICON && this.saveHabbiconMessage()) {
                return;
            }

            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("INSERT INTO chatlogs_private (user_from_id, user_to_id, message, timestamp) VALUES (?, ?, ?, ?)")) {
                statement.setInt(1, this.fromId);
                statement.setInt(2, this.toId);
                statement.setString(3, this.message);
                statement.setInt(4, this.timestamp);
                statement.execute();
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }
    }

    private boolean saveHabbiconMessage() {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("INSERT INTO chatlogs_private (user_from_id, user_to_id, message, timestamp, message_type, habbicon_id, confirmation_id, message_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setInt(1, this.fromId);
            statement.setInt(2, this.toId);
            statement.setString(3, "");
            statement.setInt(4, this.timestamp);
            statement.setInt(5, this.messageType);
            statement.setInt(6, this.habbiconId);
            statement.setInt(7, this.confirmationId);
            statement.setString(8, this.messageId);
            statement.execute();
            return true;
        } catch (SQLException e) {
            LOGGER.error("Failed to save Habbicon private chat metadata. Has sqlupdates/habbicon_messenger_messages.sql been applied?", e);
        }

        return false;
    }

    public int getToId() {
        return this.toId;
    }

    public int getFromId() {
        return this.fromId;
    }

    public String getMessage() {
        return this.message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getTimestamp() {
        return this.timestamp;
    }

    public int getMessageType() {
        return this.messageType;
    }

    public int getHabbiconId() {
        return this.habbiconId;
    }

    public int getConfirmationId() {
        return this.confirmationId;
    }

    public String getMessageId() {
        return this.messageId;
    }
}
