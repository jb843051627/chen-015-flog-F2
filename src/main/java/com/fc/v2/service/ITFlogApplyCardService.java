package com.fc.v2.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TFlogApplyCard;
import com.fc.v2.model.custom.ApplyCardReceipt;
import com.fc.v2.model.custom.CardDraft;

import java.util.List;

/**
 * 伐区采伐申请建档卡 Service接口。
 * 卡立不立得起来，业务服务层这一条保存方法说了算：必填栏由它照采伐方式当场点，
 * 顺位、核验串、额度扣划全取它的给回，界面没有第二条通道躲得过它。
 *
 * @author fuce
 * @date 2026-09-12
 */
public interface ITFlogApplyCardService {

    /**
     * 唯一保存通道：首次立卡 / 同一家同一小班二次上报并版，都走这一条。
     * 入参仍是原来的卡实体，老调用方原样能调。
     *
     * @return 成形卡面 + 同一回计算的额度账给回
     */
    ApplyCardReceipt saveCard(TFlogApplyCard record);

    /**
     * 另一种送法：草稿（卡面 + 办卡人附言）叠一个同名方法，落点仍是同一条保存通道。
     */
    ApplyCardReceipt saveCard(CardDraft draft);

    /** 卡压死：占用额度原样退回，成了形的卡覆不掉 */
    int pressCard(Long id);

    /** 现行卡列表（只摆最新一版，自动按当前人圈界：本站/上级全辖/跨县受托） */
    List<TFlogApplyCard> selectTFlogApplyCardList(Wrapper<TFlogApplyCard> queryWrapper);

    /** 一张卡各版本翻检（旧版另存一层，不与现行并列上列表） */
    List<TFlogApplyCard> listVersions(String billNo);

    /** 按主键查询 */
    TFlogApplyCard selectTFlogApplyCardById(Long id);

    /** 新增（旧入参通道，内部仍走唯一保存方法；立不住照规矩点名退回） */
    int insertTFlogApplyCard(TFlogApplyCard record);

    /** 修改（旧入参通道，改版即并版，不容覆写旧行） */
    int updateTFlogApplyCard(TFlogApplyCard record);

    /** 批量删除（已压死的卡覆不掉） */
    int deleteTFlogApplyCardByIds(String ids);

    /** 按主键删除（已压死的卡覆不掉） */
    int deleteTFlogApplyCardById(Long id);
}
