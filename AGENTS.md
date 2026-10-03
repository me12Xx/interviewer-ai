# 环境说明

- 操作系统：Windows 11
- Shell：PowerShell
- 不要使用 Unix 命令（find / rg / grep / wc）
- 使用 OpenCode 内置的 Glob 和 Grep 工具
- 路径分隔符用 /（跨平台兼容）

# 项目信息

- 项目：AI 面试官（Spring Boot + RAG + ReAct）
- 项目根：E:\IDEA1\Interviewer_ai
- 核心文件：InterviewServiceImpl.java、AiClient.java、RagService.java

# 约束

- 不要读 PROJECT_MAP.md（348KB 太大）
- 一次最多读 5 个文件
- 修改代码前先出方案，等确认
重要约定：以源码为准，本文件只做导航和约定说明，不代替阅读代码

---

1. 项目一句话

基于 Spring Boot 的 AI 模拟面试官系统。候选人粘贴简历 → AI 出题 → 候选人回答 → AI 评估熟悉度 → 动态追问 / 换题 / 结束 → 三阶段闯关（一面 6.0 / 二面 6.5 / 三面 7.0）。

---

2. 技术栈

层 技术
语言 Java 21
框架 Spring Boot
ORM MyBatis-Plus（@TableId(type = IdType.ASSIGN_ID) 雪花算法）
数据库 MySQL 8.0
LLM DeepSeek Chat（Function Calling + SSE 流式）
Embedding 智谱 AI embedding-3（2048 维）
重排序 TypeSafe Jev（可选，可降级）
同步 HTTP RestClient
响应式 HTTP WebClient（仅流式与 Embedding）
JSON Jackson（全局 SnakeCaseStrategy）
简化 Lombok
鉴权 自写 HandlerInterceptor + ThreadLocal

---

3. 目录结构与文件职责

只列关键文件。不要一次读全部，按需读。

```
src/main/java/com/interviewer/ai/
├── InterviewerAiApplication.java        # 启动类，无逻辑
│
├── controller/
│   ├── InterviewController.java         # 面试主流程接口（7 个）
│   ├── RegisterController.java          # 邀请码注册
│   └── AdminController.java             # 管理员：生成邀请码 / 禁用户
│
├── service/
│   ├── InterviewService.java            # 接口
│   ├── TypeSafeClient.java              # Jev 重排序客户端
│   └── impl/
│       └── InterviewServiceImpl.java    # ⚠️ 700+ 行上帝类，核心中的核心
│
├── client/
│   ├── AiClient.java                    # DeepSeek API 封装（同步 + 流式 + 工具）
│   └── dto/
│       ├── AiMessage.java               # 消息（支持 role:tool 回填）
│       ├── ChatRequest.java             # 请求体
│       ├── ChatResponse.java            # 响应体（嵌套 ToolCall / FunctionCall）
│       ├── StreamChunk.java             # SSE 增量块
│       └── Tool.java                    # 工具定义（JSON Schema）
│
├── rag/
│   ├── DocumentSplitter.java            # 段落 + 句子切分（含 System.out 待清理）
│   ├── DocumentChunk.java               # 文档块实体（text + embedding + score）
│   ├── DocumentWithSource.java
│   ├── Document.java                    # ⚠️ 疑似遗留，可能未使用
│   ├── EmbeddingService.java            # 智谱 Embedding（含 embedBatch）
│   ├── KnowledgeBaseStorage.java        # 本地 JSON 存储（data/knowledge_base.json）
│   ├── KnowledgeBaseInitializer.java    # 启动构建知识库
│   └── RagService.java                  # 两阶段检索：余弦召回 Top30 → Jev 重排 → TopK
│
├── config/
│   ├── AiConfig.java                    # RestClient Bean + ai.* 配置绑定
│   ├── WebClientConfig.java             # WebClient.Builder + ObjectMapper Bean
│   ├── WebConfig.java                   # 注册 AuthInterceptor
│   ├── InterviewConstants.java          # 所有业务常量（分数、上限、阈值）
│   └── KnowledgeBaseLoader.java         # V2：加载 stage1/2/3.md 到内存
│
├── auth/
│   ├── AuthInterceptor.java             # Token 鉴权 + UserContext 注入
│   └── UserContext.java                 # ThreadLocal 存 userId
│
├── mapper/                              # 全部是 BaseMapper 子接口，无自定义 SQL（除 UserMapper/InviteCodeMapper 有 @Select）
│
└── (根包)实体类：
    ├── InterviewSession.java            # 会话（含 V2 字段：stage / attemptNumber / isLatest / isArchived / previousSessionId / finalScore）
    ├── ChatMessage.java                 # 对话消息（含 V2：proficiencyLevel / evaluationReason）
    ├── Question.java                    # 题目（含 V2：toolUsed）
    ├── Assessment.java                  # 逐题评估
    ├── User.java
    └── InviteCode.java
```

