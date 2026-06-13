package com.macro.mall.portal;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.mapper.OmsCartItemMapper;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.mapper.PmsSkuStockMapper;
import com.macro.mall.model.OmsCartItem;
import com.macro.mall.model.OmsCartItemExample;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.model.PmsSkuStock;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.portal.service.impl.OmsCartItemServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 购物车数量与库存校验逻辑的聚焦单元测试。
 * 使用Mockito隔离Mapper与会员服务，无需数据库即可覆盖：
 * 正常加购、重复加购合并、库存不足、非法数量、规格重选、当前会员隔离。
 */
@ExtendWith(MockitoExtension.class)
class OmsCartItemServiceImplTest {

    @Mock
    private OmsCartItemMapper cartItemMapper;
    @Mock
    private PmsProductMapper productMapper;
    @Mock
    private PmsSkuStockMapper skuStockMapper;
    @Mock
    private UmsMemberService memberService;

    @InjectMocks
    private OmsCartItemServiceImpl cartItemService;

    private static final Long MEMBER_ID = 1L;
    private static final Long PRODUCT_ID = 100L;
    private static final Long SKU_ID = 200L;

    private UmsMember member(Long id) {
        UmsMember m = new UmsMember();
        m.setId(id);
        m.setNickname("nick-" + id);
        return m;
    }

    private PmsProduct onShelfProduct() {
        PmsProduct p = new PmsProduct();
        p.setId(PRODUCT_ID);
        p.setPublishStatus(1);
        p.setDeleteStatus(0);
        return p;
    }

    private PmsSkuStock sku(int stock) {
        PmsSkuStock s = new PmsSkuStock();
        s.setId(SKU_ID);
        s.setProductId(PRODUCT_ID);
        s.setStock(stock);
        return s;
    }

    private OmsCartItem newCartItem(Integer quantity) {
        OmsCartItem c = new OmsCartItem();
        c.setProductId(PRODUCT_ID);
        c.setProductSkuId(SKU_ID);
        c.setQuantity(quantity);
        return c;
    }

