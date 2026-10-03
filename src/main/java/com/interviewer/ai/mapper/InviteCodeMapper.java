package com.interviewer.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interviewer.ai.InviteCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface InviteCodeMapper extends BaseMapper<InviteCode> {
    
    @Update("UPDATE invite_code SET is_used = 1, used_by_user_id = #{userId}, used_at = #{usedAt} "
            + "WHERE code = #{code} AND is_used = 0")
    int markUsedIfUnused(
            @Param("code") String code,
            @Param("userId") Long userId,
            @Param("usedAt") LocalDateTime usedAt);
}
