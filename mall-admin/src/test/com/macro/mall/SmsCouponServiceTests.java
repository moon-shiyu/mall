package com.macro.mall;

import com.macro.mall.dto.SmsCouponParam;
import com.macro.mall.dto.SmsCouponQueryParam;
import com.macro.mall.mapper.SmsCouponProductCategoryRelationMapper;
import com.macro.mall.mapper.SmsCouponProductRelationMapper;
import com.macro.mall.model.*;
import com.macro.mall.service.SmsCouponService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 优惠券管理Service测试
 * 覆盖：列表筛选、创建路径、useType切换清理、校验器
 */
@SpringBootTest
public class SmsCouponServiceTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(SmsCouponServiceTests.class);

    @Autowired
    private SmsCouponService couponService;

    @Autowired
    private SmsCouponProductRelationMapper productRelationMapper;

    @Autowired
    private SmsCouponProductCategoryRelationMapper productCategoryRelationMapper;

    private static Validator validator;

    @BeforeAll
    static void setupValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // ==================== 工具方法 ====================

    /**
     * 构建基本优惠券参数
     */
    private SmsCouponParam buildCouponParam(String name, Integer useType) {
        SmsCouponParam param = new SmsCouponParam();
        param.setName(name);
        param.setType(0); // 全场赠券
        param.setPlatform(0); // 全部
        param.setAmount(new BigDecimal("10.00"));
        param.setPerLimit(1);
        param.setMinPoint(new BigDecimal("100.00"));
        param.setStartTime(new Date());
        param.setEndTime(new Date(System.currentTimeMillis() + 86400000L * 30));
        param.setUseType(useType);
        param.setPublishCount(100);
        param.setEnableTime(new Date());
        param.setCode("TEST" + System.currentTimeMillis());
        param.setMemberLevel(0);
        param.setStatus(1); // 默认进行中
        return param;
    }

    private SmsCouponProductRelation buildProductRelation(Long productId) {
        SmsCouponProductRelation relation = new SmsCouponProductRelation();
        relation.setProductId(productId);
        relation.setProductName("测试商品" + productId);
        relation.setProductSn("SN" + productId);
        return relation;
    }

    private SmsCouponProductCategoryRelation buildCategoryRelation(Long categoryId) {
        SmsCouponProductCategoryRelation relation = new SmsCouponProductCategoryRelation();
        relation.setProductCategoryId(categoryId);
        relation.setProductCategoryName("测试分类" + categoryId);
        relation.setParentCategoryName("父分类");
        return relation;
    }

    private long countProductRelations(Long couponId) {
        SmsCouponProductRelationExample example = new SmsCouponProductRelationExample();
        example.createCriteria().andCouponIdEqualTo(couponId);
        return productRelationMapper.countByExample(example);
    }

    private long countCategoryRelations(Long couponId) {
        SmsCouponProductCategoryRelationExample example = new SmsCouponProductCategoryRelationExample();
        example.createCriteria().andCouponIdEqualTo(couponId);
        return productCategoryRelationMapper.countByExample(example);
    }

    // ==================== A. 列表筛选测试 ====================

    @Test
    @Transactional
    @Rollback
    public void testListWithStatusFilter() {
        // 插入不同状态的优惠券
        SmsCouponParam p1 = buildCouponParam("StatusActive", 0);
        p1.setStatus(1); // 进行中
        SmsCouponParam p2 = buildCouponParam("StatusEnded", 0);
        p2.setStatus(2); // 已结束
        SmsCouponParam p3 = buildCouponParam("StatusStopped", 0);
        p3.setStatus(3); // 已停用
        couponService.create(p1);
        couponService.create(p2);
        couponService.create(p3);

        // 筛选 status=1（进行中）
        SmsCouponQueryParam queryParam = new SmsCouponQueryParam();
        queryParam.setStatus(1);
        List<SmsCoupon> result = couponService.list(queryParam, 10, 1);

        assertTrue(result.size() >= 1, "应至少返回1条进行中的优惠券");
        assertTrue(result.stream().allMatch(c -> Integer.valueOf(1).equals(c.getStatus())),
                "所有结果状态应为 1（进行中）");
        LOGGER.info("testListWithStatusFilter: 返回{}条记录", result.size());
    }

    @Test
    @Transactional
    @Rollback
    public void testListWithNameFilter() {
        // 插入3个不同名称的优惠券
        SmsCouponParam p1 = buildCouponParam("SpecialDiscount", 0);
        SmsCouponParam p2 = buildCouponParam("SpecialOffer", 0);
        SmsCouponParam p3 = buildCouponParam("NormalCoupon", 0);
        couponService.create(p1);
        couponService.create(p2);
        couponService.create(p3);

        // 按名称筛选
        SmsCouponQueryParam queryParam = new SmsCouponQueryParam();
        queryParam.setName("Special");
        List<SmsCoupon> result = couponService.list(queryParam, 10, 1);

        assertTrue(result.size() >= 2, "应至少返回2条包含'Special'的优惠券");
        assertTrue(result.stream().allMatch(c -> c.getName().contains("Special")),
                "所有结果名称应包含'Special'");
        LOGGER.info("testListWithNameFilter: 返回{}条记录", result.size());
    }

    @Test
    @Transactional
    @Rollback
    public void testListWithUseTypeFilter() {
        // 插入不同 useType 的优惠券
        SmsCouponParam p1 = buildCouponParam("Universal1", 0);
        SmsCouponParam p2 = buildCouponParam("Category1", 1);
        List<SmsCouponProductCategoryRelation> catRelations = new ArrayList<>();
        catRelations.add(buildCategoryRelation(1L));
        p2.setProductCategoryRelationList(catRelations);
        SmsCouponParam p3 = buildCouponParam("Product1", 2);
        List<SmsCouponProductRelation> prodRelations = new ArrayList<>();
        prodRelations.add(buildProductRelation(1L));
        p3.setProductRelationList(prodRelations);
        couponService.create(p1);
        couponService.create(p2);
        couponService.create(p3);

        // 筛选 useType=2
        SmsCouponQueryParam queryParam = new SmsCouponQueryParam();
        queryParam.setUseType(2);
        List<SmsCoupon> result = couponService.list(queryParam, 10, 1);

        assertTrue(result.stream().allMatch(c -> Integer.valueOf(2).equals(c.getUseType())),
                "所有结果 useType 应为 2");
        LOGGER.info("testListWithUseTypeFilter: 返回{}条记录", result.size());
    }

    @Test
    @Transactional
    @Rollback
    public void testListWithDateRangeFilter() {
        // 插入不同时间段的优惠券
        long now = System.currentTimeMillis();
        SmsCouponParam p1 = buildCouponParam("DateRange1", 0);
        p1.setStartTime(new Date(now - 86400000L * 10));
        p1.setEndTime(new Date(now + 86400000L * 10));

        SmsCouponParam p2 = buildCouponParam("DateRange2", 0);
        p2.setStartTime(new Date(now + 86400000L * 20));
        p2.setEndTime(new Date(now + 86400000L * 40));

        couponService.create(p1);
        couponService.create(p2);

        // 筛选 startTime 在最近5天内（应命中 p1 不命中 p2）
        SmsCouponQueryParam queryParam = new SmsCouponQueryParam();
        queryParam.setStartTimeBegin(new Date(now - 86400000L * 15));
        queryParam.setStartTimeEnd(new Date(now + 86400000L * 5));
        List<SmsCoupon> result = couponService.list(queryParam, 10, 1);

        assertTrue(result.stream()
                        .allMatch(c -> !c.getStartTime().before(queryParam.getStartTimeBegin())
                                && !c.getStartTime().after(queryParam.getStartTimeEnd())),
                "所有结果的 startTime 应在指定范围内");
        LOGGER.info("testListWithDateRangeFilter: 返回{}条记录", result.size());
    }

    @Test
    @Transactional
    @Rollback
    public void testListWithCountRangeFilters() {
        // 插入不同数量的优惠券
        SmsCouponParam p1 = buildCouponParam("CountRange1", 0);
        p1.setPublishCount(50);

        SmsCouponParam p2 = buildCouponParam("CountRange2", 0);
        p2.setPublishCount(200);

        couponService.create(p1);
        couponService.create(p2);

        // 筛选剩余数量 count 在 40-60 之间（应命中 p1，因为 count=publishCount）
        SmsCouponQueryParam queryParam = new SmsCouponQueryParam();
        queryParam.setCountMin(40);
        queryParam.setCountMax(60);
        List<SmsCoupon> result = couponService.list(queryParam, 10, 1);

        assertTrue(result.stream()
                        .allMatch(c -> c.getCount() >= 40 && c.getCount() <= 60),
                "所有结果的 count 应在 40-60 范围内");
        LOGGER.info("testListWithCountRangeFilters: 返回{}条记录", result.size());
    }

    // ==================== B. 创建路径测试 ====================

    @Test
    @Transactional
    @Rollback
    public void testCreateUseType2WithValidRelations() {
        SmsCouponParam param = buildCouponParam("ProductCoupon", 2);
        List<SmsCouponProductRelation> relations = new ArrayList<>();
        relations.add(buildProductRelation(100L));
        relations.add(buildProductRelation(200L));
        relations.add(buildProductRelation(300L));
        param.setProductRelationList(relations);

        int count = couponService.create(param);
        assertEquals(1, count, "优惠券应创建成功");
        assertNotNull(param.getId(), "应返回自增ID");

        long relationCount = countProductRelations(param.getId());
        assertEquals(3, relationCount, "应有3条商品关联");
        LOGGER.info("testCreateUseType2WithValidRelations: 优惠券ID={}, 关联数={}", param.getId(), relationCount);
    }

    @Test
    @Transactional
    @Rollback
    public void testCreateUseType2WithNullRelations() {
        // 直接调用 service（绕过 validator），测试 service 层 null 安全
        SmsCouponParam param = buildCouponParam("NullRelationCoupon", 2);
        param.setProductRelationList(null);

        // 不应抛出 NPE
        assertDoesNotThrow(() -> couponService.create(param),
                "null 关联列表不应导致 NPE");
        assertNotNull(param.getId(), "优惠券应创建成功");

        long relationCount = countProductRelations(param.getId());
        assertEquals(0, relationCount, "null 列表不应插入任何关联");
        LOGGER.info("testCreateUseType2WithNullRelations: 无 NPE, 关联数=0");
    }

    @Test
    @Transactional
    @Rollback
    public void testCreateUseType1WithValidCategories() {
        SmsCouponParam param = buildCouponParam("CategoryCoupon", 1);
        List<SmsCouponProductCategoryRelation> relations = new ArrayList<>();
        relations.add(buildCategoryRelation(10L));
        relations.add(buildCategoryRelation(20L));
        param.setProductCategoryRelationList(relations);

        int count = couponService.create(param);
        assertEquals(1, count, "优惠券应创建成功");

        long relationCount = countCategoryRelations(param.getId());
        assertEquals(2, relationCount, "应有2条分类关联");
        LOGGER.info("testCreateUseType1WithValidCategories: 优惠券ID={}, 关联数={}", param.getId(), relationCount);
    }

    @Test
    @Transactional
    @Rollback
    public void testCreateUseType0NoRelations() {
        SmsCouponParam param = buildCouponParam("UniversalCoupon", 0);
        param.setProductRelationList(null);
        param.setProductCategoryRelationList(null);

        int count = couponService.create(param);
        assertEquals(1, count, "全场通用优惠券应创建成功");

        assertEquals(0, countProductRelations(param.getId()), "不应有商品关联");
        assertEquals(0, countCategoryRelations(param.getId()), "不应有分类关联");
        LOGGER.info("testCreateUseType0NoRelations: 无关联数据");
    }

    // ==================== C. 更新 useType 切换测试 ====================

    @Test
    @Transactional
    @Rollback
    public void testUpdateUseType2To1() {
        // 创建 useType=2 优惠券 + 2条商品关联
        SmsCouponParam createParam = buildCouponParam("SwitchTest_2to1", 2);
        List<SmsCouponProductRelation> prodRelations = new ArrayList<>();
        prodRelations.add(buildProductRelation(1L));
        prodRelations.add(buildProductRelation(2L));
        createParam.setProductRelationList(prodRelations);
        couponService.create(createParam);
        Long couponId = createParam.getId();

        assertEquals(2, countProductRelations(couponId), "初始应有2条商品关联");
        assertEquals(0, countCategoryRelations(couponId), "初始不应有分类关联");

        // 更新为 useType=1 + 1条分类关联
        SmsCouponParam updateParam = buildCouponParam("SwitchTest_2to1_Updated", 1);
        List<SmsCouponProductCategoryRelation> catRelations = new ArrayList<>();
        catRelations.add(buildCategoryRelation(50L));
        updateParam.setProductCategoryRelationList(catRelations);
        couponService.update(couponId, updateParam);

        // 验证旧商品关联已清除，新分类关联已插入
        assertEquals(0, countProductRelations(couponId),
                "切换后旧商品关联应被清除");
        assertEquals(1, countCategoryRelations(couponId),
                "切换后应有1条分类关联");
        LOGGER.info("testUpdateUseType2To1: 商品关联已清除，分类关联已建立");
    }

    @Test
    @Transactional
    @Rollback
    public void testUpdateUseType1To2() {
        // 创建 useType=1 + 2条分类关联
        SmsCouponParam createParam = buildCouponParam("SwitchTest_1to2", 1);
        List<SmsCouponProductCategoryRelation> catRelations = new ArrayList<>();
        catRelations.add(buildCategoryRelation(1L));
        catRelations.add(buildCategoryRelation(2L));
        createParam.setProductCategoryRelationList(catRelations);
        couponService.create(createParam);
        Long couponId = createParam.getId();

        assertEquals(0, countProductRelations(couponId), "初始不应有商品关联");
        assertEquals(2, countCategoryRelations(couponId), "初始应有2条分类关联");

        // 更新为 useType=2 + 1条商品关联
        SmsCouponParam updateParam = buildCouponParam("SwitchTest_1to2_Updated", 2);
        List<SmsCouponProductRelation> prodRelations = new ArrayList<>();
        prodRelations.add(buildProductRelation(99L));
        updateParam.setProductRelationList(prodRelations);
        couponService.update(couponId, updateParam);

        assertEquals(0, countCategoryRelations(couponId),
                "切换后旧分类关联应被清除");
        assertEquals(1, countProductRelations(couponId),
                "切换后应有1条商品关联");
        LOGGER.info("testUpdateUseType1To2: 分类关联已清除，商品关联已建立");
    }

    @Test
    @Transactional
    @Rollback
    public void testUpdateUseType2To0() {
        // 创建 useType=2 + 2条商品关联
        SmsCouponParam createParam = buildCouponParam("SwitchTest_2to0", 2);
        List<SmsCouponProductRelation> prodRelations = new ArrayList<>();
        prodRelations.add(buildProductRelation(1L));
        prodRelations.add(buildProductRelation(2L));
        createParam.setProductRelationList(prodRelations);
        couponService.create(createParam);
        Long couponId = createParam.getId();

        assertEquals(2, countProductRelations(couponId), "初始应有2条商品关联");

        // 更新为 useType=0（全场通用，不需要关联）
        SmsCouponParam updateParam = buildCouponParam("SwitchTest_2to0_Updated", 0);
        couponService.update(couponId, updateParam);

        // 关键断言：旧关联必须全部清除，避免孤立数据
        assertEquals(0, countProductRelations(couponId),
                "切换到全场通用后，旧商品关联必须被清除（孤立数据防护）");
        assertEquals(0, countCategoryRelations(couponId),
                "切换到全场通用后，不应有分类关联");
        LOGGER.info("testUpdateUseType2To0: 所有关联已清除，无孤立数据");
    }

    @Test
    @Transactional
    @Rollback
    public void testUpdateSameUseTypeReplace() {
        // 创建 useType=2 + 2条商品关联
        SmsCouponParam createParam = buildCouponParam("SameTypeReplace", 2);
        List<SmsCouponProductRelation> oldRelations = new ArrayList<>();
        oldRelations.add(buildProductRelation(1L));
        oldRelations.add(buildProductRelation(2L));
        createParam.setProductRelationList(oldRelations);
        couponService.create(createParam);
        Long couponId = createParam.getId();

        assertEquals(2, countProductRelations(couponId), "初始应有2条商品关联");

        // 同类型更新，替换为3条新关联
        SmsCouponParam updateParam = buildCouponParam("SameTypeReplace_Updated", 2);
        List<SmsCouponProductRelation> newRelations = new ArrayList<>();
        newRelations.add(buildProductRelation(10L));
        newRelations.add(buildProductRelation(20L));
        newRelations.add(buildProductRelation(30L));
        updateParam.setProductRelationList(newRelations);
        couponService.update(couponId, updateParam);

        long relationCount = countProductRelations(couponId);
        assertEquals(3, relationCount, "更新后应恰好有3条商品关联");

        // 验证旧关联已被替换
        SmsCouponProductRelationExample example = new SmsCouponProductRelationExample();
        example.createCriteria().andCouponIdEqualTo(couponId);
        List<SmsCouponProductRelation> dbRelations = productRelationMapper.selectByExample(example);
        List<Long> productIds = dbRelations.stream()
                .map(SmsCouponProductRelation::getProductId)
                .collect(Collectors.toList());
        assertFalse(productIds.contains(1L), "旧商品ID=1应已被删除");
        assertFalse(productIds.contains(2L), "旧商品ID=2应已被删除");
        assertTrue(productIds.contains(10L), "新商品ID=10应存在");
        LOGGER.info("testUpdateSameUseTypeReplace: 旧关联已替换为新关联");
    }

    // ==================== D. 校验器单元测试 ====================

    @Test
    public void testValidatorRejectsEmptyProductList() {
        SmsCouponParam param = buildCouponParam("ValidatorTest1", 2);
        param.setProductRelationList(new ArrayList<>()); // 空列表

        Set<ConstraintViolation<SmsCouponParam>> violations = validator.validate(param);
        assertFalse(violations.isEmpty(), "空商品关联列表应触发校验失败");

        String messages = violations.stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        assertTrue(messages.contains("商品关联列表不能为空"),
                "错误消息应提示商品关联列表不能为空，实际: " + messages);
        LOGGER.info("testValidatorRejectsEmptyProductList: violations={}", messages);
    }

    @Test
    public void testValidatorRejectsDuplicateProductIds() {
        SmsCouponParam param = buildCouponParam("ValidatorTest2", 2);
        List<SmsCouponProductRelation> relations = new ArrayList<>();
        relations.add(buildProductRelation(1L));
        relations.add(buildProductRelation(1L)); // 重复
        param.setProductRelationList(relations);

        Set<ConstraintViolation<SmsCouponParam>> violations = validator.validate(param);
        assertFalse(violations.isEmpty(), "重复商品ID应触发校验失败");

        String messages = violations.stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        assertTrue(messages.contains("重复"),
                "错误消息应提示重复，实际: " + messages);
        LOGGER.info("testValidatorRejectsDuplicateProductIds: violations={}", messages);
    }

    @Test
    public void testValidatorRejectsNullCategoryId() {
        SmsCouponParam param = buildCouponParam("ValidatorTest3", 1);
        List<SmsCouponProductCategoryRelation> relations = new ArrayList<>();
        relations.add(buildCategoryRelation(null)); // null categoryId
        param.setProductCategoryRelationList(relations);

        Set<ConstraintViolation<SmsCouponParam>> violations = validator.validate(param);
        assertFalse(violations.isEmpty(), "null 分类ID应触发校验失败");

        String messages = violations.stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        assertTrue(messages.contains("分类ID不能为空"),
                "错误消息应提示分类ID不能为空，实际: " + messages);
        LOGGER.info("testValidatorRejectsNullCategoryId: violations={}", messages);
    }

    @Test
    public void testValidatorPassesUseType0() {
        SmsCouponParam param = buildCouponParam("ValidatorTest4", 0);
        param.setProductRelationList(null);
        param.setProductCategoryRelationList(null);

        Set<ConstraintViolation<SmsCouponParam>> violations = validator.validate(param);
        // 过滤掉非 CouponRelationsValidator 产生的 violation
        long relationViolations = violations.stream()
                .filter(v -> v.getMessage().contains("关联"))
                .count();
        assertEquals(0, relationViolations,
                "useType=0 全场通用不应触发关联列表校验");
        LOGGER.info("testValidatorPassesUseType0: 无关联校验违规");
    }
}
