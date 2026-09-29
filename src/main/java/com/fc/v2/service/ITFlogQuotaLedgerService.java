package com.fc.v2.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fc.v2.model.auto.TFlogQuotaLedger;

import java.math.BigDecimal;

/**
 * 编制单位年度采伐额度账 Service接口。
 * 扣过的数与尚存的数必须出自同一回计算，页面自己数的一律不算。
 *
 * @author fuce
 * @date 2026-09-28
 */
public interface ITFlogQuotaLedgerService extends IService<TFlogQuotaLedger> {

    /** 查一家一年的账（含尚存数 remainAmount=total-used，同一行算出来） */
    TFlogQuotaLedger viewLedger(String siteNo, Integer quotaYear);

    /**
     * 年初下达：一家一年立一行总数（同家同年重复下达即覆盖更正，占用数不动）。
     * 不存在账则开一行，used 从 0 起。
     */
    TFlogQuotaLedger grant(String siteNo, Integer quotaYear, java.math.BigDecimal totalAmount);

    /**
     * 从当年额度里划走 qty 立方。越过本年可占面子（或账未下达/已封账）返回 false，
     * 由调用方点名是哪张卡。并发同扣靠数据库条件更新兜底。
     */
    boolean hold(String siteNo, Integer quotaYear, BigDecimal qty);

    /** 卡压死/改版：原样退回 qty 立方。 */
    boolean release(String siteNo, Integer quotaYear, BigDecimal qty);
}
