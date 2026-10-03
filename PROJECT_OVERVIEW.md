# AI 面试官项目总览

> 本文基于当前源码、`application.yml`、`pom.xml` 和指定关键字检索整理。
> 未读取 `PROJECT_MAP.md`，未修改任何业务源码。

## 1. 项目概况

- 项目类型：Spring Boot AI 模拟面试系统。
- 技术栈：Java 21、Spring Boot 4.1.1、Spring MVC、WebFlux、MyBatis-Plus、MySQL、Jackson、Lombok。
- AI 能力：DeepSeek Chat 同步/流式对话与 Function Calling；智谱 Embedding；可选 TypeSafe Jev RAG 重排序。
- 代码规模：`src/main/java` 共 40 个 Java 文件、约 3504 行；`src/main/resources` 共 15 个文件、约 2751 行；`pom.xml` 88 行；合计约 6343 行。
- 启动类：`src/main/java/com/interviewer/ai/InterviewerAiApplication.java`。

## 2. 项目地图

### 2.1 包职责

| 包 | 职责 |
|---|---|
| `com.interviewer.ai` | 核心实体模型与 Spring Boot 启动类。 |
| `com.interviewer.ai.controller` | HTTP 接口层，负责面试、注册和管理员操作。 |
| `com.interviewer.ai.service` | 面试服务接口、TypeSafe Jev 客户端。 |
| `com.interviewer.ai.service.impl` | 面试会话生命周期、ReAct 工具执行、评分和阶段流转。 |
| `com.interviewer.ai.client` | DeepSeek Chat 同步、重试和 SSE 流式客户端。 |
| `com.interviewer.ai.client.dto` | Chat、tool call、流式 chunk 和工具 schema 数据结构。 |
| `com.interviewer.ai.rag` | 旧版文档切分、Embedding、知识库 JSON 存储、向量检索和重排序。 |
| `com.interviewer.ai.config` | AI 客户端、Web 客户端、鉴权拦截器、常量和 V2 阶段大纲加载配置。 |
| `com.interviewer.ai.auth` | 请求 Token 鉴权和基于 ThreadLocal 的当前用户上下文。 |
| `com.interviewer.ai.mapper` | MyBatis-Plus 数据访问接口。 |

### 2.2 核心类职责

| 类 | 职责 |
|---|---|
| `InterviewController` | 提供创建会话、首题、同步回答、流式回答、报告、晋级、重试、逐题评估 8 个接口。 |
| `RegisterController` | 校验昵称和邀请码，创建用户并返回 access token。 |
| `AdminController` | 校验管理员 token，生成邀请码，并保留用户创建/禁用接口。 |
| `InterviewService` | 定义面试会话、回答、报告、阶段操作和评估查询 API。 |
| `InterviewServiceImpl` | 面试核心上帝类，编排数据库、Prompt、AI tool call、评分、阶段和重试。 |
| `AiClient` | 调用 DeepSeek Chat API，负责同步请求、重试、Function Calling 和 SSE 解析。 |
| `TypeSafeClient` | 调用 TypeSafe System One 对 RAG 候选进行语义评分，失败返回空结果。 |
| `KnowledgeBaseLoader` | 启动时将 `knowledge/stage1.md`、`stage2.md`、`stage3.md` 加载到内存。 |
| `RagService` | 对旧版向量知识库执行余弦召回、Jev 重排和上下文拼接。 |
| `KnowledgeBaseInitializer` | 首次启动时扫描 Markdown、切分、批量向量化并保存 JSON。 |
| `DocumentSplitter` | 按段落和句子切分 Markdown 文档。 |
| `EmbeddingService` | 调用智谱 Embedding API，并按响应 index 恢复批量顺序。 |
| `KnowledgeBaseStorage` | 读写工作目录下的 `data/knowledge_base.json`。 |
| `AuthInterceptor` | 跳过注册/管理员路径，对其余 `/api/**` 根据 access token 设置用户上下文。 |
| `UserContext` | 维护当前请求线程的用户 ID，并在请求完成后清理。 |
| `AiConfig` | 绑定 `ai.*` 配置并创建带超时与 Authorization 的 RestClient。 |
| `WebClientConfig` | 创建 WebClient.Builder、Jackson ObjectMapper 和 RestTemplate。 |
| `InterviewConstants` | 定义简历长度、轮次、熟悉度分值和各阶段通过线。 |

