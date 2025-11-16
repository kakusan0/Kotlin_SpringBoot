package com.example.demo.service

import com.example.demo.mapper.UserMenuMapper
import com.example.demo.model.UserMenu
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class UserMenuService(
    private val userMenuMapper: UserMenuMapper
) {
    fun getByUsername(username: String): List<UserMenu> = userMenuMapper.selectByUsername(username)

    fun getMenuIdsByUsername(username: String): List<Long> = userMenuMapper.selectMenuIdsByUsername(username)

    fun getByMenuId(menuId: Long): List<UserMenu> = userMenuMapper.selectByMenuId(menuId)

    @Transactional
    fun insert(record: UserMenu): Int = userMenuMapper.insert(record)

    @Transactional
    fun deleteByUsernameAndMenuId(username: String, menuId: Long): Int =
        userMenuMapper.deleteByUsernameAndMenuId(username, menuId)
}
