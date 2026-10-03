package com.interviewer.ai.controller;

import com.interviewer.ai.InterviewSession;
import com.interviewer.ai.auth.UserContext;
import com.interviewer.ai.service.InterviewService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 面试 API 控制器
 * 提供 4 个 HTTP 接口：
 * 1. POST /api/session          - 创建会话
 * 2. POST /api/session/{id}/first - 获取第一题
 * 3. POST /api/session/{id}/chat  - 回答 + 获取下一题
 * 4. GET  /api/session/{id}/report - 查看评分报告
 */
@RestController
@RequestMapping("/api/session")
public class InterviewController {

    private final InterviewService interviewService;

    /**
     * 构造器注入（Spring 推荐方式，不需要 @Autowired）
     * Spring Boot 会自动把 InterviewServiceImpl 注入进来
     */
    public InterviewController(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    // ========== 接口 1：创建会话 ==========

    /**
     * 创建面试会话
     * HTTP 请求示例：
     * POST http://localhost:8080/api/session
     * Content-Type: application/x-www-form-urlencoded
     * resumeText=我是张三，3年Java后端
     * targetPosition=Java后端工程师
     * 返回示例（JSON）：
     * {
     *   "sessionId": 123,
     *   "firstQuestion": "请调用 /api/session/123/first 接口获取第一题"
     * }
     */
    @PostMapping
    public Map<String, Object> start(
            @RequestParam String resumeText,
            @RequestParam String targetPosition,
            @RequestParam(required = false, defaultValue = "java") String position
    ) {
        Long userId = UserContext.getCurrentUserId();

        InterviewSession session = interviewService.startSession(
                userId, resumeText, targetPosition, 1, position);

        return Map.of(
                "sessionId", session.getId().toString(),
                "firstQuestion", "请调用 /api/session/" + session.getId() + "/first 接口获取第一题"
        );
    }

    // ========== 接口 2：获取第一题 ==========

    /**
     * 获取第一题（从数据库读取）
     * HTTP 请求示例：
     * POST http://localhost:8080/api/session/123/first
     * 返回示例（纯文本）：
     * "请介绍一下 HashMap 的底层实现"
     * 设计说明：
     * - startSession 已经生成并保存了第一题
     * - 此接口只负责从数据库读取第一题并返回给前端
     */
    @PostMapping("/{id}/first")
    public String firstQuestion(@PathVariable Long id) {
        return interviewService.getFirstQuestion(id);
    }

    // ========== 接口 3：回答 + 获取下一题 ==========

    /**
     * 用户回答当前题，AI 返回下一题（或追问）
     * HTTP 请求示例：
     * POST http://localhost:8080/api/session/123/chat
     * Content-Type: application/x-www-form-urlencoded
     * answer=HashMap底层是数组+链表+红黑树
     * 返回示例（JSON - 进行中）：
     * {
     *   "question": "那什么时候链表会转成红黑树？",
     *   "proficiencyLevel": "FAMILIAR_BUT_SHALLOW",
     *   "finished": false
     * }
     * 返回示例（JSON - 面试结束）：
     * {
     *   "finished": true,
     *   "finalScore": 7.2,
     *   "status": "PASSED",
     *   "message": "恭喜通过一面！"
     * }
     */
    @PostMapping("/{id}/chat")
    public Map<String, Object> chat(
            @PathVariable Long id,           // 会话 ID
            @RequestParam String answer      // 用户回答
    ) {
        return interviewService.answer(id, answer);  // 调用 Service，返回 Map
    }

    // ========== 接口 4：查看评分报告 ==========

    /**
     * 查看评分报告
     * HTTP 请求示例：
     * GET http://localhost:8080/api/session/123/report
     * 返回内容包含最终分数、当前状态、熟悉度分布和面试对话记录。
     */
    @GetMapping("/{id}/report")
    public Map<String, Object> report(@PathVariable Long id) {
         return interviewService.getReport(id);
    }
    
    // ========== 接口 5：进入下一轮面试 ==========

    /**
     * 基于已完成且通过的会话，进入下一轮面试（二面 / 三面）。
     * HTTP 请求示例：
     * POST http://localhost:8080/api/session/123/next-stage
     * 返回示例（JSON）：
     * {
     *   "sessionId": 456,
     *   "stage": 2,
     *   "stageName": "二面",
     *   "message": "已进入二面，祝你好运！"
     * }
     * 规则：
     * - 只有当前会话状态为 PASSED 才能进入下一轮
     * - 三面（stage=3）已是最后一轮，不能再进入下一轮
     * - 新会话自动继承上一轮的简历、岗位信息，并根据 stage 切换知识库
     */
    @PostMapping("/{id}/next-stage")
    public Map<String, Object> nextStage(@PathVariable Long id) {
        InterviewSession session = interviewService.prepareNextStage(id);
        int nextStage = session.getInterviewStage();
        String stageName = switch (nextStage) {
            case 2 -> "二面";
            case 3 -> "三面";
            default -> nextStage + "面";
        };
        return Map.of(
                "sessionId", session.getId().toString(),
                "stage", nextStage,
                "stageName", stageName,
                "message", "已进入" + stageName + "，祝你好运！"
        );
    }

    // ========== 接口 6：重新开始（重试本轮） ==========

    /**
     * 重新开始本轮面试（生成一次新的尝试）。
     * HTTP 请求示例：
     * POST http://localhost:8080/api/session/123/retry
     * 返回示例（JSON）：
     * {
     *   "sessionId": 789,
     *   "stage": 1,
     *   "message": "已重新开始一面"
     * }
     */
    @PostMapping("/{id}/retry")
    public Map<String, Object> retry(@PathVariable Long id) {
        InterviewSession session = interviewService.prepareRetry(id);
        int stage = session.getInterviewStage();
        String stageName = switch (stage) {
            case 1 -> "一面";
            case 2 -> "二面";
            case 3 -> "三面";
            default -> stage + "面";
        };
        return Map.of(
                "sessionId", session.getId().toString(),
                "stage", stage,
                "message", "已重新开始" + stageName
        );
    }

    // ========== 接口 7：获取逐题分析 ==========

    /**
     * 获取会话的逐题评估详情
     * HTTP 请求示例：
     * GET http://localhost:8080/api/session/123/assessments
     * 返回示例（JSON）：
     * [
     *   {
     *     "questionContent": "请介绍HashMap的底层实现",
     *     "score": 8.5,
     *     "feedback": "回答完整，理解深入",
     *     "weakPoints": ""
     *   },
     *   ...
     * ]
     */
    @GetMapping("/{id}/assessments")
    public List<Map<String, Object>> getAssessments(@PathVariable Long id) {
        return interviewService.getAssessments(id);
    }
}
