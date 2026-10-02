// Demo data for Student Churn Prediction: 8 students with different learning behaviour in one course.
// Needs the stack from docker-compose.yml running and seed-courses.js applied first.
// Usage: node seed-churn-demo.js [--remove] [course-slug]
// Then score them: curl -X POST http://localhost:8084/api/v1/internal/churn/run
const { execFileSync } = require("child_process");

const args = process.argv.slice(2);
const REMOVE = args.includes("--remove");
const COURSE_SLUG = args.find((a) => !a.startsWith("--")) ?? "aws-developer-associate";

const psql = (container, db, sql) =>
  execFileSync(
    "docker",
    ["exec", "-i", container, "sh", "-c", `psql -q -At -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d ${db}`],
    { input: sql, encoding: "utf8" },
  );
const q = (s) => (s === null || s === undefined ? "NULL" : `'${String(s).replace(/'/g, "''")}'`);
const ago = (days) => (days === null ? "NULL" : `NOW() - INTERVAL '${days} days'`);

// loginDays: days ago with a login; lessons: completed count; lastLesson: days since the last lesson;
// took: days the last completed lesson took; recent: partially watched lessons [daysAgo, % watched]
const STUDENTS = [
  { name: "Nguyễn Văn An", enrolled: 40, loginDays: [0, 1, 2, 3, 4, 5, 6, 8, 9, 10, 11, 12, 15, 17, 20], lessons: 180, lastLesson: 0, took: 1, recent: [[0, 95], [1, 90]] },
  { name: "Trần Thị Bình", enrolled: 35, loginDays: [1, 3, 6, 9, 12, 15, 18, 21, 24], lessons: 120, lastLesson: 1, took: 2, recent: [[1, 70], [3, 60]] },
  { name: "Lê Minh Châu", enrolled: 50, loginDays: [11, 13, 15, 16, 18, 19, 20, 22, 25], lessons: 70, lastLesson: 11, took: 5, recent: [] },
  { name: "Phạm Quốc Dũng", enrolled: 70, loginDays: [], lastLogin: 38, lessons: 25, lastLesson: 40, took: 9, recent: [] },
  { name: "Hoàng Thu Hà", enrolled: 22, loginDays: [21], lessons: 0, lastLesson: null, recent: [] },
  { name: "Vũ Đức Khang", enrolled: 60, loginDays: [2, 5, 9, 14, 19, 23], lessons: 275, lastLesson: 2, took: 1, recent: [], completed: true },
  { name: "Đặng Mai Linh", enrolled: 3, loginDays: [0, 1, 2, 3], lessons: 4, lastLesson: 0, took: 1, recent: [[0, 40]] },
  { name: "Bùi Gia Minh", enrolled: 45, loginDays: [6, 13, 16, 19, 23, 26], lessons: 95, lastLesson: 7, took: 4, recent: [[6, 25]] },
];
const userId = (i) => `dddddddd-0000-4000-8000-${String(i + 1).padStart(12, "0")}`;
const ids = STUDENTS.map((_, i) => q(userId(i))).join(",");

psql("learnhub-identity-db", "identity_db", `DELETE FROM users WHERE id IN (${ids});`);
psql("learnhub-user-db", "user_db", `DELETE FROM user_profiles WHERE user_id IN (${ids}); DELETE FROM notification_settings WHERE user_id IN (${ids});`);
psql("learnhub-enrollment-db", "enrollment_db", `DELETE FROM enrollments WHERE user_id IN (${ids});`);

const course = psql("learnhub-course-db", "course_db",
  `SELECT id || '|' || title || '|' || COALESCE(thumbnail_url, '') FROM courses WHERE slug = ${q(COURSE_SLUG)};`).trim();
if (!course) throw new Error(`Course not found: ${COURSE_SLUG} (run seed-courses.js first)`);
const [courseId, courseTitle, thumbnail] = course.split("|");

// Rows are inserted directly, so the enrollment.created event that normally updates
// courses.total_students never fires; recount it from enrollment_db instead
const syncTotalStudents = () => {
  const total = psql("learnhub-enrollment-db", "enrollment_db",
    `SELECT COUNT(*) FROM enrollments WHERE course_id = ${q(courseId)};`).trim();
  psql("learnhub-course-db", "course_db", `UPDATE courses SET total_students = ${Number(total)} WHERE id = ${q(courseId)};`);
};

