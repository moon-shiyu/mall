package com.macro.mall;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.dao.UmsRoleDao;
import com.macro.mall.mapper.UmsMenuMapper;
import com.macro.mall.mapper.UmsResourceMapper;
import com.macro.mall.mapper.UmsRoleMapper;
import com.macro.mall.mapper.UmsRoleMenuRelationMapper;
import com.macro.mall.mapper.UmsRoleResourceRelationMapper;
import com.macro.mall.model.*;
import com.macro.mall.service.UmsAdminCacheService;
import com.macro.mall.service.impl.UmsRoleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * UmsRoleServiceImpl 角色分配菜单/资源 增强逻辑测试
 *
 * 覆盖场景:
 * - 空列表 (null / empty)
 * - 重复ID去重
 * - 非法ID (roleId / menuId / resourceId)
 * - 正常分配
 * - 缓存失效时机
 * - 事务回滚语义
 */
@ExtendWith(MockitoExtension.class)
public class UmsRoleServiceTests {

    @InjectMocks
    private UmsRoleServiceImpl roleService;

    @Mock
    private UmsRoleMapper roleMapper;
    @Mock
    private UmsRoleMenuRelationMapper roleMenuRelationMapper;
    @Mock
    private UmsRoleResourceRelationMapper roleResourceRelationMapper;
    @Mock
    private UmsMenuMapper menuMapper;
    @Mock
    private UmsResourceMapper resourceMapper;
    @Mock
    private UmsRoleDao roleDao;
    @Mock
    private UmsAdminCacheService adminCacheService;

    private static final Long VALID_ROLE_ID = 1L;
    private static final Long INVALID_ROLE_ID = 999L;

    @BeforeEach
    void setUp() {
        // 默认 stub：有效角色存在
        UmsRole validRole = new UmsRole();
        validRole.setId(VALID_ROLE_ID);
        validRole.setName("管理员");
        lenient().when(roleMapper.selectByPrimaryKey(VALID_ROLE_ID)).thenReturn(validRole);
        lenient().when(roleMapper.selectByPrimaryKey(INVALID_ROLE_ID)).thenReturn(null);
    }

    // ===================================================================
    //  allocMenu 测试
    // ===================================================================
    @Nested
    @DisplayName("allocMenu - 角色分配菜单")
    class AllocMenuTests {

        @Test
        @DisplayName("空列表 - 仅清除旧关系，不插入新关系，返回0")
        void emptyMenuIds_shouldClearOnly() {
            int result = roleService.allocMenu(VALID_ROLE_ID, Collections.emptyList());

            assertEquals(0, result);
            verify(roleMenuRelationMapper).deleteByExample(any(UmsRoleMenuRelationExample.class));
            verify(roleDao, never()).insertMenuList(any());
            verify(roleMenuRelationMapper, never()).insert(any());
        }

        @Test
        @DisplayName("null列表 - 仅清除旧关系，返回0")
        void nullMenuIds_shouldClearOnly() {
            int result = roleService.allocMenu(VALID_ROLE_ID, null);

            assertEquals(0, result);
            verify(roleMenuRelationMapper).deleteByExample(any(UmsRoleMenuRelationExample.class));
            verify(roleDao, never()).insertMenuList(any());
        }

        @Test
        @DisplayName("重复ID - 去重后只插入唯一ID")
        void duplicateMenuIds_shouldDedup() {
            List<Long> menuIds = Arrays.asList(1L, 2L, 2L, 3L, 1L);
            when(menuMapper.countByExample(any(UmsMenuExample.class))).thenReturn(3L);
            when(roleDao.insertMenuList(any())).thenReturn(3);

            int result = roleService.allocMenu(VALID_ROLE_ID, menuIds);

            assertEquals(3, result);
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<UmsRoleMenuRelation>> captor = ArgumentCaptor.forClass(List.class);
            verify(roleDao).insertMenuList(captor.capture());
            List<UmsRoleMenuRelation> inserted = captor.getValue();
            assertEquals(3, inserted.size());
            // 验证去重后的ID
            List<Long> insertedMenuIds = new ArrayList<>();
            for (UmsRoleMenuRelation r : inserted) {
                insertedMenuIds.add(r.getMenuId());
            }
            assertTrue(insertedMenuIds.contains(1L));
            assertTrue(insertedMenuIds.contains(2L));
            assertTrue(insertedMenuIds.contains(3L));
        }

