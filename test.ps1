# ====================================
# AI 面试官 - 完整测试脚本
# ====================================
# 使用方法：
#   1. 确保应用已启动（http://localhost:8080）
#   2. 在 PowerShell 中执行：.\test.ps1
# ====================================

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "AI 面试官 - 完整流程测试" -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan

# ====================================
# 步骤 1：创建面试会话
# ====================================
Write-Host "[步骤 1/6] 创建面试会话..." -ForegroundColor Green

$body = @{
    resumeText = "我叫李四，本科毕业，5年Java开发经验，熟悉Spring Cloud、Redis、MySQL、Kafka。做过电商订单系统、支付系统、用户画像系统。参与过双十一大促，系统峰值 QPS 达到 10w+。"
    targetPosition = "Java高级开发工程师"
}

try {
    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/session" -Method Post -Body $body
    $sessionId = $response.sessionId
    Write-Host "✓ 会话创建成功，会话 ID: $sessionId`n" -ForegroundColor Green
} catch {
    Write-Host "✗ 会话创建失败: $_" -ForegroundColor Red
    exit 1
}

# ====================================
# 步骤 2：获取第一题
# ====================================
Write-Host "[步骤 2/6] 获取第一题..." -ForegroundColor Green

try {
    $question1 = Invoke-RestMethod -Uri "http://localhost:8080/api/session/$sessionId/first" -Method Post
    Write-Host "第 1 题：" -ForegroundColor Yellow
    Write-Host "$question1`n" -ForegroundColor White
} catch {
    Write-Host "✗ 获取第一题失败: $_" -ForegroundColor Red
    exit 1
}

# ====================================
# 步骤 3-7：回答 5 轮问题
# ====================================
Write-Host "[步骤 3/6] 开始回答问题（共 5 轮）...`n" -ForegroundColor Green

$answers = @(
    "我们遇到过支付回调重复的问题。现象是用户支付成功后，支付宝回调了两次，导致订单状态被更新两次，积分也发放了两次。定位发现是网络抖动导致支付宝重试了回调。最终方案是在回调接口里加了幂等判断：用支付流水号作为唯一键，先查订单表，如果状态已经是已支付就直接返回成功，不重复处理。关键是用数据库唯一索引保证幂等，不是分布式锁。",
    
    "先更新订单状态为已支付，再发放积分。因为积分发放可能失败，但订单状态必须保证一致性。如果积分发放失败，会写到异常表，后台job定时重试。唯一索引是加在订单表的trade_no字段上，这个字段是支付宝返回的支付流水号，全局唯一。",
    
    "我们项目用Spring Cloud，服务间用Feign调用，消息队列用Kafka。订单服务、支付服务、用户服务、库存服务是独立的微服务。注册中心用Nacos，配置中心也是Nacos。限流熔断用Sentinel，链路追踪用SkyWalking。",
    
    "生产者 acks 配置是 1，只要 leader 写成功就返回。消费者是先处理业务再提交 offset，这样可以避免消息丢失。如果处理失败会抛异常，offset 不提交，下次重启会重新消费。业务侧用数据库唯一索引保证幂等，比如扣库存时把订单号作为唯一键，重复消费会被拦住。",
    
    "我们用 ShardingSphere 分库分表，按 user_id 取模分到 16 个库。每个库里订单表再按 user_id 取模分到 16 张表。跨分片查询比如按订单号查，会用广播路由，查所有分片然后合并结果。分布式事务用 Seata 的 AT 模式，支持自动回滚。慢查询优化主要是加索引和改写 SQL，用 explain 看执行计划。"
)

for ($i = 0; $i -lt $answers.Length; $i++) {
    $roundNum = $i + 1
    Write-Host "----------------------------------------" -ForegroundColor DarkGray
    Write-Host "第 $roundNum 轮回答：" -ForegroundColor Cyan
    Write-Host $answers[$i] -ForegroundColor White
    Write-Host ""
    
    $body = @{ answer = $answers[$i] }
    
    try {
        $response = Invoke-RestMethod -Uri "http://localhost:8080/api/session/$sessionId/chat" -Method Post -Body $body
        
        if ($response -like "*面试*结束*" -or $response -like "*报告*") {
            Write-Host "✓ 面试已结束" -ForegroundColor Green
            Write-Host "$response`n" -ForegroundColor Yellow
            break
        } else {
            Write-Host "面试官追问：" -ForegroundColor Yellow
            Write-Host "$response`n" -ForegroundColor White
        }
    } catch {
        Write-Host "✗ 回答失败: $_" -ForegroundColor Red
        exit 1
    }
    
    Start-Sleep -Seconds 1
}

# ====================================
# 步骤 8：查看评分报告
# ====================================
Write-Host "`n[步骤 4/6] 等待评分报告生成..." -ForegroundColor Green
Start-Sleep -Seconds 3

Write-Host "[步骤 5/6] 获取评分报告..." -ForegroundColor Green

try {
    $report = Invoke-RestMethod -Uri "http://localhost:8080/api/session/$sessionId/report" -Method Get
    
    Write-Host "`n========================================" -ForegroundColor Cyan
    Write-Host "评分报告" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host "会话 ID: $($report.sessionId)" -ForegroundColor White
    Write-Host "目标岗位: $($report.targetPosition)" -ForegroundColor White
    Write-Host "综合评分: $($report.score) / 10" -ForegroundColor Yellow
    Write-Host "`n报告内容:" -ForegroundColor White
    Write-Host "----------------------------------------" -ForegroundColor DarkGray
    Write-Host $report.report -ForegroundColor White
    Write-Host "----------------------------------------`n" -ForegroundColor DarkGray
} catch {
    Write-Host "✗ 获取报告失败: $_" -ForegroundColor Red
    exit 1
}

# ====================================
# 完成
# ====================================
Write-Host "[步骤 6/6] 测试完成！" -ForegroundColor Green
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "测试统计" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "会话 ID: $sessionId" -ForegroundColor White
Write-Host "回答轮次: $($answers.Length) 轮" -ForegroundColor White
Write-Host "最终评分: $($report.score) / 10" -ForegroundColor White
Write-Host "========================================`n" -ForegroundColor Cyan
