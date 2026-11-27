# RocketMQ 部署与接入

项目用 RocketMQ 承载两条异步链路：

| Topic | 生产者组 | 消费者组 | 用途 |
| --- | --- | --- | --- |
| `benefit-order-topic` | `benefit-order-producer` | `benefit-order-consumer` | 秒杀下单异步落库 |
| `note-feed-topic` | `note-feed-producer` | `note-feed-consumer` | 笔记发布后向粉丝收件箱扇出 |

## 一、配置项

```yaml
shifu:
  rocketmq:
    name-server: 127.0.0.1:9876
    producer-group: benefit-order-producer
    consumer-group: benefit-order-consumer
    topic: benefit-order-topic
    feed:
      producer-group: note-feed-producer
      consumer-group: note-feed-consumer
      topic: note-feed-topic
```

客户端使用 `org.apache.rocketmq:rocketmq-client:5.3.4`，未引入 starter。生产者与消费者均为手写 Bean，通过 `@PostConstruct` / `@PreDestroy` 自行管理生命周期，避免与 Spring Boot 3 的自动配置产生版本耦合。

## 二、代码结构

| 类 | 职责 |
| --- | --- |
| `mq.BenefitOrderProducer` | 秒杀订单生产者，同步发送，订单号作为消息 key |
| `mq.BenefitOrderConsumer` | 消费订单消息并落库，失败返回 `RECONSUME_LATER` 由 broker 重投 |
| `mq.NoteFeedProducer` | 笔记发布事件生产者，超时 1.2s + 1 次重试，避免拖慢发布接口 |
| `mq.NoteFeedConsumer` | 消费发布事件，调用 `IFeedService` 完成扇出 |
| `service.impl.FeedServiceImpl` | 查询粉丝列表，按 500 一批通过 pipeline 写入收件箱 ZSet |

消息体示例：

```json
// benefit-order-topic，key = 订单号
{ "id": 664390544012607489, "userId": 1010, "benefitId": 50 }

// note-feed-topic，key = note:{noteId}
{ "noteId": 25, "authorId": 1012, "publishTime": 1795668560114 }
```

## 三、部署（Windows 单机）

1. 下载 `rocketmq-all-5.3.4-bin-release` 并解压
2. 编辑 `conf\broker.conf`：

```properties
brokerClusterName = DefaultCluster
brokerName = broker-a
brokerId = 0
namesrvAddr = 127.0.0.1:9876
brokerIP1 = 127.0.0.1
deleteWhen = 04
fileReservedTime = 48
brokerRole = ASYNC_MASTER
flushDiskType = ASYNC_FLUSH
```

3. 先启动 NameServer，再启动 Broker：

```bat
set ROCKETMQ_HOME=<安装目录>
cd /d %ROCKETMQ_HOME%
bin\mqnamesrv.cmd
:: 另开一个命令行窗口
bin\mqbroker.cmd -c conf\broker.conf
```

4. 验证集群状态：

```bat
bin\mqadmin.cmd clusterList -n 127.0.0.1:9876
```

输出中包含 `broker-a` 且 `#ACTIVATED` 为 true 即部署完成。

## 四、部署（Linux）

```bash
# broker 默认堆内存较大，小规格机器建议显式调小，否则可能因内存不足启动失败
export JAVA_OPT_EXT="-Xms2g -Xmx2g"
nohup sh bin/mqnamesrv &
nohup sh bin/mqbroker -c conf/broker.conf -n 127.0.0.1:9876 &
```

跨机访问需放行 9876（NameServer）与 10909 / 10911（Broker），并将 `brokerIP1` 配置为 Broker 对外可达的地址。

## 五、运行验证

1. 启动应用，日志中出现两条 Topic 的生产者与消费者启动成功
2. 秒杀一单，确认：
   - Redis `seckill:stock:{benefitId}` 减 1
   - `biz_benefit_stock.stock` 同步减 1
   - `biz_benefit_order` 新增记录
3. 发布笔记，确认粉丝收件箱 `feed:{userId}` 中出现新笔记 id

## 六、降级策略

RocketMQ 未启动时应用仍可正常启动，相关功能自动降级：

- **秒杀**：Lua 判定通过但消息投递失败时，回滚 Redis 库存与下单标记并返回失败，不会产生「扣减库存但无订单」的中间态
- **关注流**：投递失败后降级为同步推送，功能不受影响，仅失去削峰能力

查看消费进度：

```bat
bin\mqadmin.cmd consumerProgress -g benefit-order-consumer -n 127.0.0.1:9876
```
