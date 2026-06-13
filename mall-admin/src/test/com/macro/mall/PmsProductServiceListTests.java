package com.macro.mall;

import com.macro.mall.dto.PmsProductQueryParam;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.model.PmsProductExample;
import com.macro.mall.service.impl.PmsProductServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 商品列表查询 — Service层区间筛选与排序逻辑测试
 * 使用Mockito验证PmsProductExample条件构建、排序设置、边界处理
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("商品列表Service区间筛选与排序测试")
public class PmsProductServiceListTests {

    @Mock
    private PmsProductMapper productMapper;

    @InjectMocks
    private PmsProductServiceImpl productService;

    private void setupMapperReturnEmpty() {
        when(productMapper.selectByExample(any(PmsProductExample.class)))
                .thenReturn(Collections.emptyList());
    }

    // ==================== 基础兼容性测试 ====================

    @Test
    @DisplayName("原有筛选条件保持兼容 — deleteStatus=0始终存在")
    void listWithNoFilters_shouldAlwaysSetDeleteStatusZero() {
        setupMapperReturnEmpty();
        PmsProductQueryParam param = new PmsProductQueryParam();

        productService.list(param, 5, 1);

        ArgumentCaptor<PmsProductExample> captor = ArgumentCaptor.forClass(PmsProductExample.class);
        verify(productMapper).selectByExample(captor.capture());
        PmsProductExample example = captor.getValue();
        assertFalse(example.getOredCriteria().isEmpty());
        String criteriaStr = example.getOredCriteria().get(0).getAllCriteria().toString();
        assertTrue(criteriaStr.contains("delete_status"));
        assertNull(example.getOrderByClause());
    }

    // ==================== 价格区间筛选 ====================

    @Test
    @DisplayName("价格区间 — 仅传priceMin，生成 >= 条件")
    void listWithPriceMinOnly_shouldApplyGreaterOrEqual() {
        setupMapperReturnEmpty();
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setPriceMin(new BigDecimal("100"));

        productService.list(param, 5, 1);

        ArgumentCaptor<PmsProductExample> captor = ArgumentCaptor.forClass(PmsProductExample.class);
        verify(productMapper).selectByExample(captor.capture());
        String criteriaStr = captor.getValue().getOredCriteria().get(0).getAllCriteria().toString();
        assertTrue(criteriaStr.contains("price >="));
    }

    @Test
    @DisplayName("价格区间 — 仅传priceMax，生成 <= 条件")
    void listWithPriceMaxOnly_shouldApplyLessOrEqual() {
        setupMapperReturnEmpty();
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setPriceMax(new BigDecimal("500"));

        productService.list(param, 5, 1);

        ArgumentCaptor<PmsProductExample> captor = ArgumentCaptor.forClass(PmsProductExample.class);
        verify(productMapper).selectByExample(captor.capture());
        String criteriaStr = captor.getValue().getOredCriteria().get(0).getAllCriteria().toString();
        assertTrue(criteriaStr.contains("price <="));
    }

    @Test
    @DisplayName("价格区间 — 两侧都传且min<=max，生成between条件")
    void listWithBothPriceRange_shouldApplyBetween() {
        setupMapperReturnEmpty();
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setPriceMin(new BigDecimal("100"));
        param.setPriceMax(new BigDecimal("500"));

        productService.list(param, 5, 1);

        ArgumentCaptor<PmsProductExample> captor = ArgumentCaptor.forClass(PmsProductExample.class);
        verify(productMapper).selectByExample(captor.capture());
        String criteriaStr = captor.getValue().getOredCriteria().get(0).getAllCriteria().toString();
        assertTrue(criteriaStr.contains("price between"));
    }

