package com.example.demo

import com.example.demo.mapper.AccessLogMapper
import com.example.demo.mapper.BlacklistIpMapper
import com.example.demo.mapper.UaBlacklistRuleMapper
import com.example.demo.mapper.WhitelistIpMapper
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class AdminController(
    private val whitelistIpMapper: WhitelistIpMapper,
    private val blacklistIpMapper: BlacklistIpMapper,
    private val uaBlacklistRuleMapper: UaBlacklistRuleMapper,
    private val accessLogMapper: AccessLogMapper,
) {
    @GetMapping("/manage")
    fun manage(): String {
        return "manage"
    }

    @GetMapping("/manage/ip")
    fun manageIp(model: Model): String {
        val whitelist = whitelistIpMapper.getActive()
        val blacklist = blacklistIpMapper.getAll()
        model.addAttribute("whitelist", whitelist)
        model.addAttribute("blacklist", blacklist)

        // 非nullのipAddressをそのまま重複排除セットへ追加
        val ipSet = mutableSetOf<String>()
        whitelist.forEach { ipSet.add(it.ipAddress) }
        blacklist.forEach { ipSet.add(it.ipAddress) }
        val ips = ipSet.toList()

        if (ips.isNotEmpty()) {
            val latestList = accessLogMapper.selectLatestPathByIps(ips)
            val latestPathMap = latestList.associate { it.ipAddress to it.path }
            val latestUaMap = latestList.associate { it.ipAddress to it.userAgent }
            model.addAttribute("latestPathsByIp", latestPathMap)
            model.addAttribute("latestUserAgentsByIp", latestUaMap)
        } else {
            model.addAttribute("latestPathsByIp", emptyMap<String, String?>())
            model.addAttribute("latestUserAgentsByIp", emptyMap<String, String?>())
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
