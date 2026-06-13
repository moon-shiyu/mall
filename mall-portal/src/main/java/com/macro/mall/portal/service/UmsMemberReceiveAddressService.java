package com.macro.mall.portal.service;

import com.macro.mall.model.UmsMemberReceiveAddress;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户地址管理Service
 * Created by macro on 2018/8/28.
 */
public interface UmsMemberReceiveAddressService {
    /**
     * 添加收货地址
     * - 当 defaultStatus=1 时，自动清除同会员下其他默认地址
     * - 校验必填字段
     */
    @Transactional
    int add(UmsMemberReceiveAddress address);

    /**
     * 删除收货地址
     * - 删除当前默认地址后，自动将最新地址设为默认
     * - 仅允许删除当前会员自己的地址
     * @param id 地址表的id
     */
    @Transactional
    int delete(Long id);

    /**
     * 修改收货地址
     * - 当 defaultStatus=1 时，自动清除同会员下其他默认地址
     * - 仅允许修改当前会员自己的地址
     * @param id 地址表的id
     * @param address 修改的收货地址信息
     */
    @Transactional
    int update(Long id, UmsMemberReceiveAddress address);

    /**
     * 返回当前用户的收货地址列表
     * - 默认地址优先，按 id 倒序排列
     */
    List<UmsMemberReceiveAddress> list();

    /**
     * 获取地址详情
     * - 仅允许获取当前会员自己的地址
     * @param id 地址id
     */
    UmsMemberReceiveAddress getItem(Long id);
}
