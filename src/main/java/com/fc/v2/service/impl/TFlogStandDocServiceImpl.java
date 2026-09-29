package com.fc.v2.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.exception.flog.FlogCardRejectException;
import com.fc.v2.mapper.auto.TFlogStandDocMapper;
import com.fc.v2.model.auto.TFlogStandDoc;
import com.fc.v2.service.ITFlogStandDocService;
import com.fc.v2.util.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * 编制单位在册底档 Service业务层处理。
 *
 * @author fuce
 * @date 2026-09-28
 */
@Service
public class TFlogStandDocServiceImpl
        extends ServiceImpl<TFlogStandDocMapper, TFlogStandDoc>
        implements ITFlogStandDocService {

    /** 在册 */
    private static final int STATUS_ACTIVE = 0;
    /** 年初已收回 */
    private static final int STATUS_REVOKED = 1;

    @Override
    public TFlogStandDoc selectById(Long id) {
        return this.baseMapper.selectById(id);
    }

    @Override
    public List<TFlogStandDoc> selectList() {
        return this.baseMapper.selectList(new QueryWrapper<TFlogStandDoc>()
                .eq("del_flag", 0)
                .orderByAsc("site_no"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int createDoc(TFlogStandDoc record) {
        if (record == null) {
            throw new FlogCardRejectException("底档卡面为空，无档可建");
        }
        if (StringUtils.isEmpty(record.getSiteName())) {
            throw new FlogCardRejectException("缺“编制单位全称”这一格，底档立不住");
        }
        if (StringUtils.isEmpty(record.getSiteType())) {
            throw new FlogCardRejectException("缺“经营类别”这一格（国有林场/集体林场/个人承包山），底档立不住");
        }
        if (StringUtils.isEmpty(record.getRoadName())) {
            throw new FlogCardRejectException("缺“管辖林业站（省—市—县）”这一格，底档立不住");
        }
        if (record.getTh1Max() == null || record.getTh2Max() == null || record.getTh3Max() == null) {
            throw new FlogCardRejectException("缺轻、中、重三条线，底档立不住");
        }
        // 人塞的自拟字号不给存，库里只认系统生成的
        record.setId(null);
        record.setSiteNo(generateSiteNo());
        if (record.getStatus() == null) {
            record.setStatus(STATUS_ACTIVE);
        }
        record.setDelFlag(0);
        return this.baseMapper.insert(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateDoc(TFlogStandDoc record) {
        if (record == null || record.getId() == null) {
            throw new FlogCardRejectException("缺要改的底档");
        }
        TFlogStandDoc old = this.baseMapper.selectById(record.getId());
        if (old == null || (old.getDelFlag() != null && old.getDelFlag() == 1)) {
            throw new FlogCardRejectException("底档不存在或已删除");
        }
        // 字号钉死：改单时带什么字号来都不作数
        record.setSiteNo(old.getSiteNo());
        return this.baseMapper.updateById(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int revoke(Long id) {
        TFlogStandDoc old = this.baseMapper.selectById(id);
        if (old == null || (old.getDelFlag() != null && old.getDelFlag() == 1)) {
            throw new FlogCardRejectException("底档不存在或已删除");
        }
        TFlogStandDoc update = new TFlogStandDoc();
        update.setId(id);
        update.setStatus(STATUS_REVOKED);
        return this.baseMapper.updateById(update);
    }

    /** 底档字号自动生成：SD+年度+四位序，序取当年同号头最大者加一 */
    private synchronized String generateSiteNo() {
        int year = Calendar.getInstance().get(Calendar.YEAR);
        String prefix = "SD" + year;
        int max = -1;
        List<TFlogStandDoc> all = this.baseMapper.selectList(new QueryWrapper<TFlogStandDoc>()
                .likeRight("site_no", prefix)
                .select("site_no"));
        for (TFlogStandDoc d : all) {
            if (d.getSiteNo() == null || d.getSiteNo().length() <= prefix.length()) {
                continue;
            }
            try {
                max = Math.max(max, Integer.parseInt(d.getSiteNo().substring(prefix.length())));
            } catch (NumberFormatException ignore) {
                // 非本规则的杂号不参与排序
            }
        }
        return prefix + String.format("%04d", max + 1);
    }
}
