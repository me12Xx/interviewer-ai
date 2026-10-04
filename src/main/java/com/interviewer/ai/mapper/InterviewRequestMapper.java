package com.interviewer.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interviewer.ai.InterviewRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface InterviewRequestMapper extends BaseMapper<InterviewRequest> {

    @Select("SELECT * FROM interview_request WHERE session_id = #{sessionId} AND request_id = #{requestId}")
    InterviewRequest selectByRequestId(@Param("sessionId") Long sessionId,
                                       @Param("requestId") String requestId);

    @Update("UPDATE interview_request SET status = 'COMPLETED', response_json = #{responseJson}, "
            + "updated_at = #{updatedAt} WHERE id = #{id} AND status = 'PROCESSING'")
    int markCompleted(@Param("id") Long id,
                      @Param("responseJson") String responseJson,
                      @Param("updatedAt") LocalDateTime updatedAt);

    @Update("UPDATE interview_request SET status = 'FAILED', failure_message = #{failureMessage}, "
            + "expires_at = #{expiresAt}, updated_at = #{updatedAt} "
            + "WHERE id = #{id} AND status = 'PROCESSING'")
    int markFailed(@Param("id") Long id,
                   @Param("failureMessage") String failureMessage,
                   @Param("expiresAt") LocalDateTime expiresAt,
                   @Param("updatedAt") LocalDateTime updatedAt);
}
