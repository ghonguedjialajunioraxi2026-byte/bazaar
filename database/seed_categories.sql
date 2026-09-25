-- =========================================================
-- CAMSHOP — Seed des catégories (30 principales + sous-catégories)
-- Idempotent : ré-exécutable sans doublons (clé UNIQUE sur slug).
-- Exécuter via : mysql -u root -p mboa_style < database/seed_categories.sql
-- =========================================================
SET NAMES utf8mb4;

INSERT INTO categories (name, slug, parent_id, description, icon, display_order, is_active)
VALUES ('Mode & Vêtements', 'mode-vetements', NULL, 'Vêtements homme, femme et enfant : style moderne, traditionnel et cérémonie.', 'bi-bag', 1, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=NULL, description=VALUES(description), icon=VALUES(icon), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Vêtements homme', 'vetements-homme', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='mode-vetements'), NULL, 1, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Vêtements femme', 'vetements-femme', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='mode-vetements'), NULL, 2, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Vêtements enfant', 'vetements-enfant', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='mode-vetements'), NULL, 3, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Vêtements bébé', 'vetements-bebe', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='mode-vetements'), NULL, 4, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('T-shirts', 't-shirts', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='mode-vetements'), NULL, 5, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Chemises', 'chemises', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='mode-vetements'), NULL, 6, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Polos', 'polos', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='mode-vetements'), NULL, 7, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Pantalons', 'pantalons', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='mode-vetements'), NULL, 8, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Jeans', 'jeans', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='mode-vetements'), NULL, 9, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Shorts', 'shorts', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='mode-vetements'), NULL, 10, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Jupes', 'jupes', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='mode-vetements'), NULL, 11, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Robes', 'robes', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='mode-vetements'), NULL, 12, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Costumes', 'costumes', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='mode-vetements'), NULL, 13, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Vestes', 'vestes', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='mode-vetements'), NULL, 14, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Manteaux', 'manteaux', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='mode-vetements'), NULL, 15, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Pulls & Sweats', 'pulls-sweats', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='mode-vetements'), NULL, 16, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Sous-vêtements', 'sous-vetements', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='mode-vetements'), NULL, 17, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Pyjamas', 'pyjamas', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='mode-vetements'), NULL, 18, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Vêtements de sport', 'vetements-de-sport', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='mode-vetements'), NULL, 19, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Vêtements traditionnels', 'vetements-traditionnels', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='mode-vetements'), NULL, 20, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Vêtements de cérémonie', 'vetements-de-ceremonie', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='mode-vetements'), NULL, 21, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Maillots de football', 'maillots-de-football', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='mode-vetements'), NULL, 22, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Vêtements de plage', 'vetements-de-plage', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='mode-vetements'), NULL, 23, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);

INSERT INTO categories (name, slug, parent_id, description, icon, display_order, is_active)
VALUES ('Chaussures', 'chaussures', NULL, 'Sneakers, sandales, chaussures de ville et de sport pour toute la famille.', 'bi-tags', 2, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=NULL, description=VALUES(description), icon=VALUES(icon), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Chaussures homme', 'chaussures-homme', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='chaussures'), NULL, 1, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Chaussures femme', 'chaussures-femme', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='chaussures'), NULL, 2, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Chaussures enfant', 'chaussures-enfant', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='chaussures'), NULL, 3, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Sneakers', 'sneakers', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='chaussures'), NULL, 4, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Baskets', 'baskets', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='chaussures'), NULL, 5, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Chaussures de sport', 'chaussures-de-sport', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='chaussures'), NULL, 6, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Sandales', 'sandales', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='chaussures'), NULL, 7, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Claquettes', 'claquettes', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='chaussures'), NULL, 8, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Tongs', 'tongs', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='chaussures'), NULL, 9, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Bottes', 'bottes', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='chaussures'), NULL, 10, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Bottines', 'bottines', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='chaussures'), NULL, 11, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Mocassins', 'mocassins', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='chaussures'), NULL, 12, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Chaussures de ville', 'chaussures-de-ville', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='chaussures'), NULL, 13, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Chaussures de cérémonie', 'chaussures-de-ceremonie', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='chaussures'), NULL, 14, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Chaussures de sécurité', 'chaussures-de-securite', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='chaussures'), NULL, 15, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);

INSERT INTO categories (name, slug, parent_id, description, icon, display_order, is_active)
VALUES ('Sacs & Maroquinerie', 'sacs-maroquinerie', NULL, 'Sacs à main, sacs à dos, valises et petite maroquinerie.', 'bi-handbag', 3, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=NULL, description=VALUES(description), icon=VALUES(icon), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Sacs à main', 'sacs-a-main', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='sacs-maroquinerie'), NULL, 1, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Sacs à dos', 'sacs-a-dos', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='sacs-maroquinerie'), NULL, 2, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Sacs de voyage', 'sacs-de-voyage', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='sacs-maroquinerie'), NULL, 3, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Sacs de sport', 'sacs-de-sport', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='sacs-maroquinerie'), NULL, 4, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Sacs ordinateur', 'sacs-ordinateur', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='sacs-maroquinerie'), NULL, 5, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Sacs scolaires', 'sacs-scolaires', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='sacs-maroquinerie'), NULL, 6, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Portefeuilles', 'portefeuilles', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='sacs-maroquinerie'), NULL, 7, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Porte-monnaie', 'porte-monnaie', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='sacs-maroquinerie'), NULL, 8, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Porte-cartes', 'porte-cartes', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='sacs-maroquinerie'), NULL, 9, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Valises', 'valises', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='sacs-maroquinerie'), NULL, 10, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Bagages', 'bagages', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='sacs-maroquinerie'), NULL, 11, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Accessoires de voyage', 'accessoires-de-voyage', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='sacs-maroquinerie'), NULL, 12, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);

