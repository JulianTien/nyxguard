# NyxGuard Local Regression Guide

## 目标

这份手册把“本地联调全功能回归测试规划”落成可执行流程，用于 Android 模拟器 + 本地 FastAPI 的发布前回归。

- 文档基线：`Docs/需求规格说明书.md`、`Docs/功能设计说明书.md`
- Android 包名：`com.scf.nyxguard`
- 本地 API：`http://10.0.2.2:5001/` 或本机可访问地址
- 交付物：测试结果表、缺陷单、需求符合性差异清单、截图/日志包

## 目录结构

```text
Docs/QA/
  LOCAL_REGRESSION.md
  artifacts/
  templates/
    test_results_template.md
    defect_log_template.md
    spec_gap_template.md
```

## 推荐执行顺序

1. 在 `~/.gradle/gradle.properties` 或项目 `local.properties` 配置 `nyxGuardApiBaseUrl`
2. 启动本地后端：`cd backend && python3 app.py`
3. 运行准备脚本：`./scripts/run_local_regression_prep.sh`
4. 开始 Android 回归，持续抓取 logcat：
   `./scripts/android_logcat_session.sh start Docs/QA/artifacts/<session_id>`
5. 每个关键节点抓状态快照：
   `./scripts/android_capture_state.sh Docs/QA/artifacts/<session_id> login_success`
6. 回归结束后停止 logcat：
   `./scripts/android_logcat_session.sh stop Docs/QA/artifacts/<session_id>`
7. 用模板整理结果、缺陷和需求差异

## 会话准备

### 环境前置

- Android 13+ 模拟器已启动并通过 `adb devices` 可见
- 本地后端已启动，SQLite fallback 可用
- Debug APK 可构建安装
- 首次测试前清除应用数据并允许定位、通知权限

### 一键准备

```bash
./scripts/run_local_regression_prep.sh
```

脚本会：

- 检查 `adb`、`python3`、`./gradlew`
- 检查 Android API URL 配置
- 运行 `scripts/api_smoke.py --profile local`
- 运行 `backend/tests/test_v2_api.py`
- 构建 `assembleDebug`
- 向已连接模拟器安装 Debug 包、清空数据、授予基础权限并启动应用
- 创建一份测试会话目录

## 证据采集

### 持续 logcat

```bash
./scripts/android_logcat_session.sh start Docs/QA/artifacts/<session_id>
./scripts/android_logcat_session.sh stop Docs/QA/artifacts/<session_id>
```

### 单点快照

```bash
./scripts/android_capture_state.sh Docs/QA/artifacts/<session_id> home_loaded
./scripts/android_capture_state.sh Docs/QA/artifacts/<session_id> walk_deviation
```

每次快照默认保存：

- PNG 截图
- `uiautomator` XML
- `logcat -d`
- `logcat -b crash`
- 当前前台 Activity 摘要

## 测试矩阵

| ID | 模块 | 核心检查点 | 证据 |
|---|---|---|---|
| REG-01 | 启动链路 | Splash 分流、登录态恢复、底部导航、全局 SOS 弹层 | 冷启动截图、logcat |
| REG-02 | 账号与资料 | 注册、重复注册失败、登录成功/失败、资料编辑、主题、语言 | 注册/登录截图、资料修改截图 |
| REG-03 | 守护者 | 列表、添加、删除、5 名上限、删除最后 1 名失败 | 列表截图、接口事件、Toast |
| REG-04 | 首页/守护中枢 | Dashboard 兜底、Map 页活跃/空态、恢复活跃行程 | Home/Map 截图 |
| REG-05 | 步行正向 | 定位、POI、路线、守护者、开始行程、到达确认、结束行程 | 设置页、追踪页、结束页 |
| REG-06 | 步行异常 | 超时、延长 15 分钟、偏离 200m、SOS、离线缓存与补传、静止 5 分钟、周期 10 分钟 | 提醒弹窗、通知事件、日志 |
| REG-07 | 乘车正向 | 车辆信息录入、路线规划、守护者、开始行程、15 秒上传、到达结束 | 设置页、追踪页 |
| REG-08 | 乘车异常 | 偏离 500m、震动、SOS、静止 5 分钟、周期 5 分钟、离线补传 | 弹窗、事件、日志 |
| REG-09 | AI 陪伴 | 历史、欢迎语、发送消息、网络失败本地兜底、主动关怀 | Chat 截图、接口日志 |
| REG-10 | 模拟来电/警报闪光 | 延迟来电、取消倒计时、接听、挂断、警报闪烁、长按停止 | 来电页、通话页、警报页 |
| REG-11 | SOS 全链路 | 首页/步行/乘车入口、5 秒倒计时、取消/确认、事件落库 | 倒计时截图、事件响应 |
| REG-12 | 权限与鲁棒性 | 拒绝定位、拒绝通知、前后台、重进恢复、锁屏显示、Map GL 降级 | 权限弹窗、恢复截图 |
| REG-13 | 后端联调 | smoke、`test_v2_api`、通知事件不回退 | 命令日志 |

