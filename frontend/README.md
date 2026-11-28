# 食赴 ShiFu · Web 前端

本地生活社区的移动端 H5 应用，基于 Vue 3 + Vite 构建，与后端服务分离部署。生产构建产物主包 gzip 后约 68 KB。

## 技术选型

| 能力 | 方案 | 说明 |
| --- | --- | --- |
| 框架 | Vue 3.5 | 全部页面采用 Composition API 与 `<script setup>` |
| 构建 | Vite 6 | 开发热更新，生产环境按路由分包 |
| 路由 | Vue Router 4 | history 模式，需服务端配置回退 |
| 状态管理 | Pinia | 仅登录态使用全局 store，其余为页面局部状态 |
| 请求层 | Axios | 统一注入令牌、拆解响应包装、集中处理 401 |
| 样式 | 原生 CSS + 设计令牌 | 详见「样式体系」 |

## 目录结构

```
frontend/
├── index.html
├── vite.config.js            开发代理 /api -> 127.0.0.1:8081
├── scripts/publish.mjs       构建产物发布至 nginx 静态目录
└── src/
    ├── main.js               应用入口，注册请求层与登录态联动
    ├── App.vue               应用外壳、路由过渡、全局提示容器
    ├── api/
    │   ├── http.js           Axios 实例与拦截器
    │   └── index.js          按业务分组的接口定义
    ├── router/index.js       路由表与登录守卫
    ├── store/auth.js         令牌与用户信息持久化
    ├── styles/
    │   ├── tokens.css        设计令牌：色彩、圆角、阴影、排版
    │   └── global.css        基础重置与通用样式
    ├── components/           图标、导航、卡片、评分、空状态等
    ├── utils/                格式化与轻量提示
    └── views/                页面组件
```

## 页面与接口映射

| 路由 | 页面 | 主要接口 |
| --- | --- | --- |
| `/` | 首页：分类入口与热门笔记瀑布流 | `GET /merchant-category/list`、`GET /note/hot` |
| `/merchants` | 商户列表：分类切换、排序、触底加载 | `GET /merchant/of/type` |
| `/merchant/:id` | 商户详情：图集、权益卡、秒杀 | `GET /merchant/{id}`、`GET /benefit/list/{id}` |
| `/note/:id` | 笔记详情：图集、点赞、关注、评论 | `GET /note/{id}`、`PUT /note/like/{id}`、`GET /note-comment/list/{id}`、`POST /note-comment` |
| `/note/new` | 发布笔记：多图上传、关联商户 | `POST /media/note`、`POST /note` |
| `/login` | 登录（验证码 / 密码切换） | `POST /user/code`、`POST /user/login` |
| `/me` | 个人主页：笔记、关注流、签到、密码设置 | `GET /user/me`、`GET /note/of/follow`、`PUT /user/password` |
| `/me/edit` | 资料编辑 | `GET /user/info/{id}`、`PUT /user/info` |
| `/user/:id` | 他人主页：关注与共同关注 | `GET /user/{id}`、`GET /follow/common/{id}` |

页面之外，`components/AssistantPanel.vue` 作为全局抽屉挂在 `App.vue` 上：悬浮入口打开面板，通过 `fetch` 读取 SSE 流实现逐字渲染，回答中的商户引用可跳转详情页。流式请求不走 axios —— 浏览器端的 axios 拿不到增量响应。

## 开发与构建

```bash
npm install
npm run dev            # http://localhost:5173，/api 代理至 8081

npm run build          # 生产构建
npm run publish:nginx  # 构建并发布至 nginx 静态目录
```

`publish:nginx` 默认写入 `../../nginx-1.18.0/html/shifu`，会清理旧构建产物并保留 `imgs/` 静态资源。目标目录不同时通过环境变量指定：

```bash
SHIFU_WEB_ROOT=/path/to/html/shifu npm run publish:nginx
```

## 部署配置

前端使用 history 路由，nginx 需将未命中的路径回退至 `index.html`：

```nginx
location / {
    root   html/shifu;
    index  index.html;
    try_files $uri $uri/ /index.html;
    add_header Cache-Control "no-cache, must-revalidate";
}

location /assets/ {
    root   html/shifu;
    add_header Cache-Control "public, max-age=31536000, immutable";
}

location /api {
    rewrite /api(/.*) $1 break;
    proxy_pass http://127.0.0.1:8081;
}
```

入口 HTML 不缓存、构建产物长期缓存，可在发布后立即生效且不影响加载性能。

## 样式体系

`src/styles/tokens.css` 定义了一套设计令牌，页面样式统一引用变量而非硬编码数值：

- **色彩**：暖白背景、陶土红主色、暖金评分色，与到店、食物类内容相协调
- **排版**：标题使用较大字重与负字距，正文保持 1.8 以上行高
- **组件**：卡片统一圆角与柔和投影，交互反馈基于 160–240ms 的缓动过渡

图标为内置的 24×24 线性路径组件（`components/BaseIcon.vue`），不引入额外图标库。

## 开发约定

- **金额单位**：商户人均 `avg_price` 以元存储，使用 `yuan()` 格式化；权益卡面额 `pay_value` 以分存储，使用 `money()` 格式化，两者不可混用
- **图片路径**：后端返回相对上传根目录的路径，由 `imageUrl()` 统一补全为 `/imgs` 前缀，同时兼容绝对地址
- **访问控制**：首页、商户、笔记详情对未登录用户开放；需要登录的接口在请求前阻断并引导登录，避免触发全局 401 跳转
