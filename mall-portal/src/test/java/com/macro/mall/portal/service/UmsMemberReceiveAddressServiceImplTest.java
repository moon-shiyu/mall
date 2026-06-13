package com.macro.mall.portal.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.mapper.UmsMemberReceiveAddressMapper;
import com.macro.mall.model.UmsMember;
import com.macro.mall.model.UmsMemberReceiveAddress;
import com.macro.mall.model.UmsMemberReceiveAddressExample;
import com.macro.mall.portal.service.impl.UmsMemberReceiveAddressServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会员收货地址Service聚焦单元测试。
 * <p>使用Mockito对Mapper与会员Service打桩，不依赖数据库/Redis/安全上下文，验证：
 * 会员隔离与越权拦截、同一会员唯一默认地址、删除默认地址后自动改选、列表排序、必填校验。</p>
 */
@ExtendWith(MockitoExtension.class)
public class UmsMemberReceiveAddressServiceImplTest {

    private static final Long MEMBER_ID = 1L;

    @Mock
    private UmsMemberService memberService;
    @Mock
    private UmsMemberReceiveAddressMapper addressMapper;
    @InjectMocks
    private UmsMemberReceiveAddressServiceImpl service;

    @BeforeEach
    void mockCurrentMember() {
        UmsMember member = new UmsMember();
        member.setId(MEMBER_ID);
        when(memberService.getCurrentMember()).thenReturn(member);
    }

    private UmsMemberReceiveAddress validAddress() {
        UmsMemberReceiveAddress address = new UmsMemberReceiveAddress();
        address.setName("张三");
        address.setPhoneNumber("13800000000");
        address.setProvince("广东省");
        address.setCity("深圳市");
        address.setRegion("南山区");
        address.setDetailAddress("科技园1号");
        return address;
    }

    private UmsMemberReceiveAddress addressWith(Long id, Integer defaultStatus) {
        UmsMemberReceiveAddress address = validAddress();
        address.setId(id);
        address.setMemberId(MEMBER_ID);
        address.setDefaultStatus(defaultStatus);
        return address;
    }

    /** 从Example中取出 "id =" 条件对应的目标id，用于断言改选到了正确的地址。 */
    private Long extractIdCriterion(UmsMemberReceiveAddressExample example) {
        for (UmsMemberReceiveAddressExample.Criteria criteria : example.getOredCriteria()) {
            for (UmsMemberReceiveAddressExample.Criterion criterion : criteria.getAllCriteria()) {
                if ("id =".equals(criterion.getCondition())) {
                    return (Long) criterion.getValue();
                }
            }
        }
        return null;
    }

    // ---------------- 新增 ----------------

    @Test
    void add_setDefault_clearsExistingDefaultThenInserts() {
        UmsMemberReceiveAddress address = validAddress();
        address.setDefaultStatus(1);
        when(addressMapper.insert(any())).thenReturn(1);

        int count = service.add(address);

        assertEquals(1, count);
        // 强制绑定当前会员，忽略客户端可能伪造的id
        assertEquals(MEMBER_ID, address.getMemberId());
        assertNull(address.getId());
        // 设为默认时先清除旧默认地址
        ArgumentCaptor<UmsMemberReceiveAddress> recordCaptor = ArgumentCaptor.forClass(UmsMemberReceiveAddress.class);
        verify(addressMapper, times(1)).updateByExampleSelective(recordCaptor.capture(), any());
        assertEquals(Integer.valueOf(0), recordCaptor.getValue().getDefaultStatus());
        verify(addressMapper, times(1)).insert(any());
    }

    @Test
    void add_nonDefault_doesNotTouchOtherDefaults() {
        UmsMemberReceiveAddress address = validAddress();
        address.setDefaultStatus(0);
        when(addressMapper.insert(any())).thenReturn(1);

        int count = service.add(address);

        assertEquals(1, count);
        verify(addressMapper, never()).updateByExampleSelective(any(), any());
        verify(addressMapper, times(1)).insert(any());
    }

    @Test
    void add_blankRequiredField_throwsAndSkipsInsert() {
        UmsMemberReceiveAddress address = validAddress();
        address.setName("   ");

        assertThrows(ApiException.class, () -> service.add(address));

        verify(addressMapper, never()).insert(any());
        verify(addressMapper, never()).updateByExampleSelective(any(), any());
    }

    // ---------------- 修改 ----------------

    @Test
    void update_foreignId_throwsAndSkipsWrite() {
        // 当前会员名下查不到该id -> 越权或不存在
        when(addressMapper.selectByExample(any())).thenReturn(Collections.emptyList());

        assertThrows(ApiException.class, () -> service.update(99L, validAddress()));

        verify(addressMapper, never()).updateByExampleSelective(any(), any());
    }

