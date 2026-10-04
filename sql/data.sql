-- =====================================================================
-- 演示种子数据（全部为虚构人物与虚构论文，仅用于教学演示）
-- 所有演示账号密码均为：123456
-- =====================================================================
USE journalflow;

-- 演示数据按业务故事分段插入，稿件与 current_version_id 存在循环引用；
-- 仅在本次种子导入期间关闭检查，导入完成后恢复。
SET FOREIGN_KEY_CHECKS = 0;

-- ---------- 角色 ----------
INSERT INTO role (id, code, name) VALUES
(1,'AUTHOR','作者'),(2,'REVIEWER','审稿专家'),(3,'EDITOR','编辑'),(4,'ADMIN','管理员');

-- ---------- 用户（密码统一 123456，BCrypt） ----------
SET @pwd = '$2a$10$emQcg.pHundr53vojLCsiu/JLroYRDBkwShm7tY4edER7SdVY06fK';
INSERT INTO user (id, username, password_hash, real_name, email, affiliation, research_fields, status, created_at) VALUES
(1,'admin',   @pwd,'系统管理员','admin@jf-demo.edu','示范期刊社','期刊运营',1,'2026-01-05 09:00:00'),
(2,'editor1', @pwd,'陈志远','editor1@jf-demo.edu','示范期刊编辑部','计算机科学',1,'2026-01-06 09:00:00'),
(3,'editor2', @pwd,'李文清','editor2@jf-demo.edu','示范期刊编辑部','软件工程',1,'2026-01-06 09:10:00'),
(4,'reviewer1',@pwd,'王建国','reviewer1@jf-demo.edu','北方理工大学','自然语言处理、大模型',1,'2026-01-10 10:00:00'),
(5,'reviewer2',@pwd,'张雅琴','reviewer2@jf-demo.edu','华东科学研究院','知识图谱、信息检索',1,'2026-01-10 10:05:00'),
(6,'reviewer3',@pwd,'刘思远','reviewer3@jf-demo.edu','南方大学','分布式系统、软件工程',1,'2026-01-10 10:10:00'),
(7,'reviewer4',@pwd,'赵明轩','reviewer4@jf-demo.edu','江城大学','信息安全、隐私计算',1,'2026-01-10 10:15:00'),
(8,'author1', @pwd,'林晓萌','author1@jf-demo.edu','滨海大学','大模型应用',1,'2026-02-01 14:00:00'),
(9,'author2', @pwd,'周天宇','author2@jf-demo.edu','云山学院','机器学习',1,'2026-02-03 15:00:00'),
(10,'author3',@pwd,'吴海涛','author3@jf-demo.edu','临江工业大学','软件架构',1,'2026-02-05 16:00:00');

INSERT INTO user_role (user_id, role_id) VALUES
(1,4),(2,3),(3,3),(4,2),(5,2),(6,2),(7,2),(8,1),(9,1),(10,1),
(8,2);  -- author1 兼任审稿专家（演示一人多角色）

-- ---------- 学科分类 ----------
INSERT INTO category (id, name, parent_id, sort_no) VALUES
(1,'计算机科学',NULL,1),(2,'人工智能',1,1),(3,'软件工程',1,2),(4,'网络与信息安全',1,3),
(5,'数学',NULL,2),(6,'物理学',NULL,3),(7,'生物学',NULL,4),(8,'经济学',NULL,5);

-- ---------- 标签 ----------
INSERT INTO tag (id, name) VALUES
(1,'大模型'),(2,'深度学习'),(3,'自然语言处理'),(4,'知识图谱'),(5,'联邦学习'),(6,'软件工程'),(7,'网络安全'),(8,'推荐系统');

-- =====================================================================
-- 稿件（状态全覆盖，日期均不晚于 2026-09-30）
-- =====================================================================

-- ---------- M1 林晓萌：已出版（完整走完 投稿→审→退修→复审→录用→版权→出版） ----------
INSERT INTO manuscript (id, submitter_id, title, abstract, keywords, category_id, status, current_version_id, volume, issue, pages, doi, published_at, created_at, updated_at) VALUES
(1, 8, '基于大语言模型的学术论文元数据自动抽取方法研究',
 '针对学术期刊形式审查中元数据抽取依赖人工、效率低的问题，提出一种基于大语言模型的稿件元数据结构化抽取方法。方法采用提示模板约束输出并辅以JSON Schema校验与人工确认机制，在自建演示数据集上关键字段完整率达到96.8%。结果表明该方法可显著缩短形式审查周期，且全过程留痕可追溯。',
 '大模型;信息抽取;学术期刊;人在回路', 2, 'PUBLISHED', 2, '4', '2', '1-15', '10.99999/jf.2026.002', '2026-07-01 10:00:00', '2026-05-10 09:30:00', '2026-07-01 10:00:00');
