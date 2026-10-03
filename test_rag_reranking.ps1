# RAG 重排序 A/B 对比测试脚本

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  RAG 重排序 A/B 对比测试" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$jarPath = "E:\IDEA1\Interviewer_ai\target\interviewer-ai-0.0.1-SNAPSHOT.jar"

# 测试参数
$testSessionA = $null
$testSessionB = $null
$resumeText = "5年Java开发经验，精通Spring框架、MySQL数据库优化、Redis缓存、多线程并发编程"
$targetPosition = "Java高级工程师"

Write-Host "测试简历: $resumeText" -ForegroundColor Yellow
Write-Host ""

# ==================== Test A: 基线测试（禁用 Jev） ====================
Write-Host "========================================" -ForegroundColor Green
Write-Host "  Test A: 基线测试（余弦相似度）" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green

Write-Host "1. 停止旧应用..." -ForegroundColor Gray
Stop-Process -Name java -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 3

Write-Host "2. 启动应用（TYPESAFE_ENABLED=false）..." -ForegroundColor Gray
$env:TYPESAFE_ENABLED = "false"
Remove-Item Env:\TYPESAFE_API_KEY -ErrorAction SilentlyContinue

$processA = Start-Process -FilePath "$env:JAVA_HOME\bin\java.exe" `
    -ArgumentList "-jar", $jarPath `
    -PassThru `
    -WindowStyle Hidden

Start-Sleep -Seconds 20

if (Test-NetConnection -ComputerName localhost -Port 8080 -InformationLevel Quiet) {
    Write-Host "   应用已启动" -ForegroundColor Green
} else {
    Write-Host "   应用启动失败！" -ForegroundColor Red
    exit 1
}

Write-Host "3. 创建测试会话..." -ForegroundColor Gray
$responseA = curl.exe -s -X POST "http://localhost:8080/api/session" `
    -H "Content-Type: application/x-www-form-urlencoded" `
    -d "resumeText=$resumeText&targetPosition=$targetPosition" | ConvertFrom-Json

$sessionIdA = $responseA.sessionId
Write-Host "   Session ID: $sessionIdA" -ForegroundColor Cyan

Write-Host "4. 获取第一题..." -ForegroundColor Gray
$question1A = curl.exe -s -X POST "http://localhost:8080/api/session/$sessionIdA/first"
Write-Host ""
Write-Host "   第一题: $question1A" -ForegroundColor Yellow
Write-Host ""

Write-Host "5. 回答问题（触发 RAG 检索）..." -ForegroundColor Gray
$answer1 = "Spring通过三级缓存解决循环依赖，一级缓存存完整Bean，二级缓存存早期Bean对象，三级缓存存ObjectFactory"
$responseAnswer1A = curl.exe -s -X POST "http://localhost:8080/api/session/$sessionIdA/chat" `
    -H "Content-Type: application/x-www-form-urlencoded" `
    -d "answer=$answer1" | ConvertFrom-Json

Write-Host ""
Write-Host "   下一题: $($responseAnswer1A.question)" -ForegroundColor Yellow
Write-Host "   评级: $($responseAnswer1A.proficiencyLevel)" -ForegroundColor Cyan
Write-Host ""

Write-Host "6. 停止应用..." -ForegroundColor Gray
Stop-Process -Id $processA.Id -Force
Start-Sleep -Seconds 3

Write-Host ""
Write-Host "Test A 完成！" -ForegroundColor Green
Write-Host "检索使用: 余弦相似度" -ForegroundColor Gray
Write-Host ""
Write-Host "按任意键继续 Test B..." -ForegroundColor Yellow
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")

# ==================== Test B: 实验组（启用 Jev 重排序） ====================
Write-Host ""
Write-Host "========================================" -ForegroundColor Magenta
Write-Host "  Test B: 实验组（Jev 重排序）" -ForegroundColor Magenta
Write-Host "========================================" -ForegroundColor Magenta

Write-Host "请输入你的 TypeSafe API Key: " -ForegroundColor Yellow -NoNewline
$apiKey = Read-Host

if ([string]::IsNullOrWhiteSpace($apiKey)) {
    Write-Host "未提供 API Key，跳过 Test B" -ForegroundColor Red
    exit 0
}

Write-Host "1. 启动应用（TYPESAFE_ENABLED=true）..." -ForegroundColor Gray
$env:TYPESAFE_API_KEY = $apiKey
$env:TYPESAFE_ENABLED = "true"

$processB = Start-Process -FilePath "$env:JAVA_HOME\bin\java.exe" `
    -ArgumentList "-jar", $jarPath `
    -PassThru `
    -WindowStyle Hidden

Start-Sleep -Seconds 20

if (Test-NetConnection -ComputerName localhost -Port 8080 -InformationLevel Quiet) {
    Write-Host "   应用已启动" -ForegroundColor Green
} else {
    Write-Host "   应用启动失败！" -ForegroundColor Red
    exit 1
}

Write-Host "2. 创建测试会话..." -ForegroundColor Gray
$responseB = curl.exe -s -X POST "http://localhost:8080/api/session" `
    -H "Content-Type: application/x-www-form-urlencoded" `
    -d "resumeText=$resumeText&targetPosition=$targetPosition" | ConvertFrom-Json

$sessionIdB = $responseB.sessionId
Write-Host "   Session ID: $sessionIdB" -ForegroundColor Cyan

Write-Host "3. 获取第一题..." -ForegroundColor Gray
$question1B = curl.exe -s -X POST "http://localhost:8080/api/session/$sessionIdB/first"
Write-Host ""
Write-Host "   第一题: $question1B" -ForegroundColor Yellow
Write-Host ""

Write-Host "4. 回答问题（触发 Jev 重排序）..." -ForegroundColor Gray
$responseAnswer1B = curl.exe -s -X POST "http://localhost:8080/api/session/$sessionIdB/chat" `
    -H "Content-Type: application/x-www-form-urlencoded" `
    -d "answer=$answer1" | ConvertFrom-Json

Write-Host ""
Write-Host "   下一题: $($responseAnswer1B.question)" -ForegroundColor Yellow
Write-Host "   评级: $($responseAnswer1B.proficiencyLevel)" -ForegroundColor Cyan
Write-Host ""

Write-Host "5. 停止应用..." -ForegroundColor Gray
Stop-Process -Id $processB.Id -Force

Write-Host ""
Write-Host "Test B 完成！" -ForegroundColor Green
Write-Host "检索使用: Jev 重排序" -ForegroundColor Gray
Write-Host ""

# ==================== 对比结果 ====================
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  对比结果" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Test A（余弦相似度）:" -ForegroundColor Yellow
Write-Host "  第一题: $question1A"
Write-Host "  第二题: $($responseAnswer1A.question)"
Write-Host ""
Write-Host "Test B（Jev 重排序）:" -ForegroundColor Magenta
Write-Host "  第一题: $question1B"
Write-Host "  第二题: $($responseAnswer1B.question)"
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "对比说明：" -ForegroundColor Gray
Write-Host "- 如果两个测试生成的问题明显不同，说明 Jev 重排序改变了检索结果" -ForegroundColor Gray
Write-Host "- 查看应用日志，搜索 'Jev 重排序' 查看详细耗时和置信度" -ForegroundColor Gray
Write-Host "========================================" -ForegroundColor Cyan
