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
