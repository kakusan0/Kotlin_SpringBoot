package com.example.demo.mapper;

import com.example.demo.model.BlacklistIp;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BlacklistIpMapper {
    int insert(BlacklistIp ip);

    boolean existsByIp(@Param("ipAddress") String ipAddress);

    List<BlacklistIp> getAll();

    int upsertIncrementTimes(@Param("ipAddress") String ipAddress);

    int markDeletedById(@Param("id") Long id);
}
