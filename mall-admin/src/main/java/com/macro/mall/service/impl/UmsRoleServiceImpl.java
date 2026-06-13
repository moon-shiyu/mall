package com.macro.mall.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.github.pagehelper.PageHelper;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.dao.UmsRoleDao;
import com.macro.mall.mapper.UmsMenuMapper;
import com.macro.mall.mapper.UmsResourceMapper;
import com.macro.mall.mapper.UmsRoleMapper;
import com.macro.mall.mapper.UmsRoleMenuRelationMapper;
import com.macro.mall.mapper.UmsRoleResourceRelationMapper;
import com.macro.mall.model.*;
import com.macro.mall.service.UmsAdminCacheService;
import com.macro.mall.service.UmsRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 后台角色管理Service实现类
 * Created by macro on 2018/9/30.
 */
@Service
public class UmsRoleServiceImpl implements UmsRoleService {
    @Autowired
    private UmsRoleMapper roleMapper;
    @Autowired
    private UmsRoleMenuRelationMapper roleMenuRelationMapper;
    @Autowired
    private UmsRoleResourceRelationMapper roleResourceRelationMapper;
    @Autowired
    private UmsMenuMapper menuMapper;
    @Autowired
    private UmsResourceMapper resourceMapper;
    @Autowired
    private UmsRoleDao roleDao;
    @Autowired
    private UmsAdminCacheService adminCacheService;
    @Override
    public int create(UmsRole role) {
        role.setCreateTime(new Date());
        role.setAdminCount(0);
        role.setSort(0);
        return roleMapper.insert(role);
    }

    @Override
    public int update(Long id, UmsRole role) {
        role.setId(id);
        return roleMapper.updateByPrimaryKeySelective(role);
    }

    @Override
    public int delete(List<Long> ids) {
        UmsRoleExample example = new UmsRoleExample();
        example.createCriteria().andIdIn(ids);
        int count = roleMapper.deleteByExample(example);
        adminCacheService.delResourceListByRoleIds(ids);
        return count;
    }

    @Override
    public List<UmsRole> list() {
        return roleMapper.selectByExample(new UmsRoleExample());
    }

    @Override
    public List<UmsRole> list(String keyword, Integer pageSize, Integer pageNum) {
        PageHelper.startPage(pageNum, pageSize);
        UmsRoleExample example = new UmsRoleExample();
        if (!StrUtil.isEmpty(keyword)) {
            example.createCriteria().andNameLike("%" + keyword + "%");
        }
        return roleMapper.selectByExample(example);
    }

    @Override
    public List<UmsMenu> getMenuList(Long adminId) {
        return roleDao.getMenuList(adminId);
    }

    @Override
    public List<UmsMenu> listMenu(Long roleId) {
        return roleDao.getMenuListByRoleId(roleId);
    }

    @Override
    public List<UmsResource> listResource(Long roleId) {
        return roleDao.getResourceListByRoleId(roleId);
    }

    @Override
    public int allocMenu(Long roleId, List<Long> menuIds) {
        // 1. 校验角色是否存在
        validateRoleExists(roleId);
        // 2. 过滤null并去重
        List<Long> distinctMenuIds = deduplicateIds(menuIds);
        // 3. 先删除原有关系
        UmsRoleMenuRelationExample example = new UmsRoleMenuRelationExample();
        example.createCriteria().andRoleIdEqualTo(roleId);
        roleMenuRelationMapper.deleteByExample(example);
        // 4. 去重后为空则仅清除关系，不插入新关系
        if (CollUtil.isEmpty(distinctMenuIds)) {
            return 0;
        }
        // 5. 校验所有菜单ID有效
        validateMenuIds(distinctMenuIds);
        // 6. 使用批量插入新关系
        List<UmsRoleMenuRelation> relationList = new ArrayList<>();
        for (Long menuId : distinctMenuIds) {
            UmsRoleMenuRelation relation = new UmsRoleMenuRelation();
            relation.setRoleId(roleId);
            relation.setMenuId(menuId);
            relationList.add(relation);
        }
        roleDao.insertMenuList(relationList);
        return distinctMenuIds.size();
    }

    @Override
    public int allocResource(Long roleId, List<Long> resourceIds) {
        // 1. 校验角色是否存在
        validateRoleExists(roleId);
        // 2. 过滤null并去重
        List<Long> distinctResourceIds = deduplicateIds(resourceIds);
        // 3. 先删除原有关系
        UmsRoleResourceRelationExample example = new UmsRoleResourceRelationExample();
        example.createCriteria().andRoleIdEqualTo(roleId);
        roleResourceRelationMapper.deleteByExample(example);
        // 4. 去重后为空则仅清除关系，不插入新关系
        if (CollUtil.isEmpty(distinctResourceIds)) {
            // 仍然需要使受影响管理员缓存失效
            invalidateResourceCacheAfterCommit(roleId);
            return 0;
        }
        // 5. 校验所有资源ID有效
        validateResourceIds(distinctResourceIds);
        // 6. 使用批量插入新关系
        List<UmsRoleResourceRelation> relationList = new ArrayList<>();
        for (Long resourceId : distinctResourceIds) {
            UmsRoleResourceRelation relation = new UmsRoleResourceRelation();
            relation.setRoleId(roleId);
            relation.setResourceId(resourceId);
            relationList.add(relation);
        }
        roleDao.insertResourceList(relationList);
        // 7. 事务提交后再使缓存失效，避免回滚后缓存被错误清除
        invalidateResourceCacheAfterCommit(roleId);
        return distinctResourceIds.size();
    }

    /**
     * 校验角色ID是否存在
     */
    private void validateRoleExists(Long roleId) {
        if (roleId == null) {
            Asserts.fail("角色ID不能为空");
        }
        UmsRole role = roleMapper.selectByPrimaryKey(roleId);
        if (role == null) {
            Asserts.fail("指定角色不存在");
        }
    }

    /**
     * 过滤null值并对ID列表去重，保持原始顺序
     */
    private List<Long> deduplicateIds(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return new ArrayList<>();
        }
        return ids.stream()
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 校验所有菜单ID在数据库中存在
     */
    private void validateMenuIds(List<Long> menuIds) {
        UmsMenuExample example = new UmsMenuExample();
        example.createCriteria().andIdIn(menuIds);
        long count = menuMapper.countByExample(example);
        if (count != menuIds.size()) {
            Asserts.fail("部分菜单ID无效，请检查后重试");
        }
    }

    /**
     * 校验所有资源ID在数据库中存在
     */
    private void validateResourceIds(List<Long> resourceIds) {
        UmsResourceExample example = new UmsResourceExample();
        example.createCriteria().andIdIn(resourceIds);
        long count = resourceMapper.countByExample(example);
        if (count != resourceIds.size()) {
            Asserts.fail("部分资源ID无效，请检查后重试");
        }
    }

    /**
     * 在事务提交后使受影响管理员的资源缓存失效，
     * 避免事务回滚后缓存被错误清除
     */
    private void invalidateResourceCacheAfterCommit(Long roleId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    adminCacheService.delResourceListByRole(roleId);
                }
            });
        } else {
            // 无活跃事务时直接执行
            adminCacheService.delResourceListByRole(roleId);
        }
    }
}
