-- July 2026 AIR Area Hide furniture. Safe to run repeatedly and does not
-- rewrite installations that deliberately assigned a non-default interaction.
UPDATE items_base
SET interaction_type = 'area_hide'
WHERE item_name = 'conf_area_hide'
  AND interaction_type IN ('default', 'conf_area_hide', 'area_hide');
