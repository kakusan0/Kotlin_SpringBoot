package com.example.demo.mapper;

import com.example.demo.model.BlacklistEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BlacklistEventMapper {
    int insert(BlacklistEvent event);
}
