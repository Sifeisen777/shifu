"""压测数据生成器。

生成三类产物（写入 perf/out/）：
  fixture.sql  —— 压测用户、压测权益卡与库存，可重复执行
  tokens.csv   —— JMeter 的 CSV Data Set 输入，每行一个独立登录态
  redis.cmds   —— 登录态写入 Redis 的命令流，配合 redis-cli --pipe 批量导入

用法：python perf/generate-data.py [用户数]
"""

import os
import string

USER_COUNT = int(os.sys.argv[1]) if len(os.sys.argv) > 1 else 500
USER_ID_BASE = 900001
BENEFIT_ID = 9001
STOCK = 100
WARMUP_ID = 9002
WARMUP_STOCK = 1000
OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "out")


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    users, tokens, redis_lines = [], ["token,userId"], []

    for i in range(USER_COUNT):
        user_id = USER_ID_BASE + i
        phone = "137{:08d}".format(i + 1)
        users.append("({}, '{}', '', 'bench_{:03d}', '')".format(user_id, phone, i + 1))

        # 令牌固定成「序号 + 用户id」，重复生成不会让已写入 Redis 的登录态失效
        token = "bench{:04d}u{}".format(i + 1, user_id)
        tokens.append("{},{}".format(token, user_id))
        # RESP 协议：HSET 三个字段 + EXPIRE，一条连接内顺序执行
        redis_lines.append(
            "*8\r\n$4\r\nHSET\r\n$%d\r\n%s\r\n$2\r\nid\r\n$%d\r\n%d\r\n"
            "$8\r\nnickName\r\n$9\r\nbench_%03d\r\n$4\r\nicon\r\n$0\r\n\r\n"
            % (len("login:token:" + token), "login:token:" + token,
               len(str(user_id)), user_id, i + 1)
        )
        redis_lines.append(
            "*3\r\n$6\r\nEXPIRE\r\n$%d\r\n%s\r\n$5\r\n43200\r\n"
            % (len("login:token:" + token), "login:token:" + token)
        )

    sql = [
        "-- 压测夹具：可重复执行，不影响业务数据",
        "DELETE FROM biz_benefit_order WHERE benefit_id IN ({}, {});".format(BENEFIT_ID, WARMUP_ID),
        "DELETE FROM biz_benefit_stock WHERE benefit_id IN ({}, {});".format(BENEFIT_ID, WARMUP_ID),
        "DELETE FROM biz_benefit WHERE id IN ({}, {});".format(BENEFIT_ID, WARMUP_ID),
        "INSERT INTO biz_benefit (id, merchant_id, title, sub_title, rules, pay_value, actual_value, type, status)"
        " VALUES ({}, 1, '压测权益卡', '仅用于负载测试', '压测数据', 100, 1000, 1, 1),"
        " ({}, 1, '预热权益卡', '仅用于 JVM 预热', '压测数据', 100, 1000, 1, 1);".format(BENEFIT_ID, WARMUP_ID),
        "INSERT INTO biz_benefit_stock (benefit_id, stock, begin_time, end_time)"
        " VALUES ({}, {}, '2020-01-01 00:00:00', '2035-01-01 00:00:00'),"
        " ({}, {}, '2020-01-01 00:00:00', '2035-01-01 00:00:00');".format(
            BENEFIT_ID, STOCK, WARMUP_ID, WARMUP_STOCK),
        "DELETE FROM sys_user WHERE id BETWEEN {} AND {};".format(USER_ID_BASE, USER_ID_BASE + USER_COUNT - 1),
        "INSERT INTO sys_user (id, phone, password, nick_name, icon) VALUES",
        ",\n".join(users) + ";",
    ]

    with open(os.path.join(OUT_DIR, "fixture.sql"), "w", encoding="utf-8") as f:
        f.write("\n".join(sql) + "\n")
    with open(os.path.join(OUT_DIR, "tokens.csv"), "w", encoding="utf-8") as f:
        f.write("\n".join(tokens) + "\n")
    with open(os.path.join(OUT_DIR, "redis.cmds"), "w", encoding="utf-8", newline="") as f:
        f.write("".join(redis_lines))

    print("users={} benefit_id={} stock={}".format(USER_COUNT, BENEFIT_ID, STOCK))
    print("output ->", OUT_DIR)


if __name__ == "__main__":
    main()
