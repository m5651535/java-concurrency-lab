-- 建立資料表
CREATE TABLE IF NOT EXISTS users (
                                     id SERIAL PRIMARY KEY,
                                     username VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL
    );

-- 預填 100 筆測試資料，確保預熱機制有東西跑
INSERT INTO users (id, username, email)
SELECT i, 'user_' || i, 'user_' || i || '@example.com'
FROM generate_series(1, 100) AS i;