-- Staff command to reload Reward Track definition data from the database.
-- Run this after the Reward Track foundation SQL.

ALTER TABLE `permissions`
  ADD COLUMN IF NOT EXISTS `cmd_update_rewardtrack` ENUM('0','1') NOT NULL DEFAULT '0' AFTER `cmd_update_permissions`;

UPDATE `permissions`
SET `cmd_update_rewardtrack` = '1'
WHERE `id` >= 7;

INSERT INTO `emulator_texts` (`key`, `value`) VALUES
  ('commands.keys.cmd_update_rewardtrack', 'update_rewardtrack;update_reward_track;reload_rewardtrack;reload_reward_track'),
  ('commands.description.cmd_update_rewardtrack', ':update_rewardtrack'),
  ('commands.succes.cmd_update_rewardtrack', 'Reward Track definitions reloaded. Loaded %tracks% track(s) and pushed fresh state to %users% online user(s).')
ON DUPLICATE KEY UPDATE `value` = VALUES(`value`);
