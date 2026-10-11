SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =====================================================
-- Update 4.0.8-beta to 4.0.9-beta
-- Additional public rooms: Floating Garden, Ballroom, The Park + The Infobus,
-- Theatredome, Sun Terrace, Rooftop Terrace + Rooftop Cafe, Space Cafe,
-- and the Cunning Fox Gamehall (entryhall + hallA-D).
-- Requires 4_0_6->4_0_7 (public_items table + 'Public Rooms' category) already applied.
-- Idempotent: re-running is a no-op. Auto-increment room ids; dependents resolved by model.
-- The Park ships the full Infobus bus queue (queue_tile2 lanes + parkfence solid walls + bus
-- interior). The Gamehall needs the converted hh_room_gamehall.swf deployed to gordon (it is
-- NOT in this SQL); the rooms below provide the heightmaps/furniture for entryhall + hallA-D.
-- =====================================================

CREATE TABLE IF NOT EXISTS `public_roomwalkways` (
    `room_id` INT NOT NULL,
    `to_id` INT NOT NULL,
    `coords_map` VARCHAR(255) NOT NULL,
    `door_position` VARCHAR(50) DEFAULT NULL,
    KEY `idx_walkway_room` (`room_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @official_root_public_cat_id = (
    SELECT CAST(`value` AS UNSIGNED) FROM `emulator_settings`
    WHERE `key` = 'hotel.navigator.officialroot.categoryid' LIMIT 1);
SET @official_root_public_cat_id = (
    SELECT `id` FROM `navigator_publiccats`
    WHERE `id` = @official_root_public_cat_id LIMIT 1);
SET @official_root_public_cat_id = IFNULL(@official_root_public_cat_id, (
    SELECT `id` FROM `navigator_publiccats` WHERE `name` = 'Public Rooms' ORDER BY `id` DESC LIMIT 1));

INSERT INTO `navigator_publiccats` (`name`, `image`, `image_url`, `visible`, `order_num`)
SELECT 'Public Rooms', '1', 'navigator/navi1.png', '1', 1
FROM DUAL
WHERE @official_root_public_cat_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM `navigator_publiccats` WHERE `name` = 'Public Rooms');

SET @official_root_public_cat_id = IFNULL(@official_root_public_cat_id, (
    SELECT `id` FROM `navigator_publiccats` WHERE `name` = 'Public Rooms' ORDER BY `id` DESC LIMIT 1));

INSERT INTO `emulator_settings` (`key`, `value`)
SELECT 'hotel.navigator.officialroot.categoryid', CAST(@official_root_public_cat_id AS CHAR)
FROM DUAL
WHERE @official_root_public_cat_id IS NOT NULL
ON DUPLICATE KEY UPDATE `value` = VALUES(`value`);

SET @public_room_owner_id = (
    SELECT CAST(`value` AS UNSIGNED) FROM `emulator_settings`
    WHERE `key` = 'hotel.publicroom.ownerid' LIMIT 1);
SET @public_room_owner_id = (
    SELECT `id` FROM `users` WHERE `id` = @public_room_owner_id LIMIT 1);
SET @public_room_owner_id = IFNULL(@public_room_owner_id, (
    SELECT `id` FROM `users` ORDER BY `id` LIMIT 1));
SET @public_room_owner_id = IFNULL(@public_room_owner_id, 0);
SET @public_room_owner_name = (
    SELECT `username` FROM `users` WHERE `id` = @public_room_owner_id LIMIT 1);
SET @public_room_owner_name = IFNULL(@public_room_owner_name, 'Hotel');

SET @public_room_category_id = (
    SELECT `id` FROM `navigator_flatcats` WHERE `id` = 15 LIMIT 1);
SET @public_room_category_id = IFNULL(@public_room_category_id, (
    SELECT `id` FROM `navigator_flatcats`
    WHERE `caption_save` = 'caption_save_personal'
    ORDER BY `id` LIMIT 1));
SET @public_room_category_id = IFNULL(@public_room_category_id, (
    SELECT `id` FROM `navigator_flatcats` ORDER BY `id` LIMIT 1));
SET @public_room_category_id = IFNULL(@public_room_category_id, 1);

DELETE FROM `public_items` WHERE `room_model` IN ('floatinggarden', 'ballroom', 'park_a', 'park_b', 'theater', 'sun_terrace', 'rooftop', 'rooftop_2', 'space_cafe', 'entryhall', 'hallA', 'hallB', 'hallC', 'hallD');
INSERT INTO `public_items`
    (`room_model`, `sprite`, `x`, `y`, `z`, `rotation`, `length`, `width`, `behaviour`, `top_height`, `has_dimensions`)
VALUES
('floatinggarden','stone',15,37,1,4,1,1,'can_sit_on_top,invisible',1,0),
('floatinggarden','stone',16,37,1,4,1,1,'can_sit_on_top,invisible',1,0),
('floatinggarden','stone',17,29,1,2,1,1,'can_sit_on_top,invisible',1,0),
('floatinggarden','stone',17,30,1,2,1,1,'can_sit_on_top,invisible',1,0),
('floatinggarden','stone',17,31,1,2,1,1,'can_sit_on_top,invisible',1,0),
('floatinggarden','stone',17,35,1,2,1,1,'can_sit_on_top,invisible',1,0),
('floatinggarden','stone',17,36,1,2,1,1,'can_sit_on_top,invisible',1,0),
('floatinggarden','stone',21,33,1,2,1,1,'can_sit_on_top,invisible',1,0),
('floatinggarden','stone',21,34,1,2,1,1,'can_sit_on_top,invisible',1,0),
('floatinggarden','stone',21,35,1,2,1,1,'can_sit_on_top,invisible',1,0),
('floatinggarden','stone',21,36,1,2,1,1,'can_sit_on_top,invisible',1,0),
('floatinggarden','stone',24,9,3,4,1,1,'can_sit_on_top',1,0),
('floatinggarden','stone',25,9,3,4,1,1,'can_sit_on_top',1,0),
('floatinggarden','stone',26,33,1,6,1,1,'can_sit_on_top,invisible',1,0),
('floatinggarden','stone',26,34,1,6,1,1,'can_sit_on_top,invisible',1,0),
('floatinggarden','stone',26,35,1,6,1,1,'can_sit_on_top,invisible',1,0),
('floatinggarden','stone',26,36,1,6,1,1,'can_sit_on_top,invisible',1,0),
('floatinggarden','stone',28,13,3,4,1,1,'can_sit_on_top',1,0),
('floatinggarden','stone',29,13,3,4,1,1,'can_sit_on_top',1,0),
('floatinggarden','floatbench1',17,18,3,2,1,1,'can_sit_on_top',1,0),
('floatinggarden','floatbench1',17,24,3,2,1,1,'can_sit_on_top',1,0),
('floatinggarden','floatbench1',19,18,3,6,1,1,'can_sit_on_top',1,0),
('floatinggarden','floatbench1',19,24,3,6,1,1,'can_sit_on_top',1,0),
('floatinggarden','floatbench1',21,18,3,2,1,1,'can_sit_on_top',1,0),
('floatinggarden','floatbench1',21,24,3,2,1,1,'can_sit_on_top',1,0),
('floatinggarden','floatbench1',23,18,3,6,1,1,'can_sit_on_top',1,0),
('floatinggarden','floatbench1',23,24,3,6,1,1,'can_sit_on_top',1,0),
('floatinggarden','floatbench1',28,17,1,4,1,1,'can_sit_on_top',1,0),
('floatinggarden','floatbench1',28,19,1,0,1,1,'can_sit_on_top',1,0),
('floatinggarden','floatbench1',28,23,1,4,1,1,'can_sit_on_top',1,0),
('floatinggarden','floatbench1',28,25,1,0,1,1,'can_sit_on_top',1,0),
('floatinggarden','floatbench2',17,17,3,2,1,1,'can_sit_on_top',1,0),
('floatinggarden','floatbench2',17,23,3,2,1,1,'can_sit_on_top',1,0),
('floatinggarden','floatbench2',19,17,3,6,1,1,'can_sit_on_top',1,0),
('floatinggarden','floatbench2',19,23,3,6,1,1,'can_sit_on_top',1,0),
('floatinggarden','floatbench2',21,17,3,2,1,1,'can_sit_on_top',1,0),
('floatinggarden','floatbench2',21,23,3,2,1,1,'can_sit_on_top',1,0),
('floatinggarden','floatbench2',23,17,3,6,1,1,'can_sit_on_top',1,0),
('floatinggarden','floatbench2',23,23,3,6,1,1,'can_sit_on_top',1,0),
('floatinggarden','floatbench2',27,17,1,4,1,1,'can_sit_on_top',1,0),
('floatinggarden','floatbench2',27,19,1,0,1,1,'can_sit_on_top',1,0),
('floatinggarden','floatbench2',27,23,1,4,1,1,'can_sit_on_top',1,0),
('floatinggarden','floatbench2',27,25,1,0,1,1,'can_sit_on_top',1,0),
('ballroom','broom_bench1',4,0,4,4,1,1,'can_sit_on_top',1,0),
('ballroom','broom_bench2',5,0,4,4,1,1,'can_sit_on_top',1,0),
('ballroom','broom_bench3',6,0,4,4,1,1,'can_sit_on_top',1,0),
('ballroom','broom_bench1',7,0,4,4,1,1,'can_sit_on_top',1,0),
('ballroom','broom_bench2',8,0,4,4,1,1,'can_sit_on_top',1,0),
('ballroom','broom_bench2',9,0,4,4,1,1,'can_sit_on_top',1,0),
('ballroom','broom_bench3',10,0,4,4,1,1,'can_sit_on_top',1,0),
('ballroom','broom_bench1',11,0,4,4,1,1,'can_sit_on_top',1,0),
('ballroom','broom_bench2',12,0,4,4,1,1,'can_sit_on_top',1,0),
('ballroom','broom_bench2',13,0,4,4,1,1,'can_sit_on_top',1,0),
('ballroom','broom_bench2',14,0,4,4,1,1,'can_sit_on_top',1,0),
('ballroom','broom_bench3',15,0,4,4,1,1,'can_sit_on_top',1,0),
('ballroom','broom_bench1',16,0,4,4,1,1,'can_sit_on_top',1,0),
('ballroom','broom_bench2',17,0,4,4,1,1,'can_sit_on_top',1,0),
('ballroom','broom_bench2',18,0,4,4,1,1,'can_sit_on_top',1,0),
('ballroom','broom_bench3',19,0,4,4,1,1,'can_sit_on_top',1,0),
('ballroom','broom_bench1',20,0,4,4,1,1,'can_sit_on_top',1,0),
('ballroom','broom_bench2',21,0,4,4,1,1,'can_sit_on_top',1,0),
('ballroom','broom_bench3',22,0,4,4,1,1,'can_sit_on_top',1,0),
('ballroom','broom_seatb1',7,3,2,4,1,1,'can_sit_on_top',1,0),
('ballroom','broom_seatb2',8,3,2,4,1,1,'can_sit_on_top',1,0),
('ballroom','broom_seatb3',9,3,2,4,1,1,'can_sit_on_top',1,0),
('ballroom','broom_seatb4',10,3,2,4,1,1,'can_sit_on_top',1,0),
('ballroom','broom_seat1',16,3,2,4,1,1,'can_sit_on_top',1,0),
('ballroom','broom_seat2',17,3,2,4,1,1,'can_sit_on_top',1,0),
('ballroom','broom_seat3',18,3,2,4,1,1,'can_sit_on_top',1,0),
('ballroom','broom_seat4',19,3,2,4,1,1,'can_sit_on_top',1,0),
('ballroom','broom_seatb1',7,6,0,4,1,1,'can_sit_on_top',1,0),
('ballroom','broom_seatb2',8,6,0,4,1,1,'can_sit_on_top',1,0),
('ballroom','broom_seatb3',9,6,0,4,1,1,'can_sit_on_top',1,0),
('ballroom','broom_seatb4',10,6,0,4,1,1,'can_sit_on_top',1,0),
('ballroom','broom_seat1',16,6,0,4,1,1,'can_sit_on_top',1,0),
('ballroom','broom_seat2',17,6,0,4,1,1,'can_sit_on_top',1,0),
('ballroom','broom_seat3',18,6,0,4,1,1,'can_sit_on_top',1,0),
('ballroom','broom_seat4',19,6,0,4,1,1,'can_sit_on_top',1,0),
('ballroom','broom_chair',19,10,0,4,1,1,'can_sit_on_top',1,0),
('ballroom','broom_chair',20,10,0,4,1,1,'can_sit_on_top',1,0),
('ballroom','broom_chair',21,10,0,4,1,1,'can_sit_on_top',1,0),
('ballroom','broom_table1',19,11,0,0,1,1,'solid',0.001,0),
('ballroom','broom_table2',20,11,0,0,1,1,'solid',0.001,0),
('ballroom','broom_table3',21,11,0,0,1,1,'solid',0.001,0),
('ballroom','broom_chair',19,12,0,0,1,1,'can_sit_on_top',1,0),
('ballroom','broom_chair',20,12,0,0,1,1,'can_sit_on_top',1,0),
('ballroom','broom_chair',21,12,0,0,1,1,'can_sit_on_top',1,0),
('ballroom','broom_chair',19,15,0,4,1,1,'can_sit_on_top',1,0),
('ballroom','broom_chair',20,15,0,4,1,1,'can_sit_on_top',1,0),
('ballroom','broom_chair',21,15,0,4,1,1,'can_sit_on_top',1,0),
('ballroom','broom_table1',19,16,0,0,1,1,'solid',0.001,0),
('ballroom','broom_table2',20,16,0,0,1,1,'solid',0.001,0),
('ballroom','broom_table3',21,16,0,0,1,1,'solid',0.001,0),
('ballroom','broom_chair',19,17,0,0,1,1,'can_sit_on_top',1,0),
('ballroom','broom_chair',20,17,0,0,1,1,'can_sit_on_top',1,0),
('ballroom','broom_chair',21,17,0,0,1,1,'can_sit_on_top',1,0),
('ballroom','broom_stool',9,20,1,2,1,1,'can_sit_on_top',1,0),
('ballroom','broom_stool',9,21,1,2,1,1,'can_sit_on_top',1,0),
('ballroom','broom_stool',9,22,1,2,1,1,'can_sit_on_top',1,0),
('ballroom','broom_stool',9,23,1,2,1,1,'can_sit_on_top',1,0),
('park_a','bench2',8,9,0,4,1,1,'can_sit_on_top',1,0),
('park_a','bench',9,9,0,4,1,1,'can_sit_on_top',1,0),
('park_a','bench2',7,11,0,2,1,1,'can_sit_on_top',1,0),
('park_a','bench',7,12,0,2,1,1,'can_sit_on_top',1,0),
('park_a','bench2',35,16,0,2,1,1,'can_sit_on_top',1,0),
('park_a','bench2',37,16,0,4,1,1,'can_sit_on_top',1,0),
('park_a','bench',38,16,0,4,1,1,'can_sit_on_top',1,0),
('park_a','bench',35,17,0,2,1,1,'can_sit_on_top',1,0),
('park_a','bench2',27,18,0,4,1,1,'can_sit_on_top',1,0),
('park_a','bench',28,18,0,4,1,1,'can_sit_on_top',1,0),
('park_a','bench2',35,18,0,2,1,1,'can_sit_on_top',1,0),
('park_a','bench2',25,19,0,2,1,1,'can_sit_on_top',1,0),
('park_a','bench',35,19,0,2,1,1,'can_sit_on_top',1,0),
('park_a','bench',25,20,0,2,1,1,'can_sit_on_top',1,0),
('park_a','bench2',25,29,0,4,1,1,'can_sit_on_top',1,0),
('park_a','bench',26,29,0,4,1,1,'can_sit_on_top',1,0),
('park_a','bench2',23,30,0,2,1,1,'can_sit_on_top',1,0),
('park_a','bench',23,31,0,2,1,1,'can_sit_on_top',1,0),
('park_a','queue_tile2',18,7,0,4,1,1,'extra_parameter,can_stand_on_top',0.001,0),
('park_a','queue_tile2',18,9,0,4,1,1,'extra_parameter,can_stand_on_top',0.001,0),
('park_a','queue_tile2',18,10,0,2,1,1,'extra_parameter,can_stand_on_top',0.001,0),
('park_a','queue_tile2',19,10,0,2,1,1,'extra_parameter,can_stand_on_top',0.001,0),
('park_a','queue_tile2',20,7,0,2,1,1,'extra_parameter,can_stand_on_top',0.001,0),
('park_a','queue_tile2',20,8,0,0,1,1,'extra_parameter,can_stand_on_top',0.001,0),
('park_a','queue_tile2',20,9,0,0,1,1,'extra_parameter,can_stand_on_top',0.001,0),
('park_a','queue_tile2',20,10,0,0,1,1,'extra_parameter,can_stand_on_top',0.001,0),
('park_a','queue_tile2',21,7,0,2,1,1,'extra_parameter,can_stand_on_top',0.001,0),
('park_a','queue_tile2',22,7,0,4,1,1,'extra_parameter,can_stand_on_top',0.001,0),
('park_a','queue_tile2',22,8,0,4,1,1,'extra_parameter,can_stand_on_top',0.001,0),
('park_a','queue_tile2',22,9,0,4,1,1,'extra_parameter,can_stand_on_top',0.001,0),
('park_a','queue_tile2',22,10,0,2,1,1,'extra_parameter,can_stand_on_top',0.001,0),
('park_a','queue_tile2',23,10,0,2,1,1,'extra_parameter,can_stand_on_top',0.001,0),
('park_a','queue_tile2',24,7,0,2,1,1,'extra_parameter,can_stand_on_top',0.001,0),
('park_a','queue_tile2',24,8,0,0,1,1,'extra_parameter,can_stand_on_top',0.001,0),
('park_a','queue_tile2',24,9,0,0,1,1,'extra_parameter,can_stand_on_top',0.001,0),
('park_a','queue_tile2',24,10,0,0,1,1,'extra_parameter,can_stand_on_top',0.001,0),
('park_a','queue_tile2',25,7,0,2,1,1,'extra_parameter,can_stand_on_top',0.001,0),
('park_a','queue_tile2',26,7,0,4,1,1,'extra_parameter,can_stand_on_top',0.001,0),
('park_a','queue_tile2',26,8,0,4,1,1,'extra_parameter,can_stand_on_top',0.001,0),
('park_a','queue_tile2',26,9,0,4,1,1,'extra_parameter,can_stand_on_top',0.001,0),
('park_a','queue_tile2',26,10,0,2,1,1,'extra_parameter,can_stand_on_top',0.001,0),
('park_a','queue_tile2',27,10,0,2,1,1,'extra_parameter,can_stand_on_top',0.001,0),
('park_a','queue_tile2',28,6,0,0,1,1,'extra_parameter,can_stand_on_top',0.001,0),
('park_a','queue_tile2',28,7,0,0,1,1,'extra_parameter,can_stand_on_top',0.001,0),
('park_a','queue_tile2',28,8,0,0,1,1,'extra_parameter,can_stand_on_top',0.001,0),
('park_a','queue_tile2',28,9,0,0,1,1,'extra_parameter,can_stand_on_top',0.001,0),
('park_a','queue_tile2',28,10,0,0,1,1,'extra_parameter,can_stand_on_top',0.001,0),
('park_a','queue_tile2',18,8,0,4,1,1,'extra_parameter,can_stand_on_top',0.001,0),
('park_a','parkfence1',17,11,0,4,1,1,'solid',1,0),
('park_a','parkfence1',19,6,0,0,1,1,'solid',1,0),
('park_a','parkfence1',27,6,0,2,1,1,'solid',1,0),
('park_a','parkfence1',29,11,0,6,1,1,'solid',1,0),
('park_a','parkfence2',17,7,0,0,1,1,'solid',1,0),
('park_a','parkfence2',19,9,0,6,1,1,'solid',1,0),
('park_a','parkfence2',21,8,0,0,1,1,'solid',1,0),
('park_a','parkfence2',21,11,0,2,1,1,'solid',1,0),
('park_a','parkfence2',23,6,0,4,1,1,'solid',1,0),
('park_a','parkfence2',23,9,0,6,1,1,'solid',1,0),
('park_a','parkfence2',25,8,0,0,1,1,'solid',1,0),
('park_a','parkfence2',25,11,0,2,1,1,'solid',1,0),
('park_a','parkfence2',27,5,0,0,1,1,'solid',1,0),
('park_a','parkfence2',27,9,0,6,1,1,'solid',1,0),
('park_a','parkfence2',29,5,0,0,1,1,'solid',1,0),
('park_a','parkfence3',17,8,0,2,1,1,'solid',1,0),
('park_a','parkfence3',17,9,0,2,1,1,'solid',1,0),
('park_a','parkfence3',17,10,0,2,1,1,'solid',1,0),
('park_a','parkfence3',18,11,0,4,1,1,'solid',1,0),
('park_a','parkfence3',19,7,0,2,1,1,'solid',1,0),
('park_a','parkfence3',19,8,0,2,1,1,'solid',1,0),
('park_a','parkfence3',19,11,0,4,1,1,'solid',1,0),
('park_a','parkfence3',20,6,0,4,1,1,'solid',1,0),
('park_a','parkfence3',20,11,0,4,1,1,'solid',1,0),
('park_a','parkfence3',21,6,0,4,1,1,'solid',1,0),
('park_a','parkfence3',21,9,0,2,1,1,'solid',1,0),
('park_a','parkfence3',21,10,0,2,1,1,'solid',1,0),
('park_a','parkfence3',22,6,0,4,1,1,'solid',1,0),
('park_a','parkfence3',22,11,0,4,1,1,'solid',1,0),
('park_a','parkfence3',23,7,0,2,1,1,'solid',1,0),
('park_a','parkfence3',23,8,0,2,1,1,'solid',1,0),
('park_a','parkfence3',23,11,0,4,1,1,'solid',1,0),
('park_a','parkfence3',24,6,0,4,1,1,'solid',1,0),
('park_a','parkfence3',24,11,0,4,1,1,'solid',1,0),
('park_a','parkfence3',25,6,0,4,1,1,'solid',1,0),
('park_a','parkfence3',25,9,0,2,1,1,'solid',1,0),
('park_a','parkfence3',25,10,0,2,1,1,'solid',1,0),
('park_a','parkfence3',26,6,0,4,1,1,'solid',1,0),
('park_a','parkfence3',26,11,0,4,1,1,'solid',1,0),
('park_a','parkfence3',27,7,0,2,1,1,'solid',1,0),
('park_a','parkfence3',27,8,0,2,1,1,'solid',1,0),
('park_a','parkfence3',27,11,0,4,1,1,'solid',1,0),
('park_a','parkfence3',28,11,0,4,1,1,'solid',1,0),
('park_a','parkfence3',29,6,0,2,1,1,'solid',1,0),
('park_a','parkfence3',29,7,0,2,1,1,'solid',1,0),
('park_a','parkfence3',29,8,0,2,1,1,'solid',1,0),
('park_a','parkfence3',29,9,0,2,1,1,'solid',1,0),
('park_a','parkfence3',29,10,0,2,1,1,'solid',1,0),
('park_b','cornerchair2',0,0,0,4,1,1,'solid',1,0),
('park_b','cornerchair1',1,0,0,4,1,1,'can_sit_on_top',1,0),
('park_b','chair1',2,0,0,4,1,1,'can_sit_on_top',1,0),
('park_b','chair1',3,0,0,4,1,1,'can_sit_on_top',1,0),
('park_b','table1',5,0,0,4,1,1,'solid',0.001,0),
('park_b','chair1line',6,0,0,4,1,1,'can_sit_on_top',1,0),
('park_b','chair1',7,0,0,4,1,1,'can_sit_on_top',1,0),
('park_b','chair1frontend',8,0,0,4,1,1,'can_sit_on_top',1,0),
('park_b','hububar',10,0,0,4,1,1,'solid',0.001,0),
('park_b','cornerchair1',0,1,0,2,1,1,'can_sit_on_top',1,0),
('park_b','chair1',0,2,0,2,1,1,'can_sit_on_top',1,0),
('park_b','chair1',0,3,0,2,1,1,'can_sit_on_top',1,0),
('park_b','chair1',0,4,0,2,1,1,'can_sit_on_top',1,0),
('park_b','chair1frontend',0,5,0,2,1,1,'can_sit_on_top',1,0),
('park_b','table2',3,5,0,4,1,1,'solid',0.001,0),
('park_b','modchair',5,5,0,0,1,1,'can_sit_on_top',1,0),
('park_b','table2',8,5,0,4,1,1,'solid',0.001,0),
('theater','mic',11,10,1,0,1,1,'solid',0.001,0),
('theater','thchair2',2,11,4,2,1,1,'can_sit_on_top',1,0),
('theater','thchair2',2,12,4,2,1,1,'can_sit_on_top',1,0),
('theater','thchair2',2,15,4,2,1,1,'can_sit_on_top',1,0),
('theater','thchair1',6,15,0,0,1,1,'can_sit_on_top',1,0),
('theater','thchair1',7,15,0,0,1,1,'can_sit_on_top',1,0),
('theater','thchair1',8,15,0,0,1,1,'can_sit_on_top',1,0),
('theater','thchair1',9,15,0,0,1,1,'can_sit_on_top',1,0),
('theater','thchair1',10,15,0,0,1,1,'can_sit_on_top',1,0),
('theater','thchair1',12,15,0,0,1,1,'can_sit_on_top',1,0),
('theater','thchair1',13,15,0,0,1,1,'can_sit_on_top',1,0),
('theater','thchair1',14,15,0,0,1,1,'can_sit_on_top',1,0),
('theater','thchair1',15,15,0,0,1,1,'can_sit_on_top',1,0),
('theater','thchair1',16,15,0,0,1,1,'can_sit_on_top',1,0),
('theater','thchair2',2,16,4,2,1,1,'can_sit_on_top',1,0),
('theater','thchair1',6,20,1,0,1,1,'can_sit_on_top',1,0),
('theater','thchair1',7,20,1,0,1,1,'can_sit_on_top',1,0),
('theater','thchair1',8,20,1,0,1,1,'can_sit_on_top',1,0),
('theater','thchair1',9,20,1,0,1,1,'can_sit_on_top',1,0),
('theater','thchair1',10,20,1,0,1,1,'can_sit_on_top',1,0),
('theater','thchair1',12,20,1,0,1,1,'can_sit_on_top',1,0),
('theater','thchair1',13,20,1,0,1,1,'can_sit_on_top',1,0),
('theater','thchair1',14,20,1,0,1,1,'can_sit_on_top',1,0),
('theater','thchair1',15,20,1,0,1,1,'can_sit_on_top',1,0),
('theater','thchair1',16,20,1,0,1,1,'can_sit_on_top',1,0),
('theater','thchair1',6,23,1,0,1,1,'can_sit_on_top',1,0),
('theater','thchair1',7,23,1,0,1,1,'can_sit_on_top',1,0),
('theater','thchair1',8,23,1,0,1,1,'can_sit_on_top',1,0),
('theater','thchair1',9,23,1,0,1,1,'can_sit_on_top',1,0),
('theater','thchair1',10,23,1,0,1,1,'can_sit_on_top',1,0),
('theater','thchair1',12,23,1,0,1,1,'can_sit_on_top',1,0),
('theater','thchair1',13,23,1,0,1,1,'can_sit_on_top',1,0),
('theater','thchair1',14,23,1,0,1,1,'can_sit_on_top',1,0),
('theater','thchair1',15,23,1,0,1,1,'can_sit_on_top',1,0),
('theater','thchair1',16,23,1,0,1,1,'can_sit_on_top',1,0),
('theater','thchair1',6,26,1,0,1,1,'can_sit_on_top',1,0),
('theater','thchair1',7,26,1,0,1,1,'can_sit_on_top',1,0),
('theater','thchair1',8,26,1,0,1,1,'can_sit_on_top',1,0),
('theater','thchair1',9,26,1,0,1,1,'can_sit_on_top',1,0),
('theater','thchair1',10,26,1,0,1,1,'can_sit_on_top',1,0),
('theater','thchair1',12,26,1,0,1,1,'can_sit_on_top',1,0),
('theater','thchair1',13,26,1,0,1,1,'can_sit_on_top',1,0),
('theater','thchair1',14,26,1,0,1,1,'can_sit_on_top',1,0),
('theater','thchair1',15,26,1,0,1,1,'can_sit_on_top',1,0),
('theater','thchair1',16,26,1,0,1,1,'can_sit_on_top',1,0),
('sun_terrace','sun_chair',16,11,0,2,1,1,'can_sit_on_top',1,0),
('sun_terrace','sun_chair',20,11,0,2,1,1,'can_sit_on_top',1,0),
('sun_terrace','sun_chair',16,12,0,2,1,1,'can_sit_on_top',1,0),
('sun_terrace','sun_chair',20,12,0,2,1,1,'can_sit_on_top',1,0),
('sun_terrace','blusun_chair',10,13,0,4,1,1,'can_sit_on_top',1,0),
('sun_terrace','sun_chair',16,13,0,2,1,1,'can_sit_on_top',1,0),
('sun_terrace','sun_chair',20,13,0,2,1,1,'can_sit_on_top',1,0),
('sun_terrace','sun_table',10,14,0,0,1,1,'solid',0.001,0),
('sun_terrace','sun_chair',20,14,0,2,1,1,'can_sit_on_top',1,0),
('sun_terrace','blusun_chair',10,15,0,0,1,1,'can_sit_on_top',1,0),
('sun_terrace','sun_chair',1,18,8,2,1,1,'can_sit_on_top',1,0),
('sun_terrace','sun_chair',4,18,6,2,1,1,'can_sit_on_top',1,0),
('sun_terrace','blusun_chair',11,18,0,4,1,1,'can_sit_on_top',1,0),
('sun_terrace','blusun_chair',10,19,0,2,1,1,'can_sit_on_top',1,0),
('sun_terrace','sun_table',11,19,0,0,1,1,'solid',0.001,0),
('sun_terrace','blusun_chair',12,19,0,6,1,1,'can_sit_on_top',1,0),
('sun_terrace','sun_chair',18,19,0,2,1,1,'can_sit_on_top',1,0),
('sun_terrace','sun_chair',22,19,0,2,1,1,'can_sit_on_top',1,0),
('sun_terrace','blusun_chair',11,20,0,0,1,1,'can_sit_on_top',1,0),
('sun_terrace','sun_chair',18,20,0,2,1,1,'can_sit_on_top',1,0),
('sun_terrace','sun_chair',22,20,0,2,1,1,'can_sit_on_top',1,0),
('sun_terrace','sun_chair',4,21,6,2,1,1,'can_sit_on_top',1,0),
('sun_terrace','sun_chair',6,21,4,2,1,1,'can_sit_on_top',1,0),
('sun_terrace','sun_chair',18,21,0,2,1,1,'can_sit_on_top',1,0),
('sun_terrace','sun_chair',22,21,0,2,1,1,'can_sit_on_top',1,0),
('sun_terrace','blusun_chair',2,22,9,4,1,1,'can_sit_on_top',1,0),
('sun_terrace','sun_chair',22,22,0,2,1,1,'can_sit_on_top',1,0),
('sun_terrace','blusun_chair',1,23,9,2,1,1,'can_sit_on_top',1,0),
('sun_terrace','sun_table2',2,23,9,0,1,1,'solid',0.001,0),
('sun_terrace','blusun_chair',2,24,9,0,1,1,'can_sit_on_top',1,0),
('sun_terrace','sun_chair',11,25,2,4,1,1,'can_sit_on_top',1,0),
('sun_terrace','sun_chair',12,25,2,4,1,1,'can_sit_on_top',1,0),
('sun_terrace','sun_chair',13,25,2,4,1,1,'can_sit_on_top',1,0),
('sun_terrace','sun_table',14,25,2,0,1,1,'solid',0.001,0),
('sun_terrace','sun_chair',15,25,2,4,1,1,'can_sit_on_top',1,0),
('sun_terrace','sun_chair',16,25,2,4,1,1,'can_sit_on_top',1,0),
('sun_terrace','sun_chair',17,25,2,4,1,1,'can_sit_on_top',1,0),
('sun_terrace','sun_chair',13,29,2,2,1,1,'can_sit_on_top',1,0),
('sun_terrace','sun_chair',15,29,2,2,1,1,'can_sit_on_top',1,0),
('sun_terrace','sun_chair',17,29,2,2,1,1,'can_sit_on_top',1,0),
('sun_terrace','sun_chair',13,30,2,2,1,1,'can_sit_on_top',1,0),
('sun_terrace','sun_chair',15,30,2,2,1,1,'can_sit_on_top',1,0),
('sun_terrace','sun_chair',17,30,2,2,1,1,'can_sit_on_top',1,0),
('rooftop','rooftop_emptytable',0,10,4,0,1,1,'solid',0.001,0),
('rooftop','rooftop_flatcurb',1,13,4,2,1,1,'can_sit_on_top',1,0),
('rooftop','rooftop_flatcurb',13,13,4,6,1,1,'can_sit_on_top',1,0),
('rooftop','rooftop_flatcurb',16,1,4,4,1,1,'can_sit_on_top',1,0),
('rooftop','rooftop_flatcurb',16,4,4,0,1,1,'can_sit_on_top',1,0),
('rooftop','rooftop_flatcurb',6,13,4,6,1,1,'can_sit_on_top',1,0),
('rooftop','rooftop_flatcurb',8,13,4,2,1,1,'can_sit_on_top',1,0),
('rooftop','rooftop_flatcurb2',17,1,4,4,1,1,'can_sit_on_top',1,0),
('rooftop','rooftop_flatcurb2',17,4,4,0,1,1,'can_sit_on_top',1,0),
('rooftop','rooftop_flatcurb3',1,14,4,2,1,1,'can_sit_on_top',1,0),
('rooftop','rooftop_flatcurb3',1,15,4,2,1,1,'can_sit_on_top',1,0),
('rooftop','rooftop_flatcurb3',1,16,4,2,1,1,'can_sit_on_top',1,0),
('rooftop','rooftop_flatcurb3',10,17,4,0,1,1,'can_sit_on_top',1,0),
('rooftop','rooftop_flatcurb3',11,17,4,0,1,1,'can_sit_on_top',1,0),
('rooftop','rooftop_flatcurb3',12,17,4,0,1,1,'can_sit_on_top',1,0),
('rooftop','rooftop_flatcurb3',13,14,4,6,1,1,'can_sit_on_top',1,0),
('rooftop','rooftop_flatcurb3',13,15,4,6,1,1,'can_sit_on_top',1,0),
('rooftop','rooftop_flatcurb3',13,16,4,6,1,1,'can_sit_on_top',1,0),
('rooftop','rooftop_flatcurb3',2,17,4,0,1,1,'can_sit_on_top',1,0),
('rooftop','rooftop_flatcurb3',3,17,4,0,1,1,'can_sit_on_top',1,0),
('rooftop','rooftop_flatcurb3',4,17,4,0,1,1,'can_sit_on_top',1,0),
('rooftop','rooftop_flatcurb3',5,17,4,0,1,1,'can_sit_on_top',1,0),
('rooftop','rooftop_flatcurb3',6,14,4,6,1,1,'can_sit_on_top',1,0),
('rooftop','rooftop_flatcurb3',6,15,4,6,1,1,'can_sit_on_top',1,0),
('rooftop','rooftop_flatcurb3',6,16,4,6,1,1,'can_sit_on_top',1,0),
('rooftop','rooftop_flatcurb3',8,14,4,2,1,1,'can_sit_on_top',1,0),
('rooftop','rooftop_flatcurb3',8,15,4,2,1,1,'can_sit_on_top',1,0),
('rooftop','rooftop_flatcurb3',8,16,4,2,1,1,'can_sit_on_top',1,0),
('rooftop','rooftop_flatcurb3',9,17,4,0,1,1,'can_sit_on_top',1,0),
('rooftop','rooftop_flatcurb4',1,17,4,0,1,1,'can_sit_on_top',1,0),
('rooftop','rooftop_flatcurb4',8,17,4,0,1,1,'can_sit_on_top',1,0),
('rooftop','rooftop_flatcurb5',13,17,4,0,1,1,'can_sit_on_top',1,0),
('rooftop','rooftop_flatcurb5',6,17,4,0,1,1,'can_sit_on_top',1,0),
('rooftop','rooftop_minichair',0,1,4,4,1,1,'can_sit_on_top',1,0),
('rooftop','rooftop_minichair',0,11,4,0,1,1,'can_sit_on_top',1,0),
('rooftop','rooftop_minichair',0,3,4,0,1,1,'can_sit_on_top',1,0),
('rooftop','rooftop_minichair',0,9,4,4,1,1,'can_sit_on_top',1,0),
('rooftop','rooftop_minichair',1,2,4,6,1,1,'can_sit_on_top',1,0),
('rooftop','rooftop_minichair',1,7,4,2,1,1,'can_sit_on_top',1,0),
('rooftop','rooftop_minichair',2,6,4,4,1,1,'can_sit_on_top',1,0),
('rooftop','rooftop_minichair',2,8,4,0,1,1,'can_sit_on_top',1,0),
('rooftop','rooftop_minichair',3,7,4,6,1,1,'can_sit_on_top',1,0),
('rooftop','rooftop_rodtable',0,2,4,0,1,1,'solid',0.001,0),
('rooftop','rooftop_rodtable',2,7,4,0,1,1,'solid',0.001,0),
('rooftop_2','rooftop_bigchair',8,0,0,4,1,1,'can_sit_on_top',1,0),
('rooftop_2','rooftop_bigtable',8,1,0,0,1,1,'solid',0.001,0),
('rooftop_2','rooftop_sofab',0,2,0,4,1,1,'can_sit_on_top',1,0),
('rooftop_2','rooftop_sofa',1,2,0,4,1,1,'can_sit_on_top',1,0),
('rooftop_2','rooftop_bigchair',8,2,0,0,1,1,'can_sit_on_top',1,0),
('rooftop_2','rooftop_bigchair',8,3,0,4,1,1,'can_sit_on_top',1,0),
('rooftop_2','rooftop_bigtable',8,4,0,0,1,1,'solid',0.001,0),
('rooftop_2','rooftop_sofab',0,5,0,0,1,1,'can_sit_on_top',1,0),
('rooftop_2','rooftop_sofa',1,5,0,0,1,1,'can_sit_on_top',1,0),
('rooftop_2','rooftop_bigchair',8,5,0,0,1,1,'can_sit_on_top',1,0),
('rooftop_2','rooftop_sofab',0,6,0,4,1,1,'can_sit_on_top',1,0),
('rooftop_2','rooftop_sofa',1,6,0,4,1,1,'can_sit_on_top',1,0),
('rooftop_2','rooftop_sofab',7,6,0,4,1,1,'can_sit_on_top',1,0),
('rooftop_2','rooftop_sofa',8,6,0,4,1,1,'can_sit_on_top',1,0),
('rooftop_2','rooftop_sofab',0,9,0,0,1,1,'can_sit_on_top',1,0),
('rooftop_2','rooftop_sofa',1,9,0,0,1,1,'can_sit_on_top',1,0),
('rooftop_2','rooftop_sofab',7,9,0,0,1,1,'can_sit_on_top',1,0),
('rooftop_2','rooftop_sofa',8,9,0,0,1,1,'can_sit_on_top',1,0),
('space_cafe','bigtablea',1,10,2,0,1,1,'solid',0.001,0),
('space_cafe','bigtablea',7,10,2,0,1,1,'solid',0.001,0),
('space_cafe','bigtableb',0,10,2,0,1,1,'solid',0.001,0),
('space_cafe','bigtableb',6,10,2,0,1,1,'solid',0.001,0),
('space_cafe','bigtablec',1,9,2,0,1,1,'solid',0.001,0),
('space_cafe','bigtablec',7,9,2,0,1,1,'solid',0.001,0),
('space_cafe','bigtabled',0,9,2,0,1,1,'solid',0.001,0),
('space_cafe','bigtabled',6,9,2,0,1,1,'solid',0.001,0),
('space_cafe','bluesofaa',1,11,2,0,1,1,'can_sit_on_top',1,0),
('space_cafe','bluesofaa',1,8,2,4,1,1,'can_sit_on_top',1,0),
('space_cafe','bluesofaa',14,22,0,4,1,1,'can_sit_on_top',1,0),
('space_cafe','bluesofaa',14,24,0,0,1,1,'can_sit_on_top',1,0),
('space_cafe','bluesofaa',2,10,2,6,1,1,'can_sit_on_top',1,0),
('space_cafe','bluesofab',0,11,2,0,1,1,'can_sit_on_top',1,0),
('space_cafe','bluesofab',0,8,2,4,1,1,'can_sit_on_top',1,0),
('space_cafe','bluesofab',13,22,0,4,1,1,'can_sit_on_top',1,0),
('space_cafe','bluesofab',13,24,0,0,1,1,'can_sit_on_top',1,0),
('space_cafe','bluesofab',2,9,2,6,1,1,'can_sit_on_top',1,0),
('space_cafe','midtablea',14,23,0,0,1,1,'solid',0.001,0),
('space_cafe','midtablea',19,14,1,6,1,1,'solid',0.001,0),
('space_cafe','midtableb',13,23,0,0,1,1,'solid',0.001,0),
('space_cafe','midtableb',19,13,1,6,1,1,'solid',0.001,0),
('space_cafe','redsofaa',10,22,0,2,1,1,'can_sit_on_top',1,0),
('space_cafe','redsofaa',10,25,0,2,1,1,'can_sit_on_top',1,0),
('space_cafe','redsofaa',18,14,1,2,1,1,'can_sit_on_top',1,0),
('space_cafe','redsofaa',20,14,1,6,1,1,'can_sit_on_top',1,0),
('space_cafe','redsofaa',5,10,2,2,1,1,'can_sit_on_top',1,0),
('space_cafe','redsofaa',7,11,2,0,1,1,'can_sit_on_top',1,0),
('space_cafe','redsofaa',7,8,2,4,1,1,'can_sit_on_top',1,0),
('space_cafe','redsofab',10,21,0,2,1,1,'can_sit_on_top',1,0),
('space_cafe','redsofab',10,24,0,2,1,1,'can_sit_on_top',1,0),
('space_cafe','redsofab',18,13,1,2,1,1,'can_sit_on_top',1,0),
('space_cafe','redsofab',20,13,1,6,1,1,'can_sit_on_top',1,0),
('space_cafe','redsofab',5,9,2,2,1,1,'can_sit_on_top',1,0),
('space_cafe','redsofab',6,11,2,0,1,1,'can_sit_on_top',1,0),
('space_cafe','redsofab',6,8,2,4,1,1,'can_sit_on_top',1,0),
('space_cafe','smalltable',10,23,0,0,1,1,'solid',0.001,0),
('space_cafe','smalltable',17,6,3,0,1,1,'solid',0.001,0),
('space_cafe','smalltable',19,2,3,0,1,1,'solid',0.001,0),
('space_cafe','smalltable',21,6,3,0,1,1,'solid',0.001,0),
('space_cafe','smalltable',23,2,3,0,1,1,'solid',0.001,0),
('space_cafe','smalltable',3,16,3,0,1,1,'solid',0.001,0),
('space_cafe','space_stool',19,1,3,4,1,1,'can_sit_on_top',1,0),
('space_cafe','space_stool',17,5,3,4,1,1,'can_sit_on_top',1,0),
('space_cafe','space_stool',17,7,3,0,1,1,'can_sit_on_top',1,0),
('space_cafe','space_stool',18,2,3,2,1,1,'can_sit_on_top',1,0),
('space_cafe','space_stool',18,6,3,6,1,1,'can_sit_on_top',1,0),
('space_cafe','space_stool',19,3,3,0,1,1,'can_sit_on_top',1,0),
('space_cafe','space_stool',20,2,3,6,1,1,'can_sit_on_top',1,0),
('space_cafe','space_stool',20,6,3,2,1,1,'can_sit_on_top',1,0),
('space_cafe','space_stool',21,5,3,4,1,1,'can_sit_on_top',1,0),
('space_cafe','space_stool',21,7,3,0,1,1,'can_sit_on_top',1,0),
('space_cafe','space_stool',22,2,3,2,1,1,'can_sit_on_top',1,0),
('space_cafe','space_stool',22,6,3,6,1,1,'can_sit_on_top',1,0),
('space_cafe','space_stool',23,1,3,4,1,1,'can_sit_on_top',1,0),
('space_cafe','space_stool',23,3,3,0,1,1,'can_sit_on_top',1,0),
('space_cafe','space_stool',3,15,3,2,1,1,'can_sit_on_top',1,0),
('space_cafe','space_stool',3,17,3,2,1,1,'can_sit_on_top',1,0),
('entryhall','gl_sofatable',7,16,1,0,1,1,'solid',1,0),
('entryhall','gl_sofatable',5,1,1,4,1,1,'solid',1,0),
('entryhall','gl_sofatable',16,1,1,4,1,1,'solid',1,0),
('entryhall','gl_sofatable',14,14,1,4,1,1,'solid',1,0),
('entryhall','gl_sofatable',1,19,1,0,1,1,'solid',1,0),
('entryhall','gl_yukka',15,6,1,0,1,1,'solid',0.001,0),
('entryhall','gl_yukka',7,7,1,0,1,1,'solid',0.001,0),
('entryhall','gl_chair',6,1,1,4,1,1,'can_sit_on_top',1,0),
('entryhall','gl_chair',4,1,1,4,1,1,'can_sit_on_top',1,0),
('entryhall','gl_chair',17,1,1,4,1,1,'can_sit_on_top',1,0),
('entryhall','gl_chair',18,1,1,4,1,1,'can_sit_on_top',1,0),
('entryhall','gl_chair',14,11,1,2,1,1,'can_sit_on_top',1,0),
('entryhall','gl_chair',14,12,1,2,1,1,'can_sit_on_top',1,0),
('entryhall','gl_chair',14,13,1,2,1,1,'can_sit_on_top',1,0),
('entryhall','gl_chair',13,14,1,4,1,1,'can_sit_on_top',1,0),
('entryhall','gl_chair',12,14,1,4,1,1,'can_sit_on_top',1,0),
('entryhall','gl_chair',11,14,1,4,1,1,'can_sit_on_top',1,0),
('entryhall','gl_sofaa',1,16,1,2,1,1,'can_sit_on_top',1,0),
('entryhall','gl_sofab',1,17,1,2,1,1,'can_sit_on_top',1,0),
('entryhall','gl_sofac',1,18,1,2,1,1,'can_sit_on_top',1,0),
('entryhall','gl_sofaa',1,10,1,2,1,1,'can_sit_on_top',1,0),
('entryhall','gl_sofab',1,11,1,2,1,1,'can_sit_on_top',1,0),
('entryhall','gl_sofac',1,12,1,2,1,1,'can_sit_on_top',1,0),
('entryhall','gl_sofaa',7,13,1,2,1,1,'can_sit_on_top',1,0),
('entryhall','gl_sofab',7,14,1,2,1,1,'can_sit_on_top',1,0),
('entryhall','gl_sofac',7,15,1,2,1,1,'can_sit_on_top',1,0),
('entryhall','gl_sofaa',7,8,1,2,1,1,'can_sit_on_top',1,0),
('entryhall','gl_sofab',7,9,1,2,1,1,'can_sit_on_top',1,0),
('entryhall','gl_sofac',7,10,1,2,1,1,'can_sit_on_top',1,0),
('entryhall','gl_sofaa',8,7,1,4,1,1,'can_sit_on_top',1,0),
('entryhall','gl_sofab',9,7,1,4,1,1,'can_sit_on_top',1,0),
('entryhall','gl_sofac',10,7,1,4,1,1,'can_sit_on_top',1,0),
('entryhall','gl_sofaa',13,7,1,4,1,1,'can_sit_on_top',1,0),
('entryhall','gl_sofab',14,7,1,4,1,1,'can_sit_on_top',1,0),
('entryhall','gl_sofac',15,7,1,4,1,1,'can_sit_on_top',1,0),
('entryhall','gl_table',7,12,1,6,1,1,'solid',0.001,0),
('entryhall','gl_tablea',7,11,1,6,1,1,'solid',0.001,0),
('entryhall','gl_tablea',11,7,1,0,1,1,'solid',0.001,0),
('entryhall','gl_table',12,7,1,0,1,1,'solid',0.001,0),
('hallA','gamehall_chair_wood',15,4,1,6,1,1,'can_sit_on_top',1,0),
('hallA','gamehall_chair_wood',15,5,1,6,1,1,'can_sit_on_top',1,0),
('hallA','gamehall_chair_wood',15,9,1,6,1,1,'can_sit_on_top',1,0),
('hallA','gamehall_chair_wood',15,10,1,6,1,1,'can_sit_on_top',1,0),
('hallA','gamehall_chair_wood',15,14,1,6,1,1,'can_sit_on_top',1,0),
('hallA','gamehall_chair_wood',15,15,1,6,1,1,'can_sit_on_top',1,0),
('hallA','gamehall_chair_wood',10,4,1,6,1,1,'can_sit_on_top',1,0),
('hallA','gamehall_chair_wood',10,5,1,6,1,1,'can_sit_on_top',1,0),
('hallA','gamehall_chair_wood',10,9,1,6,1,1,'can_sit_on_top',1,0),
('hallA','gamehall_chair_wood',10,10,1,6,1,1,'can_sit_on_top',1,0),
('hallA','gamehall_chair_wood',10,14,1,6,1,1,'can_sit_on_top',1,0),
('hallA','gamehall_chair_wood',10,15,1,6,1,1,'can_sit_on_top',1,0),
('hallA','gamehall_chair_wood',5,4,1,6,1,1,'can_sit_on_top',1,0),
('hallA','gamehall_chair_wood',5,5,1,6,1,1,'can_sit_on_top',1,0),
('hallA','gamehall_chair_wood',5,9,1,6,1,1,'can_sit_on_top',1,0),
('hallA','gamehall_chair_wood',5,10,1,6,1,1,'can_sit_on_top',1,0),
('hallA','gamehall_chair_wood',5,14,1,6,1,1,'can_sit_on_top',1,0),
('hallA','gamehall_chair_wood',5,15,1,6,1,1,'can_sit_on_top',1,0),
('hallA','table_xoxa',14,5,1,6,1,1,'solid',0.001,0),
('hallA','table_xoxa',14,10,1,6,1,1,'solid',0.001,0),
('hallA','table_xoxa',14,15,1,6,1,1,'solid',0.001,0),
('hallA','table_xoxa',9,5,1,6,1,1,'solid',0.001,0),
('hallA','table_xoxa',9,10,1,6,1,1,'solid',0.001,0),
('hallA','table_xoxa',9,15,1,6,1,1,'solid',0.001,0),
('hallA','table_xoxa',4,5,1,6,1,1,'solid',0.001,0),
('hallA','table_xoxa',4,10,1,6,1,1,'solid',0.001,0),
('hallA','table_xoxa',4,15,1,6,1,1,'solid',0.001,0),
('hallA','table_xoxb',14,14,1,6,1,1,'solid',0.001,0),
('hallA','table_xoxb',14,9,1,6,1,1,'solid',0.001,0),
('hallA','table_xoxb',14,4,1,6,1,1,'solid',0.001,0),
('hallA','table_xoxb',9,14,1,6,1,1,'solid',0.001,0),
('hallA','table_xoxb',9,9,1,6,1,1,'solid',0.001,0),
('hallA','table_xoxb',9,4,1,6,1,1,'solid',0.001,0),
('hallA','table_xoxb',4,14,1,6,1,1,'solid',0.001,0),
('hallA','table_xoxb',4,9,1,6,1,1,'solid',0.001,0),
('hallA','table_xoxb',4,4,1,6,1,1,'solid',0.001,0),
('hallB','gamehall_chair_green',2,4,1,4,1,1,'can_sit_on_top',1,0),
('hallB','gamehall_chair_green',2,10,1,4,1,1,'can_sit_on_top',1,0),
('hallB','gamehall_chair_green',2,16,1,4,1,1,'can_sit_on_top',1,0),
('hallB','gamehall_chair_green',2,6,1,0,1,1,'can_sit_on_top',1,0),
('hallB','gamehall_chair_green',2,12,1,0,1,1,'can_sit_on_top',1,0),
('hallB','gamehall_chair_green',2,18,1,0,1,1,'can_sit_on_top',1,0),
('hallB','gamehall_chair_green',6,3,1,2,1,1,'can_sit_on_top',1,0),
('hallB','gamehall_chair_green',8,3,1,6,1,1,'can_sit_on_top',1,0),
('hallB','gamehall_chair_green',13,3,1,2,1,1,'can_sit_on_top',1,0),
('hallB','gamehall_chair_green',15,3,1,6,1,1,'can_sit_on_top',1,0),
('hallB','table_battleships',2,5,1,0,1,1,'solid',0.001,0),
('hallB','table_battleships',2,11,1,0,1,1,'solid',0.001,0),
('hallB','table_battleships',2,17,1,0,1,1,'solid',0.001,0),
('hallB','table_battleships',7,3,1,2,1,1,'solid',0.001,0),
('hallB','table_battleships',14,3,1,2,1,1,'solid',0.001,0),
('hallC','table_chess_king',12,13,1,0,1,1,'solid',0.001,0),
('hallC','table_chess',13,6,1,0,1,1,'solid',0.001,0),
('hallC','table_chess',2,8,1,0,1,1,'solid',0.001,0),
('hallC','table_chess',5,14,1,2,1,1,'solid',0.001,0),
('hallC','table_chess',8,3,1,2,1,1,'solid',0.001,0),
('hallC','chess_king_chair',12,14,1,0,1,1,'can_sit_on_top',1,0),
('hallC','chess_king_chair',12,12,1,4,1,1,'can_sit_on_top',1,0),
('hallC','gamehall_chair_green',13,7,1,0,1,1,'can_sit_on_top',1,0),
('hallC','gamehall_chair_green',13,5,1,4,1,1,'can_sit_on_top',1,0),
('hallC','gamehall_chair_green',2,9,1,0,1,1,'can_sit_on_top',1,0),
('hallC','gamehall_chair_green',2,7,1,4,1,1,'can_sit_on_top',1,0),
('hallC','gamehall_chair_green',4,14,1,2,1,1,'can_sit_on_top',1,0),
('hallC','gamehall_chair_green',6,14,1,6,1,1,'can_sit_on_top',1,0),
('hallC','gamehall_chair_green',7,3,1,2,1,1,'can_sit_on_top',1,0),
('hallC','gamehall_chair_green',9,3,1,6,1,1,'can_sit_on_top',1,0),
('hallD','cardtableb',2,15,1,0,1,1,'solid',0.001,0),
('hallD','cardtableb',8,3,1,0,1,1,'solid',0.001,0),
('hallD','cardtableb',14,3,1,0,1,1,'solid',0.001,0),
('hallD','cardtablea',2,9,1,0,1,1,'solid',0.001,0),
('hallD','cardtablea',8,9,1,0,1,1,'solid',0.001,0),
('hallD','cardtablea',14,9,1,0,1,1,'solid',0.001,0),
('hallD','cardtablea',8,15,1,0,1,1,'solid',0.001,0),
('hallD','cardtablea',14,15,1,0,1,1,'solid',0.001,0),
('hallD','gamehall_chair_green',8,4,1,0,1,1,'can_sit_on_top',1,0),
('hallD','gamehall_chair_green',14,4,1,0,1,1,'can_sit_on_top',1,0),
('hallD','gamehall_chair_green',2,10,1,0,1,1,'can_sit_on_top',1,0),
('hallD','gamehall_chair_green',8,10,1,0,1,1,'can_sit_on_top',1,0),
('hallD','gamehall_chair_green',14,10,1,0,1,1,'can_sit_on_top',1,0),
('hallD','gamehall_chair_green',2,16,1,0,1,1,'can_sit_on_top',1,0),
('hallD','gamehall_chair_green',8,16,1,0,1,1,'can_sit_on_top',1,0),
('hallD','gamehall_chair_green',14,16,1,0,1,1,'can_sit_on_top',1,0),
('hallD','gamehall_chair_green',7,3,1,2,1,1,'can_sit_on_top',1,0),
('hallD','gamehall_chair_green',13,3,1,2,1,1,'can_sit_on_top',1,0),
('hallD','gamehall_chair_green',1,9,1,2,1,1,'can_sit_on_top',1,0),
('hallD','gamehall_chair_green',7,9,1,2,1,1,'can_sit_on_top',1,0),
('hallD','gamehall_chair_green',13,9,1,2,1,1,'can_sit_on_top',1,0),
('hallD','gamehall_chair_green',1,15,1,2,1,1,'can_sit_on_top',1,0),
('hallD','gamehall_chair_green',7,15,1,2,1,1,'can_sit_on_top',1,0),
('hallD','gamehall_chair_green',13,15,1,2,1,1,'can_sit_on_top',1,0),
('hallD','gamehall_chair_green',8,2,1,4,1,1,'can_sit_on_top',1,0),
('hallD','gamehall_chair_green',14,2,1,4,1,1,'can_sit_on_top',1,0),
('hallD','gamehall_chair_green',2,8,1,4,1,1,'can_sit_on_top',1,0),
('hallD','gamehall_chair_green',8,8,1,4,1,1,'can_sit_on_top',1,0),
('hallD','gamehall_chair_green',14,8,1,4,1,1,'can_sit_on_top',1,0),
('hallD','gamehall_chair_green',2,14,1,4,1,1,'can_sit_on_top',1,0),
('hallD','gamehall_chair_green',8,14,1,4,1,1,'can_sit_on_top',1,0),
('hallD','gamehall_chair_green',14,14,1,4,1,1,'can_sit_on_top',1,0),
('hallD','gamehall_chair_green',9,3,1,6,1,1,'can_sit_on_top',1,0),
('hallD','gamehall_chair_green',15,3,1,6,1,1,'can_sit_on_top',1,0),
('hallD','gamehall_chair_green',3,9,1,6,1,1,'can_sit_on_top',1,0),
('hallD','gamehall_chair_green',9,9,1,6,1,1,'can_sit_on_top',1,0),
('hallD','gamehall_chair_green',15,9,1,6,1,1,'can_sit_on_top',1,0),
('hallD','gamehall_chair_green',3,15,1,6,1,1,'can_sit_on_top',1,0),
('hallD','gamehall_chair_green',9,15,1,6,1,1,'can_sit_on_top',1,0),
('hallD','gamehall_chair_green',15,15,1,6,1,1,'can_sit_on_top',1,0);

-- Floating Garden
INSERT INTO `rooms`
    (`owner_id`, `owner_name`, `name`, `description`, `model`, `state`, `users_max`, `category`, `override_model`, `allow_walkthrough`, `roller_speed`, `trade_mode`)
SELECT @public_room_owner_id, @public_room_owner_name,
    'Floating Garden', 'Official public room', 'floatinggarden', 'open', 50, @public_room_category_id, '0', '0', 4, 2
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `rooms` WHERE `model` = 'floatinggarden');

INSERT INTO `room_models` (`name`, `door_x`, `door_y`, `door_dir`, `heightmap`, `public_items`, `club_only`)
SELECT 'floatinggarden', 2, 21, 4, 'xxxxxxxxxxxxxxxx333333xxxxxxxxx\r\nxxxxxxxxxxxxxxxx3xxxx3xxxxxxxxx\r\nxxxxxxxxxxxxxxxx3xxxx3xxxxxxxxx\r\nxxxxxxxxxxxxxxxx3xxxx3xxxxxxxxx\r\nxxxxxxxxxxxxxxx223xxx33xxxxxxxx\r\nxxxxxxxxxxxxxxx11xxx33333xxxxxx\r\nxxxxxxxxxxxxxxxx11xx3333333xxxx\r\nxxxxxxxxxxxxxxxx11xx33333333xxx\r\nxxxxxxxxxxxxxxxxx11xxxxxxxx3xxx\r\nxxxxxxxxxxxxxxxxxx11xxxx3333xxx\r\nxxxxxxxxxxxxxxxxxxx1xxxx33333xx\r\nxxxxxxxxxxxxxxxxxxx1xxx3333333x\r\n555xxxxxxxxxxx1111111x333333333\r\n555xxxxxxxxxxx21111111xxxxxx333\r\n555xxxxxxxxxxx22111111111xxxxxx\r\n555xxxxxxxxxxx222xxxxxxx111xxxx\r\n555xxxxxxxxxxx22xxxxxxxxxx1xxxx\r\n555xxxxxxxxxxx23333333333x111xx\r\n555xxxxxxxx33333333333333x111xx\r\n555xxxxxxxx333333x3333333x111xx\r\n555xxxxxxxx33333333333333x111xx\r\n555xxxxxxxx33x33333333333x111xx\r\n555xxxxxxxx33x33x33333333x111xx\r\n555xxxxxxxx33x33x33333333x111xx\r\n5554333333333x333x3333333x111xx\r\nx554333333xxxx33xxxxxxxxxx111xx\r\nxxxxxxxxx3xxxx333221111111111xx\r\nxxxxxxxxx3xxxx333221111111111xx\r\nxxxxxxxxx33333333xx1111x11x11xx\r\nxxxxxxxxx33333333111xxx11xxxxxx\r\nxxxxxxxxxxxxxx33311xxxx11xxxxxx\r\nxxxxxxxxxxxxxx33311xxxx11xxxxxx\r\nxxxxxxxxxxxxxx333x1xxxx11xxxxxx\r\nxxxxxxxxxxxxxx333x1xx111111xxxx\r\nxxxxxxxxxxxxxx33311xx111111xxxx\r\nxxxxxxxxxx333333311xx111111xxxx\r\nxxxxxxxxxxx33333311xx111111xxxx\r\nxxxxxxxxxxxxxxxx111xxxxxxxxxxxx\r\nxxxxxxxxxxxxxxx111xxxxxxxxxxxxx', '', '0'
FROM DUAL
ON DUPLICATE KEY UPDATE
    `door_x` = VALUES(`door_x`),
    `door_y` = VALUES(`door_y`),
    `door_dir` = VALUES(`door_dir`),
    `heightmap` = VALUES(`heightmap`);

INSERT INTO `navigator_publics` (`public_cat_id`, `room_id`, `visible`)
SELECT @official_root_public_cat_id, r.`id`, '1'
FROM `rooms` r
WHERE r.`model` = 'floatinggarden'
  AND @official_root_public_cat_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `navigator_publics` np WHERE np.`room_id` = r.`id`);

-- Ballroom
INSERT INTO `rooms`
    (`owner_id`, `owner_name`, `name`, `description`, `model`, `state`, `users_max`, `category`, `override_model`, `allow_walkthrough`, `roller_speed`, `trade_mode`)
SELECT @public_room_owner_id, @public_room_owner_name,
    'Ballroom', 'Official public room', 'ballroom', 'open', 50, @public_room_category_id, '0', '0', 4, 2
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `rooms` WHERE `model` = 'ballroom');

INSERT INTO `room_models` (`name`, `door_x`, `door_y`, `door_dir`, `heightmap`, `public_items`, `club_only`)
SELECT 'ballroom', 13, 6, 4, 'xxxx4444444444444444444\r\nxxxx4444444444444444444\r\nxxxx4444444444444444444\r\nxxxx33x2222444442222x33\r\nxxxx2222222x00xx2222222\r\nxxxx22222220000x2222222\r\nxxxx11x0000x000x0000x11\r\nxxxx0000000000000000000\r\n11100000000000000000000\r\n11100000000000000000000\r\n11100000000000000000000\r\nxxxx0000000000000000000\r\n22210000000000000000000\r\n22210000000000000000000\r\n22210000000000000000000\r\nxxxx0000000000000000000\r\n11100000000000000000000\r\n11100000000000000000000\r\n11100000000000000000000\r\nxxxxx000x11111111x0000x\r\nxxxxxx00x1111x111x000xx\r\nxxxxxxx0x11111111x00xxx\r\nxxxxxxxxx11111111x0xxxx\r\nxxxxxxxxx11111111xxxxxx', '', '0'
FROM DUAL
ON DUPLICATE KEY UPDATE
    `door_x` = VALUES(`door_x`),
    `door_y` = VALUES(`door_y`),
    `door_dir` = VALUES(`door_dir`),
    `heightmap` = VALUES(`heightmap`);

INSERT INTO `navigator_publics` (`public_cat_id`, `room_id`, `visible`)
SELECT @official_root_public_cat_id, r.`id`, '1'
FROM `rooms` r
WHERE r.`model` = 'ballroom'
  AND @official_root_public_cat_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `navigator_publics` np WHERE np.`room_id` = r.`id`);

-- The Park
INSERT INTO `rooms`
    (`owner_id`, `owner_name`, `name`, `description`, `model`, `state`, `users_max`, `category`, `override_model`, `allow_walkthrough`, `roller_speed`, `trade_mode`)
SELECT @public_room_owner_id, @public_room_owner_name,
    'The Park', 'Official public room', 'park_a', 'open', 50, @public_room_category_id, '0', '0', 4, 2
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `rooms` WHERE `model` = 'park_a');

INSERT INTO `room_models` (`name`, `door_x`, `door_y`, `door_dir`, `heightmap`, `public_items`, `club_only`)
SELECT 'park_a', 2, 15, 0, 'xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\r\nxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\r\nxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx0xxxxxxxxxxxxx\r\nxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx00xxxxxxxxxxxx\r\nxxxxxxxxxxxxx0x00xxxxxxxxxxx0x000xxxxxxxxxxx\r\nxxxxxxxxxxxx0000000000000000000000xxxxxxxxxx\r\nxxxxxxxxxxx000000000000000000000000xxxxxxxxx\r\nxxxxxxxxxxx0000000000000000000000000xxxxxxxx\r\nxxxxxxxxxxx00000000000000000000000000xxxxxxx\r\nxxxxxxxx000000000000000000000000000000xxxxxx\r\nxxxxxxx00000000000000000000000000000000xxxxx\r\nxxxxxxx000000000000000000000000000000000xxxx\r\nxxxxxxx0000000000000000000000000000000000xxx\r\nxxxxxxxxx000000000000000000000000000000000xx\r\n00000000000000000000xx00000000000000000000xx\r\n0000000000000000000xxxx00000000000xxxxxxx0xx\r\n0000000000000000000xxxx00000000000x00000xxxx\r\nxxxxx00x0000000000xxxxx0xxxxxx0000x0000000xx\r\nxxxxx0000000000000xxxxx0xx000x0000x000000xxx\r\nxxxxx0000000000000xxxxx0x000000000x00000xxxx\r\nxxxxx000000x0000000xxxx0x000000000xxx00xxxxx\r\nxxxxxxxx000x0000000xxx00xxx000000x0000xxxxxx\r\nxxxxxxxx000x000000xxxx0x0000000000000xxxxxxx\r\nxxxxxxxx000x000000011100000000000000xxxxxxxx\r\nxxxxxxxx000x00000001110000000000000xxxxxxxxx\r\nxxxxxxxxx00x0000000111x00000000x00xxxxxxxxxx\r\nxxxxxxxxxx0x0000000xxx0000000xxxxxxxxxxxxxxx\r\nxxxxxxxxxxxx000000xxxx0000000xxxxxxxxxxxxxxx\r\nxxxxxxxxxxxx000000xxx00xxxxx00xxxxxxxxxxxxxx\r\nxxxxxxxxxxxxxxxxx0xxx0xx000x00xxxxxxxxxxxxxx\r\nxxxxxxxxxxxxxxxxx0xxx0x000000xxxxxxxxxxxxxxx\r\nxxxxxxxxxxxxxxxxx0xxx0x00000xxxxxxxxxxxxxxxx\r\nxxxxxxxxxxxxxxxxx0xxxxx00xxxxxxxxxxxxxxxxxxx\r\nxxxxxxxxxxxxxxxxx0xxxxx0xxxxxxxxxxxxxxxxxxxx', '', '0'
FROM DUAL
ON DUPLICATE KEY UPDATE
    `door_x` = VALUES(`door_x`),
    `door_y` = VALUES(`door_y`),
    `door_dir` = VALUES(`door_dir`),
    `heightmap` = VALUES(`heightmap`);

INSERT INTO `navigator_publics` (`public_cat_id`, `room_id`, `visible`)
SELECT @official_root_public_cat_id, r.`id`, '1'
FROM `rooms` r
WHERE r.`model` = 'park_a'
  AND @official_root_public_cat_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `navigator_publics` np WHERE np.`room_id` = r.`id`);

-- The Infobus
INSERT INTO `rooms`
    (`owner_id`, `owner_name`, `name`, `description`, `model`, `state`, `users_max`, `category`, `override_model`, `allow_walkthrough`, `roller_speed`, `trade_mode`)
SELECT @public_room_owner_id, @public_room_owner_name,
    'The Infobus', 'Official public room', 'park_b', 'open', 50, @public_room_category_id, '0', '0', 4, 2
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `rooms` WHERE `model` = 'park_b');

INSERT INTO `room_models` (`name`, `door_x`, `door_y`, `door_dir`, `heightmap`, `public_items`, `club_only`)
SELECT 'park_b', 11, 2, 6, '0000x0000000\r\n0000xx000000\r\n000000000000\r\n00000000000x\r\n000000000000\r\n00x0000x0000', '', '0'
FROM DUAL
ON DUPLICATE KEY UPDATE
    `door_x` = VALUES(`door_x`),
    `door_y` = VALUES(`door_y`),
    `door_dir` = VALUES(`door_dir`),
    `heightmap` = VALUES(`heightmap`);

-- Theatredome
INSERT INTO `rooms`
    (`owner_id`, `owner_name`, `name`, `description`, `model`, `state`, `users_max`, `category`, `override_model`, `allow_walkthrough`, `roller_speed`, `trade_mode`)
SELECT @public_room_owner_id, @public_room_owner_name,
    'Theatredome', 'Official public room', 'theater', 'open', 50, @public_room_category_id, '0', '0', 4, 2
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `rooms` WHERE `model` = 'theater');

INSERT INTO `room_models` (`name`, `door_x`, `door_y`, `door_dir`, `heightmap`, `public_items`, `club_only`)
SELECT 'theater', 20, 27, 0, 'XXXXXXXXXXXXXXXXXXXXXXX\r\nXXXXXXXXXXXXXXXXXXXXXXX\r\nXXXXXXXXXXXXXXXXXXXXXXX\r\nXXXXXXXXXXXXXXXXXXXXXXX\r\nXXXXXXXXXXXXXXXXXXXXXXX\r\nXXXXXXXXXXXXXXXXXXXXXXX\r\nXXXXXXX111111111XXXXXXX\r\nXXXXXXX11111111100000XX\r\nXXXX00X11111111100000XX\r\nXXXX00x11111111100000XX\r\n4XXX00X11111111100000XX\r\n4440000XXXXXXXXX00000XX\r\n444000000000000000000XX\r\n4XX000000000000000000XX\r\n4XX0000000000000000000X\r\n44400000000000000000000\r\n44400000000000000000000\r\n44X0000000000000000O000\r\n44X11111111111111111000\r\n44X11111111111111111000\r\n33X11111111111111111000\r\n22X11111111111111111000\r\n22X11111111111111111000\r\n22X11111111111111111000\r\n22X11111111111111111000\r\n22X11111111111111111000\r\n22211111111111111111000\r\n22211111111111111111000\r\nXXXXXXXXXXXXXXXXXXXX00X\r\nXXXXXXXXXXXXXXXXXXXX00X', '', '0'
FROM DUAL
ON DUPLICATE KEY UPDATE
    `door_x` = VALUES(`door_x`),
    `door_y` = VALUES(`door_y`),
    `door_dir` = VALUES(`door_dir`),
    `heightmap` = VALUES(`heightmap`);

INSERT INTO `navigator_publics` (`public_cat_id`, `room_id`, `visible`)
SELECT @official_root_public_cat_id, r.`id`, '1'
FROM `rooms` r
WHERE r.`model` = 'theater'
  AND @official_root_public_cat_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `navigator_publics` np WHERE np.`room_id` = r.`id`);

-- Sun Terrace
INSERT INTO `rooms`
    (`owner_id`, `owner_name`, `name`, `description`, `model`, `state`, `users_max`, `category`, `override_model`, `allow_walkthrough`, `roller_speed`, `trade_mode`)
SELECT @public_room_owner_id, @public_room_owner_name,
    'Sun Terrace', 'Official public room', 'sun_terrace', 'open', 50, @public_room_category_id, '0', '0', 4, 2
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `rooms` WHERE `model` = 'sun_terrace');

INSERT INTO `room_models` (`name`, `door_x`, `door_y`, `door_dir`, `heightmap`, `public_items`, `club_only`)
SELECT 'sun_terrace', 9, 17, 2, 'xxxxxx21000000000xxxxxxxx\r\nxxxxxx3xxx000xx000xxxxxxx\r\nxxxxxx4xxx000xxx000xxxxxx\r\nxxxxxx44xx000x00x000xxxxx\r\nxxxxxx44xx0000xx00000xxxx\r\nxxxxxx44xx000000000000xxx\r\nxxxxxx44xx0000000000000xx\r\nxxxxxxx4xxxxxxx00000000xx\r\nxxxxxxx4xxxxxxx0000000xxx\r\nxxxxxx444432222xxx00xxxxx\r\nxxxxxx444432222x0000000xx\r\nxxxxxx444432222x0000000xx\r\nxxxxxx44400x222x0000000xx\r\nxxxxxx444000x11x0000000xx\r\nxxxxxx444000000x0000000xx\r\nxxxxxx444000000x0000000xx\r\nxxxxxx440000000000000000x\r\nxxxxxx4400000000000000000\r\nx8876x444000000x000000000\r\nx8xx6x444000000x000000000\r\nx9xx6x444000000x000000000\r\n999x65444000000x000000000\r\n999xxx444xxxxxxxxxx000000\r\n999xxx444xxxxxxxxxxx00000\r\n999xxx333xxxxxxxxxxxx0000\r\n999xxx222222222222222x000\r\nxxxxxx222222222222222xx00\r\nxxxxxx222222222222222xxx0\r\nxxxxxx222222222222222xxxx\r\nxxxxxxx22222222222222xxxx\r\nxxxxxxxx2222222222222xxxx', '', '0'
FROM DUAL
ON DUPLICATE KEY UPDATE
    `door_x` = VALUES(`door_x`),
    `door_y` = VALUES(`door_y`),
    `door_dir` = VALUES(`door_dir`),
    `heightmap` = VALUES(`heightmap`);

INSERT INTO `navigator_publics` (`public_cat_id`, `room_id`, `visible`)
SELECT @official_root_public_cat_id, r.`id`, '1'
FROM `rooms` r
WHERE r.`model` = 'sun_terrace'
  AND @official_root_public_cat_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `navigator_publics` np WHERE np.`room_id` = r.`id`);

-- Rooftop Terrace
INSERT INTO `rooms`
    (`owner_id`, `owner_name`, `name`, `description`, `model`, `state`, `users_max`, `category`, `override_model`, `allow_walkthrough`, `roller_speed`, `trade_mode`)
SELECT @public_room_owner_id, @public_room_owner_name,
    'Rooftop Terrace', 'Official public room', 'rooftop', 'open', 50, @public_room_category_id, '0', '0', 4, 2
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `rooms` WHERE `model` = 'rooftop');

INSERT INTO `room_models` (`name`, `door_x`, `door_y`, `door_dir`, `heightmap`, `public_items`, `club_only`)
SELECT 'rooftop', 17, 12, 0, '44xxxxxxxxxxxxxxxxxx\r\n444xxxxxxxxxxx444444\r\n4444xxxxxxxxxx444444\r\n44444xxxx4xxxx444444\r\n444444xxx44xxx444444\r\n44444444444444444444\r\n44444444444444444444\r\n44444444444444444444\r\n44444444xx44xx44xx44\r\n44444444xx44xx44xx44\r\n44444444444444444444\r\n44444444444444444444\r\n44444444444444444444\r\nx444444x444444xx4444\r\nx444444x444444xx333x\r\nx444444x444444xx222x\r\nx444444x444444xx11xx\r\nx444444x444444xxxxxx', '', '0'
FROM DUAL
ON DUPLICATE KEY UPDATE
    `door_x` = VALUES(`door_x`),
    `door_y` = VALUES(`door_y`),
    `door_dir` = VALUES(`door_dir`),
    `heightmap` = VALUES(`heightmap`);

INSERT INTO `navigator_publics` (`public_cat_id`, `room_id`, `visible`)
SELECT @official_root_public_cat_id, r.`id`, '1'
FROM `rooms` r
WHERE r.`model` = 'rooftop'
  AND @official_root_public_cat_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `navigator_publics` np WHERE np.`room_id` = r.`id`);

-- Rooftop Cafe
INSERT INTO `rooms`
    (`owner_id`, `owner_name`, `name`, `description`, `model`, `state`, `users_max`, `category`, `override_model`, `allow_walkthrough`, `roller_speed`, `trade_mode`)
SELECT @public_room_owner_id, @public_room_owner_name,
    'Rooftop Cafe', 'Official public room', 'rooftop_2', 'open', 50, @public_room_category_id, '0', '0', 4, 2
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `rooms` WHERE `model` = 'rooftop_2');

INSERT INTO `room_models` (`name`, `door_x`, `door_y`, `door_dir`, `heightmap`, `public_items`, `club_only`)
SELECT 'rooftop_2', 4, 11, 0, 'x0000x000\r\nxxxxxx000\r\n000000000\r\n000000000\r\n000000000\r\n000000000\r\n000000000\r\n000000000\r\n000000000\r\n000000000\r\nxxx000xxx\r\nxxx000xxx', '', '0'
FROM DUAL
ON DUPLICATE KEY UPDATE
    `door_x` = VALUES(`door_x`),
    `door_y` = VALUES(`door_y`),
    `door_dir` = VALUES(`door_dir`),
    `heightmap` = VALUES(`heightmap`);

-- Space Cafe
INSERT INTO `rooms`
    (`owner_id`, `owner_name`, `name`, `description`, `model`, `state`, `users_max`, `category`, `override_model`, `allow_walkthrough`, `roller_speed`, `trade_mode`)
SELECT @public_room_owner_id, @public_room_owner_name,
    'Space Cafe', 'Official public room', 'space_cafe', 'open', 50, @public_room_category_id, '0', '0', 4, 2
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `rooms` WHERE `model` = 'space_cafe');

INSERT INTO `room_models` (`name`, `door_x`, `door_y`, `door_dir`, `heightmap`, `public_items`, `club_only`)
SELECT 'space_cafe', 21, 17, 0, 'x3333x333211111xxxxxxxxx\r\nx3333x333211111xx3333333\r\nxxxxxx333211111xx3333333\r\n33333333xx11111xx3333333\r\n33333333xx11111xx3333333\r\n33x333xxxx11111xx3333333\r\nxxx222xxx111111xx3333333\r\n22222222xx11111xx3333333\r\n22222222xxx1111xx3333333\r\n22222222xxxx1111x2222222\r\n22222222xxxx1111x1111111\r\n22222222xxxx111111111111\r\n22222222xxxx111111111111\r\nxxx222xxxxx1111111111111\r\nxxxx33xxxx11111111111111\r\nxxx333321111111111111111\r\nxxx333321111111111111111\r\nxxx333321111111111111111\r\nxxxxxxxxxxxxx1111xxxx11x\r\nxxxxxxxxxxxxx0000xxxx11x\r\nxxxxxxxxxx0000000xxxx11x\r\nxxxxxxxxxx0000000xxxxxxx\r\nxxxxxxxxxx0000000xxxxxxx\r\nxxxxxxxxxx0000000xxxxxxx\r\nxxxxxxxxxx0000000xxxxxxx\r\nxxxxxxxxxx0000000xxxxxxx', '', '0'
FROM DUAL
ON DUPLICATE KEY UPDATE
    `door_x` = VALUES(`door_x`),
    `door_y` = VALUES(`door_y`),
    `door_dir` = VALUES(`door_dir`),
    `heightmap` = VALUES(`heightmap`);

INSERT INTO `navigator_publics` (`public_cat_id`, `room_id`, `visible`)
SELECT @official_root_public_cat_id, r.`id`, '1'
FROM `rooms` r
WHERE r.`model` = 'space_cafe'
  AND @official_root_public_cat_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `navigator_publics` np WHERE np.`room_id` = r.`id`);

-- Cunning Fox Gamehall
INSERT INTO `rooms`
    (`owner_id`, `owner_name`, `name`, `description`, `model`, `state`, `users_max`, `category`, `override_model`, `allow_walkthrough`, `roller_speed`, `trade_mode`)
SELECT @public_room_owner_id, @public_room_owner_name,
    'Cunning Fox Gamehall', 'Official public room', 'entryhall', 'open', 50, @public_room_category_id, '0', '0', 4, 2
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `rooms` WHERE `model` = 'entryhall');

INSERT INTO `room_models` (`name`, `door_x`, `door_y`, `door_dir`, `heightmap`, `public_items`, `club_only`)
SELECT 'entryhall', 17, 18, 0, 'xx11xxxx11xxxx11xxxx\r\nx1111111111111111111\r\n11111111111111111111\r\n11111111111111111111\r\nx1111111111111111111\r\nx1111111111111111111\r\nx1111111111111111111\r\nx1111111111111111111\r\nx1111111111111111111\r\nx1111111111111111111\r\nx1111111111111111111\r\nx1111111111111111111\r\nx1111111111111111111\r\nx1111111111111111111\r\nx1111111111111111111\r\nx1111111111111111111\r\nx1111111111111111111\r\nx1111111111111111111\r\nx1111111111111111111\r\nx1111111111111111111\r\nxxxxxxxxxxxxxxxxx11x', '', '0'
FROM DUAL
ON DUPLICATE KEY UPDATE
    `door_x` = VALUES(`door_x`),
    `door_y` = VALUES(`door_y`),
    `door_dir` = VALUES(`door_dir`),
    `heightmap` = VALUES(`heightmap`);

INSERT INTO `navigator_publics` (`public_cat_id`, `room_id`, `visible`)
SELECT @official_root_public_cat_id, r.`id`, '1'
FROM `rooms` r
WHERE r.`model` = 'entryhall'
  AND @official_root_public_cat_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `navigator_publics` np WHERE np.`room_id` = r.`id`);

-- TicTacToe hall
INSERT INTO `rooms`
    (`owner_id`, `owner_name`, `name`, `description`, `model`, `state`, `users_max`, `category`, `override_model`, `allow_walkthrough`, `roller_speed`, `trade_mode`)
SELECT @public_room_owner_id, @public_room_owner_name,
    'TicTacToe hall', 'Official public room', 'hallA', 'open', 50, @public_room_category_id, '0', '0', 4, 2
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `rooms` WHERE `model` = 'hallA');

INSERT INTO `room_models` (`name`, `door_x`, `door_y`, `door_dir`, `heightmap`, `public_items`, `club_only`)
SELECT 'hallA', 0, 0, 4, '11xxxxxxxxxxxxxxx\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111', '', '0'
FROM DUAL
ON DUPLICATE KEY UPDATE
    `door_x` = VALUES(`door_x`),
    `door_y` = VALUES(`door_y`),
    `door_dir` = VALUES(`door_dir`),
    `heightmap` = VALUES(`heightmap`);

INSERT INTO `navigator_publics` (`public_cat_id`, `room_id`, `visible`)
SELECT @official_root_public_cat_id, r.`id`, '1'
FROM `rooms` r
WHERE r.`model` = 'hallA'
  AND @official_root_public_cat_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `navigator_publics` np WHERE np.`room_id` = r.`id`);

-- Battleships hall
INSERT INTO `rooms`
    (`owner_id`, `owner_name`, `name`, `description`, `model`, `state`, `users_max`, `category`, `override_model`, `allow_walkthrough`, `roller_speed`, `trade_mode`)
SELECT @public_room_owner_id, @public_room_owner_name,
    'Battleships hall', 'Official public room', 'hallB', 'open', 50, @public_room_category_id, '0', '0', 4, 2
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `rooms` WHERE `model` = 'hallB');

INSERT INTO `room_models` (`name`, `door_x`, `door_y`, `door_dir`, `heightmap`, `public_items`, `club_only`)
SELECT 'hallB', 1, 0, 4, 'x11xxxxxxxxxxxxxxxx\r\n1111111111111111111\r\n1111111111111111111\r\n1111111111111111111\r\n1111111111111111111\r\n1111111111111111111\r\n1111111111111111111\r\n111111xxxxxxxxxxxxx\r\n111111xxxxxxxxxxxxx\r\n111111xxxxxxxxxxxxx\r\n111111xxxxxxxxxxxxx\r\n111111xxxxxxxxxxxxx\r\n111111xxxxxxxxxxxxx\r\n111111xxxxxxxxxxxxx\r\n111111xxxxxxxxxxxxx\r\n111111xxxxxxxxxxxxx\r\n111111xxxxxxxxxxxxx\r\n111111xxxxxxxxxxxxx\r\n111111xxxxxxxxxxxxx\r\n111111xxxxxxxxxxxxx\r\n111111xxxxxxxxxxxxx', '', '0'
FROM DUAL
ON DUPLICATE KEY UPDATE
    `door_x` = VALUES(`door_x`),
    `door_y` = VALUES(`door_y`),
    `door_dir` = VALUES(`door_dir`),
    `heightmap` = VALUES(`heightmap`);

INSERT INTO `navigator_publics` (`public_cat_id`, `room_id`, `visible`)
SELECT @official_root_public_cat_id, r.`id`, '1'
FROM `rooms` r
WHERE r.`model` = 'hallB'
  AND @official_root_public_cat_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `navigator_publics` np WHERE np.`room_id` = r.`id`);

-- Chess hall
INSERT INTO `rooms`
    (`owner_id`, `owner_name`, `name`, `description`, `model`, `state`, `users_max`, `category`, `override_model`, `allow_walkthrough`, `roller_speed`, `trade_mode`)
SELECT @public_room_owner_id, @public_room_owner_name,
    'Chess hall', 'Official public room', 'hallC', 'open', 50, @public_room_category_id, '0', '0', 4, 2
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `rooms` WHERE `model` = 'hallC');

INSERT INTO `room_models` (`name`, `door_x`, `door_y`, `door_dir`, `heightmap`, `public_items`, `club_only`)
SELECT 'hallC', 0, 0, 4, '11xxxxxxxxxxxxxxx\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111', '', '0'
FROM DUAL
ON DUPLICATE KEY UPDATE
    `door_x` = VALUES(`door_x`),
    `door_y` = VALUES(`door_y`),
    `door_dir` = VALUES(`door_dir`),
    `heightmap` = VALUES(`heightmap`);

INSERT INTO `navigator_publics` (`public_cat_id`, `room_id`, `visible`)
SELECT @official_root_public_cat_id, r.`id`, '1'
FROM `rooms` r
WHERE r.`model` = 'hallC'
  AND @official_root_public_cat_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `navigator_publics` np WHERE np.`room_id` = r.`id`);

-- Poker hall
INSERT INTO `rooms`
    (`owner_id`, `owner_name`, `name`, `description`, `model`, `state`, `users_max`, `category`, `override_model`, `allow_walkthrough`, `roller_speed`, `trade_mode`)
SELECT @public_room_owner_id, @public_room_owner_name,
    'Poker hall', 'Official public room', 'hallD', 'open', 50, @public_room_category_id, '0', '0', 4, 2
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `rooms` WHERE `model` = 'hallD');

INSERT INTO `room_models` (`name`, `door_x`, `door_y`, `door_dir`, `heightmap`, `public_items`, `club_only`)
SELECT 'hallD', 0, 0, 4, '11xxxxxxxxxxxxxxx\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111\r\n11111111111111111', '', '0'
FROM DUAL
ON DUPLICATE KEY UPDATE
    `door_x` = VALUES(`door_x`),
    `door_y` = VALUES(`door_y`),
    `door_dir` = VALUES(`door_dir`),
    `heightmap` = VALUES(`heightmap`);

INSERT INTO `navigator_publics` (`public_cat_id`, `room_id`, `visible`)
SELECT @official_root_public_cat_id, r.`id`, '1'
FROM `rooms` r
WHERE r.`model` = 'hallD'
  AND @official_root_public_cat_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `navigator_publics` np WHERE np.`room_id` = r.`id`);

-- Walkway links (resolved by model; bus = park, plain walkway = rooftop/gamehall).
INSERT INTO `public_roomwalkways` (`room_id`, `to_id`, `coords_map`, `door_position`)
SELECT a.`id`, b.`id`, '28,4 28,5', NULL
FROM `rooms` a JOIN `rooms` b ON b.`model` = 'park_b'
WHERE a.`model` = 'park_a'
  AND NOT EXISTS (SELECT 1 FROM `public_roomwalkways` w WHERE w.`room_id` = a.`id` AND w.`to_id` = b.`id`);

INSERT INTO `public_roomwalkways` (`room_id`, `to_id`, `coords_map`, `door_position`)
SELECT a.`id`, b.`id`, '11,2', '28,6,0,4'
FROM `rooms` a JOIN `rooms` b ON b.`model` = 'park_a'
WHERE a.`model` = 'park_b'
  AND NOT EXISTS (SELECT 1 FROM `public_roomwalkways` w WHERE w.`room_id` = a.`id` AND w.`to_id` = b.`id`);

INSERT INTO `public_roomwalkways` (`room_id`, `to_id`, `coords_map`, `door_position`)
SELECT a.`id`, b.`id`, '9,4 10,4 9,3', NULL
FROM `rooms` a JOIN `rooms` b ON b.`model` = 'rooftop_2'
WHERE a.`model` = 'rooftop'
  AND NOT EXISTS (SELECT 1 FROM `public_roomwalkways` w WHERE w.`room_id` = a.`id` AND w.`to_id` = b.`id`);

INSERT INTO `public_roomwalkways` (`room_id`, `to_id`, `coords_map`, `door_position`)
SELECT a.`id`, b.`id`, '3,11 4,11 5,11', '10,5,4,4'
FROM `rooms` a JOIN `rooms` b ON b.`model` = 'rooftop'
WHERE a.`model` = 'rooftop_2'
  AND NOT EXISTS (SELECT 1 FROM `public_roomwalkways` w WHERE w.`room_id` = a.`id` AND w.`to_id` = b.`id`);

-- Cunning Fox Gamehall door links. Resolve by model and same owner so installs with
-- non-standard/auto-generated room ids still get correct links.
INSERT INTO `public_roomwalkways` (`room_id`, `to_id`, `coords_map`, `door_position`)
SELECT a.`id`, b.`id`, '2,0 3,0', '1,1,1,4'
FROM `rooms` a JOIN `rooms` b ON b.`model` = 'hallA' AND b.`owner_id` = a.`owner_id`
WHERE a.`model` = 'entryhall'
  AND NOT EXISTS (SELECT 1 FROM `public_roomwalkways` w WHERE w.`room_id` = a.`id` AND w.`to_id` = b.`id`);

INSERT INTO `public_roomwalkways` (`room_id`, `to_id`, `coords_map`, `door_position`)
SELECT a.`id`, b.`id`, '8,0 9,0', '2,1,1,4'
FROM `rooms` a JOIN `rooms` b ON b.`model` = 'hallB' AND b.`owner_id` = a.`owner_id`
WHERE a.`model` = 'entryhall'
  AND NOT EXISTS (SELECT 1 FROM `public_roomwalkways` w WHERE w.`room_id` = a.`id` AND w.`to_id` = b.`id`);

INSERT INTO `public_roomwalkways` (`room_id`, `to_id`, `coords_map`, `door_position`)
SELECT a.`id`, b.`id`, '14,0 15,0', '1,1,1,4'
FROM `rooms` a JOIN `rooms` b ON b.`model` = 'hallC' AND b.`owner_id` = a.`owner_id`
WHERE a.`model` = 'entryhall'
  AND NOT EXISTS (SELECT 1 FROM `public_roomwalkways` w WHERE w.`room_id` = a.`id` AND w.`to_id` = b.`id`);

INSERT INTO `public_roomwalkways` (`room_id`, `to_id`, `coords_map`, `door_position`)
SELECT a.`id`, b.`id`, '0,2 0,3', '1,1,1,4'
FROM `rooms` a JOIN `rooms` b ON b.`model` = 'hallD' AND b.`owner_id` = a.`owner_id`
WHERE a.`model` = 'entryhall'
  AND NOT EXISTS (SELECT 1 FROM `public_roomwalkways` w WHERE w.`room_id` = a.`id` AND w.`to_id` = b.`id`);

INSERT INTO `public_roomwalkways` (`room_id`, `to_id`, `coords_map`, `door_position`)
SELECT a.`id`, b.`id`, '0,0 1,0', '3,1,1,4'
FROM `rooms` a JOIN `rooms` b ON b.`model` = 'entryhall' AND b.`owner_id` = a.`owner_id`
WHERE a.`model` = 'hallA'
  AND NOT EXISTS (SELECT 1 FROM `public_roomwalkways` w WHERE w.`room_id` = a.`id` AND w.`to_id` = b.`id`);

INSERT INTO `public_roomwalkways` (`room_id`, `to_id`, `coords_map`, `door_position`)
SELECT a.`id`, b.`id`, '2,0 1,0', '9,1,1,4'
FROM `rooms` a JOIN `rooms` b ON b.`model` = 'entryhall' AND b.`owner_id` = a.`owner_id`
WHERE a.`model` = 'hallB'
  AND NOT EXISTS (SELECT 1 FROM `public_roomwalkways` w WHERE w.`room_id` = a.`id` AND w.`to_id` = b.`id`);

INSERT INTO `public_roomwalkways` (`room_id`, `to_id`, `coords_map`, `door_position`)
SELECT a.`id`, b.`id`, '0,0 1,0', '15,1,1,4'
FROM `rooms` a JOIN `rooms` b ON b.`model` = 'entryhall' AND b.`owner_id` = a.`owner_id`
WHERE a.`model` = 'hallC'
  AND NOT EXISTS (SELECT 1 FROM `public_roomwalkways` w WHERE w.`room_id` = a.`id` AND w.`to_id` = b.`id`);

INSERT INTO `public_roomwalkways` (`room_id`, `to_id`, `coords_map`, `door_position`)
SELECT a.`id`, b.`id`, '0,0 1,0', '1,3,1,2'
FROM `rooms` a JOIN `rooms` b ON b.`model` = 'entryhall' AND b.`owner_id` = a.`owner_id`
WHERE a.`model` = 'hallD'
  AND NOT EXISTS (SELECT 1 FROM `public_roomwalkways` w WHERE w.`room_id` = a.`id` AND w.`to_id` = b.`id`);

-- Repair hotels that already ran an older revision of this update: these
-- official public rooms use shared room_models rows, not per-room custom maps.
UPDATE `rooms`
SET `category` = @public_room_category_id,
    `override_model` = '0',
    `allow_walkthrough` = '0'
WHERE `model` IN (
    'floatinggarden', 'ballroom', 'park_a', 'park_b', 'theater',
    'sun_terrace', 'rooftop', 'rooftop_2', 'space_cafe',
    'entryhall', 'hallA', 'hallB', 'hallC', 'hallD'
);
SET FOREIGN_KEY_CHECKS = 1;
