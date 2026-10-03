# BUG 日志

## BUG-001：DeepSeek API Function Calling 返回空响应

### 时间
2026-09-12

### 现象
调用 DeepSeek API 时，只要请求体带了 `tools` 参数，响应就异常：
- `finish_reason`: null
- `content`: 空字符串
- `tool_calls`: null
- `usage`: 全部为 null

不带 `tools` 的普通对话调用完全正常。

### 排查过程

1. **怀疑模型不支持** → 换了 4 个模型（opus5 → gpt-4o → deepseek-flash → deepseek-chat），全部失败
2. **怀疑 `tool_choice` 参数** → 去掉后仍然失败
3. **怀疑 API 端点** → 从 `/v1/chat/completions` 改为 `/chat/completions`，仍然失败
4. **怀疑请求格式** → 去掉 `max_tokens` / `temperature`，只保留最少参数，仍然失败
5. **curl 直接测试** → 用 PowerShell `Invoke-RestMethod` 直接打 API，发现 API 返回了正常数据（`finish_reason: "stop"`，`usage` 有值）
6. **关键对比**：curl 返回正常、Java 代码返回全 null → 问题在 Java 反序列化
7. **改为先拿原始 String 再手动反序列化** → 发现原始 JSON 里有 `reasoning_content`、`logprobs`、`system_fingerprint` 等未知字段

### 根因
**Spring RestClient 默认使用的 Jackson ObjectMapper 的 `FAIL_ON_UNKNOWN_PROPERTIES` 为 `true`。**

DeepSeek API 响应里包含了 DTO 类未定义的字段（如 `reasoning_content`、`logprobs`、`system_fingerprint`、`prompt_tokens_details`、`completion_tokens_details` 等），Jackson 反序列化时遇到未知字段直接抛异常，RestClient 内部吞掉异常后返回了一个部分解析的空对象。

### 修复
在 `AiClient` 内部持有自己的 ObjectMapper，所有 API 调用统一走 `doPost()` 方法——先拿原始 String，再用自己的 ObjectMapper 反序列化：

```java
@Component
public class AiClient {

    private final ObjectMapper objectMapper;

    public AiClient(RestClient aiRestClient, AiConfig aiConfig) {
        this.restClient = aiRestClient;
        this.aiConfig = aiConfig;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private ChatResponse doPost(ChatRequest req) {
        String rawJson = restClient.post()
                .uri(aiConfig.getApiUrl())
                .body(req)
                .retrieve()
                .body(String.class);  // 先拿原始 String

        return objectMapper.readValue(rawJson, ChatResponse.class);  // 再手动反序列化
    }
}
```

**为什么不用 RestClient 的 messageConverters 方案：**
Spring Boot 4.x 的 `RestClient.builder()` 创建的 RestClient 不会自动注入容器中的 ObjectMapper。
尝试过以下方案均无效：
1. `RestClient.builder().messageConverters()` 替换 converter → 未生效
2. `@Bean @Primary ObjectMapper` → RestClient 不读取
3. `Jackson2ObjectMapperBuilderCustomizer` → 缺少 spring-boot-autoconfigure 依赖

### 教训
1. **对接第三方 API 时，第一件事就是配 `FAIL_ON_UNKNOWN_PROPERTIES = false`**。API 提供方随时可能加新字段，不忽略未知字段 = 定时炸弹
2. **调试序列化问题时，先拿原始 String 再手动解析**，不要直接 `.body(Xxx.class)`，否则看不到真实响应
3. **RestClient 反序列化失败时不会抛异常到调用方**，而是静默返回半解析对象（所有未解析字段为 null），这非常隐蔽
4. **curl 对照测试是最有效的排查手段**：同一个请求，curl 成功 Java 失败 → 问题 100% 在客户端代码
5. **不要同时改多个变量**：模型名、端点、参数同时改，无法定位是哪个导致的问题
6. **Spring RestClient 不会自动使用容器中的 ObjectMapper**，自己创建的 RestClient 需要自己管理反序列化
7. **最简单可靠的方案往往是最好的**：与其折腾 Spring 的 MessageConverter 配置，不如直接 `.body(String.class)` + 手动 `objectMapper.readValue()`

### 涉及文件
- `src/main/java/com/interviewer/ai/config/AiConfig.java`
- `src/main/java/com/interviewer/ai/client/AiClient.java`
- `src/main/resources/application.yml`
- `src/main/java/com/interviewer/ai/client/dto/ChatResponse.java`

---

## BUG-002：startSession 第一题保存成 XML 格式

### 时间
2026-09-14

### 现象
`startSession` 方法生成的第一题，保存到数据库的内容是 XML 格式：
```xml
<｜｜DSML｜｜ calls>
<｜｜DSML｜｜ invoke name="ask_followup">
<｜｜DSML｜｜ parameter name="question" string="true">你简历里提到做过电商订单系统...</｜｜DSML｜｜ parameter>
...
</｜｜DSML｜｜ calls>
```

