package com.macro.mall.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.github.pagehelper.PageHelper;
import com.macro.mall.dao.SmsCouponDao;
import com.macro.mall.dao.SmsCouponProductCategoryRelationDao;
import com.macro.mall.dao.SmsCouponProductRelationDao;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.dto.SmsCouponParam;
import com.macro.mall.mapper.SmsCouponMapper;
import com.macro.mall.mapper.SmsCouponProductCategoryRelationMapper;
import com.macro.mall.mapper.SmsCouponProductRelationMapper;
import com.macro.mall.model.*;
import com.macro.mall.service.SmsCouponService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 优惠券管理Service实现类
 * Created by macro on 2018/8/28.
 */
@Service
public class SmsCouponServiceImpl implements SmsCouponService {
    @Autowired
    private SmsCouponMapper couponMapper;
    @Autowired
    private SmsCouponProductRelationMapper productRelationMapper;
    @Autowired
    private SmsCouponProductCategoryRelationMapper productCategoryRelationMapper;
    @Autowired
    private SmsCouponProductRelationDao productRelationDao;
    @Autowired
    private SmsCouponProductCategoryRelationDao productCategoryRelationDao;
    @Autowired
    private SmsCouponDao couponDao;
    @Override
    public int create(SmsCouponParam couponParam) {
        //校验使用类型及关联列表（为空/重复/缺失ID等）
        validateUseTypeRelations(couponParam);
        couponParam.setCount(couponParam.getPublishCount());
        couponParam.setUseCount(0);
        couponParam.setReceiveCount(0);
        //插入优惠券表
        int count = couponMapper.insert(couponParam);
        //插入优惠券和商品关系表
        if(couponParam.getUseType().equals(2)){
            for(SmsCouponProductRelation productRelation:couponParam.getProductRelationList()){
                productRelation.setCouponId(couponParam.getId());
            }
            productRelationDao.insertList(couponParam.getProductRelationList());
        }
        //插入优惠券和商品分类关系表
        if(couponParam.getUseType().equals(1)){
            for (SmsCouponProductCategoryRelation couponProductCategoryRelation : couponParam.getProductCategoryRelationList()) {
                couponProductCategoryRelation.setCouponId(couponParam.getId());
            }
            productCategoryRelationDao.insertList(couponParam.getProductCategoryRelationList());
        }
        return count;
    }

    @Override
    public int delete(Long id) {
        //删除优惠券
        int count = couponMapper.deleteByPrimaryKey(id);
        //删除商品关联
        deleteProductRelation(id);
        //删除商品分类关联
        deleteProductCategoryRelation(id);
        return count;
    }

    private void deleteProductCategoryRelation(Long id) {
        SmsCouponProductCategoryRelationExample productCategoryRelationExample = new SmsCouponProductCategoryRelationExample();
        productCategoryRelationExample.createCriteria().andCouponIdEqualTo(id);
        productCategoryRelationMapper.deleteByExample(productCategoryRelationExample);
    }

    private void deleteProductRelation(Long id) {
        SmsCouponProductRelationExample productRelationExample = new SmsCouponProductRelationExample();
        productRelationExample.createCriteria().andCouponIdEqualTo(id);
        productRelationMapper.deleteByExample(productRelationExample);
    }

    /**
     * 校验优惠券使用类型与其关联列表：
     * useType 为指定商品(2)或指定分类(1)时，关联列表不能为空、不能存在缺失ID、不能重复。
     * 校验失败时通过 Asserts.fail 抛出业务异常，由全局异常处理转为 CommonResult。
     */
    private void validateUseTypeRelations(SmsCouponParam couponParam) {
        Integer useType = couponParam.getUseType();
        if (useType == null) {
            Asserts.fail("请选择优惠券使用类型");
        }
        if (useType.equals(2)) {
            List<SmsCouponProductRelation> relationList = couponParam.getProductRelationList();
            if (CollUtil.isEmpty(relationList)) {
                Asserts.fail("指定商品类型的优惠券必须关联至少一个商品");
            }
            Set<Long> productIds = new HashSet<>();
            for (SmsCouponProductRelation relation : relationList) {
                Long productId = relation.getProductId();
                if (productId == null) {
                    Asserts.fail("关联商品列表存在缺失的商品ID");
                }
                if (!productIds.add(productId)) {
                    Asserts.fail("关联商品列表存在重复的商品");
                }
            }
        } else if (useType.equals(1)) {
            List<SmsCouponProductCategoryRelation> relationList = couponParam.getProductCategoryRelationList();
            if (CollUtil.isEmpty(relationList)) {
                Asserts.fail("指定分类类型的优惠券必须关联至少一个商品分类");
            }
            Set<Long> categoryIds = new HashSet<>();
            for (SmsCouponProductCategoryRelation relation : relationList) {
                Long categoryId = relation.getProductCategoryId();
                if (categoryId == null) {
                    Asserts.fail("关联分类列表存在缺失的分类ID");
                }
                if (!categoryIds.add(categoryId)) {
                    Asserts.fail("关联分类列表存在重复的分类");
                }
            }
        }
    }

