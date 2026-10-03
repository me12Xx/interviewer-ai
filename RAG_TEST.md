# RAG 模块测试说明

## 测试 RAG 功能

### 1. 启动应用

```bash
java -jar target/interviewer-ai-0.0.1-SNAPSHOT.jar
```

启动后会自动：
- 读取 `src/main/resources/rag-knowledge.txt` 知识库文档
- 调用 DeepSeek Embedding API 生成向量
- 将文档切分为多个 chunk（每个 500 字符，重叠 50 字符）
- 保存向量库到 `data/vector-store.json`

### 2. 测试检索接口

**查询项目技术栈：**
```bash
curl "http://localhost:8080/api/rag/search?query=项目用了哪些技术栈&topK=3"
```

**查询 ReAct 原理：**
```bash
curl "http://localhost:8080/api/rag/search?query=ReAct是什么原理&topK=3"
```

**查询雪花算法：**
```bash
curl "http://localhost:8080/api/rag/search?query=雪花算法怎么实现的&topK=3"
```

**查询如何优化性能：**
```bash
curl "http://localhost:8080/api/rag/search?query=如果QPS上涨10倍怎么优化&topK=5"
```

### 3. 测试上下文生成

```bash
curl "http://localhost:8080/api/rag/context?query=为什么手写ReAct不用LangChain&topK=3"
```

返回格式化的上下文，可以直接拼接到 system message。

### 4. 查看统计信息

```bash
curl "http://localhost:8080/api/rag/stats"
```

返回：
```json
{
  "documentCount": 15,
  "status": "ready"
}
```

## 工作原理

### 文档切分
- 按段落切分（`\n\n` 分隔符）
- 每个 chunk 大小：500 字符
- 重叠：50 字符（避免语义截断）

### Embedding 生成
- 调用 DeepSeek Embedding API
- 模型：deepseek-embedding
- 向量维度：通常是 1536 维

### 向量检索
- 相似度计算：余弦相似度
- 公式：`cos(θ) = A·B / (|A||B|)`
- Top-K 召回：返回相似度最高的 K 个文档片段

### 上下文拼接
```
以下是相关的知识库内容：

[文档片段 1] (相似度: 0.856)
ReAct 是 Reasoning + Acting 的缩合...

[文档片段 2] (相似度: 0.782)
本项目手写 ReAct Agent，不依赖 LangChain...
```

## 面试时如何讲

**Q: 你的 RAG 是怎么实现的？**

A: 我实现了基础 RAG 模块，流程是：
1. 文档切分：按段落切分，每个 chunk 500 字符，重叠 50 字符
2. Embedding 生成：调用 DeepSeek Embedding API
3. 向量存储：本地 JSON 文件（生产环境可以用 PGVector）
4. 向量检索：余弦相似度计算，Top-K 召回
5. 上下文拼接：把检索到的文档片段格式化后拼到 system message

**Q: 为什么要做重叠？**

A: 防止语义截断。比如一句话"本项目手写 ReAct Agent"，如果正好在 chunk 边界被切开，后半句会丢失上下文。重叠 50 字符能保证关键信息不会被切断。

**Q: 余弦相似度公式是什么？**

A: `cos(θ) = A·B / (|A||B|)`，其中 A·B 是向量点积，|A| 和 |B| 是向量的模。结果在 [-1, 1] 之间，值越接近 1 表示越相似。

**Q: 和 Fine-tune 的区别？**

A: RAG 是外挂知识，不改变模型参数，适合知识频繁更新的场景。Fine-tune 是内化知识，改变模型参数，适合特定领域任务。

**Q: 如果检索到的文档不相关怎么办？**

A: 可以设置相似度阈值（如 0.7），低于阈值的不拼入 prompt。或者用 Rerank 模型二次排序，但我这个项目没做。

## 注意事项

1. **首次启动会调用 Embedding API**：根据文档大小，可能需要 1-2 分钟
2. **API Key 需要配置**：`DEEPSEEK_API_KEY` 环境变量
3. **向量库会保存到 data/vector-store.json**：下次启动直接加载，不会重新生成
4. **如果知识库更新**：删除 `data/vector-store.json`，重启应用会重新索引

## 扩展方向

如果有时间（11 月评估后），可以考虑：
- 支持多文档索引
- 添加文档元数据过滤
- 改用 PGVector 向量数据库
- 添加 Rerank 二次排序
- 支持混合检索（BM25 + 向量检索）
