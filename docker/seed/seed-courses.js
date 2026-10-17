// Usage: node seed-courses.js [--dry-run] [dataDir]
const fs = require("fs");
const path = require("path");
const crypto = require("crypto");
const { execFileSync } = require("child_process");

const args = process.argv.slice(2);
const DRY_RUN = args.includes("--dry-run");
const ROOT = args.find((a) => !a.startsWith("--")) ?? path.resolve(__dirname, "../../../test_data_courses");
const OUT = path.join(__dirname, "seed-courses.sql");
const DB_CONTAINER = "learnhub-course-db";

const INSTRUCTOR_ID = "11111111-1111-1111-1111-111111111111";
const WORDS_PER_MINUTE = 150;
const LAB_PLACEHOLDER = "Nội dung hướng dẫn cho lab này đang được cập nhật.";

const COURSES = {
  "AWS CloudOps Engineer - Associate": {
    slug: "aws-cloudops-engineer-associate",
    thumbnailUrl: "https://images.unsplash.com/photo-1558494949-ef010cbdcc31?w=800&q=80",
    price: 299000,
    shortDescription:
      "Ôn luyện chứng chỉ AWS Certified CloudOps Engineer – Associate (SOA-C03): vận hành, giám sát, bảo mật và tự động hoá hạ tầng AWS.",
    description:
      "Khoá học bao quát toàn bộ nội dung kỳ thi AWS CloudOps Engineer – Associate: Compute, Storage, Networking, Databases, Automation & Optimization, Monitoring & Reporting và Security & Compliance, kèm các bài Practice Lab thực hành trên AWS Console.",
    tags: ["aws", "cloud", "devops", "soa-c03"],
    requirements: ["Có kiến thức cơ bản về AWS Cloud Practitioner", "Hiểu biết cơ bản về mạng và Linux"],
    objectives: [
      "Triển khai và vận hành workload trên AWS",
      "Giám sát, ghi log và cảnh báo với CloudWatch",
      "Tự động hoá hạ tầng với CloudFormation và Systems Manager",
      "Sẵn sàng thi chứng chỉ SOA-C03",
    ],
  },
  "AWS Developer - Associate": {
    slug: "aws-developer-associate",
    thumbnailUrl: "https://images.unsplash.com/photo-1484417894907-623942c8ee29?w=800&q=80",
    price: 0,
    shortDescription:
      "Ôn luyện chứng chỉ AWS Certified Developer – Associate: xây dựng, triển khai và debug ứng dụng cloud-native trên AWS.",
    description:
      "Khoá học bao quát nội dung kỳ thi AWS Developer – Associate: Compute, Storage, Databases, Networking, Analytics, Developer Tools, Containers, Security và Application Integration, kèm các bài Practice Lab thực hành.",
    tags: ["aws", "cloud", "developer", "serverless"],
    requirements: ["Biết ít nhất một ngôn ngữ lập trình", "Có kiến thức cơ bản về AWS"],
    objectives: [
      "Phát triển ứng dụng serverless với Lambda, API Gateway, DynamoDB",
      "Xây dựng CI/CD với AWS Developer Tools",
      "Bảo mật ứng dụng với IAM, Cognito, KMS",
      "Sẵn sàng thi chứng chỉ AWS Developer – Associate",
    ],
  },
};

const q = (s) => (s === null || s === undefined ? "NULL" : `'${String(s).replace(/'/g, "''")}'`);
const arr = (a) => `ARRAY[${a.map(q).join(",")}]::text[]`;

const orderOf = (name) => {
  const m = name.replace(/^\([^)]*\)\s*/, "").match(/^(\d+)\./);
  return m ? parseInt(m[1], 10) : Number.MAX_SAFE_INTEGER;
};
const cleanTitle = (name) =>
  name.replace(/\.md$/i, "").replace(/^\([^)]*\)\s*/, "").replace(/^\d+\.\s*/, "").trim().slice(0, 300);
const byOrder = (a, b) => orderOf(a) - orderOf(b) || a.localeCompare(b);

const listDirs = (dir) => fs.readdirSync(dir, { withFileTypes: true }).filter((d) => d.isDirectory()).map((d) => d.name).sort(byOrder);
const listMd = (dir) => fs.readdirSync(dir, { withFileTypes: true }).filter((d) => d.isFile() && /\.md$/i.test(d.name)).map((d) => d.name).sort(byOrder);
const read = (file) => fs.readFileSync(file, "utf8").replace(/^﻿/, "").replace(/\r\n/g, "\n").trim();