INSERT INTO categories (name, slug, parent_id, description, icon, display_order, is_active)
VALUES ('Bijoux & Accessoires', 'bijoux-accessoires', NULL, 'Bijoux, lunettes, ceintures et accessoires de mode.', 'bi-gem', 4, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=NULL, description=VALUES(description), icon=VALUES(icon), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Colliers', 'colliers', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bijoux-accessoires'), NULL, 1, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Bracelets', 'bracelets', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bijoux-accessoires'), NULL, 2, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Bagues', 'bagues', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bijoux-accessoires'), NULL, 3, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Boucles d''oreilles', 'boucles-doreilles', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bijoux-accessoires'), NULL, 4, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Bijoux homme', 'bijoux-homme', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bijoux-accessoires'), NULL, 5, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Bijoux femme', 'bijoux-femme', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bijoux-accessoires'), NULL, 6, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Bijoux enfant', 'bijoux-enfant', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bijoux-accessoires'), NULL, 7, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Accessoires cheveux', 'accessoires-cheveux', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bijoux-accessoires'), NULL, 8, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Lunettes', 'lunettes', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bijoux-accessoires'), NULL, 9, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Ceintures', 'ceintures', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bijoux-accessoires'), NULL, 10, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Cravates', 'cravates', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bijoux-accessoires'), NULL, 11, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Nœuds papillon', 'nouds-papillon', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bijoux-accessoires'), NULL, 12, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Foulards', 'foulards', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bijoux-accessoires'), NULL, 13, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Écharpes', 'echarpes', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bijoux-accessoires'), NULL, 14, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Chapeaux', 'chapeaux', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bijoux-accessoires'), NULL, 15, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Casquettes', 'casquettes', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bijoux-accessoires'), NULL, 16, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Bonnets', 'bonnets', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bijoux-accessoires'), NULL, 17, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);

INSERT INTO categories (name, slug, parent_id, description, icon, display_order, is_active)
VALUES ('Montres', 'montres', NULL, 'Montres classiques, connectées et de luxe.', 'bi-watch', 5, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=NULL, description=VALUES(description), icon=VALUES(icon), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Montres homme', 'montres-homme', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='montres'), NULL, 1, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Montres femme', 'montres-femme', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='montres'), NULL, 2, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Montres enfant', 'montres-enfant', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='montres'), NULL, 3, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Montres classiques', 'montres-classiques', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='montres'), NULL, 4, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Montres connectées', 'montres-connectees', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='montres'), NULL, 5, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Montres sport', 'montres-sport', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='montres'), NULL, 6, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Montres de luxe', 'montres-de-luxe', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='montres'), NULL, 7, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Bracelets de montre', 'bracelets-de-montre', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='montres'), NULL, 8, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Accessoires de montre', 'accessoires-de-montre', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='montres'), NULL, 9, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);

INSERT INTO categories (name, slug, parent_id, description, icon, display_order, is_active)
VALUES ('Téléphones & Tablettes', 'telephones-tablettes', NULL, 'Smartphones neufs et reconditionnés, tablettes et accessoires.', 'bi-phone', 6, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=NULL, description=VALUES(description), icon=VALUES(icon), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Smartphones', 'smartphones', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='telephones-tablettes'), NULL, 1, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Téléphones classiques', 'telephones-classiques', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='telephones-tablettes'), NULL, 2, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Téléphones reconditionnés', 'telephones-reconditionnes', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='telephones-tablettes'), NULL, 3, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Tablettes', 'tablettes', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='telephones-tablettes'), NULL, 4, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('iPhone', 'iphone', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='telephones-tablettes'), NULL, 5, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Samsung', 'samsung', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='telephones-tablettes'), NULL, 6, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Xiaomi', 'xiaomi', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='telephones-tablettes'), NULL, 7, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Tecno', 'tecno', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='telephones-tablettes'), NULL, 8, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Infinix', 'infinix', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='telephones-tablettes'), NULL, 9, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Itel', 'itel', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='telephones-tablettes'), NULL, 10, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Oppo', 'oppo', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='telephones-tablettes'), NULL, 11, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Redmi', 'redmi', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='telephones-tablettes'), NULL, 12, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Honor', 'honor', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='telephones-tablettes'), NULL, 13, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Google Pixel', 'google-pixel', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='telephones-tablettes'), NULL, 14, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Accessoires téléphone', 'accessoires-telephone', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='telephones-tablettes'), NULL, 15, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);

