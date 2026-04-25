package com.example.demo.mapper;

import com.example.demo.model.BlacklistEvent;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface BlacklistEventMapper {
    int insert(BlacklistEvent event);

    int insertBulk(List<BlacklistEvent> events);
}
