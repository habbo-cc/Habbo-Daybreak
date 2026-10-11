-- Reward Track + Habbicons foundations.
-- Idempotent first slice: definitions, user state, safe defaults, and one Introduction track.

CREATE TABLE IF NOT EXISTS `reward_tracks` (
  `id` varchar(64) NOT NULL,
  `theme` varchar(64) NOT NULL DEFAULT 'introduction',
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  `sort_order` int(11) NOT NULL DEFAULT 0,
  `premium_enabled` tinyint(1) NOT NULL DEFAULT 0,
  `task_points_boost` double NOT NULL DEFAULT 1,
  `instant_points` int(11) NOT NULL DEFAULT 0,
  `premium_cost_points` int(11) NOT NULL DEFAULT 0,
  `premium_cost_points_type` int(11) NOT NULL DEFAULT 5,
  `premium_cost_credits` int(11) NOT NULL DEFAULT 0,
  `created_at` int(11) NOT NULL DEFAULT 0,
  `updated_at` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_reward_tracks_enabled_sort` (`enabled`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `reward_tracks`
  ADD COLUMN IF NOT EXISTS `premium_cost_points` int(11) NOT NULL DEFAULT 0 AFTER `instant_points`;

ALTER TABLE `reward_tracks`
  ADD COLUMN IF NOT EXISTS `premium_cost_points_type` int(11) NOT NULL DEFAULT 5 AFTER `premium_cost_points`;

SET @reward_tracks_has_legacy_diamonds = (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'reward_tracks'
    AND COLUMN_NAME = 'premium_cost_diamonds'
);
SET @reward_tracks_migrate_legacy_diamonds = IF(
  @reward_tracks_has_legacy_diamonds > 0,
  'UPDATE `reward_tracks` SET `premium_cost_points` = `premium_cost_diamonds` WHERE `premium_cost_points` = 0',
  'SELECT 1'
);
PREPARE reward_tracks_migrate_legacy_diamonds_stmt FROM @reward_tracks_migrate_legacy_diamonds;
EXECUTE reward_tracks_migrate_legacy_diamonds_stmt;
DEALLOCATE PREPARE reward_tracks_migrate_legacy_diamonds_stmt;

ALTER TABLE `reward_tracks`
  DROP COLUMN IF EXISTS `premium_cost_diamonds`;

CREATE TABLE IF NOT EXISTS `reward_track_tasks` (
  `id` varchar(64) NOT NULL,
  `track_id` varchar(64) NOT NULL,
  `action_type` varchar(64) NOT NULL,
  `parameter` varchar(255) NOT NULL DEFAULT '',
  `premium` tinyint(1) NOT NULL DEFAULT 0,
  `sort_order` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_reward_track_tasks_track_sort` (`track_id`, `sort_order`),
  CONSTRAINT `fk_reward_track_tasks_track`
    FOREIGN KEY (`track_id`) REFERENCES `reward_tracks` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `reward_track_task_levels` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `task_id` varchar(64) NOT NULL,
  `level` int(11) NOT NULL DEFAULT 1,
  `required_count` int(11) NOT NULL DEFAULT 1,
  `points_reward` int(11) NOT NULL DEFAULT 0,
  `premium` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reward_track_task_level` (`task_id`, `level`),
  KEY `idx_reward_track_task_levels_task` (`task_id`),
  CONSTRAINT `fk_reward_track_task_levels_task`
    FOREIGN KEY (`task_id`) REFERENCES `reward_track_tasks` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `reward_track_rewards` (
  `id` varchar(64) NOT NULL,
  `track_id` varchar(64) NOT NULL,
  `required_points` int(11) NOT NULL DEFAULT 0,
  `product_item_type_id` smallint(6) NOT NULL DEFAULT 0,
  `reward_type` varchar(64) NOT NULL DEFAULT '',
  `extra_params` varchar(255) NOT NULL DEFAULT '',
  `reward_amount` int(11) NOT NULL DEFAULT 1,
  `premium` tinyint(1) NOT NULL DEFAULT 0,
  `sort_order` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_reward_track_rewards_track_sort` (`track_id`, `sort_order`),
  CONSTRAINT `fk_reward_track_rewards_track`
    FOREIGN KEY (`track_id`) REFERENCES `reward_tracks` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `users_reward_tracks` (
  `user_id` int(11) NOT NULL,
  `track_id` varchar(64) NOT NULL,
  `points` int(11) NOT NULL DEFAULT 0,
  `premium` tinyint(1) NOT NULL DEFAULT 0,
  `complete` tinyint(1) NOT NULL DEFAULT 0,
  `premium_complete` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` int(11) NOT NULL DEFAULT 0,
  `updated_at` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`user_id`, `track_id`),
  KEY `idx_users_reward_tracks_track` (`track_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `users_reward_track_tasks` (
  `user_id` int(11) NOT NULL,
  `track_id` varchar(64) NOT NULL,
  `task_id` varchar(64) NOT NULL,
  `progress_count` int(11) NOT NULL DEFAULT 0,
  `completed_level` int(11) NOT NULL DEFAULT 0,
  `created_at` int(11) NOT NULL DEFAULT 0,
  `updated_at` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`user_id`, `task_id`),
  KEY `idx_users_reward_track_tasks_track` (`user_id`, `track_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `users_reward_track_claims` (
  `user_id` int(11) NOT NULL,
  `track_id` varchar(64) NOT NULL,
  `reward_id` varchar(64) NOT NULL,
  `claimed_at` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`user_id`, `reward_id`),
  KEY `idx_users_reward_track_claims_track` (`track_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `habbicon_collections` (
  `id` int(11) NOT NULL,
  `name` varchar(128) NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  `price_credits` int(11) NOT NULL DEFAULT 0,
  `price_activity_points` int(11) NOT NULL DEFAULT 0,
  `activity_point_type` int(11) NOT NULL DEFAULT 0,
  `sort_order` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_habbicon_collections_enabled_sort` (`enabled`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `habbicons` (
  `id` int(11) NOT NULL,
  `collection_id` int(11) NOT NULL DEFAULT 0,
  `name` varchar(128) NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  `is_reward` tinyint(1) NOT NULL DEFAULT 0,
  `price_credits` int(11) NOT NULL DEFAULT 0,
  `price_activity_points` int(11) NOT NULL DEFAULT 0,
  `activity_point_type` int(11) NOT NULL DEFAULT 0,
  `sort_order` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_habbicons_collection_sort` (`collection_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `users_habbicons` (
  `user_id` int(11) NOT NULL,
  `habbicon_id` int(11) NOT NULL,
  `state` tinyint(3) NOT NULL DEFAULT 2,
  `favorite` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` int(11) NOT NULL DEFAULT 0,
  `updated_at` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`user_id`, `habbicon_id`),
  KEY `idx_users_habbicons_user_favorite` (`user_id`, `favorite`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `users_habbicon_recents` (
  `user_id` int(11) NOT NULL,
  `habbicon_id` int(11) NOT NULL,
  `used_at` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`user_id`, `habbicon_id`),
  KEY `idx_users_habbicon_recents_user_time` (`user_id`, `used_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `emulator_settings` (`key`, `value`) VALUES
('rewardtrack.enabled', '1'),
('rewardtrack.seed.introduction.enabled', '1'),
('habbicons.enabled', '1'),
('habbicons.asset.root', 'http://localhost/ngh/habbicons'),
('habbicons.asset.hash', 'dev'),
('habbicons.recents.max', '20')
ON DUPLICATE KEY UPDATE `value` = VALUES(`value`);

DELETE FROM `emulator_settings`
WHERE `key` IN (
  'rewardtrack.premium.price.credits',
  'rewardtrack.premium.price.points',
  'rewardtrack.premium.price.points.type',
  'rewardtrack.premium.task_points_boost',
  'rewardtrack.premium.instant_points'
);

INSERT INTO `reward_tracks`
(`id`, `theme`, `enabled`, `sort_order`, `premium_enabled`, `task_points_boost`, `instant_points`, `premium_cost_points`, `premium_cost_points_type`, `premium_cost_credits`)
VALUES
('introduction', 'introduction', 1, 10, 1, 1.2, 0, 100, 5, 100)
ON DUPLICATE KEY UPDATE
`theme` = VALUES(`theme`),
`enabled` = VALUES(`enabled`),
`sort_order` = VALUES(`sort_order`),
`premium_enabled` = VALUES(`premium_enabled`),
`task_points_boost` = VALUES(`task_points_boost`),
`instant_points` = VALUES(`instant_points`),
`premium_cost_points` = VALUES(`premium_cost_points`),
`premium_cost_points_type` = VALUES(`premium_cost_points_type`),
`premium_cost_credits` = VALUES(`premium_cost_credits`);

DELETE FROM `reward_track_tasks` WHERE `id` IN ('dance', 'wave');

INSERT INTO `reward_track_tasks`
(`id`, `track_id`, `action_type`, `parameter`, `premium`, `sort_order`)
VALUES
('visit_rooms', 'introduction', 'enter_other_users_room', '', 0, 10),
('chat_with_users', 'introduction', 'chat_with_someone', '', 0, 20),
('dance_in_room', 'introduction', 'dance', '', 0, 30),
('wave_at_user', 'introduction', 'wave', '', 0, 40),
('give_respect', 'introduction', 'give_respect', '', 0, 60),
('make_friends', 'introduction', 'request_friend', '', 0, 80),
('change_motto', 'introduction', 'change_motto', '', 0, 120),
('change_outfit', 'introduction', 'change_figure', '', 0, 130),
('wear_badge', 'introduction', 'wear_badge', '', 0, 140),
('use_teleport', 'introduction', 'teleport', '', 0, 160),
('grab_drink', 'introduction', 'find_hand_item', '', 0, 170),
('use_furniture', 'introduction', 'switch_item_state', '', 0, 180),
('go_swimming', 'introduction', 'swim', '', 0, 190),
('create_room', 'introduction', 'create_room', '', 0, 200),
('buy_catalog_furni', 'introduction', 'buy_from_catalogue', '', 0, 210),
('place_furniture', 'introduction', 'place_item', '', 0, 220),
('move_furniture', 'introduction', 'move_item', '', 0, 240),
('rotate_furniture', 'introduction', 'rotate_item', '', 0, 250),
('send_messenger_message', 'introduction', 'send_messenger_message', '', 0, 260),
('send_messenger_invite', 'introduction', 'send_messenger_invite', '', 0, 270),
('follow_friend', 'introduction', 'follow_friend', '', 0, 280),
('set_relationship_status', 'introduction', 'set_relationship_status', '', 0, 290),
('use_habbicon', 'introduction', 'use_habbicon', '', 0, 300)
ON DUPLICATE KEY UPDATE
`track_id` = VALUES(`track_id`),
`action_type` = VALUES(`action_type`),
`parameter` = VALUES(`parameter`),
`premium` = VALUES(`premium`),
`sort_order` = VALUES(`sort_order`);

INSERT INTO `reward_track_task_levels`
(`task_id`, `level`, `required_count`, `points_reward`, `premium`)
VALUES
('visit_rooms', 1, 1, 10, 0),
('visit_rooms', 2, 5, 20, 0),
('visit_rooms', 3, 20, 30, 0),
('chat_with_users', 1, 5, 10, 0),
('chat_with_users', 2, 20, 20, 0),
('chat_with_users', 3, 50, 30, 0),
('chat_with_users', 4, 200, 40, 0),
('dance_in_room', 1, 1, 20, 0),
('wave_at_user', 1, 1, 10, 0),
('give_respect', 1, 3, 10, 0),
('give_respect', 2, 6, 20, 0),
('give_respect', 3, 24, 30, 0),
('make_friends', 1, 1, 10, 0),
('make_friends', 2, 5, 20, 0),
('make_friends', 3, 10, 30, 0),
('make_friends', 4, 20, 40, 0),
('change_motto', 1, 1, 30, 0),
('change_outfit', 1, 1, 30, 0),
('wear_badge', 1, 1, 30, 0),
('use_teleport', 1, 1, 20, 0),
('grab_drink', 1, 1, 20, 0),
('use_furniture', 1, 5, 10, 0),
('use_furniture', 2, 10, 20, 0),
('use_furniture', 3, 20, 30, 0),
('go_swimming', 1, 1, 20, 0),
('create_room', 1, 1, 50, 0),
('buy_catalog_furni', 1, 1, 50, 0),
('place_furniture', 1, 5, 10, 0),
('place_furniture', 2, 10, 20, 0),
('place_furniture', 3, 20, 30, 0),
('move_furniture', 1, 5, 10, 0),
('move_furniture', 2, 10, 20, 0),
('move_furniture', 3, 20, 30, 0),
('rotate_furniture', 1, 5, 10, 0),
('rotate_furniture', 2, 10, 20, 0),
('rotate_furniture', 3, 20, 30, 0),
('send_messenger_message', 1, 5, 10, 0),
('send_messenger_message', 2, 20, 20, 0),
('send_messenger_invite', 1, 1, 30, 0),
('follow_friend', 1, 1, 30, 0),
('set_relationship_status', 1, 1, 30, 0),
('use_habbicon', 1, 1, 30, 0)
ON DUPLICATE KEY UPDATE
`required_count` = VALUES(`required_count`),
`points_reward` = VALUES(`points_reward`),
`premium` = VALUES(`premium`);

INSERT INTO `reward_track_rewards`
(`id`, `track_id`, `required_points`, `product_item_type_id`, `reward_type`, `extra_params`, `reward_amount`, `premium`, `sort_order`)
VALUES
('intro_credits_10', 'introduction', 10, 0, 'credits', '', 10, 0, 10),
('intro_diamonds_25', 'introduction', 10, 0, 'activity_points', '5', 25, 1, 10),
('intro_pixels_20', 'introduction', 20, 0, 'activity_points', '0', 20, 0, 20),
('intro_credits_30', 'introduction', 30, 0, 'credits', '', 10, 0, 30),
('intro_duckets_40', 'introduction', 40, 0, 'activity_points', '0', 20, 0, 40),
('intro_diamonds_100', 'introduction', 50, 0, 'activity_points', '5', 50, 0, 60),
('intro_diamonds_100_2', 'introduction', 60, 0, 'activity_points', '5', 25, 0, 60),
('intro_omie_70', 'introduction', 70, 0, 'catalog_item', '11532', 1, 0, 70),
('intro_badge_cnl01_80', 'introduction', 80, 0, 'badge', 'CNL01', 1, 0, 80),
('intro_habbicon_duck_90', 'introduction', 90, 0, 'habbicon', '28', 1, 0, 90)
ON DUPLICATE KEY UPDATE
`track_id` = VALUES(`track_id`),
`required_points` = VALUES(`required_points`),
`product_item_type_id` = VALUES(`product_item_type_id`),
`reward_type` = VALUES(`reward_type`),
`extra_params` = VALUES(`extra_params`),
`reward_amount` = VALUES(`reward_amount`),
`premium` = VALUES(`premium`),
`sort_order` = VALUES(`sort_order`);