INSERT INTO categories (name, slug, parent_id, description, icon, display_order, is_active)
VALUES ('Informatique', 'informatique', NULL, 'PC portables, composants, périphériques et accessoires.', 'bi-laptop', 7, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=NULL, description=VALUES(description), icon=VALUES(icon), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Ordinateurs portables', 'ordinateurs-portables', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='informatique'), NULL, 1, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Ordinateurs de bureau', 'ordinateurs-de-bureau', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='informatique'), NULL, 2, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('PC gaming', 'pc-gaming', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='informatique'), NULL, 3, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Mac', 'mac', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='informatique'), NULL, 4, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Moniteurs', 'moniteurs', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='informatique'), NULL, 5, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Claviers', 'claviers', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='informatique'), NULL, 6, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Souris', 'souris', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='informatique'), NULL, 7, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Tapis de souris', 'tapis-de-souris', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='informatique'), NULL, 8, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Webcams', 'webcams', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='informatique'), NULL, 9, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Microphones', 'microphones', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='informatique'), NULL, 10, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Imprimantes', 'imprimantes', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='informatique'), NULL, 11, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Scanners', 'scanners', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='informatique'), NULL, 12, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Disques durs', 'disques-durs', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='informatique'), NULL, 13, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('SSD', 'ssd', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='informatique'), NULL, 14, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Clés USB', 'cles-usb', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='informatique'), NULL, 15, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Cartes mémoire', 'cartes-memoire', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='informatique'), NULL, 16, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('RAM', 'ram', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='informatique'), NULL, 17, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Composants PC', 'composants-pc', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='informatique'), NULL, 18, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Routeurs', 'routeurs', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='informatique'), NULL, 19, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Modems', 'modems', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='informatique'), NULL, 20, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Onduleurs', 'onduleurs', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='informatique'), NULL, 21, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Accessoires informatiques', 'accessoires-informatiques', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='informatique'), NULL, 22, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);

INSERT INTO categories (name, slug, parent_id, description, icon, display_order, is_active)
VALUES ('Électronique & High-Tech', 'electronique-high-tech', NULL, 'Audio, chargeurs, batteries et gadgets connectés.', 'bi-speaker', 8, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=NULL, description=VALUES(description), icon=VALUES(icon), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Écouteurs', 'ecouteurs', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='electronique-high-tech'), NULL, 1, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Casques audio', 'casques-audio', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='electronique-high-tech'), NULL, 2, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Enceintes Bluetooth', 'enceintes-bluetooth', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='electronique-high-tech'), NULL, 3, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Amplificateurs', 'amplificateurs', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='electronique-high-tech'), NULL, 4, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Chargeurs', 'chargeurs', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='electronique-high-tech'), NULL, 5, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Câbles', 'cables', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='electronique-high-tech'), NULL, 6, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Batteries externes', 'batteries-externes', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='electronique-high-tech'), NULL, 7, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Adaptateurs', 'adaptateurs', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='electronique-high-tech'), NULL, 8, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Hubs USB', 'hubs-usb', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='electronique-high-tech'), NULL, 9, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Gadgets électroniques', 'gadgets-electroniques', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='electronique-high-tech'), NULL, 10, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Objets connectés', 'objets-connectes', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='electronique-high-tech'), NULL, 11, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('GPS', 'gps', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='electronique-high-tech'), NULL, 12, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Accessoires électroniques', 'accessoires-electroniques', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='electronique-high-tech'), NULL, 13, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);

INSERT INTO categories (name, slug, parent_id, description, icon, display_order, is_active)
VALUES ('TV & Home Cinema', 'tv-home-cinema', NULL, 'Téléviseurs, vidéoprojecteurs et home cinéma.', 'bi-tv', 9, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=NULL, description=VALUES(description), icon=VALUES(icon), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Téléviseurs', 'televiseurs', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='tv-home-cinema'), NULL, 1, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Smart TV', 'smart-tv', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='tv-home-cinema'), NULL, 2, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Projecteurs', 'projecteurs', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='tv-home-cinema'), NULL, 3, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Barres de son', 'barres-de-son', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='tv-home-cinema'), NULL, 4, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Home Cinema', 'home-cinema', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='tv-home-cinema'), NULL, 5, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Lecteurs multimédia', 'lecteurs-multimedia', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='tv-home-cinema'), NULL, 6, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Antennes TV', 'antennes-tv', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='tv-home-cinema'), NULL, 7, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Décodeurs', 'decodeurs', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='tv-home-cinema'), NULL, 8, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Télécommandes', 'telecommandes', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='tv-home-cinema'), NULL, 9, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Supports TV', 'supports-tv', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='tv-home-cinema'), NULL, 10, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);

INSERT INTO categories (name, slug, parent_id, description, icon, display_order, is_active)
VALUES ('Jeux vidéo', 'jeux-video', NULL, 'Consoles, jeux, manettes et accessoires gaming.', 'bi-controller', 10, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=NULL, description=VALUES(description), icon=VALUES(icon), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('PlayStation', 'playstation', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='jeux-video'), NULL, 1, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Xbox', 'xbox', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='jeux-video'), NULL, 2, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Nintendo', 'nintendo', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='jeux-video'), NULL, 3, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Consoles', 'consoles', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='jeux-video'), NULL, 4, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Jeux vidéo', 'jeux-video-jeux-video', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='jeux-video'), NULL, 5, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Manettes', 'manettes', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='jeux-video'), NULL, 6, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Casques gaming', 'casques-gaming', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='jeux-video'), NULL, 7, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Volants', 'volants', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='jeux-video'), NULL, 8, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Accessoires gaming', 'accessoires-gaming', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='jeux-video'), NULL, 9, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Cartes cadeaux', 'cartes-cadeaux', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='jeux-video'), NULL, 10, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Chaises gaming', 'chaises-gaming', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='jeux-video'), NULL, 11, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('PC gaming', 'pc-gaming-jeux-video', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='jeux-video'), NULL, 12, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);

