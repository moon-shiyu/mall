package com.macro.mall;

import com.macro.mall.dao.UmsRoleDao;
import com.macro.mall.mapper.UmsResourceMapper;
import com.macro.mall.mapper.UmsRoleMapper;
import com.macro.mall.mapper.UmsRoleResourceRelationMapper;
import com.macro.mall.model.UmsResource;
import com.macro.mall.model.UmsRole;
import com.macro.mall.model.UmsRoleResourceRelation;
import com.macro.mall.model.UmsRoleResourceRelationExample;
import com.macro.mall.service.UmsRoleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;

/**
 * {@link UmsRoleServiceImpl#allocResource} 的事务一致性集成测试。
 *
 * <p>验证“删除旧关系 + 批量插入新关系”在同一事务中：当插入阶段失败时，先前的删除必须回滚，
 * 角色的旧关系保持不变（不出现部分写入）。该断言依赖真实的 Spring 事务代理与数据库，无法用纯 Mockito 覆盖。</p>
 *
 * <p>需要运行中的 MySQL 与 Redis。默认不参与 {@code mvn test}，通过环境变量启用：
 * <pre>RUN_DB_TESTS=true mvn -pl mall-admin -am test -DskipTests=false -Dtest=UmsRoleAllocIntegrationTest</pre>
 * （缓存失效的“调用”已在 {@code UmsRoleAllocServiceTest} 中通过 verify 覆盖。）</p>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_DB_TESTS", matches = "true")
class UmsRoleAllocIntegrationTest {

    @Autowired
    private UmsRoleService roleService;
    @Autowired
    private UmsRoleMapper roleMapper;
    @Autowired
    private UmsResourceMapper resourceMapper;
    @Autowired
    private UmsRoleResourceRelationMapper roleResourceRelationMapper;

    /**
     * 用 mock 替换批量插入 DAO，使插入阶段抛异常以触发回滚。
     * 校验(roleMapper/resourceMapper)与删除(roleResourceRelationMapper)仍走真实实现。
     */
    @MockitoBean
    private UmsRoleDao roleDao;

    private Long roleId;
    private Long resourceId1;
    private Long resourceId2;

    @BeforeEach
    void setUp() {
        long suffix = System.nanoTime();
        UmsRole role = new UmsRole();
        role.setName("__it_alloc_role_" + suffix);
        role.setDescription("integration test temp role");
        role.setAdminCount(0);
        role.setStatus(1);
        role.setSort(0);
        role.setCreateTime(new Date());
        roleMapper.insert(role);
        roleId = role.getId();

        resourceId1 = createResource("__it_res1_" + suffix);
        resourceId2 = createResource("__it_res2_" + suffix);

        // 植入一条初始(旧)关系：role -> resource1
        UmsRoleResourceRelation relation = new UmsRoleResourceRelation();
        relation.setRoleId(roleId);
        relation.setResourceId(resourceId1);
        roleResourceRelationMapper.insert(relation);
    }

    @AfterEach
    void tearDown() {
        UmsRoleResourceRelationExample relationExample = new UmsRoleResourceRelationExample();
        relationExample.createCriteria().andRoleIdEqualTo(roleId);
        roleResourceRelationMapper.deleteByExample(relationExample);
        resourceMapper.deleteByPrimaryKey(resourceId1);
        resourceMapper.deleteByPrimaryKey(resourceId2);
        roleMapper.deleteByPrimaryKey(roleId);
    }

    @Test
    void allocResource_insertFails_rollsBackDelete() {
        // 让批量插入抛运行时异常 -> 触发 @Transactional 回滚
        doThrow(new RuntimeException("simulated insert failure"))
                .when(roleDao).insertRoleResourceRelationList(anyList());

        // 传入合法的 resource2：校验通过 -> 删除旧关系(role->resource1) -> 插入抛错 -> 回滚
        assertThrows(RuntimeException.class,
                () -> roleService.allocResource(roleId, Collections.singletonList(resourceId2)));

        // 回滚后旧关系应原样保留：仍只有 role->resource1 这一条
        UmsRoleResourceRelationExample example = new UmsRoleResourceRelationExample();
        example.createCriteria().andRoleIdEqualTo(roleId);
        List<UmsRoleResourceRelation> remaining = roleResourceRelationMapper.selectByExample(example);
        assertEquals(1, remaining.size(), "删除应被回滚，旧关系数量保持为1");
        assertEquals(resourceId1, remaining.get(0).getResourceId(), "保留的应为原有的 resource1 关系");
    }

    private Long createResource(String name) {
        UmsResource resource = new UmsResource();
        resource.setName(name);
        resource.setUrl("/__it__/" + name);
        resource.setDescription("integration test temp resource");
        resource.setCreateTime(new Date());
        resourceMapper.insert(resource);
        return resource.getId();
    }
}
