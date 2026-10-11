CREATE TABLE IF NOT EXISTS `gamehall_match_results` (
  `id` INT(11) NOT NULL AUTO_INCREMENT,
  `game_type` VARCHAR(32) NOT NULL,
  `room_id` INT(11) NOT NULL DEFAULT 0,
  `station_id` INT(11) NOT NULL DEFAULT 0,
  `player_one_user_id` INT(11) NOT NULL,
  `player_two_user_id` INT(11) NOT NULL,
  `winner_user_id` INT(11) DEFAULT NULL,
  `loser_user_id` INT(11) DEFAULT NULL,
  `result_type` VARCHAR(16) NOT NULL DEFAULT 'WIN',
  `started_at` BIGINT(20) DEFAULT NULL,
  `ended_at` BIGINT(20) NOT NULL,
  `duration_seconds` INT(11) NOT NULL DEFAULT 0,
  `winner_score` INT(11) NOT NULL DEFAULT 0,
  `loser_score` INT(11) NOT NULL DEFAULT 0,
  `metadata` TEXT DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_gamehall_results_game_ended` (`game_type`, `ended_at`),
  KEY `idx_gamehall_results_winner` (`game_type`, `winner_user_id`, `ended_at`),
  KEY `idx_gamehall_results_loser` (`game_type`, `loser_user_id`, `ended_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Leaderboard presentation is room-owned. Runtime requests never trust a room or game supplied by
-- the client: the selected definition must belong to the user's current room.
CREATE TABLE IF NOT EXISTS `leaderboards` (
  `id` INT(11) NOT NULL AUTO_INCREMENT,
  `room_id` INT(11) NOT NULL,
  `game_type` VARCHAR(32) NOT NULL,
  `metric` VARCHAR(32) NOT NULL,
  `title_localization_code` VARCHAR(128) NOT NULL,
  `description_localization_code` VARCHAR(128) NOT NULL,
  `menu_localization_code` VARCHAR(128) NOT NULL,
  `sort_order` INT(11) NOT NULL DEFAULT 0,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_leaderboards_room_game_metric` (`room_id`, `game_type`, `metric`),
  KEY `idx_leaderboards_room_enabled_sort` (`room_id`, `enabled`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Statistics are metric rows rather than a fixed set of columns, allowing new ranked metrics
-- without another schema alteration.
DROP TABLE IF EXISTS `gamehall_leaderboard_stats`;

CREATE TABLE IF NOT EXISTS `leaderboard_stats` (
  `game_type` VARCHAR(32) NOT NULL,
  `metric` VARCHAR(32) NOT NULL,
  `period_type` VARCHAR(16) NOT NULL,
  `period_start` BIGINT(20) NOT NULL DEFAULT 0,
  `user_id` INT(11) NOT NULL,
  `value` INT(11) NOT NULL DEFAULT 0,
  `last_result_at` BIGINT(20) NOT NULL DEFAULT 0,
  PRIMARY KEY (`game_type`, `metric`, `period_type`, `period_start`, `user_id`),
  KEY `idx_leaderboard_stats_rank` (`game_type`, `metric`, `period_type`, `period_start`, `value`, `last_result_at`),
  KEY `idx_leaderboard_stats_user` (`user_id`, `game_type`, `metric`, `period_type`, `period_start`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Every Games Hall room exposes the complete Games Hall set. Other products seed their own room
-- rows from their standalone installers.
INSERT INTO `leaderboards`
  (`room_id`, `game_type`, `metric`, `title_localization_code`,
   `description_localization_code`, `menu_localization_code`, `sort_order`, `enabled`)
SELECT `id`, 'ALL', 'POINTS', 'gamehall.leaderboard.option',
       'gamehall.leaderboard.info', 'gamehall.leaderboard.dropdown.all', 0, 1
FROM `rooms` WHERE `model` IN ('hallA', 'hallB', 'hallC', 'hallD')
ON DUPLICATE KEY UPDATE
  `title_localization_code` = VALUES(`title_localization_code`),
  `description_localization_code` = VALUES(`description_localization_code`),
  `menu_localization_code` = VALUES(`menu_localization_code`),
  `sort_order` = VALUES(`sort_order`), `enabled` = 1;

INSERT INTO `leaderboards`
  (`room_id`, `game_type`, `metric`, `title_localization_code`,
   `description_localization_code`, `menu_localization_code`, `sort_order`, `enabled`)
SELECT `id`, `game_type`, 'POINTS',
       CONCAT('gamehall.leaderboard.option.', LOWER(`game_type`)),
       CONCAT('gamehall.leaderboard.info.', LOWER(`game_type`)),
       CONCAT('gamehall.leaderboard.dropdown.', LOWER(`game_type`)),
       `sort_order`, 1
FROM `rooms`
CROSS JOIN (
  SELECT 'BATTLESHIPS' AS `game_type`, 10 AS `sort_order`
  UNION ALL SELECT 'TICTACTOE', 20
  UNION ALL SELECT 'CHESS', 30
  UNION ALL SELECT 'POKER', 40
) AS `games`
WHERE `rooms`.`model` IN ('hallA', 'hallB', 'hallC', 'hallD')
ON DUPLICATE KEY UPDATE
  `title_localization_code` = VALUES(`title_localization_code`),
  `description_localization_code` = VALUES(`description_localization_code`),
  `menu_localization_code` = VALUES(`menu_localization_code`),
  `sort_order` = VALUES(`sort_order`), `enabled` = 1;