    @Override
    public int update(Long id, SmsCouponParam couponParam) {
        //校验使用类型及关联列表（为空/重复/缺失ID等）
        validateUseTypeRelations(couponParam);
        couponParam.setId(id);
        int count =couponMapper.updateByPrimaryKey(couponParam);
        //无论原useType为何，先清理两类旧关联，避免useType切换后残留脏数据
        deleteProductRelation(id);
        deleteProductCategoryRelation(id);
        //按新的useType重新插入对应关联
        if(couponParam.getUseType().equals(2)){
            for(SmsCouponProductRelation productRelation:couponParam.getProductRelationList()){
                productRelation.setCouponId(id);
            }
            productRelationDao.insertList(couponParam.getProductRelationList());
        }
        if(couponParam.getUseType().equals(1)){
            for (SmsCouponProductCategoryRelation couponProductCategoryRelation : couponParam.getProductCategoryRelationList()) {
                couponProductCategoryRelation.setCouponId(id);
            }
            productCategoryRelationDao.insertList(couponParam.getProductCategoryRelationList());
        }
        return count;
    }

    @Override
    public List<SmsCoupon> list(String name, Integer type, Integer useType, Integer status,
                                Date startTime, Date endTime,
                                Integer minReceiveCount, Integer maxReceiveCount,
                                Integer minRemainCount, Integer maxRemainCount,
                                Integer pageSize, Integer pageNum) {
        SmsCouponExample example = new SmsCouponExample();
        SmsCouponExample.Criteria criteria = example.createCriteria();
        if(!StrUtil.isEmpty(name)){
            criteria.andNameLike("%"+name+"%");
        }
        if(type!=null){
            criteria.andTypeEqualTo(type);
        }
        if(useType!=null){
            criteria.andUseTypeEqualTo(useType);
        }
        //状态由有效期推导：0->未开始；1->进行中；2->已结束
        if(status!=null){
            Date now = new Date();
            if(status.equals(0)){
                criteria.andStartTimeGreaterThan(now);
            }else if(status.equals(1)){
                criteria.andStartTimeLessThanOrEqualTo(now);
                criteria.andEndTimeGreaterThanOrEqualTo(now);
            }else if(status.equals(2)){
                criteria.andEndTimeLessThan(now);
            }
        }
        //有效期范围：生效时间不早于startTime，失效时间不晚于endTime（两个边界相互独立、均可选）
        if(startTime!=null){
            criteria.andStartTimeGreaterThanOrEqualTo(startTime);
        }
        if(endTime!=null){
            criteria.andEndTimeLessThanOrEqualTo(endTime);
        }
        //领取数量区间（支持单边）
        if(minReceiveCount!=null&&maxReceiveCount!=null){
            criteria.andReceiveCountBetween(minReceiveCount,maxReceiveCount);
        }else if(minReceiveCount!=null){
            criteria.andReceiveCountGreaterThanOrEqualTo(minReceiveCount);
        }else if(maxReceiveCount!=null){
            criteria.andReceiveCountLessThanOrEqualTo(maxReceiveCount);
        }
        //剩余数量区间（count为剩余库存，支持单边）
        if(minRemainCount!=null&&maxRemainCount!=null){
            criteria.andCountBetween(minRemainCount,maxRemainCount);
        }else if(minRemainCount!=null){
            criteria.andCountGreaterThanOrEqualTo(minRemainCount);
        }else if(maxRemainCount!=null){
            criteria.andCountLessThanOrEqualTo(maxRemainCount);
        }
        PageHelper.startPage(pageNum,pageSize);
        return couponMapper.selectByExample(example);
    }

    @Override
    public SmsCouponParam getItem(Long id) {
        return couponDao.getItem(id);
    }
}
