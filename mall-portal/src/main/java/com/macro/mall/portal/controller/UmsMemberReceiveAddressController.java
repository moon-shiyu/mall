package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.model.UmsMemberReceiveAddress;
import com.macro.mall.portal.service.UmsMemberReceiveAddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 会员收货地址管理Controller
 * Created by macro on 2018/8/28.
 */
@Controller
@Tag(name = "UmsMemberReceiveAddressController", description = "会员收货地址管理")
@RequestMapping("/member/address")
public class UmsMemberReceiveAddressController {
    @Autowired
    private UmsMemberReceiveAddressService memberReceiveAddressService;

    @Operation(summary = "添加收货地址")
    @RequestMapping(value = "/add", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult add(@RequestBody UmsMemberReceiveAddress address) {
        try {
            int count = memberReceiveAddressService.add(address);
            if (count > 0) {
                return CommonResult.success(count);
            }
            return CommonResult.failed();
        } catch (IllegalArgumentException e) {
            return CommonResult.validateFailed(e.getMessage());
        }
    }

    @Operation(summary = "删除收货地址")
    @RequestMapping(value = "/delete/{id}", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult delete(@PathVariable Long id) {
        if (id == null) {
            return CommonResult.validateFailed("地址id不能为空");
        }
        try {
            int count = memberReceiveAddressService.delete(id);
            if (count > 0) {
                return CommonResult.success(count);
            }
            return CommonResult.failed("地址不存在或无权操作");
        } catch (IllegalArgumentException e) {
            return CommonResult.validateFailed(e.getMessage());
        }
    }

    @Operation(summary = "修改收货地址")
    @RequestMapping(value = "/update/{id}", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult update(@PathVariable Long id, @RequestBody UmsMemberReceiveAddress address) {
        if (id == null) {
            return CommonResult.validateFailed("地址id不能为空");
        }
        try {
            int count = memberReceiveAddressService.update(id, address);
            if (count > 0) {
                return CommonResult.success(count);
            }
            return CommonResult.failed("地址不存在或无权操作");
        } catch (IllegalArgumentException e) {
            return CommonResult.validateFailed(e.getMessage());
        }
    }

    @Operation(summary = "获取所有收货地址")
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<List<UmsMemberReceiveAddress>> list() {
        List<UmsMemberReceiveAddress> addressList = memberReceiveAddressService.list();
        return CommonResult.success(addressList);
    }

    @Operation(summary = "获取收货地址详情")
    @RequestMapping(value = "/{id}", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<UmsMemberReceiveAddress> getItem(@PathVariable Long id) {
        if (id == null) {
            return CommonResult.validateFailed("地址id不能为空");
        }
        UmsMemberReceiveAddress address = memberReceiveAddressService.getItem(id);
        if (address == null) {
            return CommonResult.failed("地址不存在或无权访问");
        }
        return CommonResult.success(address);
    }
}
