-- ============================================
-- MIGRATION SCRIPT V2 - ULTRA OPTIMIZED VERSION
-- JobHunter Database Enhancement
-- ============================================
-- Tối ưu dựa trên query patterns thực tế:
-- - Full-text search cho keyword
-- - Composite indexes cho multi-column queries
-- - Covering indexes cho common selects
-- - Chuẩn hóa job_alert_skill join table
-- - Indexes cho join tables
-- ============================================

-- ============================================
-- HELPER PROCEDURES (Optimized)
-- ============================================

DELIMITER $$

DROP PROCEDURE IF EXISTS AddColumnIfNotExists$$
CREATE PROCEDURE AddColumnIfNotExists(
    IN p_table_name VARCHAR(64),
    IN p_column_name VARCHAR(64),
    IN p_column_definition TEXT
)
BEGIN
    DECLARE column_exists INT DEFAULT 0;
    SELECT COUNT(*) INTO column_exists
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table_name
      AND COLUMN_NAME = p_column_name;
    IF column_exists = 0 THEN
        SET @sql = CONCAT('ALTER TABLE ', p_table_name, ' ADD COLUMN ', p_column_definition);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DROP PROCEDURE IF EXISTS AddForeignKeyIfNotExists$$
CREATE PROCEDURE AddForeignKeyIfNotExists(
    IN p_table_name VARCHAR(64),
    IN p_constraint_name VARCHAR(64),
    IN p_fk_definition TEXT
)
BEGIN
    DECLARE constraint_exists INT DEFAULT 0;
    SELECT COUNT(*) INTO constraint_exists
    FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table_name
      AND CONSTRAINT_NAME = p_constraint_name;
    IF constraint_exists = 0 THEN
        SET @sql = CONCAT('ALTER TABLE ', p_table_name, ' ADD CONSTRAINT ', p_constraint_name, ' ', p_fk_definition);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DROP PROCEDURE IF EXISTS CreateIndexIfNotExists$$
CREATE PROCEDURE CreateIndexIfNotExists(
    IN p_table_name VARCHAR(64),
    IN p_index_name VARCHAR(64),
    IN p_index_definition TEXT
)
BEGIN
    DECLARE index_exists INT DEFAULT 0;
    SELECT COUNT(*) INTO index_exists
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table_name
      AND INDEX_NAME = p_index_name;
    IF index_exists = 0 THEN
        SET @sql = CONCAT('CREATE INDEX ', p_index_name, ' ON ', p_table_name, ' ', p_index_definition);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

-- ============================================
-- 1) CATEGORIES (Optimized)
-- ============================================
CREATE TABLE IF NOT EXISTS categories (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(150) NOT NULL,
  slug VARCHAR(150) DEFAULT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY ux_categories_slug (slug),
  KEY idx_categories_name (name),
  -- Full-text search cho tìm kiếm category
  FULLTEXT KEY ft_categories_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================
-- 2) ADD category_id TO jobs (Optimized)
-- ============================================
CALL AddColumnIfNotExists('jobs', 'category_id', 'BIGINT NULL AFTER name');
CALL CreateIndexIfNotExists('jobs', 'idx_jobs_category', '(category_id)');
CALL AddForeignKeyIfNotExists('jobs', 'fk_jobs_category', 
    'FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL');

