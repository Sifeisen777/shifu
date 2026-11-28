-- 演示用秒杀券：可重复执行，不影响普通权益卡
-- 执行后会插入 3 张限时券与对应库存，库存键由应用启动时预热到 Redis

DELETE FROM biz_benefit_stock WHERE benefit_id IN (101, 102, 103);
DELETE FROM biz_benefit WHERE id IN (101, 102, 103);

INSERT INTO biz_benefit (id, merchant_id, title, sub_title, rules, pay_value, actual_value, type, status) VALUES
(101, 1, '双人下午茶套餐', '限时秒杀 · 每日限量', '仅限堂食\n每单限用一张\n不与店内其他优惠同享', 2900, 6800, 1, 1),
(102, 4, '招牌菜双人餐', '限时秒杀 · 每日限量', '需提前预约\n仅限堂食\n每单限用一张', 5900, 12800, 1, 1),
(103, 14, '欢唱三小时套餐', '限时秒杀 · 每日限量', '周一至周四可用\n需提前一天预约', 4900, 10800, 1, 1);

INSERT INTO biz_benefit_stock (benefit_id, stock, begin_time, end_time) VALUES
(101, 100, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 7 DAY)),
(102, 100, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 7 DAY)),
(103, 100, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 7 DAY));
