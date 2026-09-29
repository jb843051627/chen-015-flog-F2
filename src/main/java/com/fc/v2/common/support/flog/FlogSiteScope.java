package com.fc.v2.common.support.flog;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TFlogSiteTrustMapper;
import com.fc.v2.mapper.auto.TFlogStandDocMapper;
import com.fc.v2.model.auto.TFlogSiteTrust;
import com.fc.v2.model.auto.TFlogStandDoc;
import com.fc.v2.model.auto.TSysDepartment;
import com.fc.v2.model.auto.TSysRole;
import com.fc.v2.model.auto.TSysUser;
import com.fc.v2.service.ITSysDepartmentService;
import com.fc.v2.service.ITSysRoleService;
import com.fc.v2.shiro.util.ShiroUtils;
import com.fc.v2.util.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 伐区块的翻册圈界：谁翻得着哪几家的卡，全站只认这一条线。
 * <ul>
 *   <li>本站账号：只翻本辖那几家（底档管辖站部门 = 本人部门，空值按 road_name 兜本部门名）；</li>
 *   <li>上级账号（角色名命中 admin/上级/省/市/全辖）：看全辖；</li>
 *   <li>跨县协办：在本辖之外，再把受托关系里点名的那一家摆给受托人；别场的册不并列。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-09-28
 */
@Component
public class FlogSiteScope {

    /** 全辖角色名记号（命中其一即看全辖） */
    private static final String[] ALL_SCOPE_MARKS = {"admin", "上级", "省", "市", "全辖"};

    @Resource
    private ITSysRoleService roleService;

    @Resource
    private ITSysDepartmentService departmentService;

    @Resource
    private TFlogStandDocMapper standDocMapper;

    @Resource
    private TFlogSiteTrustMapper siteTrustMapper;

    /**
     * 当前登录人可见的底档主键集合。
     *
     * @return null 表示全辖不设界；空集合表示一家都看不见；非空为可见底档id
     */
    public Scope visibleScope() {
        TSysUser user;
        try {
            user = ShiroUtils.getUser();
        } catch (RuntimeException e) {
            return new Scope(new ArrayList<Long>());
        }
        if (user == null || user.getId() == null) {
            return new Scope(new ArrayList<Long>());
        }

        List<TSysRole> roles = roleService.queryUserRole(user.getId());
        if (roles != null) {
            for (TSysRole role : roles) {
                String name = role.getName() == null ? "" : role.getName();
                for (String mark : ALL_SCOPE_MARKS) {
                    if (name.contains(mark)) {
                        return new Scope(null);
                    }
                }
            }
        }

        List<Long> siteIds = new ArrayList<Long>();
        String depName = user.getDepId() == null ? "" : deptName(user.getDepId());
        if (user.getDepId() == null) {
            // 没有部门又不是上级，本辖一家都兜不出来，只剩跨县受托那一档
            QueryWrapper<TFlogStandDoc> empty = new QueryWrapper<TFlogStandDoc>().eq("id", -1L).eq("del_flag", 0);
            siteIds.addAll(collectIds(empty));
        } else {
            QueryWrapper<TFlogStandDoc> qw = new QueryWrapper<TFlogStandDoc>().eq("del_flag", 0);
            if (StringUtils.isNotBlank(depName)) {
                final String name = depName;
                qw.and(w -> w.eq("station_dept_id", user.getDepId()).or().like("road_name", name));
            } else {
                qw.eq("station_dept_id", user.getDepId());
            }
            siteIds.addAll(collectIds(qw));
        }

        // 跨县协办：受托那一家单独摆进来
        List<TFlogSiteTrust> trusts = siteTrustMapper.selectList(
                new QueryWrapper<TFlogSiteTrust>()
                        .eq("trustee_id", user.getId())
                        .eq("del_flag", 0));
        for (TFlogSiteTrust trust : trusts) {
            if (!siteIds.contains(trust.getSiteId())) {
                siteIds.add(trust.getSiteId());
            }
        }
        return new Scope(siteIds);
    }

    private List<Long> collectIds(QueryWrapper<TFlogStandDoc> qw) {
        List<Long> ids = new ArrayList<Long>();
        for (TFlogStandDoc doc : standDocMapper.selectList(qw)) {
            ids.add(doc.getId());
        }
        return ids;
    }

    private String deptName(Long depId) {
        TSysDepartment dept = departmentService.selectTSysDepartmentById(depId);
        return dept == null || dept.getDeptName() == null ? "" : dept.getDeptName();
    }

    /** 圈界结果：siteIds 为 null 即全辖 */
    public static class Scope implements Serializable {
        private static final long serialVersionUID = 1L;
        private final List<Long> siteIds;

        public Scope(List<Long> siteIds) {
            this.siteIds = siteIds;
        }

        public boolean isAll() {
            return siteIds == null;
        }

        public List<Long> getSiteIds() {
            return siteIds;
        }

        /** 指定底档当前人翻不翻得着 */
        public boolean canSee(Long siteId) {
            if (siteId == null) {
                return false;
            }
            return siteIds == null || siteIds.contains(siteId);
        }
    }
}