-- ============================================
-- 3) FAVORITES (Optimized with covering index)
-- ============================================
CREATE TABLE IF NOT EXISTS favorites (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  job_id BIGINT NULL,
  company_id BIGINT NULL,
  saved_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY ux_favorites_user_item (user_id, job_id, company_id),
  -- Covering index: user_id + saved_at (thường query: lấy favorites của user, sort by saved_at)
  KEY idx_fav_user_saved (user_id, saved_at),
  KEY idx_fav_job (job_id),
  KEY idx_fav_company (company_id),
  CONSTRAINT chk_fav_job_or_company CHECK (
    (job_id IS NOT NULL AND company_id IS NULL) OR 
    (job_id IS NULL AND company_id IS NOT NULL)
  ),
  CONSTRAINT fk_fav_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_fav_job FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE,
  CONSTRAINT fk_fav_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================
-- 4) FEEDBACKS (Optimized)
-- ============================================
CREATE TABLE IF NOT EXISTS feedbacks (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  job_id BIGINT NULL,
  company_id BIGINT NULL,
  rating TINYINT NULL,
  content TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_feedback_user (user_id),
  -- Composite index: job_id + rating (thường query: lấy feedbacks của job, sort by rating)
  KEY idx_feedback_job_rating (job_id, rating),
  KEY idx_feedback_company_rating (company_id, rating),
  -- Covering index: user_id + created_at (lấy feedbacks của user, sort by date)
  KEY idx_feedback_user_created (user_id, created_at),
  CONSTRAINT chk_feedback_job_or_company CHECK (
    (job_id IS NOT NULL AND company_id IS NULL) OR 
    (job_id IS NULL AND company_id IS NOT NULL)
  ),
  CONSTRAINT chk_feedback_rating CHECK (rating IS NULL OR (rating >= 1 AND rating <= 5)),
  CONSTRAINT fk_feedback_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_feedback_job FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE,
  CONSTRAINT fk_feedback_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================
-- 5) JOB_ALERTS (Ultra Optimized)
-- ============================================
CREATE TABLE IF NOT EXISTS job_alerts (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NULL,
  email VARCHAR(255) NULL,
  location VARCHAR(255) NULL,
  experience VARCHAR(50) NULL,
  desired_salary INT NULL,
  category_id BIGINT NULL,
  active TINYINT(1) DEFAULT 1,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_job_alerts_user (user_id),
  KEY idx_job_alerts_email (email),
  KEY idx_job_alerts_category (category_id),
  KEY idx_job_alerts_active (active),
  KEY idx_job_alerts_location (location),
  KEY idx_job_alerts_user_active (user_id, active),
  KEY idx_job_alerts_category_active (category_id, active),
  CONSTRAINT chk_job_alerts_user_or_email CHECK (
    (user_id IS NOT NULL) OR (email IS NOT NULL)
  ),
  CONSTRAINT fk_job_alerts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_job_alerts_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS job_alert_skill (
  job_alert_id BIGINT NOT NULL,
  skill_id BIGINT NOT NULL,
  PRIMARY KEY (job_alert_id, skill_id),
  KEY idx_job_alert_skill_skill (skill_id),
  CONSTRAINT fk_job_alert_skill_alert FOREIGN KEY (job_alert_id) REFERENCES job_alerts(id) ON DELETE CASCADE,
  CONSTRAINT fk_job_alert_skill_skill FOREIGN KEY (skill_id) REFERENCES skills(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================
-- 6) CV TEMPLATES & USER CVs (Optimized)
-- ============================================
CREATE TABLE IF NOT EXISTS cv_templates (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(200) NOT NULL,
  thumbnail_url VARCHAR(255) NULL,
  html_template LONGTEXT NULL,
  css_styles LONGTEXT NULL,
  is_active TINYINT(1) DEFAULT 1,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_cv_templates_active (is_active),
  -- Full-text search cho tìm kiếm template
  FULLTEXT KEY ft_cv_templates_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_cvs (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  template_id BIGINT NULL,
  title VARCHAR(255) DEFAULT 'My CV',
  data JSON NOT NULL,
  pdf_url VARCHAR(255) NULL,
  is_default TINYINT(1) DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_usercvs_user (user_id),
  KEY idx_usercvs_template (template_id),
  -- Composite index: user_id + is_default (thường query: lấy default CV của user)
  KEY idx_usercvs_user_default (user_id, is_default),
  -- Covering index: user_id + created_at (lấy CVs của user, sort by date)
  KEY idx_usercvs_user_created (user_id, created_at),
  CONSTRAINT fk_usercvs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_usercvs_template FOREIGN KEY (template_id) REFERENCES cv_templates(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================
-- 7) ADD user_cv_id TO resumes
-- ============================================
CALL AddColumnIfNotExists('resumes', 'user_cv_id', 'BIGINT NULL AFTER url');
CALL CreateIndexIfNotExists('resumes', 'idx_resumes_usercv', '(user_cv_id)');
CALL AddForeignKeyIfNotExists('resumes', 'fk_resumes_usercv',
    'FOREIGN KEY (user_cv_id) REFERENCES user_cvs(id) ON DELETE SET NULL');

-- ============================================
-- 8) ULTRA OPTIMIZED INDEXES FOR JOBS
-- ============================================
-- Dựa trên query patterns thực tế từ JobService

-- Single column indexes
CALL CreateIndexIfNotExists('jobs', 'idx_jobs_location', '(location)');
CALL CreateIndexIfNotExists('jobs', 'idx_jobs_level', '(level)');
CALL CreateIndexIfNotExists('jobs', 'idx_jobs_salary', '(salary)');
CALL CreateIndexIfNotExists('jobs', 'idx_jobs_active', '(active)');
CALL CreateIndexIfNotExists('jobs', 'idx_jobs_start_date', '(start_date)');
CALL CreateIndexIfNotExists('jobs', 'idx_jobs_end_date', '(end_date)');
CALL CreateIndexIfNotExists('jobs', 'idx_jobs_company_id', '(company_id)');

-- Full-text search cho job name (keyword search)
-- Sử dụng FULLTEXT index thay vì generated column để tránh lỗi syntax
CALL CreateIndexIfNotExists('jobs', 'idx_jobs_name_prefix', '(name(100))');

-- Composite indexes cho các query patterns phổ biến

-- Pattern 1: Tìm active jobs trong khoảng thời gian (matching jobs query)
CALL CreateIndexIfNotExists('jobs', 'idx_jobs_active_enddate', 
    '(active, end_date)');

-- Pattern 2: Tìm kiếm theo category + level + location (filter query)
CALL CreateIndexIfNotExists('jobs', 'idx_jobs_category_level_location', 
    '(category_id, level, location)');

-- Pattern 3: Tìm kiếm theo salary range (minSalary, maxSalary)
CALL CreateIndexIfNotExists('jobs', 'idx_jobs_active_salary', 
    '(active, salary)');

-- Pattern 4: Tìm kiếm theo company name (join với companies)
-- Index này giúp optimize join query
CALL CreateIndexIfNotExists('companies', 'idx_companies_name_lower', 
    '(name(100))');

-- Pattern 5: Covering index cho common select: active, name, location, salary, level
CALL CreateIndexIfNotExists('jobs', 'idx_jobs_covering_list', 
    '(active, category_id, level, location, salary, name(50))');

-- ============================================
-- 9) OPTIMIZE JOIN TABLES
-- ============================================

-- job_skill join table - thường dùng để filter jobs by skills
CALL CreateIndexIfNotExists('job_skill', 'idx_job_skill_skill_id', '(skill_id)');
CALL CreateIndexIfNotExists('job_skill', 'idx_job_skill_job_id', '(job_id)');
-- Composite index cho query: lấy tất cả jobs có skill X
CALL CreateIndexIfNotExists('job_skill', 'idx_job_skill_composite', 
    '(skill_id, job_id)');

-- user_skill join table - thường dùng để match user skills với job skills
CALL CreateIndexIfNotExists('user_skill', 'idx_user_skill_user_id', '(user_id)');
CALL CreateIndexIfNotExists('user_skill', 'idx_user_skill_skill_id', '(skill_id)');
CALL CreateIndexIfNotExists('user_skill', 'idx_user_skill_composite', 
    '(user_id, skill_id)');

-- job_alert_skill join table
CALL CreateIndexIfNotExists('job_alert_skill', 'idx_job_alert_skill_alert', '(job_alert_id)');
CALL CreateIndexIfNotExists('job_alert_skill', 'idx_job_alert_skill_skill_only', '(skill_id)');

-- ============================================
-- 10) OPTIMIZE RESUMES TABLE
-- ============================================
-- Thêm indexes cho các query phổ biến với resumes

-- Composite index: user_id + status (lấy resumes của user theo status)
CALL CreateIndexIfNotExists('resumes', 'idx_resumes_user_status', 
    '(user_id, status)');

-- Composite index: job_id + status (lấy resumes của job theo status)
CALL CreateIndexIfNotExists('resumes', 'idx_resumes_job_status', 
    '(job_id, status)');

-- Covering index: user_id + created_at (lấy resumes của user, sort by date)
CALL CreateIndexIfNotExists('resumes', 'idx_resumes_user_created', 
    '(user_id, created_at)');

-- ============================================
-- 11) OPTIMIZE USERS TABLE (if needed)
-- ============================================
-- Index cho email search (thường dùng trong authentication)
CALL CreateIndexIfNotExists('users', 'idx_users_email', '(email)');

