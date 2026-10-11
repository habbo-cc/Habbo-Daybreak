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