        @Test
        @DisplayName("列表包含null元素 - 过滤null后去重并插入")
        void menuIdsWithNulls_shouldFilterNulls() {
            List<Long> menuIds = Arrays.asList(1L, null, 2L, null, 1L);
            when(menuMapper.countByExample(any(UmsMenuExample.class))).thenReturn(2L);
            when(roleDao.insertMenuList(any())).thenReturn(2);

            int result = roleService.allocMenu(VALID_ROLE_ID, menuIds);

            assertEquals(2, result);
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<UmsRoleMenuRelation>> captor = ArgumentCaptor.forClass(List.class);
            verify(roleDao).insertMenuList(captor.capture());
            assertEquals(2, captor.getValue().size());
        }

        @Test
        @DisplayName("无效roleId - 抛出ApiException")
        void invalidRoleId_shouldThrow() {
            ApiException ex = assertThrows(ApiException.class,
                    () -> roleService.allocMenu(INVALID_ROLE_ID, Arrays.asList(1L)));
            assertTrue(ex.getMessage().contains("角色不存在"));
        }

        @Test
        @DisplayName("null roleId - 抛出ApiException")
        void nullRoleId_shouldThrow() {
            ApiException ex = assertThrows(ApiException.class,
                    () -> roleService.allocMenu(null, Arrays.asList(1L)));
            assertTrue(ex.getMessage().contains("角色ID不能为空"));
        }

        @Test
        @DisplayName("无效menuId - 抛出ApiException且不插入")
        void invalidMenuIds_shouldThrow() {
            // 模拟数据库只存在1个菜单(ID=1)，但传入了[1, 999]
            when(menuMapper.countByExample(any(UmsMenuExample.class))).thenReturn(1L);

            ApiException ex = assertThrows(ApiException.class,
                    () -> roleService.allocMenu(VALID_ROLE_ID, Arrays.asList(1L, 999L)));
            assertTrue(ex.getMessage().contains("菜单ID无效"));
            // 验证没有执行批量插入（事务应当回滚）
            verify(roleDao, never()).insertMenuList(any());
        }

        @Test
        @DisplayName("正常分配 - 删除旧关系后批量插入新关系")
        void normalAllocation_shouldDeleteAndBatchInsert() {
            List<Long> menuIds = Arrays.asList(10L, 20L, 30L);
            when(menuMapper.countByExample(any(UmsMenuExample.class))).thenReturn(3L);
            when(roleDao.insertMenuList(any())).thenReturn(3);

            int result = roleService.allocMenu(VALID_ROLE_ID, menuIds);

            assertEquals(3, result);
            // 验证先删除
            verify(roleMenuRelationMapper).deleteByExample(any(UmsRoleMenuRelationExample.class));
            // 验证批量插入（而非逐条insert）
            verify(roleDao).insertMenuList(any());
            verify(roleMenuRelationMapper, never()).insert(any());
        }

        @Test
        @DisplayName("使用批量插入而非逐条insert")
        void normalAllocation_shouldUseBatchInsert() {
            List<Long> menuIds = Arrays.asList(1L, 2L);
            when(menuMapper.countByExample(any(UmsMenuExample.class))).thenReturn(2L);
            when(roleDao.insertMenuList(any())).thenReturn(2);

            roleService.allocMenu(VALID_ROLE_ID, menuIds);

            // 逐条 insert 不应被调用
            verify(roleMenuRelationMapper, never()).insert(any(UmsRoleMenuRelation.class));
            // 批量 insertMenuList 应被调用一次
            verify(roleDao, times(1)).insertMenuList(any());
        }
    }

    // ===================================================================
    //  allocResource 测试
    // ===================================================================
    @Nested
    @DisplayName("allocResource - 角色分配资源")
    class AllocResourceTests {

        @Test
        @DisplayName("空列表 - 仅清除旧关系并使缓存失效")
        void emptyResourceIds_shouldClearAndInvalidateCache() {
            roleService.allocResource(VALID_ROLE_ID, Collections.emptyList());

            verify(roleResourceRelationMapper).deleteByExample(any(UmsRoleResourceRelationExample.class));
            verify(roleDao, never()).insertResourceList(any());
            // 空列表场景仍然需要使缓存失效
            verify(adminCacheService).delResourceListByRole(VALID_ROLE_ID);
        }

