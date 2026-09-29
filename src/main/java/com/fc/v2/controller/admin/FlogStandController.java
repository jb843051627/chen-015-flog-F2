package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TFlogSiteTrust;
import com.fc.v2.model.auto.TFlogStandDoc;
import com.fc.v2.service.ITFlogQuotaLedgerService;
import com.fc.v2.service.ITFlogStandDocService;
import com.fc.v2.mapper.auto.TFlogSiteTrustMapper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

/**
 * 编制单位底档 / 年度额度下达 / 跨县协办受托 的管理入口。
 *
 * @author fuce
 * @date 2026-09-28
 */
@Api(value = "伐区底档与额度账")
@Controller
@RequestMapping("/flogStand")
public class FlogStandController extends BaseController {

    @Autowired
    private ITFlogStandDocService standDocService;

    @Autowired
    private ITFlogQuotaLedgerService quotaLedgerService;

    @Autowired
    private TFlogSiteTrustMapper siteTrustMapper;

    @ApiOperation(value = "底档列表", notes = "底档列表（含已收回，已收回只可翻不可挂新卡）")
    @GetMapping("/list")
    @RequiresPermissions("flog:stand:list")
    @ResponseBody
    public AjaxResult list() {
        return AjaxResult.success().put("data", standDocService.selectList());
    }

    @Log(title = "伐区底档建档", action = "add")
    @ApiOperation(value = "底档建档", notes = "字号自动生成，手填字号不收")
    @PostMapping("/add")
    @RequiresPermissions("flog:stand:add")
    @ResponseBody
    public AjaxResult add(TFlogStandDoc record) {
        record.setSiteNo(null);
        return toAjax(standDocService.createDoc(record));
    }

    @Log(title = "伐区底档改档", action = "edit")
    @ApiOperation(value = "底档改档", notes = "字号钉死不挪")
    @PostMapping("/edit")
    @RequiresPermissions("flog:stand:edit")
    @ResponseBody
    public AjaxResult edit(TFlogStandDoc record) {
        return toAjax(standDocService.updateDoc(record));
    }

    @Log(title = "伐区底档年初收回", action = "revoke")
    @ApiOperation(value = "年初收回", notes = "收回后不接纳新卡，早年旧卡仍翻得到")
    @PostMapping("/revoke")
    @RequiresPermissions("flog:stand:edit")
    @ResponseBody
    public AjaxResult revoke(Long id) {
        return toAjax(standDocService.revoke(id));
    }

    @Log(title = "年度采伐额度下达", action = "grant")
    @ApiOperation(value = "额度下达", notes = "一家一年立一行总数")
    @PostMapping("/quotaGrant")
    @RequiresPermissions("flog:stand:grant")
    @ResponseBody
    public AjaxResult quotaGrant(String siteNo, Integer quotaYear, java.math.BigDecimal totalAmount) {
        return AjaxResult.success("额度已下达").put("data",
                quotaLedgerService.grant(siteNo, quotaYear, totalAmount));
    }

    @ApiOperation(value = "额度账查看", notes = "扣过与尚存同一回计算")
    @GetMapping("/ledger")
    @RequiresPermissions("flog:stand:list")
    @ResponseBody
    public AjaxResult ledger(String siteNo, Integer quotaYear) {
        return AjaxResult.success().put("data", quotaLedgerService.viewLedger(siteNo, quotaYear));
    }

    @Log(title = "跨县协办受托指定", action = "trust")
    @ApiOperation(value = "受托指定", notes = "受托人此后只多看得见受托那一家")
    @PostMapping("/trust/add")
    @RequiresPermissions("flog:stand:grant")
    @ResponseBody
    public AjaxResult trustAdd(TFlogSiteTrust trust) {
        if (trust.getSiteId() == null || trust.getTrusteeId() == null) {
            return error("缺受托底档或受托人");
        }
        TFlogStandDoc doc = standDocService.selectById(trust.getSiteId());
        if (doc == null) {
            return error("受托底档不存在");
        }
        trust.setSiteNo(doc.getSiteNo());
        trust.setDelFlag(0);
        TFlogSiteTrust exist = siteTrustMapper.selectOne(new QueryWrapper<TFlogSiteTrust>()
                .eq("site_id", trust.getSiteId())
                .eq("trustee_id", trust.getTrusteeId()));
        if (exist == null) {
            siteTrustMapper.insert(trust);
        } else {
            TFlogSiteTrust upd = new TFlogSiteTrust();
            upd.setId(exist.getId());
            upd.setDelFlag(0);
            upd.setTrusteeName(trust.getTrusteeName());
            siteTrustMapper.updateById(upd);
        }
        return AjaxResult.success("受托关系已落");
    }

    @ApiOperation(value = "受托关系查询", notes = "按受托人查其受托的那几家")
    @GetMapping("/trust/list")
    @RequiresPermissions("flog:stand:list")
    @ResponseBody
    public AjaxResult trustList(Long trusteeId) {
        return AjaxResult.success().put("data", siteTrustMapper.selectList(
                new QueryWrapper<TFlogSiteTrust>().eq("trustee_id", trusteeId).eq("del_flag", 0)));
    }
}
