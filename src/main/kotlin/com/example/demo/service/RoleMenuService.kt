package com.example.demo.service

import com.example.demo.mapper.RoleMenuMapper
import com.example.demo.model.RoleMenu
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class RoleMenuService(
    private val roleMenuMapper: RoleMenuMapper
) {
    fun getMenuIdsByRole(roleName: String): List<Long> = roleMenuMapper.selectMenuIdsByRole(roleName)

    @Transactional
    fun insert(record: RoleMenu): Int = roleMenuMapper.insert(record)

    @Transactional
    fun deleteByRoleAndMenu(roleName: String, menuId: Long): Int = roleMenuMapper.deleteByRoleAndMenu(roleName, menuId)
}

