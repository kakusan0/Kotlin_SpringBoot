package com.example.demo

import com.example.demo.mapper.BlacklistIpMapper
import com.example.demo.mapper.WhitelistIpMapper
import com.example.demo.service.ContentItemService
import com.example.demo.service.MenuService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class AdminController(
    private val contentItemService: ContentItemService,
    private val menuService: MenuService,
    private val whitelistIpMapper: WhitelistIpMapper,
    private val blacklistIpMapper: BlacklistIpMapper
) {
    @GetMapping("/manage")
    fun manage(model: Model): String {
        model.apply {
            addAttribute("screens", contentItemService.getAll())
            addAttribute("menus", menuService.getAll())
            addAttribute("whitelist", whitelistIpMapper.getAll())
            addAttribute("blacklist", blacklistIpMapper.getAll())
        }
        return "manage"
    }
}
