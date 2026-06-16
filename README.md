# 超星学习通自动签到系统 - 项目分析报告

## 1. 项目概述

测试网站：https://sign.missu.asia

**ChaoxingAutoSign** 是一个**超星学习通自动签到系统**。学生登录超星账号后，系统在后台定时监控课程签到活动，根据预设配置（签到码、地址、经纬度、时间窗口等）自动完成签到，省去手动操作。

**架构**：前后端分离
- 前端 `ChaoxingAutoSign_admin` — Vue 3 SPA
- 后端 `ChaoxingAutoSign_service` — Spring Boot REST API
- 部署 `deploy/` — Nginx 反向代理配置

**生产域名**：`sign.missu.asia`

---

## 2. 技术栈一览

| 层级 | 技术 | 版本 |
|------|------|------|
| **前端框架** | Vue 3 + TypeScript | ^3.5.34 / ~6.0.2 |
| **构建工具** | Vite | ^8.0.12 |
| **UI 组件库** | Element Plus | ^2.8.6 |
| **状态管理** | Pinia | ^2.2.6 |
| **路由** | Vue Router | ^4.4.5 |
| **HTTP 客户端** | Axios | ^1.7.7 |
| **后端框架** | Spring Boot | 4.1.0 |
| **语言** | Java 17 + Maven | - |
| **ORM** | Spring Data JPA (Hibernate) | - |
| **数据库** | MySQL 8 | `chaoxing_autosigns` |
| **缓存** | Redis (Lettuce) | localhost:6379 |
| **安全** | JWT (jjwt 0.12.6) + AES-128-CBC | - |
| **HTML 解析** | Jsoup | 1.17.2 |
| **部署** | Nginx HTTPS 反向代理 | - |

---

## 3. 项目目录结构

```
ChaoxingAutoSign/
├── ChaoxingAutoSign_admin/         # Vue 3 前端
│   ├── src/
│   │   ├── api/                    # API 调用层
│   │   │   ├── request.ts          # Axios 实例 + 拦截器
│   │   │   ├── auth.ts             # 登录/登出
│   │   │   ├── user.ts             # 用户信息
│   │   │   ├── course.ts           # 课程/课表/封面代理
│   │   │   ├── signConfig.ts       # 签到配置 CRUD
│   │   │   └── task.ts             # 任务控制 + 签到记录
│   │   ├── stores/                 # Pinia 状态管理
│   │   │   ├── auth.ts             # 登录状态/Token/用户信息
│   │   │   ├── course.ts           # 课程列表/文件夹
│   │   │   └── task.ts             # 任务运行状态（5s 轮询）
│   │   ├── router/index.ts         # 路由配置 + 守卫
│   │   ├── views/                  # 页面组件
│   │   │   ├── LoginView.vue       # 登录页
│   │   │   ├── DashboardView.vue   # 仪表盘
│   │   │   ├── CourseListView.vue  # 课程管理
│   │   │   ├── ScheduleView.vue    # 课程表
│   │   │   └── SignConfigView.vue  # 签到配置
│   │   ├── components/layout/      # 布局组件
│   │   │   └── AppLayout.vue       # 主布局（侧边栏+顶栏）
│   │   └── types/index.ts          # TypeScript 类型定义
│   ├── package.json
│   ├── vite.config.ts
│   └── tsconfig.json
│
├── ChaoxingAutoSign_service/       # Spring Boot 后端
│   └── src/main/java/chaoxing/autosign/
│       ├── ChaoxingAutoSignApplication.java  # 启动类
│       ├── config/                 # 配置类
│       │   ├── CorsConfig.java
│       │   ├── JwtAuthFilter.java
│       │   ├── RedisConfig.java
│       │   ├── RestTemplateConfig.java
│       │   └── SchedulerConfig.java
│       ├── controller/             # REST 控制器
│       │   ├── AuthController.java
│       │   ├── UserController.java
│       │   ├── CourseController.java
│       │   ├── SignConfigController.java
│       │   ├── SignRecordController.java
│       │   └── TaskController.java
│       ├── service/                # 业务逻辑层
│       │   ├── AuthService.java    # 认证（超星登录 + Cookie 管理）
│       │   ├── UserService.java
│       │   ├── CourseService.java  # 课程拉取 + 课程表解析（核心，~25KB）
│       │   ├── SignConfigService.java
│       │   └── SignTaskService.java # 签到执行引擎（核心，~23KB）
│       ├── entity/                 # JPA 实体
│       │   ├── User.java
│       │   ├── Course.java
│       │   ├── SignConfig.java
│       │   ├── SignTimeWindow.java
│       │   ├── SignRecord.java
│       │   ├── SignLog.java
│       │   └── TaskStatus.java
│       ├── repository/             # JPA Repository 接口
│       ├── dto/                    # 数据传输对象
│       │   ├── ApiResponse.java
│       │   ├── LoginRequest.java / LoginResponse.java
│       │   ├── SignConfigRequest.java
│       │   └── TaskStatusDTO.java
│       ├── scheduler/
│       │   └── SignScheduler.java  # 定时签到调度（每 30s）
│       └── util/
│           ├── AesUtil.java        # AES-128-CBC 加解密
│           └── JwtUtil.java        # JWT 生成/验证
│
└── deploy/
    └── sign.missu.asia.nginx.conf  # Nginx 站点配置
```

