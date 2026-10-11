-- Habbicon messenger message metadata.
-- Idempotent on MySQL/MariaDB: adds nullable/defaulted columns without changing existing text rows.

SET @schema_name := DATABASE();

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `chatlogs_private` ADD COLUMN `message_type` TINYINT(1) NOT NULL DEFAULT 0 AFTER `timestamp`',
        'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'chatlogs_private' AND COLUMN_NAME = 'message_type'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `chatlogs_private` ADD COLUMN `habbicon_id` INT(11) NOT NULL DEFAULT 0 AFTER `message_type`',
        'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'chatlogs_private' AND COLUMN_NAME = 'habbicon_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `chatlogs_private` ADD COLUMN `confirmation_id` INT(11) NOT NULL DEFAULT 0 AFTER `habbicon_id`',
        'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'chatlogs_private' AND COLUMN_NAME = 'confirmation_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `chatlogs_private` ADD COLUMN `message_id` VARCHAR(64) NOT NULL DEFAULT '''' AFTER `confirmation_id`',
        'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'chatlogs_private' AND COLUMN_NAME = 'message_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `chatlogs_private` ADD KEY `idx_chatlogs_private_habbicon_pair_time` (`user_from_id`, `user_to_id`, `message_type`, `timestamp`)',
        'SELECT 1')
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'chatlogs_private' AND INDEX_NAME = 'idx_chatlogs_private_habbicon_pair_time'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
