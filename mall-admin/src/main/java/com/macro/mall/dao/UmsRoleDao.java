package com.macro.mall.dao;

import com.macro.mall.model.UmsMenu;
import com.macro.mall.model.UmsResource;
import com.macro.mall.model.UmsRoleMenuRelation;
import com.macro.mall.model.UmsRoleResourceRelation;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 后台角色管理自定义Dao
 * Created by macro on 2020/2/2.
 */
public interface UmsRoleDao {
    /**
     * 根据后台用户ID获取菜单
     */
    List<UmsMenu> getMenuList(@Param("adminId") Long adminId);
    /**
     * 根据角色ID获取菜单
     */
    List<UmsMenu> getMenuListByRoleId(@Param("roleId") Long roleId);
    /**
     * 根据角色ID获取资源
     */
    List<UmsResource> getResourceListByRoleId(@Param("roleId") Long roleId);
    /**
     * 批量插入角色-菜单关系
     */
    int insertMenuList(@Param("list") List<UmsRoleMenuRelation> list);
    /**
     * 批量插入角色-资源关系
     */
    int insertResourceList(@Param("list") List<UmsRoleResourceRelation> list);
}