### 2.3 实体与持久化

| 实体 | 表/用途 |
|---|---|
| `InterviewSession` | `interview_session`，保存用户、简历、岗位、阶段、尝试、状态、最终分数和前序会话。 |
| `ChatMessage` | `chat_message`，保存 user/assistant 对话、轮次、熟悉度和评价理由。 |
| `Question` | `question`，保存问题文本、轮次和产生问题的工具。 |
| `Assessment` | `assessment`，设计用于保存逐题分数、反馈和薄弱点。当前主链路只查询。 |
| `User` | `user`，保存昵称、access token、启用状态和创建时间。 |
| `InviteCode` | `invite_code`，保存邀请码使用状态和使用者。 |

所有 mapper 均继承 MyBatis-Plus `BaseMapper`；`UserMapper` 额外提供带 `is_active = 1` 条件的 Token 查询，`InviteCodeMapper` 额外提供查询未使用邀请码的方法。

## 3. 调用关系

```text
HTTP 请求
  -> WebConfig
  -> AuthInterceptor
  -> UserContext
  -> Controller
  -> InterviewServiceImpl
       -> InterviewSessionMapper / ChatMessageMapper / QuestionMapper / AssessmentMapper
       -> KnowledgeBaseLoader
       -> AiClient
            -> RestClient: 同步 Chat / Function Calling
            -> WebClient: SSE 流式 Chat

启动流程
  -> KnowledgeBaseLoader: 加载 stage1/2/3.md 到 Prompt 内存缓存
  -> KnowledgeBaseInitializer: 旧版 RAG 初始化
       -> DocumentSplitter
       -> EmbeddingService
       -> KnowledgeBaseStorage
       -> RagService.loadKnowledgeBase
```


## 4. 核心业务流程

### 4.1 创建会话与首题

1. `POST /api/session` 经鉴权后从 `UserContext` 获取用户 ID。
2. `InterviewServiceImpl.startSession(Long, ...)` 创建 `IN_PROGRESS` 会话，当前轮次设为 1。
3. 构造 system prompt，加入候选人简历、阶段大纲和工具规则。
4. 调用 `AiClient.chatWithToolsRetry`，执行 `ask_followup`、`change_topic` 或 `finish_interview`。
5. 保存 assistant 消息并返回会话 ID。
6. `POST /api/session/{id}/first` 又会重新构造 Prompt 并调用 AI；如果得到工具问题，会再次保存消息和问题。


### 4.2 回答与 ReAct

1. 查询会话并校验归属；已结束会话直接返回结束状态。
2. 检查问题数量是否达到 15 轮，以及最近 5 条已评价回答中 `NOT_FAMILIAR` 是否达到 2 条。
3. 保存候选人回答，加载历史消息，调用 AI 工具循环，最多 10 次。
4. `ask_followup`：评价为 `FAMILIAR_BUT_SHALLOW`，不推进轮次，保存追问。
5. `change_topic`：评价为 `NOT_FAMILIAR` 或 `VERY_FAMILIAR`，推进轮次并保存新题；连续两次不熟悉时直接结束。
6. `finish_interview`：按熟悉度分值计算平均分，与阶段通过线比较，写入会话最终状态。
7. 模型不调用工具时最多纠正 2 次，仍失败则清洗文本并降级为新问题。


### 4.3 评分、报告与逐题分析

