-- Original Shockwave BattleBall support for NGH/Arcturus.
-- Map masks and spawn rows are preserved from the Kepler reference database.

CREATE TABLE IF NOT EXISTS `battleball_maps` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `game_type` VARCHAR(16) NOT NULL DEFAULT 'battleball',
  `map_id` INT NOT NULL,
  `heightmap` MEDIUMTEXT NOT NULL,
  `tile_map` MEDIUMTEXT NOT NULL,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_battleball_map` (`map_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `battleball_spawns` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `type` VARCHAR(16) NOT NULL DEFAULT 'battleball',
  `map_id` INT NOT NULL,
  `team_id` INT NOT NULL,
  `x` INT NOT NULL,
  `y` INT NOT NULL,
  `z` DOUBLE NOT NULL DEFAULT 0,
  `rotation` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_battleball_spawn` (`type`, `map_id`, `team_id`, `x`, `y`),
  KEY `idx_battleball_spawn` (`map_id`, `team_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

START TRANSACTION;

-- Seed maps by logical map_id; surrogate ids are deployment-specific.
INSERT INTO `battleball_maps` (`game_type`, `map_id`, `heightmap`, `tile_map`) VALUES
	('battleball', '5', 'xxxx000000000000000xxxx|xxxx000000000000000xxxx|xxxx000000000000000xxxx|xxxx000000000000000xxxx|00000000000000000000000|00000000000000000000000|00000000000000000000000|00000000000000000000000|00000000000000000000000|00000000000000000000000|00000000000000000000000|00000000000000000000000|00000000000000000000000|00000000000000000000000|00000000000000000000000|00000000000000000000000|00000000000000000000000|00000000000000000000000|00000000000000000000000|xxxx000000000000000xxxx|xxxx000000000000000xxxx|xxxx000000000000000xxxx|xxxx000000000000000xxxx|', '00001111111111111110000|00001111111111111110000|00001111111111111110000|00001111111111111110000|11111111111111111111111|11111111111111111111111|11111111111111111111111|11111111111111111111111|11111111111111111111111|11111111111111111111111|11111111111111111111111|11111111111111111111111|11111111111111111111111|11111111111111111111111|11111111111111111111111|11111111111111111111111|11111111111111111111111|11111111111111111111111|11111111111111111111111|00001111111111111110000|00001111111111111110000|00001111111111111110000|00001111111111111110000|'),
	('battleball', '3', '0000000x000x0000xxxxxxxxxxxxxxxxx|0xx00x0x0x0000000111000xxxxxxxxxx|0xx00x0x0x00000001110000xxxxxxxxx|0000xx000xxx0000xxxxxx000xxxxxxxx|000xxxxxxxxxxxxxxxxxxxx000xxxxxxx|0xxxxxxxxxxxxxxxxxxxxxxx000xxxxxx|0000xxxxxxxxxxxxxxxxxxxxx000xxxxx|xxx0xxx111111111111111xxx0x0xxxxx|0000xxx111111111111111xxx0x0x0xxx|0xxxxxx111111111111111xxx0x0x0xxx|000xxxx1111111111111111000x00000x|x00xxxx1111111111111111000x0x0xxx|0000xxx111112222211111xxxxxxx0000|0000xxx111112222211111xxx000xxxx0|0000xxx111112222211111xxx000000x0|0000xxx111112222211111xxx000xx0x0|x00xxxx111112222211111xxxxxxx0000|x11xxxx11111111111111110000000xxx|x11xxxx11111111111111110000000xxx|x11xxxx111111111111111xxx0xxxxxxx|x00xxxx111111111111111xxx000xxxxx|x00xxxx111111111111111xxxxxxxxxxx|x000xxxxxx11xxxxx11xxxxxxxxxxxxxx|xx000xxxxx00xxxxx00xxxxxxxxxxxxxx|xxx000xxxx00xxxxx00xxxxxxxxxxxxxx|xxxx00000000x000x0000xxxxxxxxxxxx|xxxxx00xxxxxx000x00x0xxxxxxxxxxxx|xxxxxx000000x000x00x0xxxxxxxxxxxx|xxxxxxxxxx0xxx0xx00xxxxxxxxxxxxxx|xxxxxxxx00000x0x000xxxxxxxxxxxxxx|xxxxxxxxxx0x0x000xxxxxxxxxxxxxxxx|xxxxxxxxxx0x0xxx0xxxxxxxxxxxxxxxx|xxxxxxxxxxxx00000xxxxxxxxxxxxxxxx|', '111100000000111100000000000000000|100100000000111100000000000000000|100100000000111100000000000000000|111100000000111100000000000000000|000000000000000000000000000000000|000000000000000000000000000000000|000000000000000000000000000000000|000000011111110111111100000000000|000000011111110111111100000000000|000000011111110111111100000000000|000000011111110111111100000000000|000000011110000000111100000000000|111100011110000000111100000000000|111100011110011100111100011100000|111100000000011100000000011100000|111100011110011100111100011100000|000000011110000000111100000000000|000000011110000000111100000000000|000000011111110111111100000000000|000000011111110111111100000000000|000000011111110111111100000000000|000000011111110111111100000000000|000000000000000000000000000000000|000000000000000000000000000000000|000000000000000000000000000000000|000000000000011100000000000000000|000000000000011100000000000000000|000000000000011100000000000000000|000000000000000000000000000000000|000000000000000000000000000000000|000000000000000000000000000000000|000000000000000000000000000000000|000000000000000000000000000000000|'),
	('battleball', '1', 'xxxxxxxxxxxxx444444xxxxxxxxx|xxxxxxxxxxxxx444444xxxxxxxxx|xxxxxxxxxxxxx444444xxxxxxxxx|xxx22222222xx444444xxxxxxxxx|xxx22222222xx444444xxxxxxxxx|xxx22222222xx333333xxxxxxxxx|xxx22222222xx222222xxxxxxxxx|xxx222222222222222222xxxxxxx|xxx222222222222222222xxxxxxx|xxx2222222222222222222100000|xxx2222222222222222222100000|xxxxxxx222222222222222100000|xxxxxxx222222222222222100000|4444432222222222222222100000|4444432222222222222222100000|444443222222222222222xxxxxxx|444443222222222222222xxxxxxx|4444432222222222222222222xxx|4444432222222222222222222xxx|xxxxxxx222222222222222222xxx|xxxxxxx222222222222222222xxx|xxxxxxxxx222222xx22222222xxx|xxxxxxxxx111111xx22222222xxx|xxxxxxxxx000000xx22222222xxx|xxxxxxxxx000000xx22222222xxx|xxxxxxxxx000000xxxxxxxxxxxxx|xxxxxxxxx000000xxxxxxxxxxxxx|xxxxxxxxx000000xxxxxxxxxxxxx|', '0000000000000111111000000000|0000000000000111111000000000|0000000000000111111000000000|0001111111100111111000000000|0001111111100000000000000000|0001111111100000000000000000|0001111111100000000000000000|0001111111111111111110000000|0001111111111111111110000000|0001111111111111111110001111|0001111111111111111110001111|0000000111111111111110001111|0000000111111111111110001111|1111000111111111111110001111|1111000111111111111110001111|1111000111111111111110000000|1111000111111111111110000000|1111000111111111111111110000|1111000111111111111111110000|0000000111111111111111111000|0000000111111111111111111000|0000000000000000011111111000|0000000000000000011111111000|0000000000000000011111111000|0000000001111110011111111000|0000000001111110000000000000|0000000001111110000000000000|0000000001111110000000000000|'),
	('battleball', '2', 'xxxxxxx00000000xxx00000000xxxxxxx|xxxxxxx00000000xxx00000000xxxxxxx|xxxxxxx00000000xxx00000000xxxxxxx|xxxxxxx0000000000000000000xxxxxxx|xxxxxxx0000000000000000000xxxxxxx|xxxxxxx0000000000000000000xxxxxxx|xxxxxxx00000000xxx00000000xxxxxxx|2222xxx00000000xxx00000000xxx2222|2222xxx00000000xxx00000000xxx2222|222221000000000xxx000000000122222|222221000000000xxx000000000122222|2222xxx00000000xxx00000000xxx2222|2222xxx00000000xxx00000000xxx2222|xxxxxxx00000000xxx00000000xxxxxxx|xxxxxxx0000000000000000000xxxxxxx|xxxxxxx0000000000000000000xxxxxxx|xxxxxxx0000000000000000000xxxxxxx|xxxxxxx00000000xxx00000000xxxxxxx|xxxxxxx00000000xxx00000000xxxxxxx|xxxxxxx00000000xxx00000000xxxxxxx|', '000000011111111000111111110000000|000000011111111000111111110000000|000000011111111000111111110000000|000000011111111000111111110000000|000000011111111000111111110000000|000000011111111000111111110000000|000000011111111000111111110000000|111100011111111000111111110001111|111100011111111000111111110001111|111100011111111000111111110001111|111100011111111000111111110001111|111100011111111000111111110001111|111100011111111000111111110001111|000000011111111000111111110000000|000000011111111000111111110000000|000000011111111000111111110000000|000000011111111000111111110000000|000000011111111000111111110000000|000000011111111000111111110000000|000000011111111000111111110000000|'),
	('battleball', '4', 'xxxxxxx222222222222222xxxxxxxx|xxxxxxx222222222222222xxxxxxxx|xxxxxxx222222222222222xxxxxxxx|xxxxxxx222222222222222xxxxxxxx|xxxxxxx222222222222222xxxxxxxx|00012222222222222222222211111x|00012222222222222222222211111x|00012222222222222222222211111x|00012222222222222222222211111x|00xxxxx222222222222222xxx1111x|000xxxx222222222222222xxx0000x|0000xxx222222222222222x0000000|0000xxx22222222222222210000000|0000xxx22222222222222210000000|0000xxx222222222222222x0000000|0000xxx222222222222222xxxxxxxx|00000xx222222222222222xxxxxxxx|000000xxxx11xx11xx11xxxxxxxxxx|x0000000000000000000000xxxxxxx|xx000000000000000000000xxxxxxx|xxx00000000000000000000xxxxxxx|xxxx0000000000000000000xxxxxxx|', '000000011111111111111100000000|000000011111111111111100000000|000000011111111111111100000000|000000011111111111111100000000|000000011111111111111100000000|000000011111111111111100011110|000000011111111111111100011110|000000011111111111111100011110|000000011111111111111100011110|110000011111111111111100000000|111000011111111111111100000000|111100011111111111111101111111|111100011111111111111101111111|111100011111111111111101111111|111100011111111111111101111111|111100011111111111111100000000|111110011111111111111100000000|111111000000000000000000000000|011111111111111111111110000000|001111111111111111111110000000|000111111111111111111110000000|000011111111111111111110000000|')
