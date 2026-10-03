# V2 测试验证清单

**目标读者**：测试工程师 / QA  
**预计工作量**：3-4 小时  
**测试环境**：开发环境 / 预发布环境

---

## 📋 测试前准备

### 1. 环境检查
- [ ] Java 21 已安装并配置 JAVA_HOME
- [ ] MySQL 8.0 已启动
- [ ] Redis 已启动（如需要）
- [ ] 已执行数据库迁移脚本 `migration_v2.sql`
- [ ] 应用已启动并监听 8080 端口

### 2. 数据准备
```sql
-- 清理测试数据
DELETE FROM interview_session WHERE user_id = 999;
DELETE FROM chat_message WHERE session_id IN (SELECT id FROM interview_session WHERE user_id = 999);
DELETE FROM question WHERE session_id IN (SELECT id FROM interview_session WHERE user_id = 999);

-- 验证表结构
DESC interview_session;  -- 确认有 interview_stage, final_score, is_archived 等字段
DESC chat_message;        -- 确认有 proficiency_level, evaluation_reason 字段
DESC question;            -- 确认有 tool_used 字段
```

---

## 🧪 核心功能测试

### 测试 1：正常面试流程

#### 1.1 创建会话
```bash
curl -X POST http://localhost:8080/api/session \
  -d "resumeText=3年Java后端，熟悉Spring、MySQL、Redis" \
  -d "targetPosition=Java后端工程师"
```

**期望输出**：
```json
{
  "sessionId": 1,
  "firstQuestion": "请调用 /api/session/1/first 接口获取第一题"
}
```

**验证点**：
- [ ] 返回的 `sessionId` 为正整数
- [ ] 数据库 `interview_session` 表新增一条记录
- [ ] `interview_stage` = 1
- [ ] `status` = "IN_PROGRESS"
- [ ] `current_round` = 0

---

#### 1.2 获取第一题
```bash
curl -X POST http://localhost:8080/api/session/1/first
```

**期望输出**：
```
"请介绍一下 HashMap 的底层实现"
```

**验证点**：
- [ ] 返回的是纯文本字符串（不是 JSON）
- [ ] 数据库 `chat_message` 表新增一条 `role='assistant'` 的记录
- [ ] 数据库 `question` 表新增一条记录，`round_num=1`
- [ ] `interview_session.current_round` 更新为 1

---

#### 1.3 回答问题（获取 Map 返回）
```bash
curl -X POST http://localhost:8080/api/session/1/chat \
  -d "answer=HashMap 底层是数组+链表，1.8 引入红黑树优化链表过长的情况"
```

**期望输出**（JSON）：
```json
{
  "question": "那具体是链表长度达到多少时会转换？",
  "proficiencyLevel": "FAMILIAR_BUT_SHALLOW",
  "finished": false
}
```

**验证点**：
- [ ] 返回类型是 JSON（不是纯文本）
- [ ] `finished` = `false`
- [ ] `proficiencyLevel` 的值为以下之一：
  - `VERY_FAMILIAR`
  - `FAMILIAR_BUT_SHALLOW`
  - `NOT_FAMILIAR`
  - `null`（降级场景）
- [ ] 数据库 `chat_message` 表新增 2 条记录：
  - 1 条 `role='user'`，内容为用户回答
  - 1 条 `role='assistant'`，内容为下一题
- [ ] 用户消息的 `proficiency_level` 字段已填充

---

### 测试 2：连续 2 次 NOT_FAMILIAR 自动淘汰

#### 2.1 第一次答不出
```bash
curl -X POST http://localhost:8080/api/session/1/chat \
  -d "answer=不太了解"
```

**期望输出**：
```json
{
  "question": "那我们换个话题，请介绍线程池的核心参数",
  "proficiencyLevel": "NOT_FAMILIAR",
  "finished": false
}
```

**验证点**：
- [ ] `proficiencyLevel` = `"NOT_FAMILIAR"`
- [ ] `finished` = `false`（第一次，未触发淘汰）
- [ ] 数据库最近一条用户消息的 `proficiency_level` = "NOT_FAMILIAR"

---

#### 2.2 第二次答不出（触发淘汰）
```bash
curl -X POST http://localhost:8080/api/session/1/chat \
  -d "answer=不知道"
```

**期望输出**：
```json
{
  "finished": true,
  "finalScore": 3.5,
  "status": "FAILED",
  "message": "多次答不出核心问题，面试终止"
}
```

**验证点**：
- [ ] `finished` = `true`
- [ ] `status` = `"FAILED"`（不是 `"FINISHED"`）
- [ ] `finalScore` 为 0-10 之间的数字，保留 1 位小数
- [ ] 数据库 `interview_session` 表：
  - `status` = "FAILED"
  - `final_score` 已填充
  - `finished_at` 已填充
