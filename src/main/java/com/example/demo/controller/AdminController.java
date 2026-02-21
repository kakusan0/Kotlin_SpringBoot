package com.example.demo.controller;

import com.example.demo.mapper.AccessLogMapper;
import com.example.demo.mapper.BlacklistIpMapper;
import com.example.demo.mapper.UaBlacklistRuleMapper;
import com.example.demo.mapper.WhitelistIpMapper;
import com.example.demo.model.BlacklistIp;
import com.example.demo.model.IpLatestPath;
import com.example.demo.model.WhitelistIp;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.*;
import java.util.stream.Collectors;

@Controller
public class AdminController {

    private final WhitelistIpMapper whitelistIpMapper;
    private final BlacklistIpMapper blacklistIpMapper;
    private final UaBlacklistRuleMapper uaBlacklistRuleMapper;
    private final AccessLogMapper accessLogMapper;

    public AdminController(
            WhitelistIpMapper whitelistIpMapper,
            BlacklistIpMapper blacklistIpMapper,
            UaBlacklistRuleMapper uaBlacklistRuleMapper,
            AccessLogMapper accessLogMapper
    ) {
        this.whitelistIpMapper = whitelistIpMapper;
        this.blacklistIpMapper = blacklistIpMapper;
        this.uaBlacklistRuleMapper = uaBlacklistRuleMapper;
        this.accessLogMapper = accessLogMapper;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/manage")
    public String manage() {
        return "manage";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/manage/ip")
    public String manageIp(Model model) {
        List<WhitelistIp> whitelist = whitelistIpMapper.getActive();
        List<BlacklistIp> blacklist = blacklistIpMapper.getAll();
        model.addAttribute("whitelist", whitelist);
        model.addAttribute("blacklist", blacklist);

        Set<String> ipSet = new HashSet<>();
        for (WhitelistIp ip : whitelist) {
            if (ip.getIpAddress() != null) ipSet.add(ip.getIpAddress());
        }
        for (BlacklistIp ip : blacklist) {
            if (ip.getIpAddress() != null) ipSet.add(ip.getIpAddress());
        }
        List<String> ips = ipSet.stream().toList();

        if (!ips.isEmpty()) {
            List<IpLatestPath> latestList = accessLogMapper.selectLatestPathByIps(ips);
            Map<String, String> latestPathMap = latestList.stream()
                    .collect(Collectors.toMap(IpLatestPath::getIpAddress, IpLatestPath::getPath, (a, b) -> a));
            Map<String, String> latestUaMap = latestList.stream()
                    .collect(Collectors.toMap(IpLatestPath::getIpAddress, IpLatestPath::getUserAgent, (a, b) -> a));
            model.addAttribute("latestPathsByIp", latestPathMap);
            model.addAttribute("latestUserAgentsByIp", latestUaMap);
        } else {
            model.addAttribute("latestPathsByIp", new HashMap<String, String>());
            model.addAttribute("latestUserAgentsByIp", new HashMap<String, String>());
        }
        return "manage_ip";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/manage/ua")
    public String manageUa(Model model) {
        model.addAttribute("uaRules", uaBlacklistRuleMapper.selectActive());
        return "manage_ua";
    }
}
