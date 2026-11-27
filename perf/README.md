# 压测脚本

秒杀链路的负载测试脚本与数据夹具，配合 [压测报告](../docs/压测报告.md) 使用。

## 目录

| 文件 | 说明 |
| --- | --- |
| `generate-data.py` | 生成压测用户、权益卡、登录态与 JMeter 令牌表 |
| `seckill.jmx` | 秒杀下单压测计划（500 并发，抢 100 库存） |
| `note-hot.jmx` | 首页读接口压测计划，用于对照 |
| `out/` | 运行产物（夹具、结果、报告），不纳入版本控制 |

## 前置条件

- 后端、MySQL、Redis、RocketMQ 均在运行
- JMeter 5.6.x（脚本未随仓库分发）

## 复现步骤

```bash
# 1. 生成夹具（默认 500 个用户，令牌形如 bench0001u900001，可重复生成）
python perf/generate-data.py 500

# 2. 导入压测用户与两张权益卡：9001 库存 100，9002 库存 1000（预热用）
mysql -uroot -p"$MYSQL_PASSWORD" -D shifu < perf/out/fixture.sql

# 3. 写入登录态与库存快照
redis-cli -a "$REDIS_PASSWORD" --pipe < perf/out/redis.cmds
redis-cli -a "$REDIS_PASSWORD" set seckill:stock:9001 100
redis-cli -a "$REDIS_PASSWORD" del seckill:order:9001

# 4. 预热一轮，消除 JIT 与连接池冷启动的影响
jmeter -n -t perf/seckill.jmx -l perf/out/warmup.jtl -JBENEFIT_ID=9002 -Jthreads=500 -Jramp=1

# 5. 正式压测（每轮前需重置库存与订单）
jmeter -n -t perf/seckill.jmx -l perf/out/r1.jtl -JBENEFIT_ID=9001 -Jthreads=500 -Jramp=1

# 6. 生成 HTML 报告
jmeter -g perf/out/r1.jtl -o perf/out/report
```

## 可调参数

`-J` 传入，未指定时取默认值：

| 参数 | 默认 | 说明 |
| --- | --- | --- |
| `threads` | 500 | 并发线程数 |
| `ramp` | 1 | 并发爬升秒数 |
| `loops` | 1 | 每线程请求次数 |
| `BENEFIT_ID` | 9001 | 目标权益卡 |

## 结果校验

压测只提供延迟与吞吐，正确性以两侧数据为准：

```sql
-- 订单数应等于库存数，库存应归零
SELECT COUNT(*) FROM biz_benefit_order WHERE benefit_id = 9001;
SELECT stock FROM biz_benefit_stock WHERE benefit_id = 9001;
```

```bash
# Redis 侧：库存归零，下单用户数等于成交单数
redis-cli -a "$REDIS_PASSWORD" get seckill:stock:9001
redis-cli -a "$REDIS_PASSWORD" scard seckill:order:9001
```