INSERT INTO manuscript_version (id, manuscript_id, version_no, title, abstract, keywords, file_path, file_name, file_size, file_hash, change_note, created_by, created_at) VALUES
(1, 1, 1, '基于大语言模型的学术论文元数据自动抽取方法研究', '（v1 摘要）针对学术期刊形式审查中元数据抽取依赖人工的问题，提出基于大语言模型的抽取方法。', '大模型;信息抽取', '2026-05/seed-m01-v1.pdf', '元数据自动抽取_v1.pdf', 528384, 'a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90', NULL, 8, '2026-05-10 09:30:00'),
(2, 1, 2, '基于大语言模型的学术论文元数据自动抽取方法研究', '（v2 摘要）补充对比实验与错误分析，增加人工确认环节的消融实验。', '大模型;信息抽取;人在回路', '2026-06/seed-m01-v2.pdf', '元数据自动抽取_v2.pdf', 561152, 'b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90a1', '按审稿意见补充对比实验、增加消融分析、修正参考文献格式', 8, '2026-06-10 14:20:00');
INSERT INTO manuscript_author (manuscript_id, user_id, author_name, affiliation, author_order, is_corresponding) VALUES
(1, 8, '林晓萌', '滨海大学', 1, 1),(1, NULL, '苏文瑾', '滨海大学计算机学院', 2, 0);
INSERT INTO manuscript_tag (manuscript_id, tag_id) VALUES (1,1),(1,3);
INSERT INTO review_assignment (id, manuscript_id, version_id, reviewer_id, invited_by, invited_at, due_at, status) VALUES
(1, 1, 1, 4, 2, '2026-05-12 10:00:00', '2026-05-26 10:00:00', 'COMPLETED'),
(2, 1, 1, 5, 2, '2026-05-12 10:00:00', '2026-05-26 10:00:00', 'COMPLETED'),
(3, 1, 2, 4, 2, '2026-06-11 09:00:00', '2026-06-25 09:00:00', 'COMPLETED'),
(4, 1, 2, 5, 2, '2026-06-11 09:00:00', '2026-06-25 09:00:00', 'COMPLETED');
INSERT INTO review_report (assignment_id, manuscript_id, version_id, score_novelty, score_quality, score_clarity, comments_to_editor, comments_to_author, recommendation, submitted_at) VALUES
(1, 1, 1, 8, 7, 7, '选题贴合编辑部实际痛点，方法可行。', '1) 建议补充与规则基线的对比实验；2) 第3节抽取字段定义需给出明确表格；3) 参考文献格式不统一。', 'MINOR', '2026-05-20 16:00:00'),
(2, 1, 1, 7, 6, 6, '创新性一般但工程价值较高。', '1) 缺少对超长稿件的截断策略分析；2) 摘要中“显著缩短”缺乏数据支撑；3) 图2坐标轴缺少单位。', 'MAJOR', '2026-05-24 11:00:00'),
(3, 1, 2, 8, 8, 8, '修改到位，建议录用。', '修改说明充分，实验补充完整，可以录用。', 'ACCEPT', '2026-06-22 10:00:00'),
(4, 1, 2, 8, 7, 8, '达到发表水平。', '对比实验与消融分析已补充，问题基本解决，建议小修后录用（仅需润色语言）。', 'MINOR', '2026-06-24 15:30:00');
INSERT INTO editorial_decision (manuscript_id, version_id, stage, decision, reason, summary_report, llm_task_id, decided_by, decided_at) VALUES
(1, 1, 'FORMAT', 'PASS', '形式审查通过：元数据完整、格式符合投稿须知。', NULL, NULL, 2, '2026-05-12 11:00:00'),
(1, 1, 'REVIEW', 'REVISE', '两位审稿人提出实验对比与表达问题，建议退修。', '两位审稿人均认可选题价值（创新性7-8分）。主要问题：缺少规则基线对比、字段定义不清、参考文献格式不统一。一致建议小修/大修后复审。', NULL, 2, '2026-06-01 09:40:00'),
(1, 2, 'REVIEW', 'ACCEPT', '复审意见均为正面，同意录用。', '复审两位审稿人分别给出录用与小修建议，修改说明充分，实验完整，达成录用共识。', NULL, 2, '2026-06-28 15:00:00');
INSERT INTO revision (manuscript_id, based_version_id, new_version_id, response_to_reviewers, submitted_at) VALUES
(1, 1, 2, '尊敬的两位审稿专家：1) 已补充规则基线与BERT基线对比实验（表4）；2) 第3节新增表2明确字段定义；3) 统一参考文献为GB/T 7714格式；4) 摘要补充量化结果（96.8%）。', '2026-06-10 14:20:00');
INSERT INTO copyright_record (manuscript_id, version_id, license_type, agreement_text, agreement_hash, signed_by, signed_at) VALUES
(1, 2, 'CC-BY-4.0', '作者同意将论文以知识共享署名4.0（CC-BY-4.0）许可协议在示范期刊发表，授予编辑部汇编权及数字化传播权，作者保留署名权。本协议由系统留痕存证。', 'c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2', 8, '2026-06-30 09:00:00');

-- ---------- M12 周天宇：已出版（早期文章，浏览量高，用于热门推荐/评论） ----------
INSERT INTO manuscript (id, submitter_id, title, abstract, keywords, category_id, status, current_version_id, volume, issue, pages, doi, published_at, created_at, updated_at) VALUES
(12, 9, '基于深度学习的短文本情感分析方法',
 '短文本特征稀疏导致传统情感分析效果受限。本文提出融合预训练语言模型与注意力机制的短文本情感分析方法，在某电商评论演示数据集上准确率达到91.2%，较基线提升4.6个百分点。',
 '深度学习;情感分析;注意力机制', 2, 'PUBLISHED', 5, '3', '6', '44-52', '10.99999/jf.2025.116', '2026-04-20 09:00:00', '2026-02-15 10:00:00', '2026-04-20 09:00:00');
INSERT INTO manuscript_version (id, manuscript_id, version_no, title, abstract, keywords, file_path, file_name, file_size, file_hash, change_note, created_by, created_at) VALUES
(5, 12, 1, '基于深度学习的短文本情感分析方法', '（v1）提出融合预训练语言模型与注意力机制的短文本情感分析方法。', '深度学习;情感分析', '2026-02/seed-m12-v1.pdf', '短文本情感分析_v1.pdf', 498688, 'd4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3', NULL, 9, '2026-02-15 10:00:00');
INSERT INTO manuscript_author (manuscript_id, user_id, author_name, affiliation, author_order, is_corresponding) VALUES
(12, 9, '周天宇', '云山学院', 1, 1);
INSERT INTO manuscript_tag (manuscript_id, tag_id) VALUES (12,2);
INSERT INTO review_assignment (id, manuscript_id, version_id, reviewer_id, invited_by, invited_at, due_at, status) VALUES
(9, 12, 5, 6, 2, '2026-02-18 09:00:00', '2026-03-04 09:00:00', 'COMPLETED'),
(10, 12, 5, 7, 2, '2026-02-18 09:00:00', '2026-03-04 09:00:00', 'COMPLETED');
INSERT INTO review_report (assignment_id, manuscript_id, version_id, score_novelty, score_quality, score_clarity, comments_to_editor, comments_to_author, recommendation, submitted_at) VALUES
(9, 12, 5, 7, 8, 8, '方法常规但实验扎实。', '建议补充不同预训练模型的对比；语言表达清晰。', 'ACCEPT', '2026-02-28 14:00:00'),
(10, 12, 5, 7, 7, 8, '可以发表。', '建议在结论中讨论方法在跨领域场景的局限。', 'MINOR', '2026-03-02 10:30:00');
INSERT INTO editorial_decision (manuscript_id, version_id, stage, decision, reason, summary_report, llm_task_id, decided_by, decided_at) VALUES
(12, 5, 'FORMAT', 'PASS', '形式审查通过。', NULL, NULL, 2, '2026-02-17 09:30:00'),
(12, 5, 'REVIEW', 'ACCEPT', '审稿意见正面，同意录用。', '两位审稿人均认可实验充分性，一致建议录用或小修。', NULL, 2, '2026-03-10 14:00:00');
INSERT INTO copyright_record (manuscript_id, version_id, license_type, agreement_text, agreement_hash, signed_by, signed_at) VALUES
(12, 5, 'CC-BY-4.0', '作者同意将论文以CC-BY-4.0许可协议在示范期刊发表，授予编辑部汇编权及数字化传播权。', 'e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4', 9, '2026-03-15 10:00:00');

-- ---------- M2 周天宇：审稿中（v1，一份已接受一份待接受） ----------
INSERT INTO manuscript (id, submitter_id, title, abstract, keywords, category_id, status, current_version_id, created_at, updated_at) VALUES
(2, 9, '面向开放域问答的知识图谱增强检索方法',
 '针对开放域问答中检索结果与问题语义关联弱的问题，提出知识图谱增强的混合检索框架，在演示数据集上答案命中率提升8.3%。',
 '知识图谱;开放域问答;检索增强', 2, 'UNDER_REVIEW', 3, '2026-09-18 10:00:00', '2026-09-20 09:00:00');
