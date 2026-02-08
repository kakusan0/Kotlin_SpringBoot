package com.example.demo.mapper;

import com.example.demo.model.ReportJob;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface ReportJobMapper {
    int insert(ReportJob job);

    ReportJob selectById(Long id);

    int updateStatus(Map<String, Object> params);

    List<ReportJob> selectPending();
}
