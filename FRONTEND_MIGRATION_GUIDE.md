# 前端迁移指南 - V2 API 适配

**目标读者**：前端开发者  
**预计工作量**：1-2 小时  
**影响范围**：所有调用 `/api/session/{id}/chat` 的页面

---

## 🎯 核心变化

### API 返回值变化

```diff
- 旧版：返回 String（纯文本）
+ 新版：返回 JSON 对象
```

---

## 📋 必须修改的代码

### ❌ 旧版代码（需删除）

```javascript
// 调用面试接口
fetch('/api/session/123/chat', {
  method: 'POST',
  headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  body: 'answer=' + encodeURIComponent(userInput)
})
.then(response => response.text())  // ❌ 旧版：text()
.then(question => {
  // 直接显示下一题
  document.getElementById('question').innerText = question;
  
  // 旧版无法判断面试是否结束，只能靠魔法字符串
  if (question.includes('已结束')) {
    showReport();
  }
});
```

---

### ✅ 新版代码（必须改为）

```javascript
// 调用面试接口
fetch('/api/session/123/chat', {
  method: 'POST',
  headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  body: 'answer=' + encodeURIComponent(userInput)
})
.then(response => response.json())  // ✅ 新版：json()
.then(result => {
  if (result.finished) {
    // 面试结束，显示最终报告
    showFinalReport({
      score: result.finalScore,      // 最终得分（如 7.2）
      status: result.status,          // 状态：PASSED 或 FAILED
      message: result.message         // 结束消息
    });
  } else {
    // 继续面试，显示下一题
    document.getElementById('question').innerText = result.question;
    
    // 新增：显示本题评价（可选）
    showProficiencyBadge(result.proficiencyLevel);
  }
});
```

---

## 🆕 新增字段说明

### 进行中时的返回值