INSERT INTO manuscript_version (id, manuscript_id, version_no, title, abstract, keywords, file_path, file_name, file_size, file_hash, change_note, created_by, created_at) VALUES
(3, 2, 1, '面向开放域问答的知识图谱增强检索方法', '（v1）提出知识图谱增强的混合检索框架。', '知识图谱;检索增强', '2026-09/seed-m02-v1.pdf', '知识图谱增强检索_v1.pdf', 512000, 'f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5', NULL, 9, '2026-09-18 10:00:00');
INSERT INTO manuscript_author (manuscript_id, user_id, author_name, affiliation, author_order, is_corresponding) VALUES (2, 9, '周天宇', '云山学院', 1, 1);
INSERT INTO manuscript_tag (manuscript_id, tag_id) VALUES (2,4);
INSERT INTO review_assignment (id, manuscript_id, version_id, reviewer_id, invited_by, invited_at, due_at, status) VALUES
(5, 2, 3, 5, 3, '2026-09-20 09:00:00', '2026-10-04 09:00:00', 'ACCEPTED'),
(6, 2, 3, 7, 3, '2026-09-20 09:00:00', '2026-10-04 09:00:00', 'PENDING');
INSERT INTO editorial_decision (manuscript_id, version_id, stage, decision, reason, decided_by, decided_at) VALUES
(2, 3, 'FORMAT', 'PASS', '形式审查通过。', 3, '2026-09-20 08:40:00');

-- ---------- M3 吴海涛：待编辑决定（两份意见已齐，LLM 汇总待确认） ----------
INSERT INTO manuscript (id, submitter_id, title, abstract, keywords, category_id, status, current_version_id, created_at, updated_at) VALUES
(3, 10, '微服务架构下的分布式事务一致性保障机制',
 '针对微服务间跨库事务一致性难题，对比分析Saga、TCC与本地消息表三类模式，提出基于编排式Saga与幂等补偿的改进方案，并通过混沌实验验证其在演示负载下的可用性。',
 '微服务;分布式事务;Saga', 3, 'PENDING_DECISION', 4, '2026-08-25 09:00:00', '2026-09-29 16:00:00');
INSERT INTO manuscript_version (id, manuscript_id, version_no, title, abstract, keywords, file_path, file_name, file_size, file_hash, change_note, created_by, created_at) VALUES
(4, 3, 1, '微服务架构下的分布式事务一致性保障机制', '（v1）对比Saga/TCC/本地消息表并提出改进方案。', '微服务;分布式事务', '2026-08/seed-m03-v1.pdf', '分布式事务保障_v1.pdf', 606208, '0718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f6', NULL, 10, '2026-08-25 09:00:00');
INSERT INTO manuscript_author (manuscript_id, user_id, author_name, affiliation, author_order, is_corresponding) VALUES
(3, 10, '吴海涛', '临江工业大学', 1, 1),(3, NULL, '郑一鸣', '临江工业大学软件学院', 2, 0);
INSERT INTO manuscript_tag (manuscript_id, tag_id) VALUES (3,6);
INSERT INTO review_assignment (id, manuscript_id, version_id, reviewer_id, invited_by, invited_at, due_at, status) VALUES
(7, 3, 4, 4, 2, '2026-08-28 09:00:00', '2026-09-11 09:00:00', 'COMPLETED'),
(8, 3, 4, 6, 2, '2026-08-28 09:00:00', '2026-09-11 09:00:00', 'COMPLETED');
INSERT INTO review_report (assignment_id, manuscript_id, version_id, score_novelty, score_quality, score_clarity, comments_to_editor, comments_to_author, recommendation, submitted_at) VALUES
(7, 3, 4, 6, 7, 7, '工程实践性强，理论创新一般。', '1) 补偿逻辑的测试覆盖不足；2) 第4章实验缺少失败注入比例说明；3) 图5与文字描述不一致。', 'MAJOR', '2026-09-08 15:00:00'),
(8, 3, 4, 7, 7, 8, '写作清晰，建议小修。', '1) 建议补充与Seata框架的对比；2) 部分缩写首次出现未给出全称。', 'MINOR', '2026-09-10 10:00:00');
INSERT INTO editorial_decision (manuscript_id, version_id, stage, decision, reason, decided_by, decided_at) VALUES
(3, 4, 'FORMAT', 'PASS', '形式审查通过。', 2, '2026-08-27 10:00:00');

-- ---------- M4 林晓萌：退修（作者可修回演示） ----------
INSERT INTO manuscript (id, submitter_id, title, abstract, keywords, category_id, status, current_version_id, created_at, updated_at) VALUES
(4, 8, '联邦学习场景下的梯度泄露攻击与防御综述',
 '系统梳理联邦学习中梯度泄露攻击的攻击面与防御手段，从隐私预算、通信开销、模型精度三个维度对DLG、GradInversion等攻击与差分隐私、安全聚合等防御进行对比分析，并给出开放问题。',
 '联邦学习;隐私安全;梯度泄露', 4, 'REVISION_REQUIRED', 6, '2026-09-05 09:00:00', '2026-09-22 11:00:00');
INSERT INTO manuscript_version (id, manuscript_id, version_no, title, abstract, keywords, file_path, file_name, file_size, file_hash, change_note, created_by, created_at) VALUES
(6, 4, 1, '联邦学习场景下的梯度泄露攻击与防御综述', '（v1）梳理联邦学习梯度泄露攻击与防御。', '联邦学习;隐私安全', '2026-09/seed-m04-v1.pdf', '联邦学习梯度泄露综述_v1.pdf', 716800, '18293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f607', NULL, 8, '2026-09-05 09:00:00');
INSERT INTO manuscript_author (manuscript_id, user_id, author_name, affiliation, author_order, is_corresponding) VALUES (4, 8, '林晓萌', '滨海大学', 1, 1);
INSERT INTO manuscript_tag (manuscript_id, tag_id) VALUES (4,5),(4,7);
INSERT INTO review_assignment (id, manuscript_id, version_id, reviewer_id, invited_by, invited_at, due_at, status) VALUES
(11, 4, 6, 5, 3, '2026-09-08 09:00:00', '2026-09-22 09:00:00', 'COMPLETED'),
(12, 4, 6, 6, 3, '2026-09-08 09:00:00', '2026-09-22 09:00:00', 'COMPLETED');
INSERT INTO review_report (assignment_id, manuscript_id, version_id, score_novelty, score_quality, score_clarity, comments_to_editor, comments_to_author, recommendation, submitted_at) VALUES
(11, 4, 6, 7, 6, 6, '综述覆盖较全，但缺乏批判性分析。', '1) 仅罗列工作，缺少防御方法适用场景的对比表；2) 攻击小节未区分白盒/黑盒假设；3) 建议补充2025年以来的最新工作。', 'MAJOR', '2026-09-18 16:00:00'),
(12, 4, 6, 7, 7, 7, '整体可读，建议小修。', '1) 第2节术语“梯度泄露”与“梯度反演”混用，需统一；2) 部分图表清晰度不足。', 'MINOR', '2026-09-20 09:30:00');
INSERT INTO editorial_decision (manuscript_id, version_id, stage, decision, reason, summary_report, llm_task_id, decided_by, decided_at) VALUES
(4, 6, 'FORMAT', 'PASS', '形式审查通过。', NULL, NULL, 3, '2026-09-07 10:00:00'),
(4, 6, 'REVIEW', 'REVISE', '一位审稿人认为综述缺乏批判性分析，需补充防御方法对比与最新工作后复审。', '两份意见共同点：综述覆盖全但分析深度不足。主要修改点：补充防御方法对比表、区分攻击威胁模型、统一术语、补充最新文献。建议大修后复审。', NULL, 3, '2026-09-22 11:00:00');

