package com.example.demo.mapper

import com.example.demo.model.RoleMenu
import org.apache.ibatis.annotations.Mapper

@Mapper
interface RoleMenuMapper {
    fun selectMenuIdsByRole(roleName: String): List<Long>
    fun insert(record: RoleMenu): Int
    fun deleteByRoleAndMenu(roleName: String, menuId: Long): Int
}