        @Test
        @DisplayName("null列表 - 仅清除旧关系并使缓存失效")
        void nullResourceIds_shouldClearAndInvalidateCache() {
            roleService.allocResource(VALID_ROLE_ID, null);

            verify(roleResourceRelationMapper).deleteByExample(any(UmsRoleResourceRelationExample.class));
            verify(roleDao, never()).insertResourceList(any());
            verify(adminCacheService).delResourceListByRole(VALID_ROLE_ID);
        }

        @Test
        @DisplayName("重复ID - 去重后只插入唯一ID")
        void duplicateResourceIds_shouldDedup() {
            List<Long> resourceIds = Arrays.asList(1L, 1L, 2L, 3L, 3L);
            when(resourceMapper.countByExample(any(UmsResourceExample.class))).thenReturn(3L);
            when(roleDao.insertResourceList(any())).thenReturn(3);

            int result = roleService.allocResource(VALID_ROLE_ID, resourceIds);

            assertEquals(3, result);
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<UmsRoleResourceRelation>> captor = ArgumentCaptor.forClass(List.class);
            verify(roleDao).insertResourceList(captor.capture());
            assertEquals(3, captor.getValue().size());
        }

        @Test
        @DisplayName("无效roleId - 抛出ApiException且不操作关系表")
        void invalidRoleId_shouldThrow() {
            ApiException ex = assertThrows(ApiException.class,
                    () -> roleService.allocResource(INVALID_ROLE_ID, Arrays.asList(1L)));
            assertTrue(ex.getMessage().contains("角色不存在"));
            verify(roleResourceRelationMapper, never()).deleteByExample(any());
            verify(roleDao, never()).insertResourceList(any());
        }

        @Test
        @DisplayName("null roleId - 抛出ApiException")
        void nullRoleId_shouldThrow() {
            ApiException ex = assertThrows(ApiException.class,
                    () -> roleService.allocResource(null, Arrays.asList(1L)));
            assertTrue(ex.getMessage().contains("角色ID不能为空"));
        }

        @Test
        @DisplayName("无效resourceId - 抛出ApiException且不插入")
        void invalidResourceIds_shouldThrow() {
            when(resourceMapper.countByExample(any(UmsResourceExample.class))).thenReturn(1L);

            ApiException ex = assertThrows(ApiException.class,
                    () -> roleService.allocResource(VALID_ROLE_ID, Arrays.asList(1L, 888L)));
            assertTrue(ex.getMessage().contains("资源ID无效"));
            verify(roleDao, never()).insertResourceList(any());
        }

        @Test
        @DisplayName("正常分配 - 删除旧关系后批量插入并使缓存失效")
        void normalAllocation_shouldDeleteBatchInsertAndInvalidateCache() {
            List<Long> resourceIds = Arrays.asList(10L, 20L);
            when(resourceMapper.countByExample(any(UmsResourceExample.class))).thenReturn(2L);
            when(roleDao.insertResourceList(any())).thenReturn(2);

            int result = roleService.allocResource(VALID_ROLE_ID, resourceIds);

            assertEquals(2, result);
            verify(roleResourceRelationMapper).deleteByExample(any(UmsRoleResourceRelationExample.class));
            verify(roleDao).insertResourceList(any());
            verify(adminCacheService).delResourceListByRole(VALID_ROLE_ID);
            // 逐条 insert 不应被调用
            verify(roleResourceRelationMapper, never()).insert(any());
        }

        @Test
        @DisplayName("列表包含null元素 - 过滤null后去重并插入")
        void resourceIdsWithNulls_shouldFilterNulls() {
            List<Long> resourceIds = Arrays.asList(1L, null, 2L);
            when(resourceMapper.countByExample(any(UmsResourceExample.class))).thenReturn(2L);
            when(roleDao.insertResourceList(any())).thenReturn(2);

            int result = roleService.allocResource(VALID_ROLE_ID, resourceIds);

            assertEquals(2, result);
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<UmsRoleResourceRelation>> captor = ArgumentCaptor.forClass(List.class);
            verify(roleDao).insertResourceList(captor.capture());
            assertEquals(2, captor.getValue().size());
        }
    }

    // ===================================================================
    //  缓存失效 & 事务语义 测试
    // ===================================================================
    @Nested
    @DisplayName("缓存失效与事务语义")
    class CacheAndTransactionTests {

