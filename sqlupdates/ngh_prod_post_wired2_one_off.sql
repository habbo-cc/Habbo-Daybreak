-- NGH production one-off update: Quests, complete Wired 2, Wired chests,
-- Original BattleBall, and the BattleBall public lobby.
-- Generated from the current Arcturus-Community branch on 2026-08-15.
--
-- IMPORTANT:
--   1. Back up the target database first. MariaDB DDL auto-commits.
--   2. Select the target database before running this file.
--      Example: mysql -uUSER -p DATABASE_NAME < this_file.sql
--   3. Do not add USE ngh_prod here: the live database may have another name.
--
-- Safe for the audited NGH production schema and safe to rerun. Existing
-- operator-tuned setting values and user/runtime data are preserved. The only
-- replaced data is the authoritative bb_lobby_1 public-room item layout.

SET NAMES utf8mb4;
SET @ngh_upgrade_database := DATABASE();
SELECT IF(@ngh_upgrade_database IS NULL,
  'ERROR: no database selected',
  CONCAT('Applying NGH feature update to `', @ngh_upgrade_database, '`')) AS `ngh_upgrade`;

-- ---------------------------------------------------------------------------




-- Wired 2 foundation




-- ---------------------------------------------------------------------------




-- Wired 2.0 advanced source settings: generic per-item persistence of
-- furniSourceTypes/userSourceTypes (JSON {"f":[...],"u":[...]}, empty = defaults).
ALTER TABLE `items`
  ADD COLUMN IF NOT EXISTS `wired_sources` VARCHAR(512) NOT NULL DEFAULT '';

-- Wired 2.0 / May 2026 chat: persist the free-flow chat font-size selector.
-- The metadata guard is idempotent on MariaDB 10.2 and remains compatible with
-- older MySQL-family deployments that lack ADD COLUMN IF NOT EXISTS.

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users_settings'
      AND COLUMN_NAME = 'chat_size_preference'
);