INSERT INTO categories (name, slug, parent_id, description, icon, display_order, is_active)
VALUES ('Photo & Vidéo', 'photo-video', NULL, 'Appareils photo, caméras, drones et accessoires.', 'bi-camera', 11, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=NULL, description=VALUES(description), icon=VALUES(icon), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Appareils photo', 'appareils-photo', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='photo-video'), NULL, 1, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Caméras', 'cameras', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='photo-video'), NULL, 2, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Caméras d''action', 'cameras-daction', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='photo-video'), NULL, 3, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Drones', 'drones', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='photo-video'), NULL, 4, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Objectifs', 'objectifs', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='photo-video'), NULL, 5, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Trépieds', 'trepieds', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='photo-video'), NULL, 6, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Flashs', 'flashs', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='photo-video'), NULL, 7, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Batteries', 'batteries', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='photo-video'), NULL, 8, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Cartes mémoire', 'cartes-memoire-photo-video', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='photo-video'), NULL, 9, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Micros', 'micros', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='photo-video'), NULL, 10, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Éclairage studio', 'eclairage-studio', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='photo-video'), NULL, 11, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Accessoires photo', 'accessoires-photo', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='photo-video'), NULL, 12, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);

INSERT INTO categories (name, slug, parent_id, description, icon, display_order, is_active)
VALUES ('Beauté & Cosmétiques', 'beaute-cosmetiques', NULL, 'Maquillage, parfums et soins pour toute la famille.', 'bi-heart', 12, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=NULL, description=VALUES(description), icon=VALUES(icon), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Maquillage', 'maquillage', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='beaute-cosmetiques'), NULL, 1, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Parfums', 'parfums', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='beaute-cosmetiques'), NULL, 2, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Soins du visage', 'soins-du-visage', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='beaute-cosmetiques'), NULL, 3, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Soins du corps', 'soins-du-corps', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='beaute-cosmetiques'), NULL, 4, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Soins des cheveux', 'soins-des-cheveux', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='beaute-cosmetiques'), NULL, 5, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Produits capillaires', 'produits-capillaires', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='beaute-cosmetiques'), NULL, 6, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Produits pour barbe', 'produits-pour-barbe', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='beaute-cosmetiques'), NULL, 7, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Manucure', 'manucure', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='beaute-cosmetiques'), NULL, 8, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Pédicure', 'pedicure', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='beaute-cosmetiques'), NULL, 9, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Déodorants', 'deodorants', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='beaute-cosmetiques'), NULL, 10, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Produits solaires', 'produits-solaires', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='beaute-cosmetiques'), NULL, 11, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Accessoires beauté', 'accessoires-beaute', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='beaute-cosmetiques'), NULL, 12, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Miroirs', 'miroirs', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='beaute-cosmetiques'), NULL, 13, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Appareils de beauté', 'appareils-de-beaute', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='beaute-cosmetiques'), NULL, 14, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);

INSERT INTO categories (name, slug, parent_id, description, icon, display_order, is_active)
VALUES ('Santé & Hygiène', 'sante-hygiene', NULL, 'Hygiène quotidienne et petit matériel de santé.', 'bi-heart-pulse', 13, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=NULL, description=VALUES(description), icon=VALUES(icon), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Hygiène corporelle', 'hygiene-corporelle', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='sante-hygiene'), NULL, 1, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Savons', 'savons', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='sante-hygiene'), NULL, 2, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Shampooings', 'shampooings', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='sante-hygiene'), NULL, 3, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Dentifrices', 'dentifrices', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='sante-hygiene'), NULL, 4, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Soins bucco-dentaires', 'soins-bucco-dentaires', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='sante-hygiene'), NULL, 5, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Produits d''hygiène', 'produits-dhygiene', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='sante-hygiene'), NULL, 6, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Matériel médical', 'materiel-medical', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='sante-hygiene'), NULL, 7, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Thermomètres', 'thermometres', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='sante-hygiene'), NULL, 8, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Premiers secours', 'premiers-secours', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='sante-hygiene'), NULL, 9, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Accessoires de santé', 'accessoires-de-sante', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='sante-hygiene'), NULL, 10, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);

INSERT INTO categories (name, slug, parent_id, description, icon, display_order, is_active)
VALUES ('Maison & Décoration', 'maison-decoration', NULL, 'Décoration intérieure, luminaires et textiles maison.', 'bi-house', 14, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=NULL, description=VALUES(description), icon=VALUES(icon), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Décoration', 'decoration', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='maison-decoration'), NULL, 1, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Tableaux', 'tableaux', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='maison-decoration'), NULL, 2, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Miroirs', 'miroirs-maison-decoration', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='maison-decoration'), NULL, 3, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Tapis', 'tapis', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='maison-decoration'), NULL, 4, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Rideaux', 'rideaux', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='maison-decoration'), NULL, 5, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Coussins', 'coussins', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='maison-decoration'), NULL, 6, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Lampes', 'lampes', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='maison-decoration'), NULL, 7, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Luminaires', 'luminaires', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='maison-decoration'), NULL, 8, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Bougies', 'bougies', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='maison-decoration'), NULL, 9, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Horloges', 'horloges', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='maison-decoration'), NULL, 10, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Décoration murale', 'decoration-murale', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='maison-decoration'), NULL, 11, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Articles décoratifs', 'articles-decoratifs', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='maison-decoration'), NULL, 12, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);

