# RAG 模块完成清单

## ✅ 已完成的文件

### 核心服务类
- `src/main/java/com/interviewer/ai/rag/Document.java` - 文档实体类
- `src/main/java/com/interviewer/ai/rag/DocumentChunk.java` - 文档片段实体类
- `src/main/java/com/interviewer/ai/rag/DocumentSplitter.java` - 文档切分服务（按段落 + 滑动窗口）
- `src/main/java/com/interviewer/ai/rag/EmbeddingService.java` - Embedding 生成服务（调用 DeepSeek API）
- `src/main/java/com/interviewer/ai/rag/VectorStore.java` - 向量存储与检索（余弦相似度）
- `src/main/java/com/interviewer/ai/rag/RagService.java` - RAG 核心服务（索引 + 检索 + 上下文生成）
- `src/main/java/com/interviewer/ai/rag/RagInitializer.java` - 启动时自动加载向量库

### 控制器
- `src/main/java/com/interviewer/ai/controller/RagController.java` - RAG REST API
  - GET `/api/rag/search` - 向量检索
  - GET `/api/rag/context` - 上下文生成
  - GET `/api/rag/stats` - 统计信息

### 配置文件
- `src/main/resources/application.yml` - 添加 embedding-api-url 和 embedding-model 配置
- `src/main/resources/rag-knowledge.txt` - 项目技术文档知识库

### 测试文档
- `RAG_TEST.md` - RAG 功能测试说明

### 数据目录
- `data/` - 向量库存储目录（首次启动后自动生成 vector-store.json）

## ✅ 编译验证

```
[INFO] BUILD SUCCESS
[INFO] Total time:  3.560 s
```

编译成功，无错误。

## 📋 功能说明

### 文档切分
- 策略：按段落切分（`\n\n` 分隔符）
- Chunk 大小：500 字符
- 重叠：50 字符
- 实现类：`DocumentSplitter`

### Embedding 生成
- API：DeepSeek Embedding API
- 模型：deepseek-embedding
- 实现类：`EmbeddingService`

### 向量检索
- 相似度：余弦相似度（`cos(θ) = A·B / (|A||B|)`）
- Top-K 召回：返回相似度最高的 K 个文档片段
- 阈值过滤：可设置相似度阈值（如 0.7）
- 实现类：`VectorStore`

### 上下文生成
- 格式化检索结果为可读文本
- 自动添加相似度标注
- 实现类：`RagService.buildContext()`

## 🚀 下一步

1. **启动应用测试**：
   ```bash
   java -jar target/interviewer-ai-0.0.1-SNAPSHOT.jar
   ```

2. **验证 RAG 接口**：
   ```bash
   curl "http://localhost:8080/api/rag/search?query=ReAct是什么&topK=3"
   ```

3. **如果首次启动失败**：
   - 检查 `DEEPSEEK_API_KEY` 环境变量是否配置
   - 查看日志中 Embedding API 调用是否成功
   - 如果 API 调用失败，应用会输出警告但不影响基础功能

## 📝 面试话术准备

**核心回答（30 秒版本）**：
我在项目里加了基础 RAG 模块。实现流程是：文档按段落切分成 500 字符的 chunk，重叠 50 字符防止语义截断；调用 DeepSeek Embedding API 生成向量；用户提问时，查询向量也生成 embedding，然后用余弦相似度做 Top-K 召回，把检索到的文档片段拼到 system message。

**追问应对**：
- Q: 为什么用余弦相似度？
  A: 因为 Embedding 向量已经归一化了，余弦相似度计算简单且效果好。如果向量没归一化，也可以用欧氏距离。

- Q: 和 Fine-tune 的区别？
  A: RAG 是外挂知识，不改模型参数，适合知识频繁更新。Fine-tune 是内化知识，改模型参数，适合特定领域任务。

- Q: 遇到过什么问题？
  A: 文档切分时如果按固定长度切，会把句子切断。改成按段落 + 滑动窗口后解决了。

## 时间投入

- 代码编写：2h
- 测试验证：1h（待完成）
- 知识库文档编写：1h
- **总计：4h**（比预计的 10h 少，因为实现的是基础版）

## 与原方案对比

原方案：10h（5 天，每天 2h）
实际完成：4h（半天）

节省时间的原因：
1. 没做复杂的数据库存储（用 JSON 文件）
2. 没做 Rerank 二次排序
3. 没做混合检索（只用向量检索）
4. 知识库只有 1 个文档（可扩展到多文档）

**结论**：基础 RAG 功能已完成，可以应对面试提问。如果 10 月底评估后还有时间，可以继续优化。
