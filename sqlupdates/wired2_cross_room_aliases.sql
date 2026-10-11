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
