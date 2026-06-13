package com.macro.mall.portal.service.impl;

import com.macro.mall.common.exception.Asserts;
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

import java.util.List;

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
    @Transactional(rollbackFor = Exception.class)
    public int add(UmsMemberReceiveAddress address) {
        UmsMember currentMember = memberService.getCurrentMember();
        validateAddress(address);
        if (address.getDefaultStatus() == null) {
            address.setDefaultStatus(0);
        }
        //设置为默认地址时，先清除该会员已有的默认地址，保证同一会员只有一个默认地址
        if (isDefault(address.getDefaultStatus())) {
            clearMemberDefault(currentMember.getId());
        }
        //防止越权：强制绑定当前会员，忽略客户端传入的id/memberId
        address.setId(null);
        address.setMemberId(currentMember.getId());
        return addressMapper.insert(address);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        UmsMember currentMember = memberService.getCurrentMember();
        //校验地址归属，杜绝通过id删除他人地址
        UmsMemberReceiveAddress existAddress = getOwnedAddress(currentMember.getId(), id);
        boolean wasDefault = isDefault(existAddress.getDefaultStatus());
        UmsMemberReceiveAddressExample example = new UmsMemberReceiveAddressExample();
        example.createCriteria().andMemberIdEqualTo(currentMember.getId()).andIdEqualTo(id);
        int count = addressMapper.deleteByExample(example);
        //删除的是默认地址且仍有其它地址时，自动选取一个作为新的默认地址
        if (wasDefault) {
            assignNewDefault(currentMember.getId());
        }
        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(Long id, UmsMemberReceiveAddress address) {
        UmsMember currentMember = memberService.getCurrentMember();
        //校验地址归属，杜绝通过id修改他人地址
        getOwnedAddress(currentMember.getId(), id);
        validateAddress(address);
        if (address.getDefaultStatus() == null) {
            address.setDefaultStatus(0);
        }
        //设置为默认地址时，先清除该会员已有的默认地址，保证同一会员只有一个默认地址
        if (isDefault(address.getDefaultStatus())) {
            clearMemberDefault(currentMember.getId());
        }
        //强制绑定id与会员，避免主键/归属被篡改
        address.setId(null);
        address.setMemberId(currentMember.getId());
        UmsMemberReceiveAddressExample example = new UmsMemberReceiveAddressExample();
        example.createCriteria().andMemberIdEqualTo(currentMember.getId()).andIdEqualTo(id);
        return addressMapper.updateByExampleSelective(address, example);
    }

    @Override
    public List<UmsMemberReceiveAddress> list() {
        UmsMember currentMember = memberService.getCurrentMember();
        UmsMemberReceiveAddressExample example = new UmsMemberReceiveAddressExample();
        example.createCriteria().andMemberIdEqualTo(currentMember.getId());
        //默认地址优先，其余按id倒序(创建顺序)稳定排序
        example.setOrderByClause("default_status desc, id desc");
        return addressMapper.selectByExample(example);
    }

    @Override
    public UmsMemberReceiveAddress getItem(Long id) {
        UmsMember currentMember = memberService.getCurrentMember();
        return getOwnedAddress(currentMember.getId(), id);
    }

    /**
     * 获取并校验当前会员名下的指定地址，id非法、地址不存在或不属于当前会员时抛出清晰异常
     */
    private UmsMemberReceiveAddress getOwnedAddress(Long memberId, Long id) {
        if (id == null) {
            Asserts.fail("收货地址id不能为空");
        }
        UmsMemberReceiveAddressExample example = new UmsMemberReceiveAddressExample();
        example.createCriteria().andMemberIdEqualTo(memberId).andIdEqualTo(id);
        List<UmsMemberReceiveAddress> list = addressMapper.selectByExample(example);
        if (CollectionUtils.isEmpty(list)) {
            Asserts.fail("收货地址不存在或无权操作");
        }
        return list.get(0);
    }

    /**
     * 清除当前会员已有的默认地址标记
     */
    private void clearMemberDefault(Long memberId) {
        UmsMemberReceiveAddress record = new UmsMemberReceiveAddress();
        record.setDefaultStatus(0);
        UmsMemberReceiveAddressExample example = new UmsMemberReceiveAddressExample();
        example.createCriteria()
                .andMemberIdEqualTo(memberId)
                .andDefaultStatusEqualTo(1);
        addressMapper.updateByExampleSelective(record, example);
    }

    /**
     * 删除默认地址后，从剩余地址中按创建顺序(id倒序)选取最新的一条作为新的默认地址；
     * 没有剩余地址时不做任何处理。
     */
    private void assignNewDefault(Long memberId) {
        UmsMemberReceiveAddressExample example = new UmsMemberReceiveAddressExample();
        example.createCriteria().andMemberIdEqualTo(memberId);
        example.setOrderByClause("id desc");
        List<UmsMemberReceiveAddress> remaining = addressMapper.selectByExample(example);
        if (CollectionUtils.isEmpty(remaining)) {
            return;
        }
        UmsMemberReceiveAddress newDefault = remaining.get(0);
        UmsMemberReceiveAddress record = new UmsMemberReceiveAddress();
        record.setDefaultStatus(1);
        UmsMemberReceiveAddressExample updateExample = new UmsMemberReceiveAddressExample();
        updateExample.createCriteria()
                .andMemberIdEqualTo(memberId)
                .andIdEqualTo(newDefault.getId());
        addressMapper.updateByExampleSelective(record, updateExample);
    }

    private boolean isDefault(Integer defaultStatus) {
        return Integer.valueOf(1).equals(defaultStatus);
    }

    /**
     * 校验地址必填字段，字段缺失或为空白时返回清晰的错误信息
     */
    private void validateAddress(UmsMemberReceiveAddress address) {
        if (address == null) {
            Asserts.fail("收货地址信息不能为空");
        }
        if (!StringUtils.hasText(address.getName())) {
            Asserts.fail("收货人名称不能为空");
        }
        if (!StringUtils.hasText(address.getPhoneNumber())) {
            Asserts.fail("收货人电话不能为空");
        }
        if (!StringUtils.hasText(address.getProvince())) {
            Asserts.fail("省份/直辖市不能为空");
        }
        if (!StringUtils.hasText(address.getCity())) {
            Asserts.fail("城市不能为空");
        }
        if (!StringUtils.hasText(address.getRegion())) {
            Asserts.fail("区不能为空");
        }
        if (!StringUtils.hasText(address.getDetailAddress())) {
            Asserts.fail("详细地址不能为空");
        }
    }
}
