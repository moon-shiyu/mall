package com.macro.mall.service;

import com.github.pagehelper.PageHelper;
import com.macro.mall.common.exception.ApiException;
import com.macro.mall.dao.SmsCouponDao;
import com.macro.mall.dao.SmsCouponProductCategoryRelationDao;
import com.macro.mall.dao.SmsCouponProductRelationDao;
import com.macro.mall.dto.SmsCouponParam;
import com.macro.mall.mapper.SmsCouponMapper;
import com.macro.mall.mapper.SmsCouponProductCategoryRelationMapper;
import com.macro.mall.mapper.SmsCouponProductRelationMapper;
import com.macro.mall.model.SmsCoupon;
import com.macro.mall.model.SmsCouponExample;
import com.macro.mall.model.SmsCouponProductCategoryRelation;
import com.macro.mall.model.SmsCouponProductCategoryRelationExample;
import com.macro.mall.model.SmsCouponProductRelation;
import com.macro.mall.model.SmsCouponProductRelationExample;
import com.macro.mall.service.impl.SmsCouponServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SmsCouponServiceImpl 聚焦单元测试（不依赖数据库/Spring 容器）。
 * 通过 Mockito 校验：
 * 1. list() 根据各筛选条件构建出正确的 SmsCouponExample 查询条件；
 * 2. create()/update() 对 useType 关联列表的为空/重复/缺失ID等异常校验；
 * 3. update() 在 useType 切换时清理两类旧关联，避免脏数据。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SmsCouponServiceImplTest {

    @Mock
    private SmsCouponMapper couponMapper;
    @Mock
    private SmsCouponProductRelationMapper productRelationMapper;
    @Mock
    private SmsCouponProductCategoryRelationMapper productCategoryRelationMapper;
    @Mock
    private SmsCouponProductRelationDao productRelationDao;
    @Mock
    private SmsCouponProductCategoryRelationDao productCategoryRelationDao;
    @Mock
    private SmsCouponDao couponDao;

    @InjectMocks
    private SmsCouponServiceImpl couponService;

    @BeforeEach
    void setUp() {
        when(couponMapper.selectByExample(any(SmsCouponExample.class))).thenReturn(Collections.emptyList());
        when(couponMapper.insert(any(SmsCoupon.class))).thenReturn(1);
        when(couponMapper.updateByPrimaryKey(any(SmsCoupon.class))).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        //清理 PageHelper 线程局部分页参数，避免污染其它用例
        PageHelper.clearPage();
    }

    // ---------------------------------------------------------------------
    // list() 筛选条件构建
    // ---------------------------------------------------------------------

    @Test
    void list_basicFilters_buildExpectedConditions() {
        couponService.list("满减", 1, 2, null,
                null, null,
                10, 100,
                5, 50,
                1, 5);

        Set<String> conditions = capturedConditions();
        assertTrue(conditions.contains("name like"), "应包含名称模糊匹配");
        assertTrue(conditions.contains("type ="), "应包含类型筛选");
        assertTrue(conditions.contains("use_type ="), "应包含使用类型筛选");
        assertTrue(conditions.contains("receive_count between"), "应包含领取数量区间");
        assertTrue(conditions.contains("count between"), "应包含剩余数量区间");
    }

    @Test
    void list_statusNotStarted_buildsStartTimeGreaterThan() {
        couponService.list(null, null, null, 0,
                null, null, null, null, null, null, 1, 5);

        Set<String> conditions = capturedConditions();
        assertTrue(conditions.contains("start_time >"), "未开始：start_time > now");
        assertFalse(conditions.contains("end_time <"), "未开始不应包含已结束条件");
    }

    @Test
    void list_statusInProgress_buildsStartAndEndBounds() {
        couponService.list(null, null, null, 1,
                null, null, null, null, null, null, 1, 5);

        Set<String> conditions = capturedConditions();
        assertTrue(conditions.contains("start_time <="), "进行中：start_time <= now");
        assertTrue(conditions.contains("end_time >="), "进行中：end_time >= now");
    }

    @Test
    void list_statusEnded_buildsEndTimeLessThan() {
        couponService.list(null, null, null, 2,
                null, null, null, null, null, null, 1, 5);

        Set<String> conditions = capturedConditions();
        assertTrue(conditions.contains("end_time <"), "已结束：end_time < now");
        assertFalse(conditions.contains("start_time >"), "已结束不应包含未开始条件");
    }

    @Test
    void list_validityRange_buildsIndependentBounds() {
        Date from = new Date(1_000_000L);
        Date to = new Date(2_000_000L);
        couponService.list(null, null, null, null,
                from, to, null, null, null, null, 1, 5);

        Set<String> conditions = capturedConditions();
        assertTrue(conditions.contains("start_time >="), "生效时间不早于 from");
        assertTrue(conditions.contains("end_time <="), "失效时间不晚于 to");
    }

    @Test
    void list_singleSidedRanges_useGreaterOrLessThanOrEqual() {
        // 仅领取数量下限 + 仅剩余数量上限
        couponService.list(null, null, null, null,
                null, null, 10, null, null, 50, 1, 5);

        Set<String> conditions = capturedConditions();
        assertTrue(conditions.contains("receive_count >="), "仅下限时用 >=");
        assertTrue(conditions.contains("count <="), "仅上限时用 <=");
        assertFalse(conditions.contains("receive_count between"), "单边不应使用 between");
        assertFalse(conditions.contains("count between"), "单边不应使用 between");
    }

    @Test
    void list_noFilters_buildsEmptyCriteriaButStillQueries() {
        couponService.list(null, null, null, null,
                null, null, null, null, null, null, 1, 5);

        Set<String> conditions = capturedConditions();
        assertTrue(conditions.isEmpty(), "无筛选条件时 criteria 应为空");
        verify(couponMapper).selectByExample(any(SmsCouponExample.class));
    }

    // ---------------------------------------------------------------------
    // create() 关联列表校验
    // ---------------------------------------------------------------------

    @Test
    void create_nullUseType_fails() {
        SmsCouponParam param = new SmsCouponParam();
        param.setUseType(null);
        ApiException ex = assertThrows(ApiException.class, () -> couponService.create(param));
        assertTrue(ex.getMessage().contains("使用类型"));
        verify(couponMapper, never()).insert(any(SmsCoupon.class));
    }

    @Test
    void create_useTypeProduct_emptyList_fails() {
        SmsCouponParam param = productCoupon(new ArrayList<>());
        ApiException ex = assertThrows(ApiException.class, () -> couponService.create(param));
        assertTrue(ex.getMessage().contains("商品"));
        verify(productRelationDao, never()).insertList(anyList());
    }

    @Test
    void create_useTypeProduct_missingId_fails() {
        SmsCouponParam param = productCoupon(Arrays.asList(productRelation(1L), productRelation(null)));
        ApiException ex = assertThrows(ApiException.class, () -> couponService.create(param));
        assertTrue(ex.getMessage().contains("缺失"));
    }

    @Test
    void create_useTypeProduct_duplicate_fails() {
        SmsCouponParam param = productCoupon(Arrays.asList(productRelation(1L), productRelation(1L)));
        ApiException ex = assertThrows(ApiException.class, () -> couponService.create(param));
        assertTrue(ex.getMessage().contains("重复"));
    }

    @Test
    void create_useTypeCategory_emptyList_fails() {
        SmsCouponParam param = categoryCoupon(new ArrayList<>());
        ApiException ex = assertThrows(ApiException.class, () -> couponService.create(param));
        assertTrue(ex.getMessage().contains("分类"));
    }

    @Test
    void create_useTypeGeneral_ok_noRelationInsertAndCountsInitialized() {
        SmsCouponParam param = new SmsCouponParam();
        param.setUseType(0);
        param.setPublishCount(200);

        int result = couponService.create(param);

        assertEquals(1, result);
        assertEquals(Integer.valueOf(200), param.getCount());
        assertEquals(Integer.valueOf(0), param.getUseCount());
        assertEquals(Integer.valueOf(0), param.getReceiveCount());
        verify(productRelationDao, never()).insertList(anyList());
        verify(productCategoryRelationDao, never()).insertList(anyList());
    }

    @Test
    void create_useTypeProduct_ok_stampsCouponIdAndInsertsOnlyProduct() {
        // insert 时回填生成的主键，验证 couponId 是否被正确回写到关联对象
        when(couponMapper.insert(any(SmsCoupon.class))).thenAnswer(invocation -> {
            SmsCoupon inserted = invocation.getArgument(0);
            inserted.setId(99L);
            return 1;
        });
        SmsCouponProductRelation relation = productRelation(7L);
        SmsCouponParam param = productCoupon(new ArrayList<>(Collections.singletonList(relation)));

        couponService.create(param);

        assertEquals(Long.valueOf(99L), relation.getCouponId());
        verify(productRelationDao).insertList(param.getProductRelationList());
        verify(productCategoryRelationDao, never()).insertList(anyList());
    }

    // ---------------------------------------------------------------------
    // update() useType 切换清理脏数据
    // ---------------------------------------------------------------------

    @Test
    void update_switchToGeneral_cleansBothRelationsAndInsertsNone() {
        SmsCouponParam param = new SmsCouponParam();
        param.setUseType(0);

        couponService.update(1L, param);

        verify(productRelationMapper).deleteByExample(any(SmsCouponProductRelationExample.class));
        verify(productCategoryRelationMapper).deleteByExample(any(SmsCouponProductCategoryRelationExample.class));
        verify(productRelationDao, never()).insertList(anyList());
        verify(productCategoryRelationDao, never()).insertList(anyList());
    }

    @Test
    void update_switchToCategory_cleansBothThenInsertsCategoryOnly() {
        SmsCouponParam param = categoryCoupon(new ArrayList<>(Collections.singletonList(categoryRelation(3L))));

        couponService.update(1L, param);

        verify(productRelationMapper).deleteByExample(any(SmsCouponProductRelationExample.class));
        verify(productCategoryRelationMapper).deleteByExample(any(SmsCouponProductCategoryRelationExample.class));
        verify(productCategoryRelationDao).insertList(param.getProductCategoryRelationList());
        verify(productRelationDao, never()).insertList(anyList());
    }

    @Test
    void update_useTypeProduct_cleansBothThenInsertsProductWithCouponId() {
        SmsCouponProductRelation relation = productRelation(7L);
        SmsCouponParam param = productCoupon(new ArrayList<>(Collections.singletonList(relation)));

        int result = couponService.update(5L, param);

        assertEquals(1, result);
        assertEquals(Long.valueOf(5L), relation.getCouponId());
        verify(productRelationMapper).deleteByExample(any(SmsCouponProductRelationExample.class));
        verify(productCategoryRelationMapper).deleteByExample(any(SmsCouponProductCategoryRelationExample.class));
        verify(productRelationDao).insertList(param.getProductRelationList());
    }

    @Test
    void update_useTypeProduct_emptyList_failsBeforeUpdate() {
        SmsCouponParam param = productCoupon(new ArrayList<>());
        ApiException ex = assertThrows(ApiException.class, () -> couponService.update(1L, param));
        assertTrue(ex.getMessage().contains("商品"));
        verify(couponMapper, never()).updateByPrimaryKey(any(SmsCoupon.class));
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private Set<String> capturedConditions() {
        ArgumentCaptor<SmsCouponExample> captor = ArgumentCaptor.forClass(SmsCouponExample.class);
        verify(couponMapper).selectByExample(captor.capture());
        Set<String> conditions = new HashSet<>();
        for (SmsCouponExample.Criteria criteria : captor.getValue().getOredCriteria()) {
            for (SmsCouponExample.Criterion criterion : criteria.getAllCriteria()) {
                conditions.add(criterion.getCondition());
            }
        }
        return conditions;
    }

    private SmsCouponParam productCoupon(List<SmsCouponProductRelation> relations) {
        SmsCouponParam param = new SmsCouponParam();
        param.setUseType(2);
        param.setPublishCount(100);
        param.setProductRelationList(relations);
        return param;
    }

    private SmsCouponParam categoryCoupon(List<SmsCouponProductCategoryRelation> relations) {
        SmsCouponParam param = new SmsCouponParam();
        param.setUseType(1);
        param.setPublishCount(100);
        param.setProductCategoryRelationList(relations);
        return param;
    }

    private SmsCouponProductRelation productRelation(Long productId) {
        SmsCouponProductRelation relation = new SmsCouponProductRelation();
        relation.setProductId(productId);
        return relation;
    }

    private SmsCouponProductCategoryRelation categoryRelation(Long categoryId) {
        SmsCouponProductCategoryRelation relation = new SmsCouponProductCategoryRelation();
        relation.setProductCategoryId(categoryId);
        return relation;
    }
}
