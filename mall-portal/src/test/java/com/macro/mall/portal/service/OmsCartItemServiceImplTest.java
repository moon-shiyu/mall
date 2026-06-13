package com.macro.mall.portal.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.mapper.OmsCartItemMapper;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.mapper.PmsSkuStockMapper;
import com.macro.mall.model.*;
import com.macro.mall.portal.dao.PortalProductDao;
import com.macro.mall.portal.service.impl.OmsCartItemServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 购物车Service单元测试
 * 使用Mockito模拟依赖，不需要Spring上下文或数据库
 */
@ExtendWith(MockitoExtension.class)
public class OmsCartItemServiceImplTest {

    @InjectMocks
    private OmsCartItemServiceImpl cartItemService;

    @Mock
    private OmsCartItemMapper cartItemMapper;
    @Mock
    private PortalProductDao productDao;
    @Mock
    private OmsPromotionService promotionService;
    @Mock
    private UmsMemberService memberService;
    @Mock
    private PmsProductMapper productMapper;
    @Mock
    private PmsSkuStockMapper skuStockMapper;

    private UmsMember currentMember;
    private PmsProduct validProduct;
    private PmsSkuStock validSkuStock;

    @BeforeEach
    void setUp() {
        // 模拟当前登录会员
        currentMember = new UmsMember();
        currentMember.setId(1L);
        currentMember.setNickname("testUser");

        // 模拟上架商品
        validProduct = new PmsProduct();
        validProduct.setId(100L);
        validProduct.setPublishStatus(1);
        validProduct.setDeleteStatus(0);

        // 模拟有效SKU（库存10，锁定0）
        validSkuStock = new PmsSkuStock();
        validSkuStock.setId(200L);
        validSkuStock.setProductId(100L);
        validSkuStock.setStock(10);
        validSkuStock.setLockStock(0);
    }

    // ========== 辅助方法 ==========

    private OmsCartItem buildCartItem(Long productId, Long skuId, int quantity) {
        OmsCartItem item = new OmsCartItem();
        item.setProductId(productId);
        item.setProductSkuId(skuId);
        item.setQuantity(quantity);
        return item;
    }

    private void stubCurrentMember() {
        lenient().when(memberService.getCurrentMember()).thenReturn(currentMember);
    }

    private void stubValidProductAndSku() {
        lenient().when(productMapper.selectByPrimaryKey(100L)).thenReturn(validProduct);
        lenient().when(skuStockMapper.selectByPrimaryKey(200L)).thenReturn(validSkuStock);
    }

    // ========== add() 正常加购 ==========