ON DUPLICATE KEY UPDATE
  `game_type` = VALUES(`game_type`),
  `heightmap` = VALUES(`heightmap`),
  `tile_map` = VALUES(`tile_map`),
  `enabled` = 1;
DELETE FROM `battleball_spawns` WHERE `type` = 'battleball' AND `map_id` BETWEEN 1 AND 5;

INSERT INTO `battleball_spawns` (`type`, `map_id`, `team_id`, `x`, `y`, `rotation`) VALUES
	('battleball', 5, 0, 22, 14, 6),
	('battleball', 5, 0, 22, 13, 6),
	('battleball', 5, 0, 22, 12, 6),
	('battleball', 5, 0, 22, 11, 6),
	('battleball', 5, 0, 22, 10, 6),
	('battleball', 5, 0, 22, 9, 6),
	('battleball', 5, 3, 9, 0, 4),
	('battleball', 5, 3, 10, 0, 4),
	('battleball', 5, 3, 11, 0, 4),
	('battleball', 5, 3, 12, 0, 4),
	('battleball', 5, 3, 13, 0, 4),
	('battleball', 5, 3, 14, 0, 4),
	('battleball', 5, 2, 9, 22, 0),
	('battleball', 5, 2, 10, 22, 0),
	('battleball', 5, 2, 11, 22, 0),
	('battleball', 5, 2, 12, 22, 0),
	('battleball', 5, 2, 13, 22, 0),
	('battleball', 5, 2, 14, 22, 0),
	('battleball', 5, 1, 0, 14, 2),
	('battleball', 5, 1, 0, 13, 2),
	('battleball', 5, 1, 0, 12, 2),
	('battleball', 5, 1, 0, 11, 2),
	('battleball', 5, 1, 0, 10, 2),
	('battleball', 5, 1, 0, 9, 2),
	('battleball', 3, 0, 21, 11, 6),
	('battleball', 3, 0, 21, 12, 6),
	('battleball', 3, 0, 21, 13, 6),
	('battleball', 3, 0, 21, 14, 6),
	('battleball', 3, 0, 21, 15, 6),
	('battleball', 3, 0, 21, 16, 6),
	('battleball', 3, 1, 7, 16, 2),
	('battleball', 3, 1, 7, 15, 2),
	('battleball', 3, 1, 7, 14, 2),
	('battleball', 3, 1, 7, 13, 2),
	('battleball', 3, 1, 7, 12, 2),
	('battleball', 3, 1, 7, 11, 2),
	('battleball', 3, 2, 11, 7, 4),
	('battleball', 3, 2, 12, 7, 4),
	('battleball', 3, 2, 13, 7, 4),
	('battleball', 3, 2, 14, 7, 4),
	('battleball', 3, 2, 15, 7, 4),
	('battleball', 3, 2, 16, 7, 4),
	('battleball', 3, 3, 16, 21, 0),
	('battleball', 3, 3, 15, 21, 0),
	('battleball', 3, 3, 14, 21, 0),
	('battleball', 3, 3, 13, 21, 0),
	('battleball', 3, 3, 12, 21, 0),
	('battleball', 3, 3, 11, 21, 0),
	('battleball', 1, 0, 0, 13, 2),
	('battleball', 1, 0, 0, 14, 2),
	('battleball', 1, 0, 0, 15, 2),
	('battleball', 1, 0, 0, 16, 2),
	('battleball', 1, 0, 0, 17, 2),
	('battleball', 1, 0, 0, 18, 2),
	('battleball', 1, 1, 27, 9, 6),
	('battleball', 1, 1, 27, 10, 6),
	('battleball', 1, 1, 27, 11, 6),
	('battleball', 1, 1, 27, 12, 6),
	('battleball', 1, 1, 27, 13, 6),
	('battleball', 1, 1, 27, 14, 6),
	('battleball', 1, 3, 13, 0, 4),
	('battleball', 1, 3, 14, 0, 4),
	('battleball', 1, 3, 15, 0, 4),
	('battleball', 1, 3, 16, 0, 4),
	('battleball', 1, 3, 17, 0, 4),
	('battleball', 1, 3, 18, 0, 4),
	('battleball', 1, 2, 14, 27, 0),
	('battleball', 1, 2, 13, 27, 0),
	('battleball', 1, 2, 12, 27, 0),
	('battleball', 1, 2, 11, 27, 0),
	('battleball', 1, 2, 10, 27, 0),
	('battleball', 1, 2, 9, 27, 0),
	('battleball', 2, 0, 0, 7, 2),
	('battleball', 2, 0, 0, 8, 2),
	('battleball', 2, 0, 0, 9, 2),
	('battleball', 2, 0, 0, 10, 2),
	('battleball', 2, 0, 0, 11, 2),
	('battleball', 2, 0, 0, 12, 2),
	('battleball', 2, 2, 14, 12, 6),
	('battleball', 2, 2, 14, 11, 6),
	('battleball', 2, 2, 14, 10, 6),
	('battleball', 2, 2, 14, 9, 6),
	('battleball', 2, 2, 14, 8, 6),
	('battleball', 2, 2, 14, 7, 6),
	('battleball', 2, 3, 18, 8, 2),
	('battleball', 2, 3, 18, 9, 2),
	('battleball', 2, 3, 18, 10, 2),
	('battleball', 2, 3, 18, 11, 2),
	('battleball', 2, 3, 18, 12, 2),
	('battleball', 2, 3, 18, 13, 2),
	('battleball', 2, 1, 32, 7, 6),
	('battleball', 2, 1, 32, 8, 6),
	('battleball', 2, 1, 32, 9, 6),
	('battleball', 2, 1, 32, 10, 6),
	('battleball', 2, 1, 32, 11, 6),
	('battleball', 2, 1, 32, 12, 6),
	('battleball', 4, 0, 12, 16, 0),
	('battleball', 4, 0, 13, 16, 0),
	('battleball', 4, 0, 14, 16, 0),
	('battleball', 4, 0, 15, 16, 0),
	('battleball', 4, 0, 16, 16, 0),
	('battleball', 4, 0, 17, 16, 0),
	('battleball', 4, 1, 17, 0, 4),
	('battleball', 4, 1, 16, 0, 4),
	('battleball', 4, 1, 15, 0, 4),
	('battleball', 4, 1, 14, 0, 4),
	('battleball', 4, 1, 13, 0, 4),
	('battleball', 4, 1, 12, 0, 4),
	('battleball', 4, 2, 7, 5, 2),
	('battleball', 4, 2, 7, 6, 2),
	('battleball', 4, 2, 7, 7, 2),
	('battleball', 4, 2, 7, 8, 2),
	('battleball', 4, 2, 7, 9, 2),
	('battleball', 4, 2, 7, 10, 2),
	('battleball', 4, 3, 21, 5, 6),
	('battleball', 4, 3, 21, 6, 6),
	('battleball', 4, 3, 21, 7, 6),
	('battleball', 4, 3, 21, 8, 6),
	('battleball', 4, 3, 21, 9, 6),
	('battleball', 4, 3, 21, 10, 6);