INSERT INTO categories (name, slug, parent_id, description, icon, display_order, is_active)
VALUES ('Meubles', 'meubles', NULL, 'Canapés, lits, tables et rangements.', 'bi-lamp', 15, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=NULL, description=VALUES(description), icon=VALUES(icon), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Canapés', 'canapes', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='meubles'), NULL, 1, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Fauteuils', 'fauteuils', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='meubles'), NULL, 2, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Tables', 'tables', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='meubles'), NULL, 3, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Chaises', 'chaises', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='meubles'), NULL, 4, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Bureaux', 'bureaux', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='meubles'), NULL, 5, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Lits', 'lits', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='meubles'), NULL, 6, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Matelas', 'matelas', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='meubles'), NULL, 7, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Armoires', 'armoires', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='meubles'), NULL, 8, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Commodes', 'commodes', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='meubles'), NULL, 9, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Étagères', 'etageres', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='meubles'), NULL, 10, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Meubles TV', 'meubles-tv', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='meubles'), NULL, 11, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Meubles de cuisine', 'meubles-de-cuisine', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='meubles'), NULL, 12, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);

INSERT INTO categories (name, slug, parent_id, description, icon, display_order, is_active)
VALUES ('Cuisine & Électroménager', 'cuisine-electromenager', NULL, 'Électroménager, cuisson et ustensiles de cuisine.', 'bi-cup-hot', 16, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=NULL, description=VALUES(description), icon=VALUES(icon), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Réfrigérateurs', 'refrigerateurs', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='cuisine-electromenager'), NULL, 1, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Congélateurs', 'congelateurs', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='cuisine-electromenager'), NULL, 2, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Cuisinières', 'cuisinieres', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='cuisine-electromenager'), NULL, 3, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Fours', 'fours', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='cuisine-electromenager'), NULL, 4, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Micro-ondes', 'micro-ondes', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='cuisine-electromenager'), NULL, 5, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Mixeurs', 'mixeurs', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='cuisine-electromenager'), NULL, 6, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Blenders', 'blenders', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='cuisine-electromenager'), NULL, 7, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Friteuses', 'friteuses', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='cuisine-electromenager'), NULL, 8, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Cafetières', 'cafetieres', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='cuisine-electromenager'), NULL, 9, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Bouilloires', 'bouilloires', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='cuisine-electromenager'), NULL, 10, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Grille-pain', 'grille-pain', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='cuisine-electromenager'), NULL, 11, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Robots de cuisine', 'robots-de-cuisine', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='cuisine-electromenager'), NULL, 12, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Vaisselle', 'vaisselle', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='cuisine-electromenager'), NULL, 13, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Casseroles', 'casseroles', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='cuisine-electromenager'), NULL, 14, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Poêles', 'poeles', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='cuisine-electromenager'), NULL, 15, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Ustensiles de cuisine', 'ustensiles-de-cuisine', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='cuisine-electromenager'), NULL, 16, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);

INSERT INTO categories (name, slug, parent_id, description, icon, display_order, is_active)
VALUES ('Entretien & Nettoyage', 'entretien-nettoyage', NULL, 'Aspirateurs, produits ménagers et rangement.', 'bi-stars', 17, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=NULL, description=VALUES(description), icon=VALUES(icon), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Aspirateurs', 'aspirateurs', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='entretien-nettoyage'), NULL, 1, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Balais', 'balais', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='entretien-nettoyage'), NULL, 2, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Serpillières', 'serpillieres', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='entretien-nettoyage'), NULL, 3, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Produits ménagers', 'produits-menagers', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='entretien-nettoyage'), NULL, 4, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Détergents', 'detergents', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='entretien-nettoyage'), NULL, 5, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Désinfectants', 'desinfectants', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='entretien-nettoyage'), NULL, 6, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Accessoires de nettoyage', 'accessoires-de-nettoyage', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='entretien-nettoyage'), NULL, 7, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Rangement', 'rangement', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='entretien-nettoyage'), NULL, 8, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);

INSERT INTO categories (name, slug, parent_id, description, icon, display_order, is_active)
VALUES ('Bébé & Enfants', 'bebe-enfants', NULL, 'Tout pour bébé : couches, poussettes, jouets et soins.', 'bi-balloon', 18, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=NULL, description=VALUES(description), icon=VALUES(icon), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Vêtements bébé', 'vetements-bebe-bebe-enfants', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bebe-enfants'), NULL, 1, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Chaussures bébé', 'chaussures-bebe', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bebe-enfants'), NULL, 2, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Couches', 'couches', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bebe-enfants'), NULL, 3, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Biberons', 'biberons', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bebe-enfants'), NULL, 4, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Poussettes', 'poussettes', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bebe-enfants'), NULL, 5, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Lits bébé', 'lits-bebe', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bebe-enfants'), NULL, 6, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Sièges auto', 'sieges-auto', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bebe-enfants'), NULL, 7, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Produits d''hygiène bébé', 'produits-dhygiene-bebe', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bebe-enfants'), NULL, 8, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Jouets bébé', 'jouets-bebe', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bebe-enfants'), NULL, 9, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Accessoires bébé', 'accessoires-bebe', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bebe-enfants'), NULL, 10, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);

