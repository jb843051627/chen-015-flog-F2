package com.fc.v2.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.mapper.auto.TFlogQuotaLedgerMapper;
import com.fc.v2.mapper.custom.FlogQuotaDao;
import com.fc.v2.model.auto.TFlogQuotaLedger;
import com.fc.v2.service.ITFlogQuotaLedgerService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;

/**
 * 编制单位年度采伐额度账 Service业务层处理。
 *
 * @author fuce
 * @date 2026-09-28
 */
@Service
public class TFlogQuotaLedgerServiceImpl
        extends ServiceImpl<TFlogQuotaLedgerMapper, TFlogQuotaLedger>
        implements ITFlogQuotaLedgerService {

    @Resource
    private FlogQuotaDao flogQuotaDao;

    @Override
    public TFlogQuotaLedger viewLedger(String siteNo, Integer quotaYear) {
        TFlogQuotaLedger ledger = load(siteNo, quotaYear);
        return fillRemain(ledger);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public TFlogQuotaLedger grant(String siteNo, Integer quotaYear, BigDecimal totalAmount) {
        if (siteNo == null || siteNo.trim().isEmpty() || quotaYear == null) {
            throw new com.fc.v2.common.exception.flog.FlogCardRejectException("下达额度缺底档字号或年度");
        }
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new com.fc.v2.common.exception.flog.FlogCardRejectException("下达的本年可占总数不能为负");
        }
        TFlogQuotaLedger ledger = load(siteNo, quotaYear);
        if (ledger == null) {
            ledger = new TFlogQuotaLedger();
            ledger.setSiteNo(siteNo);
            ledger.setQuotaYear(quotaYear);
            ledger.setTotalAmount(totalAmount);
            ledger.setUsedAmount(BigDecimal.ZERO);
            ledger.setStatus(0);
            ledger.setDelFlag(0);
            this.baseMapper.insert(ledger);
        } else {
            if (totalAmount.compareTo(ledger.getUsedAmount() == null
                    ? BigDecimal.ZERO : ledger.getUsedAmount()) < 0) {
                throw new com.fc.v2.common.exception.flog.FlogCardRejectException(
                        "下达总数 " + totalAmount.toPlainString() + " 低于已占用数，收不下来");
            }
            TFlogQuotaLedger upd = new TFlogQuotaLedger();
            upd.setId(ledger.getId());
            upd.setTotalAmount(totalAmount);
            this.baseMapper.updateById(upd);
            ledger.setTotalAmount(totalAmount);
        }
        return fillRemain(ledger);
    }

    @Override
    public boolean hold(String siteNo, Integer quotaYear, BigDecimal qty) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        return flogQuotaDao.applyHold(siteNo, quotaYear, qty) > 0;
    }

    @Override
    public boolean release(String siteNo, Integer quotaYear, BigDecimal qty) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        return flogQuotaDao.releaseHold(siteNo, quotaYear, qty) > 0;
    }

    private TFlogQuotaLedger load(String siteNo, Integer quotaYear) {
        return this.baseMapper.selectOne(new QueryWrapper<TFlogQuotaLedger>()
                .eq("site_no", siteNo)
                .eq("quota_year", quotaYear)
                .eq("del_flag", 0));
    }

    /** 尚存数与已扣数同一回算定，屏上两个数必须来自这里 */
    private TFlogQuotaLedger fillRemain(TFlogQuotaLedger ledger) {
        if (ledger == null) {
            return null;
        }
        BigDecimal used = ledger.getUsedAmount() == null ? BigDecimal.ZERO : ledger.getUsedAmount();
        BigDecimal total = ledger.getTotalAmount() == null ? BigDecimal.ZERO : ledger.getTotalAmount();
        ledger.setRemainAmount(total.subtract(used));
        return ledger;
    }
}
