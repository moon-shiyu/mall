package com.macro.mall.portal;

import com.macro.mall.mapper.UmsMemberReceiveAddressMapper;
import com.macro.mall.model.UmsMember;
import com.macro.mall.model.UmsMemberReceiveAddress;
import com.macro.mall.model.UmsMemberReceiveAddressExample;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.portal.service.impl.UmsMemberReceiveAddressServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 会员收货地址Service单元测试
 * 覆盖场景：新增默认、更新默认、删除默认后自动重设、越权id、列表排序、无剩余地址
 */
@ExtendWith(MockitoExtension.class)
public class UmsMemberReceiveAddressServiceTests {

    @Mock
    private UmsMemberService memberService;

    @Mock
    private UmsMemberReceiveAddressMapper addressMapper;

    @InjectMocks
    private UmsMemberReceiveAddressServiceImpl addressService;

    private UmsMember currentMember;

    @BeforeEach
    void setUp() {
        currentMember = new UmsMember();
        currentMember.setId(1L);
    }

    // ========== 新增地址场景 ==========

    @Test
    void add_withDefaultStatus_shouldClearOldDefaultAndInsert() {
        when(memberService.getCurrentMember()).thenReturn(currentMember);
        when(addressMapper.updateByExampleSelective(any(), any())).thenReturn(1);
        when(addressMapper.insert(any())).thenReturn(1);

        UmsMemberReceiveAddress address = buildValidAddress();
        address.setDefaultStatus(1);

        int result = addressService.add(address);

        assertEquals(1, result);
        // 验证清除了旧的默认地址
        verify(addressMapper).updateByExampleSelective(any(UmsMemberReceiveAddress.class), any(UmsMemberReceiveAddressExample.class));
        // 验证插入了新地址
        verify(addressMapper).insert(address);
        assertEquals(1L, address.getMemberId());
    }

    @Test
    void add_withoutDefaultStatus_shouldInsertDirectly() {
        when(memberService.getCurrentMember()).thenReturn(currentMember);
        when(addressMapper.insert(any())).thenReturn(1);

        UmsMemberReceiveAddress address = buildValidAddress();
        address.setDefaultStatus(0);

        int result = addressService.add(address);

        assertEquals(1, result);
        // 不应清除默认地址
        verify(addressMapper, never()).updateByExampleSelective(any(), any());
        verify(addressMapper).insert(address);
    }

