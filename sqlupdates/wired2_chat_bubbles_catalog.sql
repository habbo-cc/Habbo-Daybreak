CREATE TABLE IF NOT EXISTS `users_chat_styles` (
  `user_id` INT(11) NOT NULL,
  `style_id` INT(11) NOT NULL,
  `created_at` INT(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`user_id`, `style_id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- Seed only missing rows. Catalog pricing and placement are administrator
-- configuration, so rerunning a schema migration must not delete or reset them.
INSERT IGNORE INTO `catalog_pages`
(`id`, `parent_id`, `caption_save`, `caption`, `icon_color`, `icon_image`, `visible`, `enabled`, `min_rank`, `club_only`, `order_num`, `page_layout`, `catalog_type`, `page_headline`, `page_teaser`, `page_special`, `page_text1`, `page_text2`, `page_text_details`, `page_text_teaser`, `includes`, `room_id`)
VALUES
(72000, 209, 'chat_bubbles', 'Chat Bubbles', 1, 1, '1', '1', 1, '0', 999, 'default_3x3', 'NORMAL', 'catalog_chat_bubbles_headline', 'catalog_chat_bubbles_teaser', '', 'Unlock chat bubble styles for your room chat.', '', '', '', '', 0);

INSERT IGNORE INTO `catalog_items`
(`id`, `page_id`, `item_ids`, `catalog_name`, `cost_credits`, `cost_points`, `points_type`, `amount`, `song_id`, `limited_stack`, `limited_sells`, `extradata`, `club_only`, `have_offer`, `offer_id`, `subscription_type`, `subscription_days`, `order_number`)
VALUES
(720001, 72000, '0', 'chat_style_3', 5, 0, 0, 1, 0, 0, 0, 'chat_style:3', '0', '1', 720001, NULL, NULL, 1),
(720002, 72000, '0', 'chat_style_4', 5, 0, 0, 1, 0, 0, 0, 'chat_style:4', '0', '1', 720002, NULL, NULL, 2),
(720003, 72000, '0', 'chat_style_5', 5, 0, 0, 1, 0, 0, 0, 'chat_style:5', '0', '1', 720003, NULL, NULL, 3),
(720004, 72000, '0', 'chat_style_6', 5, 0, 0, 1, 0, 0, 0, 'chat_style:6', '0', '1', 720004, NULL, NULL, 4),
(720005, 72000, '0', 'chat_style_7', 5, 0, 0, 1, 0, 0, 0, 'chat_style:7', '0', '1', 720005, NULL, NULL, 5),
(720006, 72000, '0', 'chat_style_9', 5, 0, 0, 1, 0, 0, 0, 'chat_style:9', '0', '1', 720006, NULL, NULL, 6),
(720007, 72000, '0', 'chat_style_10', 5, 0, 0, 1, 0, 0, 0, 'chat_style:10', '0', '1', 720007, NULL, NULL, 7),
(720008, 72000, '0', 'chat_style_11', 5, 0, 0, 1, 0, 0, 0, 'chat_style:11', '0', '1', 720008, NULL, NULL, 8),
(720009, 72000, '0', 'chat_style_12', 5, 0, 0, 1, 0, 0, 0, 'chat_style:12', '0', '1', 720009, NULL, NULL, 9),
(720010, 72000, '0', 'chat_style_13', 5, 0, 0, 1, 0, 0, 0, 'chat_style:13', '0', '1', 720010, NULL, NULL, 10),
(720011, 72000, '0', 'chat_style_14', 5, 0, 0, 1, 0, 0, 0, 'chat_style:14', '0', '1', 720011, NULL, NULL, 11),
(720012, 72000, '0', 'chat_style_15', 5, 0, 0, 1, 0, 0, 0, 'chat_style:15', '0', '1', 720012, NULL, NULL, 12),
(720013, 72000, '0', 'chat_style_16', 5, 0, 0, 1, 0, 0, 0, 'chat_style:16', '0', '1', 720013, NULL, NULL, 13),
(720014, 72000, '0', 'chat_style_17', 5, 0, 0, 1, 0, 0, 0, 'chat_style:17', '0', '1', 720014, NULL, NULL, 14),
(720015, 72000, '0', 'chat_style_19', 5, 0, 0, 1, 0, 0, 0, 'chat_style:19', '0', '1', 720015, NULL, NULL, 15),
(720016, 72000, '0', 'chat_style_20', 5, 0, 0, 1, 0, 0, 0, 'chat_style:20', '0', '1', 720016, NULL, NULL, 16),
(720017, 72000, '0', 'chat_style_21', 5, 0, 0, 1, 0, 0, 0, 'chat_style:21', '0', '1', 720017, NULL, NULL, 17),
(720018, 72000, '0', 'chat_style_22', 5, 0, 0, 1, 0, 0, 0, 'chat_style:22', '0', '1', 720018, NULL, NULL, 18),
(720019, 72000, '0', 'chat_style_24', 5, 0, 0, 1, 0, 0, 0, 'chat_style:24', '0', '1', 720019, NULL, NULL, 19),
(720020, 72000, '0', 'chat_style_25', 5, 0, 0, 1, 0, 0, 0, 'chat_style:25', '0', '1', 720020, NULL, NULL, 20),
(720021, 72000, '0', 'chat_style_26', 5, 0, 0, 1, 0, 0, 0, 'chat_style:26', '0', '1', 720021, NULL, NULL, 21),
(720022, 72000, '0', 'chat_style_27', 5, 0, 0, 1, 0, 0, 0, 'chat_style:27', '0', '1', 720022, NULL, NULL, 22),
(720023, 72000, '0', 'chat_style_29', 5, 0, 0, 1, 0, 0, 0, 'chat_style:29', '0', '1', 720023, NULL, NULL, 23),
(720024, 72000, '0', 'chat_style_1000', 5, 0, 0, 1, 0, 0, 0, 'chat_style:1000', '0', '1', 720024, NULL, NULL, 24),
(720025, 72000, '0', 'chat_style_1001', 5, 0, 0, 1, 0, 0, 0, 'chat_style:1001', '0', '1', 720025, NULL, NULL, 25),
(720026, 72000, '0', 'chat_style_1002', 5, 0, 0, 1, 0, 0, 0, 'chat_style:1002', '0', '1', 720026, NULL, NULL, 26),
(720027, 72000, '0', 'chat_style_1003', 5, 0, 0, 1, 0, 0, 0, 'chat_style:1003', '0', '1', 720027, NULL, NULL, 27),
(720028, 72000, '0', 'chat_style_1004', 5, 0, 0, 1, 0, 0, 0, 'chat_style:1004', '0', '1', 720028, NULL, NULL, 28),
(720029, 72000, '0', 'chat_style_1005', 5, 0, 0, 1, 0, 0, 0, 'chat_style:1005', '0', '1', 720029, NULL, NULL, 29),
(720030, 72000, '0', 'chat_style_1006', 5, 0, 0, 1, 0, 0, 0, 'chat_style:1006', '0', '1', 720030, NULL, NULL, 30),
(720031, 72000, '0', 'chat_style_1007', 5, 0, 0, 1, 0, 0, 0, 'chat_style:1007', '0', '1', 720031, NULL, NULL, 31),
(720032, 72000, '0', 'chat_style_1010', 5, 0, 0, 1, 0, 0, 0, 'chat_style:1010', '0', '1', 720032, NULL, NULL, 32),
(720033, 72000, '0', 'chat_style_1011', 5, 0, 0, 1, 0, 0, 0, 'chat_style:1011', '0', '1', 720033, NULL, NULL, 33),
(720034, 72000, '0', 'chat_style_1012', 5, 0, 0, 1, 0, 0, 0, 'chat_style:1012', '0', '1', 720034, NULL, NULL, 34),
(720035, 72000, '0', 'chat_style_1013', 5, 0, 0, 1, 0, 0, 0, 'chat_style:1013', '0', '1', 720035, NULL, NULL, 35),
(720036, 72000, '0', 'chat_style_1014', 5, 0, 0, 1, 0, 0, 0, 'chat_style:1014', '0', '1', 720036, NULL, NULL, 36),
(720037, 72000, '0', 'chat_style_1015', 5, 0, 0, 1, 0, 0, 0, 'chat_style:1015', '0', '1', 720037, NULL, NULL, 37),
(720038, 72000, '0', 'chat_style_1016', 5, 0, 0, 1, 0, 0, 0, 'chat_style:1016', '0', '1', 720038, NULL, NULL, 38),
(720039, 72000, '0', 'chat_style_1017', 5, 0, 0, 1, 0, 0, 0, 'chat_style:1017', '0', '1', 720039, NULL, NULL, 39),
(720040, 72000, '0', 'chat_style_1018', 5, 0, 0, 1, 0, 0, 0, 'chat_style:1018', '0', '1', 720040, NULL, NULL, 40),
(720041, 72000, '0', 'chat_style_1019', 5, 0, 0, 1, 0, 0, 0, 'chat_style:1019', '0', '1', 720041, NULL, NULL, 41),
(720042, 72000, '0', 'chat_style_1020', 5, 0, 0, 1, 0, 0, 0, 'chat_style:1020', '0', '1', 720042, NULL, NULL, 42),
(720043, 72000, '0', 'chat_style_1021', 5, 0, 0, 1, 0, 0, 0, 'chat_style:1021', '0', '1', 720043, NULL, NULL, 43),
(720044, 72000, '0', 'chat_style_1022', 5, 0, 0, 1, 0, 0, 0, 'chat_style:1022', '0', '1', 720044, NULL, NULL, 44),
(720045, 72000, '0', 'chat_style_1023', 5, 0, 0, 1, 0, 0, 0, 'chat_style:1023', '0', '1', 720045, NULL, NULL, 45),
(720046, 72000, '0', 'chat_style_1024', 5, 0, 0, 1, 0, 0, 0, 'chat_style:1024', '0', '1', 720046, NULL, NULL, 46),
(720047, 72000, '0', 'chat_style_1025', 5, 0, 0, 1, 0, 0, 0, 'chat_style:1025', '0', '1', 720047, NULL, NULL, 47),
(720048, 72000, '0', 'chat_style_1026', 5, 0, 0, 1, 0, 0, 0, 'chat_style:1026', '0', '1', 720048, NULL, NULL, 48),
(720049, 72000, '0', 'chat_style_10000', 5, 0, 0, 1, 0, 0, 0, 'chat_style:10000', '0', '1', 720049, NULL, NULL, 49);
