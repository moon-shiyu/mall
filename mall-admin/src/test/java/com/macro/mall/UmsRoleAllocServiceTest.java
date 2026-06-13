package com.macro.mall;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.dao.UmsRoleDao;
import com.macro.mall.mapper.UmsMenuMapper;
import com.macro.mall.mapper.UmsResourceMapper;
import com.macro.mall.mapper.UmsRoleMapper;
import com.macro.mall.mapper.UmsRoleMenuRelationMapper;
import com.macro.mall.mapper.UmsRoleResourceRelationMapper;
import com.macro.mall.model.UmsMenu;
import com.macro.mall.model.UmsMenuExample;
import com.macro.mall.model.UmsResource;
import com.macro.mall.model.UmsResourceExample;
import com.macro.mall.model.UmsRole;
import com.macro.mall.model.UmsRoleMenuRelation;
import com.macro.mall.model.UmsRoleMenuRelationExample;
import com.macro.mall.model.UmsRoleResourceRelation;
import com.macro.mall.model.UmsRoleResourceRelationExample;
import com.macro.mall.service.UmsAdminCacheService;
import com.macro.mall.service.impl.UmsRoleServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link UmsRoleServiceImpl#allocMenu} / {@link UmsRoleServiceImpl#allocResource} 的聚焦单元测试。
 * 纯 Mockito，无需 MySQL/Redis，覆盖：空列表(清空)、重复ID去重、无效ID整单拒绝、
 * 无效/空 roleId、正常分配、以及分配资源后的缓存失效。
 */
@ExtendWith(MockitoExtension.class)
class UmsRoleAllocServiceTest {

    @Mock
    private UmsRoleMapper roleMapper;
    @Mock
    private UmsRoleMenuRelationMapper roleMenuRelationMapper;
    @Mock
    private UmsRoleResourceRelationMapper roleResourceRelationMapper;
    @Mock
    private UmsRoleDao roleDao;
    @Mock
    private UmsAdminCacheService adminCacheService;
    @Mock
    private UmsMenuMapper menuMapper;
    @Mock
    private UmsResourceMapper resourceMapper;

    @InjectMocks
    private UmsRoleServiceImpl roleService;

    private UmsResource resource(long id) {
        UmsResource r = new UmsResource();
        r.setId(id);
        return r;
    }

    private UmsMenu menu(long id) {
        UmsMenu m = new UmsMenu();
        m.setId(id);
        return m;
    }

    // ---------------- allocResource ----------------

    @Test
    void allocResource_emptyList_clearsAll_returnsZero() {
        when(roleMapper.selectByPrimaryKey(1L)).thenReturn(new UmsRole());

        int count = roleService.allocResource(1L, Collections.emptyList());

        assertEquals(0, count);
        // 仍删除旧关系（清空语义），但不插入新关系
        verify(roleResourceRelationMapper).deleteByExample(any(UmsRoleResourceRelationExample.class));
        verify(roleDao, never()).insertRoleResourceRelationList(anyList());
        // 清空也必须失效缓存
        verify(adminCacheService).delResourceListByRole(1L);
    }