## 详细检查项

### REG-01 启动链路

- 清空应用数据后冷启动，未登录用户应进入登录页
- 完成登录后重新启动应用，已登录用户应直接进入主壳层
- 底部导航 `Home / Map / Chat / Profile` 可切换
- 全局 SOS 浮动按钮可拉起 5 秒倒计时弹层，取消后不触发事件

### REG-02 账号与资料

- 注册成功后自动进入主壳层
- 重复手机号/账号注册失败并展示错误
- 错误密码登录失败
- 资料页昵称和紧急联系人可编辑
- 主题切换后页面重建正常
- 中英文切换后关键导航、资料页文案刷新

### REG-03 守护者

- 空列表时展示空态
- 可新增守护者并刷新列表
- 列表达到 5 名后新增被拦截
- 删除普通守护者成功
- 删除最后 1 名守护者时，后端应拒绝；结果单中标记为“实现约束”

### REG-04 首页与守护中枢

- Dashboard 接口正常时显示活跃守护摘要
- Dashboard 失败时首页仍回退到本地问候文案
- Map 页无活跃行程时显示 idle 状态
- 有活跃行程时可从 Map 页恢复进入对应追踪页
- 结束活跃行程后壳层状态恢复为空闲

### REG-05 / REG-06 步行模式

- 设置页验证：当前位置、POI 搜索、路线规划、守护者选择、开始按钮状态
- 追踪页验证：ETA、剩余距离、轨迹、前台服务通知
- 到达 100m 内弹出到达确认；确认后结束行程
- 超时后弹窗可选择延长 15 分钟或触发 SOS
- 偏离 200m 后弹窗；选择“我没事”后继续，选择“需要帮助”进入 SOS
- 断网时位置缓存到本地，恢复网络后补传成功
- 静止 5 分钟触发主动关怀
- 周期主动关怀按 10 分钟检查

### REG-07 / REG-08 乘车模式

- 车辆信息录入完整，车型和颜色下拉正常
- 规划路线后可进入乘车追踪页
- 追踪页展示车牌、车型、颜色、ETA、剩余距离
- 位置批量上传周期为 15 秒
- 到达 100m 内可确认结束
- 偏离 500m 触发预警和震动
- 选择“司机换路了”后继续
- 选择“不安全”后触发 SOS
- 静止 5 分钟和周期 5 分钟主动关怀可触发
- 断网缓存和恢复补传正常

### REG-09 AI 陪伴

- 无历史时展示欢迎语
- 消息发送成功时展示服务端回复
- 网络失败时回退到 `LocalAIResponder`
- 行程开始、偏离、超时、静止、周期关怀可在 UI 或接口日志中看到主动消息
- 后端返回 `used_fallback=true` 时，结果单中记录为“AI 服务端降级”

### REG-10 模拟来电与警报闪光

- 立即、10 秒、30 秒、1 分钟、5 分钟延迟均可触发
- 倒计时中点击取消可停止来电触发
- 来电页可接听/拒接；接听后进入通话页并可挂断
- 警报页有屏幕闪烁与震动，长按停止按钮可退出
- 如发现文档中的预录音/自定义滑块缺失，记录到需求差异清单

### REG-11 SOS 全链路

- 首页、步行页、乘车页都可进入统一 5 秒倒计时
- 取消后不生成事件
- 确认后生成接口请求、Toast、位置取值、音频占位 URL
- 本地验证以 `/api/notifications/events` 记录为准

### REG-12 权限与鲁棒性

- 拒绝定位时应用不崩溃，并出现合理提示
- 拒绝通知权限时追踪页仍可进入
- 前后台切换、旋转、重进应用后状态保持合理
- 锁屏上方可显示模拟来电和警报闪光页面
- OpenGL 不可用时，Map 页应降级而不闪退

### REG-13 后端联调回归

- Android 回归前后均运行 `scripts/api_smoke.py --profile local`
- 每轮执行 `cd backend && python3 -m unittest tests.test_v2_api`
- 若 smoke 失败，整轮回归记为“环境阻塞”

## 需求符合性重点核对

- 模拟来电：文档提到预录音频播放与自定义滑块，当前实现优先核对是否缺失
- 步行/乘车异常：文档中的“30 秒/60 秒无响应通知”需要和实际实现对照
- 守护者管理：文档只写最多 5 名，当前后端还限制至少保留 1 名

## 会话关闭

回归结束后至少归档以下内容：

- `templates/test_results_template.md`
- `templates/defect_log_template.md`
- `templates/spec_gap_template.md`
- `artifacts/<session_id>/`

