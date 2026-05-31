package com.campus.qa.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.qa.entity.Question;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QuestionMapper extends BaseMapper<Question> {
}
