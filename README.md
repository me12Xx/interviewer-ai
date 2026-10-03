# AI 面试官 - 追问式模拟面试系统

基于 DeepSeek API + Function Calling 实现的智能面试系统，能够根据候选人简历生成针对性问题，并通过 ReAct 循环实现追问、换题、结束面试的完整流程。

---

## 项目亮点

1. **手写 ReAct 循环 + Function Calling**（不依赖 LangChain），展现技术深度
2. **追问能力强**：能发现候选人方案的技术漏洞（并发竞态、唯一索引失效等）
3. **答非所问识别**：准确识别候选人堆技术栈、背模板、转移话题，并果断换题
4. **决策可审计**：每次工具调用都记录 `reason` 字段，便于分析模型决策质量
5. **评分报告专业**：结构清晰、分析准确、建议可操作

---

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 21 | 语言 |
| Spring Boot | 4.1.1 | 框架 |
| MyBatis-Plus | 3.5.16 | ORM |
| MySQL | 8.0+ | 数据库 |
| DeepSeek API | deepseek-flash | 大模型 |
| Lombok | - | 代码简化 |

---

## 核心功能

### 1. 开始面试（startSession）

- 根据简历生成第一题
- 使用 ReAct 循环，通过工具调用（`ask_followup` / `change_topic`）生成问题
- 避免 XML 格式问题（早期版本的 BUG）

### 2. 用户回答（answer）

- **ReAct 核心循环**：
  - 模型返回 `tool_calls` → 执行工具 → 返回问题
  - 模型返回纯文本 → 纠正重试（最多 1 次）
  - 纠正后仍不调工具 → 降级：清洗后直接返回
- **三个工具**：
  - `ask_followup`：追问当前话题（轮次不推进）
  - `change_topic`：换新话题（轮次 +1）
  - `finish_interview`：结束面试（触发评分报告生成）

### 3. 评分报告（getReport）

- 两步调用大模型：
  1. 生成详细评价（总体评价、薄弱点、改进建议）
  2. 生成数字评分（0-10 分）
- 报告结构：
  - 总体评价
  - 主要薄弱点（按严重程度排序）
  - 改进建议（技术层面 + 面试表达层面）
  - 最终建议（通过 / 不通过）

---

## 数据库设计

### interview_session（面试会话）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| resume_text | TEXT | 简历内容 |
| target_position | VARCHAR(100) | 目标岗位 |
| status | VARCHAR(20) | 状态（IN_PROGRESS / FINISHED） |
| current_round | INT | 当前轮次 |
| created_at | DATETIME | 创建时间 |
| finished_at | DATETIME | 结束时间 |

### chat_message（对话记录）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| session_id | BIGINT | 会话 ID |
| role | VARCHAR(20) | 角色（user / assistant / tool） |
| content | TEXT | 消息内容 |
| round_num | INT | 所属轮次 |
| created_at | DATETIME | 创建时间 |

### question（面试题目）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| session_id | BIGINT | 会话 ID |
| content | TEXT | 题目内容 |
| category | VARCHAR(50) | 题目分类（可选） |
| round_num | INT | 所属轮次 |
| created_at | DATETIME | 创建时间 |

### assessment（评分报告）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| session_id | BIGINT | 会话 ID |
| question_id | BIGINT | 题目 ID（可选） |
| score | DECIMAL(3,1) | 评分（0-10） |
| feedback | TEXT | 详细评价 |
| weak_points | TEXT | 薄弱点 |
| created_at | DATETIME | 创建时间 |

---

## 快速开始

### 1. 环境要求

- JDK 21
- MySQL 8.0+
- Maven 3.6+

### 2. 数据库初始化

```sql
CREATE DATABASE interviewer_ai CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE interviewer_ai;

-- 创建表（见 BUG_LOG.md 或 schema.sql）
```

### 3. 配置环境变量

在 IDEA 或命令行中设置：

```bash
export DEEPSEEK_API_KEY=sk-your-api-key-here
```

或在 `application.yml` 中直接配置（**不推荐**，避免泄露）：

```yaml
ai:
  api-key: sk-your-api-key-here
```

### 4. 启动应用

```bash
mvn spring-boot:run
```

或在 IDEA 中右键 `InterviewerAiApplication` → Run

### 5. 测试 API

创建会话：

```bash
curl -X POST http://localhost:8080/api/session \
  -d "resumeText=我叫张三，3年Java后端经验，熟悉Spring、MySQL、Redis" \
  -d "targetPosition=Java开发工程师"
```

获取第一题：

```bash
curl -X POST http://localhost:8080/api/session/1/first
```

回答问题：

```bash
curl -X POST http://localhost:8080/api/session/1/chat \
  -d "answer=我用过Redis分布式锁和数据库唯一索引双保险"
```

查看报告：

```bash
curl http://localhost:8080/api/session/1/report
```

---

## API 文档

### 接口列表

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 创建会话 | POST | `/api/session` | 创建面试会话 |
| 获取第一题 | POST | `/api/session/{id}/first` | 获取第一题 |
| 回答问题 | POST | `/api/session/{id}/chat` | 回答 + 获取下一题 |
| 查看报告 | GET | `/api/session/{id}/report` | 查看评分报告 |

### 1. 创建会话

**请求**：

```http
POST /api/session
Content-Type: application/x-www-form-urlencoded

resumeText=我叫张三，3年Java后端经验，熟悉Spring、MySQL、Redis
targetPosition=Java开发工程师
```

**响应**：

