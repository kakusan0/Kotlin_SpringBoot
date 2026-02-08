package com.example.demo.mapper;

import com.example.demo.model.WhitelistIp;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WhitelistIpMapper {
    int insert(WhitelistIp ip);

    boolean existsByIp(@Param("ipAddress") String ipAddress);

    List<WhitelistIp> getAll();

    List<WhitelistIp> getActive();

    int markBlacklistedAndIncrement(@Param("ipAddress") String ipAddress);
}