- 阶段通过线：一面 6.0、二面 6.5、三面 7.0。
- `GET /report` 读取会话权威 `finalScore`，统计熟悉度分布并拼接对话记录。
- `GET /assessments` 查询 `Assessment`，再逐条查询对应 `Question`。
- 当前源码没有发现 `assessmentMapper.insert` 或 `update`；逐题评估数据不会由当前面试主流程生成。

## 5. 已知问题清单

### P0：影响正确性或安全性





|---|---|---|---|
| 会话创建事务包住外部 AI 调用 | `startSession(Long,...)` 的 `@Transactional` 覆盖了数据库写入、同步 API 调用和工具执行，外部调用慢/失败会长期占用事务并扩大锁持有时间 | `service/impl/InterviewServiceImpl.java:105-154` | 中：拆成短事务创建会话与事务外 AI，再用短事务保存结果 |
| 流式接口绕过同步回答的安全和状态校验 | `answerStream` 未检查会话是否存在、归属、是否已结束、轮次上限或连续不熟悉条件 | `service/impl/InterviewServiceImpl.java:910-981` | 中：复用统一前置校验和结束处理 |
| 外部 AI 空响应可能导致未处理异常 | `AiClient.doPost` 反序列化失败返回 null；最终重试兜底不验证；服务层直接访问 `choices.get(0)`；`simpleChat` 也直接解引用 | `client/AiClient.java:44-57,108-151`; `service/impl/InterviewServiceImpl.java:129-132` | 中：统一异常类型、响应校验和控制器错误映射 |
| 流式结束状态未完整处理 | 流式路径执行工具后只处理 `nextQuestion`，没有像同步路径一样处理 `finished`、最终分数和状态返回 | `service/impl/InterviewServiceImpl.java:965-975` | 中：建立流式事件模型并补齐 finish 分支 |
| 邀请码可能并发重复使用 | 注册先 `SELECT is_used=0`，再插入用户、再普通更新邀请码，不是条件原子占用 | `controller/RegisterController.java:61-78`; `mapper/InviteCodeMapper.java:11-12` | 中：条件更新并检查影响行数，或加锁/唯一约束策略 |

### P1：影响可维护性或性能

| 问题 | 根因 | 涉及文件 | 修复难度 |
|---|---|---|---|
| 核心服务过度集中 | `InterviewServiceImpl` 约 1197 行，同时承担 Prompt、工具执行、评分、阶段管理、报告、流式编排 | `service/impl/InterviewServiceImpl.java` | 高：拆出 PromptBuilder、ToolExecutor、StageService、ReportService |
| 首题存在重复 AI 调用和重复持久化风险 | `startSession` 已调用 AI，`getFirstQuestion` 又重新调用 AI；控制器仍要求前端再次调用 first 接口 | `controller/InterviewController.java:53-89`; `service/impl/InterviewServiceImpl.java:104-204` | 中：创建时生成并返回首题，或创建时只落会话并由 first 唯一生成 |
| `Assessment` 设计存在但主流程不写入 | 评分仅写入用户消息熟悉度和会话最终分数，逐题查询只能返回空或历史数据 | `service/impl/InterviewServiceImpl.java:821-848,1159-1195` | 中：在工具执行评价时建立题目-回答-评估关联并写入 |
| 逐题评估存在 N+1 查询 | 遍历评估记录时逐条 `questionMapper.selectById` | `service/impl/InterviewServiceImpl.java:1175-1181` | 低：批量查询题目并内存映射 |
| RAG 不是当前面试主流程 | 主流程使用 `KnowledgeBaseLoader`，未调用 `RagService.retrieve` | `service/impl/InterviewServiceImpl.java:12,66,708-730`; `rag/*` | 低至中：明确废弃旧链路或把检索上下文接入 Prompt |
| RAG 共享对象被并发修改 | 每次检索直接修改共享 `DocumentChunk.score`，同时排序和 Jev 重排 | `rag/RagService.java:47-66,140-145` | 中：使用查询结果副本或不可变评分对象 |
| RAG 向量输入缺少完整校验 | 未检查空向量、零向量、维度不一致、Embedding data 缺项或 index 越界 | `rag/RagService.java:189-205`; `rag/EmbeddingService.java:63-88`; `rag/KnowledgeBaseInitializer.java:73-80` | 低至中：校验响应结构并跳过/降级异常块 |
| RAG 知识库不会随 Markdown 自动更新 | 只要 `data/knowledge_base.json` 存在就跳过构建 | `rag/KnowledgeBaseInitializer.java:30-40` | 低：增加版本/hash/更新时间校验 |
| RAG 文件写入非原子 | 保存直接覆盖工作目录 JSON，未检查目录创建结果，也没有临时文件替换 | `rag/KnowledgeBaseStorage.java:20-27` | 低 |
| 历史消息无限加载 | 常量 `MAX_HISTORY_PER_STAGE=10` 定义但 `loadHistory` 未使用限制，长会话会不断扩大 Prompt | `service/impl/InterviewServiceImpl.java:55-56,757-766` | 低 |
| 同一话题追问上限只写在 Prompt | `MAX_FOLLOWUP_PER_TOPIC` 未在代码中计数或强制执行 | `config/InterviewConstants.java:19-20`; `service/impl/InterviewServiceImpl.java:741` | 中 |
| 外部 API 同步阻塞业务线程 | Embedding 使用 `.block()`，Chat 重试使用 `Thread.sleep`，RAG/同步面试均可能长时间占用线程 | `rag/EmbeddingService.java:54-61`; `client/AiClient.java:69-143` | 中：统一超时、异步化或隔离线程池 |