-- ---------- M5 周天宇：草稿 ----------
INSERT INTO manuscript (id, submitter_id, title, abstract, keywords, category_id, status, created_at, updated_at) VALUES
(5, 9, '基于注意力机制的中文医疗命名实体识别', '（草稿摘要待完善）探索领域自适应的中文医疗NER方法。', '命名实体识别;医疗NLP', 2, 'DRAFT', '2026-09-26 20:00:00', '2026-09-26 20:00:00');
INSERT INTO manuscript_author (manuscript_id, user_id, author_name, affiliation, author_order, is_corresponding) VALUES (5, 9, '周天宇', '云山学院', 1, 1);

-- ---------- M6 吴海涛：已提交，待形式审查（编辑+AI 抽取演示入口） ----------
INSERT INTO manuscript (id, submitter_id, title, abstract, keywords, category_id, status, current_version_id, created_at, updated_at) VALUES
(6, 10, '软件供应链安全风险评估方法研究',
 '软件供应链攻击事件频发，本文提出基于依赖图分析与威胁情报融合的供应链风险评估方法，对npm与Maven演示样本库共1200个包进行评估，识别出37个高风险传递依赖。',
 '软件供应链;风险评估;依赖分析', 4, 'SUBMITTED', 7, '2026-09-28 15:00:00', '2026-09-28 15:00:00');
INSERT INTO manuscript_version (id, manuscript_id, version_no, title, abstract, keywords, file_path, file_name, file_size, file_hash, change_note, created_by, created_at) VALUES
(7, 6, 1, '软件供应链安全风险评估方法研究', '（v1）提出基于依赖图分析与威胁情报融合的评估方法。', '软件供应链;风险评估', '2026-09/seed-m06-v1.pdf', '软件供应链风险评估_v1.pdf', 655360, '8293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f607', NULL, 10, '2026-09-28 15:00:00');
INSERT INTO manuscript_author (manuscript_id, user_id, author_name, affiliation, author_order, is_corresponding) VALUES (6, 10, '吴海涛', '临江工业大学', 1, 1);
INSERT INTO manuscript_tag (manuscript_id, tag_id) VALUES (6,7);

-- ---------- M7 周天宇：形式审查中（AI 抽取结果待编辑确认） ----------
INSERT INTO manuscript (id, submitter_id, title, abstract, keywords, category_id, status, current_version_id, created_at, updated_at) VALUES
(7, 9, '大模型辅助的代码缺陷检测研究进展',
 '综述大语言模型在代码缺陷检测中的应用，覆盖缺陷预测、定位与修复建议三类任务，总结提示策略与微调路线的效果差异，并讨论评测基准的不足。',
 '大模型;代码缺陷检测;综述', 3, 'IN_FORMAT_REVIEW', 8, '2026-09-27 09:00:00', '2026-09-27 11:00:00');
INSERT INTO manuscript_version (id, manuscript_id, version_no, title, abstract, keywords, file_path, file_name, file_size, file_hash, change_note, created_by, created_at) VALUES
(8, 7, 1, '大模型辅助的代码缺陷检测研究进展', '（v1）综述大模型在代码缺陷检测中的应用。', '大模型;代码缺陷检测', '2026-09/seed-m07-v1.pdf', '代码缺陷检测综述_v1.pdf', 688128, '93a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718', NULL, 9, '2026-09-27 09:00:00');
INSERT INTO manuscript_author (manuscript_id, user_id, author_name, affiliation, author_order, is_corresponding) VALUES (7, 9, '周天宇', '云山学院', 1, 1);
INSERT INTO manuscript_tag (manuscript_id, tag_id) VALUES (7,1),(7,6);

-- ---------- M8 林晓萌：已退稿（作者已申诉，待管理员终审） ----------
INSERT INTO manuscript (id, submitter_id, title, abstract, keywords, category_id, status, current_version_id, created_at, updated_at) VALUES
(8, 8, '图神经网络在推荐系统中的应用综述',
 '综述图神经网络在协同过滤、序列推荐与多行为推荐中的应用，归纳代表性模型的统一框架，并讨论可扩展性与过平滑问题。',
 '图神经网络;推荐系统', 2, 'REJECTED', 9, '2026-07-15 09:00:00', '2026-08-05 14:00:00');
INSERT INTO manuscript_version (id, manuscript_id, version_no, title, abstract, keywords, file_path, file_name, file_size, file_hash, change_note, created_by, created_at) VALUES
(9, 8, 1, '图神经网络在推荐系统中的应用综述', '（v1）综述GNN在推荐系统中的应用。', '图神经网络;推荐系统', '2026-07/seed-m08-v1.pdf', 'GNN推荐综述_v1.pdf', 593920, 'a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f6071829', NULL, 8, '2026-07-15 09:00:00');
INSERT INTO manuscript_author (manuscript_id, user_id, author_name, affiliation, author_order, is_corresponding) VALUES (8, 8, '林晓萌', '滨海大学', 1, 1);
INSERT INTO manuscript_tag (manuscript_id, tag_id) VALUES (8,8);
INSERT INTO review_assignment (id, manuscript_id, version_id, reviewer_id, invited_by, invited_at, due_at, status) VALUES
(13, 8, 9, 4, 2, '2026-07-18 09:00:00', '2026-08-01 09:00:00', 'COMPLETED'),
(14, 8, 9, 7, 2, '2026-07-18 09:00:00', '2026-08-01 09:00:00', 'COMPLETED');
INSERT INTO review_report (assignment_id, manuscript_id, version_id, score_novelty, score_quality, score_clarity, comments_to_editor, comments_to_author, recommendation, submitted_at) VALUES
(13, 8, 9, 5, 5, 6, '同类综述已有多篇，增量有限，建议退稿后另投。', '1) 与2024年SIGIR教程内容重合度高；2) 未讨论工业界大规模部署经验；3) 统一框架图与后文模型对应不清。', 'REJECT', '2026-07-30 10:00:00'),
(14, 8, 9, 6, 6, 7, '覆盖面可以但深度不足，倾向退稿。', '1) 缺少对过平滑问题的定量分析；2) 部分引用为预印本，建议替换正式版本。', 'MAJOR', '2026-08-01 16:00:00');
INSERT INTO editorial_decision (manuscript_id, version_id, stage, decision, reason, summary_report, llm_task_id, decided_by, decided_at) VALUES
(8, 9, 'FORMAT', 'PASS', '形式审查通过。', NULL, NULL, 2, '2026-07-17 09:00:00'),
(8, 9, 'REVIEW', 'REJECT', '一位审稿人认为与已有教程/综述重合度高、增量有限，另一位认为深度不足，综合建议退稿。', '两位审稿人一致认为综述增量有限（评分5-6）。主要不足：与已有工作重合、缺少定量分析、引用不规范。建议退稿。', NULL, 2, '2026-08-05 14:00:00');