    @Test
    void allocResource_nullList_clearsAll_returnsZero() {
        when(roleMapper.selectByPrimaryKey(1L)).thenReturn(new UmsRole());

        int count = roleService.allocResource(1L, null);

        assertEquals(0, count);
        verify(roleResourceRelationMapper).deleteByExample(any(UmsRoleResourceRelationExample.class));
        verify(roleDao, never()).insertRoleResourceRelationList(anyList());
        verify(adminCacheService).delResourceListByRole(1L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void allocResource_duplicateIds_dedupBeforeInsert() {
        when(roleMapper.selectByPrimaryKey(1L)).thenReturn(new UmsRole());
        // 去重后为 [1,2]，两者均存在
        when(resourceMapper.countByExample(any(UmsResourceExample.class))).thenReturn(2L);
        ArgumentCaptor<List<UmsRoleResourceRelation>> captor = ArgumentCaptor.forClass(List.class);

        int count = roleService.allocResource(1L, Arrays.asList(1L, 1L, 2L, 2L, 2L));

        assertEquals(2, count);
        verify(roleDao).insertRoleResourceRelationList(captor.capture());
        assertEquals(2, captor.getValue().size());
        verify(adminCacheService).delResourceListByRole(1L);
    }

    @Test
    void allocResource_invalidId_throws_noWrite() {
        when(roleMapper.selectByPrimaryKey(1L)).thenReturn(new UmsRole());
        // 传入 [1,99]，仅 1 存在
        when(resourceMapper.countByExample(any(UmsResourceExample.class))).thenReturn(1L);
        when(resourceMapper.selectByExample(any(UmsResourceExample.class)))
                .thenReturn(Collections.singletonList(resource(1L)));

        ApiException ex = assertThrows(ApiException.class,
                () -> roleService.allocResource(1L, Arrays.asList(1L, 99L)));
        assertTrue(ex.getMessage().contains("99"), "异常信息应包含无效ID 99");

        // 校验失败：未删除、未插入、未失效缓存（零写入）
        verify(roleResourceRelationMapper, never()).deleteByExample(any());
        verify(roleDao, never()).insertRoleResourceRelationList(anyList());
        verify(adminCacheService, never()).delResourceListByRole(anyLong());
    }

    @Test
    void allocResource_invalidRoleId_throws_noWrite() {
        when(roleMapper.selectByPrimaryKey(99999L)).thenReturn(null);

        assertThrows(ApiException.class,
                () -> roleService.allocResource(99999L, Arrays.asList(1L)));

        verify(roleResourceRelationMapper, never()).deleteByExample(any());
        verify(roleDao, never()).insertRoleResourceRelationList(anyList());
        verify(adminCacheService, never()).delResourceListByRole(anyLong());
    }

    @Test
    void allocResource_nullRoleId_throws_noWrite() {
        assertThrows(ApiException.class,
                () -> roleService.allocResource(null, Arrays.asList(1L)));

        verifyNoInteractions(roleResourceRelationMapper, roleDao);
        verify(adminCacheService, never()).delResourceListByRole(anyLong());
    }

    @Test
    @SuppressWarnings("unchecked")
    void allocResource_valid_insertsAndEvictsCache() {
        when(roleMapper.selectByPrimaryKey(5L)).thenReturn(new UmsRole());
        when(resourceMapper.countByExample(any(UmsResourceExample.class))).thenReturn(3L);
        ArgumentCaptor<List<UmsRoleResourceRelation>> captor = ArgumentCaptor.forClass(List.class);

        int count = roleService.allocResource(5L, Arrays.asList(1L, 2L, 3L));

        assertEquals(3, count);
        verify(roleResourceRelationMapper).deleteByExample(any(UmsRoleResourceRelationExample.class));
        verify(roleDao).insertRoleResourceRelationList(captor.capture());
        List<UmsRoleResourceRelation> inserted = captor.getValue();
        assertEquals(3, inserted.size());
        assertTrue(inserted.stream().allMatch(r -> r.getRoleId().equals(5L)), "所有关系应绑定到 roleId=5");
        verify(adminCacheService).delResourceListByRole(5L);
    }

    // ---------------- allocMenu ----------------

    @Test
    void allocMenu_emptyList_clearsAll_returnsZero_noCacheCall() {
        when(roleMapper.selectByPrimaryKey(1L)).thenReturn(new UmsRole());

        int count = roleService.allocMenu(1L, Collections.emptyList());

        assertEquals(0, count);
        verify(roleMenuRelationMapper).deleteByExample(any(UmsRoleMenuRelationExample.class));
        verify(roleDao, never()).insertRoleMenuRelationList(anyList());
        // 菜单不走缓存，不应触碰缓存服务
        verifyNoInteractions(adminCacheService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void allocMenu_duplicateIds_dedupBeforeInsert() {
        when(roleMapper.selectByPrimaryKey(1L)).thenReturn(new UmsRole());
        when(menuMapper.countByExample(any(UmsMenuExample.class))).thenReturn(2L);
        ArgumentCaptor<List<UmsRoleMenuRelation>> captor = ArgumentCaptor.forClass(List.class);

        int count = roleService.allocMenu(1L, Arrays.asList(7L, 7L, 8L));

        assertEquals(2, count);
        verify(roleDao).insertRoleMenuRelationList(captor.capture());
        assertEquals(2, captor.getValue().size());
    }

    @Test
    void allocMenu_invalidId_throws_noWrite() {
        when(roleMapper.selectByPrimaryKey(1L)).thenReturn(new UmsRole());
        when(menuMapper.countByExample(any(UmsMenuExample.class))).thenReturn(1L);
        when(menuMapper.selectByExample(any(UmsMenuExample.class)))
                .thenReturn(Collections.singletonList(menu(7L)));

        ApiException ex = assertThrows(ApiException.class,
                () -> roleService.allocMenu(1L, Arrays.asList(7L, 88L)));
        assertTrue(ex.getMessage().contains("88"), "异常信息应包含无效ID 88");

        verify(roleMenuRelationMapper, never()).deleteByExample(any());
        verify(roleDao, never()).insertRoleMenuRelationList(anyList());
    }

    @Test
    @SuppressWarnings("unchecked")
    void allocMenu_valid_inserts() {
        when(roleMapper.selectByPrimaryKey(5L)).thenReturn(new UmsRole());
        when(menuMapper.countByExample(any(UmsMenuExample.class))).thenReturn(2L);
        ArgumentCaptor<List<UmsRoleMenuRelation>> captor = ArgumentCaptor.forClass(List.class);

        int count = roleService.allocMenu(5L, Arrays.asList(7L, 8L));

        assertEquals(2, count);
        verify(roleMenuRelationMapper).deleteByExample(any(UmsRoleMenuRelationExample.class));
        verify(roleDao).insertRoleMenuRelationList(captor.capture());
        assertEquals(2, captor.getValue().size());
        verifyNoInteractions(adminCacheService);
    }
}
