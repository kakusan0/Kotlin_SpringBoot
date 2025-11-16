package com.example.demo.service

import com.example.demo.mapper.MenuSettingMapper
import com.example.demo.model.MenuSetting
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class MenuSettingService(
    private val menuSettingMapper: MenuSettingMapper
) {
    fun getAll(): List<MenuSetting> = menuSettingMapper.selectAll()
    fun getByMenuId(menuId: Long): MenuSetting? = menuSettingMapper.selectByMenuId(menuId)

    @Transactional
    fun upsert(record: MenuSetting): Int = menuSettingMapper.upsert(record)
}