COMMIT;

CREATE TABLE IF NOT EXISTS `battleball_rounds` (
  `result_id` INT NOT NULL AUTO_INCREMENT,
  `instance_id` INT NOT NULL,
  `round_number` INT NOT NULL,
  `game_name` VARCHAR(32) NOT NULL DEFAULT '',
  `owner_user_id` INT NOT NULL DEFAULT 0,
  `owner_name` VARCHAR(64) NOT NULL DEFAULT '',
  `map_id` INT NOT NULL,
  `private_game` TINYINT(1) NOT NULL DEFAULT 0,
  `team_count` TINYINT UNSIGNED NOT NULL DEFAULT 2,
  `capacity` TINYINT UNSIGNED NOT NULL DEFAULT 2,
  `round_seconds` SMALLINT UNSIGNED NOT NULL DEFAULT 120,
  `started_at` INT NOT NULL,
  `ended_at` INT NULL,
  `winning_team` INT NULL,
  `team_0_score` INT NOT NULL DEFAULT 0,
  `team_1_score` INT NOT NULL DEFAULT 0,
  `team_2_score` INT NOT NULL DEFAULT 0,
  `team_3_score` INT NOT NULL DEFAULT 0,
  `ranked` TINYINT(1) NOT NULL DEFAULT 1,
  `aborted` TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`instance_id`, `round_number`),
  UNIQUE KEY `uq_battleball_round_result` (`result_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE `battleball_rounds`
  ADD COLUMN IF NOT EXISTS `result_id` INT NOT NULL AUTO_INCREMENT FIRST,
  ADD UNIQUE INDEX IF NOT EXISTS `uq_battleball_round_result` (`result_id`),
  ADD COLUMN IF NOT EXISTS `game_name` VARCHAR(32) NOT NULL DEFAULT '' AFTER `round_number`,
  ADD COLUMN IF NOT EXISTS `owner_user_id` INT NOT NULL DEFAULT 0 AFTER `game_name`,
  ADD COLUMN IF NOT EXISTS `owner_name` VARCHAR(64) NOT NULL DEFAULT '' AFTER `owner_user_id`,
  ADD COLUMN IF NOT EXISTS `private_game` TINYINT(1) NOT NULL DEFAULT 0 AFTER `map_id`,
  ADD COLUMN IF NOT EXISTS `team_count` TINYINT UNSIGNED NOT NULL DEFAULT 2 AFTER `private_game`,
  ADD COLUMN IF NOT EXISTS `capacity` TINYINT UNSIGNED NOT NULL DEFAULT 2 AFTER `team_count`,
  ADD COLUMN IF NOT EXISTS `round_seconds` SMALLINT UNSIGNED NOT NULL DEFAULT 120 AFTER `capacity`,
  ADD COLUMN IF NOT EXISTS `team_0_score` INT NOT NULL DEFAULT 0 AFTER `winning_team`,
  ADD COLUMN IF NOT EXISTS `team_1_score` INT NOT NULL DEFAULT 0 AFTER `team_0_score`,
  ADD COLUMN IF NOT EXISTS `team_2_score` INT NOT NULL DEFAULT 0 AFTER `team_1_score`,
  ADD COLUMN IF NOT EXISTS `team_3_score` INT NOT NULL DEFAULT 0 AFTER `team_2_score`,
  ADD COLUMN IF NOT EXISTS `ranked` TINYINT(1) NOT NULL DEFAULT 1 AFTER `team_3_score`,
  ADD COLUMN IF NOT EXISTS `aborted` TINYINT(1) NOT NULL DEFAULT 0 AFTER `ranked`;

CREATE TABLE IF NOT EXISTS `battleball_round_players` (
  `instance_id` INT NOT NULL,
  `round_number` INT NOT NULL,
  `user_id` INT NOT NULL,
  `player_name` VARCHAR(64) NOT NULL DEFAULT '',
  `team_id` INT NOT NULL,
  `ticket_cost` INT NOT NULL DEFAULT 0,
  `score` INT NOT NULL DEFAULT 0,
  `winner` TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`instance_id`, `round_number`, `user_id`),
  KEY `idx_battleball_round_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE `battleball_round_players`
  ADD COLUMN IF NOT EXISTS `player_name` VARCHAR(64) NOT NULL DEFAULT '' AFTER `user_id`;

CREATE TABLE IF NOT EXISTS `battleball_player_stats` (
  `user_id` INT NOT NULL,
  `games_played` INT NOT NULL DEFAULT 0,
  `wins` INT NOT NULL DEFAULT 0,
  `total_score` BIGINT NOT NULL DEFAULT 0,
  `skill_level` INT NOT NULL DEFAULT 1,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Tickets are shared by multiple classic public-room activities, not owned by BattleBall.
-- Preserve an administrator's existing currency type while migrating the old key.
INSERT INTO `emulator_settings` (`key`, `value`)
SELECT 'hotel.ticket.currency.type', `value`
FROM `emulator_settings`
WHERE `key` = 'battleball.ticket.currency.type'
ON DUPLICATE KEY UPDATE `value` = `value`;

INSERT INTO `emulator_settings` (`key`, `value`) VALUES
  ('hotel.ticket.currency.type', '106')
ON DUPLICATE KEY UPDATE `value` = `value`;

DELETE FROM `emulator_settings` WHERE `key` = 'battleball.ticket.currency.type';

-- Migrate the early singular bot-setting names without losing administrator values.
INSERT IGNORE INTO `emulator_settings` (`key`, `value`)
SELECT 'battleball.bots.decision.interval.ms', `value` FROM `emulator_settings`
WHERE `key` = 'battleball.bot.decision.milliseconds';
INSERT IGNORE INTO `emulator_settings` (`key`, `value`)
SELECT 'battleball.bots.search-radius.tiles', `value` FROM `emulator_settings`
WHERE `key` = 'battleball.bot.search.radius';
INSERT IGNORE INTO `emulator_settings` (`key`, `value`)
SELECT 'battleball.bots.figures', `value` FROM `emulator_settings`
WHERE `key` = 'battleball.bot.figures';
DELETE FROM `emulator_settings` WHERE `key` IN (
  'battleball.bot.decision.milliseconds',
  'battleball.bot.search.radius',
  'battleball.bot.figures'
);

INSERT INTO `emulator_settings` (`key`, `value`) VALUES
  ('battleball.enabled', '0'),
  ('battleball.ticket.cost', '2'),
  ('battleball.bots.enabled', '1'),
  ('battleball.bots.max-per-game', '8'),
  ('battleball.ranked.requires.human.opponent', '1'),
  ('battleball.bots.decision.interval.ms', '1500'),
  ('battleball.bots.search-radius.tiles', '8'),
  ('battleball.bots.figures', 'hr-100.hd-190-7.ch-210-66.lg-270-82.sh-290-80,hr-100-61.hd-190-1.ch-210-92.lg-270-82.sh-290-80,hr-115-42.hd-195-19.ch-3030-82.lg-275-1408.fa-1201.ca-1804-64,ch-665-92.hd-629-1.sh-740-92.hr-515-31.lg-3174-71-92.he-3082-92'),
  ('battleball.max.instances', '100'),
  ('battleball.max.spectators', '50'),
  ('battleball.idle.timeout.seconds', '60'),
  ('battleball.idle.warning.seconds', '15')
ON DUPLICATE KEY UPDATE `value` = VALUES(`value`);

-- Shared, room-scoped leaderboard catalogue and metric store.
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

INSERT INTO `leaderboards`
  (`room_id`, `game_type`, `metric`, `title_localization_code`,
   `description_localization_code`, `menu_localization_code`, `sort_order`, `enabled`)
SELECT `id`, 'BATTLEBALL', 'POINTS', 'gamehall.leaderboard.option.battleball',
       'gamehall.leaderboard.info.battleball', 'gamehall.leaderboard.dropdown.battleball', 0, 1
FROM `rooms` WHERE `model` = 'bb_lobby_1'
ON DUPLICATE KEY UPDATE
  `title_localization_code` = VALUES(`title_localization_code`),
  `description_localization_code` = VALUES(`description_localization_code`),
  `menu_localization_code` = VALUES(`menu_localization_code`),
  `sort_order` = VALUES(`sort_order`), `enabled` = 1;
