-- =====================================================================
-- Crackables redesign: tiers / types (simple|bundle) / decimal chances
-- Converts items_crackable from one weighted pool per furni to multi-row
-- tiered outcomes. GENERIC: the data conversion reads each hotel's own
-- existing rows (no hard-coded furni ids).
--
-- Target: MariaDB 10.2+. Run ONCE per hotel database.
-- Old model : 1 row/furni, prizes = "id:chance;id2:chance2" (no colon => 100)
-- New model : N rows/furni; columns tier, type('s'|'b'), item_ids(csv),
--             chance(csv for 's', single for 'b'). Per-furni meta stays on
--             every row (read from the tier-0 row by the emulator).
-- =====================================================================

-- 1. Schema: drop the item_id PK, rename it to crackable_id, add the new
--    columns, and add a surrogate auto-increment primary key. prizes is
--    kept for now so step 2 can convert it.
ALTER TABLE `items_crackable`
    DROP PRIMARY KEY,
    CHANGE `item_id` `crackable_id` INT(11) NOT NULL,
    ADD COLUMN `tier`     INT(11)       NOT NULL DEFAULT 0   AFTER `crackable_id`,
    ADD COLUMN `type`     ENUM('s','b') NOT NULL DEFAULT 's' AFTER `tier`,
    ADD COLUMN `item_ids` VARCHAR(900)  NOT NULL DEFAULT ''  AFTER `type`,
    ADD COLUMN `chance`   VARCHAR(900)  NOT NULL DEFAULT ''  AFTER `item_ids`,
    ADD COLUMN `id`       INT(11)       NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST,
    ADD INDEX `idx_crackable` (`crackable_id`, `tier`);

-- 2. Data: convert each existing prizes string into the new simple (type 's',
--    tier 0) format. Entries without a chance (no ':') default to 100, exactly
--    like the old loader. Handles any number of entries and trailing ';'.
DELIMITER $$

DROP PROCEDURE IF EXISTS migrate_crackable_prizes $$

CREATE PROCEDURE migrate_crackable_prizes()
BEGIN
    DECLARE v_done    INT DEFAULT 0;
    DECLARE v_id      INT;
    DECLARE v_prizes  VARCHAR(900);
    DECLARE v_rest    VARCHAR(900);
    DECLARE v_entry   VARCHAR(900);
    DECLARE v_eid     VARCHAR(64);
    DECLARE v_ech     VARCHAR(64);
    DECLARE v_ids     VARCHAR(900);
    DECLARE v_chs     VARCHAR(900);
    DECLARE cur CURSOR FOR SELECT `id`, `prizes` FROM `items_crackable`;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;

    OPEN cur;
    read_loop: LOOP
        FETCH cur INTO v_id, v_prizes;
        IF v_done = 1 THEN
            LEAVE read_loop;
        END IF;

        SET v_ids  = '';
        SET v_chs  = '';
        SET v_rest = IFNULL(v_prizes, '');

        WHILE LENGTH(v_rest) > 0 DO
            SET v_entry = SUBSTRING_INDEX(v_rest, ';', 1);

            IF LENGTH(v_entry) + 1 >= LENGTH(v_rest) THEN
                SET v_rest = '';
            ELSE
                SET v_rest = SUBSTRING(v_rest, LENGTH(v_entry) + 2);
            END IF;

            IF LENGTH(v_entry) > 0 THEN
                IF LOCATE(':', v_entry) > 0 THEN
                    SET v_eid = SUBSTRING_INDEX(v_entry, ':', 1);
                    SET v_ech = SUBSTRING_INDEX(v_entry, ':', -1);
                ELSE
                    SET v_eid = v_entry;
                    SET v_ech = '100';
                END IF;

                SET v_ids = IF(v_ids = '', v_eid, CONCAT(v_ids, ',', v_eid));
                SET v_chs = IF(v_chs = '', v_ech, CONCAT(v_chs, ',', v_ech));
            END IF;
        END WHILE;

        UPDATE `items_crackable`
           SET `item_ids` = v_ids,
               `chance`   = v_chs,
               `type`     = 's',
               `tier`     = 0
         WHERE `id` = v_id;
    END LOOP;
    CLOSE cur;
END $$

DELIMITER ;

CALL migrate_crackable_prizes();
DROP PROCEDURE IF EXISTS migrate_crackable_prizes;

-- 3. Schema: drop the now-migrated prizes column.
ALTER TABLE `items_crackable` DROP COLUMN `prizes`;
