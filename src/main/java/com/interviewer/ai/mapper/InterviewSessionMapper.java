package com.interviewer.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interviewer.ai.InterviewSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface InterviewSessionMapper extends BaseMapper<InterviewSession> {

    @Update("UPDATE interview_session SET lease_owner = #{owner}, lease_expires_at = #{expiresAt} "
            + "WHERE id = #{sessionId} AND status = 'IN_PROGRESS' "
            + "AND (lease_owner IS NULL OR lease_expires_at < #{now} OR lease_owner = #{owner})")
    int acquireLease(@Param("sessionId") Long sessionId,
                     @Param("owner") String owner,
                     @Param("now") LocalDateTime now,
                     @Param("expiresAt") LocalDateTime expiresAt);

    @Update("UPDATE interview_session SET lease_owner = NULL, lease_expires_at = NULL "
            + "WHERE id = #{sessionId} AND lease_owner = #{owner}")
    int releaseLease(@Param("sessionId") Long sessionId, @Param("owner") String owner);
}
