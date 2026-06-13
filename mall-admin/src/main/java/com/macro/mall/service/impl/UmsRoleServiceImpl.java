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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
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
    private UmsRoleDao roleDao;
    @Autowired
    private UmsAdminCacheService adminCacheService;
    @Autowired
    private UmsMenuMapper menuMapper;
    @Autowired
    private UmsResourceMapper resourceMapper;
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
        //校验角色ID与角色是否存在（任意非法均在删除前抛出，保证零写入）
        if (roleId == null) {
            Asserts.fail("角色ID不能为空");
        }
        if (roleMapper.selectByPrimaryKey(roleId) == null) {
            Asserts.fail("角色不存在: " + roleId);
        }
        //去除null并去重，保持顺序；空列表表示清空该角色全部菜单
        List<Long> distinctMenuIds = distinctIds(menuIds);
        //校验菜单ID是否都有效，存在任意无效ID则整单拒绝
        if (!distinctMenuIds.isEmpty()) {
            UmsMenuExample menuExample = new UmsMenuExample();
            menuExample.createCriteria().andIdIn(distinctMenuIds);
            long existCount = menuMapper.countByExample(menuExample);
            if (existCount != distinctMenuIds.size()) {
                List<Long> existIds = menuMapper.selectByExample(menuExample).stream()
                        .map(UmsMenu::getId).collect(Collectors.toList());
                List<Long> invalidIds = distinctMenuIds.stream()
                        .filter(id -> !existIds.contains(id)).collect(Collectors.toList());
                Asserts.fail("包含无效的菜单ID: " + invalidIds);
            }
        }
        //删除原有关系
        UmsRoleMenuRelationExample example = new UmsRoleMenuRelationExample();
        example.createCriteria().andRoleIdEqualTo(roleId);
        roleMenuRelationMapper.deleteByExample(example);
        //批量插入去重后的新关系（空列表已是清空，无需插入，避免foreach空VALUES）
        if (!distinctMenuIds.isEmpty()) {
            List<UmsRoleMenuRelation> relationList = distinctMenuIds.stream().map(menuId -> {
                UmsRoleMenuRelation relation = new UmsRoleMenuRelation();
                relation.setRoleId(roleId);
                relation.setMenuId(menuId);
                return relation;
            }).collect(Collectors.toList());
            roleDao.insertRoleMenuRelationList(relationList);
        }
        //菜单数据未走缓存（缓存层仅缓存admin与资源列表），无需失效缓存
        return distinctMenuIds.size();
    }

    @Override
    public int allocResource(Long roleId, List<Long> resourceIds) {
        //校验角色ID与角色是否存在（任意非法均在删除前抛出，保证零写入）
        if (roleId == null) {
            Asserts.fail("角色ID不能为空");
        }
        if (roleMapper.selectByPrimaryKey(roleId) == null) {
            Asserts.fail("角色不存在: " + roleId);
        }
        //去除null并去重，保持顺序；空列表表示清空该角色全部资源
        List<Long> distinctResourceIds = distinctIds(resourceIds);
        //校验资源ID是否都有效，存在任意无效ID则整单拒绝
        if (!distinctResourceIds.isEmpty()) {
            UmsResourceExample resourceExample = new UmsResourceExample();
            resourceExample.createCriteria().andIdIn(distinctResourceIds);
            long existCount = resourceMapper.countByExample(resourceExample);
            if (existCount != distinctResourceIds.size()) {
                List<Long> existIds = resourceMapper.selectByExample(resourceExample).stream()
                        .map(UmsResource::getId).collect(Collectors.toList());
                List<Long> invalidIds = distinctResourceIds.stream()
                        .filter(id -> !existIds.contains(id)).collect(Collectors.toList());
                Asserts.fail("包含无效的资源ID: " + invalidIds);
            }
        }
        //删除原有关系
        UmsRoleResourceRelationExample example = new UmsRoleResourceRelationExample();
        example.createCriteria().andRoleIdEqualTo(roleId);
        roleResourceRelationMapper.deleteByExample(example);
        //批量插入去重后的新关系（空列表已是清空，无需插入，避免foreach空VALUES）
        if (!distinctResourceIds.isEmpty()) {
            List<UmsRoleResourceRelation> relationList = distinctResourceIds.stream().map(resourceId -> {
                UmsRoleResourceRelation relation = new UmsRoleResourceRelation();
                relation.setRoleId(roleId);
                relation.setResourceId(resourceId);
                return relation;
            }).collect(Collectors.toList());
            roleDao.insertRoleResourceRelationList(relationList);
        }
        //资源变更后失效缓存：覆盖当前角色下所有受影响管理员的资源列表缓存（含清空场景，无条件执行）
        adminCacheService.delResourceListByRole(roleId);
        return distinctResourceIds.size();
    }

    /**
     * 去除集合中的null元素并去重，保持原有顺序；入参为null或空时返回空列表
     */
    private List<Long> distinctIds(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return new ArrayList<>();
        }
        return ids.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }
}
