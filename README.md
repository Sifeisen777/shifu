# 食赴 ShiFu

面向「到店」场景的本地生活社区：用户按分类发现商户、领取到店权益卡、发布实拍笔记，并通过关注关系形成信息流。

后端 Spring Boot 3 + MySQL + Redis + RocketMQ，前端 Vue 3 前后端分离，秒杀下单走 Redis + Lua 原子扣减，再经 RocketMQ 异步落库。

---

## 技术栈

| 层 | 技术 |
|---|---|
| 运行时 | JDK 17、Spring Boot 3.5 |
| 持久层 | MyBatis-Plus 3.5、MySQL 8.0 |
| 缓存 / 中间件 | Redis 8（缓存、GEO、点赞 ZSet、Feed 收件箱）、Redisson（分布式锁）、RocketMQ 5.3（订单异步落库） |
| 构建 | Maven |
| 前端 | Vue 3 + Vite 6 + Vue Router 4 + Pinia + Axios（自建设计系统，未用组件库） |
| 静态托管 | nginx（静态资源 + `/api` 反向代理） |

## 功能

| 模块 | 能力 |
|---|---|
| 商户 | 分类浏览、按距离/评分/人气排序、关键字搜索、详情与图集 |
| 笔记 | 热门瀑布流、图文发布（多图上传）、点赞、关注作者、关注流（Feed） |
| 权益卡 | 商户权益列表、限时秒杀（Redis + Lua 预扣库存、MQ 异步落库） |
| 账户 | 手机验证码登录、资料编辑、每日打卡、他人主页与共同关注 |
| 基础设施 | 商户 GEO 索引自动初始化、登录 token 服务端失效、统一响应与异常 |

## 架构

```
                    ┌──────────────────────────────┐
   浏览器  ───────►  │  nginx :8080                 │
                    │  ├─ /        → html/shifu     │  Vue3 构建产物
                    │  └─ /api/*   → 127.0.0.1:8081 │  反向代理
                    └──────────────┬───────────────┘
                                   │
                    ┌──────────────▼───────────────┐
                    │  shifu-server :8081          │
                    │  Controller → Service → Mapper│
                    └───┬──────────┬──────────┬────┘
                        │          │          │
                   ┌────▼───┐ ┌────▼───┐ ┌────▼─────┐
                   │ MySQL  │ │ Redis  │ │ RocketMQ │
                   │ 业务数据│ │缓存/GEO│ │ 订单异步 │
                   └────────┘ └────────┘ └──────────┘
```

### 秒杀链路

1. 前端 `POST /benefit-order/seckill/{id}`
2. 服务端执行 `seckill.lua`：校验库存与一人一单，原子扣减并写入下单标记
3. 脚本返回 0 则投递 RocketMQ；投递失败回滚 Redis 库存与标记，保证不超卖
4. 消费者落库生成订单

## 快速开始

### 1. 准备依赖

- MySQL 8.0，导入 `src/main/resources/db/shifu.sql`
- Redis（默认 `127.0.0.1:6379`）
- RocketMQ NameServer `9876` / Broker `10911`（未启动只影响秒杀落库，不影响启动）

配置在 `src/main/resources/application.yaml`。

### 2. 启动后端

```bash
mvn clean package -DskipTests
java -jar target/shifu-server-1.0.0-SNAPSHOT.jar
```

> 一定要带 `clean`：`target` 里残留旧资源会被打进 jar，导致启动时找不到映射文件。

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev          # http://localhost:5173
```

或构建后交给 nginx：

```bash
npm run publish:nginx   # 构建并写入 nginx 静态目录
```

nginx 配置见 `frontend/README.md`。

## 目录结构

```
├─ src/main/java/com/shifu/
│  ├─ controller/       # 9 个 REST 控制器
│  ├─ service/          # 业务接口与实现
│  ├─ mapper/           # MyBatis-Plus Mapper
│  ├─ entity/           # 表实体
│  ├─ dto/              # 统一响应、DTO、滚动分页
│  ├─ config/           # MVC 拦截器、Redisson、GEO 初始化
│  ├─ mq/               # 秒杀订单生产者 / 消费者
│  └─ utils/            # 常量、登录上下文、ID 生成器
├─ src/main/resources/
│  ├─ application.yaml
│  ├─ db/shifu.sql      # 建表与种子数据
│  ├─ mapper/           # XML 映射
│  └─ seckill.lua       # 秒杀原子脚本
├─ frontend/            # Vue 3 前端（见 frontend/README.md）
└─ docs/                # 部署与重构说明
```

## 文档

- [`docs/重构方案.md`](docs/重构方案.md)：从教学项目到本项目的命名与结构调整
- [`docs/RocketMQ部署与接入.md`](docs/RocketMQ部署与接入.md)：RocketMQ 部署与项目接入
- [`frontend/README.md`](frontend/README.md)：前端结构、接口映射与设计决定