if (REMOVE) {
  syncTotalStudents();
  console.log("Churn demo students removed");
  process.exit(0);
}
const lessons = psql("learnhub-course-db", "course_db",
  `SELECT l.id || '|' || COALESCE(l.video_duration, 300) FROM lessons l JOIN sections s ON s.id = l.section_id
   WHERE l.course_id = ${q(courseId)} AND l.is_published ORDER BY s.display_order, l.display_order;`)
  .trim().split("\n").map((row) => { const [id, d] = row.split("|"); return { id, duration: Number(d) }; });

const identity = [], users = [], enrollment = [];
STUDENTS.forEach((s, i) => {
  const id = userId(i);
  const email = `demo.student${i + 1}@learnhub.local`;
  const lastLogin = s.loginDays.length ? Math.min(...s.loginDays) : s.lastLogin;
  identity.push(`INSERT INTO users (id, cognito_sub, email, role, is_email_verified, created_at, last_login_at)
    VALUES (${q(id)}, ${q(`demo-churn-${i + 1}`)}, ${q(email)}, 'student', true, ${ago(s.enrolled + 1)}, ${ago(lastLogin)});`);
  for (const d of s.loginDays) identity.push(`INSERT INTO user_login_days VALUES (${q(id)}, CURRENT_DATE - ${d});`);

  users.push(`INSERT INTO user_profiles (user_id, full_name) VALUES (${q(id)}, ${q(s.name)});`);
  users.push(`INSERT INTO notification_settings (user_id) VALUES (${q(id)});`);

  const total = lessons.length;
  const done = Math.min(s.lessons, total);
  const enrollmentId = `eeeeeeee-0000-4000-8000-${String(i + 1).padStart(12, "0")}`;
  enrollment.push(`INSERT INTO enrollments (id, user_id, course_id, course_title, course_thumbnail_url, total_lessons,
      completed_lessons, progress_percent, is_completed, enrolled_at, completed_at, last_accessed_at)
    VALUES (${q(enrollmentId)}, ${q(id)}, ${q(courseId)}, ${q(courseTitle)}, ${q(thumbnail || null)}, ${total}, ${done},
      ${((done * 100) / total).toFixed(2)}, ${!!s.completed}, ${ago(s.enrolled)}, ${s.completed ? ago(s.lastLesson) : "NULL"},
      ${ago(s.lastLesson)});`);

  // Only the latest few completed lessons need realistic timestamps for the features
  for (let k = Math.max(0, done - 5); k < done; k++) {
    const l = lessons[k];
    const completedAgo = s.lastLesson + (done - 1 - k) * s.took;
    enrollment.push(`INSERT INTO lesson_progress (enrollment_id, user_id, lesson_id, is_completed, watch_duration_sec,
        last_position_sec, video_duration_sec, completed_at, created_at, updated_at)
      VALUES (${q(enrollmentId)}, ${q(id)}, ${q(l.id)}, true, ${l.duration}, ${l.duration}, ${l.duration},
        ${ago(completedAgo)}, ${ago(completedAgo + s.took)}, ${ago(completedAgo)});`);
  }
  s.recent.forEach(([daysAgo, pct], j) => {
    const l = lessons[done + j];
    if (!l) return;
    const watched = Math.round((l.duration * pct) / 100);
    enrollment.push(`INSERT INTO lesson_progress (enrollment_id, user_id, lesson_id, is_completed, watch_duration_sec,
        last_position_sec, video_duration_sec, created_at, updated_at)
      VALUES (${q(enrollmentId)}, ${q(id)}, ${q(l.id)}, false, ${watched}, ${watched}, ${l.duration},
        ${ago(daysAgo + 1)}, ${ago(daysAgo)});`);
  });
});

psql("learnhub-identity-db", "identity_db", identity.join("\n"));
psql("learnhub-user-db", "user_db", users.join("\n"));
psql("learnhub-enrollment-db", "enrollment_db", enrollment.join("\n"));
syncTotalStudents();
console.log(`Seeded ${STUDENTS.length} churn demo students into "${courseTitle}" (${courseId})`);
