package com.macro.mall.service.impl;

import cn.hutool.core.util.StrUtil;
import com.github.pagehelper.PageHelper;
import com.macro.mall.dao.SmsCouponDao;
import com.macro.mall.dao.SmsCouponProductCategoryRelationDao;
import com.macro.mall.dao.SmsCouponProductRelationDao;
import com.macro.mall.dto.SmsCouponParam;
import com.macro.mall.dto.SmsCouponQueryParam;
import com.macro.mall.mapper.SmsCouponMapper;
import com.macro.mall.mapper.SmsCouponProductCategoryRelationMapper;
import com.macro.mall.mapper.SmsCouponProductRelationMapper;
import com.macro.mall.model.*;
import com.macro.mall.service.SmsCouponService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

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
        couponParam.setCount(couponParam.getPublishCount());
        couponParam.setUseCount(0);
        couponParam.setReceiveCount(0);
        //插入优惠券表
        int count = couponMapper.insert(couponParam);
        //插入优惠券和商品关系表（null-safe）
        if (Integer.valueOf(2).equals(couponParam.getUseType())) {
            List<SmsCouponProductRelation> relations = couponParam.getProductRelationList();
            if (relations != null && !relations.isEmpty()) {
                for (SmsCouponProductRelation productRelation : relations) {
                    productRelation.setCouponId(couponParam.getId());
                }
                productRelationDao.insertList(relations);
            }
        }
        //插入优惠券和商品分类关系表（null-safe）
        if (Integer.valueOf(1).equals(couponParam.getUseType())) {
            List<SmsCouponProductCategoryRelation> relations = couponParam.getProductCategoryRelationList();
            if (relations != null && !relations.isEmpty()) {
                for (SmsCouponProductCategoryRelation couponProductCategoryRelation : relations) {
                    couponProductCategoryRelation.setCouponId(couponParam.getId());
                }
                productCategoryRelationDao.insertList(relations);
            }
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

    @Override
    public int update(Long id, SmsCouponParam couponParam) {
        couponParam.setId(id);
        int count = couponMapper.updateByPrimaryKey(couponParam);
        // 无论 useType 如何变化，先清理所有旧关联，避免切换 useType 时产生孤立数据
        deleteProductRelation(id);
        deleteProductCategoryRelation(id);
        //按新 useType 插入关联（null-safe）
        if (Integer.valueOf(2).equals(couponParam.getUseType())) {
            List<SmsCouponProductRelation> relations = couponParam.getProductRelationList();
            if (relations != null && !relations.isEmpty()) {
                for (SmsCouponProductRelation productRelation : relations) {
                    productRelation.setCouponId(couponParam.getId());
                }
                productRelationDao.insertList(relations);
            }
        }
        if (Integer.valueOf(1).equals(couponParam.getUseType())) {
            List<SmsCouponProductCategoryRelation> relations = couponParam.getProductCategoryRelationList();
            if (relations != null && !relations.isEmpty()) {
                for (SmsCouponProductCategoryRelation couponProductCategoryRelation : relations) {
                    couponProductCategoryRelation.setCouponId(couponParam.getId());
                }
                productCategoryRelationDao.insertList(relations);
            }
        }
        return count;
    }

    @Override
    public List<SmsCoupon> list(SmsCouponQueryParam queryParam, Integer pageSize, Integer pageNum) {
        SmsCouponExample example = new SmsCouponExample();
        SmsCouponExample.Criteria criteria = example.createCriteria();

        if (queryParam != null) {
            // 名称模糊匹配（向后兼容）
            if (!StrUtil.isEmpty(queryParam.getName())) {
                criteria.andNameLike("%" + queryParam.getName() + "%");
            }
            // 优惠券类型精确匹配（向后兼容）
            if (queryParam.getType() != null) {
                criteria.andTypeEqualTo(queryParam.getType());
            }
            // 优惠券状态精确匹配
            if (queryParam.getStatus() != null) {
                criteria.andStatusEqualTo(queryParam.getStatus());
            }
            // 使用类型精确匹配
            if (queryParam.getUseType() != null) {
                criteria.andUseTypeEqualTo(queryParam.getUseType());
            }
            // 有效期起始时间范围
            if (queryParam.getStartTimeBegin() != null) {
                criteria.andStartTimeGreaterThanOrEqualTo(queryParam.getStartTimeBegin());
            }
            if (queryParam.getStartTimeEnd() != null) {
                criteria.andStartTimeLessThanOrEqualTo(queryParam.getStartTimeEnd());
            }
            // 有效期结束时间范围
            if (queryParam.getEndTimeBegin() != null) {
                criteria.andEndTimeGreaterThanOrEqualTo(queryParam.getEndTimeBegin());
            }
            if (queryParam.getEndTimeEnd() != null) {
                criteria.andEndTimeLessThanOrEqualTo(queryParam.getEndTimeEnd());
            }
            // 领取数量区间
            if (queryParam.getReceiveCountMin() != null && queryParam.getReceiveCountMax() != null) {
                criteria.andReceiveCountBetween(queryParam.getReceiveCountMin(), queryParam.getReceiveCountMax());
            } else if (queryParam.getReceiveCountMin() != null) {
                criteria.andReceiveCountGreaterThanOrEqualTo(queryParam.getReceiveCountMin());
            } else if (queryParam.getReceiveCountMax() != null) {
                criteria.andReceiveCountLessThanOrEqualTo(queryParam.getReceiveCountMax());
            }
            // 剩余数量区间
            if (queryParam.getCountMin() != null && queryParam.getCountMax() != null) {
                criteria.andCountBetween(queryParam.getCountMin(), queryParam.getCountMax());
            } else if (queryParam.getCountMin() != null) {
                criteria.andCountGreaterThanOrEqualTo(queryParam.getCountMin());
            } else if (queryParam.getCountMax() != null) {
                criteria.andCountLessThanOrEqualTo(queryParam.getCountMax());
            }
        }

        PageHelper.startPage(pageNum, pageSize);
        return couponMapper.selectByExample(example);
    }

    @Override
    public SmsCouponParam getItem(Long id) {
        return couponDao.getItem(id);
    }
}
