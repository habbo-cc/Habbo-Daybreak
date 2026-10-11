-- Gamehall per-game enable/disable switches (read via GamehallGameType.isEnabled()).
-- A disabled game (value '0') never opens its board UI: sitting its chair just seats the avatar,
-- so coded games keep working while uncoded ones stay inert. Default is disabled when a row is
-- missing, so these rows are what actually switch a game on.
-- Battleships + Tic-Tac-Toe are implemented (1); Chess + Poker remain stubs (0) until coded.
INSERT IGNORE INTO `emulator_settings` (`key`, `value`) VALUES
('gamehall.battleships.enabled', '1'),
('gamehall.tictactoe.enabled', '1'),
('gamehall.chess.enabled', '0'),
('gamehall.poker.enabled', '0');
