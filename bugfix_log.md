# Bug 修复记录

## 2026-09-13 ElasticTest 启动失败

- 现象：`item-service` 的 `ElasticTest` 中 4 个文档增删改查测试均未进入测试方法，启动时报 `IllegalStateException: Error processing condition on com.hmall.common.config.MyBatisConfig.mybatisPlusInterceptor`，根因堆栈包含 `NoClassDefFoundError: org/springframework/amqp/support/converter/MessageConverter`。
- 根因：`hm-common` 将 `spring-amqp`/`spring-rabbit` 声明为 `provided`，`item-service` 未提供运行时依赖；同时项目 HLRC 7.12.1 与 Docker 中 ES 9.1.4 不兼容。
- 修复方案：在 `item-service` 引入 `spring-boot-starter-amqp`；显式使用 HLRC 7.12.1，并将 Elasticsearch/Kibana 镜像对齐到 7.17.26；ES 集成测试关闭不相关且在 JDK 17 下会触发 CGLIB 反射异常的 Seata 自动配置。
- 验证结果：`mvn -pl item-service -DskipTests test-compile` 编译通过；加入 AMQP 依赖后 `ElasticTest#testConnection` Spring 容器启动通过。4 个 ES 测试在当前仍运行的 ES 9.1.4 容器上无法作为兼容性验证，需按 compose 重建 7.17.26 容器后再执行。
- 容器重建：已创建并切换到独立卷 `es-data-7`、`es-plugins-7`，ES/Kibana 容器现运行 7.17.0，`http://localhost:9200` 返回 7.17.0。旧 ES 9 卷 `es-data`、`es-plugins` 保留未删除。IK 插件下载因外网连接失败未安装，原依赖该插件的 `ik_max_word` 索引映射仍需后续补装插件。
- IK 插件补装：通过 `127.0.0.1:7897` 代理从 `get.infini.cloud` 下载并安装 `analysis-ik 7.17.0` 到 `es-plugins-7`，重启后 `_cat/plugins` 已确认加载。
- 严格测试：为 `ElasticTest` 固定了索引创建、文档写入、查询、更新、搜索、聚合、文档删除、索引删除顺序，并移除异常吞掉逻辑。补齐 IK 配置文件并升级 ES/Kibana/IK 到 7.17.18 后，`mvn -pl item-service -Dtest=ElasticTest test` 结果为 13 个测试全部通过。