    @Test
    @DisplayName("价格区间倒置 — min>max时跳过区间条件")
    void listWithInvertedPriceRange_shouldSkipCondition() {
        setupMapperReturnEmpty();
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setPriceMin(new BigDecimal("500"));
        param.setPriceMax(new BigDecimal("100"));

        productService.list(param, 5, 1);

        ArgumentCaptor<PmsProductExample> captor = ArgumentCaptor.forClass(PmsProductExample.class);
        verify(productMapper).selectByExample(captor.capture());
        String criteriaStr = captor.getValue().getOredCriteria().get(0).getAllCriteria().toString();
        assertFalse(criteriaStr.contains("price between"));
        assertFalse(criteriaStr.contains("price >="));
        assertFalse(criteriaStr.contains("price <="));
    }

    @Test
    @DisplayName("价格区间 — min==max时生成between条件（等于场景）")
    void listWithEqualPriceRange_shouldApplyBetween() {
        setupMapperReturnEmpty();
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setPriceMin(new BigDecimal("100"));
        param.setPriceMax(new BigDecimal("100"));

        productService.list(param, 5, 1);

        ArgumentCaptor<PmsProductExample> captor = ArgumentCaptor.forClass(PmsProductExample.class);
        verify(productMapper).selectByExample(captor.capture());
        String criteriaStr = captor.getValue().getOredCriteria().get(0).getAllCriteria().toString();
        assertTrue(criteriaStr.contains("price between"));
    }

    // ==================== 库存区间筛选 ====================

    @Test
    @DisplayName("库存区间 — 仅传stockMin")
    void listWithStockMinOnly_shouldApplyGreaterOrEqual() {
        setupMapperReturnEmpty();
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setStockMin(10);

        productService.list(param, 5, 1);

        ArgumentCaptor<PmsProductExample> captor = ArgumentCaptor.forClass(PmsProductExample.class);
        verify(productMapper).selectByExample(captor.capture());
        String criteriaStr = captor.getValue().getOredCriteria().get(0).getAllCriteria().toString();
        assertTrue(criteriaStr.contains("stock >="));
    }

    @Test
    @DisplayName("库存区间 — 仅传stockMax")
    void listWithStockMaxOnly_shouldApplyLessOrEqual() {
        setupMapperReturnEmpty();
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setStockMax(100);

        productService.list(param, 5, 1);

        ArgumentCaptor<PmsProductExample> captor = ArgumentCaptor.forClass(PmsProductExample.class);
        verify(productMapper).selectByExample(captor.capture());
        String criteriaStr = captor.getValue().getOredCriteria().get(0).getAllCriteria().toString();
        assertTrue(criteriaStr.contains("stock <="));
    }

    @Test
    @DisplayName("库存区间 — 两侧都传且min<=max")
    void listWithBothStockRange_shouldApplyBetween() {
        setupMapperReturnEmpty();
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setStockMin(0);
        param.setStockMax(100);

        productService.list(param, 5, 1);

        ArgumentCaptor<PmsProductExample> captor = ArgumentCaptor.forClass(PmsProductExample.class);
        verify(productMapper).selectByExample(captor.capture());
        String criteriaStr = captor.getValue().getOredCriteria().get(0).getAllCriteria().toString();
        assertTrue(criteriaStr.contains("stock between"));
    }

    @Test
    @DisplayName("库存区间倒置 — min>max时跳过")
    void listWithInvertedStockRange_shouldSkipCondition() {
        setupMapperReturnEmpty();
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setStockMin(100);
        param.setStockMax(0);

        productService.list(param, 5, 1);

        ArgumentCaptor<PmsProductExample> captor = ArgumentCaptor.forClass(PmsProductExample.class);
        verify(productMapper).selectByExample(captor.capture());
        String criteriaStr = captor.getValue().getOredCriteria().get(0).getAllCriteria().toString();
        assertFalse(criteriaStr.contains("stock between"));
    }

    // ==================== 创建时间范围筛选 ====================