而不是纯文本问题。

### 根因
`startSession` 使用 `chatWithRetry`（无工具调用），但 `buildSystemPrompt` 里写了"每次必须调用工具"，导致模型在无工具可用的情况下，把工具调用信息直接写在 `content` 字段里（以 XML 格式）。

### 修复
改为用 `chatWithToolsRetry` + 工具调用生成第一题：

```java
@Override
public InterviewSession startSession(String resumeText, String targetPosition) {
    // ... 创建 session ...
    
    List<AiMessage> messages = new ArrayList<>();
    messages.add(aiMsg("system", buildSystemPrompt(session)));
    messages.add(aiMsg("user", "请开始面试，从简历中选一个核心话题出第一个问题，不要寒暄。"));

    List<Tool> tools = buildTools();
    
    ChatResponse resp = aiClient.chatWithToolsRetry(messages, tools, 3);
    // 解析 tool_calls，提取 question
    // ...
    
    saveMessage(session.getId(), "assistant", firstQuestion, 1);
    return session;
}
```

### 教训
1. **System prompt 的指令要和实际调用方式匹配**：要求"必须调用工具"，就必须传 `tools` 参数
2. **第一题也应该走统一的 ReAct 流程**，不要用单独的逻辑
3. **保存到数据库前，确认内容格式正确**（特别是 content 字段）

### 涉及文件
- `src/main/java/com/interviewer/ai/service/impl/InterviewServiceImpl.java` - startSession 方法

---

## BUG-003：实体类 @Data 注解导致的 equals/hashCode 问题

### 时间
2026-09-14

### 现象
Lombok 的 `@Data` 注解会自动生成基于**所有字段**的 `equals()` 和 `hashCode()` 方法。

**导致的问题**：
1. 修改对象后，hashCode 变化，HashSet/HashMap 找不到原对象
2. 同一条数据库记录，两次查出来不 equals（因为 `createdAt` 时间略有不同）
3. 在 HashMap/HashSet 里查找失效

### 修复
用 `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` + `@EqualsAndHashCode.Include`，只根据 `id` 计算 equals 和 hashCode：

```java
@TableName("interview_session")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class InterviewSession {

    @TableId(type = IdType.AUTO)
    @EqualsAndHashCode.Include
    private Long id;

    private String resumeText;
    // ...
}
```

同时用 `@ToString.Exclude` 排除长文本字段（避免日志膨胀）：

```java
@ToString.Exclude
private String resumeText;

@ToString.Exclude
private String content;
```

### 教训
1. **数据库实体类的 equals/hashCode 应该只基于主键**（id），而非所有字段
2. **toString 应该排除长文本字段**，避免日志膨胀和性能问题
3. **@Data 注解很方便，但要理解它生成的所有方法的行为**

### 涉及文件
- `src/main/java/com/interviewer/ai/InterviewSession.java`
- `src/main/java/com/interviewer/ai/ChatMessage.java`
- `src/main/java/com/interviewer/ai/Question.java`
- `src/main/java/com/interviewer/ai/Assessment.java`

---

## BUG-004：/first 接口调用 answer("") 导致 500 错误

### 时间
2026-09-14

### 现象
修改 `startSession` 改用 ReAct 循环后，调用 `/api/session/{id}/first` 接口返回 500 错误。

### 根因
`startSession` 已经在创建会话时生成并保存了第一题到 `chat_message` 表，但 `/first` 接口仍然调用 `answer(id, "")`，导致：
1. 历史消息里已有第一题（assistant 消息）
2. 再次走 ReAct 循环时逻辑混乱

### 修复
改为从数据库直接读取第一题：

```java
@Override
public String getFirstQuestion(Long sessionId) {
    LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(ChatMessage::getSessionId, sessionId)
           .eq(ChatMessage::getRole, "assistant")
           .eq(ChatMessage::getRoundNum, 1)
           .orderByAsc(ChatMessage::getId)
           .last("LIMIT 1");
    
    ChatMessage firstMsg = chatMessageMapper.selectOne(wrapper);
    if (firstMsg == null) {
        throw new IllegalArgumentException("第一题不存在，会话可能未正确初始化");
    }
    return firstMsg.getContent();
}
```

### 教训
1. **接口设计要与内部实现匹配**：`startSession` 生成第一题后，`/first` 应该只负责读取，而非重新生成
2. **单一职责原则**：一个接口只做一件事
3. **幂等性**：多次调用 `/first` 应该返回相同结果

### 涉及文件
- `src/main/java/com/interviewer/ai/controller/InterviewController.java`
- `src/main/java/com/interviewer/ai/service/InterviewService.java`
- `src/main/java/com/interviewer/ai/service/impl/InterviewServiceImpl.java`
