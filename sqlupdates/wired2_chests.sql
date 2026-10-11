-- July 2026 Wired chests. Safe to run repeatedly on the fork's MariaDB 10.2+
-- installations and on databases created by Seth's earlier chest migration.

CREATE TABLE IF NOT EXISTS `items_chest_settings` (
  `chest_id` int(11) NOT NULL,
  `allow_open` tinyint(1) NOT NULL DEFAULT 1,
  `allow_donate` tinyint(1) NOT NULL DEFAULT 0,
  `display_name` varchar(30) NOT NULL DEFAULT '',
  `description` varchar(200) NOT NULL DEFAULT '',
  `appearance_state` tinyint(3) unsigned NOT NULL DEFAULT 0,
  `preview_mode` tinyint(3) unsigned NOT NULL DEFAULT 0,
  `preview_amount` tinyint(3) unsigned NOT NULL DEFAULT 1,
  `capacity` int(10) unsigned NOT NULL DEFAULT 0,
  `capacity_level` tinyint(3) unsigned NOT NULL DEFAULT 0,
  `wired_enabled` tinyint(1) NOT NULL DEFAULT 0,
  `locked` tinyint(1) NOT NULL DEFAULT 1,
  `auto_lock` tinyint(1) NOT NULL DEFAULT 0,
  `notify_mode` tinyint(3) unsigned NOT NULL DEFAULT 0,
  `notify_full` tinyint(1) NOT NULL DEFAULT 0,
  `notify_donation` tinyint(1) NOT NULL DEFAULT 0,
  `notify_withdraw` tinyint(1) NOT NULL DEFAULT 0,
  `notify_empty` tinyint(1) NOT NULL DEFAULT 0,
  `notify_wired_transaction` tinyint(1) NOT NULL DEFAULT 0,
  `revision` bigint(20) unsigned NOT NULL DEFAULT 0,
  `created_at` bigint(20) unsigned NOT NULL DEFAULT 0,
  `updated_at` bigint(20) unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (`chest_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `items_chest_settings`
  ADD COLUMN IF NOT EXISTS `capacity_level` tinyint(3) unsigned NOT NULL DEFAULT 0 AFTER `capacity`,
  ADD COLUMN IF NOT EXISTS `wired_enabled` tinyint(1) NOT NULL DEFAULT 0 AFTER `capacity_level`,
  ADD COLUMN IF NOT EXISTS `notify_mode` tinyint(3) unsigned NOT NULL DEFAULT 0 AFTER `auto_lock`,
  ADD COLUMN IF NOT EXISTS `revision` bigint(20) unsigned NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS `created_at` bigint(20) unsigned NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS `updated_at` bigint(20) unsigned NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS `items_chest_storage` (
  `item_id` int(11) NOT NULL,
  `chest_id` int(11) NOT NULL,
  `deposited_by` int(11) NOT NULL DEFAULT 0,
  `deposited_at` bigint(20) unsigned NOT NULL DEFAULT 0,
  `lock_state` tinyint(3) unsigned NOT NULL DEFAULT 0,
  `transaction_id` bigint(20) unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (`item_id`),
  KEY `items_chest_storage_chest_order` (`chest_id`, `deposited_at`, `item_id`),
  KEY `items_chest_storage_transaction` (`transaction_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `items_chest_storage`
  ADD COLUMN IF NOT EXISTS `lock_state` tinyint(3) unsigned NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS `transaction_id` bigint(20) unsigned NOT NULL DEFAULT 0,
  ADD KEY IF NOT EXISTS `items_chest_storage_chest_order` (`chest_id`, `deposited_at`, `item_id`),
  ADD KEY IF NOT EXISTS `items_chest_storage_transaction` (`transaction_id`);

-- The canonical table uses item_id as its primary key. Seth's table instead
-- has an AUTO_INCREMENT primary key plus unique_chest_item. Add a unique item
-- constraint only when neither shape already guarantees it.
SET @items_chest_storage_has_unique_item := (
  SELECT COUNT(*)
  FROM (
    SELECT `INDEX_NAME`
    FROM `information_schema`.`STATISTICS`
    WHERE `TABLE_SCHEMA` = DATABASE()
      AND `TABLE_NAME` = 'items_chest_storage'
      AND `NON_UNIQUE` = 0
    GROUP BY `INDEX_NAME`
    HAVING COUNT(*) = 1
      AND MAX(
        CASE
          WHEN `SEQ_IN_INDEX` = 1 AND `COLUMN_NAME` = 'item_id' THEN 1
          ELSE 0
        END
      ) = 1
  ) AS `unique_item_indexes`
);
SET @items_chest_storage_unique_item_sql := IF(
  @items_chest_storage_has_unique_item = 0,
  'ALTER TABLE `items_chest_storage`
     ADD UNIQUE KEY `items_chest_storage_item_unique` (`item_id`)',
  'DO 0'
);
PREPARE items_chest_storage_unique_item_stmt
  FROM @items_chest_storage_unique_item_sql;
EXECUTE items_chest_storage_unique_item_stmt;
DEALLOCATE PREPARE items_chest_storage_unique_item_stmt;

-- Clean up the duplicate named unique index created by an earlier revision of
-- this migration, but only when another exact item_id unique constraint exists.
SET @items_chest_storage_has_named_item_unique := (
  SELECT COUNT(*)
  FROM `information_schema`.`STATISTICS`
  WHERE `TABLE_SCHEMA` = DATABASE()
    AND `TABLE_NAME` = 'items_chest_storage'
    AND `INDEX_NAME` = 'items_chest_storage_item_unique'
);
SET @items_chest_storage_has_other_unique_item := (
  SELECT COUNT(*)
  FROM (
    SELECT `INDEX_NAME`
    FROM `information_schema`.`STATISTICS`
    WHERE `TABLE_SCHEMA` = DATABASE()
      AND `TABLE_NAME` = 'items_chest_storage'
      AND `NON_UNIQUE` = 0
      AND `INDEX_NAME` <> 'items_chest_storage_item_unique'
    GROUP BY `INDEX_NAME`
    HAVING COUNT(*) = 1
      AND MAX(
        CASE
          WHEN `SEQ_IN_INDEX` = 1 AND `COLUMN_NAME` = 'item_id' THEN 1
          ELSE 0
        END
      ) = 1
  ) AS `other_unique_item_indexes`
);
SET @items_chest_storage_drop_duplicate_item_sql := IF(
  @items_chest_storage_has_named_item_unique > 0
    AND @items_chest_storage_has_other_unique_item > 0,
  'ALTER TABLE `items_chest_storage`
     DROP INDEX `items_chest_storage_item_unique`',
  'DO 0'
);
PREPARE items_chest_storage_drop_duplicate_item_stmt
  FROM @items_chest_storage_drop_duplicate_item_sql;
EXECUTE items_chest_storage_drop_duplicate_item_stmt;
DEALLOCATE PREPARE items_chest_storage_drop_duplicate_item_stmt;

-- Seth stored epoch seconds in a signed INT while the July runtime writes epoch
-- milliseconds. Widen first, then convert only plausible second values so this
-- remains safe to rerun.
ALTER TABLE `items_chest_storage`
  MODIFY COLUMN `deposited_at` bigint(20) unsigned NOT NULL DEFAULT 0;
UPDATE `items_chest_storage`
SET `deposited_at` = `deposited_at` * 1000
WHERE `deposited_at` > 0
  AND `deposited_at` < 100000000000;

CREATE TABLE IF NOT EXISTS `items_chest_coins` (
  `chest_id` int(11) NOT NULL,
  `coins` int(10) unsigned NOT NULL DEFAULT 0,
  `revision` bigint(20) unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (`chest_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `items_chest_coins`
  ADD COLUMN IF NOT EXISTS `revision` bigint(20) unsigned NOT NULL DEFAULT 0;

-- Legacy balances were signed. Negative chest currency is never meaningful.
UPDATE `items_chest_coins`
SET `coins` = 0
WHERE `coins` < 0;
ALTER TABLE `items_chest_coins`
  MODIFY COLUMN `coins` int(10) unsigned NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS `wired_chest_transactions` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `room_id` int(11) NOT NULL,
  `actor_id` int(11) NOT NULL DEFAULT 0,
  `actor_name` varchar(64) NOT NULL DEFAULT '',
  `transaction_type` tinyint(3) unsigned NOT NULL DEFAULT 0,
  `definition_info` varchar(255) NOT NULL DEFAULT '',
  `status` tinyint(3) unsigned NOT NULL DEFAULT 0,
  `created_at` bigint(20) unsigned NOT NULL,
  `expires_at` bigint(20) unsigned NOT NULL DEFAULT 0,
  `completed_at` bigint(20) unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `wired_chest_transactions_room_newest` (`room_id`, `id`),
  KEY `wired_chest_transactions_expiry` (`status`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `wired_chest_transaction_chests` (
  `transaction_id` bigint(20) unsigned NOT NULL,
  `chest_id` int(11) NOT NULL,
  PRIMARY KEY (`transaction_id`, `chest_id`),
  KEY `wired_chest_transaction_chests_chest` (`chest_id`, `transaction_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `wired_chest_transaction_items` (
  `transaction_id` bigint(20) unsigned NOT NULL,
  `item_id` int(11) NOT NULL,
  `direction` tinyint(3) unsigned NOT NULL,
  PRIMARY KEY (`transaction_id`, `item_id`),
  UNIQUE KEY `wired_chest_transaction_items_reserved` (`item_id`),
  KEY `wired_chest_transaction_items_transaction` (`transaction_id`, `direction`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `wired_chest_logs` (
  `transaction_id` bigint(20) unsigned NOT NULL,
  `room_id` int(11) NOT NULL,
  `transaction_type` tinyint(3) unsigned NOT NULL DEFAULT 0,
  `definition_info` varchar(255) NOT NULL DEFAULT '',
  `user_id` int(11) NOT NULL DEFAULT 0,
  `username` varchar(64) NOT NULL DEFAULT '',
  `created_at` bigint(20) unsigned NOT NULL,
  `chest_count` int(10) unsigned NOT NULL DEFAULT 0,
  `withdraw_furni_count` int(10) unsigned NOT NULL DEFAULT 0,
  `deposit_furni_count` int(10) unsigned NOT NULL DEFAULT 0,
  `withdraw_coins_count` int(10) unsigned NOT NULL DEFAULT 0,
  `deposit_coins_count` int(10) unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (`transaction_id`),
  KEY `wired_chest_logs_room_transaction` (`room_id`, `transaction_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Upgrade the earlier Seth log table in place. It used an AUTO_INCREMENT `id`,
-- text transaction types and differently named counters. Keeping its `id`
-- primary key is harmless; the runtime addresses logs by the new unique
-- `transaction_id`.
ALTER TABLE `wired_chest_logs`
  ADD COLUMN IF NOT EXISTS `transaction_id` bigint(20) unsigned NULL,
  ADD COLUMN IF NOT EXISTS `definition_info` varchar(255) NOT NULL DEFAULT '',
  ADD COLUMN IF NOT EXISTS `withdraw_furni_count` int(10) unsigned NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS `deposit_furni_count` int(10) unsigned NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS `withdraw_coins_count` int(10) unsigned NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS `deposit_coins_count` int(10) unsigned NOT NULL DEFAULT 0;

SET @wired_chest_logs_has_legacy_id = (
  SELECT COUNT(*) > 0
  FROM `information_schema`.`COLUMNS`
  WHERE `TABLE_SCHEMA` = DATABASE()
    AND `TABLE_NAME` = 'wired_chest_logs'
    AND `COLUMN_NAME` = 'id'
);

SET @wired_chest_logs_upgrade_sql = IF(
  @wired_chest_logs_has_legacy_id,
  'UPDATE `wired_chest_logs`
     SET `transaction_id` = `id`
   WHERE `transaction_id` IS NULL OR `transaction_id` = 0',
  'DO 0'
);
PREPARE wired_chest_logs_upgrade_stmt FROM @wired_chest_logs_upgrade_sql;
EXECUTE wired_chest_logs_upgrade_stmt;
DEALLOCATE PREPARE wired_chest_logs_upgrade_stmt;

SET @wired_chest_logs_has_legacy_counts = (
  SELECT COUNT(*) = 4
  FROM `information_schema`.`COLUMNS`
  WHERE `TABLE_SCHEMA` = DATABASE()
    AND `TABLE_NAME` = 'wired_chest_logs'
    AND `COLUMN_NAME` IN (
      'withdrawal_furni', 'deposit_furni', 'withdrawal_coins', 'deposit_coins'
    )
);

SET @wired_chest_logs_counts_sql = IF(
  @wired_chest_logs_has_legacy_counts,
  'UPDATE `wired_chest_logs`
     SET `withdraw_furni_count` =
           IF(`withdraw_furni_count` = 0, GREATEST(`withdrawal_furni`, 0),
              `withdraw_furni_count`),
         `deposit_furni_count` =
           IF(`deposit_furni_count` = 0, GREATEST(`deposit_furni`, 0),
              `deposit_furni_count`),
         `withdraw_coins_count` =
           IF(`withdraw_coins_count` = 0, GREATEST(`withdrawal_coins`, 0),
              `withdraw_coins_count`),
         `deposit_coins_count` =
           IF(`deposit_coins_count` = 0, GREATEST(`deposit_coins`, 0),
              `deposit_coins_count`)
   WHERE `transaction_id` IS NOT NULL
     AND (
       (`withdraw_furni_count` = 0 AND `withdrawal_furni` <> 0)
       OR (`deposit_furni_count` = 0 AND `deposit_furni` <> 0)
       OR (`withdraw_coins_count` = 0 AND `withdrawal_coins` <> 0)
       OR (`deposit_coins_count` = 0 AND `deposit_coins` <> 0)
     )',
  'DO 0'
);
PREPARE wired_chest_logs_counts_stmt FROM @wired_chest_logs_counts_sql;
EXECUTE wired_chest_logs_counts_stmt;
DEALLOCATE PREPARE wired_chest_logs_counts_stmt;

-- Convert Seth's text transaction type to July's numeric code. Retain the old
-- label in definition_info so migrated history remains diagnosable.
UPDATE `wired_chest_logs`
SET `definition_info` =
      CASE
        WHEN `definition_info` = ''
          AND (
            CAST(`transaction_type` AS CHAR) NOT REGEXP '^[0-9]+$'
            OR CAST(`transaction_type` AS UNSIGNED) > 255
          )
          THEN CONCAT('legacy:', `transaction_type`)
        ELSE `definition_info`
      END,
    `transaction_type` =
      CASE
        WHEN CAST(`transaction_type` AS CHAR) REGEXP '^[0-9]+$'
          AND CAST(`transaction_type` AS UNSIGNED) <= 255
          THEN CAST(`transaction_type` AS UNSIGNED)
        ELSE 0
      END;

-- The old details_json snapshot is retained for administrators, but July log
-- inserts no longer populate it. Make it nullable or strict SQL mode rejects
-- every new log written after an in-place Seth upgrade.
SET @wired_chest_logs_has_details_json := (
  SELECT COUNT(*)
  FROM `information_schema`.`COLUMNS`
  WHERE `TABLE_SCHEMA` = DATABASE()
    AND `TABLE_NAME` = 'wired_chest_logs'
    AND `COLUMN_NAME` = 'details_json'
);
SET @wired_chest_logs_details_json_sql := IF(
  @wired_chest_logs_has_details_json = 1,
  'ALTER TABLE `wired_chest_logs`
     MODIFY COLUMN `details_json` TEXT NULL',
  'DO 0'
);
PREPARE wired_chest_logs_details_json_stmt
  FROM @wired_chest_logs_details_json_sql;
EXECUTE wired_chest_logs_details_json_stmt;
DEALLOCATE PREPARE wired_chest_logs_details_json_stmt;

ALTER TABLE `wired_chest_logs`
  MODIFY COLUMN `transaction_id` bigint(20) unsigned NOT NULL,
  MODIFY COLUMN `transaction_type` tinyint(3) unsigned NOT NULL DEFAULT 0;

-- A fresh July table has transaction_id as its primary key, while Seth's table
-- keeps id as the primary key. Add only the unique/index shapes actually
-- missing; checking names alone creates duplicate indexes on fresh installs.
SET @wired_chest_logs_has_unique_transaction := (
  SELECT COUNT(*)
  FROM (
    SELECT `INDEX_NAME`
    FROM `information_schema`.`STATISTICS`
    WHERE `TABLE_SCHEMA` = DATABASE()
      AND `TABLE_NAME` = 'wired_chest_logs'
      AND `NON_UNIQUE` = 0
    GROUP BY `INDEX_NAME`
    HAVING COUNT(*) = 1
      AND MAX(
        CASE
          WHEN `SEQ_IN_INDEX` = 1 AND `COLUMN_NAME` = 'transaction_id' THEN 1
          ELSE 0
        END
      ) = 1
  ) AS `unique_transaction_indexes`
);
SET @wired_chest_logs_unique_transaction_sql := IF(
  @wired_chest_logs_has_unique_transaction = 0,
  'ALTER TABLE `wired_chest_logs`
     ADD UNIQUE KEY `wired_chest_logs_transaction_unique` (`transaction_id`)',
  'DO 0'
);
PREPARE wired_chest_logs_unique_transaction_stmt
  FROM @wired_chest_logs_unique_transaction_sql;
EXECUTE wired_chest_logs_unique_transaction_stmt;
DEALLOCATE PREPARE wired_chest_logs_unique_transaction_stmt;

SET @wired_chest_logs_has_room_transaction := (
  SELECT COUNT(*)
  FROM (
    SELECT `INDEX_NAME`
    FROM `information_schema`.`STATISTICS`
    WHERE `TABLE_SCHEMA` = DATABASE()
      AND `TABLE_NAME` = 'wired_chest_logs'
    GROUP BY `INDEX_NAME`
    HAVING GROUP_CONCAT(
      `COLUMN_NAME` ORDER BY `SEQ_IN_INDEX` SEPARATOR ','
    ) = 'room_id,transaction_id'
  ) AS `room_transaction_indexes`
);
SET @wired_chest_logs_room_transaction_sql := IF(
  @wired_chest_logs_has_room_transaction = 0,
  'ALTER TABLE `wired_chest_logs`
     ADD KEY `wired_chest_logs_room_transaction` (`room_id`, `transaction_id`)',
  'DO 0'
);
PREPARE wired_chest_logs_room_transaction_stmt
  FROM @wired_chest_logs_room_transaction_sql;
EXECUTE wired_chest_logs_room_transaction_stmt;
DEALLOCATE PREPARE wired_chest_logs_room_transaction_stmt;

-- Remove only duplicate indexes created by an earlier revision of this file.
-- Seth's differently shaped room_id/id index and its required transaction_id
-- unique index are deliberately retained.
SET @wired_chest_logs_primary_columns := (
  SELECT GROUP_CONCAT(`COLUMN_NAME` ORDER BY `SEQ_IN_INDEX` SEPARATOR ',')
  FROM `information_schema`.`STATISTICS`
  WHERE `TABLE_SCHEMA` = DATABASE()
    AND `TABLE_NAME` = 'wired_chest_logs'
    AND `INDEX_NAME` = 'PRIMARY'
);
SET @wired_chest_logs_has_named_transaction_unique := (
  SELECT COUNT(*)
  FROM `information_schema`.`STATISTICS`
  WHERE `TABLE_SCHEMA` = DATABASE()
    AND `TABLE_NAME` = 'wired_chest_logs'
    AND `INDEX_NAME` = 'wired_chest_logs_transaction_unique'
);
SET @wired_chest_logs_drop_duplicate_unique_sql := IF(
  @wired_chest_logs_primary_columns = 'transaction_id'
    AND @wired_chest_logs_has_named_transaction_unique > 0,
  'ALTER TABLE `wired_chest_logs`
     DROP INDEX `wired_chest_logs_transaction_unique`',
  'DO 0'
);
PREPARE wired_chest_logs_drop_duplicate_unique_stmt
  FROM @wired_chest_logs_drop_duplicate_unique_sql;
EXECUTE wired_chest_logs_drop_duplicate_unique_stmt;
DEALLOCATE PREPARE wired_chest_logs_drop_duplicate_unique_stmt;

SET @wired_chest_logs_room_newest_columns := (
  SELECT GROUP_CONCAT(`COLUMN_NAME` ORDER BY `SEQ_IN_INDEX` SEPARATOR ',')
  FROM `information_schema`.`STATISTICS`
  WHERE `TABLE_SCHEMA` = DATABASE()
    AND `TABLE_NAME` = 'wired_chest_logs'
    AND `INDEX_NAME` = 'wired_chest_logs_room_newest'
);
SET @wired_chest_logs_has_named_room_transaction := (
  SELECT COUNT(*)
  FROM `information_schema`.`STATISTICS`
  WHERE `TABLE_SCHEMA` = DATABASE()
    AND `TABLE_NAME` = 'wired_chest_logs'
    AND `INDEX_NAME` = 'wired_chest_logs_room_transaction'
);
SET @wired_chest_logs_drop_duplicate_room_sql := IF(
  @wired_chest_logs_room_newest_columns = 'room_id,transaction_id'
    AND @wired_chest_logs_has_named_room_transaction > 0,
  'ALTER TABLE `wired_chest_logs`
     DROP INDEX `wired_chest_logs_room_newest`',
  'DO 0'
);
PREPARE wired_chest_logs_drop_duplicate_room_stmt
  FROM @wired_chest_logs_drop_duplicate_room_sql;
EXECUTE wired_chest_logs_drop_duplicate_room_stmt;
DEALLOCATE PREPARE wired_chest_logs_drop_duplicate_room_stmt;

-- Reserve every migrated legacy id in the authoritative transaction sequence.
-- Explicit AUTO_INCREMENT inserts advance the next generated id past the
-- retained history, preventing a new transaction from colliding with it.
INSERT IGNORE INTO `wired_chest_transactions`
  (`id`, `room_id`, `actor_id`, `actor_name`, `transaction_type`,
   `definition_info`, `status`, `created_at`, `expires_at`, `completed_at`)
SELECT
  `transaction_id`, `room_id`, `user_id`, `username`,
  CAST(`transaction_type` AS UNSIGNED), `definition_info`,
  1, `created_at`, 0, `created_at`
FROM `wired_chest_logs`;

CREATE TABLE IF NOT EXISTS `wired_chest_log_chests` (
  `transaction_id` bigint(20) unsigned NOT NULL,
  `chest_id` int(11) NOT NULL,
  PRIMARY KEY (`transaction_id`, `chest_id`),
  KEY `wired_chest_log_chests_chest_newest` (`chest_id`, `transaction_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Snapshot item types are retained because the item rows change owner (or are
-- redeemed) as part of the transaction. July's detail packet groups by this
-- exact wall/type/poster tuple and marks legacy rows without snapshots as
-- incomplete rather than inventing item data.
CREATE TABLE IF NOT EXISTS `wired_chest_log_items` (
  `transaction_id` bigint(20) unsigned NOT NULL,
  `direction` tinyint(3) unsigned NOT NULL,
  `is_wall` tinyint(1) NOT NULL DEFAULT 0,
  `type_id` int(11) NOT NULL,
  `legacy_poster_id` varchar(255) NOT NULL DEFAULT '',
  `amount` int(10) unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (
    `transaction_id`, `direction`, `is_wall`, `type_id`, `legacy_poster_id`
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- The primary key already begins with transaction_id/direction and serves the
-- detail query without a duplicate secondary index.
ALTER TABLE `wired_chest_log_items`
  DROP INDEX IF EXISTS `wired_chest_log_items_transaction`;

-- July retains at most five chest notifications while an owner is offline.
CREATE TABLE IF NOT EXISTS `users_chest_notifications` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `notification_type` varchar(40) NOT NULL,
  `chest_id` int(11) NOT NULL,
  `room_id` int(11) NOT NULL,
  `actor_name` varchar(64) NOT NULL DEFAULT '',
  `chest_name` varchar(64) NOT NULL DEFAULT '',
  `created_at` bigint(20) unsigned NOT NULL,
  PRIMARY KEY (`id`),
  KEY `users_chest_notifications_owner_oldest` (`user_id`, `id`),
  KEY `users_chest_notifications_chest` (`chest_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `users_chest_notifications`
  ADD KEY IF NOT EXISTS `users_chest_notifications_owner_oldest` (`user_id`, `id`),
  ADD KEY IF NOT EXISTS `users_chest_notifications_chest` (`chest_id`, `id`);

INSERT INTO `emulator_settings` (`key`, `value`) VALUES
  ('wired.chests_starter_infix', '_starter'),
  ('wired.chests.upgrade_cost_credits', '10'),
  ('wired.chests.upgrade_cost_diamonds', '10'),
  ('wired.chests_max_logs', '2000'),
  ('wired.coins_chest.initial_capacity', '5000'),
  ('wired.coins_chest.max_upgrades', '19'),
  ('wired.coins_chest.starter_capacity', '500'),
  ('wired.coins_chest.upgrade_capacity', '5000'),
  ('wired.furni_chest.initial_capacity', '1000'),
  ('wired.furni_chest.max_upgrades', '9'),
  ('wired.furni_chest.starter_capacity', '100'),
  ('wired.furni_chest.upgrade_capacity', '1000')
ON DUPLICATE KEY UPDATE `value` = `value`;

-- Official furnidata uses a visual-variant suffix for storage chests while the
-- runtime deliberately shares one interaction implementation per chest kind.
-- The other July chest/Wired class names already match their registered
-- interaction names exactly.
UPDATE `items_base`
SET `interaction_type` = 'wf_storage_furni'
WHERE `item_name` IN (
  'wf_storage_furni1',
  'wf_storage_furni2',
  'wf_storage_furni_starter'
)
  AND (
    `interaction_type` = 'default'
    OR `interaction_type` = 'wf_storage_furni'
    OR `interaction_type` = `item_name`
  );

UPDATE `items_base`
SET `interaction_type` = 'wf_storage_coins'
WHERE `item_name` IN (
  'wf_storage_coins1',
  'wf_storage_coins2'
)
  AND (
    `interaction_type` = 'default'
    OR `interaction_type` = 'wf_storage_coins'
    OR `interaction_type` = `item_name`
  );