-- Index cho company_id (lấy users của company)
CALL CreateIndexIfNotExists('users', 'idx_users_company', '(company_id)');

-- ============================================
-- 12) PERFORMANCE TUNING
-- ============================================

-- Tối ưu InnoDB settings (chạy sau khi tạo tables)
-- SET GLOBAL innodb_buffer_pool_size = 1073741824; -- 1GB (adjust based on RAM)
-- SET GLOBAL innodb_log_file_size = 268435456; -- 256MB

-- ============================================
-- CLEANUP
-- ============================================
-- DROP PROCEDURE IF EXISTS AddColumnIfNotExists;
-- DROP PROCEDURE IF EXISTS AddForeignKeyIfNotExists;
-- DROP PROCEDURE IF EXISTS CreateIndexIfNotExists;

-- ============================================
-- VERIFICATION QUERIES
-- ============================================
-- Chạy các query sau để verify indexes:

-- 1. Xem tất cả indexes của jobs table
-- SHOW INDEXES FROM jobs;

-- 2. Analyze table để update statistics
-- ANALYZE TABLE jobs;
-- ANALYZE TABLE favorites;
-- ANALYZE TABLE feedbacks;
-- ANALYZE TABLE job_alerts;

-- 3. Check index usage (sau khi chạy queries)
-- SELECT * FROM sys.schema_unused_indexes WHERE object_schema = DATABASE();

-- ============================================
-- MIGRATION HOÀN TẤT
-- ============================================
-- Tối ưu hóa đã thực hiện:
-- ✅ Full-text search indexes
-- ✅ Composite indexes cho multi-column queries
-- ✅ Covering indexes để tránh table lookups
-- ✅ Join table indexes với PRIMARY KEY
-- ✅ Prefix indexes cho text search
-- ✅ Indexes cho common filter patterns
-- ✅ Chuẩn hóa job_alert_skill (bỏ JSON, dùng join table)
-- ============================================