    @Test
    void update_setDefault_clearsOthersThenUpdatesOwnRow() {
        when(addressMapper.selectByExample(any()))
                .thenReturn(Collections.singletonList(addressWith(10L, 0)));
        when(addressMapper.updateByExampleSelective(any(), any())).thenReturn(1);

        UmsMemberReceiveAddress address = validAddress();
        address.setDefaultStatus(1);
        int count = service.update(10L, address);

        assertEquals(1, count);
        assertEquals(MEMBER_ID, address.getMemberId());
        assertNull(address.getId());
        // 一次清除旧默认 + 一次更新本条
        verify(addressMapper, times(2)).updateByExampleSelective(any(), any());
    }

    // ---------------- 删除 ----------------

    @Test
    void delete_default_reassignsNewestRemainingAsDefault() {
        UmsMemberReceiveAddress deleted = addressWith(10L, 1);
        // 第一次查询用于归属校验，第二次查询返回剩余地址(按id倒序，最新在前)
        when(addressMapper.selectByExample(any()))
                .thenReturn(Collections.singletonList(deleted))
                .thenReturn(Arrays.asList(addressWith(12L, 0), addressWith(8L, 0)));
        when(addressMapper.deleteByExample(any())).thenReturn(1);

        int count = service.delete(10L);

        assertEquals(1, count);
        verify(addressMapper, times(1)).deleteByExample(any());
        // 改选默认地址时，按id倒序(创建顺序)查询剩余地址
        ArgumentCaptor<UmsMemberReceiveAddressExample> selectCaptor =
                ArgumentCaptor.forClass(UmsMemberReceiveAddressExample.class);
        verify(addressMapper, times(2)).selectByExample(selectCaptor.capture());
        assertEquals("id desc", selectCaptor.getAllValues().get(1).getOrderByClause());
        // 删除默认地址后，应把最新的剩余地址(id=12)设为默认
        ArgumentCaptor<UmsMemberReceiveAddress> recordCaptor = ArgumentCaptor.forClass(UmsMemberReceiveAddress.class);
        ArgumentCaptor<UmsMemberReceiveAddressExample> exampleCaptor =
                ArgumentCaptor.forClass(UmsMemberReceiveAddressExample.class);
        verify(addressMapper, times(1)).updateByExampleSelective(recordCaptor.capture(), exampleCaptor.capture());
        assertEquals(Integer.valueOf(1), recordCaptor.getValue().getDefaultStatus());
        assertEquals(Long.valueOf(12L), extractIdCriterion(exampleCaptor.getValue()));
    }

    @Test
    void delete_default_noRemaining_doesNotReassign() {
        when(addressMapper.selectByExample(any()))
                .thenReturn(Collections.singletonList(addressWith(10L, 1)))
                .thenReturn(Collections.emptyList());
        when(addressMapper.deleteByExample(any())).thenReturn(1);

        int count = service.delete(10L);

        assertEquals(1, count);
        verify(addressMapper, never()).updateByExampleSelective(any(), any());
    }

    @Test
    void delete_nonDefault_doesNotReassign() {
        when(addressMapper.selectByExample(any()))
                .thenReturn(Collections.singletonList(addressWith(10L, 0)));
        when(addressMapper.deleteByExample(any())).thenReturn(1);

        int count = service.delete(10L);

        assertEquals(1, count);
        // 删除的不是默认地址，无需改选，也不应再次查询剩余地址
        verify(addressMapper, times(1)).selectByExample(any());
        verify(addressMapper, never()).updateByExampleSelective(any(), any());
    }

    @Test
    void delete_foreignId_throwsAndSkipsDelete() {
        when(addressMapper.selectByExample(any())).thenReturn(Collections.emptyList());

        assertThrows(ApiException.class, () -> service.delete(99L));

        verify(addressMapper, never()).deleteByExample(any());
    }

    // ---------------- 列表 / 详情 ----------------

    @Test
    void list_ordersByDefaultThenIdDesc_scopedToMember() {
        when(addressMapper.selectByExample(any()))
                .thenReturn(Collections.singletonList(addressWith(3L, 1)));

        List<UmsMemberReceiveAddress> result = service.list();

        assertEquals(1, result.size());
        ArgumentCaptor<UmsMemberReceiveAddressExample> exampleCaptor =
                ArgumentCaptor.forClass(UmsMemberReceiveAddressExample.class);
        verify(addressMapper).selectByExample(exampleCaptor.capture());
        assertEquals("default_status desc, id desc", exampleCaptor.getValue().getOrderByClause());
    }

    @Test
    void getItem_ownAddress_returnsIt() {
        UmsMemberReceiveAddress owned = addressWith(3L, 0);
        when(addressMapper.selectByExample(any())).thenReturn(Collections.singletonList(owned));

        UmsMemberReceiveAddress result = service.getItem(3L);

        assertSame(owned, result);
    }

    @Test
    void getItem_foreignId_throws() {
        when(addressMapper.selectByExample(any())).thenReturn(Collections.emptyList());

        assertThrows(ApiException.class, () -> service.getItem(7L));
    }
}