---

## 4. 路由与页面

| 路径 | 页面 | 认证 |
|------|------|------|
| `/login` | LoginView.vue | 否 |
| `/dashboard` | DashboardView.vue | 是 |
| `/courses` | CourseListView.vue | 是 |
| `/schedule` | ScheduleView.vue | 是 |
| `/sign-config` | SignConfigView.vue | 是 |

**路由守卫**：
- 未登录访问受保护页面 → 跳转 `/login`
- 已登录访问 `/login` → 跳转 `/dashboard`

---

## 5. API 端点一览

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/auth/login` | 超星账号登录 | 否 |
| POST | `/api/auth/logout` | 登出 | 是 |
| GET | `/api/user/info` | 获取用户信息 | 是 |
| GET | `/api/course/list` | 课程列表 | 是 |
| GET | `/api/course/folders` | 课程文件夹 | 是 |
| GET | `/api/course/schedule` | 课程表 | 是 |
| GET | `/api/course/cover` | 课程封面代理 | 否 |
| GET | `/api/sign-config` | 签到配置列表 | 是 |
| POST | `/api/sign-config` | 创建签到配置 | 是 |
| PUT | `/api/sign-config/{id}` | 更新签到配置 | 是 |
| DELETE | `/api/sign-config/{id}` | 删除签到配置 | 是 |
| POST | `/api/task/start` | 启动签到任务 | 是 |
| POST | `/api/task/stop` | 停止签到任务 | 是 |
| GET | `/api/task/status` | 任务状态 | 是 |
| GET | `/api/sign-record/list` | 签到记录（分页） | 是 |
| GET | `/api/sign-record/logs` | 签到日志 | 是 |
| DELETE | `/api/sign-record/logs` | 清空日志 | 是 |

---

## 6. 数据库设计

**数据库**：MySQL 8 — `chaoxing_autosigns`（JPA `ddl-auto=update`）

| 表名 | 实体 | 用途 |
|------|------|------|
| `t_user` | User | 用户账户、加密密码、超星 Cookie |
| `t_course` | Course | 课程信息（按 userId+courseId 唯一） |
| `t_sign_config` | SignConfig | 签到配置（按 userId+courseName 唯一） |
| `t_sign_time_window` | SignTimeWindow | 签到时间窗口（多时段支持） |
| `t_sign_record` | SignRecord | 签到记录 |
| `t_sign_log` | SignLog | 签到日志 |
| `t_task_status` | TaskStatus | 签到任务运行状态 |

---

## 7. Redis 缓存设计

| Key 模式 | 用途 | TTL |
|----------|------|-----|
| `token:{userId}` | JWT 刷新令牌 | 7 天 |
| `cookie:{userId}` | 超星 Cookie 缓存 | 2 小时 |
| `user:info:{userId}` | 用户信息缓存 | 30 分钟 |
| `course:list:{userId}` | 课程列表缓存 | 10 分钟 |
| `sign:lock:{userId}:{activeId}` | 签到防重锁 | 1 小时 |
| `sign:done:{userId}:{configId}:{today}:{startTime}` | 当日窗口已完成标记 | 到当天 23:59:59 |
| `task:running:{userId}` | 任务运行标记 | - |

---

## 8. 核心签到引擎

### 8.1 调度机制
- `SignScheduler` — `@Scheduled(fixedRate = 30000)` 每 **30 秒**触发一次
- 遍历所有 `task_status = "running"` 的用户执行签到循环

### 8.2 签到流程
```
1. 获取活跃签到活动列表 (getActiveList)
2. 匹配签到配置 → 找到对应 SignConfig
3. 检查时间窗口 → 当前时间是否命中某窗口
4. 检查窗口是否已完成 → Redis sign:done 标记
5. 预签到 (preSign) → 获取签到表单参数
6. 执行签到 (executeSign) → 提交签到请求
7. 成功后标记窗口完成 → sign:done + TTL
```

### 8.3 Cookie 失效自动刷新
- `AuthService.refreshCookie(userId)` — 用 DB 中的加密密码重新登录超星，刷新 Cookie 并更新 DB + Redis
- `SignTaskService` 检测到 API 返回 HTML 时自动触发刷新，`refreshedUsers` 集合防止同一轮反复刷新

### 8.4 多时间窗口支持
- `SignTimeWindow` 表支持一个签到配置对应多个时间段
- 签到成功后在 Redis 写入 `sign:done` 标记，后续轮询直接跳过，避免被超星防火墙拦截

### 8.5 防重签
- 使用 Redis `SETNX` + TTL 实现分布式锁 `sign:lock:{userId}:{activeId}`

### 8.6 登录加密
- **AES-128-CBC** 加密密码，密钥/IV: `u2oh6Vu^HWe4_AES`，与超星登录接口加密方式一致

---

## 9. 安全配置

| 配置项 | 值 |
|--------|-----|
| JWT 密钥 | `ChaoxingAutoSign2024SecretKeyForJWTTokenGeneration` |
| JWT 有效期 | 86400000ms (24小时) |
| JWT 过滤器路径 | 排除 `/api/auth/**` 和 `/api/course/cover` |
| CORS | 所有来源开放 |
| 后端端口 | 18080 |
| 前端开发端口 | 5173 |

---

## 10. 部署架构

```
                       ┌──────────────────────┐
                       │   Nginx (443/80)      │
                       │   sign.missu.asia     │
                       ├──────────────────────┤
                       │ /        → /dist/     │  ← Vue 静态文件
                       │ /api/*   → :18080     │  ← Spring Boot API
                       └──────────────────────┘
                                │
                    ┌───────────┴───────────┐
                    │                       │
              ┌─────▼─────┐          ┌─────▼─────┐
              │  MySQL    │          │   Redis   │
              │  :3306    │          │   :6379   │
              └───────────┘          └───────────┘
```

**Nginx 配置**（`deploy/sign.missu.asia.nginx.conf`）：
- 静态文件根目录：`/www/wwwroot/sign.missu.asia/dist`
- SSL 证书由宝塔面板管理
- HTTP → HTTPS 自动跳转
- SPA 路由支持：`try_files $uri $uri/ /index.html`
- API 反向代理到 `http://127.0.0.1:18080`

---

## 11. 已知风险点

| 风险 | 说明 | 建议 |
|------|------|------|
| AES 密钥硬编码 | `AesUtil.java` 明文存储加密密钥 | 迁移至环境变量或配置中心 |
| JWT 密钥明文 | `application.properties` 明文配置 | 使用环境变量覆盖 |
| MySQL 密码明文 | `123456` 直接写在配置文件 | 使用环境变量 |
| CORS 全开放 | 允许所有来源访问 | 生产环境限制具体域名 |
| 无 Spring Security | 手写 JWT 过滤器，缺少成熟框架保护 | 考虑迁移至 Spring Security |
| 访问日志关闭 | Nginx `access_log /dev/null` | 至少保留一段时间用于排查 |

---

## 12. 开发命令

```bash
# 前端
cd ChaoxingAutoSign_admin
npm install          # 安装依赖
npm run dev          # 启动开发服务器 (localhost:5173)
npm run build        # 生产构建
npm run preview      # 预览生产构建

# 后端
cd ChaoxingAutoSign_service
mvn spring-boot:run  # 启动 Spring Boot (localhost:18080)
```

---

## 13. 功能待办

- [ ] 移动端响应式适配（AppLayout / DashboardView / CourseListView / ScheduleView / SignConfigView / LoginView）
- [ ] 创建 `src/composables/useResponsive.ts` 断点检测
- [ ] 安装前端 npm 依赖后验证
- [ ] 课表同步功能完善