-- ---------- M9 吴海涛：已录用（待签版权→出版演示） ----------
INSERT INTO manuscript (id, submitter_id, title, abstract, keywords, category_id, status, current_version_id, created_at, updated_at) VALUES
(9, 10, '多模态大模型幻觉问题缓解方法',
 '针对多模态大模型在图文问答中的幻觉问题，提出基于检索增强与视觉一致性校验的两阶段缓解方法，在演示评测集上将幻觉率从18.7%降至9.4%。',
 '多模态大模型;幻觉;检索增强', 2, 'ACCEPTED', 10, '2026-08-30 09:00:00', '2026-09-20 10:00:00');
INSERT INTO manuscript_version (id, manuscript_id, version_no, title, abstract, keywords, file_path, file_name, file_size, file_hash, change_note, created_by, created_at) VALUES
(10, 9, 1, '多模态大模型幻觉问题缓解方法', '（v1）提出检索增强与视觉一致性校验的幻觉缓解方法。', '多模态大模型;检索增强', '2026-08/seed-m09-v1.pdf', '多模态幻觉缓解_v1.pdf', 720896, 'b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a', NULL, 10, '2026-08-30 09:00:00');
INSERT INTO manuscript_author (manuscript_id, user_id, author_name, affiliation, author_order, is_corresponding) VALUES (9, 10, '吴海涛', '临江工业大学', 1, 1);
INSERT INTO manuscript_tag (manuscript_id, tag_id) VALUES (9,1);
INSERT INTO review_assignment (id, manuscript_id, version_id, reviewer_id, invited_by, invited_at, due_at, status) VALUES
(15, 9, 10, 4, 2, '2026-09-02 09:00:00', '2026-09-16 09:00:00', 'COMPLETED'),
(16, 9, 10, 6, 2, '2026-09-02 09:00:00', '2026-09-16 09:00:00', 'COMPLETED');
INSERT INTO review_report (assignment_id, manuscript_id, version_id, score_novelty, score_quality, score_clarity, comments_to_editor, comments_to_author, recommendation, submitted_at) VALUES
(15, 9, 10, 8, 8, 8, '问题重要，方法有效，建议录用。', '建议补充与POPE评测协议的对照说明。', 'ACCEPT', '2026-09-12 10:00:00'),
(16, 9, 10, 8, 7, 8, '达到发表水平。', '第4节消融实验建议补充置信区间。', 'MINOR', '2026-09-15 14:00:00');
INSERT INTO editorial_decision (manuscript_id, version_id, stage, decision, reason, summary_report, llm_task_id, decided_by, decided_at) VALUES
(9, 10, 'FORMAT', 'PASS', '形式审查通过。', NULL, NULL, 2, '2026-09-01 10:00:00'),
(9, 10, 'REVIEW', 'ACCEPT', '审稿意见均为正面，同意录用。', '两位审稿人均认可方法有效性与实验完整性（评分7-8），一致建议录用或小修。', NULL, 2, '2026-09-20 10:00:00');

-- ---------- M10 周天宇：出版中（待归档发布演示） ----------
INSERT INTO manuscript (id, submitter_id, title, abstract, keywords, category_id, status, current_version_id, volume, issue, pages, doi, created_at, updated_at) VALUES
(10, 9, '面向低资源场景的提示学习方法研究',
 '研究低资源场景下的提示学习策略，比较人工模板、自动模板与指令微调的效果差异，在三个演示任务上平均提升5.1个百分点。',
 '提示学习;低资源;微调', 2, 'IN_PRODUCTION', 11, '4', '3', '16-27', '10.99999/jf.2026.003', '2026-06-20 09:00:00', '2026-09-25 10:00:00');
INSERT INTO manuscript_version (id, manuscript_id, version_no, title, abstract, keywords, file_path, file_name, file_size, file_hash, change_note, created_by, created_at) VALUES
(11, 10, 1, '面向低资源场景的提示学习方法研究', '（v1）比较低资源场景下的提示学习策略。', '提示学习;低资源', '2026-06/seed-m10-v1.pdf', '低资源提示学习_v1.pdf', 630784, 'c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b', NULL, 9, '2026-06-20 09:00:00');
INSERT INTO manuscript_author (manuscript_id, user_id, author_name, affiliation, author_order, is_corresponding) VALUES (10, 9, '周天宇', '云山学院', 1, 1);
INSERT INTO review_assignment (id, manuscript_id, version_id, reviewer_id, invited_by, invited_at, due_at, status) VALUES
(17, 10, 11, 5, 2, '2026-06-23 09:00:00', '2026-07-07 09:00:00', 'COMPLETED'),
(18, 10, 11, 7, 2, '2026-06-23 09:00:00', '2026-07-07 09:00:00', 'COMPLETED');
INSERT INTO review_report (assignment_id, manuscript_id, version_id, score_novelty, score_quality, score_clarity, comments_to_editor, comments_to_author, recommendation, submitted_at) VALUES
(17, 10, 11, 8, 7, 8, '实验充分，建议录用。', '建议讨论模板敏感性分析。', 'ACCEPT', '2026-07-03 11:00:00'),
(18, 10, 11, 7, 8, 8, '可以录用。', '建议补充跨任务泛化性讨论。', 'MINOR', '2026-07-05 15:00:00');
INSERT INTO editorial_decision (manuscript_id, version_id, stage, decision, reason, summary_report, llm_task_id, decided_by, decided_at) VALUES
(10, 11, 'FORMAT', 'PASS', '形式审查通过。', NULL, NULL, 2, '2026-06-22 09:00:00'),
(10, 11, 'REVIEW', 'ACCEPT', '审稿意见正面，同意录用。', '两位审稿人均认可实验充分性，一致建议录用。', NULL, 2, '2026-07-12 10:00:00');

