package com.fc.v2.model.custom;

import com.fc.v2.model.auto.TFlogApplyCard;

import java.io.Serializable;

/**
 * 建档卡的另一种送法：卡面草稿 + 本笔办卡人附言。
 * 与直接送卡实体是同名方法 {@code saveCard} 的两种送法，落点同一条保存通道。
 *
 * @author fuce
 * @date 2026-09-28
 */
public class CardDraft implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 卡面各栏 */
    private TFlogApplyCard card;

    /** 本笔办卡人（不填则取登录人；立卡人不由此栏决定，仍钉首次立卡那人） */
    private String operator;

    /** 本笔附言（续记到办卡人记事那一串） */
    private String handlerNote;

    public TFlogApplyCard getCard() {
        return card;
    }

    public void setCard(TFlogApplyCard card) {
        this.card = card;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getHandlerNote() {
        return handlerNote;
    }

    public void setHandlerNote(String handlerNote) {
        this.handlerNote = handlerNote;
    }
}