```json
{
  "sessionId": 1,
  "firstQuestion": "请调用 /api/session/1/first 接口获取第一题"
}
```

### 2. 获取第一题

**请求**：

```http
POST /api/session/1/first
```

**响应**（纯文本）：

```
你简历里提到做过电商订单系统，请介绍一下订单创建这条链路：从用户点击下单到订单落库，中间经过了哪些步骤？你具体负责了哪一部分？
```

### 3. 回答问题

**请求**：

```http
POST /api/session/1/chat
Content-Type: application/x-www-form-urlencoded

answer=订单创建时先验参数，再查库存，库存够就锁库存，生成订单号落库...
```

**响应**（纯文本）：

```
你说先查库存再锁库存，这中间有并发窗口。两个请求同时查到库存为 10，都认为够用，然后都去扣减，最终库存变成 8 而不是预期的 9。你的代码是怎么处理这个问题的？
```

或（如果面试结束）：

```
面试问答部分已结束，请调用报告接口查看评分。
```

### 4. 查看报告

**请求**：

```http
GET /api/session/1/report
```

**响应**：

```json
{
  "sessionId": 1,
  "targetPosition": "Java开发工程师",
  "score": 7.5,
  "report": "# 面试评估报告\n\n## 一、总体评价\n候选人..."
}
```

---

## 项目结构

```
src/main/java/com/interviewer/ai/
├── client/                    # 大模型客户端
│   ├── AiClient.java         # RestClient 封装
│   └── dto/                  # DTO 类
│       ├── AiMessage.java    # 消息
│       ├── ChatRequest.java  # 请求体
│       ├── ChatResponse.java # 响应体
│       └── Tool.java         # 工具定义
├── config/
│   └── AiConfig.java         # API 配置
├── controller/
│   └── InterviewController.java  # HTTP 接口
├── service/
│   ├── InterviewService.java    # 接口定义
│   └── impl/
│       └── InterviewServiceImpl.java  # 核心业务逻辑（ReAct 循环）
├── mapper/                   # MyBatis Mapper
│   ├── InterviewSessionMapper.java
│   ├── ChatMessageMapper.java
│   ├── QuestionMapper.java
│   └── AssessmentMapper.java
└── InterviewerAiApplication.java  # 启动类
```

---

## 核心实现

### ReAct 循环（InterviewServiceImpl.answer）

```java
for (int loop = 0; loop < MAX_REACT_LOOPS; loop++) {
    ChatResponse resp = aiClient.chatWithToolsRetry(messages, tools, 3);
    List<ToolCall> toolCalls = resp.getChoices().get(0).getMessage().getToolCalls();

    if (toolCalls != null && !toolCalls.isEmpty()) {
        // 正常路径：模型调用了工具
        for (ToolCall tc : toolCalls) {
            ToolExecutionResult result = executeTool(session, tc.getFunction().getName(), tc.getFunction().getArguments());
            if (result.questionForUser != null) {
                saveMessage(sessionId, "assistant", result.questionForUser, currentRound);
                return result.questionForUser;
            }
        }
    } else {
        // 降级路径：模型返回纯文本 → 纠正重试
        if (correctionCount < MAX_TOOL_CORRECTION) {
            messages.add(aiMsg("user", "你必须使用工具，不要直接输出问题文本..."));
            correctionCount++;
            continue;
        } else {
            // 纠正后仍不调工具 → 清洗后直接返回
            return cleanQuestion(content);
        }
    }
}
```

### 工具定义（buildTools）

```java
Tool.builder()
    .type("function")
    .function(Tool.FunctionDef.builder()
        .name("ask_followup")
        .description("当候选人回答含糊、有漏洞、或值得深挖时，调用此工具对当前话题追问")
        .parameters(Map.of(
            "type", "object",
            "properties", Map.of(
                "question", Map.of("type", "string", "description", "追问的具体问题"),
                "reason", Map.of("type", "string", "description", "为什么要追问，候选人哪里答得不好")
            ),
            "required", List.of("question", "reason")
        ))
        .build())
    .build()
```

---

## 常见问题

### 1. 为什么不用 LangChain？

- **控制力**：手写 ReAct 循环，每一步都在掌控之中
- **可调试**：清晰的日志，方便排查问题
- **简洁**：不需要引入庞大的框架，核心代码只有 500 行

### 2. 为什么选 DeepSeek API？

- **性价比高**：0.15 元 / 百万 token（输入），比 GPT-4 便宜 10 倍
- **Function Calling 支持完善**：标准 OpenAI 格式
- **响应速度快**：deepseek-flash 模型平均响应时间 2-3 秒

### 3. 评分标准是什么？

模型根据以下维度综合评分：
- 答题完整性（是否回答了问题的所有方面）
- 技术深度（是否有实际项目细节）
- 逻辑严密性（方案是否有明显漏洞）
- 表达能力（是否答非所问、堆砌技术栈）

### 4. 如何调整面试轮次？

修改 `InterviewServiceImpl` 的常量：

```java
private static final int MAX_ROUNDS = 5;  // 改为你想要的轮次
```

### 5. 如何换其他大模型？

修改 `application.yml`：

```yaml
ai:
  api-url: https://api.openai.com/v1/chat/completions  # 或其他兼容 OpenAI 格式的 API
  api-key: ${YOUR_API_KEY}
  model: gpt-4o  # 或其他模型
```

---

## BUG 记录

详见 [BUG_LOG.md](./BUG_LOG.md)

---

## License

MIT

---

## 作者

- GitHub: [你的 GitHub]
- 邮箱: [你的邮箱]
