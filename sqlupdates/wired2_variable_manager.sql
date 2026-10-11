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

-- The pre-release variable prototype was never used on NGH. Remove its
-- superseded storage so the room-scoped manager remains the sole source of
-- variable definitions and values.
DROP TABLE IF EXISTS `wired_variables`;