INSERT INTO categories (name, slug, parent_id, description, icon, display_order, is_active)
VALUES ('Jouets & Jeux', 'jouets-jeux', NULL, 'Jouets éducatifs, jeux de société et plein air.', 'bi-puzzle', 19, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=NULL, description=VALUES(description), icon=VALUES(icon), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Jouets éducatifs', 'jouets-educatifs', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='jouets-jeux'), NULL, 1, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Jeux de société', 'jeux-de-societe', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='jouets-jeux'), NULL, 2, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Jeux de construction', 'jeux-de-construction', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='jouets-jeux'), NULL, 3, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Puzzles', 'puzzles', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='jouets-jeux'), NULL, 4, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Peluches', 'peluches', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='jouets-jeux'), NULL, 5, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Voitures miniatures', 'voitures-miniatures', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='jouets-jeux'), NULL, 6, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Poupées', 'poupees', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='jouets-jeux'), NULL, 7, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Jeux créatifs', 'jeux-creatifs', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='jouets-jeux'), NULL, 8, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Jouets électroniques', 'jouets-electroniques', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='jouets-jeux'), NULL, 9, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Jeux d''extérieur', 'jeux-dexterieur', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='jouets-jeux'), NULL, 10, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);

INSERT INTO categories (name, slug, parent_id, description, icon, display_order, is_active)
VALUES ('Sport & Fitness', 'sport-fitness', NULL, 'Équipements fitness, sports collectifs et outdoor.', 'bi-trophy', 20, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=NULL, description=VALUES(description), icon=VALUES(icon), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Vêtements de sport', 'vetements-de-sport-sport-fitness', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='sport-fitness'), NULL, 1, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Chaussures de sport', 'chaussures-de-sport-sport-fitness', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='sport-fitness'), NULL, 2, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Haltères', 'halteres', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='sport-fitness'), NULL, 3, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Bancs de musculation', 'bancs-de-musculation', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='sport-fitness'), NULL, 4, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Tapis de sport', 'tapis-de-sport', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='sport-fitness'), NULL, 5, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Élastiques', 'elastiques', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='sport-fitness'), NULL, 6, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Équipements fitness', 'equipements-fitness', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='sport-fitness'), NULL, 7, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Football', 'football', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='sport-fitness'), NULL, 8, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Basketball', 'basketball', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='sport-fitness'), NULL, 9, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Volleyball', 'volleyball', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='sport-fitness'), NULL, 10, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Tennis', 'tennis', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='sport-fitness'), NULL, 11, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Boxe', 'boxe', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='sport-fitness'), NULL, 12, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Cyclisme', 'cyclisme', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='sport-fitness'), NULL, 13, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Natation', 'natation', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='sport-fitness'), NULL, 14, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Running', 'running', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='sport-fitness'), NULL, 15, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Yoga', 'yoga', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='sport-fitness'), NULL, 16, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);

INSERT INTO categories (name, slug, parent_id, description, icon, display_order, is_active)
VALUES ('Automobile & Moto', 'automobile-moto', NULL, 'Pièces, pneus, accessoires auto et moto.', 'bi-car-front', 21, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=NULL, description=VALUES(description), icon=VALUES(icon), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Pièces automobiles', 'pieces-automobiles', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='automobile-moto'), NULL, 1, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Pneus', 'pneus', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='automobile-moto'), NULL, 2, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Jantes', 'jantes', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='automobile-moto'), NULL, 3, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Huiles', 'huiles', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='automobile-moto'), NULL, 4, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Batteries', 'batteries-automobile-moto', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='automobile-moto'), NULL, 5, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Accessoires voiture', 'accessoires-voiture', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='automobile-moto'), NULL, 6, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Autoradios', 'autoradios', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='automobile-moto'), NULL, 7, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Caméras de recul', 'cameras-de-recul', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='automobile-moto'), NULL, 8, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('GPS', 'gps-automobile-moto', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='automobile-moto'), NULL, 9, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Housses', 'housses', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='automobile-moto'), NULL, 10, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Tapis voiture', 'tapis-voiture', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='automobile-moto'), NULL, 11, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Produits d''entretien automobile', 'produits-dentretien-automobile', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='automobile-moto'), NULL, 12, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Casques moto', 'casques-moto', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='automobile-moto'), NULL, 13, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Accessoires moto', 'accessoires-moto', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='automobile-moto'), NULL, 14, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Pièces moto', 'pieces-moto', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='automobile-moto'), NULL, 15, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);

