package com.fc.v2.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fc.v2.model.auto.TFlogStandDoc;

import java.util.List;

/**
 * 编制单位在册底档 Service接口。
 * 一档只管一家；底档字号自动生成，人往里塞自拟的号，库里不给存。
 *
 * @author fuce
 * @date 2026-09-28
 */
public interface ITFlogStandDocService extends IService<TFlogStandDoc> {

    /** 按主键查 */
    TFlogStandDoc selectById(Long id);

    /** 底档列表 */
    List<TFlogStandDoc> selectList();

    /** 建档：字号由系统生成，入参里带的字号一律丢弃 */
    int createDoc(TFlogStandDoc record);

    /** 改档：字号不挪、不重排 */
    int updateDoc(TFlogStandDoc record);

    /** 年初收回：从此不接纳新卡，早年旧卡仍翻得到 */
    int revoke(Long id);
}
