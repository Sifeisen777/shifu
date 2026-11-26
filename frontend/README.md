# 食赴 ShiFu · Web 前端

本地生活社区的移动端 H5 前端，Vue 3 + Vite 构建，与 `shifu-server` 后端分离部署。

## 技术选型

| 能力 | 方案 | 说明 |
|---|---|---|
| 框架 | Vue 3.5（Composition API + `<script setup>`） | 全部页面使用 SFC |
| 构建 | Vite 6 | 开发热更新、生产按路由分包 |
| 路由 | Vue Router 4（history 模式） | 需服务端 `try_files` 回退 |
| 状态 | Pinia | 仅登录态一个 store，其余为页面局部状态 |
| 请求 | Axios | 统一拦截器：注入 token、拆解 `Result`、401 统一跳登录 |
| 样式 | 原生 CSS + 设计令牌 | 未引入组件库，见下文「为什么不用组件库」 |

## 目录结构

```
frontend/
├─ index.html
├─ vite.config.js          # 开发代理 /api -> 127.0.0.1:8081
├─ scripts/publish.mjs     # 构建产物发布到 nginx 静态目录
└─ src/
   ├─ main.js              # 应用入口 + 401 处理注册
   ├─ App.vue              # 应用外壳、路由过渡、Toast 容器
   ├─ api/
   │  ├─ http.js           # Axios 实例与拦截器
   │  └─ index.js          # 按业务分组的接口函数
   ├─ router/index.js      # 路由表 + 登录守卫
   ├─ store/auth.js        # token / 用户信息持久化
   ├─ styles/
   │  ├─ tokens.css        # 设计令牌（配色、圆角、阴影、版式）
   │  └─ global.css        # 重置与通用原子类
   ├─ components/          # 图标、导航、卡片、评分、空状态等
   ├─ utils/               # 格式化、Toast
   └─ views/               # 9 个页面
```

## 页面与接口对应

| 路由 | 页面 | 主要接口 |
|---|---|---|
| `/` | 首页（分类 + 热门笔记瀑布流） | `GET /merchant-category/list`、`GET /note/hot`、`GET /merchant/of/name` |
| `/merchants` | 商户列表（分类切换 / 排序 / 触底加载） | `GET /merchant/of/type` |
| `/merchant/:id` | 商户详情（图集 / 权益卡 / 秒杀） | `GET /merchant/{id}`、`GET /benefit/list/{id}`、`POST /benefit-order/seckill/{id}` |
| `/note/:id` | 笔记详情（图集 / 点赞 / 关注） | `GET /note/{id}`、`GET /note/likes/{id}`、`PUT /note/like/{id}`、`PUT /follow/{id}/{bool}` |
| `/note/new` | 发布笔记（多图上传 / 关联商户） | `POST /media/note`、`POST /note` |
| `/login` | 手机验证码登录 | `POST /user/code`、`POST /user/login` |
| `/me` | 个人主页（笔记 / 关注动态 / 打卡） | `GET /user/me`、`GET /note/of/me`、`GET /note/of/follow`、`POST /user/sign` |
| `/me/edit` | 资料编辑 | `GET /user/info/{id}`、`PUT /user/info` |
| `/user/:id` | 他人主页（关注 / 共同关注） | `GET /user/{id}`、`GET /follow/common/{id}` |

## 开发与发布

```bash
# 开发（需后端已在 8081 运行）
npm install
npm run dev          # http://localhost:5173，/api 自动代理到 8081

# 生产构建
npm run build

# 构建并发布到 nginx 静态目录
npm run publish:nginx
```

`publish:nginx` 默认写入 `../../nginx-1.18.0/html/shifu`，会清理旧版 html 与 css/js，保留 `imgs/` 静态资源。
目录不同时用环境变量覆盖：`SHIFU_WEB_ROOT=D:/path/to/html/shifu npm run publish:nginx`。

nginx 需要为 history 路由配置回退：

```nginx
location / {
    root   html/shifu;
    index  index.html;
    try_files $uri $uri/ /index.html;
}
location /api {
    rewrite /api(/.*) $1 break;
    proxy_pass http://127.0.0.1:8081;
}
```

## 几个刻意的设计决定

**为什么不用组件库。**
组件库的默认视觉（圆角、阴影、间距、主色）会立刻暴露"套模板"。这里用 `tokens.css` 定义一套设计令牌：暖纸质底 `#f5f0e8`、陶土红主色 `#ce4a26`、暖金评分 `#b08a4f`，卡片统一 16px 圆角配暖色投影。图标是内置的 24×24 线性路径集，不额外引依赖。最终产物主包 gzip 后约 68KB。

**为什么首页用深咖啡色 Hero。**
食物图片普遍偏暖偏亮，浅色背景会让图片"糊"在页面里。深色区把内容图衬托出来，也让品牌区和列表区形成明确层次。

**图片路径为什么要拼 `/imgs`。**
后端 `/media/note` 返回的是相对上传根目录的路径（如 `/notes/0/0/xxx.png`），静态资源由 nginx 挂在 `/imgs` 下。`utils/format.js` 的 `imageUrl()` 统一处理绝对地址与相对路径两种情况。

**金额单位不统一。**
`biz_merchant.avg_price` 存的是**元**，`biz_benefit.pay_value` 存的是**分**。格式化函数因此分成 `yuan()` 与 `money()` 两个，不要混用。

**游客能看什么。**
首页、商户、笔记详情对游客开放（后端把 `/note/*` 加入白名单）；笔记详情里的"点赞用户列表"是受保护接口，未登录时不请求，否则会触发 401 跳登录。