INSERT INTO categories (name, slug, parent_id, description, icon, display_order, is_active)
VALUES ('Bricolage & Outillage', 'bricolage-outillage', NULL, 'Outillage électrique et manuel pour tous vos travaux.', 'bi-tools', 22, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=NULL, description=VALUES(description), icon=VALUES(icon), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Perceuses', 'perceuses', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bricolage-outillage'), NULL, 1, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Visseuses', 'visseuses', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bricolage-outillage'), NULL, 2, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Meuleuses', 'meuleuses', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bricolage-outillage'), NULL, 3, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Scies', 'scies', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bricolage-outillage'), NULL, 4, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Marteaux', 'marteaux', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bricolage-outillage'), NULL, 5, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Tournevis', 'tournevis', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bricolage-outillage'), NULL, 6, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Clés', 'cles', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bricolage-outillage'), NULL, 7, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Coffrets d''outils', 'coffrets-doutils', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bricolage-outillage'), NULL, 8, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Outillage électrique', 'outillage-electrique', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bricolage-outillage'), NULL, 9, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Outillage manuel', 'outillage-manuel', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bricolage-outillage'), NULL, 10, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Matériel de chantier', 'materiel-de-chantier', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bricolage-outillage'), NULL, 11, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Équipement de sécurité', 'equipement-de-securite', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bricolage-outillage'), NULL, 12, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);

INSERT INTO categories (name, slug, parent_id, description, icon, display_order, is_active)
VALUES ('Électricité & Énergie', 'electricite-energie', NULL, 'Éclairage LED, solaire et matériel électrique.', 'bi-lightning-charge', 23, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=NULL, description=VALUES(description), icon=VALUES(icon), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Ampoules', 'ampoules', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='electricite-energie'), NULL, 1, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Lampes', 'lampes-electricite-energie', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='electricite-energie'), NULL, 2, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('LED', 'led', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='electricite-energie'), NULL, 3, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Interrupteurs', 'interrupteurs', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='electricite-energie'), NULL, 4, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Prises', 'prises', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='electricite-energie'), NULL, 5, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Câbles électriques', 'cables-electriques', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='electricite-energie'), NULL, 6, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Multiprises', 'multiprises', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='electricite-energie'), NULL, 7, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Onduleurs', 'onduleurs-electricite-energie', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='electricite-energie'), NULL, 8, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Batteries', 'batteries-electricite-energie', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='electricite-energie'), NULL, 9, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Panneaux solaires', 'panneaux-solaires', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='electricite-energie'), NULL, 10, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Lampes solaires', 'lampes-solaires', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='electricite-energie'), NULL, 11, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Régulateurs', 'regulateurs', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='electricite-energie'), NULL, 12, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Convertisseurs', 'convertisseurs', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='electricite-energie'), NULL, 13, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);

INSERT INTO categories (name, slug, parent_id, description, icon, display_order, is_active)
VALUES ('Jardinage', 'jardinage', NULL, 'Outils, pots, graines et mobilier de jardin.', 'bi-flower1', 24, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=NULL, description=VALUES(description), icon=VALUES(icon), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Outils de jardinage', 'outils-de-jardinage', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='jardinage'), NULL, 1, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Pots', 'pots', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='jardinage'), NULL, 2, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Jardinières', 'jardinieres', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='jardinage'), NULL, 3, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Graines', 'graines', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='jardinage'), NULL, 4, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Accessoires d''arrosage', 'accessoires-darrosage', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='jardinage'), NULL, 5, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Tuyaux', 'tuyaux', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='jardinage'), NULL, 6, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Mobilier de jardin', 'mobilier-de-jardin', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='jardinage'), NULL, 7, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Éclairage extérieur', 'eclairage-exterieur', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='jardinage'), NULL, 8, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);

INSERT INTO categories (name, slug, parent_id, description, icon, display_order, is_active)
VALUES ('Livres & Éducation', 'livres-education', NULL, 'Livres, manuels scolaires et fournitures.', 'bi-book', 25, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=NULL, description=VALUES(description), icon=VALUES(icon), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Livres', 'livres', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='livres-education'), NULL, 1, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Romans', 'romans', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='livres-education'), NULL, 2, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Livres scolaires', 'livres-scolaires', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='livres-education'), NULL, 3, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Livres professionnels', 'livres-professionnels', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='livres-education'), NULL, 4, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Livres pour enfants', 'livres-pour-enfants', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='livres-education'), NULL, 5, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Dictionnaires', 'dictionnaires', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='livres-education'), NULL, 6, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Manuels', 'manuels', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='livres-education'), NULL, 7, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Cahiers', 'cahiers', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='livres-education'), NULL, 8, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Agendas', 'agendas', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='livres-education'), NULL, 9, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Fournitures scolaires', 'fournitures-scolaires', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='livres-education'), NULL, 10, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);

INSERT INTO categories (name, slug, parent_id, description, icon, display_order, is_active)
VALUES ('Bureau & Fournitures', 'bureau-fournitures', NULL, 'Papeterie, impression et mobilier de bureau.', 'bi-briefcase', 26, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=NULL, description=VALUES(description), icon=VALUES(icon), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Stylos', 'stylos', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bureau-fournitures'), NULL, 1, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Cahiers', 'cahiers-bureau-fournitures', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bureau-fournitures'), NULL, 2, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Classeurs', 'classeurs', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bureau-fournitures'), NULL, 3, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Papier', 'papier', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bureau-fournitures'), NULL, 4, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Imprimantes', 'imprimantes-bureau-fournitures', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bureau-fournitures'), NULL, 5, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Cartouches', 'cartouches', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bureau-fournitures'), NULL, 6, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Agrafeuses', 'agrafeuses', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bureau-fournitures'), NULL, 7, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Organiseurs', 'organiseurs', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bureau-fournitures'), NULL, 8, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Mobilier de bureau', 'mobilier-de-bureau', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bureau-fournitures'), NULL, 9, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Accessoires de bureau', 'accessoires-de-bureau', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='bureau-fournitures'), NULL, 10, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);