- [ ] 查询最近 2 条用户消息，`proficiency_level` 均为 "NOT_FAMILIAR"

---

### 测试 3：15 轮硬兜底

#### 3.1 模拟 15 轮对话
```bash
# 创建新会话
SESSION_ID=$(curl -X POST http://localhost:8080/api/session \
  -d "resumeText=测试用户" \
  -d "targetPosition=测试岗位" \
  | jq -r '.sessionId')

# 获取第一题
curl -X POST http://localhost:8080/api/session/$SESSION_ID/first

# 循环回答 14 次（使第 15 轮成为第 16 次 API 调用）
for i in {1..14}; do
  echo "第 $i 轮回答"
  curl -X POST http://localhost:8080/api/session/$SESSION_ID/chat \
    -d "answer=这是第 $i 次回答"
  sleep 1
done
```

#### 3.2 第 15 轮（触发兜底）
```bash
curl -X POST http://localhost:8080/api/session/$SESSION_ID/chat \
  -d "answer=第15次回答"
```

**期望输出**：
```json
{
  "finished": true,
  "finalScore": 6.8,
  "status": "PASSED",
  "message": "已达最大轮次，系统自动结束"
}
```

**验证点**：
- [ ] `finished` = `true`
- [ ] `message` 包含 "已达最大轮次"
- [ ] 数据库 `question` 表该会话的记录数 = 15
- [ ] 后台日志包含 `[Force-Finish] Session X 达到 15 轮，强制结束`

---

### 测试 4：正常通过面试

#### 4.1 模拟高质量回答
```bash
# 创建新会话
SESSION_ID=$(curl -X POST http://localhost:8080/api/session \
  -d "resumeText=5年Java架构师" \
  -d "targetPosition=高级Java工程师" \
  | jq -r '.sessionId')

curl -X POST http://localhost:8080/api/session/$SESSION_ID/first

# 回答 5-8 次，每次都给高质量回答
curl -X POST http://localhost:8080/api/session/$SESSION_ID/chat \
  -d "answer=HashMap底层是数组+链表+红黑树。1.7用头插法有死循环风险，1.8改尾插+引入红黑树，当链表长度>=8且数组长度>=64时转红黑树，保证最坏O(logn)查询..."

# 继续回答几轮...
```

#### 4.2 主动结束面试
AI 模型认为考察足够后，会调用 `finish_interview` 工具：

**期望输出**：
```json
{
  "finished": true,
  "finalScore": 8.2,
  "status": "PASSED",
  "message": "面试已结束。最终得分：8.2，状态：PASSED"
}
```

**验证点**：
- [ ] `status` = `"PASSED"`（因为 8.2 >= 6.0）
- [ ] `finalScore` >= 6.0
- [ ] 数据库 `interview_session.status` = "PASSED"

---

### 测试 5：查看评分报告

```bash
curl http://localhost:8080/api/session/1/report
```

**期望输出**：
```json
{
  "sessionId": 1,
  "stage": 1,
  "targetPosition": "Java后端工程师",
  "finalScore": 7.2,
  "status": "PASSED",
  "proficiencyDistribution": {
    "NOT_FAMILIAR": 1,
    "FAMILIAR_BUT_SHALLOW": 3,
    "VERY_FAMILIAR": 2
  },
  "transcript": "面试官: 请介绍一下 HashMap 的底层实现\n候选人: ..."
}
```

**验证点**：
- [ ] `finalScore` 与 `interview_session.final_score` 一致
- [ ] `status` 为 "PASSED" 或 "FAILED"（不是 "FINISHED"）
- [ ] `proficiencyDistribution` 统计正确
- [ ] `transcript` 包含完整对话历史

---

## 🔒 边界情况测试

### 边界 1：空值保护

#### 1.1 AI 未返回 proficiency_level
**模拟方法**：修改 `normalizeProficiency` 方法暂时返回 `null`

**期望行为**：
- 系统使用默认值 `"FAMILIAR_BUT_SHALLOW"`
- 不应抛出 NullPointerException
- 后台日志包含 `[Proficiency] 模型未填写 proficiency_level，使用默认值`

---

### 边界 2：超长简历

```bash
LONG_RESUME=$(python3 -c "print('测试' * 1000)")  # 生成 2000 字简历

curl -X POST http://localhost:8080/api/session \
  -d "resumeText=$LONG_RESUME" \
  -d "targetPosition=测试"
```

**验证点**：
- [ ] 简历被截断到 2000 字
- [ ] 会话创建成功
- [ ] 后台日志无报错

---

### 边界 3：非法 proficiency_level 值

**模拟方法**：AI 模型返回 `"UNKNOWN"` 或其他非法值