    @Test
    @DisplayName("创建时间区间 — 仅传createTimeFrom")
    void listWithCreateTimeFromOnly_shouldApplyGreaterOrEqual() {
        setupMapperReturnEmpty();
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setCreateTimeFrom(new Date());

        productService.list(param, 5, 1);

        ArgumentCaptor<PmsProductExample> captor = ArgumentCaptor.forClass(PmsProductExample.class);
        verify(productMapper).selectByExample(captor.capture());
        String criteriaStr = captor.getValue().getOredCriteria().get(0).getAllCriteria().toString();
        assertTrue(criteriaStr.contains("create_time >="));
    }

    @Test
    @DisplayName("创建时间区间 — 仅传createTimeTo")
    void listWithCreateTimeToOnly_shouldApplyLessOrEqual() {
        setupMapperReturnEmpty();
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setCreateTimeTo(new Date());

        productService.list(param, 5, 1);

        ArgumentCaptor<PmsProductExample> captor = ArgumentCaptor.forClass(PmsProductExample.class);
        verify(productMapper).selectByExample(captor.capture());
        String criteriaStr = captor.getValue().getOredCriteria().get(0).getAllCriteria().toString();
        assertTrue(criteriaStr.contains("create_time <="));
    }

    @Test
    @DisplayName("创建时间区间 — 两侧都传且from<=to")
    void listWithBothCreateTimeRange_shouldApplyBetween() {
        setupMapperReturnEmpty();
        PmsProductQueryParam param = new PmsProductQueryParam();
        Date from = new Date(System.currentTimeMillis() - 86400000L);
        Date to = new Date();
        param.setCreateTimeFrom(from);
        param.setCreateTimeTo(to);

        productService.list(param, 5, 1);

        ArgumentCaptor<PmsProductExample> captor = ArgumentCaptor.forClass(PmsProductExample.class);
        verify(productMapper).selectByExample(captor.capture());
        String criteriaStr = captor.getValue().getOredCriteria().get(0).getAllCriteria().toString();
        assertTrue(criteriaStr.contains("create_time between"));
    }

    @Test
    @DisplayName("创建时间区间倒置 — from>to时跳过")
    void listWithInvertedCreateTimeRange_shouldSkipCondition() {
        setupMapperReturnEmpty();
        PmsProductQueryParam param = new PmsProductQueryParam();
        Date from = new Date();
        Date to = new Date(System.currentTimeMillis() - 86400000L);
        param.setCreateTimeFrom(from);
        param.setCreateTimeTo(to);

        productService.list(param, 5, 1);

        ArgumentCaptor<PmsProductExample> captor = ArgumentCaptor.forClass(PmsProductExample.class);
        verify(productMapper).selectByExample(captor.capture());
        String criteriaStr = captor.getValue().getOredCriteria().get(0).getAllCriteria().toString();
        assertFalse(criteriaStr.contains("create_time between"));
    }

    // ==================== 排序逻辑测试 ====================

    @Test
    @DisplayName("排序 — sortField+sortOrder 设置到orderByClause")
    void listWithSortField_shouldSetOrderByClause() {
        setupMapperReturnEmpty();
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setSortField("price");
        param.setSortOrder("asc");

        productService.list(param, 5, 1);

        ArgumentCaptor<PmsProductExample> captor = ArgumentCaptor.forClass(PmsProductExample.class);
        verify(productMapper).selectByExample(captor.capture());
        assertEquals("price asc", captor.getValue().getOrderByClause());
    }

    @Test
    @DisplayName("排序 — sortField有值但sortOrder为空时默认desc")
    void listWithSortFieldNoOrder_shouldDefaultDesc() {
        setupMapperReturnEmpty();
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setSortField("sale");

        productService.list(param, 5, 1);

        ArgumentCaptor<PmsProductExample> captor = ArgumentCaptor.forClass(PmsProductExample.class);
        verify(productMapper).selectByExample(captor.capture());
        assertEquals("sale desc", captor.getValue().getOrderByClause());
    }

