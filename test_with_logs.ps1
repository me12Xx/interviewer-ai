# 测试脚本：启动应用并捕获日志
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
cd E:\IDEA1\Interviewer_ai

# 启动应用并捕获输出
$job = Start-Job -ScriptBlock {
    & "C:\Program Files\Java\jdk-21\bin\java.exe" -jar "E:\IDEA1\Interviewer_ai\target\interviewer-ai-0.0.1-SNAPSHOT.jar" 2>&1
}

Write-Host "等待应用启动..." -ForegroundColor Yellow
Start-Sleep -Seconds 20

# 测试应用是否启动
$port = Test-NetConnection -ComputerName localhost -Port 8080 -InformationLevel Quiet
if ($port) {
    Write-Host "应用已启动，发送测试请求..." -ForegroundColor Green
    
    # 发送测试请求
    curl.exe -X POST "http://localhost:8080/api/session" `
        -H "Content-Type: application/x-www-form-urlencoded" `
        -d "resumeText=5年Java开发&targetPosition=Java工程师"
    
    Start-Sleep -Seconds 2
    
    # 获取日志
    Write-Host "`n============ 应用日志（最后50行）============" -ForegroundColor Cyan
    Receive-Job -Job $job | Select-Object -Last 50
    
} else {
    Write-Host "应用启动失败！" -ForegroundColor Red
    Receive-Job -Job $job
}

# 停止应用
Stop-Job -Job $job
Remove-Job -Job $job
Stop-Process -Name java -Force -ErrorAction SilentlyContinue