    @Test
    void add_withEmptyName_shouldThrowException() {
        when(memberService.getCurrentMember()).thenReturn(currentMember);

        UmsMemberReceiveAddress address = buildValidAddress();
        address.setName("");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> addressService.add(address));
        assertEquals("收货人姓名不能为空", ex.getMessage());
    }

    @Test
    void add_withNullPhoneNumber_shouldThrowException() {
        when(memberService.getCurrentMember()).thenReturn(currentMember);

        UmsMemberReceiveAddress address = buildValidAddress();
        address.setPhoneNumber(null);

        assertThrows(IllegalArgumentException.class, () -> addressService.add(address));
    }

    @Test
    void add_withNullBody_shouldThrowException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> addressService.add(null));
        assertEquals("地址信息不能为空", ex.getMessage());
    }

    // ========== 更新地址场景 ==========

    @Test
    void update_withDefaultStatus_shouldClearOldDefaultAndUpdate() {
        when(memberService.getCurrentMember()).thenReturn(currentMember);
        // getItem 内部调用：验证地址归属
        UmsMemberReceiveAddress existing = buildValidAddress();
        existing.setId(10L);
        existing.setMemberId(1L);
        when(addressMapper.selectByExample(any(UmsMemberReceiveAddressExample.class)))
                .thenReturn(Collections.singletonList(existing));
        when(addressMapper.updateByExampleSelective(any(), any())).thenReturn(1);

        UmsMemberReceiveAddress address = buildValidAddress();
        address.setDefaultStatus(1);

        int result = addressService.update(10L, address);

        assertEquals(1, result);
        // 至少调用两次 updateByExampleSelective：一次清除旧默认，一次更新当前地址
        verify(addressMapper, atLeast(2)).updateByExampleSelective(any(), any());
    }

    @Test
    void update_nonExistingAddress_shouldThrowException() {
        when(memberService.getCurrentMember()).thenReturn(currentMember);
        when(addressMapper.selectByExample(any(UmsMemberReceiveAddressExample.class)))
                .thenReturn(Collections.emptyList());

        UmsMemberReceiveAddress address = buildValidAddress();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> addressService.update(999L, address));
        assertEquals("地址不存在或无权操作", ex.getMessage());
    }

    @Test
    void update_nullId_shouldThrowException() {
        assertThrows(IllegalArgumentException.class,
                () -> addressService.update(null, buildValidAddress()));
    }

    @Test
    void update_withNullBody_shouldThrowException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> addressService.update(10L, null));
        assertEquals("地址信息不能为空", ex.getMessage());
    }

    @Test
    void update_withEmptyName_shouldThrowException() {
        when(memberService.getCurrentMember()).thenReturn(currentMember);
        UmsMemberReceiveAddress existing = buildValidAddress();
        existing.setId(10L);
        existing.setMemberId(1L);
        when(addressMapper.selectByExample(any(UmsMemberReceiveAddressExample.class)))
                .thenReturn(Collections.singletonList(existing));

        UmsMemberReceiveAddress address = buildValidAddress();
        address.setName("");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> addressService.update(10L, address));
        assertEquals("收货人姓名不能为空", ex.getMessage());
    }

    @Test
    void update_shouldNotAllowMemberIdOverride() {
        when(memberService.getCurrentMember()).thenReturn(currentMember);
        UmsMemberReceiveAddress existing = buildValidAddress();
        existing.setId(10L);
        existing.setMemberId(1L);
        when(addressMapper.selectByExample(any(UmsMemberReceiveAddressExample.class)))
                .thenReturn(Collections.singletonList(existing));
        when(addressMapper.updateByExampleSelective(any(), any())).thenReturn(1);

        // 攻击者尝试篡改 memberId 为其他会员
        UmsMemberReceiveAddress address = buildValidAddress();
        address.setMemberId(999L);

        addressService.update(10L, address);

        // 验证传入 mapper 的 address 对象 memberId 已被置空，不会被 updateByExampleSelective 写入
        ArgumentCaptor<UmsMemberReceiveAddress> captor = ArgumentCaptor.forClass(UmsMemberReceiveAddress.class);
        verify(addressMapper, atLeastOnce()).updateByExampleSelective(captor.capture(), any());
        assertNull(captor.getAllValues().get(captor.getAllValues().size() - 1).getMemberId(),
                "memberId 应被置空以防止篡改");
    }

    // ========== 删除地址场景 ==========

    @Test
    void delete_defaultAddress_shouldAutoSelectNewDefault() {
        when(memberService.getCurrentMember()).thenReturn(currentMember);

        // 被删除的地址是默认地址
        UmsMemberReceiveAddress defaultAddr = buildValidAddress();
        defaultAddr.setId(10L);
        defaultAddr.setMemberId(1L);
        defaultAddr.setDefaultStatus(1);

        // 剩余地址
        UmsMemberReceiveAddress remainingAddr = buildValidAddress();
        remainingAddr.setId(5L);
        remainingAddr.setMemberId(1L);
        remainingAddr.setDefaultStatus(0);

        // 第一次 selectByExample 是 getItem 调用，返回被删除的地址
        // 第二次 selectByExample 是 autoSelectNewDefault 调用，返回剩余地址
        when(addressMapper.selectByExample(any(UmsMemberReceiveAddressExample.class)))
                .thenReturn(Collections.singletonList(defaultAddr))
                .thenReturn(Collections.singletonList(remainingAddr));
        when(addressMapper.deleteByExample(any())).thenReturn(1);
        when(addressMapper.updateByExampleSelective(any(), any())).thenReturn(1);

        int result = addressService.delete(10L);

        assertEquals(1, result);
        // 验证设置了新默认地址
        ArgumentCaptor<UmsMemberReceiveAddress> captor = ArgumentCaptor.forClass(UmsMemberReceiveAddress.class);
        verify(addressMapper).updateByExampleSelective(captor.capture(), any());
        assertEquals(1, captor.getValue().getDefaultStatus());
    }

    @Test
    void delete_defaultAddress_noRemaining_shouldNotSetNewDefault() {
        when(memberService.getCurrentMember()).thenReturn(currentMember);

        UmsMemberReceiveAddress defaultAddr = buildValidAddress();
        defaultAddr.setId(10L);
        defaultAddr.setMemberId(1L);
        defaultAddr.setDefaultStatus(1);

        // getItem 返回默认地址，autoSelectNewDefault 查无剩余地址
        when(addressMapper.selectByExample(any(UmsMemberReceiveAddressExample.class)))
                .thenReturn(Collections.singletonList(defaultAddr))
                .thenReturn(Collections.emptyList());
        when(addressMapper.deleteByExample(any())).thenReturn(1);

        int result = addressService.delete(10L);

        assertEquals(1, result);
        // 不应调用 updateByExampleSelective（无剩余地址可设）
        verify(addressMapper, never()).updateByExampleSelective(any(), any());
    }

    @Test
    void delete_nonDefaultAddress_shouldNotAutoSelect() {
        when(memberService.getCurrentMember()).thenReturn(currentMember);

        UmsMemberReceiveAddress normalAddr = buildValidAddress();
        normalAddr.setId(10L);
        normalAddr.setMemberId(1L);
        normalAddr.setDefaultStatus(0);

        when(addressMapper.selectByExample(any(UmsMemberReceiveAddressExample.class)))
                .thenReturn(Collections.singletonList(normalAddr));
        when(addressMapper.deleteByExample(any())).thenReturn(1);

        int result = addressService.delete(10L);

        assertEquals(1, result);
        // 删除非默认地址，不应触发自动设置新默认
        verify(addressMapper, never()).updateByExampleSelective(any(), any());
    }

    @Test
    void delete_nonExistingAddress_shouldThrowException() {
        when(memberService.getCurrentMember()).thenReturn(currentMember);
        when(addressMapper.selectByExample(any(UmsMemberReceiveAddressExample.class)))
                .thenReturn(Collections.emptyList());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> addressService.delete(999L));
        assertEquals("地址不存在或无权操作", ex.getMessage());
    }

    @Test
    void delete_nullId_shouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> addressService.delete(null));
    }

    // ========== 列表排序场景 ==========

    @Test
    void list_shouldReturnDefaultFirst_thenByIdDesc() {
        when(memberService.getCurrentMember()).thenReturn(currentMember);

        UmsMemberReceiveAddress addr1 = buildValidAddress();
        addr1.setId(1L);
        addr1.setDefaultStatus(0);

        UmsMemberReceiveAddress addr2 = buildValidAddress();
        addr2.setId(2L);
        addr2.setDefaultStatus(1); // 默认地址

        UmsMemberReceiveAddress addr3 = buildValidAddress();
        addr3.setId(3L);
        addr3.setDefaultStatus(0);

        // mapper 返回无序列表
        List<UmsMemberReceiveAddress> dbList = new ArrayList<>();
        dbList.add(addr1);
        dbList.add(addr2);
        dbList.add(addr3);
        when(addressMapper.selectByExample(any())).thenReturn(dbList);

        List<UmsMemberReceiveAddress> result = addressService.list();

        assertEquals(3, result.size());
        // 默认地址排第一
        assertEquals(2L, result.get(0).getId());
        assertEquals(1, result.get(0).getDefaultStatus());
        // 其余按 id 倒序
        assertEquals(3L, result.get(1).getId());
        assertEquals(1L, result.get(2).getId());
    }

    @Test
    void list_emptyList_shouldReturnEmpty() {
        when(memberService.getCurrentMember()).thenReturn(currentMember);
        when(addressMapper.selectByExample(any())).thenReturn(Collections.emptyList());

        List<UmsMemberReceiveAddress> result = addressService.list();

        assertTrue(result.isEmpty());
    }

    // ========== getItem 越权场景 ==========

    @Test
    void getItem_otherMemberAddress_shouldReturnNull() {
        when(memberService.getCurrentMember()).thenReturn(currentMember);
        when(addressMapper.selectByExample(any(UmsMemberReceiveAddressExample.class)))
                .thenReturn(Collections.emptyList());

        UmsMemberReceiveAddress result = addressService.getItem(999L);

        assertNull(result);
    }

    @Test
    void getItem_nullId_shouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> addressService.getItem(null));
    }

    // ========== 辅助方法 ==========

    private UmsMemberReceiveAddress buildValidAddress() {
        UmsMemberReceiveAddress address = new UmsMemberReceiveAddress();
        address.setName("张三");
        address.setPhoneNumber("13800138000");
        address.setProvince("广东省");
        address.setCity("深圳市");
        address.setRegion("南山区");
        address.setDetailAddress("科技园南路1号");
        address.setPostCode("518000");
        address.setDefaultStatus(0);
        return address;
    }
}
