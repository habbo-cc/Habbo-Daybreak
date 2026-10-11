package com.eu.habbo.habbohotel.tickets;

import com.eu.habbo.Emulator;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Database transactions for the hotel-wide ticket currency. */
final class HotelTicketRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(HotelTicketRepository.class);

    record PurchaseResult(int status, int recipientUserId, String recipientName, int creditsRemaining,
                          int ticketBalance, int ticketsAdded) {
        static PurchaseResult failed(int status) {
            return new PurchaseResult(status, 0, "", -1, -1, 0);
        }

        boolean success() {
            return this.status == HotelTicketService.PURCHASE_OK;
        }
    }

    PurchaseResult purchase(int buyerUserId, String requestedRecipient, int currencyType,
                            int creditCost, int ticketsAdded) {
        if (buyerUserId <= 0 || currencyType < 0 || creditCost <= 0 || ticketsAdded <= 0) {
            return PurchaseResult.failed(HotelTicketService.PURCHASE_INVALID_BUNDLE);
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                int recipientUserId;
                String recipientName;
                if (requestedRecipient == null || requestedRecipient.isBlank()) {
                    try (PreparedStatement recipient = connection.prepareStatement(
                            "SELECT id, username FROM users WHERE id = ? LIMIT 1 FOR UPDATE")) {
                        recipient.setInt(1, buyerUserId);
                        try (ResultSet rows = recipient.executeQuery()) {
                            if (!rows.next()) {
                                connection.rollback();
                                return PurchaseResult.failed(HotelTicketService.PURCHASE_RECIPIENT_NOT_FOUND);
                            }
                            recipientUserId = rows.getInt("id");
                            recipientName = rows.getString("username");
                        }
                    }
                } else {
                    try (PreparedStatement recipient = connection.prepareStatement(
                            "SELECT id, username FROM users WHERE username = ? LIMIT 1 FOR UPDATE")) {
                        recipient.setString(1, requestedRecipient.trim());
                        try (ResultSet rows = recipient.executeQuery()) {
                            if (!rows.next()) {
                                connection.rollback();
                                return PurchaseResult.failed(HotelTicketService.PURCHASE_RECIPIENT_NOT_FOUND);
                            }
                            recipientUserId = rows.getInt("id");
                            recipientName = rows.getString("username");
                        }
                    }
                }

                try (PreparedStatement debit = connection.prepareStatement(
                        "UPDATE users SET credits = credits - ? WHERE id = ? AND credits >= ? LIMIT 1")) {
                    debit.setInt(1, creditCost);
                    debit.setInt(2, buyerUserId);
                    debit.setInt(3, creditCost);
                    if (debit.executeUpdate() != 1) {
                        connection.rollback();
                        return PurchaseResult.failed(HotelTicketService.PURCHASE_INSUFFICIENT_CREDITS);
                    }
                }

                try (PreparedStatement credit = connection.prepareStatement(
                        "INSERT INTO users_currency (user_id, type, amount) VALUES (?, ?, ?) "
                                + "ON DUPLICATE KEY UPDATE amount = amount + VALUES(amount)")) {
                    credit.setInt(1, recipientUserId);
                    credit.setInt(2, currencyType);
                    credit.setInt(3, ticketsAdded);
                    credit.executeUpdate();
                }

                int creditsRemaining;
                try (PreparedStatement balance = connection.prepareStatement(
                        "SELECT credits FROM users WHERE id = ? LIMIT 1")) {
                    balance.setInt(1, buyerUserId);
                    try (ResultSet rows = balance.executeQuery()) {
                        if (!rows.next()) throw new SQLException("Ticket buyer disappeared during purchase");
                        creditsRemaining = rows.getInt(1);
                    }
                }

                int ticketBalance = readCurrency(connection, recipientUserId, currencyType);
                connection.commit();
                return new PurchaseResult(HotelTicketService.PURCHASE_OK, recipientUserId, recipientName,
                        creditsRemaining, ticketBalance, ticketsAdded);
            } catch (SQLException exception) {
                connection.rollback();
                LOGGER.error("Hotel Tickets -> Purchase failed for user {}.", buyerUserId, exception);
                return PurchaseResult.failed(HotelTicketService.PURCHASE_DATABASE_ERROR);
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException exception) {
            LOGGER.error("Hotel Tickets -> Could not open purchase transaction for user {}.", buyerUserId, exception);
            return PurchaseResult.failed(HotelTicketService.PURCHASE_DATABASE_ERROR);
        }
    }

    private static int readCurrency(Connection connection, int userId, int type) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT amount FROM users_currency WHERE user_id = ? AND type = ? LIMIT 1")) {
            statement.setInt(1, userId);
            statement.setInt(2, type);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt(1) : 0;
            }
        }
    }
}