---

4. 核心业务流程（三条链路）

链路 A：创建会话 → 拿第一题

```
POST /api/session
  → AuthInterceptor 解析 Token → UserContext.setUserId
  → InterviewController.start
  → InterviewServiceImpl.startSession(userId, resume, position, stage=1)
    ⚠️ 事务里调用了 AI API，见"坑点"
    → sessionMapper.insert
    → aiClient.chatWithToolsRetry(messages, tools, 3)
    → executeTool（生成第一题，通常是 change_topic 或 ask_followup）
    → saveMessage

POST /api/session/{id}/first
  → getFirstQuestion
    → 重新构建 system prompt + history
    → 再次调 AI 出题（这里和 startSession 有重复调用嫌疑，注意）
    → saveMessage + saveQuestion
```

链路 B：回答 → ReAct 循环（核心）

```
POST /api/session/{id}/chat  （同步）
POST /api/session/{id}/chat-stream  （SSE）

answer() 内部：
  1. 校验会话存在 + checkOwnership
  2. 若 status ∈ {PASSED, FAILED} → 直接返回结束
  3. 兜底检查 1：questionMapper.selectCount >= MAX_ROUNDS_SOFT(15) → 强制结束
  4. 兜底检查 2：countRecentNotFamiliar(5) >= 2 → 强制结束
  5. saveMessage(user)
  6. 构建 messages = [system] + loadHistory
  7. 构建 tools = buildTools()（3 个：ask_followup / change_topic / finish_interview）
  8. ReAct 循环（最多 MAX_REACT_LOOPS = 10 次）：
       a. aiClient.chatWithToolsRetry(messages, tools, 3)
       b. 若 toolCalls 非空：
            - executeTool(session, toolName, argsJson)
            - messages.add(AiMessage.fromAssistantResponse(assistantMsg))
            - messages.add(AiMessage.toolResult(tcId, toolName, resultMsg))
            - 若 result.finished → 返回结束
            - 若 result.nextQuestion != null → saveMessage + 返回
            - 否则 continue（极端情况）
       c. 若 toolCalls 为空但 content 非空：
            - 若 correctionCount < MAX_TOOL_CORRECTION(2) → 追加纠正 user 消息 → continue
            - 否则 → cleanQuestion(content) → 降级返回
       d. 若 content 也为空 → 记日志，继续循环
  9. 循环耗尽 → finishInterviewForce("系统繁忙")

executeTool() 关键分支：
  - ask_followup：写入 proficiency，saveQuestion，round 不变
  - change_topic：写入 proficiency，round +1，saveQuestion
       ⚠️ 若 proficiency=NOT_FAMILIAR 且最近 2 条也是 NOT_FAMILIAR → 直接触发 finish_interview
  - finish_interview：calculateFinalScore → 写 session.finalScore + status
```

链路 C：报告 / 晋级 / 重试

```
GET  /api/session/{id}/report        → getReport
   - 读 session.finalScore（权威分数）
   - 统计 proficiencyDistribution
   - 拼接 transcript（供前端展示）
   - 返回 { sessionId, stage, finalScore, status, proficiencyDistribution, transcript }

GET  /api/session/{id}/assessments   → getAssessments（逐题分析）

POST /api/session/{id}/next-stage    → prepareNextStage
   - 校验 status == PASSED
   - 校验 stage < 3
   - 复用 startNextStage（内部会校验前置阶段最高分 >= passLine）
   - 新建 session，previousSessionId = 旧 id

POST /api/session/{id}/retry         → prepareRetry → retryInterview
   - 旧 session.isLatest = false
   - 新建 session，attemptNumber +1，previousSessionId = 旧 id
```

---

5. 数据模型要点

interview_session