```json
{
  "question": "那什么时候链表会转成红黑树？",
  "proficiencyLevel": "FAMILIAR_BUT_SHALLOW",
  "finished": false
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `question` | String | 下一题的问题文本 |
| `proficiencyLevel` | String | 对本次回答的评价（见下表） |
| `finished` | Boolean | 是否面试结束（`false` 表示继续） |

#### proficiencyLevel 可能的值

| 值 | 含义 | UI 建议 |
|----|------|---------|
| `VERY_FAMILIAR` | 非常熟悉（10 分） | 显示绿色徽章 "✅ 很好" |
| `FAMILIAR_BUT_SHALLOW` | 了解但浅（6 分） | 显示黄色徽章 "⚠️ 还需深入" |
| `NOT_FAMILIAR` | 不熟悉（2 分） | 显示红色徽章 "❌ 需加强" |
| `null` | 未评价（降级场景） | 不显示徽章 |

---

### 面试结束时的返回值

```json
{
  "finished": true,
  "finalScore": 7.2,
  "status": "PASSED",
  "message": "恭喜通过一面！"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `finished` | Boolean | `true` 表示面试结束 |
| `finalScore` | Number | 最终得分（0-10，保留 1 位小数） |
| `status` | String | `PASSED`（通过）或 `FAILED`（未通过） |
| `message` | String | 结束消息（如 "恭喜通过一面！"） |

---

## 💡 推荐 UI 实现

### 1. 显示实时评价徽章

```javascript
function showProficiencyBadge(level) {
  const badgeEl = document.getElementById('proficiency-badge');
  
  if (!level) {
    badgeEl.style.display = 'none';
    return;
  }
  
  const badges = {
    'VERY_FAMILIAR': { text: '✅ 很好', color: '#28a745' },
    'FAMILIAR_BUT_SHALLOW': { text: '⚠️ 还需深入', color: '#ffc107' },
    'NOT_FAMILIAR': { text: '❌ 需加强', color: '#dc3545' }
  };
  
  const badge = badges[level];
  badgeEl.innerText = badge.text;
  badgeEl.style.backgroundColor = badge.color;
  badgeEl.style.display = 'inline-block';
  
  // 3 秒后自动隐藏
  setTimeout(() => {
    badgeEl.style.display = 'none';
  }, 3000);
}
```

**HTML**：
```html
<div id="proficiency-badge" style="
  display: none;
  padding: 8px 16px;
  border-radius: 4px;
  color: white;
  font-weight: bold;
  margin-top: 10px;
"></div>
```

---

### 2. 显示最终报告

```javascript
function showFinalReport({ score, status, message }) {
  // 隐藏问题区域
  document.getElementById('interview-area').style.display = 'none';
  
  // 显示报告区域
  const reportEl = document.getElementById('report-area');
  reportEl.style.display = 'block';
  
  // 渲染报告
  reportEl.innerHTML = `
    <h2>${status === 'PASSED' ? '🎉 恭喜通过！' : '😔 未通过'}</h2>
    <div class="score-circle">
      <div class="score-value">${score}</div>
      <div class="score-label">最终得分</div>
    </div>
    <p class="message">${message}</p>
    
    ${status === 'PASSED' 
      ? '<button onclick="startNextStage()">开启下一阶段</button>'
      : '<button onclick="retryInterview()">重新挑战</button>'
    }
    
    <button onclick="viewDetailReport()">查看详细报告</button>
  `;
}
```

---

### 3. 错误处理

```javascript
fetch('/api/session/123/chat', {
  method: 'POST',
  body: 'answer=' + encodeURIComponent(userInput)
})
.then(response => {
  if (!response.ok) {
    throw new Error('HTTP ' + response.status);
  }
  return response.json();
})
.then(result => {
  // 正常处理逻辑
  if (result.finished) {
    showFinalReport(result);
  } else {
    document.getElementById('question').innerText = result.question;
    showProficiencyBadge(result.proficiencyLevel);
  }
})
.catch(error => {
  console.error('API 调用失败:', error);
  alert('网络错误，请稍后重试');
});
```

---

## 🔍 测试检查清单

### 基础功能测试
- [ ] 回答问题后，能正确显示下一题
- [ ] `proficiencyLevel` 徽章能正确显示并匹配颜色
- [ ] 面试结束时，能正确跳转到报告页面
- [ ] 最终得分和状态（PASSED/FAILED）能正确显示

### 边界情况测试
- [ ] 连续答错 2 次后，面试自动结束（显示 FAILED）
- [ ] 达到 15 轮后，面试强制结束
- [ ] 网络错误时，能正确显示错误提示
- [ ] `proficiencyLevel` 为 `null` 时，不显示徽章（不报错）

### 兼容性测试
- [ ] Chrome / Edge / Firefox 均能正常工作
- [ ] 移动端浏览器能正常显示徽章
- [ ] 弱网环境下能正确处理超时

---

## 🐛 常见问题

### Q1：为什么我的代码报 `result.question is undefined`？

**原因**：面试已结束时，返回值中没有 `question` 字段，只有 `finished`、`finalScore`、`status`、`message`。

**解决**：
```javascript
// ❌ 错误：不判断 finished 直接访问 question
document.getElementById('question').innerText = result.question;  // undefined

// ✅ 正确：先判断 finished
if (result.finished) {
  showFinalReport(result);
} else {
  document.getElementById('question').innerText = result.question;
}
```

---

### Q2：旧版如何判断面试结束？

**旧版**：靠魔法字符串 `question.includes('已结束')`，极其脆弱。

**新版**：直接判断 `result.finished === true`，可靠且清晰。

---

### Q3：`proficiencyLevel` 什么时候会是 `null`？

**场景**：极少数情况下，AI 模型未调用工具而是直接输出文本，系统降级返回纯文本问题时，`proficiencyLevel` 为 `null`。

**处理**：
```javascript
if (result.proficiencyLevel) {
  showProficiencyBadge(result.proficiencyLevel);
}
// null 时不显示徽章，不影响正常流程
```

---

### Q4：如何测试新 API？

**方法 1：使用 Postman**
```
POST http://localhost:8080/api/session/1/chat
Content-Type: application/x-www-form-urlencoded

answer=HashMap是数组+链表+红黑树
```

**方法 2：使用 curl**
```bash
curl -X POST http://localhost:8080/api/session/1/chat \
  -d "answer=HashMap是数组+链表+红黑树" \
  | jq '.'
```

**期望输出**：
```json
{
  "question": "那什么时候链表会转成红黑树？",
  "proficiencyLevel": "FAMILIAR_BUT_SHALLOW",
  "finished": false
}
```

---

## 📚 完整示例（React）

```jsx
import { useState } from 'react';

function InterviewChat({ sessionId }) {
  const [question, setQuestion] = useState('');
  const [userInput, setUserInput] = useState('');
  const [proficiency, setProficiency] = useState(null);
  const [finished, setFinished] = useState(false);
  const [finalResult, setFinalResult] = useState(null);

  const handleSubmit = async (e) => {
    e.preventDefault();
    
    const response = await fetch(`/api/session/${sessionId}/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: `answer=${encodeURIComponent(userInput)}`
    });
    
    const result = await response.json();
    
    if (result.finished) {
      setFinished(true);
      setFinalResult(result);
    } else {
      setQuestion(result.question);
      setProficiency(result.proficiencyLevel);
      setUserInput('');  // 清空输入框
    }
  };

  if (finished) {
    return (
      <div className="final-report">
        <h2>{finalResult.status === 'PASSED' ? '🎉 恭喜通过！' : '😔 未通过'}</h2>
        <div className="score">{finalResult.finalScore}</div>
        <p>{finalResult.message}</p>
      </div>
    );
  }

  return (
    <div className="interview-chat">
      <div className="question-box">
        <h3>面试官问：</h3>
        <p>{question}</p>
      </div>
      
      {proficiency && (
        <div className={`badge badge-${proficiency.toLowerCase()}`}>
          {proficiency === 'VERY_FAMILIAR' && '✅ 很好'}
          {proficiency === 'FAMILIAR_BUT_SHALLOW' && '⚠️ 还需深入'}
          {proficiency === 'NOT_FAMILIAR' && '❌ 需加强'}
        </div>
      )}
      
      <form onSubmit={handleSubmit}>
        <textarea 
          value={userInput}
          onChange={(e) => setUserInput(e.target.value)}
          placeholder="输入你的回答..."
        />
        <button type="submit">提交</button>
      </form>
    </div>
  );
}
```

---

## ✅ 迁移完成标志

- [ ] 所有调用 `/api/session/{id}/chat` 的地方已改为 `response.json()`
- [ ] 已添加 `result.finished` 判断逻辑
- [ ] 已实现实时评价徽章显示（可选但推荐）
- [ ] 已实现最终报告页面（显示 `finalScore` 和 `status`）
- [ ] 已完成基础功能测试
- [ ] 已完成边界情况测试

---

**如有疑问，请参考主文档：`V2_UPDATE_SUMMARY.md`**
