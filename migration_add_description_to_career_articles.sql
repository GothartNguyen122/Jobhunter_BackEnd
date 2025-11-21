-- ============================================
-- Migration: Add description column to career_articles
-- Date: 2025-11-18
-- Description: Thêm field description vào bảng career_articles để hiển thị mô tả ngắn cho bài viết
-- ============================================

-- ============================================
-- Cách 1: Chạy trực tiếp (Đơn giản nhất - Khuyến nghị)
-- ============================================
-- Kiểm tra và thêm column description nếu chưa tồn tại
SET @column_exists = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'career_articles'
      AND COLUMN_NAME = 'description'
);

SET @sql = IF(@column_exists = 0,
    'ALTER TABLE career_articles ADD COLUMN description VARCHAR(500) NULL AFTER title',
    'SELECT "Column description already exists" AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================
-- Cách 2: Sử dụng procedure (Nếu muốn tái sử dụng)
-- ============================================
-- DELIMITER $$
-- 
-- DROP PROCEDURE IF EXISTS AddColumnIfNotExists$$
-- CREATE PROCEDURE AddColumnIfNotExists(
--     IN p_table_name VARCHAR(64),
--     IN p_column_name VARCHAR(64),
--     IN p_column_definition TEXT
-- )
-- BEGIN
--     DECLARE column_exists INT DEFAULT 0;
--     SELECT COUNT(*) INTO column_exists
--     FROM INFORMATION_SCHEMA.COLUMNS
--     WHERE TABLE_SCHEMA = DATABASE()
--       AND TABLE_NAME = p_table_name
--       AND COLUMN_NAME = p_column_name;
--     IF column_exists = 0 THEN
--         SET @sql = CONCAT('ALTER TABLE ', p_table_name, ' ADD COLUMN ', p_column_name, ' ', p_column_definition);
--         PREPARE stmt FROM @sql;
--         EXECUTE stmt;
--         DEALLOCATE PREPARE stmt;
--     END IF;
-- END$$
-- 
-- DELIMITER ;
-- 
-- CALL AddColumnIfNotExists('career_articles', 'description', 'VARCHAR(500) NULL AFTER title');

-- Tạo index cho description nếu cần tìm kiếm (tùy chọn)
-- CALL CreateIndexIfNotExists('career_articles', 'idx_career_articles_description', '(description(100))');

-- ============================================
-- Note: 
-- - Column description là NULL (optional) để tương thích với dữ liệu cũ
-- - VARCHAR(500) đủ cho mô tả ngắn 100-200 ký tự
-- - Nếu cần full-text search, có thể thêm FULLTEXT index sau
-- ============================================

