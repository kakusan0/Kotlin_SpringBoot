package com.example.demo.mapper;

import com.example.demo.model.AccessLog;
import com.example.demo.model.IpLatestPath;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AccessLogMapper {
    int insert(AccessLog record);

    List<IpLatestPath> selectLatestPathByIps(@Param("ips") List<String> ips);

    List<String> selectIpsWithMissingUserAgent();

    List<String> selectIpsByUserAgentPattern(
            @Param("pattern") String pattern,
            @Param("matchType") String matchType
    );
}