-- ---------- M11 林晓萌：已撤稿 ----------
INSERT INTO manuscript (id, submitter_id, title, abstract, keywords, category_id, status, current_version_id, created_at, updated_at) VALUES
(11, 8, '基于区块链的学术成果存证平台设计', '设计一个基于联盟链的学术成果存证与引用追踪平台原型。', '区块链;存证', 3, 'WITHDRAWN', 12, '2026-08-12 09:00:00', '2026-09-08 10:00:00');
INSERT INTO manuscript_version (id, manuscript_id, version_no, title, abstract, keywords, file_path, file_name, file_size, file_hash, change_note, created_by, created_at) VALUES
(12, 11, 1, '基于区块链的学术成果存证平台设计', '（v1）基于联盟链的学术成果存证平台原型。', '区块链;存证', '2026-08/seed-m11-v1.pdf', '区块链存证平台_v1.pdf', 458752, 'd7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c', NULL, 8, '2026-08-12 09:00:00');
INSERT INTO manuscript_author (manuscript_id, user_id, author_name, affiliation, author_order, is_corresponding) VALUES (11, 8, '林晓萌', '滨海大学', 1, 1);
INSERT INTO editorial_decision (manuscript_id, version_id, stage, decision, reason, decided_by, decided_at) VALUES
(11, 12, 'FORMAT', 'PASS', '形式审查通过。', 2, '2026-08-14 09:00:00');

-- =====================================================================
-- LLM 任务留痕（抽取/汇总/解释三场景 + 失败样例）
-- =====================================================================
INSERT INTO llm_task (id, scene, manuscript_id, version_id, template_id, input_digest, provider, model, raw_output, parsed_output, confirmed_output, status, confirmed_by, confirmed_at, latency_ms, error_msg, created_by, created_at) VALUES
(1, 'EXTRACT', 1, 1, 'TPL-EXTRACT-01', '标题+摘要+正文前2000字+前10条参考文献（共约2360字）', 'mock', 'mock-glm-4-flash',
 '{"title":"基于大语言模型的学术论文元数据自动抽取方法研究","abstract":"针对学术期刊形式审查中元数据抽取依赖人工的问题，提出一种基于大语言模型的元数据结构化抽取方法，采用提示模板约束输出并辅以JSON Schema校验与人工确认机制。","keywords":["大模型","信息抽取","学术期刊","人在回路"],"primary_category":"人工智能","language":"zh","word_count_est":7800,"reference_count_est":28,"has_ethics_statement":false,"possible_issues":["参考文献格式不统一"]}',
 '{"title":"基于大语言模型的学术论文元数据自动抽取方法研究","abstract":"针对学术期刊形式审查中元数据抽取依赖人工的问题，提出一种基于大语言模型的元数据结构化抽取方法，采用提示模板约束输出并辅以JSON Schema校验与人工确认机制。","keywords":["大模型","信息抽取","学术期刊","人在回路"],"primary_category":"人工智能","language":"zh","word_count_est":7800,"reference_count_est":28,"has_ethics_statement":false,"possible_issues":["参考文献格式不统一"]}',
 '{"title":"基于大语言模型的学术论文元数据自动抽取方法研究","abstract":"（编辑确认，与抽取一致）","keywords":["大模型","信息抽取","学术期刊","人在回路"],"primary_category":"人工智能","language":"zh","word_count_est":7800,"reference_count_est":28,"has_ethics_statement":false,"possible_issues":["参考文献格式不统一"]}',
 'CONFIRMED', 2, '2026-05-12 10:50:00', 812, NULL, 2, '2026-05-12 10:48:30'),
(2, 'SUMMARY', 3, 4, 'TPL-SUMMARY-01', '2份审稿意见（评分/建议/意见原文，共约480字）', 'mock', 'mock-glm-4-flash',
 '{"summary":"两位审稿人认可稿件工程价值，均指出实验与表达方面问题。","major_issues":["补偿逻辑测试覆盖不足","实验缺少失败注入比例说明","缺少与Seata框架的对比"],"minor_issues":["缩写首次出现未给出全称","图5与文字描述不一致"],"consensus":"major","dissent":"无实质分歧：一位建议大修、一位建议小修。","suggested_decision":"REVISE"}',
 '{"summary":"两位审稿人认可稿件工程价值，均指出实验与表达方面问题。","major_issues":["补偿逻辑测试覆盖不足","实验缺少失败注入比例说明","缺少与Seata框架的对比"],"minor_issues":["缩写首次出现未给出全称","图5与文字描述不一致"],"consensus":"major","dissent":"无实质分歧：一位建议大修、一位建议小修。","suggested_decision":"REVISE"}',
 NULL, 'SUCCESS', NULL, NULL, 640, NULL, 2, '2026-09-29 16:00:00'),
(3, 'EXTRACT', 7, 8, 'TPL-EXTRACT-01', '标题+摘要+正文前2000字+前10条参考文献（共约2210字）', 'mock', 'mock-glm-4-flash',
 '{"title":"大模型辅助的代码缺陷检测研究进展","abstract":"综述大语言模型在代码缺陷检测中的应用，覆盖缺陷预测、定位与修复建议三类任务。","keywords":["大模型","代码缺陷检测","综述"],"primary_category":"软件工程","language":"zh","word_count_est":9200,"reference_count_est":41,"has_ethics_statement":false,"possible_issues":["引用了3篇未正式发表的预印本"]}',
 '{"title":"大模型辅助的代码缺陷检测研究进展","abstract":"综述大语言模型在代码缺陷检测中的应用，覆盖缺陷预测、定位与修复建议三类任务。","keywords":["大模型","代码缺陷检测","综述"],"primary_category":"软件工程","language":"zh","word_count_est":9200,"reference_count_est":41,"has_ethics_statement":false,"possible_issues":["引用了3篇未正式发表的预印本"]}',
 NULL, 'SUCCESS', NULL, NULL, 755, NULL, 2, '2026-09-27 10:50:00'),
(4, 'EXPLAIN', 4, 6, 'TPL-EXPLAIN-01', '2份审稿意见原文（共约310字）+稿件版本v1', 'mock', 'mock-glm-4-flash',
 '{"explanations":[{"issue":"仅罗列工作，缺少防御方法适用场景的对比表","plain":"审稿人认为综述只是把已有研究一条条列出来，没有把它们放在一起比较，看不出哪种防御方法适合什么场景。","suggestion":"增加一张对比表：行是防御方法（差分隐私、安全聚合等），列是隐私预算、通信开销、精度损失、适用场景。"},{"issue":"攻击小节未区分白盒/黑盒假设","plain":"审稿人认为介绍攻击方法时没有说明攻击者能拿到多少模型信息，不同假设下攻击难度和效果差别很大。","suggestion":"在2.x节为每种攻击标注威胁模型（白盒/黑盒/灰盒），并补充一段威胁模型定义。"},{"issue":"建议补充2025年以来的最新工作","plain":"审稿人认为参考文献偏旧，近一年该方向进展很快。","suggestion":"检索并补充2025-2026年顶会相关工作3-5篇，并在正文对应小节引用。"}],"action_items":["补充防御方法对比表","标注攻击威胁模型","补充2025年以来文献"]}',
 '{"explanations":[{"issue":"仅罗列工作，缺少防御方法适用场景的对比表","plain":"审稿人认为综述只是把已有研究一条条列出来，没有把它们放在一起比较，看不出哪种防御方法适合什么场景。","suggestion":"增加一张对比表：行是防御方法（差分隐私、安全聚合等），列是隐私预算、通信开销、精度损失、适用场景。"},{"issue":"攻击小节未区分白盒/黑盒假设","plain":"审稿人认为介绍攻击方法时没有说明攻击者能拿到多少模型信息，不同假设下攻击难度和效果差别很大。","suggestion":"在2.x节为每种攻击标注威胁模型（白盒/黑盒/灰盒），并补充一段威胁模型定义。"},{"issue":"建议补充2025年以来的最新工作","plain":"审稿人认为参考文献偏旧，近一年该方向进展很快。","suggestion":"检索并补充2025-2026年顶会相关工作3-5篇，并在正文对应小节引用。"}],"action_items":["补充防御方法对比表","标注攻击威胁模型","补充2025年以来文献"]}',
 NULL, 'SUCCESS', NULL, NULL, 703, NULL, 8, '2026-09-23 09:00:00'),
