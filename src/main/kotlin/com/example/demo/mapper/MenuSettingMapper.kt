package com.example.demo.mapper

import com.example.demo.model.MenuSetting
import org.apache.ibatis.annotations.Mapper

@Mapper
interface MenuSettingMapper {
    fun selectAll(): List<MenuSetting>
    fun selectByMenuId(menuId: Long): MenuSetting?
    fun upsert(record: MenuSetting): Int
}

