-- Chat bubble ownership for the :chat command.
--
-- All chat bubbles are sold in the catalog (only the white/original bubble is free in the
-- client picker), so the :chat command now rejects catalog bubbles the user does not own
-- (no users_chat_styles row) with a dedicated message. The new acc_allchatbubbles permission
-- lets staff use any catalog bubble via :chat without having purchased it.

-- 1. New permission column (auto-loaded by Rank.load via column metadata; enum('0','1') like the others).
ALTER TABLE `permissions`
    ADD COLUMN IF NOT EXISTS `acc_allchatbubbles` ENUM('0','1') NOT NULL DEFAULT '0' AFTER `acc_anychatcolor`;

-- Grant the bypass to ranks that already have acc_anychatcolor (the chat-bubble staff ranks).
-- Adjust to taste for your rank setup.
UPDATE `permissions` SET `acc_allchatbubbles` = '1' WHERE `acc_anychatcolor` = '1';

-- 2. Dedicated "not owned" error text used by ChatTypeCommand.
INSERT INTO `emulator_texts` (`key`, `value`)
VALUES ('commands.error.cmd_chatcolor.not_owned', 'You need to purchase this bubble first before using it!')
ON DUPLICATE KEY UPDATE `value` = VALUES(`value`);

-- 3. Toggle for the ownership rule. 1 = enforce ownership (the new rules + acc_allchatbubbles bypass),
--    0 = legacy behaviour where anyone can :chat any bubble for free. Keeps an existing value on re-run.
INSERT INTO `emulator_settings` (`key`, `value`)
VALUES ('commands.cmd_chatcolor.require_ownership', '1')
ON DUPLICATE KEY UPDATE `value` = `value`;
