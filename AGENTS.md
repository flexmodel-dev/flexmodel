# AGENTS.md

Flexmodel — 面向下一代应用的统一数据访问层，基于 Java 25 + Quarkus 3 多模块 Maven 项目。

## 项目结构

| 模块 | 职责 |
|---|---|
| `flexmodel-engine/` | 核心引擎（codegen、core、graphql、quarkus 适配、maven 插件） |
| `flexmodel-server/` | 后端服务（Quarkus 3，REST/GraphQL/SSE/MCP，特性垂直分包） |
| `flexmodel-ui/` | 前端 UI（React + TypeScript + Ant Design v6 + Vite） |
| `flexmodel-website/` | 文档站（Docusaurus） |
| `flexmodel-functions-runtime/` | 云函数运行时（Deno + Hono.js，Worker 隔离执行） |

## Startup Workflow

Before writing code:

1. **Confirm working directory** with `pwd`
2. **Read this file** completely
3. **Read project docs** (`README.md`, `flexmodel-server/README.md`, `AGENTS.md` in submodules)
4. **Run `./init.sh`** to verify environment is healthy
5. **Read `feature_list.json`** to see current feature state
6. **Review recent commits** with `git log --oneline -5`

If baseline verification is failing, repair that first before adding new scope.

## Working Rules

- **One feature at a time**: Pick exactly one unfinished feature from `feature_list.json`
- **Verification required**: Don't claim done without running verification commands
- **Update artifacts**: Before ending session, update `progress.md` and `feature_list.json`
- **Stay in scope**: Don't modify files unrelated to the current feature
- **Leave clean state**: Next session must be able to run `./init.sh` immediately

## Required Artifacts

- `feature_list.json` — Feature state tracker (source of truth)
- `progress.md` — Session continuity log
- `init.sh` — Standard startup and verification path
- `session-handoff.md` — Optional, for larger sessions

## Definition of Done

A feature is done only when ALL of the following are true:

- [ ] Target behavior is implemented
- [ ] Required verification actually ran (tests / lint / type-check)
- [ ] Evidence recorded in `feature_list.json` or `progress.md`
- [ ] Repository remains restartable from standard startup path

## End of Session

Before ending a session:

1. Update `progress.md` with current state
2. Update `feature_list.json` with new feature status
3. Record any unresolved risks or blockers
4. Commit with descriptive message once work is in safe state
5. Leave repo clean enough for next session to run `./init.sh` immediately

## Verification Commands

```bash
# Full verification (recommended)
./init.sh
```

Required checks:
- `mvn clean compile -pl '!flexmodel-engine/flexmodel-maven-plugin'`（全模块编译验证）
- `mvn test -pl flexmodel-engine`（引擎模块测试）
- Optional: `mvn clean test -pl flexmodel-server -am`（服务模块测试，部分测试存在已知失败）

## 架构约束

- **特性垂直分包**：flexmodel-server 按业务特性（auth/flow/data/api/modeling/...）组织代码，每个特性包含 Resource、Service、Repository、DTO
- **Java 25**：所有模块编译目标为 Java 25（`maven.compiler.release=25`）
- **多模块构建**：默认构建 `flexmodel-engine` 和 `flexmodel-server`，使用 `-Pwith-ui` 可启用前端模块
- **Quarkus 3**：后端服务基于 Quarkus 3 框架，开发模式使用 `./mvnw quarkus:dev`

## Escalation

If you encounter:
- **Architecture decisions**: Consult project architecture docs if present, otherwise ask user
- **Unclear requirements**: Check product/requirements docs if present, otherwise ask user
- **Repeated test failures**: Update progress, flag for human review
- **Scope ambiguity**: Re-read `feature_list.json` for definition of done
