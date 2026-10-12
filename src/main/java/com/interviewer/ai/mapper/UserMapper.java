package com.interviewer.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interviewer.ai.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    
    @Select("SELECT * FROM user WHERE access_token = #{token} AND is_active = 1 "
            + "AND (access_expires_at IS NULL OR access_expires_at > NOW())")
    User selectByToken(String token);

    @Update("UPDATE `user` u JOIN invite_code i ON i.used_by_user_id = u.id "
            + "SET u.access_expires_at = CASE WHEN i.access_days IS NULL THEN NULL "
            + "ELSE DATE_ADD(u.created_at, INTERVAL i.access_days DAY) END "
            + "WHERE u.id = #{id}")
    int setAccessExpiryFromInvite(@Param("id") Long id);
}