SET @sql := IF(
    @column_exists = 0,
    'ALTER TABLE `users_settings` ADD COLUMN `chat_size_preference` TINYINT(1) NOT NULL DEFAULT 0 AFTER `chat_color`',
    'SELECT 1'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

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

-- Explicitly seed runtime-registered variable limits so the live database is
-- complete before first boot. Existing operator values win.
INSERT INTO `emulator_settings` (`key`, `value`) VALUES
  ('wired.variables.max.definitions.per.room', '512'),
  ('wired.variables.max.values.per.room', '50000'),
  ('wired.variables.max.values.per.definition', '10000'),
  ('wired.variables.max.diff.hashes', '2048')
ON DUPLICATE KEY UPDATE `value` = `value`;

-- ---------------------------------------------------------------------------




-- Wired 2 persistence, menu and monitoring




-- ---------------------------------------------------------------------------




-- Durable definition/source registry for July Reference/Echo variable aliases.
-- Shared source definitions are indexed here as well as alias edges because
-- room_wired_variable_definitions deliberately stores only hashes.

CREATE TABLE IF NOT EXISTS `room_wired_variable_alias_definitions` (
  `definition_item_id` INT(11) NOT NULL,
  `room_id` INT(11) NOT NULL,
  `variable_id` VARCHAR(64) NOT NULL,
  `variable_type` TINYINT(3) NOT NULL,
  `holder_scope` TINYINT(3) NOT NULL,
  `variable_name` VARCHAR(40) NOT NULL,
  `availability` INT(11) NOT NULL,
  `has_value` TINYINT(1) NOT NULL DEFAULT 0,
  `definition_version` INT(11) NOT NULL DEFAULT 1,
  `definition_hash` INT(11) NOT NULL,
  `source_room_id` INT(11) NULL,
  `source_variable_id` VARCHAR(64) NULL,
  `read_only` TINYINT(1) NOT NULL DEFAULT 0,
  `updated_at` BIGINT(20) NOT NULL DEFAULT 0,
  PRIMARY KEY (`definition_item_id`),
  UNIQUE KEY `uq_wired_variable_alias_room_variable` (`room_id`,`variable_id`),
  KEY `idx_wired_variable_alias_source` (`source_room_id`,`source_variable_id`),
  KEY `idx_wired_variable_alias_shared` (`availability`,`room_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Preserve registries created by the early port while using the domain name
-- shared by the other Wired variable tables.
SET @wired_alias_has_variable_type := (
  SELECT COUNT(*)
  FROM `information_schema`.`COLUMNS`
  WHERE `TABLE_SCHEMA` = DATABASE()
    AND `TABLE_NAME` = 'room_wired_variable_alias_definitions'
    AND `COLUMN_NAME` = 'variable_type'
);
SET @wired_alias_has_protocol_type := (
  SELECT COUNT(*)
  FROM `information_schema`.`COLUMNS`
  WHERE `TABLE_SCHEMA` = DATABASE()
    AND `TABLE_NAME` = 'room_wired_variable_alias_definitions'
    AND `COLUMN_NAME` = 'protocol_type'
);
SET @wired_alias_variable_type_sql := IF(
  @wired_alias_has_variable_type = 0
    AND @wired_alias_has_protocol_type = 1,
  'ALTER TABLE `room_wired_variable_alias_definitions`
     CHANGE COLUMN `protocol_type` `variable_type` TINYINT(3) NOT NULL',
  'DO 0'
);
PREPARE wired_alias_variable_type_stmt FROM @wired_alias_variable_type_sql;
EXECUTE wired_alias_variable_type_stmt;
DEALLOCATE PREPARE wired_alias_variable_type_stmt;

-- Room-scoped Wired 2.0 variable manager.
--
-- Definition furniture configuration remains in items.wired_data. The compact
-- definition table records only the deterministic signature used to decide when
-- a reload is the same definition rather than a new synchronized revision.
CREATE TABLE IF NOT EXISTS `room_wired_variable_revisions` (
  `room_id` INT(11) NOT NULL,
  `revision` BIGINT(20) NOT NULL DEFAULT 0,
  `updated_at` BIGINT(20) NOT NULL DEFAULT 0,
  PRIMARY KEY (`room_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `room_wired_variable_definitions` (
  `room_id` INT(11) NOT NULL,
  `variable_id` VARCHAR(64) NOT NULL,
  `definition_hash` INT(11) NOT NULL,
  PRIMARY KEY (`room_id`, `variable_id`),
  KEY `idx_wired_variable_definition_id` (`variable_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `room_wired_variable_values` (
  `room_id` INT(11) NOT NULL,
  `variable_id` VARCHAR(64) NOT NULL,
  `scope_type` TINYINT(3) UNSIGNED NOT NULL,
  `holder_id` INT(11) NOT NULL,
  `value` INT(11) NOT NULL DEFAULT 0,
  `created_at` BIGINT(20) NOT NULL DEFAULT 0,
  `updated_at` BIGINT(20) NOT NULL DEFAULT 0,
  `revision` BIGINT(20) NOT NULL DEFAULT 0,
  PRIMARY KEY (`room_id`, `variable_id`, `scope_type`, `holder_id`),
  KEY `idx_wired_variable_holder_cleanup` (`room_id`, `scope_type`, `holder_id`),
  KEY `idx_wired_variable_revision` (`room_id`, `revision`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- The primary key already serves every room_id/variable_id lookup. Remove the
-- duplicate secondary index left by early installations.
ALTER TABLE `room_wired_variable_values`
  DROP INDEX IF EXISTS `idx_wired_variable_definition_values`;


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

-- ---------------------------------------------------------------------------




-- Wired 2 chests




-- ---------------------------------------------------------------------------




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

-- ---------------------------------------------------------------------------




-- Wired 2 environment interaction mappings




-- ---------------------------------------------------------------------------




-- July 2026 AIR Area Hide furniture. Safe to run repeatedly and does not
-- rewrite installations that deliberately assigned a non-default interaction.
UPDATE items_base
SET interaction_type = 'area_hide'
WHERE item_name = 'conf_area_hide'
  AND interaction_type IN ('default', 'conf_area_hide', 'area_hide');

-- July 2026 AIR room configuration controllers. Safe to run repeatedly and
-- does not rewrite installations that deliberately use custom interactions.
UPDATE items_base
SET interaction_type = 'conf_handitem_block'
WHERE item_name = 'conf_handitem_block'
  AND interaction_type IN ('default', 'conf_handitem_block');

UPDATE items_base
SET interaction_type = 'conf_invis_control'
WHERE item_name = 'conf_invis_control'
  AND interaction_type IN ('default', 'conf_invis_control');

-- Every July invisible furni variant shares the same server interaction.
-- Wired trigger 21 distinguishes the actual click target by item_name
-- (`room_invisible_click_tile`), not by interaction_type.
UPDATE items_base
SET interaction_type = 'room_invisible_tile'
WHERE item_name IN (
    'room_invisible_block',
    'room_invisible_block2',
    'room_invisible_block3',
    'room_invisible_block4',
    'room_invisible_click_tile',
    'room_invisible_lay_tile',
    'room_invisible_sit_tile'
)
  AND (
    interaction_type = 'default'
    OR interaction_type = 'room_invisible_tile'
    OR interaction_type = item_name
  );

UPDATE items_base
SET interaction_type = 'conf_invis_control'
WHERE item_name = 'conf_invis_control'
  AND interaction_type IN ('default', 'conf_invis_control');

-- ---------------------------------------------------------------------------
-- Quests (corrected, idempotent form of sqlupdates/quests.sql)
-- ---------------------------------------------------------------------------
INSERT INTO `emulator_settings` (`key`, `value`)
VALUES ('hotel.quest.room_pulse.interval', '5')
ON DUPLICATE KEY UPDATE `value` = `value`;

INSERT INTO `emulator_texts` (`key`, `value`) VALUES
  ('commands.keys.cmd_update_quests', 'update_quests;reload_quests'),
  ('commands.succes.cmd_update_quests.updated', 'Quests reloaded successfully.')
ON DUPLICATE KEY UPDATE `value` = `value`;

ALTER TABLE `permissions`
  ADD COLUMN IF NOT EXISTS `cmd_update_quests` ENUM('0', '1') NOT NULL DEFAULT '0';

CREATE TABLE IF NOT EXISTS `quests` (
  `id` int(11) NOT NULL,
  `campaign_id` int(11) NOT NULL,
  `activity_point_type` int(11) NOT NULL DEFAULT 0,
  `trigger_type` varchar(64) NOT NULL DEFAULT '',
  `target_type` varchar(64) NOT NULL DEFAULT '',
  `target_value` varchar(255) NOT NULL DEFAULT '',
  `image_version` varchar(64) NOT NULL DEFAULT '',
  `reward_currency_amount` int(11) NOT NULL DEFAULT 0,
  `localization_code` varchar(64) NOT NULL DEFAULT '',
  `total_steps` int(11) NOT NULL DEFAULT 1,
  `sort_order` int(11) NOT NULL DEFAULT 0,
  `catalog_page_name` varchar(64) NOT NULL DEFAULT '',
  `chain_code` varchar(64) NOT NULL DEFAULT '',
  `easy` tinyint(1) NOT NULL DEFAULT 1,
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  KEY `quests_campaign_order` (`campaign_id`, `sort_order`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `quests_campaigns` (
  `id` int(11) NOT NULL,
  `code` varchar(64) NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  UNIQUE KEY `quests_campaigns_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `quests_conditions` (
  `id` int(11) NOT NULL,
  `quest_id` int(11) NOT NULL,
  `condition_type` varchar(64) NOT NULL,
  `condition_key` varchar(64) DEFAULT NULL,
  `condition_value` varchar(255) DEFAULT NULL,
  `condition_extra` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `quests_conditions_quest` (`quest_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `users_quests` (
  `user_id` int(11) NOT NULL,
  `quest_id` int(11) NOT NULL,
  `accepted` tinyint(1) NOT NULL DEFAULT 0,
  `completed_steps` int(11) NOT NULL DEFAULT 0,
  `completed_at` int(11) NOT NULL DEFAULT 0,
  `claimed_at` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`user_id`, `quest_id`),
  KEY `users_quests_quest` (`quest_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Upgrade a partially installed copy of the original migration, whose tables
-- had no keys. The audited production database has none of these tables yet.
SET @ngh_sql := IF(
  (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
   WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'quests'
     AND CONSTRAINT_TYPE = 'PRIMARY KEY') = 0,
  'ALTER TABLE `quests` ADD PRIMARY KEY (`id`)', 'DO 0');
PREPARE ngh_stmt FROM @ngh_sql; EXECUTE ngh_stmt; DEALLOCATE PREPARE ngh_stmt;

SET @ngh_sql := IF(
  (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
   WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'quests_campaigns'
     AND CONSTRAINT_TYPE = 'PRIMARY KEY') = 0,
  'ALTER TABLE `quests_campaigns` ADD PRIMARY KEY (`id`)', 'DO 0');
PREPARE ngh_stmt FROM @ngh_sql; EXECUTE ngh_stmt; DEALLOCATE PREPARE ngh_stmt;

SET @ngh_sql := IF(
  (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
   WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'quests_conditions'
     AND CONSTRAINT_TYPE = 'PRIMARY KEY') = 0,
  'ALTER TABLE `quests_conditions` ADD PRIMARY KEY (`id`)', 'DO 0');
PREPARE ngh_stmt FROM @ngh_sql; EXECUTE ngh_stmt; DEALLOCATE PREPARE ngh_stmt;

SET @ngh_sql := IF(
  (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
   WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'users_quests'
     AND CONSTRAINT_TYPE = 'PRIMARY KEY') = 0,
  'ALTER TABLE `users_quests` ADD PRIMARY KEY (`user_id`, `quest_id`)', 'DO 0');
PREPARE ngh_stmt FROM @ngh_sql; EXECUTE ngh_stmt; DEALLOCATE PREPARE ngh_stmt;

ALTER TABLE `quests`
  ADD KEY IF NOT EXISTS `quests_campaign_order` (`campaign_id`, `sort_order`, `id`);
ALTER TABLE `quests_campaigns`
  ADD UNIQUE KEY IF NOT EXISTS `quests_campaigns_code` (`code`);
ALTER TABLE `quests_conditions`
  ADD KEY IF NOT EXISTS `quests_conditions_quest` (`quest_id`);
ALTER TABLE `users_quests`
  ADD KEY IF NOT EXISTS `users_quests_quest` (`quest_id`);

INSERT INTO `quests`
  (`id`, `campaign_id`, `activity_point_type`, `trigger_type`, `target_type`,
   `target_value`, `image_version`, `reward_currency_amount`, `localization_code`,
   `total_steps`, `sort_order`, `catalog_page_name`, `chain_code`, `easy`, `enabled`)
VALUES
(1, 1, 0, 'ENTER_OTHER_USERS_ROOM', '', '', '', 10, '1761299476975', 2, 0, '', '', 1, 1),
(2, 1, 0, 'FIND_HAND_ITEM', 'hand_item_id', '1', '', 10, '1761579872551', 1, 1, '', '', 1, 1),
(3, 1, 0, 'ROOM_PULSE', 'base_item_id', '6', '', 10, '1761580701585', 6, 2, '', '', 1, 1),
(4, 1, 0, 'FIND_HAND_ITEM', 'hand_item_id', '2', '', 10, '1761581055462', 1, 3, '', '', 1, 1),
(5, 1, 0, 'ROOM_PULSE', 'base_item_id', '6', '', 10, '1761646163810', 6, 4, '', '', 1, 1),
(6, 1, 0, 'SWITCH_ITEM_STATE', 'base_item_id', '7', '', 10, '1761647062487', 1, 5, '', '', 1, 1),
(7, 1, 0, 'ROOM_PULSE', '', '', '', 10, '1761647456344', 1, 6, '', '', 1, 1),
(8, 1, 0, 'CHANGE_MOTTO', '', '', '', 10, '1761648186978', 1, 7, '', '', 1, 1),
(9, 1, 0, 'CHAT_WITH_SOMEONE', '', '', '', 10, '1761648572954', 1, 8, '', '', 1, 1),
(10, 1, 0, 'CHANGE_FIGURE', '', '', '', 10, '1761648741810', 1, 9, '', '', 1, 1),
(11, 1, 0, 'SWITCH_ITEM_STATE', 'base_item_id', '7', '', 10, '1761648878323', 3, 10, '', '', 1, 1),
(12, 1, 1, 'GIVE_RESPECT', '', '', '', 10, '1761650198783', 1, 11, '', '', 1, 1),
(13, 1, 2, 'WEAR_BADGE', 'badge_code', 'XM8', '', 10, '1761654735890', 1, 12, '', '', 1, 1),
(14, 1, 0, 'WAVE', '', '', '', 10, '1761655367136', 2, 13, '', '', 1, 1),
(15, 1, 0, 'DANCE', '', '', '', 10, '1761658240298', 1, 14, '', '', 1, 1),
(16, 1, 0, 'FIND_STUFF', 'base_item_id', '6', '', 10, '1761658512806', 1, 15, '', '', 1, 1),
(17, 1, 0, 'SWITCH_ITEM_STATE', 'base_item_id', '7', '', 10, '1761659793882', 1, 16, '', '', 1, 1),
(18, 1, 0, 'PLACE_ITEM', '', '', '', 10, '1761661887742', 1, 17, '', '', 1, 1),
(19, 1, 0, 'PLACE_ITEM', 'base_item_id', '7', '', 10, '1761662926811', 1, 18, '', '', 1, 1),
(20, 1, 0, 'MOVE_ITEM', 'base_item_id', '6', '', 10, '1761662961113', 1, 19, '', '', 1, 1),
(21, 1, 0, 'ROTATE_ITEM', 'base_item_id', '7', '', 10, '1761663889314', 1, 20, '', '', 1, 1),
(22, 1, 0, 'BUY_FROM_CATALOGUE', 'catalog_item_id', '7', '', 10, '1761664164296', 1, 21, '', '', 1, 1),
(23, 1, 0, 'WALK_OVER_STUFF', 'base_item_id', '10', '', 10, '1761665188454', 1, 22, '', '', 1, 1),
(24, 1, 0, 'CRAFT_PRODUCT', 'craft_reward', '2675', '', 10, '1761666539690', 1, 23, '', '', 1, 1),
(25, 1, 0, 'REQUEST_FRIEND', '', '', '', 10, '1761666799701', 1, 24, '', '', 1, 1),
(26, 1, 0, 'SEND_MESSENGER_MESSAGE', '', '', '', 10, '1761728780398', 1, 25, '', '', 1, 1),
(27, 1, 0, 'SEND_MESSENGER_INVITE', '', '', '', 10, '1761731096129', 1, 26, '', '', 1, 1),
(28, 1, 0, 'FOLLOW_FRIEND', '', '', '', 10, '1761731141046', 1, 27, '', '', 1, 1),
(29, 1, 0, 'SET_RELATIONSHIP_STATUS', 'relation_ship', '2', '', 10, '1761731259486', 1, 28, '', '', 1, 1),
(30, 1, 0, 'TELEPORT', '', '', '', 10, '1761731326196', 1, 29, '', '', 1, 1),
(31, 1, 0, 'PET_RESPECT', '', '', '', 10, '1761731487186', 2, 30, '', '', 1, 1),
(32, 1, 0, 'PET_LEVEL', '', '', '', 10, '1761731524375', 1, 31, '', '', 1, 1),
(33, 1, 0, 'PET_EAT', '', '', '', 10, '1761731597491', 1, 32, '', '', 1, 1),
(34, 1, 0, 'KICK_BALL', 'base_item_id', '4192', '', 10, '1761732734334', 1, 33, '', '', 1, 1),
(35, 1, 0, 'KICK_BALL', 'base_item_id', '1494', '', 10, '1761735280310', 1, 34, '', '', 1, 1),
(36, 1, 0, 'SWIM', '', '', '', 10, '1761735305842', 1, 35, '', '', 1, 1),
(37, 1, 0, 'CREATE_ROOM', '', '', '', 10, '1761825579021', 1, 36, '', '', 1, 1),
(38, 1, 0, 'FRIEND_FURNI_LOCKED', '', '', '', 10, '1761905903471', 1, 37, '', '', 1, 1),
(39, 1, 0, 'GAME_BB_LOCK_TILES', 'tile_count_min', '10', '', 10, '1762184918549', 1, 38, '', '', 1, 1),
(40, 1, 0, 'GAME_BB_LOCK_TILE', '', '', '', 10, '1762263648686', 30, 39, '', '', 1, 1),
(41, 1, 0, 'GAME_BB_LOCK_TILE', '', '', '', 10, '1762263673586', 30, 40, '', '', 1, 1),
(42, 1, 0, 'GAME_WIN_GAME', 'game_type', 'battle_banzai', '', 10, '1762264146566', 1, 41, '', '', 1, 1),
(43, 1, 0, 'GAME_PLAY_GAME', 'game_type', 'battle_banzai', '', 10, '1762355997065', 1, 42, '', '', 1, 1),
(44, 1, 0, 'CHAT_WITH_SOMEONE', '', '', '', 5, '', 1, 43, '', '', 1, 1)
ON DUPLICATE KEY UPDATE `id` = VALUES(`id`);

INSERT INTO `quests_campaigns` (`id`, `code`, `enabled`)
VALUES
(1, 'xmas25_weekquests', 1)
ON DUPLICATE KEY UPDATE `id` = VALUES(`id`);

INSERT INTO `quests_conditions`
  (`id`, `quest_id`, `condition_type`, `condition_key`, `condition_value`, `condition_extra`)
VALUES
(1, 3, 'FIGURE_PART', 'ha', '1006', NULL),
(2, 10, 'FIGURE_PART', 'ha', '1006', NULL),
(3, 44, 'ROOM_ITEM_STATE', 'base_item_id', '21', '1')
ON DUPLICATE KEY UPDATE `id` = VALUES(`id`);

-- ---------------------------------------------------------------------------

-- Optional camera wall-colour overrides. INSERT IGNORE preserves an existing
-- operator-specific path while preventing a false missing-key error at boot.
INSERT IGNORE INTO `emulator_settings` (`key`, `value`) VALUES
  ('camera.wall.colors.path', './camera_wall_colors.properties');

-- ---------------------------------------------------------------------------




-- Original BattleBall server persistence and authoritative maps




-- ---------------------------------------------------------------------------




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
  ('battleball.enabled', '1'),
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
ON DUPLICATE KEY UPDATE `value` = `value`;

-- ---------------------------------------------------------------------------
-- BattleBall public lobby (embedded; no dependency on the havana database)
-- ---------------------------------------------------------------------------
INSERT INTO `room_models`
  (`name`, `door_x`, `door_y`, `door_dir`, `heightmap`, `public_items`, `club_only`)
VALUES
  ('bb_lobby_1', 14, 19, 0, 'xxx2222222222222222x\r\nxxx2222222222222222x\r\nxxx2222222222222222x\r\nxxx2222222222222222x\r\nxxx11111111111111111\r\n11x11111111111111111\r\n11x11111111111111111\r\n11x11111111111111111\r\nx1x11111111111111111\r\nxxx11111111111111111\r\nxxx11111111111111111\r\nxxx11111111111111111\r\nxxx11111111111111111\r\nxxx11111111111111111\r\nxxxxxxxxx00000000000\r\nxxxxxxxxx00000000000\r\nxxxxxxxxx00000000000\r\nxxxxxxxxx00000000000\r\nxxxxxxxxx00000000000\r\nxxxxxxxxx00000000000\r\nxxxxxxxxxxxxx000xxxx\r\nxxxxxxxxxxxxx000xxxx\r\nxxxxxxxxxxxxx000xxxx', '', '0')
ON DUPLICATE KEY UPDATE
  `door_x` = VALUES(`door_x`),
  `door_y` = VALUES(`door_y`),
  `door_dir` = VALUES(`door_dir`),
  `heightmap` = VALUES(`heightmap`),
  `club_only` = VALUES(`club_only`);

-- The layout is authoritative and contains no player/runtime data.
DELETE FROM `public_items` WHERE `room_model` = 'bb_lobby_1';
INSERT INTO `public_items`
  (`room_model`, `sprite`, `x`, `y`, `z`, `rotation`, `length`,
   `width`, `behaviour`, `top_height`, `has_dimensions`)
VALUES
('bb_lobby_1','bb_crossrd',3,0,2,2,1,1,'solid',0.001,0),
('bb_lobby_1','bb_bench1',4,0,2,4,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_bench2',5,0,2,4,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_plant1',8,0,2,0,1,1,'solid',0.001,0),
('bb_lobby_1','bb_sofa1',9,0,2,4,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_sofa2',10,0,2,4,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_plant1',11,0,2,0,1,1,'solid',0.001,0),
('bb_lobby_1','bb_sofa1',12,0,2,4,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_sofa2',13,0,2,4,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_plant1',14,0,2,0,1,1,'solid',0.001,0),
('bb_lobby_1','bb_bench1',16,0,2,4,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_bench2',17,0,2,4,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_corner1out',18,0,2,0,1,1,'solid',0.001,0),
('bb_lobby_1','bb_bench1',3,1,2,2,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_wallend1in',18,1,2,2,1,1,'solid',0.001,0),
('bb_lobby_1','bb_bench2',3,2,2,2,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_wallend1in',18,2,2,2,1,1,'solid',0.001,0),
('bb_lobby_1','bb_plant3',3,3,2,0,1,1,'solid',0.001,0),
('bb_lobby_1','bb_special',7,3,2,6,1,1,'solid',0.001,0),
('bb_lobby_1','bb_wallend1in',8,3,2,6,1,1,'solid',0.001,0),
('bb_lobby_1','bb_wallend1in',9,3,2,6,1,1,'solid',0.001,0),
('bb_lobby_1','bb_wallend1in',10,3,2,6,1,1,'solid',0.001,0),
('bb_lobby_1','bb_wallend1in',11,3,2,6,1,1,'solid',0.001,0),
('bb_lobby_1','bb_crossrd',12,3,2,2,1,1,'solid',0.001,0),
('bb_lobby_1','bb_crossrd',16,3,2,0,1,1,'solid',0.001,0),
('bb_lobby_1','bb_wallend1in',17,3,2,6,1,1,'solid',0.001,0),
('bb_lobby_1','bb_crossrd',18,3,2,2,1,1,'solid',0.001,0),
('bb_lobby_1','bb_wallend2in',3,4,1,4,1,1,'solid',0.001,0),
('bb_lobby_1','bb_wallend1in',7,4,1,4,1,1,'solid',0.001,0),
('bb_lobby_1','bb_bench1',8,4,1,4,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_bench2',9,4,1,4,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_bench1',10,4,1,4,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_bench2',11,4,1,4,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_wallend2in',12,4,1,4,1,1,'solid',0.001,0),
('bb_lobby_1','bb_wallend1in',16,4,1,4,1,1,'solid',0.001,0),
('bb_lobby_1','bb_bench1',17,4,1,4,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_bench2',18,4,1,4,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_wallendout',19,4,1,4,1,1,'solid',0.001,0),
('bb_lobby_1','bb_stool',7,5,1,3,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_stool',12,5,1,5,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_bench1',19,5,1,6,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_stool',3,6,1,6,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_bench2',19,6,1,6,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_chair',9,7,1,4,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_chair',10,7,1,4,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_bench1',17,7,1,0,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_bench2',18,7,1,0,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_wallendout',19,7,1,0,1,1,'solid',0.001,0),
('bb_lobby_1','bb_stool',3,8,1,6,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_extra',17,8,1,4,1,1,'solid',0.001,0),
('bb_lobby_1','bb_extra',18,8,1,2,1,1,'solid',0.001,0),
('bb_lobby_1','bb_crossrd',19,8,1,6,1,1,'solid',0.001,0),
('bb_lobby_1','bb_stool',3,9,1,6,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_chair',9,9,1,0,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_chair',10,9,1,0,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_bench1',17,9,1,4,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_bench2',18,9,1,4,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_wallendout',19,9,1,4,1,1,'solid',0.001,0),
('bb_lobby_1','bb_bench1',19,10,1,6,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_stool',7,11,1,1,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_stool',12,11,1,7,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_bench2',19,11,1,6,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_wallend1in',7,12,1,0,1,1,'solid',0.001,0),
('bb_lobby_1','bb_bench1',8,12,1,0,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_bench2',9,12,1,0,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_bench1',10,12,1,0,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_bench2',11,12,1,0,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_wallend2in',12,12,1,0,1,1,'solid',0.001,0),
('bb_lobby_1','bb_bench1',17,12,1,0,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_bench2',18,12,1,0,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_wallendout',19,12,1,0,1,1,'solid',0.001,0),
('bb_lobby_1','bb_corner1out',7,13,1,4,1,1,'solid',0.001,0),
('bb_lobby_1','bb_wallout',8,13,1,6,1,1,'solid',0.001,0),
('bb_lobby_1','bb_wallout',9,13,1,6,1,1,'solid',0.001,0),
('bb_lobby_1','bb_wallout',10,13,1,6,1,1,'solid',0.001,0),
('bb_lobby_1','bb_wallout',11,13,1,6,1,1,'solid',0.001,0),
('bb_lobby_1','bb_special',12,13,1,2,1,1,'solid',0.001,0),
('bb_lobby_1','bb_wallendout',16,13,1,6,1,1,'solid',0.001,0),
('bb_lobby_1','bb_wallout',17,13,1,6,1,1,'solid',0.001,0),
('bb_lobby_1','bb_wallout',18,13,1,6,1,1,'solid',0.001,0),
('bb_lobby_1','bb_corner1out',19,13,1,2,1,1,'solid',0.001,0),
('bb_lobby_1','bb_plant0',9,14,0,6,1,1,'solid',0.001,0),
('bb_lobby_1','bb_sofa1',10,14,0,4,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_sofa2',11,14,0,4,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_plant2',12,14,0,0,1,1,'solid',0.001,0),
('bb_lobby_1','bb_plant2',16,14,0,6,1,1,'solid',0.001,0),
('bb_lobby_1','bb_sofa1',17,14,0,4,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_sofa2',18,14,0,4,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_plant0',19,14,0,0,1,1,'solid',0.001,0),
('bb_lobby_1','bb_sofa1',9,15,0,2,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_sofa1',19,15,0,6,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_sofa2',9,16,0,2,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_sofa2',19,16,0,6,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_sofa1',9,17,0,2,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_sofa1',19,17,0,6,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_sofa2',9,18,0,2,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_sofa2',19,18,0,6,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_plant0',9,19,0,4,1,1,'solid',0.001,0),
('bb_lobby_1','bb_sofa1',10,19,0,0,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_sofa2',11,19,0,0,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_plant3',12,19,0,2,1,1,'solid',0.001,0),
('bb_lobby_1','bb_plant3',16,19,0,4,1,1,'solid',0.001,0),
('bb_lobby_1','bb_sofa1',17,19,0,0,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_sofa2',18,19,0,0,1,1,'can_sit_on_top',1,0),
('bb_lobby_1','bb_plant0',19,19,0,2,1,1,'solid',0.001,0);

-- Register the lobby using the same model-driven convention as the other
-- public-room installers. Numeric room/category IDs are resolved at install
-- time and are never fixed in this migration.
SET @bb_public_cat_id := (
  SELECT CAST(`value` AS UNSIGNED) FROM `emulator_settings`
  WHERE `key` = 'hotel.navigator.officialroot.categoryid' LIMIT 1);
SET @bb_public_cat_id := (
  SELECT `id` FROM `navigator_publiccats`
  WHERE `id` = @bb_public_cat_id LIMIT 1);
SET @bb_public_cat_id := IFNULL(@bb_public_cat_id, (
  SELECT `id` FROM `navigator_publiccats`
  WHERE `name` = 'Public Rooms' ORDER BY `id` DESC LIMIT 1));

INSERT INTO `navigator_publiccats` (`name`, `image`, `image_url`, `visible`, `order_num`)
SELECT 'Public Rooms', '1', 'navigator/navi1.png', '1', 1
FROM DUAL
WHERE @bb_public_cat_id IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM `navigator_publiccats` WHERE `name` = 'Public Rooms');

SET @bb_public_cat_id := IFNULL(@bb_public_cat_id, (
  SELECT `id` FROM `navigator_publiccats`
  WHERE `name` = 'Public Rooms' ORDER BY `id` DESC LIMIT 1));

INSERT INTO `emulator_settings` (`key`, `value`)
SELECT 'hotel.navigator.officialroot.categoryid', CAST(@bb_public_cat_id AS CHAR)
FROM DUAL
WHERE @bb_public_cat_id IS NOT NULL
ON DUPLICATE KEY UPDATE `value` = VALUES(`value`);

SET @bb_owner_id := (
  SELECT `owner_id` FROM `rooms`
  WHERE `model` = 'bb_lobby_1' ORDER BY `id` LIMIT 1);
SET @bb_owner_id := IFNULL(@bb_owner_id, (
  SELECT CAST(`value` AS UNSIGNED) FROM `emulator_settings`
  WHERE `key` = 'hotel.publicroom.ownerid' LIMIT 1));
SET @bb_owner_id := (
  SELECT `id` FROM `users` WHERE `id` = @bb_owner_id LIMIT 1);
SET @bb_owner_id := IFNULL(@bb_owner_id, (
  SELECT `id` FROM `users` ORDER BY `id` LIMIT 1));
SET @bb_owner_id := IFNULL(@bb_owner_id, 0);
SET @bb_owner_name := IFNULL((
  SELECT `username` FROM `users` WHERE `id` = @bb_owner_id LIMIT 1), 'Hotel');

SET @bb_room_category_id := (
  SELECT `id` FROM `navigator_flatcats` WHERE `id` = 15 LIMIT 1);
SET @bb_room_category_id := IFNULL(@bb_room_category_id, (
  SELECT `id` FROM `navigator_flatcats`
  WHERE `caption_save` = 'caption_save_personal' ORDER BY `id` LIMIT 1));
SET @bb_room_category_id := IFNULL(@bb_room_category_id, (
  SELECT `id` FROM `navigator_flatcats` ORDER BY `id` LIMIT 1));
SET @bb_room_category_id := IFNULL(@bb_room_category_id, 1);

INSERT INTO `rooms`
  (`owner_id`, `owner_name`, `name`, `description`, `model`, `state`,
   `users_max`, `category`, `override_model`, `allow_walkthrough`,
   `roller_speed`, `trade_mode`)
SELECT @bb_owner_id, @bb_owner_name, 'Battleball Lobby',
       'The original BattleBall lounge', 'bb_lobby_1', 'open', 200,
       @bb_room_category_id, '0', '0', 4, 2
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `rooms` WHERE `model` = 'bb_lobby_1');

-- NavigatorManager cannot load links whose room has been deleted. Remove only
-- demonstrably orphaned public-room links; no numeric room ID is assumed.
DELETE np
FROM `navigator_publics` np
LEFT JOIN `rooms` r ON r.`id` = np.`room_id`
WHERE r.`id` IS NULL;

INSERT INTO `navigator_publics` (`public_cat_id`, `room_id`, `visible`)
SELECT @bb_public_cat_id, r.`id`, '1'
FROM `rooms` r
WHERE r.`model` = 'bb_lobby_1'
  AND @bb_public_cat_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `navigator_publics` np WHERE np.`room_id` = r.`id`);

-- ---------------------------------------------------------------------------
-- Postflight report (read-only)
-- ---------------------------------------------------------------------------
SELECT 'quests' AS component, COUNT(*) AS definitions FROM `quests`
UNION ALL SELECT 'battleball_maps', COUNT(*) FROM `battleball_maps`
UNION ALL SELECT 'battleball_spawns', COUNT(*) FROM `battleball_spawns`
UNION ALL SELECT 'bb_lobby_public_items', COUNT(*) FROM `public_items` WHERE `room_model`='bb_lobby_1';

SELECT `name`, `model`, `owner_name`
FROM `rooms` WHERE `model` = 'bb_lobby_1' ORDER BY `id` LIMIT 1;

SELECT 'NGH production feature update completed' AS `ngh_upgrade`;
