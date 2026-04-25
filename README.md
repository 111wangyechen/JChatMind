# JChatMind - AI智能体助手

JChatMind 是一个智能 AI Agent 系统，基于 Spring AI 框架构建，实现了自主决策、工具调用和知识库检索等核心能力。

系统采用 **Think-Execute 循环机制，能够理解复杂任务、规划执行步骤、调用外部工具，并基于 RAG 技术从知识库中检索相关信息，完成多步骤的复杂任务**。

## 核心特性

- **Think-Execute 循环**：自主决策、多轮规划、多轮工具调用
- **工具调用框架**：可扩展的工具系统，支持固定工具/可选工具分类管理
- **RAG 知识库**：PostgreSQL + pgvector 向量检索
- **多模型支持**：DeepSeek / 智谱 AI 可切换，注册表模式管理
- **SSE 实时推送**：执行状态实时可视化

## 项目演示

![image](https://file1.kamacoder.com/i/web/2026-01-09_16-30-36.jpg)

![image](https://file1.kamacoder.com/i/web/2026-01-09_16-31-19.jpg)

![image](https://file1.kamacoder.com/i/web/2026-01-09_16-31-49.jpg)

![image](https://file1.kamacoder.com/i/web/2026-01-09_16-32-08.jpg)

## 项目架构

![](https://file1.kamacoder.com/i/web/2026-01-08_11-19-14.jpg)

JChatMind 通过分层架构 + Agent 核心服务，把 AI 能力（模型、RAG、工具）抽象成可组合、可扩展的系统模块。

## Harness Engineering 智能体增强架构

项目实现了完整的 Harness Engineering 六层架构，增强 Agent 的可靠性、可观测性和自我纠错能力：

| 层级 | 名称 | 核心能力 |
|------|------|----------|
| L1 | 信息边界层 | Token 预算管控，上下文窗口自动压缩（40%/70%/90% 三级阈值） |
| L2 | 工具系统层 | 工具调用前校验与冲突检测，工具结果智能提炼 |
| L3 | 执行编排层 | LLM 驱动的多步任务规划，步骤追踪与进度管理 |
| L4 | 记忆与状态层 | 长对话 LLM 摘要压缩，结构化任务状态管理与回滚 |
| L5 | 评估与观测层 | 输出自验证与纠错循环，全链路执行指标采集 |
| L6 | 约束校验与恢复层 | 安全护栏（注入检测），指数退避重试，错误恢复与降级 |

**核心理念**：`Agent = Model + Harness`，通过 HarnessEngine 中央协调器在 Think-Execute 循环的每个关键节点提供增强，所有 Harness 调用采用 try-catch 降级设计，确保主流程不受影响。

```
agent/harness/
├── HarnessEngine.java          -- 中央协调器
├── HarnessConfig.java          -- 配置类
├── context/                    -- L1: 信息边界层
├── tool/                       -- L2: 工具系统层
├── orchestration/              -- L3: 执行编排层
├── memory/                     -- L4: 记忆与状态层
├── evaluation/                 -- L5: 评估与观测层
└── recovery/                   -- L6: 约束校验与恢复层
```

## 技术栈

### 后端
- Java 17+
- Spring Boot 3.x
- Spring AI
- MyBatis
- PostgreSQL + pgvector

### 前端
- React 18
- TypeScript
- Tailwind CSS
- Vite

## 快速开始

### 环境要求
- JDK 17+
- Node.js 18+
- PostgreSQL 14+ (需安装 pgvector 扩展)

### 后端启动

```bash
cd jchatmind
./mvnw spring-boot:run
```

### 前端启动

```bash
cd ui
npm install
npm run dev
```

## License

MIT License
