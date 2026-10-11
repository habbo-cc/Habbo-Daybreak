-- :bus open / :bus close — staff command to open/close the Infobus (The Park) doors.
-- Idempotent. Grants the command to the same ranks that already hold :updatenavigator.

-- Permission column (loaded dynamically by Rank from any cmd_* column).
ALTER TABLE `permissions`
    ADD COLUMN IF NOT EXISTS `cmd_bus` ENUM('0','1') NOT NULL DEFAULT '0' AFTER `cmd_update_navigator`;

-- Grant to the same ranks that can already run :updatenavigator (staff).
UPDATE `permissions` SET `cmd_bus` = `cmd_update_navigator`;

-- Command texts (triggers, help, success + error messages, and the closed-door notice).
DELETE FROM `emulator_texts` WHERE `key` IN (
    'commands.keys.cmd_bus',
    'commands.description.cmd_bus',
    'commands.succes.cmd_bus.opened',
    'commands.succes.cmd_bus.closed',
    'commands.error.cmd_bus',
    'infobus.doors.closed'
);

INSERT INTO `emulator_texts` (`key`, `value`) VALUES
    ('commands.keys.cmd_bus', 'bus'),
    ('commands.description.cmd_bus', ':bus <open/close>'),
    ('commands.succes.cmd_bus.opened', 'The Infobus doors are now open.'),
    ('commands.succes.cmd_bus.closed', 'The Infobus doors are now closed.'),
    ('commands.error.cmd_bus', 'Usage: :bus <open/close>'),
    ('infobus.doors.closed', 'The Infobus doors are currently closed.');