字段 说明
status IN_PROGRESS / PASSED / FAILED
interviewStage 1 / 2 / 3
attemptNumber 同阶段第几次尝试（重试时 +1）
isLatest 是否该阶段当前一次
isArchived false = 有效成绩；true = 历史记录（reset 时置 true）
previousSessionId 上一次尝试的会话 id
finalScore DECIMAL(3,1)，结束时写入

chat_message

字段 说明
role user / assistant / tool（当前只写了 user/assistant）
proficiencyLevel NOT_FAMILIAR / FAMILIAR_BUT_SHALLOW / VERY_FAMILIAR
evaluationReason 模型给的评价理由

question

字段 说明
toolUsed ask_followup / change_topic / finish_interview / null

关系

· interview_session 1→N chat_message
· interview_session 1→N question
· interview_session 1→1 assessment
· interview_session 1→N interview_session（通过 previousSessionId 自关联）

---

6. 关键约定（读代码前必看）

1. proficiency_level 是枚举约束：buildTools() 里对 3 个工具分别限定了枚举。换题时只能填 NOT_FAMILIAR 或 VERY_FAMILIAR；追问时只能填 FAMILIAR_BUT_SHALLOW。不要修改这个约束，除非同步改 InterviewConstants.PROFICIENCY_SCORES。
2. arguments 是 JSON 字符串，必须用 ObjectMapper 二次解析成 Map。这是 OpenAI Function Calling 协议约定，不要试图改成 Object。
3. AiClient 的重试是"响应无效也重试"，不是"仅异常重试"。响应有效条件：
   · chatWithRetry：content 非空
   · chatWithToolsRetry：toolCalls 或 content 任一非空
4. 流式响应里 arguments 是增量拼接的，必须用 finish_reason=tool_calls 判断结束，然后才能解析 JSON。
5. Jev 重排序失败必须降级到余弦排序，不能抛异常。
6. 知识库本地 JSON 路径写死在 KnowledgeBaseStorage.STORAGE_FILE = "data/knowledge_base.json"。
7. V2 知识库是 classpath:knowledge/stage{1,2,3}.md，由 KnowledgeBaseLoader 加载，不是 KnowledgeBaseInitializer 那套老的切分逻辑。
8. InterviewServiceImpl.startSession(String, String) 和 startNextStage(Long, Integer) 已 @Deprecated，新代码走 startSession(Long, String, String, Integer) 和 prepareNextStage(Long)。

---

7. 坑点清单（改代码前必读）

# 位置 问题 建议
1 application.yml 明文 API Key 和 DB 密码 P0：改环境变量，吊销旧 Key
2 InterviewServiceImpl.startSession 事务内调用 AI API（1-2 秒） P0：把 AI 调用移出事务
3 InterviewServiceImpl 700+ 行上帝类，6 个依赖 拆 ToolExecutor / PromptBuilder / StageService
4 AiClient 构造函数 new ObjectMapper() 而非注入 注入 Spring 的（WebClientConfig 已有）
5 AiClient.doPost 反序列化失败返回 null 抛自定义异常，调用方统一处理
6 全项目 无 @RestControllerAdvice 加全局异常处理
7 DocumentSplitter 7 处 System.out.println 改 log.debug
8 TypeSafeClient.systemOne state / questions 格式未经真实 API 验证 对着 TypeSafe 官方文档核实
9 TypeSafeClient.ScoreQuestion @Data + final 字段 + 构造器，可能冲突 改 @Getter
10 RagService.cosineSimilarity 与 VectorSimilarity（V1 文档里的类）重复 如果 VectorSimilarity 已删除，忽略；否则合并
11 InterviewServiceImpl 里 executeTool change_topic 里的连续 2 次淘汰逻辑写在 case 里 抽成独立方法
12 getAssessments 循环内 questionMapper.selectById → N+1 批量查询
13 Document.java 疑似遗留，无引用 确认后删除
14 answerStream AtomicReference 在单线程 Flux 中略多余 可保留，不算 bug
15 InterviewController.start 返回 session.getId().toString()，前端 sessionId 是 String 注意前端类型

---

8. 关键常量（InterviewConstants）

