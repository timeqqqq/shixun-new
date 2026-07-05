CREATE DATABASE IF NOT EXISTS campus_qa
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;

USE campus_qa;

CREATE TABLE IF NOT EXISTS question (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  question VARCHAR(255) NOT NULL,
  answer TEXT NOT NULL,
  category VARCHAR(64) DEFAULT NULL,
  source VARCHAR(64) DEFAULT NULL,
  hit_count BIGINT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_pinned TINYINT NOT NULL DEFAULT 0,
  pinned_order INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_question (question),
  KEY idx_category (category),
  KEY idx_hit_count (hit_count),
  KEY idx_pinned_order (is_pinned, pinned_order)
);

CREATE TABLE IF NOT EXISTS question_embedding (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  question_id BIGINT NOT NULL,
  embedding_model VARCHAR(128) NOT NULL,
  vector_json LONGTEXT NOT NULL,
  content_hash VARCHAR(64) NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_question_id (question_id),
  KEY idx_content_hash (content_hash)
);

CREATE TABLE IF NOT EXISTS contribution (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  question VARCHAR(255) NOT NULL,
  answer TEXT NOT NULL,
  category VARCHAR(64) DEFAULT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'pending',
  submit_ip VARCHAR(64) NOT NULL,
  submit_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  audit_time DATETIME DEFAULT NULL,
  reject_reason VARCHAR(255) DEFAULT NULL,
  KEY idx_status_submit_time (status, submit_time),
  KEY idx_submit_ip_time (submit_ip, submit_time)
);

CREATE TABLE IF NOT EXISTS crawl_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_name VARCHAR(128) NOT NULL,
  target_url VARCHAR(500) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'pending',
  total_found INT NOT NULL DEFAULT 0,
  total_inserted INT NOT NULL DEFAULT 0,
  start_time DATETIME DEFAULT NULL,
  end_time DATETIME DEFAULT NULL,
  error_message VARCHAR(500) DEFAULT NULL,
  KEY idx_status_start_time (status, start_time)
);

CREATE TABLE IF NOT EXISTS query_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  keyword VARCHAR(255) NOT NULL,
  matched_question_id BIGINT DEFAULT NULL,
  user_ip VARCHAR(64) NOT NULL,
  query_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_keyword (keyword),
  KEY idx_query_time (query_time),
  KEY idx_matched_question_id (matched_question_id),
  KEY idx_user_ip_time (user_ip, query_time)
);

CREATE TABLE IF NOT EXISTS sensitive_word (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  word VARCHAR(64) NOT NULL,
  level VARCHAR(16) NOT NULL DEFAULT 'high',
  enabled TINYINT NOT NULL DEFAULT 1,
  source VARCHAR(64) DEFAULT 'manual',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_word (word),
  KEY idx_enabled_level (enabled, level)
);

DELETE FROM question
WHERE question IN (
'食堂几点关门','早餐几点开始','夜宵还有吗','饭卡丢了怎么办','食堂可以打包吗',
'校医院在哪里','校医院几点开门','医保怎么报销','感冒可以去校医院吗','校医院可以开药吗',
'挂号怎么操作','门诊需要带什么','体检在哪里做','校医院能看牙吗','发烧晚上怎么办',
'图书馆几点开门','图书馆能自习吗','图书馆可以借书吗','借的书逾期了怎么办','图书馆有打印吗',
'图书馆能带水吗','自习室怎么找','图书馆周末开吗','图书馆有空调吗','图书馆座位怎么占',
'宿舍报修怎么操作','空调坏了怎么报修','宿舍停电怎么办','热水器坏了找谁','门锁坏了怎么办',
'厕所堵了怎么报修','洗衣机故障怎么办','宿舍晚上几点关门','宿舍能用大功率电器吗','宿舍换宿舍怎么申请',
'在哪里打印','可以复印身份证吗','论文装订去哪里','自助打印怎么用','黑白打印多少钱',
'校车时刻表怎么看','校车几点发车','校车经过哪些站点','校园里能骑电动车吗','停车怎么申请',
'校园网怎么连接','校园网密码忘了怎么办','宿舍没网怎么办','无线网连不上怎么处理','怎么开通校园网',
'选课什么时候开始','成绩在哪里查','补考怎么申请','考试冲突怎么办','课程表在哪里看',
'请假流程是什么','奖学金怎么申请','助学金什么时候评','贫困认定怎么做','辅导员怎么联系',
'社团怎么报名','志愿活动怎么参加','运动会什么时候开','讲座信息在哪里看','校园活动能加学分吗',
'快递点在哪里','快递晚上能取吗','超市几点关门','理发店在哪里','热水供应时间',
'学校标识','校徽','学校全称是什么','学校简称是什么','校训是什么',
'学校校庆是哪天','校名英文怎么写','logo是什么','学校标志长什么样','校名图片',
'怎么联系管理员','热点推荐有什么用','搜索不到怎么办','管理员能做什么','用户可以提交问题吗',
'系统会记录搜索吗','为什么有热点排行榜','搜索为什么会更快','系统支持模糊匹配吗','系统支持图片答案吗',
'校园卡在哪里补办','校园卡可以提现吗','校园卡怎么充值','校园卡密码忘了怎么办',
'补办学生证怎么办','毕业手续怎么办','离校流程是什么','转专业什么时候申请','缓考怎么申请',
'课堂请假需要找谁','学费怎么交','发票怎么开','奖学金发到哪里','报销票据丢了怎么办',
'操场晚上开放吗','健身房怎么预约','游泳馆开放吗','羽毛球场怎么订'
);


