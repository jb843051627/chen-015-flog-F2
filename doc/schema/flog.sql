-- flog 林木采伐限额与采伐许可证管理 -- schema (chen-015)
-- 列名与基线实体契约（@TableName/@TableField）逐列对齐，改列必须同步实体。
-- 库：chen_015

CREATE TABLE IF NOT EXISTS t_flog_apply_card (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '伐区采伐申请建档卡号（自动生成，跨版本不变）',
  node_no int DEFAULT NULL COMMENT '本卡版本序（同一卡号自1起逐版加一）',
  site_id bigint DEFAULT NULL COMMENT '所属编制单位在册底档主键',
  site_no varchar(64) DEFAULT NULL COMMENT '所属单位底档字号（以字号认归属，名号不算数）',
  subcomp_no varchar(64) DEFAULT NULL COMMENT '小班号',
  cut_block_no varchar(64) DEFAULT NULL COMMENT '伐区名号',
  cut_mode int DEFAULT NULL COMMENT '采伐方式 1皆伐 2渐伐 3择伐 4抚育采伐 5更新采伐 6低质低效林改造',
  clear_trees int DEFAULT NULL COMMENT '皆伐专属：株数',
  grad_years int DEFAULT NULL COMMENT '渐伐专属：分年次数',
  grad_interval int DEFAULT NULL COMMENT '渐伐专属：间隔年数',
  select_intensity decimal(6,2) DEFAULT NULL COMMENT '择伐专属：择伐强度(百分数)',
  reserve_trees int DEFAULT NULL COMMENT '择伐专属：保留株数',
  tend_method varchar(64) DEFAULT NULL COMMENT '抚育采伐专属：抚育办法',
  tend_per_ha decimal(12,2) DEFAULT NULL COMMENT '抚育采伐专属：每公顷采出量(立方米/公顷)',
  renew_mode varchar(64) DEFAULT NULL COMMENT '更新采伐专属：更新方式',
  renew_finish_year int DEFAULT NULL COMMENT '更新采伐专属：完成年度',
  target_species varchar(128) DEFAULT NULL COMMENT '低质低效林改造专属：目标树种',
  current_flag int DEFAULT NULL COMMENT '版本现行标记 1现行 0历史（列表只摆现行）',
  qty decimal(12,2) DEFAULT NULL COMMENT '本卡合并后拟采蓄积(立方米，零与负数不收)',
  fine_amt decimal(12,2) DEFAULT NULL COMMENT '本卡新报拟采面积(公顷)',
  held_qty decimal(12,2) DEFAULT NULL COMMENT '本版本此刻在年度额度账上占着的立方数（压死/改版退0）',
  card_year int DEFAULT NULL COMMENT '立卡年度（额度账按这一家这一年走）',
  founder varchar(64) DEFAULT NULL COMMENT '立卡人（立卡一刻钉住，重录不挪窝）',
  founder_time datetime DEFAULT NULL COMMENT '立卡时刻',
  handlers varchar(1000) DEFAULT NULL COMMENT '历次办卡人记事（办一笔续一笔，不覆盖）',
  content varchar(255) DEFAULT NULL COMMENT '核验串（随版本序走，重录日子挪到新版那天）',
  grade_level int DEFAULT NULL COMMENT '拟采强度折到的取用顺位 1轻 2中 3重（系统折算回填，不留手填口子）',
  status int DEFAULT NULL COMMENT '进展 0待核对 1已核对 2已压死',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_card_version (bill_no, node_no),
  KEY idx_site_subcomp_current (site_no, subcomp_no, current_flag, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='伐区采伐申请建档卡';

CREATE TABLE IF NOT EXISTS t_flog_close_card (
  id bigint NOT NULL COMMENT '主键',
  biz_no varchar(64) DEFAULT NULL COMMENT '伐区勾销事务卡号',
  stage int DEFAULT NULL COMMENT '当前段次 0..3（报伐/核株/验更/了断）',
  status int DEFAULT NULL COMMENT '卡的落定 0未起 1在办 2已了断',
  content varchar(255) DEFAULT NULL COMMENT '勾销记事',
  last_action varchar(64) DEFAULT NULL COMMENT '最近一次过口动作',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='伐区勾销事务卡';

CREATE TABLE IF NOT EXISTS t_flog_declare_row (
  id bigint NOT NULL COMMENT '主键',
  batch_no varchar(64) DEFAULT NULL COMMENT '伐区申报清册批次号',
  row_no int DEFAULT NULL COMMENT '原清册行次',
  item_code varchar(64) DEFAULT NULL COMMENT '被打回的前置项代号',
  qty decimal(12,2) DEFAULT NULL COMMENT '本行申报拟采蓄积(立方米)',
  status int DEFAULT NULL COMMENT '行落地情形 0待核 1已收 2挂起',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='伐区申报清册核收行';

CREATE TABLE IF NOT EXISTS t_flog_permit_bill (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '采伐许可证核签单号',
  node_no int DEFAULT NULL COMMENT '当前所在级 0..3',
  sign_mode int DEFAULT NULL COMMENT '同级核签办法 0任一人 1两名点齐',
  need_count int DEFAULT NULL COMMENT '本级应签人数',
  sign_count int DEFAULT NULL COMMENT '本级已签人数',
  status int DEFAULT NULL COMMENT '报批情形 0在核 1已核讫 2已打回',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='采伐许可证逐级核签单';

CREATE TABLE IF NOT EXISTS t_flog_quota_task (
  id bigint NOT NULL COMMENT '主键',
  item_no varchar(64) DEFAULT NULL COMMENT '额度事项号',
  due_at datetime DEFAULT NULL COMMENT '本桩事该动手那天的最后一刻',
  amount decimal(12,2) DEFAULT NULL COMMENT '开口前预先提醒的自然日数',
  content varchar(255) DEFAULT NULL COMMENT '事由与受文林场记要',
  status int DEFAULT NULL COMMENT '条目情形 0未开口 1已落地 2办不下去',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='年采伐限额年度执行额度下达与结余收回事项';

-- 年度额度账：一家编制单位一年一行。年初下达一个总数，立卡当场从这一行扣，压死当场退回。
CREATE TABLE IF NOT EXISTS t_flog_quota_ledger (
  id bigint NOT NULL COMMENT '主键',
  site_no varchar(64) NOT NULL COMMENT '编制单位底档字号',
  quota_year int NOT NULL COMMENT '额度年度',
  total_amount decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '年初批下的本年可占总数(立方米)',
  used_amount decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '已扣占用数(立方米，扣/退都落这一格)',
  status int DEFAULT 0 COMMENT '账情形 0执行中 1已封账',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_site_year (site_no, quota_year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='编制单位年度采伐额度账';

-- 跨县协办受托：受托人只摆得出受托的那一家，别场的册不并列。
CREATE TABLE IF NOT EXISTS t_flog_site_trust (
  id bigint NOT NULL COMMENT '主键',
  site_id bigint NOT NULL COMMENT '受托底档主键',
  site_no varchar(64) NOT NULL COMMENT '受托底档字号',
  trustee_id bigint NOT NULL COMMENT '受托人用户id',
  trustee_name varchar(64) DEFAULT NULL COMMENT '受托人账号',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_site_trustee (site_id, trustee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='跨县协办受托关系';

CREATE TABLE IF NOT EXISTS t_flog_stand_doc (
  id bigint NOT NULL COMMENT '主键',
  site_no varchar(64) DEFAULT NULL COMMENT '编制单位在册底档字号（自动生成，手填不收）',
  site_name varchar(128) DEFAULT NULL COMMENT '编制单位全称',
  site_type varchar(32) DEFAULT NULL COMMENT '经营类别 国有林场/集体林场/个人承包山',
  road_name varchar(128) DEFAULT NULL COMMENT '管辖林业站(省—市—县)',
  station_dept_id bigint DEFAULT NULL COMMENT '管辖林业站对应部门id（本站可见圈界用；空则按road_name兜）',
  th1_max decimal(12,2) DEFAULT NULL COMMENT '单卡拟采蓄积轻度线上限(立方米)',
  th2_max decimal(12,2) DEFAULT NULL COMMENT '中度线上限(立方米，与线一样高落重度)',
  th3_max decimal(12,2) DEFAULT NULL COMMENT '重度线上限(立方米，越过此线仍归重度)',
  status int DEFAULT NULL COMMENT '底档情形 0在册 1已收回（收回后不接纳新卡，旧卡仍可翻）',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_site_no (site_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='编制单位在册底档';

CREATE TABLE IF NOT EXISTS t_flog_yield_rule (
  id bigint NOT NULL COMMENT '主键',
  rule_code varchar(64) DEFAULT NULL COMMENT '可采强度界限值代号',
  rule_name varchar(128) DEFAULT NULL COMMENT '线名',
  th1_max decimal(12,2) DEFAULT NULL COMMENT '轻度线上限(立方米/公顷)',
  th2_max decimal(12,2) DEFAULT NULL COMMENT '中度线上限(立方米/公顷)',
  th3_max decimal(12,2) DEFAULT NULL COMMENT '重度线上限(立方米/公顷)',
  eff_start datetime DEFAULT NULL COMMENT '启用之日',
  eff_end datetime DEFAULT NULL COMMENT '交棒之日(不含)',
  priority int DEFAULT NULL COMMENT '抢线顺位(数值越大越先开口)',
  status int DEFAULT NULL COMMENT '线的情形 0现行 1已让位',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='可采强度界限值线';

-- 初始底档数据（在册、已收回两类各一条）
-- t_flog_stand_doc 两条种子底档：id=0 在册、id=1 已收回。
-- 单卡拟采蓄积三线上限取 40/120/300 立方米（挨着界往高里数：等于中度线落重度，越过重度线仍归重度）。
INSERT IGNORE INTO t_flog_stand_doc (id, site_no, site_name, site_type, road_name, station_dept_id, th1_max, th2_max, th3_max, status, del_flag, create_by, create_time)
VALUES (0, 'SD20260000', '青屏县编制单位在册底档（青屏国有林场，在册）', '国有林场', '青屏省—青屏市—青屏县', NULL, 40.00, 120.00, 300.00, 0, 0, 'seed', NOW()),
       (1, 'SD20260001', '青屏县编制单位在册底档（白石集体林场，已收回）', '集体林场', '青屏省—青屏市—青屏县', NULL, 40.00, 120.00, 300.00, 1, 0, 'seed', NOW());

-- 2026 年度额度账种子：在册那家下达 500 立方米；已收回那家虽有账，新卡也挂不进去。
INSERT IGNORE INTO t_flog_quota_ledger (id, site_no, quota_year, total_amount, used_amount, status, del_flag, create_by, create_time)
VALUES (0, 'SD20260000', 2026, 500.00, 0.00, 0, 0, 'seed', NOW()),
       (1, 'SD20260001', 2026, 500.00, 0.00, 0, 0, 'seed', NOW());