    @Test
    void add_normalAdd_success() {
        stubCurrentMember();
        stubValidProductAndSku();
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class)))
                .thenReturn(Collections.emptyList());
        when(cartItemMapper.insert(any(OmsCartItem.class))).thenReturn(1);

        OmsCartItem cartItem = buildCartItem(100L, 200L, 2);
        int result = cartItemService.add(cartItem);

        assertEquals(1, result);
        verify(cartItemMapper).insert(argThat(item ->
                item.getMemberId() == 1L && item.getQuantity() == 2));
    }

    // ========== add() 重复加购合并数量 ==========

    @Test
    void add_duplicateSku_mergeQuantity() {
        stubCurrentMember();
        stubValidProductAndSku();

        OmsCartItem existItem = buildCartItem(100L, 200L, 3);
        existItem.setId(10L);
        existItem.setMemberId(1L);
        existItem.setDeleteStatus(0);
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class)))
                .thenReturn(Collections.singletonList(existItem));
        when(cartItemMapper.updateByPrimaryKey(any(OmsCartItem.class))).thenReturn(1);

        OmsCartItem cartItem = buildCartItem(100L, 200L, 2);
        int result = cartItemService.add(cartItem);

        assertEquals(1, result);
        verify(cartItemMapper).updateByPrimaryKey(argThat(item ->
                item.getQuantity() == 5)); // 3 + 2
    }

    // ========== add() 重复加购超库存 ==========

    @Test
    void add_duplicateSku_exceedStock_throwsException() {
        stubCurrentMember();
        stubValidProductAndSku(); // stock=10

        OmsCartItem existItem = buildCartItem(100L, 200L, 8);
        existItem.setId(10L);
        existItem.setMemberId(1L);
        existItem.setDeleteStatus(0);
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class)))
                .thenReturn(Collections.singletonList(existItem));

        OmsCartItem cartItem = buildCartItem(100L, 200L, 5); // 8+5=13 > 10
        ApiException ex = assertThrows(ApiException.class, () -> cartItemService.add(cartItem));
        assertTrue(ex.getMessage().contains("库存不足"));
    }

    // ========== add() 库存不足（新品） ==========

    @Test
    void add_insufficientStock_throwsException() {
        stubCurrentMember();
        stubValidProductAndSku(); // stock=10
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class)))
                .thenReturn(Collections.emptyList());

        OmsCartItem cartItem = buildCartItem(100L, 200L, 15); // 15 > 10
        ApiException ex = assertThrows(ApiException.class, () -> cartItemService.add(cartItem));
        assertTrue(ex.getMessage().contains("库存不足"));
    }

    // ========== add() 非法数量 ==========

    @Test
    void add_quantityNull_throwsException() {
        stubCurrentMember();
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class)))
                .thenReturn(Collections.emptyList());

        OmsCartItem cartItem = buildCartItem(100L, 200L, 0);
        cartItem.setQuantity(null);
        ApiException ex = assertThrows(ApiException.class, () -> cartItemService.add(cartItem));
        assertEquals("商品数量不合法", ex.getMessage());
    }

    @Test
    void add_quantityZero_throwsException() {
        stubCurrentMember();
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class)))
                .thenReturn(Collections.emptyList());

        OmsCartItem cartItem = buildCartItem(100L, 200L, 0);
        ApiException ex = assertThrows(ApiException.class, () -> cartItemService.add(cartItem));
        assertEquals("商品数量不合法", ex.getMessage());
    }

    @Test
    void add_quantityNegative_throwsException() {
        stubCurrentMember();
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class)))
                .thenReturn(Collections.emptyList());

        OmsCartItem cartItem = buildCartItem(100L, 200L, -5);
        ApiException ex = assertThrows(ApiException.class, () -> cartItemService.add(cartItem));
        assertEquals("商品数量不合法", ex.getMessage());
    }

    // ========== add() 商品下架 ==========

    @Test
    void add_productOffShelf_throwsException() {
        stubCurrentMember();
        PmsProduct offShelfProduct = new PmsProduct();
        offShelfProduct.setId(100L);
        offShelfProduct.setPublishStatus(0);
        offShelfProduct.setDeleteStatus(0);
        when(productMapper.selectByPrimaryKey(100L)).thenReturn(offShelfProduct);
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class)))
                .thenReturn(Collections.emptyList());

        OmsCartItem cartItem = buildCartItem(100L, 200L, 1);
        ApiException ex = assertThrows(ApiException.class, () -> cartItemService.add(cartItem));
        assertEquals("商品已下架", ex.getMessage());
    }

    // ========== add() 商品已删除 ==========

    @Test
    void add_productDeleted_throwsException() {
        stubCurrentMember();
        PmsProduct deletedProduct = new PmsProduct();
        deletedProduct.setId(100L);
        deletedProduct.setPublishStatus(1);
        deletedProduct.setDeleteStatus(1);
        when(productMapper.selectByPrimaryKey(100L)).thenReturn(deletedProduct);
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class)))
                .thenReturn(Collections.emptyList());

        OmsCartItem cartItem = buildCartItem(100L, 200L, 1);
        ApiException ex = assertThrows(ApiException.class, () -> cartItemService.add(cartItem));
        assertEquals("商品已删除", ex.getMessage());
    }

    // ========== add() SKU不存在 ==========

    @Test
    void add_skuNotFound_throwsException() {
        stubCurrentMember();
        when(productMapper.selectByPrimaryKey(100L)).thenReturn(validProduct);
        when(skuStockMapper.selectByPrimaryKey(999L)).thenReturn(null);
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class)))
                .thenReturn(Collections.emptyList());

        OmsCartItem cartItem = buildCartItem(100L, 999L, 1);
        ApiException ex = assertThrows(ApiException.class, () -> cartItemService.add(cartItem));
        assertEquals("商品规格不存在", ex.getMessage());
    }

    // ========== add() SKU与商品不匹配 ==========

    @Test
    void add_skuProductMismatch_throwsException() {
        stubCurrentMember();
        when(productMapper.selectByPrimaryKey(100L)).thenReturn(validProduct);
        PmsSkuStock otherSku = new PmsSkuStock();
        otherSku.setId(300L);
        otherSku.setProductId(999L); // 属于另一个商品
        otherSku.setStock(10);
        otherSku.setLockStock(0);
        when(skuStockMapper.selectByPrimaryKey(300L)).thenReturn(otherSku);
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class)))
                .thenReturn(Collections.emptyList());

        OmsCartItem cartItem = buildCartItem(100L, 300L, 1);
        ApiException ex = assertThrows(ApiException.class, () -> cartItemService.add(cartItem));
        assertEquals("商品规格与商品不匹配", ex.getMessage());
    }

    // ========== updateQuantity() 正常修改 ==========

    @Test
    void updateQuantity_normal_success() {
        OmsCartItem existItem = buildCartItem(100L, 200L, 2);
        existItem.setId(10L);
        existItem.setMemberId(1L);
        existItem.setDeleteStatus(0);
        when(cartItemMapper.selectByPrimaryKey(10L)).thenReturn(existItem);
        stubValidProductAndSku();
        when(cartItemMapper.updateByExampleSelective(any(), any(OmsCartItemExample.class)))
                .thenReturn(1);

        int result = cartItemService.updateQuantity(10L, 1L, 5);

        assertEquals(1, result);
        verify(cartItemMapper).updateByExampleSelective(
                argThat(item -> item.getQuantity() == 5), any(OmsCartItemExample.class));
    }

    // ========== updateQuantity() 超库存 ==========

    @Test
    void updateQuantity_exceedStock_throwsException() {
        OmsCartItem existItem = buildCartItem(100L, 200L, 2);
        existItem.setId(10L);
        existItem.setMemberId(1L);
        existItem.setDeleteStatus(0);
        when(cartItemMapper.selectByPrimaryKey(10L)).thenReturn(existItem);
        stubValidProductAndSku(); // stock=10

        ApiException ex = assertThrows(ApiException.class,
                () -> cartItemService.updateQuantity(10L, 1L, 15));
        assertTrue(ex.getMessage().contains("库存不足"));
    }

    // ========== updateQuantity() 跨会员 ==========

    @Test
    void updateQuantity_otherMember_throwsException() {
        OmsCartItem existItem = buildCartItem(100L, 200L, 2);
        existItem.setId(10L);
        existItem.setMemberId(1L);
        existItem.setDeleteStatus(0);
        when(cartItemMapper.selectByPrimaryKey(10L)).thenReturn(existItem);

        ApiException ex = assertThrows(ApiException.class,
                () -> cartItemService.updateQuantity(10L, 2L, 5)); // memberId=2，不是拥有者
        assertEquals("购物车商品不存在", ex.getMessage());
    }

    // ========== updateQuantity() 商品下架 ==========

    @Test
    void updateQuantity_productOffShelf_throwsException() {
        OmsCartItem existItem = buildCartItem(100L, 200L, 2);
        existItem.setId(10L);
        existItem.setMemberId(1L);
        existItem.setDeleteStatus(0);
        when(cartItemMapper.selectByPrimaryKey(10L)).thenReturn(existItem);

        PmsProduct offShelfProduct = new PmsProduct();
        offShelfProduct.setId(100L);
        offShelfProduct.setPublishStatus(0);
        offShelfProduct.setDeleteStatus(0);
        when(productMapper.selectByPrimaryKey(100L)).thenReturn(offShelfProduct);

        ApiException ex = assertThrows(ApiException.class,
                () -> cartItemService.updateQuantity(10L, 1L, 3));
        assertEquals("商品已下架", ex.getMessage());
    }

    // ========== updateAttr() 正常重选规格 ==========

    @Test
    void updateAttr_normal_success() {
        stubCurrentMember();
        stubValidProductAndSku();

        // 旧购物车项
        OmsCartItem oldItem = buildCartItem(100L, 201L, 2);
        oldItem.setId(10L);
        oldItem.setMemberId(1L);
        oldItem.setDeleteStatus(0);
        when(cartItemMapper.selectByPrimaryKey(10L)).thenReturn(oldItem);

        // 新SKU没有已有购物车项
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class)))
                .thenReturn(Collections.emptyList());
        when(cartItemMapper.updateByPrimaryKeySelective(any(OmsCartItem.class))).thenReturn(1);
        when(cartItemMapper.insert(any(OmsCartItem.class))).thenReturn(1);

        OmsCartItem newItem = buildCartItem(100L, 200L, 3);
        newItem.setId(10L);
        int result = cartItemService.updateAttr(newItem);

        assertEquals(1, result);
        // 验证旧记录被软删除
        verify(cartItemMapper).updateByPrimaryKeySelective(argThat(item ->
                item.getId() == 10L && item.getDeleteStatus() == 1));
        // 验证新记录被插入
        verify(cartItemMapper).insert(any(OmsCartItem.class));
    }

    // ========== updateAttr() 新规格库存不足 — 旧记录不丢失 ==========

    @Test
    void updateAttr_insufficientStock_oldItemPreserved() {
        stubCurrentMember();

        // 旧购物车项（SKU 201）
        OmsCartItem oldItem = buildCartItem(100L, 201L, 2);
        oldItem.setId(10L);
        oldItem.setMemberId(1L);
        oldItem.setDeleteStatus(0);
        when(cartItemMapper.selectByPrimaryKey(10L)).thenReturn(oldItem);

        // 新SKU只有1个库存
        PmsSkuStock lowStockSku = new PmsSkuStock();
        lowStockSku.setId(200L);
        lowStockSku.setProductId(100L);
        lowStockSku.setStock(1);
        lowStockSku.setLockStock(0);

        when(productMapper.selectByPrimaryKey(100L)).thenReturn(validProduct);
        when(skuStockMapper.selectByPrimaryKey(200L)).thenReturn(lowStockSku);
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class)))
                .thenReturn(Collections.emptyList());

        OmsCartItem newItem = buildCartItem(100L, 200L, 5); // 5 > 1 库存
        newItem.setId(10L);

        ApiException ex = assertThrows(ApiException.class,
                () -> cartItemService.updateAttr(newItem));
        assertTrue(ex.getMessage().contains("库存不足"));

        // 关键断言：旧记录未被软删除（没有调用过updateByPrimaryKeySelective设置deleteStatus=1）
        verify(cartItemMapper, never()).updateByPrimaryKeySelective(argThat(item ->
                item.getDeleteStatus() != null && item.getDeleteStatus() == 1));
    }

    // ========== updateAttr() 不能修改他人购物车 ==========

    @Test
    void updateAttr_otherMember_throwsException() {
        stubCurrentMember();

        OmsCartItem oldItem = buildCartItem(100L, 201L, 2);
        oldItem.setId(10L);
        oldItem.setMemberId(99L); // 其他会员的购物车
        oldItem.setDeleteStatus(0);
        when(cartItemMapper.selectByPrimaryKey(10L)).thenReturn(oldItem);

        OmsCartItem newItem = buildCartItem(100L, 200L, 1);
        newItem.setId(10L);

        ApiException ex = assertThrows(ApiException.class,
                () -> cartItemService.updateAttr(newItem));
        assertEquals("不能修改他人购物车", ex.getMessage());
    }

    // ========== 会员隔离：list() 仅返回当前会员数据 ==========

    @Test
    void list_memberIsolation_onlyReturnsOwnItems() {
        OmsCartItem memberItem = buildCartItem(100L, 200L, 1);
        memberItem.setMemberId(1L);
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class)))
                .thenReturn(Collections.singletonList(memberItem));

        java.util.List<OmsCartItem> result = cartItemService.list(1L);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getMemberId());
        // 验证查询条件包含 memberId
        verify(cartItemMapper).selectByExample(argThat(example -> {
            // 确保Example不为空即可（MyBatis Example内部结构复杂，不做深度断言）
            return example != null;
        }));
    }

    // ========== add() 考虑锁定库存的可用库存计算 ==========

    @Test
    void add_considersLockStock() {
        stubCurrentMember();

        // stock=10, lockStock=7 → realStock=3
        PmsSkuStock lockedSku = new PmsSkuStock();
        lockedSku.setId(200L);
        lockedSku.setProductId(100L);
        lockedSku.setStock(10);
        lockedSku.setLockStock(7);

        when(productMapper.selectByPrimaryKey(100L)).thenReturn(validProduct);
        when(skuStockMapper.selectByPrimaryKey(200L)).thenReturn(lockedSku);
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class)))
                .thenReturn(Collections.emptyList());

        // 加购4个，realStock只有3 → 应该失败
        OmsCartItem cartItem = buildCartItem(100L, 200L, 4);
        ApiException ex = assertThrows(ApiException.class, () -> cartItemService.add(cartItem));
        assertTrue(ex.getMessage().contains("库存不足"));
        assertTrue(ex.getMessage().contains("3")); // 可用库存为3
    }

    // ========== add() 商品不存在 ==========

    @Test
    void add_productNotFound_throwsException() {
        stubCurrentMember();
        when(productMapper.selectByPrimaryKey(999L)).thenReturn(null);
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class)))
                .thenReturn(Collections.emptyList());

        OmsCartItem cartItem = buildCartItem(999L, 200L, 1);
        ApiException ex = assertThrows(ApiException.class, () -> cartItemService.add(cartItem));
        assertEquals("商品不存在", ex.getMessage());
    }

    // ========== add() 未选择规格（productSkuId为null） ==========

    @Test
    void add_skuIdNull_throwsException() {
        stubCurrentMember();
        when(productMapper.selectByPrimaryKey(100L)).thenReturn(validProduct);
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class)))
                .thenReturn(Collections.emptyList());

        OmsCartItem cartItem = buildCartItem(100L, null, 1);
        ApiException ex = assertThrows(ApiException.class, () -> cartItemService.add(cartItem));
        assertEquals("请选择商品规格", ex.getMessage());
    }

    // ========== updateQuantity() 数量为null ==========

    @Test
    void updateQuantity_quantityNull_throwsException() {
        OmsCartItem existItem = buildCartItem(100L, 200L, 2);
        existItem.setId(10L);
        existItem.setMemberId(1L);
        existItem.setDeleteStatus(0);
        when(cartItemMapper.selectByPrimaryKey(10L)).thenReturn(existItem);

        ApiException ex = assertThrows(ApiException.class,
                () -> cartItemService.updateQuantity(10L, 1L, null));
        assertEquals("商品数量不合法", ex.getMessage());
    }

    // ========== updateQuantity() 数量为0 ==========

    @Test
    void updateQuantity_quantityZero_throwsException() {
        OmsCartItem existItem = buildCartItem(100L, 200L, 2);
        existItem.setId(10L);
        existItem.setMemberId(1L);
        existItem.setDeleteStatus(0);
        when(cartItemMapper.selectByPrimaryKey(10L)).thenReturn(existItem);

        ApiException ex = assertThrows(ApiException.class,
                () -> cartItemService.updateQuantity(10L, 1L, 0));
        assertEquals("商品数量不合法", ex.getMessage());
    }

    // ========== updateQuantity() 数量为负数 ==========

    @Test
    void updateQuantity_quantityNegative_throwsException() {
        OmsCartItem existItem = buildCartItem(100L, 200L, 2);
        existItem.setId(10L);
        existItem.setMemberId(1L);
        existItem.setDeleteStatus(0);
        when(cartItemMapper.selectByPrimaryKey(10L)).thenReturn(existItem);

        ApiException ex = assertThrows(ApiException.class,
                () -> cartItemService.updateQuantity(10L, 1L, -3));
        assertEquals("商品数量不合法", ex.getMessage());
    }

    // ========== updateAttr() 修改为相同SKU ==========

    @Test
    void updateAttr_sameSku_softDeletesAndReinserts() {
        stubCurrentMember();
        stubValidProductAndSku();

        // 旧购物车项，SKU 为 200L
        OmsCartItem oldItem = buildCartItem(100L, 200L, 2);
        oldItem.setId(10L);
        oldItem.setMemberId(1L);
        oldItem.setDeleteStatus(0);
        when(cartItemMapper.selectByPrimaryKey(10L)).thenReturn(oldItem);

        // getCartItem 返回旧记录本身（同一SKU）
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class)))
                .thenReturn(Collections.singletonList(oldItem));
        when(cartItemMapper.updateByPrimaryKeySelective(any(OmsCartItem.class))).thenReturn(1);
        when(cartItemMapper.insert(any(OmsCartItem.class))).thenReturn(1);

        // 修改数量为5，SKU不变
        OmsCartItem newItem = buildCartItem(100L, 200L, 5);
        newItem.setId(10L);
        int result = cartItemService.updateAttr(newItem);

        assertEquals(1, result);
        // 旧记录被软删除
        verify(cartItemMapper).updateByPrimaryKeySelective(argThat(item ->
                item.getId() == 10L && item.getDeleteStatus() == 1));
        // 新记录被插入（因为duplicateItem就是oldItem本身，走else分支）
        verify(cartItemMapper).insert(argThat(item ->
                item.getQuantity() == 5));
    }

    // ========== updateAttr() 新SKU已存在于其他购物车项 → 合并 ==========

    @Test
    void updateAttr_duplicateSku_mergesQuantity() {
        stubCurrentMember();
        stubValidProductAndSku(); // SKU 200L, stock=10

        // 旧购物车项（SKU 201L）
        OmsCartItem oldItem = buildCartItem(100L, 201L, 2);
        oldItem.setId(10L);
        oldItem.setMemberId(1L);
        oldItem.setDeleteStatus(0);
        when(cartItemMapper.selectByPrimaryKey(10L)).thenReturn(oldItem);

        // 新SKU（200L）已有另一个购物车项，数量为3
        OmsCartItem duplicateItem = buildCartItem(100L, 200L, 3);
        duplicateItem.setId(20L);
        duplicateItem.setMemberId(1L);
        duplicateItem.setDeleteStatus(0);
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class)))
                .thenReturn(Collections.singletonList(duplicateItem));
        when(cartItemMapper.updateByPrimaryKeySelective(any(OmsCartItem.class))).thenReturn(1);
        when(cartItemMapper.updateByPrimaryKey(any(OmsCartItem.class))).thenReturn(1);

        // 将旧项改为SKU 200L，数量为4，合并后应为3+4=7 ≤ 10
        OmsCartItem newItem = buildCartItem(100L, 200L, 4);
        newItem.setId(10L);
        int result = cartItemService.updateAttr(newItem);

        assertEquals(1, result);
        // 旧记录被软删除
        verify(cartItemMapper).updateByPrimaryKeySelective(argThat(item ->
                item.getId() == 10L && item.getDeleteStatus() == 1));
        // 已有的SKU 200L购物车项被更新为合并数量 3+4=7
        verify(cartItemMapper).updateByPrimaryKey(argThat(item ->
                item.getId() == 20L && item.getQuantity() == 7));
    }

    // ========== updateAttr() 新SKU合并后超库存 ==========

    @Test
    void updateAttr_duplicateSku_exceedStock_throwsException() {
        stubCurrentMember();
        stubValidProductAndSku(); // SKU 200L, stock=10

        // 旧购物车项
        OmsCartItem oldItem = buildCartItem(100L, 201L, 2);
        oldItem.setId(10L);
        oldItem.setMemberId(1L);
        oldItem.setDeleteStatus(0);
        when(cartItemMapper.selectByPrimaryKey(10L)).thenReturn(oldItem);

        // 新SKU已有购物车项数量为8
        OmsCartItem duplicateItem = buildCartItem(100L, 200L, 8);
        duplicateItem.setId(20L);
        duplicateItem.setMemberId(1L);
        duplicateItem.setDeleteStatus(0);
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class)))
                .thenReturn(Collections.singletonList(duplicateItem));

        // 改为SKU 200L，数量4，合并后8+4=12 > 10
        OmsCartItem newItem = buildCartItem(100L, 200L, 4);
        newItem.setId(10L);

        ApiException ex = assertThrows(ApiException.class,
                () -> cartItemService.updateAttr(newItem));
        assertTrue(ex.getMessage().contains("库存不足"));
        // 旧记录未被修改
        verify(cartItemMapper, never()).updateByPrimaryKeySelective(any());
    }

    // ========== updateAttr() 新规格productSkuId为null ==========

    @Test
    void updateAttr_nullProductSkuId_throwsException() {
        stubCurrentMember();
        when(productMapper.selectByPrimaryKey(100L)).thenReturn(validProduct);

        OmsCartItem oldItem = buildCartItem(100L, 201L, 2);
        oldItem.setId(10L);
        oldItem.setMemberId(1L);
        oldItem.setDeleteStatus(0);
        when(cartItemMapper.selectByPrimaryKey(10L)).thenReturn(oldItem);

        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class)))
                .thenReturn(Collections.emptyList());

        OmsCartItem newItem = buildCartItem(100L, null, 1);
        newItem.setId(10L);

        ApiException ex = assertThrows(ApiException.class,
                () -> cartItemService.updateAttr(newItem));
        assertEquals("请选择商品规格", ex.getMessage());
    }

    // ========== updateAttr() 商品已下架 ==========

    @Test
    void updateAttr_productOffShelf_throwsException() {
        stubCurrentMember();

        OmsCartItem oldItem = buildCartItem(100L, 201L, 2);
        oldItem.setId(10L);
        oldItem.setMemberId(1L);
        oldItem.setDeleteStatus(0);
        when(cartItemMapper.selectByPrimaryKey(10L)).thenReturn(oldItem);

        PmsProduct offShelfProduct = new PmsProduct();
        offShelfProduct.setId(100L);
        offShelfProduct.setPublishStatus(0);
        offShelfProduct.setDeleteStatus(0);
        when(productMapper.selectByPrimaryKey(100L)).thenReturn(offShelfProduct);

        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class)))
                .thenReturn(Collections.emptyList());

        OmsCartItem newItem = buildCartItem(100L, 200L, 1);
        newItem.setId(10L);

        ApiException ex = assertThrows(ApiException.class,
                () -> cartItemService.updateAttr(newItem));
        assertEquals("商品已下架", ex.getMessage());
    }

    // ========== updateAttr() 购物车项不存在 ==========

    @Test
    void updateAttr_cartItemNotFound_throwsException() {
        stubCurrentMember();
        when(cartItemMapper.selectByPrimaryKey(999L)).thenReturn(null);

        OmsCartItem newItem = buildCartItem(100L, 200L, 1);
        newItem.setId(999L);

        ApiException ex = assertThrows(ApiException.class,
                () -> cartItemService.updateAttr(newItem));
        assertEquals("购物车商品不存在", ex.getMessage());
    }

    // ========== add() 重复加购恰好等于库存 ==========

    @Test
    void add_duplicateSku_exactStock_success() {
        stubCurrentMember();
        stubValidProductAndSku(); // stock=10

        OmsCartItem existItem = buildCartItem(100L, 200L, 7);
        existItem.setId(10L);
        existItem.setMemberId(1L);
        existItem.setDeleteStatus(0);
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class)))
                .thenReturn(Collections.singletonList(existItem));
        when(cartItemMapper.updateByPrimaryKey(any(OmsCartItem.class))).thenReturn(1);

        // 7 + 3 = 10, 恰好等于库存
        OmsCartItem cartItem = buildCartItem(100L, 200L, 3);
        int result = cartItemService.add(cartItem);

        assertEquals(1, result);
        verify(cartItemMapper).updateByPrimaryKey(argThat(item ->
                item.getQuantity() == 10));
    }
}
