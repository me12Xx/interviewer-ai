# 项目地图

## 一、Java 源码

- src/main/java/com/interviewer/ai/Assessment.java (33 行)
- src/main/java/com/interviewer/ai/ChatMessage.java (34 行)
- src/main/java/com/interviewer/ai/InterviewerAiApplication.java (14 行)
- src/main/java/com/interviewer/ai/InterviewSession.java (43 行)
- src/main/java/com/interviewer/ai/InviteCode.java (25 行)
- src/main/java/com/interviewer/ai/Question.java (30 行)
- src/main/java/com/interviewer/ai/User.java (24 行)
- src/main/java/com/interviewer/ai/auth/AuthInterceptor.java (63 行)
- src/main/java/com/interviewer/ai/auth/UserContext.java (25 行)
- src/main/java/com/interviewer/ai/client/AiClient.java (177 行)
- src/main/java/com/interviewer/ai/client/dto/AiMessage.java (57 行)
- src/main/java/com/interviewer/ai/client/dto/ChatRequest.java (41 行)
- src/main/java/com/interviewer/ai/client/dto/ChatResponse.java (66 行)
- src/main/java/com/interviewer/ai/client/dto/Tool.java (36 行)
- src/main/java/com/interviewer/ai/config/AiConfig.java (34 行)
- src/main/java/com/interviewer/ai/config/InterviewConstants.java (39 行)
- src/main/java/com/interviewer/ai/config/PromptLoader.java (46 行)
- src/main/java/com/interviewer/ai/config/WebClientConfig.java (34 行)
- src/main/java/com/interviewer/ai/config/WebConfig.java (22 行)
- src/main/java/com/interviewer/ai/controller/AdminController.java (132 行)
- src/main/java/com/interviewer/ai/controller/InterviewController.java (235 行)
- src/main/java/com/interviewer/ai/controller/RegisterController.java (84 行)
- src/main/java/com/interviewer/ai/exception/AiServiceException.java (16 行)
- src/main/java/com/interviewer/ai/exception/ApiException.java (20 行)
- src/main/java/com/interviewer/ai/exception/ErrorCode.java (29 行)
- src/main/java/com/interviewer/ai/exception/GlobalExceptionHandler.java (68 行)
- src/main/java/com/interviewer/ai/mapper/AssessmentMapper.java (9 行)
- src/main/java/com/interviewer/ai/mapper/ChatMessageMapper.java (9 行)
- src/main/java/com/interviewer/ai/mapper/InterviewSessionMapper.java (9 行)
- src/main/java/com/interviewer/ai/mapper/InviteCodeMapper.java (20 行)
- src/main/java/com/interviewer/ai/mapper/QuestionMapper.java (9 行)
- src/main/java/com/interviewer/ai/mapper/UserMapper.java (13 行)
- src/main/java/com/interviewer/ai/rag/DocumentChunk.java (34 行)
- src/main/java/com/interviewer/ai/rag/DocumentSplitter.java (95 行)
- src/main/java/com/interviewer/ai/rag/EmbeddingService.java (127 行)
- src/main/java/com/interviewer/ai/rag/KnowledgeBaseInitializer.java (98 行)
- src/main/java/com/interviewer/ai/rag/KnowledgeBaseStorage.java (44 行)
- src/main/java/com/interviewer/ai/rag/RagService.java (240 行)
- src/main/java/com/interviewer/ai/service/InterviewService.java (64 行)
- src/main/java/com/interviewer/ai/service/TypeSafeClient.java (172 行)
- src/main/java/com/interviewer/ai/service/impl/InterviewServiceImpl.java (1241 行)

## 二、关键资源文件

- src/main/resources/application.yml
- src/main/resources/prompt/common.md
- src/main/resources/prompt/stage1.md
- src/main/resources/prompt/stage2.md
- src/main/resources/prompt/stage3.md
- src/main/resources/prompt/position-java.md
- src/main/resources/prompt/position-ai.md
- pom.xml