**期望行为**：
- `normalizeProficiency` 方法返回默认值 `"FAMILIAR_BUT_SHALLOW"`
- 后台日志包含 `[Proficiency] 非法值: UNKNOWN，使用默认值`

---

### 边界 4：会话不存在

```bash
curl -X POST http://localhost:8080/api/session/999999/chat \
  -d "answer=测试"
```

**期望输出**：
```json
{
  "error": "会话不存在: 999999"
}
```

**验证点**：
- [ ] HTTP 状态码 = 400 或 404
- [ ] 错误消息清晰

---

### 边界 5：面试已结束后继续回答

```bash
# 1. 先完成一场面试（status 变为 PASSED 或 FAILED）
# ...

# 2. 再次调用 chat 接口
curl -X POST http://localhost:8080/api/session/1/chat \
  -d "answer=测试"
```

**期望输出**：
```json
{
  "finished": true,
  "finalScore": 7.2,
  "status": "PASSED",
  "message": "本次面试已结束，请查看评分报告。"
}
```

**验证点**：
- [ ] 不会重新开始面试
- [ ] 返回已结束的状态和分数

---

## 🐛 异常场景测试

### 异常 1：数据库连接断开

**模拟方法**：停止 MySQL 服务

**期望行为**：
- API 返回 500 错误
- 错误消息包含 "数据库连接失败"
- 后台日志记录详细堆栈

---

### 异常 2：AI API 超时

**模拟方法**：暂时关闭 AI 服务或设置极短超时

**期望行为**：
- API 返回 503 错误
- 错误消息包含 "AI 服务暂时不可用"
- 不应导致数据不一致

---

### 异常 3：并发创建会话

```bash
# 同时发起 10 个创建会话请求
for i in {1..10}; do
  curl -X POST http://localhost:8080/api/session \
    -d "resumeText=并发测试$i" \
    -d "targetPosition=测试" &
done
wait
```

**验证点**：
- [ ] 所有请求均成功返回
- [ ] 10 个 `sessionId` 互不相同
- [ ] 数据库无脏数据

---

## 📊 性能测试

### 性能 1：单次 API 响应时间

```bash
time curl -X POST http://localhost:8080/api/session/1/chat \
  -d "answer=测试回答"
```

**期望**：
- [ ] 平均响应时间 < 3 秒（取决于 AI API）
- [ ] P95 响应时间 < 5 秒

---

### 性能 2：15 轮完整面试耗时

```bash
start_time=$(date +%s)

# 执行完整 15 轮面试
# ...

end_time=$(date +%s)
echo "总耗时: $((end_time - start_time)) 秒"
```

**期望**：
- [ ] 15 轮完整面试 < 60 秒（平均每轮 4 秒）

---

## ✅ 测试通过标准

### 必须通过的测试（P0）
- [ ] 测试 1：正常面试流程
- [ ] 测试 2：连续 2 次 NOT_FAMILIAR 自动淘汰
- [ ] 测试 3：15 轮硬兜底
- [ ] 测试 5：查看评分报告
- [ ] 边界 4：会话不存在
- [ ] 边界 5：面试已结束后继续回答

### 推荐通过的测试（P1）
- [ ] 测试 4：正常通过面试
- [ ] 边界 1：空值保护
- [ ] 边界 2：超长简历
- [ ] 异常 1：数据库连接断开

### 可选测试（P2）
- [ ] 边界 3：非法 proficiency_level 值
- [ ] 异常 2：AI API 超时
- [ ] 异常 3：并发创建会话
- [ ] 性能 1、2

---

## 📝 测试报告模板

```markdown
# V2 测试报告

**测试日期**：2026-09-21  
**测试人员**：XXX  
**测试环境**：开发环境

## 测试结果汇总

| 测试项 | 状态 | 备注 |
|--------|------|------|
| 正常面试流程 | ✅ 通过 | - |
| 连续淘汰机制 | ✅ 通过 | - |
| 15 轮兜底 | ✅ 通过 | - |
| 评分报告 | ✅ 通过 | - |
| 边界情况 | ⚠️ 部分通过 | 超长简历未截断（已修复） |
| 异常场景 | ✅ 通过 | - |

## 发现的问题

### 问题 1：[标题]
- **严重程度**：高 / 中 / 低
- **复现步骤**：
  1. ...
  2. ...
- **期望结果**：...
- **实际结果**：...
- **截图**：...

## 测试结论

✅ V2 功能基本符合预期，建议发布  
⚠️ V2 存在 X 个 P1 问题，修复后可发布  
❌ V2 存在严重问题，不建议发布

**测试人签字**：________  
**日期**：________
```

---

## 🔗 相关文档

- 主文档：`V2_UPDATE_SUMMARY.md`
- 前端迁移指南：`FRONTEND_MIGRATION_GUIDE.md`
- 数据库迁移脚本：`migration_v2.sql`
