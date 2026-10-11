SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =====================================================
-- Update 4.0.3-beta to 4.0.4-beta
-- =====================================================

ALTER TABLE `catalog_pages`
    ADD COLUMN IF NOT EXISTS `catalog_type` ENUM('NORMAL', 'BUILDERS_CLUB') NOT NULL DEFAULT 'NORMAL' AFTER `page_layout`;

ALTER TABLE `catalog_items`
    ADD COLUMN IF NOT EXISTS `subscription_type` VARCHAR(64) NULL DEFAULT NULL AFTER `offer_id`,
    ADD COLUMN IF NOT EXISTS `subscription_days` INT NULL DEFAULT NULL AFTER `subscription_type`;

ALTER TABLE `users_settings`
    ADD COLUMN IF NOT EXISTS `builders_club_furni_limit` INT NOT NULL DEFAULT 50 AFTER `max_friends`,
    ADD COLUMN IF NOT EXISTS `builders_club_max_furni_limit` INT NOT NULL DEFAULT 50 AFTER `builders_club_furni_limit`;

-- Add BC flag column to items
ALTER TABLE `items`
    ADD COLUMN IF NOT EXISTS `is_builders_club` TINYINT(1) NOT NULL DEFAULT 0 AFTER `wired_data`;

-- Add index for BC furni count queries (MariaDB 10.2 compatible)
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'items' AND INDEX_NAME = 'idx_items_bc');
SET @sql_idx = IF(@idx_exists = 0, 'ALTER TABLE `items` ADD INDEX `idx_items_bc` (`user_id`, `is_builders_club`, `room_id`)', 'SELECT 1');
PREPARE stmt_idx FROM @sql_idx;
EXECUTE stmt_idx;
DEALLOCATE PREPARE stmt_idx;

-- Drop old sequence tables
DROP TABLE IF EXISTS `builders_club_item_sequence`;
DROP TABLE IF EXISTS `items_item_sequence`;

-- Purge any old BC items (IDs in the reserved range)
DELETE FROM `items` WHERE `id` >= 2147418112;

-- Reset AUTO_INCREMENT to be safe (after purging BC items)
-- This dynamically sets it to MAX(id) + 1
SET @max_id = (SELECT COALESCE(MAX(`id`), 0) + 1 FROM `items` WHERE `id` < 2147418112);
SET @sql = CONCAT('ALTER TABLE `items` AUTO_INCREMENT = ', @max_id);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO `emulator_settings` (`key`, `value`) VALUES
('builders.club.enabled', '0'),
('builders.club.furni.limit', '50'),
('builders.club.max.furni.limit', '250'),
('builders.club.box.furni.limit.increment', '750'),
('builders.club.grace.seconds', '0'),
('builders.club.furniture.placement.group.room.enabled', '0'),
('builders.club.expiry.action', 'none'),
('builders.club.achievement', 'BuildersClub'),
('builders.club.buy_membership_page', ''),
('builders.club.try_page', ''),
('builders.club.furnidata.url', ''),
('builders.club.free_trial.enabled', '1'),
('builders.club.free_trial.limit', '50'),
('builders.club.expiry.warning.seconds', '86400')
ON DUPLICATE KEY UPDATE `value` = VALUES(`value`);

-- JSON camera renderer defaults (new on json-camera branch).
-- INSERT IGNORE preserves any operator-customised paths/URLs already set.
INSERT IGNORE INTO `emulator_settings` (`key`, `value`) VALUES
('camera.assets.sprites.path', './camera/sprites'),
('camera.assets.frames.path', './camera/frames'),
('camera.assets.binary.path', './camera/binary'),
('camera.output.path', './camera/output'),
('camera.output.thumbnail.path', './camera/output/thumbnails'),
('camera.output.url', 'http://localhost/camera/'),
('camera.wall.colors.path', './camera_wall_colors.properties'),
('camera.allowed.image.hosts', ''),
('camera.image.fetch.budget.ms', '8000'),
('camera.image.fetch.connect.timeout.ms', '2000'),
('camera.image.fetch.read.timeout.ms', '3000'),
('camera.image.fetch.max.bytes', '2097152'),
('camera.image.fetch.max.per.render', '30'),
('camera.image.fetch.max.pixels', '4000000'),
('camera.limits.compressed.bytes', '16384'),
('camera.limits.inflated.bytes', '262144'),
('camera.limits.filters', '16'),
('camera.limits.planes', '64'),
('camera.limits.sprites', '512'),
('camera.limits.plane.texture.pixels', '4000000');

SET FOREIGN_KEY_CHECKS = 1;
