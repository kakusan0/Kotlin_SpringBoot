package com.example.demo.mapper

import com.example.demo.model.UserMenu
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface UserMenuMapper {
    fun selectByUsername(username: String): List<UserMenu>
    fun selectMenuIdsByUsername(username: String): List<Long>
    fun selectByMenuId(menuId: Long): List<UserMenu>
    fun insert(record: UserMenu): Int
    fun deleteByUsernameAndMenuId(@Param("username") username: String, @Param("menuId") menuId: Long): Int
}