(5, 'EXTRACT', 2, 3, 'TPL-EXTRACT-01', '标题+摘要+正文前2000字（约1980字）', 'openai-compat', 'glm-4-flash',
 '"这是一个针对开放域问答的研究，主要贡献在于..."',
 NULL, NULL, 'FAILED', NULL, NULL, 30412, '输出非JSON（重试1次后仍失败），已转人工处理', 3, '2026-09-20 08:30:00');

-- =====================================================================
-- 互动（评论/收藏/订阅/关注）
-- =====================================================================
INSERT INTO comment (id, target_type, target_id, user_id, content, parent_id, status, created_at) VALUES
(1, 'MANUSCRIPT', 1, 9, '方法很实用，请问提示模板会开源吗？', NULL, 1, '2026-07-02 10:00:00'),
(2, 'MANUSCRIPT', 1, 8, '感谢关注，模板与评测脚本将在录用后随数据集一起开源。', 1, 1, '2026-07-02 14:00:00'),
(3, 'MANUSCRIPT', 1, 4, '人工确认环节的设计很扎实，建议后续补充跨期刊泛化实验。', NULL, 1, '2026-07-05 09:00:00'),
(4, 'MANUSCRIPT', 1, 10, '抽取的字段能支持自定义扩展吗？编辑部字段和我们的不完全一样。', NULL, 1, '2026-07-08 16:00:00'),
(5, 'MANUSCRIPT', 1, 3, '这条评论包含不当言论（演示举报样例）。', NULL, 1, '2026-07-09 12:00:00'),
(6, 'MANUSCRIPT', 12, 8, '注意力池化的消融实验很清楚，学习了。', NULL, 1, '2026-04-22 09:00:00'),
(7, 'MANUSCRIPT', 12, 6, '建议补充跨领域迁移的讨论，已邮件联系作者。', NULL, 1, '2026-04-25 10:00:00');
INSERT INTO favorite (user_id, manuscript_id, created_at) VALUES
(9, 1, '2026-07-02 10:05:00'),(10, 1, '2026-07-03 09:00:00'),(2, 1, '2026-07-04 11:00:00'),(8, 12, '2026-04-22 09:10:00'),(10, 12, '2026-05-01 14:00:00');
INSERT INTO subscription (id, user_id, category_id, keyword, created_at) VALUES
(1, 8, 2, NULL, '2026-03-01 09:00:00'),(2, 8, NULL, '大模型', '2026-03-01 09:01:00'),
(3, 9, 3, NULL, '2026-03-02 09:00:00'),(4, 4, 2, NULL, '2026-03-05 09:00:00'),
(5, 10, 2, NULL, '2026-03-06 09:00:00');
INSERT INTO follow (follower_id, followee_id, created_at) VALUES
(9, 8, '2026-03-10 09:00:00'),(10, 8, '2026-03-12 09:00:00'),(8, 4, '2026-03-15 09:00:00'),(9, 4, '2026-04-01 09:00:00');

-- =====================================================================
-- 举报 / 申诉 / 版权
-- =====================================================================
INSERT INTO report (id, reporter_id, target_type, target_id, reason_type, description, status, handled_by, handled_at, resolution, created_at) VALUES
(1, 9, 'COMMENT', 5, '不当言论', '该评论对其他读者使用侮辱性语言，违反社区规范。', 'PENDING', NULL, NULL, NULL, '2026-07-10 09:00:00'),
(2, 8, 'COMMENT', 7, '其他', '评论包含与论文无关的广告链接。', 'RESOLVED', 2, '2026-05-06 10:00:00', '经核实评论含推广链接，已隐藏该评论并通知评论人。', '2026-05-04 09:00:00');
INSERT INTO appeal (id, report_id, decision_id, appellant_id, content, status, result, handled_by, handled_at, created_at) VALUES
(1, NULL, 2, 8, '本人不服退稿决定：审稿人1所指"与2024年SIGIR教程重合"的判断有误，本文聚焦GNN在多行为推荐中的统一建模，与教程侧重不同；且已按审稿人2意见补充过平滑定量分析（见附件实验）。请求编辑部复议。', 'PENDING', NULL, NULL, NULL, '2026-08-10 09:00:00');
-- 注：appeal.decision_id=2 对应稿件8的退稿决定（editorial_decision id=2）

-- =====================================================================
-- 站内消息
-- =====================================================================
INSERT INTO notification (id, user_id, type, title, content, ref_id, read_at, created_at) VALUES
(1, 8, 'REVISE_REQUEST', '稿件《联邦学习场景下的梯度泄露攻击与防御综述》需要修改', '编辑已作出"退修"决定，请查看审稿意见并在截止日期前修回。', 4, NULL, '2026-09-22 11:05:00'),
(2, 8, 'DECISION', '稿件《图神经网络在推荐系统中的应用综述》未通过评审', '很遗憾，稿件被退稿。如对决定有异议，可在7日内提交申诉（仅1次）。', 8, '2026-08-06 09:00:00', '2026-08-05 14:05:00'),
(3, 4, 'REVIEW_INVITE', '您有新的审稿邀请：微服务架构下的分布式事务一致性保障机制', '请在该稿件截止日期前接受或拒绝邀请。', 7, '2026-08-29 09:00:00', '2026-08-28 09:05:00'),
(4, 5, 'REVIEW_INVITE', '您有新的审稿邀请：联邦学习场景下的梯度泄露攻击与防御综述', '请在该稿件截止日期前接受或拒绝邀请。', 11, '2026-09-09 09:00:00', '2026-09-08 09:05:00'),
(5, 7, 'REVIEW_INVITE', '您有新的审稿邀请：面向开放域问答的知识图谱增强检索方法', '请在该稿件截止日期前接受或拒绝邀请。', 6, NULL, '2026-09-20 09:05:00'),
(6, 2, 'SYSTEM', '稿件《微服务架构下的分布式事务一致性保障机制》审稿意见已齐', '所有审稿任务已完成，请汇总意见并作出决定。', 3, NULL, '2026-09-29 16:01:00'),
(7, 3, 'LLM', '一条元数据抽取任务失败，已转人工', '稿件《面向开放域问答的知识图谱增强检索方法》的抽取任务输出非法JSON，请人工核对元数据。', 5, '2026-09-21 09:00:00', '2026-09-20 08:35:00'),
(8, 9, 'PUBLISH', '稿件《基于深度学习的短文本情感分析方法》已正式发布', '文章已归档发布，可在公开检索页查看。', 12, '2026-04-21 09:00:00', '2026-04-20 09:05:00'),
(9, 1, 'APPEAL', '收到退稿申诉：图神经网络在推荐系统中的应用综述', '作者林晓萌对退稿决定提出申诉，请进入申诉管理终审。', 1, NULL, '2026-08-10 09:05:00'),
(10, 8, 'PUBLISH', '稿件《基于大语言模型的学术论文元数据自动抽取方法研究》已正式发布', '文章已归档发布，卷期：4卷2期，DOI：10.99999/jf.2026.002。', 1, '2026-07-02 09:00:00', '2026-07-01 10:05:00');

