package com.fc.v2.model.custom;

import com.fc.v2.model.auto.TFlogApplyCard;
import com.fc.v2.model.auto.TFlogQuotaLedger;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 建档卡保存方法的给回。
 * 折到第几级、核验串、卡号版本序、从当年额度里划走多少立方全在这一份里；
 * 扣过的数与尚存的数同屏且出自同一回计算，页面不许自己数。
 *
 * @author fuce
 * @date 2026-09-28
 */
public class ApplyCardReceipt implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 成形后的卡（最新现行版） */
    private TFlogApplyCard card;

    /** 额度年度 */
    private Integer quotaYear;

    /** 年初批下的本年可占总数 */
    private BigDecimal totalAmount;

    /** 本笔扣过之后账上累计占用数 */
    private BigDecimal usedAmount;

    /** 本笔扣过之后尚存数（total-used，同一回计算） */
    private BigDecimal remainAmount;

    public ApplyCardReceipt() {
    }

    public ApplyCardReceipt(TFlogApplyCard card, TFlogQuotaLedger ledger) {
        this.card = card;
        if (ledger != null) {
            this.quotaYear = ledger.getQuotaYear();
            this.totalAmount = ledger.getTotalAmount();
            this.usedAmount = ledger.getUsedAmount();
            this.remainAmount = ledger.getRemainAmount();
        }
    }

    public TFlogApplyCard getCard() {
        return card;
    }

    public void setCard(TFlogApplyCard card) {
        this.card = card;
    }

    public Integer getQuotaYear() {
        return quotaYear;
    }

    public void setQuotaYear(Integer quotaYear) {
        this.quotaYear = quotaYear;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public BigDecimal getUsedAmount() {
        return usedAmount;
    }

    public void setUsedAmount(BigDecimal usedAmount) {
        this.usedAmount = usedAmount;
    }

    public BigDecimal getRemainAmount() {
        return remainAmount;
    }

    public void setRemainAmount(BigDecimal remainAmount) {
        this.remainAmount = remainAmount;
    }
}
