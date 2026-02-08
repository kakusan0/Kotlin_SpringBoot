package com.example.demo.mapper;

import com.example.demo.model.UaBlacklistRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UaBlacklistRuleMapper {
    List<UaBlacklistRule> selectActive();

    int insert(UaBlacklistRule rule);

    int logicalDelete(@Param("id") Long id);
}