### P2：代码整洁度和低风险缺陷

| 问题 | 根因 | 涉及文件 | 修复难度 |
|---|---|---|---|
| 切分器保留调试输出 | 9 处 `System.out.println`，包括原文前 100 字符和每个段落长度 | `rag/DocumentSplitter.java:24-57` | 低：替换为受控 debug 日志 |
| 控制器类注释过时 | 注释写提供 4 个接口，实际有 8 个映射端点 | `controller/InterviewController.java:13-20` | 低 |
| RAG 遗留模型可疑 | `Document` 使用 `double[]`，当前 RAG 使用 `DocumentChunk` 的 `List<Double>`，源码中未发现有效使用路径 | `rag/Document.java`; `rag/DocumentChunk.java` | 低：确认后删除 |
| 配置打开 MyBatis stdout SQL 日志 | `StdOutImpl` 会直接输出 SQL 日志，不适合生产默认配置 | `src/main/resources/application.yml:7-13` | 低 |
| AI 客户端重复创建 ObjectMapper | Spring 已提供 ObjectMapper，`AiClient` 和部分 RAG 存储类自行 new | `client/AiClient.java:31-35`; `rag/KnowledgeBaseStorage.java:18` | 低 |
| 无统一 REST 异常处理 | 多处直接抛 `RuntimeException`/`IllegalArgumentException`，控制器未统一转换错误响应 | `controller/*`; `service/impl/InterviewServiceImpl.java` | 中 |
| 无效 Token 日志包含凭据 | 鉴权失败时直接记录完整 `Authorization` token | `auth/AuthInterceptor.java:43-47` | 低 |

## 6. 修复建议顺序

### 第一阶段：先修正确性和访问边界

