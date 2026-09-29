package com.fc.v2.mapper.custom;

import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/**
 * 年度额度账原子动账：扣、还都落成一条带条件的 UPDATE，
 * 并发两笔同扣一家时数据库只放一笔过去，从根上护住"扣过的认账"。
 *
 * @author fuce
 * @date 2026-09-28
 */
public interface FlogQuotaDao {

    /**
     * 占用：账上累计 +qty，条件是加完不越过本年可占总数。
     * 影响行数 0 即超额（或账不存在/已封账），整笔立卡回滚。
     */
    int applyHold(@Param("siteNo") String siteNo,
                  @Param("quotaYear") Integer quotaYear,
                  @Param("qty") BigDecimal qty);

    /**
     * 退回：账上累计 -qty，条件是账上确实占着这么多（不会退成负数）。
     */
    int releaseHold(@Param("siteNo") String siteNo,
                    @Param("quotaYear") Integer quotaYear,
                    @Param("qty") BigDecimal qty);
}
