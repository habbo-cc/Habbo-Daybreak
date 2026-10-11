-- Habbicons protocol/runtime seed.
-- Idempotent and state-preserving: never deletes or rewrites user Habbicon ownership.
-- IDs intentionally match the hosted July Habbicon spritesheet metadata:
-- C:\habbo\ngh\habbicons\dev\habbicons.json

INSERT INTO `emulator_settings` (`key`, `value`) VALUES
('habbicons.enabled', '1'),
('habbicons.asset.root', 'http://localhost/ngh/habbicons'),
('habbicons.asset.hash', 'dev'),
('habbicons.recents.max', '20'),
('habbicons.use.cooldown.seconds', '5')
ON DUPLICATE KEY UPDATE `value` = VALUES(`value`);

INSERT INTO `habbicon_collections`
(`id`, `name`, `enabled`, `price_credits`, `price_activity_points`, `activity_point_type`, `sort_order`)
VALUES
(5, 'Duckicons', 1, 0, 0, 0, 10),
(6, 'Duckicons 2', 1, 0, 0, 0, 20),
(7, 'Frankicons', 1, 0, 0, 0, 30)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `enabled` = VALUES(`enabled`),
  `price_credits` = VALUES(`price_credits`),
  `price_activity_points` = VALUES(`price_activity_points`),
  `activity_point_type` = VALUES(`activity_point_type`),
  `sort_order` = VALUES(`sort_order`);

INSERT INTO `habbicons`
(`id`, `collection_id`, `name`, `enabled`, `is_reward`, `price_credits`, `price_activity_points`, `activity_point_type`, `sort_order`)
VALUES
(28, 5, 'duck_duck', 1, 0, 0, 0, 0, 10),
(29, 5, 'duck_happy', 1, 0, 0, 0, 0, 20),
(30, 5, 'duck_sad', 1, 0, 0, 0, 0, 30),
(31, 5, 'duck_shock', 1, 0, 0, 0, 0, 40),
(32, 5, 'duck_think', 1, 0, 0, 0, 0, 50),
(33, 5, 'duck_nohear', 1, 0, 0, 0, 0, 60),
(34, 5, 'duck_nosee', 1, 0, 0, 0, 0, 70),
(35, 5, 'duck_nosay', 1, 0, 0, 0, 0, 80),
(36, 5, 'duck_angel', 1, 0, 0, 0, 0, 90),
(37, 5, 'duck_devil', 1, 0, 0, 0, 0, 100),
(38, 5, 'duck_spinning', 1, 1, 0, 0, 0, 1000),
(41, 6, 'duck_laughing', 1, 0, 0, 0, 0, 10),
(40, 6, 'duck_pleased', 1, 0, 0, 0, 0, 20),
(39, 6, 'duck_cool', 1, 0, 0, 0, 0, 30),
(42, 6, 'duck_grimace', 1, 0, 0, 0, 0, 40),
(43, 6, 'duck_devious', 1, 0, 0, 0, 0, 50),
(44, 6, 'duck_metal', 1, 0, 0, 0, 0, 60),
(45, 6, 'duck_pleading', 1, 0, 0, 0, 0, 70),
(46, 6, 'duck_silly', 1, 0, 0, 0, 0, 80),
(47, 6, 'duck_wink', 1, 0, 0, 0, 0, 90),
(48, 6, 'duck_party', 1, 0, 0, 0, 0, 100),
(49, 6, 'duck_love', 1, 1, 0, 0, 0, 1000),
(50, 7, 'frank_frank', 1, 0, 0, 0, 0, 10),
(51, 7, 'frank_smile', 1, 0, 0, 0, 0, 20),
(52, 7, 'frank_happy', 1, 0, 0, 0, 0, 30),
(53, 7, 'frank_sad', 1, 0, 0, 0, 0, 40),
(54, 7, 'frank_scared', 1, 0, 0, 0, 0, 50),
(55, 7, 'frank_surprised', 1, 0, 0, 0, 0, 60),
(56, 7, 'frank_thinking', 1, 0, 0, 0, 0, 70),
(57, 7, 'frank_silly', 1, 0, 0, 0, 0, 80),
(58, 7, 'frank_relief', 1, 0, 0, 0, 0, 90),
(59, 7, 'frank_wink', 1, 0, 0, 0, 0, 100),
(60, 7, 'frank_stareyes', 1, 1, 0, 0, 0, 1000)
ON DUPLICATE KEY UPDATE
  `collection_id` = VALUES(`collection_id`),
  `name` = VALUES(`name`),
  `enabled` = VALUES(`enabled`),
  `is_reward` = VALUES(`is_reward`),
  `price_credits` = VALUES(`price_credits`),
  `price_activity_points` = VALUES(`price_activity_points`),
  `activity_point_type` = VALUES(`activity_point_type`),
  `sort_order` = VALUES(`sort_order`);
