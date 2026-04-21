# NyxGuard 用户管理模块开发文档

## 1. 模块定位

用户管理模块负责以下能力：

- 用户注册、登录、登录态持久化
- 个人中心基础资料展示
- 守护者列表读取、添加、删除
- 用户资料与守护者相关接口的客户端接入

当前项目口径为：

- Android 客户端 + FastAPI 后端
- 生产数据库为 Neon Postgres
- 本地开发、演示和离线调试可使用 SQLite fallback
- 生产部署采用 FastAPI 独立部署
- Vercel 用于承载前端展示页、静态内容或文档站

`MockApiClient` 仅作为开发调试兜底，不应视为正式生产链路。

## 2. 当前实现范围

### 2.1 Android 侧已实现

- `LoginActivity`：登录页面与表单校验
- `RegisterActivity`：注册页面与表单校验
- `TokenManager`：JWT Token、本地用户 ID、昵称缓存
- `ProfileFragment`：个人信息读取与展示、退出登录
- `GuardianActivity`：守护者列表、添加、删除
- `GuardianAdapter` / `Guardian`：守护者列表展示模型
- `ValidationUtils`：手机号、昵称、密码等输入校验

### 2.2 网络层现状

- 主线网络层为 `ApiClient + Retrofit + OkHttp + Gson`
- 个人中心、守护者管理等页面已依赖真实 `ApiClient`
- `MockApiClient` 仍保留用于开发调试与局部未完成联调场景
- 主线方向应统一收敛到真实 FastAPI 接口与 typed DTO

## 3. 技术栈

### 3.1 Android 客户端

- Kotlin
- ViewBinding
- Material Design 3
- Retrofit + OkHttp
- Gson
- SharedPreferences（登录态本地持久化）

### 3.2 后端与数据

- FastAPI
- SQLAlchemy
- JWT 认证
- Neon Postgres（生产）
- SQLite fallback（本地开发 / 演示）

## 4. 关键业务规则

- 登录成功后保存 Token、用户 ID、昵称
- Token 有效期口径为 7 天
- 用户最多添加 5 名守护者
- 步行/乘车模式使用前至少应选 1 名守护者
- 密码规则为 6-20 位，需包含字母和数字

## 5. 开发与联调说明

### 5.1 主线联调原则

- 真实接口优先，Mock 仅用于开发兜底
- 文档、接口字段、客户端解析逻辑应统一按 FastAPI 主线维护
- 不再使用“仓库只有 Android 客户端代码”或其他过时架构口径

### 5.2 调试注意事项

- `ApiClient` 负责真实后端地址和鉴权头注入
- `TokenManager` 为登录态唯一来源，修改登录返回结构时需同步调整
- 若临时启用 `MockApiClient`，应确保字段名与 FastAPI 主线合同一致，避免契约漂移

## 6. 后续工作建议

- 保持登录/注册以真实 FastAPI 接口为主链路，Mock 仅在 debug fallback 场景使用
- 继续保持 typed DTO 契约一致性，避免页面侧重新引入 `JsonObject` 解析
- 补齐个人资料编辑链路
- 为用户管理模块补充单元测试和联调测试

## 7. 相关文档

- `AGENTS.md`
- `CLAUDE.md`
- `Docs/需求规格说明书.md`
- `Docs/功能设计说明书.md`
- `Docs/数据库设计说明书.md`

## 8. 文档同步说明

以下材料为二进制文件，不会随本次 Markdown 更新自动同步，需后续人工同步内容口径：

- `Docs/PPT/*.pptx`
- `Docs/项目介绍.pdf`
- `Docs/课程大纲.pdf`