    // ---------- 正常加购 ----------
    @Test
    void add_newItem_success() {
        when(memberService.getCurrentMember()).thenReturn(member(MEMBER_ID));
        when(productMapper.selectByPrimaryKey(PRODUCT_ID)).thenReturn(onShelfProduct());
        when(skuStockMapper.selectByPrimaryKey(SKU_ID)).thenReturn(sku(10));
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class))).thenReturn(Collections.emptyList());
        when(cartItemMapper.insert(any(OmsCartItem.class))).thenReturn(1);

        int count = cartItemService.add(newCartItem(2));

        assertEquals(1, count);
        verify(cartItemMapper).insert(any(OmsCartItem.class));
        verify(cartItemMapper, never()).updateByPrimaryKey(any(OmsCartItem.class));
    }

    // ---------- 重复加购：同一SKU合并数量 ----------
    @Test
    void add_duplicateSameSku_mergesQuantity() {
        when(memberService.getCurrentMember()).thenReturn(member(MEMBER_ID));
        when(productMapper.selectByPrimaryKey(PRODUCT_ID)).thenReturn(onShelfProduct());
        when(skuStockMapper.selectByPrimaryKey(SKU_ID)).thenReturn(sku(10));
        OmsCartItem existing = newCartItem(3);
        existing.setId(5L);
        existing.setMemberId(MEMBER_ID);
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class))).thenReturn(List.of(existing));
        when(cartItemMapper.updateByPrimaryKey(any(OmsCartItem.class))).thenReturn(1);

        int count = cartItemService.add(newCartItem(4));

        assertEquals(1, count);
        ArgumentCaptor<OmsCartItem> captor = ArgumentCaptor.forClass(OmsCartItem.class);
        verify(cartItemMapper).updateByPrimaryKey(captor.capture());
        assertEquals(7, captor.getValue().getQuantity());
        verify(cartItemMapper, never()).insert(any(OmsCartItem.class));
    }

    // ---------- 重复加购：合并后超过库存 ----------
    @Test
    void add_mergedQuantityExceedsStock_fails() {
        when(memberService.getCurrentMember()).thenReturn(member(MEMBER_ID));
        when(productMapper.selectByPrimaryKey(PRODUCT_ID)).thenReturn(onShelfProduct());
        when(skuStockMapper.selectByPrimaryKey(SKU_ID)).thenReturn(sku(5));
        OmsCartItem existing = newCartItem(3);
        existing.setId(5L);
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class))).thenReturn(List.of(existing));

        ApiException ex = assertThrows(ApiException.class, () -> cartItemService.add(newCartItem(4)));
        assertTrue(ex.getMessage().contains("库存"));
        verify(cartItemMapper, never()).updateByPrimaryKey(any(OmsCartItem.class));
        verify(cartItemMapper, never()).insert(any(OmsCartItem.class));
    }

    // ---------- 库存不足（新加购） ----------
    @Test
    void add_insufficientStockNewItem_fails() {
        when(memberService.getCurrentMember()).thenReturn(member(MEMBER_ID));
        when(productMapper.selectByPrimaryKey(PRODUCT_ID)).thenReturn(onShelfProduct());
        when(skuStockMapper.selectByPrimaryKey(SKU_ID)).thenReturn(sku(1));
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class))).thenReturn(Collections.emptyList());

        assertThrows(ApiException.class, () -> cartItemService.add(newCartItem(2)));
        verify(cartItemMapper, never()).insert(any(OmsCartItem.class));
    }

    // ---------- 商品下架 ----------
    @Test
    void add_productOffShelf_fails() {
        when(memberService.getCurrentMember()).thenReturn(member(MEMBER_ID));
        PmsProduct off = onShelfProduct();
        off.setPublishStatus(0);
        when(productMapper.selectByPrimaryKey(PRODUCT_ID)).thenReturn(off);

        ApiException ex = assertThrows(ApiException.class, () -> cartItemService.add(newCartItem(1)));
        assertTrue(ex.getMessage().contains("下架"));
        verify(skuStockMapper, never()).selectByPrimaryKey(any());
    }

    // ---------- 非法数量（0 与 null） ----------
    @Test
    void add_illegalQuantity_fails() {
        when(memberService.getCurrentMember()).thenReturn(member(MEMBER_ID));

        assertThrows(ApiException.class, () -> cartItemService.add(newCartItem(0)));
        assertThrows(ApiException.class, () -> cartItemService.add(newCartItem(null)));

        verifyNoInteractions(productMapper);
        verify(cartItemMapper, never()).insert(any(OmsCartItem.class));
    }

    // ---------- SKU不存在 ----------
    @Test
    void add_skuNotExist_fails() {
        when(memberService.getCurrentMember()).thenReturn(member(MEMBER_ID));
        when(productMapper.selectByPrimaryKey(PRODUCT_ID)).thenReturn(onShelfProduct());
        when(skuStockMapper.selectByPrimaryKey(SKU_ID)).thenReturn(null);

        ApiException ex = assertThrows(ApiException.class, () -> cartItemService.add(newCartItem(1)));
        assertTrue(ex.getMessage().contains("规格"));
    }

    // ---------- 修改数量：成功 ----------
    @Test
    void updateQuantity_success() {
        OmsCartItem existing = newCartItem(1);
        existing.setId(5L);
        existing.setMemberId(MEMBER_ID);
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class))).thenReturn(List.of(existing));
        when(productMapper.selectByPrimaryKey(PRODUCT_ID)).thenReturn(onShelfProduct());
        when(skuStockMapper.selectByPrimaryKey(SKU_ID)).thenReturn(sku(10));
        when(cartItemMapper.updateByExampleSelective(any(OmsCartItem.class), any(OmsCartItemExample.class))).thenReturn(1);

        int count = cartItemService.updateQuantity(5L, MEMBER_ID, 3);

        assertEquals(1, count);
    }

    // ---------- 修改数量：超过库存 ----------
    @Test
    void updateQuantity_overStock_fails() {
        OmsCartItem existing = newCartItem(1);
        existing.setId(5L);
        existing.setMemberId(MEMBER_ID);
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class))).thenReturn(List.of(existing));
        when(productMapper.selectByPrimaryKey(PRODUCT_ID)).thenReturn(onShelfProduct());
        when(skuStockMapper.selectByPrimaryKey(SKU_ID)).thenReturn(sku(5));

        assertThrows(ApiException.class, () -> cartItemService.updateQuantity(5L, MEMBER_ID, 6));
        verify(cartItemMapper, never()).updateByExampleSelective(any(OmsCartItem.class), any(OmsCartItemExample.class));
    }

    // ---------- 当前会员隔离：不能修改他人购物车项 ----------
    @Test
    void updateQuantity_otherMembersItem_fails() {
        // 按 id + memberId 过滤后查不到，说明该购物车项不属于当前会员
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class))).thenReturn(Collections.emptyList());

        ApiException ex = assertThrows(ApiException.class,
                () -> cartItemService.updateQuantity(999L, MEMBER_ID, 1));
        assertTrue(ex.getMessage().contains("购物车"));
        verify(cartItemMapper, never()).updateByExampleSelective(any(OmsCartItem.class), any(OmsCartItemExample.class));
        verifyNoInteractions(productMapper);
    }

    // ---------- 规格重选：校验失败时不删除原购物车项（避免数据丢失） ----------
    @Test
    void updateAttr_validationFails_doesNotDeleteOriginal() {
        when(memberService.getCurrentMember()).thenReturn(member(MEMBER_ID));
        PmsProduct off = onShelfProduct();
        off.setPublishStatus(0);
        when(productMapper.selectByPrimaryKey(PRODUCT_ID)).thenReturn(off);

        OmsCartItem req = newCartItem(1);
        req.setId(5L);

        assertThrows(ApiException.class, () -> cartItemService.updateAttr(req));
        // 关键断言：新规格校验失败时，原购物车项不应被删除
        verify(cartItemMapper, never()).updateByExampleSelective(any(OmsCartItem.class), any(OmsCartItemExample.class));
        verify(cartItemMapper, never()).updateByPrimaryKeySelective(any(OmsCartItem.class));
    }

    // ---------- 规格重选：成功时删除旧项并新增新项 ----------
    @Test
    void updateAttr_success_deletesOldAndAddsNew() {
        when(memberService.getCurrentMember()).thenReturn(member(MEMBER_ID));
        when(productMapper.selectByPrimaryKey(PRODUCT_ID)).thenReturn(onShelfProduct());
        when(skuStockMapper.selectByPrimaryKey(SKU_ID)).thenReturn(sku(10));
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class))).thenReturn(Collections.emptyList());
        when(cartItemMapper.updateByExampleSelective(any(OmsCartItem.class), any(OmsCartItemExample.class))).thenReturn(1);
        when(cartItemMapper.insert(any(OmsCartItem.class))).thenReturn(1);

        OmsCartItem req = newCartItem(2);
        req.setId(5L);

        int result = cartItemService.updateAttr(req);

        assertEquals(1, result);
        verify(cartItemMapper).updateByExampleSelective(any(OmsCartItem.class), any(OmsCartItemExample.class));
        verify(cartItemMapper).insert(any(OmsCartItem.class));
    }
}
