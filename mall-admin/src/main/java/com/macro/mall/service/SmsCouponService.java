package com.macro.mall.service;

import com.macro.mall.dto.SmsCouponParam;
import com.macro.mall.model.SmsCoupon;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 优惠券管理Service
 * Created by macro on 2018/8/28.
 */
public interface SmsCouponService {
    /**
     * 添加优惠券
     */
    @Transactional
    int create(SmsCouponParam couponParam);

    /**
     * 根据优惠券id删除优惠券
     */
    @Transactional
    int delete(Long id);

    /**
     * 根据优惠券id更新优惠券信息
     */
    @Transactional
    int update(Long id, SmsCouponParam couponParam);

    /**
     * 分页获取优惠券列表
     *
     * @param name            优惠券名称（模糊匹配）
     * @param type            优惠券类型
     * @param useType         使用类型：0->全场通用；1->指定分类；2->指定商品
     * @param status          状态（由有效期推导）：0->未开始；1->进行中；2->已结束
     * @param startTime       有效期范围：生效时间不早于该值
     * @param endTime         有效期范围：失效时间不晚于该值
     * @param minReceiveCount 领取数量区间下限
     * @param maxReceiveCount 领取数量区间上限
     * @param minRemainCount  剩余数量区间下限
     * @param maxRemainCount  剩余数量区间上限
     */
    List<SmsCoupon> list(String name, Integer type, Integer useType, Integer status,
                         Date startTime, Date endTime,
                         Integer minReceiveCount, Integer maxReceiveCount,
                         Integer minRemainCount, Integer maxRemainCount,
                         Integer pageSize, Integer pageNum);

    /**
     * 获取优惠券详情
     * @param id 优惠券表id
     */
    SmsCouponParam getItem(Long id);
}
