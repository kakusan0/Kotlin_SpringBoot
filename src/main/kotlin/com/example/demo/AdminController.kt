package com.example.demo

import com.example.demo.mapper.BlacklistIpMapper
import com.example.demo.mapper.WhitelistIpMapper
import com.example.demo.mapper.UaBlacklistRuleMapper
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
    private val blacklistIpMapper: BlacklistIpMapper,
    private val uaBlacklistRuleMapper: UaBlacklistRuleMapper,
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

    @GetMapping("/manage/ip")
    fun manageIp(model: Model): String {
        model.apply {
            addAttribute("whitelist", whitelistIpMapper.getAll())
            addAttribute("blacklist", blacklistIpMapper.getAll())
        }
        return "manage_ip"
    }

    @GetMapping("/manage/ua")
    fun manageUa(model: Model): String {
        // サーバーサイド描画のフォールバック用に一覧を渡す（JSが失敗しても最低限表示）
        model.addAttribute("uaRules", uaBlacklistRuleMapper.selectActive())
        return "manage_ua"
    }
}