INSERT INTO question
(question, answer, category, source, hit_count, create_time, is_pinned, pinned_order)
VALUES
('食堂几点关门','学生食堂工作日通常营业至 21:00，周末通常营业至 20:30。若遇节假日安排调整，以现场公告为准。','餐饮服务','demo100',0,NOW(),0,0),
('校医院在哪里','校医院位于校园生活服务区附近，提供门诊、基础检查、医保咨询等服务。','医疗服务','demo100',0,NOW(),0,0),
('医保怎么报销','学生就医后可按学校医保流程提交报销材料，一般包括票据、病历和相关证明，具体要求以医保通知为准。','医保服务','demo100',0,NOW(),0,0),
('宿舍报修怎么操作','宿舍设施损坏后，可通过宿舍管理或后勤报修渠道提交问题，填写宿舍号、故障现象和联系方式。','后勤服务','demo100',0,NOW(),0,0),
('图书馆能自习吗','图书馆提供阅览和自习空间，进入后应保持安静，遵守座位管理和文明使用规定。','图书馆服务','demo100',0,NOW(),0,0),
('在哪里打印','校园内常见打印需求可在文印店、自助打印点或部分教学楼周边服务点解决。','学习服务','demo100',0,NOW(),0,0),
('校车时刻表怎么看','可通过学校发布的班车或校车通知查看运行线路和发车时间。','交通服务','demo100',0,NOW(),0,0),
('学校标识','IMAGE::https://www.scut.edu.cn/__local/F/5D/79/6E1E7537AC7A6748D5A71CFB194_57F0C6AA_12729.png
图片说明：学校标识页面中的校名图片。','校园文化','demo100',0,NOW(),0,0),
('校徽','IMAGE::https://www.scut.edu.cn/__local/1/58/50/125EE818B82E6C9B8FA74D9953D_66236132_14A27.png
图片说明：学校标识页面中的校徽图片。','校园文化','demo100',0,NOW(),0,0),
('学校全称是什么','学校全称为华南理工大学，英文名称为 South China University of Technology。','校园文化','demo100',0,NOW(),0,0),
('热点推荐有什么用','热点推荐会根据高频查询词生成排行榜，便于用户快速查看大家最关心的问题。','系统使用','demo100',0,NOW(),0,0),
('搜索不到怎么办','如果搜索不到合适答案，可以点击补充问题，提交新的问答内容等待审核。','系统使用','demo100',0,NOW(),0,0),
('管理员能做什么','管理员可以进行 Excel 导入、网站采集、贡献审核、热点管理和敏感词管理。','系统使用','demo100',0,NOW(),0,0),
('系统支持模糊匹配吗','支持，系统对部分高频词加入了同义词扩展和模糊匹配，提高搜索命中率。','系统使用','demo100',0,NOW(),0,0),
('系统支持图片答案吗','支持，像校徽、学校标识这类内容可以直接以图片形式展示在搜索结果中。','系统使用','demo100',0,NOW(),0,0);

DELETE FROM question WHERE source = 'demo100';