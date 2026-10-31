/**
 * @author cjbi
 * 零代码应用管理模块。
 *
 * <p>应用（{@code f_app}）与项目（{@code f_project}）一对一共享主键；图标、主色、导航与表单配置存储在
 * {@code f_app} 实体中。根包本身承载应用聚合根：{@code AppResource} / {@code AppService} /
 * {@code AppRepository} 及其 {@code dto}。</p>
 *
 * <p>按特性垂直分包：</p>
 * <ul>
 *   <li>{@code form} — 表单与字段定义（底层映射为 Flexmodel 实体）</li>
 *   <li>{@code record} — 记录读写、讨论与变更历史</li>
 *   <li>{@code navigation} — 应用导航与菜单</li>
 *   <li>{@code workbench} — 工作台组件布局与数据</li>
 *   <li>{@code approval} — 审批流程、实例与签核任务</li>
 *   <li>{@code dataauthorization} — 表单级数据授权与字段权限</li>
 *   <li>{@code transfer} — 表单数据的 Excel 导入导出</li>
 *   <li>{@code apppackage} — 应用包（zip）的整包导入导出与任务状态</li>
 *   <li>{@code template} — 内置应用模板目录，复用 {@code apppackage} 的导入管道</li>
 * </ul>
 *
 * <p>导入导出按场景区分：{@code apppackage} 面向整包迁移，{@code transfer} 面向单表单/多表单的
 * Excel 交换，{@code template} 只提供内置包目录。</p>
 *
 * <p>命名约定：{@code *Repository} 表示数据库实体访问，{@code *Store} 表示本地文件或临时任务状态。</p>
 */
package dev.flexmodel.apps;