-- =====================================================================
-- 操作日志（历史样例）
-- =====================================================================
INSERT INTO operation_log (id, user_id, action, object_type, object_id, detail, ip, success, created_at) VALUES
(1, 2, 'LOGIN', 'USER', 2, '{"username":"editor1"}', '192.168.1.10', 1, '2026-09-27 08:30:00'),
(2, 2, 'FORMAT_REVIEW_START', 'MANUSCRIPT', 7, '{"title":"大模型辅助的代码缺陷检测研究进展"}', '192.168.1.10', 1, '2026-09-27 10:45:00'),
(3, 2, 'LLM_TASK_CREATE', 'LLM_TASK', 3, '{"scene":"EXTRACT","manuscriptId":7}', '192.168.1.10', 1, '2026-09-27 10:50:00'),
(4, 3, 'ASSIGN_REVIEWERS', 'MANUSCRIPT', 2, '{"reviewerIds":[5,7],"dueAt":"2026-10-04"}', '192.168.1.11', 1, '2026-09-20 09:00:00'),
(5, 5, 'SUBMIT_REPORT', 'MANUSCRIPT', 4, '{"assignmentId":11,"recommendation":"MAJOR"}', '10.20.30.41', 1, '2026-09-18 16:00:00'),
(6, 2, 'DECISION', 'MANUSCRIPT', 4, '{"decision":"REVISE","stage":"REVIEW"}', '192.168.1.10', 1, '2026-09-22 11:00:00'),
(7, 2, 'PUBLISH', 'MANUSCRIPT', 12, '{"volume":"3","issue":"6"}', '192.168.1.10', 1, '2026-04-20 09:00:00'),
(8, 2, 'REPORT_HANDLE', 'REPORT', 2, '{"status":"RESOLVED"}', '192.168.1.10', 1, '2026-05-06 10:00:00'),
(9, 1, 'USER_STATUS', 'USER', 10, '{"status":1}', '192.168.1.5', 1, '2026-06-01 09:00:00'),
(10, 10, 'LOGIN', 'USER', 10, '{"username":"author3"}', '10.20.30.42', 0, '2026-09-28 14:59:00'),
(11, 10, 'LOGIN', 'USER', 10, '{"username":"author3"}', '10.20.30.42', 1, '2026-09-28 15:00:00'),
(12, 10, 'SUBMIT_MANUSCRIPT', 'MANUSCRIPT', 6, '{"title":"软件供应链安全风险评估方法研究","versionNo":1}', '10.20.30.42', 1, '2026-09-28 15:00:00');

-- =====================================================================
-- 阅读统计（最近 N 天，相对当前日期）
-- =====================================================================
INSERT INTO view_stat (manuscript_id, view_count, download_count, stat_date) VALUES
(1, 32, 5, DATE_SUB(CURDATE(), INTERVAL 1 DAY)),
(1, 45, 8, DATE_SUB(CURDATE(), INTERVAL 2 DAY)),
(1, 28, 4, DATE_SUB(CURDATE(), INTERVAL 3 DAY)),
(1, 51, 9, DATE_SUB(CURDATE(), INTERVAL 4 DAY)),
(1, 37, 6, DATE_SUB(CURDATE(), INTERVAL 5 DAY)),
(1, 22, 3, DATE_SUB(CURDATE(), INTERVAL 6 DAY)),
(1, 40, 7, DATE_SUB(CURDATE(), INTERVAL 8 DAY)),
(1, 35, 5, DATE_SUB(CURDATE(), INTERVAL 10 DAY)),
(1, 26, 4, DATE_SUB(CURDATE(), INTERVAL 13 DAY)),
(1, 44, 8, DATE_SUB(CURDATE(), INTERVAL 16 DAY)),
(1, 30, 6, DATE_SUB(CURDATE(), INTERVAL 20 DAY)),
(1, 38, 7, DATE_SUB(CURDATE(), INTERVAL 25 DAY)),
(1, 29, 5, DATE_SUB(CURDATE(), INTERVAL 30 DAY)),
(1, 33, 6, DATE_SUB(CURDATE(), INTERVAL 40 DAY)),
(1, 41, 8, DATE_SUB(CURDATE(), INTERVAL 50 DAY)),
(12, 66, 12, DATE_SUB(CURDATE(), INTERVAL 1 DAY)),
(12, 72, 15, DATE_SUB(CURDATE(), INTERVAL 2 DAY)),
(12, 58, 10, DATE_SUB(CURDATE(), INTERVAL 3 DAY)),
(12, 80, 18, DATE_SUB(CURDATE(), INTERVAL 4 DAY)),
(12, 63, 11, DATE_SUB(CURDATE(), INTERVAL 5 DAY)),
(12, 55, 9, DATE_SUB(CURDATE(), INTERVAL 7 DAY)),
(12, 69, 13, DATE_SUB(CURDATE(), INTERVAL 9 DAY)),
(12, 61, 12, DATE_SUB(CURDATE(), INTERVAL 12 DAY)),
(12, 74, 16, DATE_SUB(CURDATE(), INTERVAL 15 DAY)),
(12, 57, 10, DATE_SUB(CURDATE(), INTERVAL 18 DAY)),
(12, 65, 12, DATE_SUB(CURDATE(), INTERVAL 22 DAY)),
(12, 70, 14, DATE_SUB(CURDATE(), INTERVAL 26 DAY)),
(12, 52, 8, DATE_SUB(CURDATE(), INTERVAL 31 DAY)),
(12, 60, 11, DATE_SUB(CURDATE(), INTERVAL 36 DAY)),
(12, 68, 13, DATE_SUB(CURDATE(), INTERVAL 42 DAY));

SET FOREIGN_KEY_CHECKS = 1;