        @Test
        @DisplayName("allocResource 无活跃事务时 - 直接调用缓存失效")
        void allocResource_noTransaction_shouldCallCacheDirectly() {
            // 确保无活跃事务同步
            assertFalse(TransactionSynchronizationManager.isSynchronizationActive());

            List<Long> resourceIds = Arrays.asList(1L, 2L);
            when(resourceMapper.countByExample(any(UmsResourceExample.class))).thenReturn(2L);
            when(roleDao.insertResourceList(any())).thenReturn(2);

            roleService.allocResource(VALID_ROLE_ID, resourceIds);

            // 无事务时直接调用缓存失效
            verify(adminCacheService).delResourceListByRole(VALID_ROLE_ID);
        }

        @Test
        @DisplayName("allocResource 有活跃事务时 - 注册afterCommit回调")
        void allocResource_withTransaction_shouldRegisterAfterCommitCallback() {
            // 初始化事务同步管理器
            TransactionSynchronizationManager.initSynchronization();
            try {
                List<Long> resourceIds = Arrays.asList(1L, 2L);
                when(resourceMapper.countByExample(any(UmsResourceExample.class))).thenReturn(2L);
                when(roleDao.insertResourceList(any())).thenReturn(2);

                roleService.allocResource(VALID_ROLE_ID, resourceIds);

                // 事务中不应立即调用缓存失效（应延迟到afterCommit）
                verify(adminCacheService, never()).delResourceListByRole(VALID_ROLE_ID);
            } finally {
                if (TransactionSynchronizationManager.isSynchronizationActive()) {
                    TransactionSynchronizationManager.clearSynchronization();
                }
            }
        }

        @Test
        @DisplayName("allocResource 空列表有活跃事务时 - 也注册afterCommit回调")
        void allocResource_emptyListWithTransaction_shouldRegisterCallback() {
            TransactionSynchronizationManager.initSynchronization();
            try {
                roleService.allocResource(VALID_ROLE_ID, Collections.emptyList());

                // 事务中延迟到afterCommit
                verify(adminCacheService, never()).delResourceListByRole(VALID_ROLE_ID);
            } finally {
                if (TransactionSynchronizationManager.isSynchronizationActive()) {
                    TransactionSynchronizationManager.clearSynchronization();
                }
            }
        }

        @Test
        @DisplayName("allocMenu 不调用资源缓存失效 (菜单不缓存到Redis)")
        void allocMenu_shouldNotInvalidateResourceCache() {
            List<Long> menuIds = Arrays.asList(1L, 2L);
            when(menuMapper.countByExample(any(UmsMenuExample.class))).thenReturn(2L);
            when(roleDao.insertMenuList(any())).thenReturn(2);

            roleService.allocMenu(VALID_ROLE_ID, menuIds);

            // allocMenu不应触发资源缓存失效
            verify(adminCacheService, never()).delResourceListByRole(any());
            verify(adminCacheService, never()).delResourceListByRoleIds(any());
        }

        @Test
        @DisplayName("无效roleId - 不触发任何数据库写操作和缓存失效")
        void invalidRoleId_noWriteNoCacheInvalidation() {
            assertThrows(ApiException.class,
                    () -> roleService.allocResource(INVALID_ROLE_ID, Arrays.asList(1L, 2L)));

            verify(roleResourceRelationMapper, never()).deleteByExample(any());
            verify(roleDao, never()).insertResourceList(any());
            verify(adminCacheService, never()).delResourceListByRole(any());
        }

        @Test
        @DisplayName("无效resourceId - 已删除的旧关系无法恢复(需事务回滚)")
        void invalidResourceIds_shouldNotInsert_butDeleteAlreadyHappened() {
            when(resourceMapper.countByExample(any(UmsResourceExample.class))).thenReturn(1L);

            assertThrows(ApiException.class,
                    () -> roleService.allocResource(VALID_ROLE_ID, Arrays.asList(1L, 999L)));

            // delete 已执行 - 这证明需要 @Transactional 回滚来保证原子性
            verify(roleResourceRelationMapper).deleteByExample(any(UmsRoleResourceRelationExample.class));
            // 但 insert 不应执行
            verify(roleDao, never()).insertResourceList(any());
            // 缓存不应失效（因为事务会回滚）
            verify(adminCacheService, never()).delResourceListByRole(any());
        }
    }
}
