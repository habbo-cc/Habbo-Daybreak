-- Wired 2.0: enable the new wired execution engine.
-- Older 4.0.0 upgrades insert wired.engine.enabled = '0'; Wired 2.0 requires it on.
-- Idempotent: inserts the key if missing, otherwise forces it to '1'.

INSERT INTO `emulator_settings` (`key`, `value`)
VALUES ('wired.engine.enabled', '1')
ON DUPLICATE KEY UPDATE `value` = '1';

-- The versioned protocol is live for the server-complete feature families.
-- This is deliberately separate from the room capability mask in code: the
-- latter advertises only compiled July features and excludes Creator Tools.
INSERT INTO `emulator_settings` (`key`, `value`)
VALUES ('wired2.protocol.enabled', '1')
ON DUPLICATE KEY UPDATE `value` = '1';

-- Wired 2.0 execution safety limits are emulator runtime settings, not
-- deployment/bootstrap properties. Keep them database-backed with the rest of
-- the Wired engine settings so they can be managed and reloaded consistently.
-- A migration rerun must not overwrite limits deliberately tuned by an
-- administrator after the initial install.
INSERT INTO `emulator_settings` (`key`, `value`) VALUES
('wired.abuse.protection.enabled', '1'),
('hotel.wired.max_delay', '20'),
('wired.engine.maxTotalStepsPerRun', '1000'),
('wired.engine.maxSignalDepth', '10'),
('wired.engine.maxRemoteDepth', '10'),
('wired.engine.maxTriggerStackDepth', '10'),
('wired.engine.maxFanOut', '1000'),
('wired.engine.maxTargets', '1000')
ON DUPLICATE KEY UPDATE `value` = `value`;
