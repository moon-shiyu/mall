-- 为 pms_product 增加创建时间列（支持后台商品列表按创建时间范围筛选/排序）。
-- 对已有数据库执行本脚本即可；新列默认 CURRENT_TIMESTAMP，历史数据回填为当前时间。
-- 注意：document/sql/mall.sql 中 pms_product 使用位置式 INSERT（无列名），
--      不能直接在其 CREATE TABLE 增列，否则会导致列数不匹配，故以本迁移脚本统一加列。
ALTER TABLE `pms_product`
    ADD COLUMN `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

UPDATE `pms_product` SET `create_time` = NOW() WHERE `create_time` IS NULL;
