package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TFlogApplyCard;
import com.fc.v2.model.custom.ApplyCardReceipt;
import com.fc.v2.model.custom.CardDraft;
import com.fc.v2.service.ITFlogApplyCardService;
import com.fc.v2.service.ITFlogQuotaLedgerService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 伐区采伐申请建档卡 Controller。
 * 开不开卡只由业务服务层那一条保存方法说了算：本层没有第二条躲得过它的通道，
 * 屏上落的顺位、核验串、扣过/尚存数全照保存方法的给回回填。
 *
 * @author fuce
 * @date 2026-09-12
 */
@Api(value = "伐区采伐申请建档卡")
@Controller
@RequestMapping("/FlogApplyCardController")
public class FlogApplyCardController extends BaseController {

    private final String prefix = "admin/flogApplyCard";

    @Autowired
    private ITFlogApplyCardService flogApplyCardService;

    @Autowired
    private ITFlogQuotaLedgerService flogQuotaLedgerService;

    @ApiOperation(value = "分页跳转", notes = "分页跳转")
    @GetMapping("/view")
    @RequiresPermissions("flog:flogApplyCard:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "伐区采伐申请建档卡集合查询", action = "list")
    @ApiOperation(value = "分页查询（只摆现行版，自动圈界）", notes = "分页查询（只摆现行版，自动圈界）")
    @GetMapping("/list")
    @RequiresPermissions("flog:flogApplyCard:list")
    @ResponseBody
    public ResultTable list(TFlogApplyCard record) {
        QueryWrapper<TFlogApplyCard> queryWrapper = new QueryWrapper<TFlogApplyCard>();
        if (record.getSiteNo() != null && !record.getSiteNo().isEmpty()) {
            queryWrapper.eq("site_no", record.getSiteNo());
        }
        if (record.getSubcompNo() != null && !record.getSubcompNo().isEmpty()) {
            queryWrapper.eq("subcomp_no", record.getSubcompNo());
        }
        if (record.getCutMode() != null) {
            queryWrapper.eq("cut_mode", record.getCutMode());
        }
        if (record.getStatus() != null) {
            queryWrapper.eq("status", record.getStatus());
        }
        startPage();
        List<TFlogApplyCard> rows = flogApplyCardService.selectTFlogApplyCardList(queryWrapper);
        com.github.pagehelper.PageInfo<TFlogApplyCard> page =
                new com.github.pagehelper.PageInfo<TFlogApplyCard>(rows);
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "伐区采伐申请建档卡立卡", action = "add")
    @ApiOperation(value = "立卡/并版（唯一保存通道，原卡面入参）", notes = "立卡/并版（唯一保存通道，原卡面入参）")
    @PostMapping("/add")
    @RequiresPermissions("flog:flogApplyCard:add")
    @ResponseBody
    public AjaxResult add(TFlogApplyCard record) {
        // 界面塞进来的自拟卡号、顺位、核验串、立卡人一律不作数，保存方法自己折自己回填
        ApplyCardReceipt receipt = flogApplyCardService.saveCard(record);
        return AjaxResult.success("立卡成功").put("data", receipt);
    }

    @Log(title = "伐区采伐申请建档卡重录并版", action = "saveDraft")
    @ApiOperation(value = "立卡/并版（另一种送法：卡面+办卡人附言）", notes = "立卡/并版（另一种送法：卡面+办卡人附言）")
    @PostMapping("/saveDraft")
    @RequiresPermissions("flog:flogApplyCard:add")
    @ResponseBody
    public AjaxResult saveDraft(@RequestBody CardDraft draft) {
        ApplyCardReceipt receipt = flogApplyCardService.saveCard(draft);
        return AjaxResult.success("立卡成功").put("data", receipt);
    }

    @Log(title = "伐区采伐申请建档卡改版", action = "edit")
    @ApiOperation(value = "重录改版（旧版另存，不容覆写）", notes = "重录改版（旧版另存，不容覆写）")
    @PostMapping("/edit")
    @RequiresPermissions("flog:flogApplyCard:edit")
    @ResponseBody
    public AjaxResult editSave(TFlogApplyCard record) {
        ApplyCardReceipt receipt = flogApplyCardService.saveCard(record);
        return AjaxResult.success("改版成功").put("data", receipt);
    }

    @Log(title = "伐区采伐申请建档卡压死", action = "press")
    @ApiOperation(value = "压死成形（占用额度原样退回，成形覆不掉）", notes = "压死成形（占用额度原样退回，成形覆不掉）")
    @PostMapping("/press")
    @RequiresPermissions("flog:flogApplyCard:edit")
    @ResponseBody
    public AjaxResult press(Long id) {
        return toAjax(flogApplyCardService.pressCard(id));
    }

    @ApiOperation(value = "各版本翻检", notes = "各版本翻检（旧版另存一层）")
    @GetMapping("/versions")
    @RequiresPermissions("flog:flogApplyCard:list")
    @ResponseBody
    public AjaxResult versions(String billNo) {
        return AjaxResult.success().put("data", flogApplyCardService.listVersions(billNo));
    }

    @ApiOperation(value = "单卡查询", notes = "单卡查询（圈界生效）")
    @GetMapping("/detail")
    @RequiresPermissions("flog:flogApplyCard:list")
    @ResponseBody
    public AjaxResult detail(Long id) {
        return AjaxResult.success().put("data", flogApplyCardService.selectTFlogApplyCardById(id));
    }

    @ApiOperation(value = "年度额度账同屏", notes = "扣过的数与尚存的数出自同一回计算，页面不许自己数")
    @GetMapping("/ledger")
    @RequiresPermissions("flog:flogApplyCard:list")
    @ResponseBody
    public AjaxResult ledger(String siteNo, Integer quotaYear) {
        return AjaxResult.success().put("data", flogQuotaLedgerService.viewLedger(siteNo, quotaYear));
    }

    @Log(title = "伐区采伐申请建档卡删除", action = "remove")
    @ApiOperation(value = "删除", notes = "删除（已压死的卡覆不掉）")
    @DeleteMapping("/remove")
    @RequiresPermissions("flog:flogApplyCard:remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(flogApplyCardService.deleteTFlogApplyCardByIds(ids));
    }
}
