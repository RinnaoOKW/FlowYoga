-- MySQL 8.0.16+；每个服务使用独立账号，仅授权自己的库。
CREATE DATABASE IF NOT EXISTS flowyoga_user CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS flowyoga_member CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS flowyoga_course CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
-- 用户库由 flowyoga-user 的 Flyway V1__user_identity.sql 管理，禁止重复手工建表。