1. **统一同步/流式请求前置校验和结束处理**。这是最优先项，因为流式接口当前可能绕过会话归属和状态检查，属于直接影响数据越权与状态一致性的风险。范围：`InterviewServiceImpl.answerStream`、抽取共享校验方法、补充 finished 事件/最终状态。
2. **修复 AI 客户端错误契约**。所有非 2xx、空 body、反序列化失败、空 choices 都应转为明确异常或结构化失败，不能返回 null 后由业务层偶发 NPE。范围：`AiClient` 及调用处，预计 1-3 个文件。
3. **原子化邀请码占用**。在并发注册场景下先条件更新 `is_used`，再确认影响行数，避免一个邀请码创建多个用户。范围：`InviteCodeMapper`、`RegisterController`，预计 2 个文件。
4. **将 AI 调用移出创建会话事务**。先短事务写会话，事务外调用模型，再短事务保存首题；失败时写入可恢复状态。范围：`InterviewServiceImpl`，必要时增加一个结果状态字段或辅助服务。
 
### 第二阶段：恢复业务数据完整性和流程一致性

5. **消除首题重复调用**。选定“创建会话即生成首题”或“first 接口唯一生成”一种契约，删除另一条路径的重复 AI 调用和重复写入。范围：`InterviewController`、`InterviewServiceImpl`，预计 2 个文件。
6. **补齐 Assessment 写入链路**。明确评估对应的是哪道题和哪条回答，在评价完成时写入 `Assessment`；之后逐题报告才有稳定数据。范围：`InterviewServiceImpl`、实体/mapper 可能需要轻量扩展。
7. **实现历史、追问和轮次的代码级限制**。应用 `MAX_HISTORY_PER_STAGE`，实际计数 `MAX_FOLLOWUP_PER_TOPIC`，统一同步与流式计数。范围：`InterviewServiceImpl` 和必要的查询条件。
 
### 第三阶段：降低维护和性能成本

8. **拆分 `InterviewServiceImpl`**。先抽 `PromptBuilder` 和 `ToolExecutor`，再抽阶段/报告服务；每次拆分后保持接口不变并补核心单测。范围较大，预计 4-8 个文件。
9. **修复 RAG 输入、并发和存储边界**。处理向量维度/零向量/Embedding 响应完整性，避免共享 score 写入，并采用原子文件替换。范围：`rag` 下 3-5 个文件。
10. **明确两套知识库的产品边界**。当前 V2 Prompt 大纲和旧版 RAG 向量链路并存但主流程只用前者；应选择保留、接入或删除旧链路，避免维护无效组件。范围：配置、RAG 和服务实现。
 
### 第四阶段：清理和工程化

11. 将 `System.out` 替换为日志并移除 `Document` 等已确认遗留类。
12. 注入统一 `ObjectMapper`，关闭或按环境控制 MyBatis stdout SQL 日志。
13. 增加 `@RestControllerAdvice`，统一错误码和响应结构。
14. 补充单元测试：`DocumentSplitter`、余弦相似度、最终评分、工具参数解析、连续不熟悉淘汰、邀请码并发占用和会话归属校验。


## 7. 资源文件概览

- `src/main/resources/static/index.html`：静态前端入口。
- `src/main/resources/knowledge/stage1.md`、`stage2.md`、`stage3.md`：当前主流程使用的三阶段 Prompt 大纲。
- `src/main/resources/knowledge/Java并发基础.md`、`Java并发进阶.md`、`Java集合框架.md`、`Java集合源码.md`、`JVM内存与GC.md`、`MySQL事务.md`、`MySQL索引.md`、`Redis.md`、`Spring框架.md`：旧版 RAG 文档资源。
- `src/main/resources/rag-knowledge.txt`：旧版知识内容资源。

## 8. 探索边界与未确认事项

- 当前工作目录不是 Git 仓库，无法提供 Git diff/status 作为变更证明；本次仅使用 `apply_patch` 新增本文档。
- 未读取 `PROJECT_MAP.md`，符合项目约束。
- 未对 MySQL 表结构、数据库索引、前端 JavaScript 行为和真实外部 API 响应做运行时验证，因此邀请码唯一约束、列类型、前端是否重复调用首题等事项仍需结合数据库/前端或集成测试确认。
- `TypeSafeClient` 的请求格式由源码固定为 `model/state/questions`，是否完全符合当前官方 API 版本未做联网验证。
