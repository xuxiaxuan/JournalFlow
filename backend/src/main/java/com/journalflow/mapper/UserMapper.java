package com.journalflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.journalflow.entity.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface UserMapper extends BaseMapper<User> {
    @Select("SELECT r.code FROM user_role ur JOIN role r ON r.id = ur.role_id WHERE ur.user_id = #{userId}")
    List<String> selectRoleCodes(@Param("userId") Long userId);
}
