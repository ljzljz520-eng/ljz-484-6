package com.researchnotes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地数据存储：data/db.json 一个文件保存全部课题、笔记、章节与引用。
 * 所有公开入口都加了 synchronized，读方法只返回 isPublic=true 的笔记。
 */
public final class DataStore {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path dbFile;
    private Map<String, Object> db;
    private long noteSeq = 0;
    private long topicSeq = 0;

    public DataStore(Path dataDir) throws IOException {
        Files.createDirectories(dataDir);
        this.dbFile = dataDir.resolve("db.json");
        load();
    }

    // ================= 加载 / 保存 =================

    private synchronized void load() throws IOException {
        if (Files.exists(dbFile)) {
            String text = new String(Files.readAllBytes(dbFile), StandardCharsets.UTF_8);
            if (!text.trim().isEmpty()) {
                db = Json.parseObject(text);
            } else {
                db = blankDb();
            }
        } else {
            db = seedDb();
            persist();
            System.out.println("未发现数据文件，已创建示例数据: " + dbFile.toAbsolutePath());
        }
        // 序号从已有 id 推算，保证重启后不重复
        for (Object o : notes()) {
            noteSeq = Math.max(noteSeq, idTail(str(o, "id"), "n"));
        }
        for (Object o : topics()) {
            topicSeq = Math.max(topicSeq, idTail(str(o, "id"), "t"));
        }
    }