```java
RESUME_MAX_LENGTH = 2000
MAX_ROUNDS_SOFT = 15
MAX_NOT_FAMILIAR_STREAK = 2
MAX_FOLLOWUP_PER_TOPIC = 3
PASS_SCORES = {1: 6.0, 2: 6.5, 3: 7.0}
PROFICIENCY_SCORES = {VERY_FAMILIAR: 10, FAMILIAR_BUT_SHALLOW: 6, NOT_FAMILIAR: 2}
```

InterviewServiceImpl 内部还有：

```java
MAX_REACT_LOOPS = 10
MAX_TOOL_CORRECTION = 2
MAX_HISTORY_PER_STAGE = 10   // ⚠️ 定义但未使用，需要确认
```

---

9. 外部 API 约定

DeepSeek Chat（AiConfig.apiUrl）

· 请求体：{model, messages, tools?, max_tokens?, temperature?, stream?}
· 响应：{choices: [{message: {role, content, tool_calls?}, finish_reason}]}
· 流式：SSE，每行 data: {json}，结束 data: [DONE]

智谱 Embedding（ai.embedding-api-url）

· 请求体：{model, input}，input 可以是 String 或 List<String>
· 响应：{data: [{index, embedding: [...]}], usage}
· 批量时用 index 还原顺序，不要假设顺序

TypeSafe Jev（TypeSafeClient）

· URL：https://api.typesafe.ai/v1/systemone
· 请求体：{model, state: Map, questions: Map<key, {type, instructions, criteria}>}
· 响应：{answers: Map<key, {type, score, confidence, probabilities}>}
· enabled=false 或 api-key 为空时，isAvailable() 返回 false，跳过重排

---

10. 改动风险提示

改动 风险 影响面
修改 buildTools() 的 enum 高 模型可能不填或填错，normalizeProficiency 会兜底，但评分会偏
修改 PASS_SCORES 中 影响晋级判定和历史成绩一致性
修改 MAX_ROUNDS_SOFT 低 只影响兜底结束
修改 AiClient 重试逻辑 高 可能引入死循环或额度浪费
修改 executeTool 的 change_topic 高 淘汰逻辑、轮次推进、评分都在这里
修改 RagService.retrieve 中 影响检索质量，但不影响面试主流程
修改 AuthInterceptor 白名单 高 可能导致注册接口被拦或管理接口裸奔
修改 UserContext 高 ThreadLocal 泄漏风险
引入缓存 中 需要处理 session 一致性
拆分 InterviewServiceImpl 高 涉及大量方法搬迁，建议小步进行

---

11. 不要做的事

1. ❌ 不要一次性读整个项目。按链路读：InterviewController → InterviewServiceImpl → AiClient → executeTool → buildTools。
2. ❌ 不要修改 Prompt 里的安全规则和评分标定，除非有明确理由。
3. ❌ 不要引入 Redis / 向量数据库 / 消息队列，当前规模用不上。
4. ❌ 不要拆微服务。
5. ❌ 不要改 arguments 的 JSON 字符串约定，OpenAI 协议规定。
6. ❌ 不要在事务里调 AI API（startSession 里现在有，是待修项）。
7. ❌ 不要把 V1 的 KnowledgeBaseInitializer 和 V2 的 KnowledgeBaseLoader 搞混，两者职责不同。
8. ❌ 不要相信 docs/*.docx，那些是历史文档，部分已过时，以源码为准。

---

12. 重构优先级（如果需要做）

P0（安全 / 正确性）

1. 明文 Key → 环境变量，吊销旧 Key
2. startSession 里 AI 调用移出事务

P1（可维护性）

3. 加 @RestControllerAdvice
4. AiClient 注入 ObjectMapper，异常规范化
5. DocumentSplitter 去 System.out

P2（结构）

6. 拆 InterviewServiceImpl 出 ToolExecutor / PromptBuilder
7. 加核心单测：cosineSimilarity、DocumentSplitter.split、calculateFinalScore、executeTool

P3（不建议现在做）

8. Redis / 向量库 / 限流 / 微服务

---

13. 快速验证命令（给 Claude Code 参考）

```bash
# 编译
mvn -q clean compile

# 运行（需先设置环境变量）
DEEPSEEK_API_KEY=xxx \
TYPESAFE_API_KEY=xxx \
mvn spring-boot:run

# 手动测试链路
# 见 test.http
```

---

14. 结语

本文件是导航，不是替代品。
任何与源码冲突的地方，以源码为准。
改动前先读上下文，尤其是 InterviewServiceImpl 和 AiClient。