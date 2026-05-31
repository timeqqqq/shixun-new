package com.campus.qa.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.qa.entity.QueryLog;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface QueryLogMapper extends BaseMapper<QueryLog> {
    @Select("""
        SELECT matched_question_id AS questionId, COUNT(*) AS cnt
        FROM query_log
        WHERE matched_question_id IS NOT NULL
          AND (#{startTime} IS NULL OR query_time >= #{startTime})
        GROUP BY matched_question_id
        ORDER BY cnt DESC
        LIMIT #{limit}
        """)
    List<Map<String, Object>> countHotQuestions(@Param("startTime") LocalDateTime startTime, @Param("limit") int limit);
}
