CREATE TABLE categories (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(100) NOT NULL,
    slug          VARCHAR(100) NOT NULL,
    icon          VARCHAR(255),
    parent_id     UUID,                        -- NULL = root category
    display_order INTEGER      NOT NULL DEFAULT 0,
    is_active     BOOLEAN      NOT NULL DEFAULT true,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT categories_slug_unique   UNIQUE (slug),
    CONSTRAINT categories_parent_fk
    FOREIGN KEY (parent_id) REFERENCES categories (id)
        ON DELETE SET NULL
);

COMMENT ON TABLE  categories             IS 'Course categories, supports 2 levels (parent-child)';
COMMENT ON COLUMN categories.slug        IS 'URL-friendly name, e.g.: lap-trinh-web';
COMMENT ON COLUMN categories.parent_id   IS 'NULL = root category, has value = child category';
COMMENT ON COLUMN categories.display_order IS 'Display order in UI';

-- Seed data (default category names kept in Vietnamese — user-facing content)
INSERT INTO categories (name, slug, icon, display_order) VALUES
    ('Lập trình',        'lap-trinh',       '💻', 1),
    ('Thiết kế',         'thiet-ke',        '🎨', 2),
    ('Kinh doanh',       'kinh-doanh',      '💼', 3),
    ('Marketing',        'marketing',       '📊', 4),
    ('Ngoại ngữ',        'ngoai-ngu',       '🌍', 5),
    ('Âm nhạc',          'am-nhac',         '🎵', 6),
    ('Sức khỏe',         'suc-khoe',        '💪', 7),
    ('Nhiếp ảnh',        'nhiep-anh',       '📷', 8);