-- 关注关系约束与计数校准（面向已在运行的数据库，仅首次执行）
-- 新部署直接导入 shifu.sql 即可，无需执行本脚本。

-- 1. 清理历史重复关系，保留 id 最小的一条
DELETE f FROM `biz_follow` f
  JOIN `biz_follow` d
    ON f.user_id = d.user_id
   AND f.follow_user_id = d.follow_user_id
   AND f.id > d.id;

-- 2. 一个人对同一用户只能有一条关注关系，并发重复关注由数据库兜底
ALTER TABLE `biz_follow`
  ADD UNIQUE INDEX `uk_user_follow` (`user_id`, `follow_user_id`);

-- 3. 按关系表重算已有资料行的计数
UPDATE `sys_user_profile` p
   SET p.`followee` = (SELECT COUNT(*) FROM `biz_follow` f WHERE f.user_id = p.user_id),
       p.`fans` = (SELECT COUNT(*) FROM `biz_follow` f WHERE f.follow_user_id = p.user_id);

-- 4. 为参与关注关系但尚无资料行的用户补齐记录
INSERT INTO `sys_user_profile` (`user_id`, `fans`, `followee`)
SELECT t.id,
       (SELECT COUNT(*) FROM `biz_follow` WHERE follow_user_id = t.id),
       (SELECT COUNT(*) FROM `biz_follow` WHERE user_id = t.id)
  FROM (
        SELECT user_id AS id FROM `biz_follow`
        UNION
        SELECT follow_user_id FROM `biz_follow`
       ) t
 WHERE NOT EXISTS (SELECT 1 FROM `sys_user_profile` p WHERE p.user_id = t.id);

-- 应用启动时也会自动校准（FollowCountInitializer），后续无需重复执行本脚本。
