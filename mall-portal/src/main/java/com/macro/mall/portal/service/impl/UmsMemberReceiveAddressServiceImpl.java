package com.macro.mall.portal.service.impl;

import com.macro.mall.mapper.UmsMemberReceiveAddressMapper;
import com.macro.mall.model.UmsMember;
import com.macro.mall.model.UmsMemberReceiveAddress;
import com.macro.mall.model.UmsMemberReceiveAddressExample;
import com.macro.mall.portal.service.UmsMemberReceiveAddressService;
import com.macro.mall.portal.service.UmsMemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户地址管理Service实现类
 * Created by macro on 2018/8/28.
 */
@Service
public class UmsMemberReceiveAddressServiceImpl implements UmsMemberReceiveAddressService {
    @Autowired
    private UmsMemberService memberService;
    @Autowired
    private UmsMemberReceiveAddressMapper addressMapper;

    @Override
    @Transactional
    public int add(UmsMemberReceiveAddress address) {
        if (address == null) {
            throw new IllegalArgumentException("地址信息不能为空");
        }
        UmsMember currentMember = memberService.getCurrentMember();
        address.setMemberId(currentMember.getId());

        // 校验必填字段
        validateAddress(address);

        // 处理默认地址逻辑
        if (address.getDefaultStatus() == null) {
            address.setDefaultStatus(0);
        }
        if (address.getDefaultStatus() == 1) {
            // 清除该会员下原有的默认地址
            clearDefaultAddress(currentMember.getId());
        }
        return addressMapper.insert(address);
    }

    @Override
    @Transactional
    public int delete(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("地址id不能为空");
        }
        UmsMember currentMember = memberService.getCurrentMember();
        Long memberId = currentMember.getId();

        // 先查询要删除的地址，判断是否为默认地址
        UmsMemberReceiveAddress toDelete = getItem(id);
        if (toDelete == null) {
            throw new IllegalArgumentException("地址不存在或无权操作");
        }
        boolean wasDefault = toDelete.getDefaultStatus() != null && toDelete.getDefaultStatus() == 1;

        // 删除地址（已含会员隔离条件）
        UmsMemberReceiveAddressExample example = new UmsMemberReceiveAddressExample();
        example.createCriteria().andMemberIdEqualTo(memberId).andIdEqualTo(id);
        int count = addressMapper.deleteByExample(example);

        // 如果删除的是默认地址，自动选择一个新默认地址
        if (wasDefault && count > 0) {
            autoSelectNewDefault(memberId);
        }
        return count;
    }

    @Override
    @Transactional
    public int update(Long id, UmsMemberReceiveAddress address) {
        if (id == null) {
            throw new IllegalArgumentException("地址id不能为空");
        }
        if (address == null) {
            throw new IllegalArgumentException("地址信息不能为空");
        }
        address.setId(null);
        address.setMemberId(null); // 防止客户端篡改会员归属
        UmsMember currentMember = memberService.getCurrentMember();
        Long memberId = currentMember.getId();

        // 校验该地址是否属于当前会员
        UmsMemberReceiveAddress existing = getItem(id);
        if (existing == null) {
            throw new IllegalArgumentException("地址不存在或无权操作");
        }

        // 校验必填字段
        validateAddress(address);

        if (address.getDefaultStatus() == null) {
            address.setDefaultStatus(0);
        }
        if (address.getDefaultStatus() == 1) {
            // 清除该会员下原有的默认地址
            clearDefaultAddress(memberId);
        }

        UmsMemberReceiveAddressExample example = new UmsMemberReceiveAddressExample();
        example.createCriteria().andMemberIdEqualTo(memberId).andIdEqualTo(id);
        return addressMapper.updateByExampleSelective(address, example);
    }

    @Override
    public List<UmsMemberReceiveAddress> list() {
        UmsMember currentMember = memberService.getCurrentMember();
        UmsMemberReceiveAddressExample example = new UmsMemberReceiveAddressExample();
        example.createCriteria().andMemberIdEqualTo(currentMember.getId());
        List<UmsMemberReceiveAddress> addressList = addressMapper.selectByExample(example);

        // 排序：默认地址优先，其次按 id 倒序（较新地址靠前）
        if (!CollectionUtils.isEmpty(addressList)) {
            addressList = addressList.stream()
                    .sorted(Comparator
                            .comparingInt((UmsMemberReceiveAddress a) ->
                                    a.getDefaultStatus() != null && a.getDefaultStatus() == 1 ? 0 : 1)
                            .thenComparing(Comparator.comparingLong(
                                    (UmsMemberReceiveAddress a) -> a.getId() != null ? a.getId() : 0L).reversed()))
                    .collect(Collectors.toList());
        }
        return addressList;
    }

    @Override
    public UmsMemberReceiveAddress getItem(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("地址id不能为空");
        }
        UmsMember currentMember = memberService.getCurrentMember();
        UmsMemberReceiveAddressExample example = new UmsMemberReceiveAddressExample();
        example.createCriteria().andMemberIdEqualTo(currentMember.getId()).andIdEqualTo(id);
        List<UmsMemberReceiveAddress> addressList = addressMapper.selectByExample(example);
        if (!CollectionUtils.isEmpty(addressList)) {
            return addressList.get(0);
        }
        return null;
    }

    /**
     * 清除指定会员下的所有默认地址标记
     */
    private void clearDefaultAddress(Long memberId) {
        UmsMemberReceiveAddress record = new UmsMemberReceiveAddress();
        record.setDefaultStatus(0);
        UmsMemberReceiveAddressExample updateExample = new UmsMemberReceiveAddressExample();
        updateExample.createCriteria()
                .andMemberIdEqualTo(memberId)
                .andDefaultStatusEqualTo(1);
        addressMapper.updateByExampleSelective(record, updateExample);
    }

    /**
     * 自动选择一个新的默认地址（按 id 倒序取最新的一条）
     */
    private void autoSelectNewDefault(Long memberId) {
        UmsMemberReceiveAddressExample queryExample = new UmsMemberReceiveAddressExample();
        queryExample.createCriteria().andMemberIdEqualTo(memberId);
        queryExample.setOrderByClause("id desc");
        List<UmsMemberReceiveAddress> remaining = addressMapper.selectByExample(queryExample);
        if (!CollectionUtils.isEmpty(remaining)) {
            UmsMemberReceiveAddress newDefault = new UmsMemberReceiveAddress();
            newDefault.setDefaultStatus(1);
            UmsMemberReceiveAddressExample updateExample = new UmsMemberReceiveAddressExample();
            updateExample.createCriteria()
                    .andMemberIdEqualTo(memberId)
                    .andIdEqualTo(remaining.get(0).getId());
            addressMapper.updateByExampleSelective(newDefault, updateExample);
        }
    }

    /**
     * 校验地址必填字段
     */
    private void validateAddress(UmsMemberReceiveAddress address) {
        if (!StringUtils.hasText(address.getName())) {
            throw new IllegalArgumentException("收货人姓名不能为空");
        }
        if (!StringUtils.hasText(address.getPhoneNumber())) {
            throw new IllegalArgumentException("手机号不能为空");
        }
        if (!StringUtils.hasText(address.getProvince())) {
            throw new IllegalArgumentException("省份不能为空");
        }
        if (!StringUtils.hasText(address.getCity())) {
            throw new IllegalArgumentException("城市不能为空");
        }
        if (!StringUtils.hasText(address.getRegion())) {
            throw new IllegalArgumentException("区/县不能为空");
        }
        if (!StringUtils.hasText(address.getDetailAddress())) {
            throw new IllegalArgumentException("详细地址不能为空");
        }
    }
}