    @Test
    @DisplayName("排序 — 无sortField时不设置orderByClause")
    void listWithNoSortField_shouldNotSetOrderByClause() {
        setupMapperReturnEmpty();
        PmsProductQueryParam param = new PmsProductQueryParam();

        productService.list(param, 5, 1);

        ArgumentCaptor<PmsProductExample> captor = ArgumentCaptor.forClass(PmsProductExample.class);
        verify(productMapper).selectByExample(captor.capture());
        assertNull(captor.getValue().getOrderByClause());
    }

    @Test
    @DisplayName("排序 — newStatus映射为数据库列名new_status")
    void listWithNewStatusSortField_shouldMapToDbColumnName() {
        setupMapperReturnEmpty();
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setSortField("newStatus");
        param.setSortOrder("asc");

        productService.list(param, 5, 1);

        ArgumentCaptor<PmsProductExample> captor = ArgumentCaptor.forClass(PmsProductExample.class);
        verify(productMapper).selectByExample(captor.capture());
        assertEquals("new_status asc", captor.getValue().getOrderByClause());
    }

    @Test
    @DisplayName("排序 — newStatus默认排序方向为desc且使用数据库列名")
    void listWithNewStatusNoOrder_shouldDefaultDescWithDbColumnName() {
        setupMapperReturnEmpty();
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setSortField("newStatus");

        productService.list(param, 5, 1);

        ArgumentCaptor<PmsProductExample> captor = ArgumentCaptor.forClass(PmsProductExample.class);
        verify(productMapper).selectByExample(captor.capture());
        assertEquals("new_status desc", captor.getValue().getOrderByClause());
    }

    // ==================== 组合场景测试 ====================

    @Test
    @DisplayName("组合 — 原有筛选+区间筛选+排序同时生效")
    void listWithAllFiltersAndSort_shouldApplyAll() {
        setupMapperReturnEmpty();
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setKeyword("手机");
        param.setPublishStatus(1);
        param.setBrandId(1L);
        param.setPriceMin(new BigDecimal("100"));
        param.setPriceMax(new BigDecimal("500"));
        param.setStockMin(10);
        param.setCreateTimeFrom(new Date());
        param.setSortField("price");
        param.setSortOrder("asc");

        productService.list(param, 10, 1);

        ArgumentCaptor<PmsProductExample> captor = ArgumentCaptor.forClass(PmsProductExample.class);
        verify(productMapper).selectByExample(captor.capture());
        PmsProductExample example = captor.getValue();
        String criteriaStr = example.getOredCriteria().get(0).getAllCriteria().toString();

        // 原有条件
        assertTrue(criteriaStr.contains("delete_status"));
        assertTrue(criteriaStr.contains("publish_status"));
        assertTrue(criteriaStr.contains("name like"));
        assertTrue(criteriaStr.contains("brand_id"));
        // 新增区间条件
        assertTrue(criteriaStr.contains("price between"));
        assertTrue(criteriaStr.contains("stock >="));
        assertTrue(criteriaStr.contains("create_time >="));
        // 排序
        assertEquals("price asc", example.getOrderByClause());
    }

    @Test
    @DisplayName("所有区间参数为null时，不生成额外区间条件")
    void listWithAllNullRangeParams_shouldOnlyHaveDeleteStatus() {
        setupMapperReturnEmpty();
        PmsProductQueryParam param = new PmsProductQueryParam();

        productService.list(param, 5, 1);

        ArgumentCaptor<PmsProductExample> captor = ArgumentCaptor.forClass(PmsProductExample.class);
        verify(productMapper).selectByExample(captor.capture());
        String criteriaStr = captor.getValue().getOredCriteria().get(0).getAllCriteria().toString();

        // 只有 deleteStatus
        assertTrue(criteriaStr.contains("delete_status"));
        assertFalse(criteriaStr.contains("price"));
        assertFalse(criteriaStr.contains("stock"));
        assertFalse(criteriaStr.contains("create_time"));
    }
}
