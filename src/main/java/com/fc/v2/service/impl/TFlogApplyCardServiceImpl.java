package com.fc.v2.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.exception.flog.FlogCardRejectException;
import com.fc.v2.common.support.flog.FlogSiteScope;
import com.fc.v2.mapper.auto.TFlogApplyCardMapper;
import com.fc.v2.mapper.auto.TFlogStandDocMapper;
import com.fc.v2.model.auto.TFlogApplyCard;
import com.fc.v2.model.auto.TFlogQuotaLedger;
import com.fc.v2.model.auto.TFlogStandDoc;
import com.fc.v2.model.custom.ApplyCardReceipt;
import com.fc.v2.model.custom.CardDraft;
import com.fc.v2.service.ITFlogApplyCardService;
import com.fc.v2.service.ITFlogQuotaLedgerService;
import com.fc.v2.shiro.util.ShiroUtils;
import com.fc.v2.util.MD5Util;
import com.fc.v2.util.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * 伐区采伐申请建档卡 Service业务层处理。
 * 卡立不立得起来只认这一条保存方法：必填栏照采伐方式当场点、顺位系统折算回填、
 * 核验串随版本钉死、额度当场扣/退；并版只退旧行不覆写，旧版另存一层。
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class TFlogApplyCardServiceImpl implements ITFlogApplyCardService {

    /** 采伐方式：1皆伐 2渐伐 3择伐 4抚育采伐 5更新采伐 6低质低效林改造 */
    private static final int MODE_CLEAR = 1;
    private static final int MODE_GRADUAL = 2;
    private static final int MODE_SELECTION = 3;
    private static final int MODE_TENDING = 4;
    private static final int MODE_RENEWAL = 5;
    private static final int MODE_TRANSFORM = 6;

    /** 顺位：1轻 2中 3重（越过重度线仍归重度，没有第四档） */
    private static final int GRADE_LIGHT = 1;
    private static final int GRADE_MIDDLE = 2;
    private static final int GRADE_HEAVY = 3;

    /** 进展：0待核对 1已核对 2已压死 */
    private static final int STATUS_DRAFT = 0;
    private static final int STATUS_PRESSED = 2;

    private static final int CURRENT_YES = 1;
    private static final int CURRENT_NO = 0;

    @Resource
    private TFlogApplyCardMapper baseMapper;
    @Resource
    private TFlogStandDocMapper flogStandDocMapper;
    @Resource
    private ITFlogQuotaLedgerService quotaLedgerService;
    @Resource
    private FlogSiteScope siteScope;

    /* ================= 唯一保存通道 ================= */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApplyCardReceipt saveCard(TFlogApplyCard record) {
        return doSave(record, null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApplyCardReceipt saveCard(CardDraft draft) {
        if (draft == null || draft.getCard() == null) {
            throw new FlogCardRejectException("卡面为空，无卡可立");
        }
        return doSave(draft.getCard(), draft.getOperator(), draft.getHandlerNote());
    }

    private ApplyCardReceipt doSave(TFlogApplyCard record, String operatorIn, String handlerNote) {
        if (record == null) {
            throw new FlogCardRejectException("卡面为空，无卡可立");
        }

        // 1) 归哪家单位：以底档字号认归属，名号不算数；已收回的档不接新卡
        if (record.getId() != null && record.getSiteId() == null) {
            // 改版没另指底档时，沿用旧版挂的那家
            TFlogApplyCard hinted = baseMapper.selectById(record.getId());
            if (hinted != null) {
                record.setSiteId(hinted.getSiteId());
            }
        }
        if (record.getSiteId() == null) {
            throw new FlogCardRejectException("缺“归属底档”这一格，卡立不住");
        }
        TFlogStandDoc arch = flogStandDocMapper.selectById(record.getSiteId());
        if (arch == null || (arch.getDelFlag() != null && arch.getDelFlag() == 1)) {
            throw new FlogCardRejectException("归属底档不在册（id=" + record.getSiteId() + "），卡挂不住");
        }
        if (!siteScope.visibleScope().canSee(arch.getId())) {
            throw new FlogCardRejectException("本账号翻不着这家底档，不许往它名下立卡");
        }
        if (arch.getStatus() != null && arch.getStatus() == 1) {
            throw new FlogCardRejectException("底档字号【" + arch.getSiteNo() + "】已于年初收回，不再接纳新卡；早年旧卡仍可翻检");
        }
        record.setSiteNo(arch.getSiteNo());

        // 2) 小班号（同一家同一小班二次上报并成一行的另一半对键）
        if (StringUtils.isEmpty(record.getSubcompNo())) {
            throw new FlogCardRejectException("缺“小班号”这一格，卡立不住");
        }

        // 3) 采伐方式 + 各方式当场点必填栏
        validateByMode(record);

        // 4) 拟采蓄积：零、负数一样起不来
        BigDecimal qty = record.getQty();
        if (qty == null) {
            throw new FlogCardRejectException("缺“本卡合并后拟采蓄积”这一格，卡立不住");
        }
        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new FlogCardRejectException("“本卡合并后拟采蓄积”为零或负数（" + qty.toPlainString() + "），卡立不住");
        }

        // 5) 定位旧版：送了id按id找（覆盖改挂别家的情形），否则按底档字号+小班号找现行行
        TFlogApplyCard prev = null;
        if (record.getId() != null) {
            prev = baseMapper.selectById(record.getId());
            if (prev == null || (prev.getDelFlag() != null && prev.getDelFlag() == 1)) {
                throw new FlogCardRejectException("要重录的卡不存在或已删除");
            }
            if (!siteScope.visibleScope().canSee(prev.getSiteId())) {
                throw new FlogCardRejectException("本账号翻不着旧卡所属底档，不许改版它");
            }
            if (prev.getCurrentFlag() != null && prev.getCurrentFlag() == CURRENT_NO) {
                throw new FlogCardRejectException("建档卡号【" + prev.getBillNo()
                        + "】这是旧版，旧版只许翻检不许在它身上改版；要改请从现行版起");
            }
            if (prev.getStatus() != null && prev.getStatus() == STATUS_PRESSED) {
                throw new FlogCardRejectException("建档卡号【" + prev.getBillNo() + "】已压死成形，覆不掉也改不动");
            }
        } else {
            // 对键只并未压死的现行行：已压死代表上一轮已成形，同小班新一轮上报另立卡号
            prev = baseMapper.selectOne(new QueryWrapper<TFlogApplyCard>()
                    .eq("site_no", arch.getSiteNo())
                    .eq("subcomp_no", record.getSubcompNo())
                    .eq("current_flag", CURRENT_YES)
                    .ne("status", STATUS_PRESSED)
                    .eq("del_flag", 0)
                    .last("limit 1"));
        }

        String operator = StringUtils.isNotEmpty(operatorIn) ? operatorIn : loginName();
        Date now = new Date();
        int year = yearOf(now);

        TFlogApplyCard version = new TFlogApplyCard();
        version.setId(null); // 旧版另存一行，绝不覆写旧行
        copyMutableFields(record, version);
        version.setSiteId(arch.getId());
        version.setSiteNo(arch.getSiteNo());
        version.setCurrentFlag(CURRENT_YES);
        version.setStatus(STATUS_DRAFT);
        version.setDelFlag(0);

        boolean rehang = prev != null && !arch.getSiteNo().equals(prev.getSiteNo());
        if (prev == null) {
            // 首次立卡：卡号自动生成，人塞的号不给存
            version.setBillNo(generateBillNo(year));
            version.setNodeNo(1);
            version.setCardYear(year);
            version.setFounder(operator);
            version.setFounderTime(now);
            version.setHandlers(appendHandler(null, operator, now, handlerNote));
        } else {
            // 重录并版：旧版退下去另存，新版版本序加一；立卡人钉死不挪窝
            version.setBillNo(prev.getBillNo());
            version.setNodeNo((prev.getNodeNo() == null ? 1 : prev.getNodeNo()) + 1);
            version.setCardYear(rehang ? year : prev.getCardYear());
            version.setFounder(prev.getFounder());
            version.setFounderTime(prev.getFounderTime());
            version.setHandlers(appendHandler(prev.getHandlers(), operator, now, handlerNote));
        }

        // 6) 取用顺位系统折算回填，界面没有手填的口子
        version.setGradeLevel(foldGrade(qty, arch));

        // 7) 核验串随版本序走：日子取本版那天，旧版那一行原样留着
        version.setContent(buildChecksum(version.getBillNo(), version.getNodeNo(), now));

        // 8) 额度账同一条线：先退旧版占的，再扣新版的；超额当场点卡号
        if (prev != null && prev.getHeldQty() != null
                && prev.getHeldQty().compareTo(BigDecimal.ZERO) > 0) {
            if (!quotaLedgerService.release(prev.getSiteNo(), prev.getCardYear(), prev.getHeldQty())) {
                throw new FlogCardRejectException("建档卡号【" + prev.getBillNo()
                        + "】旧版占用的额度退不回账，改版整笔不作数");
            }
            TFlogApplyCard zero = new TFlogApplyCard();
            zero.setId(prev.getId());
            zero.setHeldQty(BigDecimal.ZERO);
            baseMapper.updateById(zero);
        }
        int ledgerYear = version.getCardYear();
        if (!quotaLedgerService.hold(arch.getSiteNo(), ledgerYear, qty)) {
            TFlogQuotaLedger ledger = quotaLedgerService.viewLedger(arch.getSiteNo(), ledgerYear);
            if (ledger == null) {
                throw new FlogCardRejectException("建档卡号【" + version.getBillNo()
                        + "】立不住：底档【" + arch.getSiteName() + "】" + ledgerYear + "年度额度账尚未下达");
            }
            throw new FlogCardRejectException("建档卡号【" + version.getBillNo() + "】拟采蓄积 "
                    + qty.toPlainString() + " 立方米越过底档【" + arch.getSiteName() + "】"
                    + ledgerYear + "年度可占额度：总额 " + ledger.getTotalAmount().toPlainString()
                    + " 立方米，尚存 " + ledger.getRemainAmount().toPlainString() + " 立方米");
        }
        version.setHeldQty(qty);

        // 9) 旧版退下、新版落库
        if (prev != null) {
            TFlogApplyCard retire = new TFlogApplyCard();
            retire.setId(prev.getId());
            retire.setCurrentFlag(CURRENT_NO);
            baseMapper.updateById(retire);
        }
        baseMapper.insert(version);

        // 10) 给回：折到第几级、核验串、划走多少、扣过与尚存同屏且同一回计算
        TFlogQuotaLedger ledgerAfter = quotaLedgerService.viewLedger(arch.getSiteNo(), ledgerYear);
        return new ApplyCardReceipt(version, ledgerAfter);
    }

    /* ================= 按采伐方式当场点必填栏 ================= */

    private void validateByMode(TFlogApplyCard r) {
        Integer mode = r.getCutMode();
        if (mode == null) {
            throw new FlogCardRejectException("缺“采伐方式”这一格，卡立不住");
        }
        // 新报拟采面积是卡面六样之一，六类方式都要给
        if (r.getFineAmt() == null || r.getFineAmt().compareTo(BigDecimal.ZERO) <= 0) {
            throw new FlogCardRejectException("缺“本卡新报拟采面积”这一格，卡立不住");
        }

        // 先照本方式点命门栏（此时六类专属栏都还在）
        switch (mode) {
            case MODE_CLEAR:
                requirePositiveInt(r.getClearTrees(), "皆伐株数");
                break;
            case MODE_GRADUAL:
                requirePositiveInt(r.getGradYears(), "渐伐分年次数");
                if (r.getGradInterval() == null || r.getGradInterval() < 0) {
                    throw new FlogCardRejectException("缺“渐伐间隔年数”这一格（走渐伐得给分年次数与间隔年数），卡立不住");
                }
                break;
            case MODE_SELECTION:
                if (r.getSelectIntensity() == null
                        || r.getSelectIntensity().compareTo(BigDecimal.ZERO) <= 0
                        || r.getSelectIntensity().compareTo(new BigDecimal("100")) > 0) {
                    throw new FlogCardRejectException("缺“择伐强度”这一格或取值不在 0~100 之间（走择伐得给择伐强度与保留株数），卡立不住");
                }
                requirePositiveInt(r.getReserveTrees(), "择伐保留株数");
                break;
            case MODE_TENDING:
                if (StringUtils.isEmpty(r.getTendMethod())) {
                    throw new FlogCardRejectException("缺“抚育办法”这一格（走抚育采伐得给抚育办法与每公顷采出量），卡立不住");
                }
                if (r.getTendPerHa() == null || r.getTendPerHa().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new FlogCardRejectException("缺“每公顷采出量”这一格（走抚育采伐得给抚育办法与每公顷采出量），卡立不住");
                }
                break;
            case MODE_RENEWAL:
                if (StringUtils.isEmpty(r.getRenewMode())) {
                    throw new FlogCardRejectException("缺“更新方式”这一格（走更新采伐得给更新方式与完成年度），卡立不住");
                }
                if (r.getRenewFinishYear() == null || r.getRenewFinishYear() < 1900) {
                    throw new FlogCardRejectException("缺“更新完成年度”这一格（走更新采伐得给更新方式与完成年度），卡立不住");
                }
                break;
            case MODE_TRANSFORM:
                if (StringUtils.isEmpty(r.getTargetSpecies())) {
                    throw new FlogCardRejectException("缺“目标树种”这一格（走低质低效林改造得给目标树种），卡立不住");
                }
                break;
            default:
                throw new FlogCardRejectException("“采伐方式”取值 " + mode + " 不在皆伐/渐伐/择伐/抚育采伐/更新采伐/低质低效林改造六类之内，卡立不住");
        }

        // 方式一换，用不上的格子一律空着，不带着旧方式的数起卡
        r.setClearTrees(mode == MODE_CLEAR ? r.getClearTrees() : null);
        r.setGradYears(mode == MODE_GRADUAL ? r.getGradYears() : null);
        r.setGradInterval(mode == MODE_GRADUAL ? r.getGradInterval() : null);
        r.setSelectIntensity(mode == MODE_SELECTION ? r.getSelectIntensity() : null);
        r.setReserveTrees(mode == MODE_SELECTION ? r.getReserveTrees() : null);
        r.setTendMethod(mode == MODE_TENDING ? r.getTendMethod() : null);
        r.setTendPerHa(mode == MODE_TENDING ? r.getTendPerHa() : null);
        r.setRenewMode(mode == MODE_RENEWAL ? r.getRenewMode() : null);
        r.setRenewFinishYear(mode == MODE_RENEWAL ? r.getRenewFinishYear() : null);
        r.setTargetSpecies(mode == MODE_TRANSFORM ? r.getTargetSpecies() : null);
    }

    private void requirePositiveInt(Integer val, String label) {
        if (val == null || val <= 0) {
            throw new FlogCardRejectException("缺“" + label + "”这一格，卡立不住");
        }
    }

    /* ================= 顺位折算：挨着界往高里数 ================= */

    private int foldGrade(BigDecimal qty, TFlogStandDoc arch) {
        BigDecimal th1 = arch.getTh1Max();
        BigDecimal th2 = arch.getTh2Max();
        BigDecimal th3 = arch.getTh3Max();
        if (th1 == null || th2 == null || th3 == null) {
            throw new FlogCardRejectException("底档字号【" + arch.getSiteNo() + "】压着的轻、中、重三条线不齐，顺位折不出来，卡立不住");
        }
        // 与中度线一样高落重度；越过重度线也一律重度——只设三档，往上不另开档
        if (qty.compareTo(th1) < 0) {
            return GRADE_LIGHT;
        }
        if (qty.compareTo(th2) < 0) {
            return GRADE_MIDDLE;
        }
        return GRADE_HEAVY;
    }

    /* ================= 压死：原样退额度，成形覆不掉 ================= */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int pressCard(Long id) {
        TFlogApplyCard card = baseMapper.selectById(id);
        if (card == null || (card.getDelFlag() != null && card.getDelFlag() == 1)) {
            throw new FlogCardRejectException("要压死的卡不存在");
        }
        if (!siteScope.visibleScope().canSee(card.getSiteId())) {
            throw new FlogCardRejectException("本账号翻不着这张卡，不许压死它");
        }
        if (card.getStatus() != null && card.getStatus() == STATUS_PRESSED) {
            throw new FlogCardRejectException("建档卡号【" + card.getBillNo() + "】已压死成形，覆不掉");
        }
        if (card.getHeldQty() != null && card.getHeldQty().compareTo(BigDecimal.ZERO) > 0) {
            quotaLedgerService.release(card.getSiteNo(), card.getCardYear(), card.getHeldQty());
        }
        TFlogApplyCard update = new TFlogApplyCard();
        update.setId(card.getId());
        update.setStatus(STATUS_PRESSED);
        update.setHeldQty(BigDecimal.ZERO);
        return baseMapper.updateById(update);
    }

    /* ================= 查询：列表只摆现行，旧版另层翻检，圈界生效 ================= */

    @Override
    public List<TFlogApplyCard> selectTFlogApplyCardList(Wrapper<TFlogApplyCard> queryWrapper) {
        // 外部条件（等值栏过滤）照旧带上，再强制钉死三条底线：未删、现行、不越圈界
        QueryWrapper<TFlogApplyCard> wrapper = queryWrapper instanceof QueryWrapper
                ? (QueryWrapper<TFlogApplyCard>) queryWrapper
                : new QueryWrapper<TFlogApplyCard>();
        wrapper.eq("del_flag", 0).eq("current_flag", CURRENT_YES);
        FlogSiteScope.Scope scope = siteScope.visibleScope();
        if (!scope.isAll()) {
            if (scope.getSiteIds().isEmpty()) {
                return new ArrayList<TFlogApplyCard>();
            }
            wrapper.in("site_id", scope.getSiteIds());
        }
        wrapper.orderByDesc("create_time");
        return baseMapper.selectList(wrapper);
    }

    @Override
    public List<TFlogApplyCard> listVersions(String billNo) {
        if (StringUtils.isEmpty(billNo)) {
            return new ArrayList<TFlogApplyCard>();
        }
        List<TFlogApplyCard> versions = baseMapper.selectList(new QueryWrapper<TFlogApplyCard>()
                .eq("bill_no", billNo)
                .eq("del_flag", 0)
                .orderByDesc("node_no"));
        if (versions.isEmpty()) {
            return versions;
        }
        if (!siteScope.visibleScope().canSee(versions.get(0).getSiteId())) {
            throw new FlogCardRejectException("本账号翻不着建档卡号【" + billNo + "】的底档，旧版不许翻");
        }
        return versions;
    }

    @Override
    public TFlogApplyCard selectTFlogApplyCardById(Long id) {
        TFlogApplyCard card = baseMapper.selectOne(new QueryWrapper<TFlogApplyCard>()
                .eq("id", id)
                .eq("del_flag", 0));
        if (card != null && !siteScope.visibleScope().canSee(card.getSiteId())) {
            throw new FlogCardRejectException("本账号翻不着这张卡");
        }
        return card;
    }

    /* ================= 旧入参通道：原样能调，落点仍是唯一保存方法 ================= */

    @Override
    public int insertTFlogApplyCard(TFlogApplyCard record) {
        // 人塞的自拟卡号、顺位一律不给存，由保存通道另折另生成
        record.setBillNo(null);
        record.setNodeNo(null);
        record.setGradeLevel(null);
        record.setContent(null);
        saveCard(record);
        return 1;
    }

    @Override
    public int updateTFlogApplyCard(TFlogApplyCard record) {
        if (record == null || record.getId() == null) {
            throw new FlogCardRejectException("缺要重录的卡，改版无从并起");
        }
        record.setBillNo(null);
        record.setGradeLevel(null);
        record.setContent(null);
        saveCard(record);
        return 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteTFlogApplyCardByIds(String ids) {
        int rows = 0;
        for (Long id : Arrays.asList(com.fc.v2.common.support.ConvertUtil.toLongArray(ids))) {
            rows += softDelete(id);
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteTFlogApplyCardById(Long id) {
        return softDelete(id);
    }

    private int softDelete(Long id) {
        TFlogApplyCard card = baseMapper.selectById(id);
        if (card == null || (card.getDelFlag() != null && card.getDelFlag() == 1)) {
            return 0;
        }
        if (!siteScope.visibleScope().canSee(card.getSiteId())) {
            throw new FlogCardRejectException("本账号翻不着这张卡，不许销它");
        }
        if (card.getStatus() != null && card.getStatus() == STATUS_PRESSED) {
            throw new FlogCardRejectException("建档卡号【" + card.getBillNo() + "】已压死成形，覆不掉");
        }
        TFlogApplyCard update = new TFlogApplyCard();
        update.setId(id);
        update.setDelFlag(1);
        return baseMapper.updateById(update);
    }

    /* ================= 杂务 ================= */

    /** 只誊卡面各栏，系统折算/钉死的栏由上层逐栏定，杜绝界面夹带 */
    private void copyMutableFields(TFlogApplyCard from, TFlogApplyCard to) {
        to.setSubcompNo(from.getSubcompNo());
        to.setCutBlockNo(from.getCutBlockNo());
        to.setCutMode(from.getCutMode());
        to.setQty(from.getQty());
        to.setFineAmt(from.getFineAmt());
        to.setRemark(from.getRemark());
        // 方式专属栏誊一份；validateByMode 已在 from 上清过无关栏并验过命门栏
        to.setClearTrees(from.getClearTrees());
        to.setGradYears(from.getGradYears());
        to.setGradInterval(from.getGradInterval());
        to.setSelectIntensity(from.getSelectIntensity());
        to.setReserveTrees(from.getReserveTrees());
        to.setTendMethod(from.getTendMethod());
        to.setTendPerHa(from.getTendPerHa());
        to.setRenewMode(from.getRenewMode());
        to.setRenewFinishYear(from.getRenewFinishYear());
        to.setTargetSpecies(from.getTargetSpecies());
    }

    private String appendHandler(String prev, String operator, Date at, String note) {
        String line = operator + " " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(at)
                + (StringUtils.isNotEmpty(note) ? " " + note : "");
        return StringUtils.isEmpty(prev) ? line : prev + "；" + line;
    }

    /** 核验串：卡号+版本序+本版日子，旧版留原样，两版共用一串即是错 */
    private String buildChecksum(String billNo, int nodeNo, Date versionDay) {
        String day = new SimpleDateFormat("yyyyMMddHHmmss").format(versionDay);
        return MD5Util.encode(billNo + "|" + nodeNo + "|" + day);
    }

    /** 建档卡号自动生成：FK+年度+四位序，序取现有卡号最大者加一 */
    private synchronized String generateBillNo(int year) {
        String prefix = "FK" + year;
        int max = 0;
        List<TFlogApplyCard> all = baseMapper.selectList(new QueryWrapper<TFlogApplyCard>()
                .likeRight("bill_no", prefix)
                .select("bill_no"));
        for (TFlogApplyCard c : all) {
            if (c.getBillNo() == null || c.getBillNo().length() <= prefix.length()) {
                continue;
            }
            try {
                max = Math.max(max, Integer.parseInt(c.getBillNo().substring(prefix.length())));
            } catch (NumberFormatException ignore) {
                // 非本规则生成的杂号不参与排序
            }
        }
        return prefix + String.format("%04d", max + 1);
    }

    private String loginName() {
        try {
            return ShiroUtils.getLoginName();
        } catch (RuntimeException e) {
            return "unknown";
        }
    }

    private int yearOf(Date date) {
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        return c.get(Calendar.YEAR);
    }
}