    private synchronized void persist() {
        try {
            Path tmp = dbFile.resolveSibling(dbFile.getFileName() + ".tmp");
            Files.write(tmp, Json.stringify(db).getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(tmp, dbFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException am) {
                // 某些文件系统（如跨设备挂载）不支持原子移动，退回普通替换
                Files.move(tmp, dbFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("写入数据文件失败: " + dbFile, e);
        }
    }

    private static Map<String, Object> blankDb() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("topics", new ArrayList<Object>());
        m.put("notes", new ArrayList<Object>());
        return m;
    }

    // ================= 类型访问辅助 =================

    @SuppressWarnings("unchecked")
    private List<Object> topics() {
        return (List<Object>) db.get("topics");
    }

    @SuppressWarnings("unchecked")
    private List<Object> notes() {
        return (List<Object>) db.get("notes");
    }

    private static String str(Object o, String key) {
        if (o instanceof Map) {
            Object v = ((Map<?, ?>) o).get(key);
            return v == null ? "" : String.valueOf(v);
        }
        return "";
    }

    private static boolean bool(Object o, String key) {
        return o instanceof Map && Boolean.TRUE.equals(((Map<?, ?>) o).get(key));
    }

    private static long idTail(String id, String prefix) {
        try {
            return Long.parseLong(id.substring(prefix.length()));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static String now() {
        return LocalDateTime.now().format(TS);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object o, String key) {
        if (o instanceof Map) {
            Object v = ((Map<?, ?>) o).get(key);
            if (v instanceof List) {
                return (List<Object>) v;
            }
        }
        return new ArrayList<Object>();
    }

    private static Map<String, Object> find(List<Object> list, String key, String value) {
        for (Object o : list) {
            if (o instanceof Map && value.equals(((Map<?, ?>) o).get(key))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) o;
                return m;
            }
        }
        return null;
    }

    // ================= 公开只读接口（私密笔记绝不外泄） =================

    /** 公开目录：仅包含公开笔记，不返回正文；空课题不出现在目录中。 */
    public synchronized Map<String, Object> publicCatalog() {
        List<Object> topicList = new ArrayList<Object>();
        for (Object t : topics()) {
            List<Object> pubNotes = new ArrayList<Object>();
            for (Object n : notes()) {
                if (bool(n, "isPublic") && str(t, "id").equals(str(n, "topicId"))) {
                    pubNotes.add(noteSummary(n));
                }
            }
            if (!pubNotes.isEmpty()) {
                topicList.add(topicWithNotes(t, pubNotes));
            }
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("generatedAt", now());
        result.put("topics", topicList);
        return result;
    }

    /** 公开阅读页数据：只能取到公开笔记，私密笔记按 404 处理（不泄露存在性）。 */
    public synchronized Map<String, Object> publicNote(String id) {
        Map<String, Object> n = find(notes(), "id", id);
        if (n == null || !bool(n, "isPublic")) {
            return null;
        }
        return noteDetail(n);
    }

    // ================= 研究员（后台）接口 =================

    public synchronized Map<String, Object> adminTopics() {
        List<Object> out = new ArrayList<Object>();
        for (Object t : topics()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> src = (Map<String, Object>) t;
            Map<String, Object> copy = new LinkedHashMap<String, Object>();
            copy.putAll(src);
            int total = 0;
            int pub = 0;
            for (Object n : notes()) {
                if (str(n, "topicId").equals(str(t, "id"))) {
                    total++;
                    if (bool(n, "isPublic")) {
                        pub++;
                    }
                }
            }
            copy.put("noteCount", total);
            copy.put("publicCount", pub);
            out.add(copy);
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("topics", out);
        return result;
    }

    public synchronized Map<String, Object> adminTopic(String id) {
        Map<String, Object> t = find(topics(), "id", id);
        return t == null ? null : new LinkedHashMap<String, Object>(t);
    }

    public synchronized Map<String, Object> createTopic(Map<String, Object> body) {
        String title = requiredText(body, "title", "课题标题");
        Map<String, Object> t = new LinkedHashMap<String, Object>();
        t.put("id", "t" + (++topicSeq));
        t.put("title", title);
        t.put("description", text(body, "description"));
        t.put("createdAt", now());
        t.put("updatedAt", now());
        topics().add(t);
        persist();
        return new LinkedHashMap<String, Object>(t);
    }

    public synchronized Map<String, Object> updateTopic(String id, Map<String, Object> body) {
        Map<String, Object> t = find(topics(), "id", id);
        if (t == null) {
            return null;
        }
        if (body.containsKey("title")) {
            t.put("title", requiredText(body, "title", "课题标题"));
        }
        if (body.containsKey("description")) {
            t.put("description", text(body, "description"));
        }
        t.put("updatedAt", now());
        persist();
        return new LinkedHashMap<String, Object>(t);
    }

    /** 删除课题：仍挂有笔记（无论公开/私密）时拒绝，避免笔记成为孤儿数据。 */
    public synchronized boolean deleteTopic(String id) {
        Map<String, Object> t = find(topics(), "id", id);
        if (t == null) {
            return false;
        }
        for (Object n : notes()) {
            if (id.equals(str(n, "topicId"))) {
                throw new IllegalStateException("课题下仍有笔记，请先删除或转移这些笔记");
            }
        }
        topics().remove(t);
        persist();
        return true;
    }

    public synchronized Map<String, Object> adminNotes() {
        List<Object> out = new ArrayList<Object>();
        for (Object n : notes()) {
            out.add(adminNoteRow(n));
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("notes", out);
        return result;
    }

    public synchronized Map<String, Object> adminNote(String id) {
        Map<String, Object> n = find(notes(), "id", id);
        return n == null ? null : adminNoteRow(n);
    }

    public synchronized Map<String, Object> createNote(Map<String, Object> body) {
        String topicId = requiredText(body, "topicId", "课题 ID");
        if (find(topics(), "id", topicId) == null) {
            throw new IllegalArgumentException("课题不存在: " + topicId);
        }
        String title = requiredText(body, "title", "笔记标题");
        Map<String, Object> n = new LinkedHashMap<String, Object>();
        n.put("id", "n" + (++noteSeq));
        n.put("topicId", topicId);
        n.put("title", title);
        n.put("summary", text(body, "summary"));
        n.put("content", text(body, "content"));
        n.put("chapters", normalizeChapters(body.get("chapters")));
        n.put("references", normalizeRefs(body.get("references")));
        // 新建笔记默认私密，避免研究草稿意外公开
        n.put("isPublic", Boolean.FALSE);
        n.put("createdAt", now());
        n.put("updatedAt", now());
        notes().add(n);
        persist();
        return adminNoteRow(n);
    }

    public synchronized Map<String, Object> updateNote(String id, Map<String, Object> body) {
        Map<String, Object> n = find(notes(), "id", id);
        if (n == null) {
            return null;
        }
        if (body.containsKey("topicId")) {
            String topicId = requiredText(body, "topicId", "课题 ID");
            if (find(topics(), "id", topicId) == null) {
                throw new IllegalArgumentException("课题不存在: " + topicId);
            }
            n.put("topicId", topicId);
        }
        if (body.containsKey("title")) {
            n.put("title", requiredText(body, "title", "笔记标题"));
        }
        if (body.containsKey("summary")) {
            n.put("summary", text(body, "summary"));
        }
        if (body.containsKey("content")) {
            n.put("content", text(body, "content"));
        }
        if (body.containsKey("chapters")) {
            n.put("chapters", normalizeChapters(body.get("chapters")));
        }
        if (body.containsKey("references")) {
            n.put("references", normalizeRefs(body.get("references")));
        }
        if (body.containsKey("isPublic")) {
            n.put("isPublic", Boolean.TRUE.equals(body.get("isPublic")));
        }
        n.put("updatedAt", now());
        persist();
        return adminNoteRow(n);
    }

    public synchronized boolean deleteNote(String id) {
        Map<String, Object> n = find(notes(), "id", id);
        if (n == null) {
            return false;
        }
        notes().remove(n);
        persist();
        return true;
    }

    // ================= 组装视图对象 =================

    private Map<String, Object> topicWithNotes(Object t, List<Object> noteSummaries) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", str(t, "id"));
        m.put("title", str(t, "title"));
        m.put("description", str(t, "description"));
        m.put("notes", noteSummaries);
        return m;
    }

    private Map<String, Object> noteSummary(Object n) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", str(n, "id"));
        m.put("title", str(n, "title"));
        m.put("summary", str(n, "summary"));
        m.put("chapterCount", list(n, "chapters").size());
        m.put("referenceCount", list(n, "references").size());
        m.put("updatedAt", str(n, "updatedAt"));
        return m;
    }

    private Map<String, Object> noteDetail(Object n) {
        Map<String, Object> topic = find(topics(), "id", str(n, "topicId"));
        Map<String, Object> m = noteSummary(n);
        m.put("content", str(n, "content"));
        m.put("chapters", list(n, "chapters"));
        m.put("references", list(n, "references"));
        m.put("topicId", str(n, "topicId"));
        m.put("topicTitle", topic == null ? "未分类" : str(topic, "title"));
        m.put("createdAt", str(n, "createdAt"));
        return m;
    }

    private Map<String, Object> adminNoteRow(Object n) {
        Map<String, Object> m = noteDetail(n);
        m.put("isPublic", bool(n, "isPublic"));
        Map<String, Object> topic = find(topics(), "id", str(n, "topicId"));
        m.put("topicTitle", topic == null ? "未分类" : str(topic, "title"));
        return m;
    }

    // ================= 入参规整与校验 =================

    private static String text(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static String requiredText(Map<String, Object> body, String key, String label) {
        String v = text(body, key);
        if (v.isEmpty()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        return v;
    }

    /** 章节：{heading, anchor?, body}；anchor 缺省按序号生成。 */
    private static List<Object> normalizeChapters(Object raw) {
        List<Object> out = new ArrayList<Object>();
        if (!(raw instanceof List)) {
            return out;
        }
        int idx = 0;
        for (Object o : (List<?>) raw) {
            if (!(o instanceof Map)) {
                continue;
            }
            Map<?, ?> src = (Map<?, ?>) o;
            String heading = src.get("heading") == null ? "" : String.valueOf(src.get("heading")).trim();
            String body = src.get("body") == null ? "" : String.valueOf(src.get("body"));
            String anchor = src.get("anchor") == null ? "" : String.valueOf(src.get("anchor")).trim();
            if (heading.isEmpty() && body.trim().isEmpty()) {
                continue;
            }
            idx++;
            Map<String, Object> c = new LinkedHashMap<String, Object>();
            c.put("heading", heading.isEmpty() ? "第 " + idx + " 节" : heading);
            c.put("anchor", anchor.isEmpty() ? "chap-" + idx : anchor);
            c.put("body", body);
            out.add(c);
        }
        return out;
    }

    /** 引用说明：{type, title, authors?, year?, url?, note?} */
    private static List<Object> normalizeRefs(Object raw) {
        List<Object> out = new ArrayList<Object>();
        if (!(raw instanceof List)) {
            return out;
        }
        for (Object o : (List<?>) raw) {
            if (!(o instanceof Map)) {
                continue;
            }
            Map<?, ?> src = (Map<?, ?>) o;
            String title = src.get("title") == null ? "" : String.valueOf(src.get("title")).trim();
            if (title.isEmpty()) {
                continue;
            }
            Map<String, Object> r = new LinkedHashMap<String, Object>();
            r.put("type", src.get("type") == null ? "其他" : String.valueOf(src.get("type")).trim());
            r.put("title", title);
            r.put("authors", src.get("authors") == null ? "" : String.valueOf(src.get("authors")).trim());
            r.put("year", src.get("year") == null ? "" : String.valueOf(src.get("year")).trim());
            r.put("url", src.get("url") == null ? "" : String.valueOf(src.get("url")).trim());
            r.put("note", src.get("note") == null ? "" : String.valueOf(src.get("note")));
            out.add(r);
        }
        return out;
    }

    // ================= 示例数据 =================

    private Map<String, Object> seedDb() {
        Map<String, Object> db = blankDb();

        Map<String, Object> t1 = new LinkedHashMap<String, Object>();
        t1.put("id", "t1");
        t1.put("title", "计算生物学");
        t1.put("description", "关注蛋白质结构预测与基因序列分析的研究笔记。");
        t1.put("createdAt", "2026-08-20 09:00:00");
        t1.put("updatedAt", "2026-09-01 10:30:00");

        Map<String, Object> t2 = new LinkedHashMap<String, Object>();
        t2.put("id", "t2");
        t2.put("title", "量子计算");
        t2.put("description", "量子纠错与近期含噪量子设备（NISQ）方向的阅读记录。");
        t2.put("createdAt", "2026-08-25 14:00:00");
        t2.put("updatedAt", "2026-09-05 16:20:00");

        db.put("topics", new ArrayList<Object>(java.util.Arrays.asList(t1, t2)));

        List<Object> notes = new ArrayList<Object>();
        notes.add(note("n1", "t1", "蛋白质结构预测方法综述",
                true,
                "梳理从同源建模到 AlphaFold 的技术脉络，以及评估指标的含义。",
                "# 蛋白质结构预测方法综述\n\n本笔记整理结构预测的三类路线：**同源建模**、**穿线法**与**端到端深度学习**。\n\n## 同源建模\n\n当目标序列与已知结构模板的序列相似度较高（一般 >30%）时，可直接以模板为骨架建模。\n\n## 端到端学习\n\nAlphaFold2 将序列、MSA 与几何约束统一进神经网络，关键创新包括：\n\n- Evoformer 对 MSA 与配对表示的联合更新\n- 结构模块的等变注意力与迭代精化\n- pLDDT 与 PAE 提供逐残基置信度\n\n> 局限：对**固有无序区域**、复合物构象变化和突变效应的预测仍需谨慎。",
                chapters(
                        chapter("研究背景", "chap-1", "蛋白质功能由三维结构决定，而实验测定结构成本高昂，计算预测长期是核心问题。"),
                        chapter("方法脉络", "chap-2", "同源建模依赖模板；穿线法在折叠库上匹配；深度学习直接从序列学习距离与坐标。"),
                        chapter("评估指标", "chap-3", "GDT_TS 衡量全局相似度，CASP 以其排名；pLDDT 提供模型自评估。")),
                refs(
                        ref("期刊论文", "Highly accurate protein structure prediction with AlphaFold", "Jumper J. 等", "2021",
                                "https://www.nature.com/articles/s41586-021-03819-2", "AlphaFold2 的主论文"),
                        ref("会议", "Critical assessment of methods of protein structure prediction (CASP)", "CASP 组委会", "2020",
                                "https://predictioncenter.org/", "双年度盲测竞赛说明"))));

        notes.add(note("n2", "t1", "单细胞测序批次效应校正笔记（草稿）",
                false,
                "尚未完成：对比 Harmony、scVI、BBKNN 的适用场景，含未发表数据结果。",
                "# 单细胞测序批次效应校正（草稿）\n\n## 待办\n\n- [ ] 补全 Harmony 数学推导\n- [ ] 内部数据集结果暂不公开\n- [ ] 与导师讨论 scVI 的先验假设\n\n**本笔记为私密草稿，请勿外传。**",
                chapters(
                        chapter("问题定义", "chap-1", "不同时间、不同试剂造成的批次差异会与真实生物信号混淆。"),
                        chapter("方法对比（未完成）", "chap-2", "待补充实验表格。")),
                refs(
                        ref("期刊论文", "Fast, sensitive and accurate integration of single-cell data with Harmony", "Korsunsky I. 等", "2019",
                                "https://www.nature.com/articles/s41592-019-0619-0", ""))));

        notes.add(note("n3", "t2", "表面码与逻辑量子比特入门",
                true,
                "表面码为什么能容错，逻辑错误率与物理错误率、码距的关系。",
                "# 表面码与逻辑量子比特入门\n\n容错量子计算通过**冗余编码**压制错误：物理错误率低于阈值时，增大码距可指数级降低逻辑错误率。\n\n## 核心概念\n\n- 数据比特与校验比特（X/Z stabilizer）交替排列在二维格点上\n- 每次校验测量得到 *syndrome*，解码器据此推断错误链\n- 常用解码器：MWPM 最小权完美匹配\n\n## 阈值定理的直觉\n\n物理错误率 $p$ 低于阈值 $p_{th}$ 时，逻辑错误率近似为 $A(p/p_{th})^{(d+1)/2}$，$d$ 为码距。",
                chapters(
                        chapter("为什么需要纠错", "chap-1", "物理量子比特退相干与门错误使大规模电路无法直接运行。"),
                        chapter("表面码布局", "chap-2", "距离 d 的表面码约需 2d^2-1 个物理比特。"),
                        chapter("开销问题", "chap-3", "容错通用计算还需要 magic state distillation，是主要面积开销。")),
                refs(
                        ref("期刊论文", "Surface codes: Towards practical large-scale quantum computation", "Fowler A.G. 等", "2012",
                                "https://link.aps.org/doi/10.1103/PhysRevA.86.032324", "表面码工程综述"),
                        ref("教材", "Quantum Computation and Quantum Information", "Nielsen M., Chuang I.", "2010",
                                "", "第 10-12 章纠错基础"))));

        notes.add(note("n4", "t2", "NISQ 变分算法阅读清单",
                true,
                "VQE / QAOA 的代表性论文与“贫瘠高原”问题索引。",
                "# NISQ 变分算法阅读清单\n\n近期设备量子比特有限、噪声显著，**变分算法**用经典优化器训练参数化量子线路。\n\n## 代表方向\n\n1. VQE：变分求基态能量\n2. QAOA：组合优化近似求解\n3. 贫瘠高原：梯度随比特数指数消失\n\n选型时优先考虑噪声鲁棒性，而非理想模拟器表现。",
                chapters(
                        chapter("VQE", "chap-1", "哈密顿量分组与 ansatz 设计是两大工程难点。"),
                        chapter("贫瘠高原", "chap-2", "全局纠缠的硬件高效 ansatz 容易出现梯度消失。")),
                refs(
                        ref("期刊论文", "A variational eigenvalue solver on a photonic quantum processor", "Peruzzo A. 等", "2014",
                                "https://www.nature.com/articles/ncomms5213", "VQE 开山之作"),
                        ref("期刊论文", "Barren plateaus in quantum neural network training landscapes", "McClean J.R. 等", "2018",
                                "https://www.nature.com/articles/s41467-018-07090-4", "贫瘠高原问题"))));

        notes.add(note("n5", "t2", "实验室硬件校准数据（内部）",
                false,
                "记录 QPU 校准参数与未公开实验，仅课题组内部可见。",
                "# 硬件校准数据（内部）\n\n本节包含未公开的 T1/T2 测量数据与门保真度表格，**不对外发布**。",
                chapters(chapter("校准记录", "chap-1", "略。")),
                refs()));

        db.put("notes", notes);
        noteSeq = 5;
        topicSeq = 2;
        return db;
    }

    private static Map<String, Object> note(String id, String topicId, String title, boolean pub,
                                            String summary, String content,
                                            List<Object> chapters, List<Object> refs) {
        Map<String, Object> n = new LinkedHashMap<String, Object>();
        n.put("id", id);
        n.put("topicId", topicId);
        n.put("title", title);
        n.put("summary", summary);
        n.put("content", content);
        n.put("chapters", chapters);
        n.put("references", refs);
        n.put("isPublic", pub);
        n.put("createdAt", "2026-08-28 09:00:00");
        n.put("updatedAt", "2026-09-08 18:00:00");
        return n;
    }

    private static List<Object> chapters(Map<String, Object>... cs) {
        return new ArrayList<Object>(java.util.Arrays.asList(cs));
    }

    private static Map<String, Object> chapter(String heading, String anchor, String body) {
        Map<String, Object> c = new LinkedHashMap<String, Object>();
        c.put("heading", heading);
        c.put("anchor", anchor);
        c.put("body", body);
        return c;
    }

    private static List<Object> refs(Map<String, Object>... rs) {
        return new ArrayList<Object>(java.util.Arrays.asList(rs));
    }

    private static Map<String, Object> ref(String type, String title, String authors, String year, String url, String note) {
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("type", type);
        r.put("title", title);
        r.put("authors", authors);
        r.put("year", year);
        r.put("url", url);
        r.put("note", note);
        return r;
    }
}