const hasRealText = (text) => text.replace(/!\[\[[^\]]*\]\]/g, "").trim().length > 0;
const estimateSeconds = (text) => Math.max(60, Math.round((text.split(/\s+/).filter(Boolean).length / WORDS_PER_MINUTE) * 60));

const sql = ["SET client_encoding = 'UTF8';", "BEGIN;"];
sql.push(`DELETE FROM courses WHERE slug IN (${Object.values(COURSES).map((c) => q(c.slug)).join(",")});`);

const summary = [];

for (const courseDir of listDirs(ROOT)) {
  const meta = COURSES[courseDir];
  if (!meta) throw new Error(`No metadata for course folder: ${courseDir}`);

  const courseId = crypto.randomUUID();
  const base = path.join(ROOT, courseDir, "English");

  sql.push(`INSERT INTO courses (id, instructor_id, category_id, title, slug, description, short_description, thumbnail_url,
  level, language, price, status, tags, requirements, objectives, published_at)
VALUES (${q(courseId)}, ${q(INSTRUCTOR_ID)}, (SELECT id FROM categories WHERE slug = 'lap-trinh'),
  ${q(courseDir)}, ${q(meta.slug)}, ${q(meta.description)}, ${q(meta.shortDescription)}, ${q(meta.thumbnailUrl)},
  'intermediate', 'en', ${meta.price}, 'published', ${arr(meta.tags)}, ${arr(meta.requirements)}, ${arr(meta.objectives)}, NOW());`);

  const sections = [];
  const topDirs = listDirs(base);
  for (const d of topDirs.filter((d) => d !== "Practice Labs")) {
    sections.push({ title: cleanTitle(d), dir: path.join(base, d), isLab: false });
  }
  if (topDirs.includes("Practice Labs")) {
    const labs = path.join(base, "Practice Labs");
    if (listMd(labs).length) sections.push({ title: "Practice Labs", dir: labs, isLab: true });
    for (const d of listDirs(labs)) {
      sections.push({ title: `Practice Labs: ${cleanTitle(d)}`, dir: path.join(labs, d), isLab: true });
    }
  }

  let lessonCount = 0;
  let placeholders = 0;
  let firstLesson = true;

  sections.forEach((s, si) => {
    const sectionId = crypto.randomUUID();
    sql.push(`INSERT INTO sections (id, course_id, title, display_order) VALUES (${q(sectionId)}, ${q(courseId)}, ${q(s.title)}, ${si + 1});`);

    listMd(s.dir).forEach((file, li) => {
      const text = read(path.join(s.dir, file));
      const title = cleanTitle(file);
      let type, content = null, transcript = null, duration = null;

      if (s.isLab) {
        type = "text";
        if (hasRealText(text)) content = text;
        else { content = LAB_PLACEHOLDER; placeholders++; }
      } else {
        type = "video";
        transcript = text.replace(/^Video: https?:\/\/\S+\s*$/gm, "").trim();
        duration = estimateSeconds(transcript);
      }

      sql.push(`INSERT INTO lessons (section_id, course_id, title, lesson_type, video_duration, content, transcript,
  display_order, is_preview, is_published, published_at)
VALUES (${q(sectionId)}, ${q(courseId)}, ${q(title)}, '${type}', ${duration ?? "NULL"}, ${q(content)}, ${q(transcript)},
  ${li + 1}, ${firstLesson}, true, NOW());`);
      firstLesson = false;
      lessonCount++;
    });
  });

  sql.push(`UPDATE courses SET
  total_lessons  = (SELECT COUNT(*) FROM lessons WHERE course_id = ${q(courseId)}),
  total_duration = (SELECT COALESCE(SUM(video_duration), 0) FROM lessons WHERE course_id = ${q(courseId)})
WHERE id = ${q(courseId)};`);

  summary.push({ course: courseDir, sections: sections.length, lessons: lessonCount, labPlaceholders: placeholders });
}

sql.push("COMMIT;");
fs.writeFileSync(OUT, sql.join("\n\n") + "\n", "utf8");
console.table(summary);

if (DRY_RUN) {
  console.log(`SQL written to ${OUT}`);
} else {
  execFileSync(
    "docker",
    ["exec", "-i", DB_CONTAINER, "sh", "-c", 'psql -q -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'],
    { input: fs.readFileSync(OUT), stdio: ["pipe", "inherit", "inherit"] }
  );
  console.log(`Seeded into ${DB_CONTAINER}`);
}
