CREATE TABLE IF NOT EXISTS `room_wired_settings` (
  `room_id` INT NOT NULL,
  `modify_permission_mask` INT NOT NULL DEFAULT 8,
  `read_permission_mask` INT NOT NULL DEFAULT 15,
  `timezone` VARCHAR(64) NOT NULL DEFAULT 'UTC',
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`room_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Arcturus ConfigurationManager.register inserts exactly (`key`, `value`).
-- Some imported fork schemas add a third `hidden` column, which makes that
-- canonical positional insert fail. Normalize upgrades to Arcturus' schema.
ALTER TABLE `emulator_settings`
  DROP COLUMN IF EXISTS `hidden`;

-- Defaults used by the bounded Wired Menu usage meter. Preserve any values
-- already chosen by an emulator administrator. Arcturus emulator_settings has
-- only the `key` and `value` columns; `hidden` belongs to other forks.
INSERT IGNORE INTO `emulator_settings` (`key`, `value`) VALUES
  ('wired.max_usage', '8750'),
  ('wired.usage.window.ms', '1000'),
  ('wired.usage.stack_baseline.interval.ms', '1000'),
  ('wired.delayed.events.max', '500'),
  ('wired.executor.overload.ms', '250'),
  ('hotel.room.wallfurni.max', '2500'),
  ('hotel.room.furni.variable.max', '100'),
  ('hotel.room.user.variable.max', '100'),
  ('hotel.room.global.variable.max', '100');

CREATE TABLE IF NOT EXISTS `users_wired_preferences` (
  `user_id` INT NOT NULL,
  `menu_button` TINYINT(1) NOT NULL DEFAULT 1,
  `inspect_button` TINYINT(1) NOT NULL DEFAULT 1,
  `playtest_mode` TINYINT(1) NOT NULL DEFAULT 0,
  `whisper_disabled` TINYINT(1) NOT NULL DEFAULT 0,
  `all_notifications` TINYINT(1) NOT NULL DEFAULT 0,
  `ui_style` VARCHAR(32) NOT NULL DEFAULT '',
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Room-scoped Wired engine diagnostics. Adapted from Seth's bounded
-- wired_logs persistence, without the Creator Tools feature dependency.
CREATE TABLE IF NOT EXISTS `room_wired_monitor_logs` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `room_id` INT NOT NULL,
  `level` TINYINT UNSIGNED NOT NULL,
  `source` TINYINT UNSIGNED NOT NULL,
  `message` VARCHAR(400) NOT NULL,
  `created_at` BIGINT NOT NULL,
  PRIMARY KEY (`id`),
  KEY `room_wired_monitor_logs_newest` (`room_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- July's Write to Logs editor accepts 400 characters. This also upgrades
-- installations created by an earlier version of this migration.
ALTER TABLE `room_wired_monitor_logs`
  MODIFY `message` VARCHAR(400) NOT NULL;

CREATE TABLE IF NOT EXISTS `room_wired_monitor_errors` (
  `room_id` INT NOT NULL,
  `monitor_id` INT NOT NULL,
  `error_name` VARCHAR(64) NOT NULL,
  `category` VARCHAR(32) NOT NULL,
  `message` VARCHAR(255) NOT NULL,
  `occurrence_count` INT UNSIGNED NOT NULL,
  `first_at` BIGINT NOT NULL,
  `last_at` BIGINT NOT NULL,
  PRIMARY KEY (`room_id`, `monitor_id`),
  UNIQUE KEY `room_wired_monitor_errors_identity`
    (`room_id`, `category`, `error_name`, `message`),
  KEY `room_wired_monitor_errors_newest` (`room_id`, `last_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Official July furniture class names for action types 49 and 50.
UPDATE `items_base`
SET `interaction_type` = `item_name`
WHERE `item_name` IN ('wf_act_log', 'wf_act_neg_log')
  AND (`interaction_type` = 'default' OR `interaction_type` = `item_name`);