INSERT INTO categories (name, slug, parent_id, description, icon, display_order, is_active)
VALUES ('Animaux', 'animaux', NULL, 'Alimentation et accessoires pour vos animaux.', 'bi-emoji-smile', 27, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=NULL, description=VALUES(description), icon=VALUES(icon), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Alimentation animale', 'alimentation-animale', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='animaux'), NULL, 1, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Accessoires chien', 'accessoires-chien', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='animaux'), NULL, 2, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Accessoires chat', 'accessoires-chat', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='animaux'), NULL, 3, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Jouets animaux', 'jouets-animaux', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='animaux'), NULL, 4, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Lits animaux', 'lits-animaux', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='animaux'), NULL, 5, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Colliers', 'colliers-animaux', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='animaux'), NULL, 6, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Laisses', 'laisses', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='animaux'), NULL, 7, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Produits d''hygiène animale', 'produits-dhygiene-animale', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='animaux'), NULL, 8, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Aquariophilie', 'aquariophilie', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='animaux'), NULL, 9, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);

INSERT INTO categories (name, slug, parent_id, description, icon, display_order, is_active)
VALUES ('Alimentation & Épicerie', 'alimentation-epicerie', NULL, 'Épicerie, boissons et produits locaux.', 'bi-basket', 28, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=NULL, description=VALUES(description), icon=VALUES(icon), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Produits alimentaires', 'produits-alimentaires', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='alimentation-epicerie'), NULL, 1, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Boissons', 'boissons', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='alimentation-epicerie'), NULL, 2, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Snacks', 'snacks', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='alimentation-epicerie'), NULL, 3, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Chocolats', 'chocolats', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='alimentation-epicerie'), NULL, 4, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Café', 'cafe', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='alimentation-epicerie'), NULL, 5, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Thé', 'the', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='alimentation-epicerie'), NULL, 6, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Épices', 'epices', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='alimentation-epicerie'), NULL, 7, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Conserves', 'conserves', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='alimentation-epicerie'), NULL, 8, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Produits céréaliers', 'produits-cerealiers', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='alimentation-epicerie'), NULL, 9, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Produits locaux', 'produits-locaux', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='alimentation-epicerie'), NULL, 10, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Produits africains', 'produits-africains', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='alimentation-epicerie'), NULL, 11, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);

INSERT INTO categories (name, slug, parent_id, description, icon, display_order, is_active)
VALUES ('Voyage & Plein air', 'voyage-plein-air', NULL, 'Camping, randonnée et accessoires de voyage.', 'bi-airplane', 29, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=NULL, description=VALUES(description), icon=VALUES(icon), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Tentes', 'tentes', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='voyage-plein-air'), NULL, 1, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Sacs de randonnée', 'sacs-de-randonnee', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='voyage-plein-air'), NULL, 2, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Lampes', 'lampes-voyage-plein-air', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='voyage-plein-air'), NULL, 3, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Équipements camping', 'equipements-camping', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='voyage-plein-air'), NULL, 4, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Accessoires voyage', 'accessoires-voyage', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='voyage-plein-air'), NULL, 5, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Oreillers de voyage', 'oreillers-de-voyage', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='voyage-plein-air'), NULL, 6, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Gourdes', 'gourdes', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='voyage-plein-air'), NULL, 7, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Équipements outdoor', 'equipements-outdoor', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='voyage-plein-air'), NULL, 8, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);

INSERT INTO categories (name, slug, parent_id, description, icon, display_order, is_active)
VALUES ('Cadeaux', 'cadeaux', NULL, 'Cadeaux personnalisés et coffrets pour toutes occasions.', 'bi-gift', 30, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=NULL, description=VALUES(description), icon=VALUES(icon), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Cadeaux personnalisés', 'cadeaux-personnalises', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='cadeaux'), NULL, 1, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Coffrets cadeaux', 'coffrets-cadeaux', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='cadeaux'), NULL, 2, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Articles anniversaire', 'articles-anniversaire', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='cadeaux'), NULL, 3, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Articles mariage', 'articles-mariage', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='cadeaux'), NULL, 4, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Décoration événementielle', 'decoration-evenementielle', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='cadeaux'), NULL, 5, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Cadeaux homme', 'cadeaux-homme', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='cadeaux'), NULL, 6, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Cadeaux femme', 'cadeaux-femme', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='cadeaux'), NULL, 7, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
INSERT INTO categories (name, slug, parent_id, description, display_order, is_active)
VALUES ('Cadeaux enfant', 'cadeaux-enfant', (SELECT id FROM (SELECT * FROM categories) AS p WHERE slug='cadeaux'), NULL, 8, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), parent_id=VALUES(parent_id), display_order=VALUES(display_order), is_active=VALUES(is_active);
