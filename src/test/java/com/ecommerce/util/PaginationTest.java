package com.ecommerce.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Non-régression Phase 20 (VULN-002) : pagination hostile bornée serveur.
 * page/limit négatifs ou énormes ne doivent jamais produire d'OFFSET
 * négatif ni de dump complet (erreur 500 observée avant correction).
 */
class PaginationTest {

    @Test
    void pageNegativeOuNulleRameneeAUn() {
        assertEquals(1, Pagination.clampPage(-1));
        assertEquals(1, Pagination.clampPage(0));
        assertEquals(1, Pagination.clampPage(1));
        assertEquals(3, Pagination.clampPage(3));
    }

    @Test
    void limitBorneeMinMax() {
        assertEquals(1, Pagination.clampLimit(-5, 50));
        assertEquals(1, Pagination.clampLimit(0, 50));
        assertEquals(1, Pagination.clampLimit(1, 50));
        assertEquals(12, Pagination.clampLimit(12, 50));
        assertEquals(50, Pagination.clampLimit(50, 50));
        assertEquals(50, Pagination.clampLimit(999999999, 50));
    }

    @Test
    void limitAdminBorneeCent() {
        assertEquals(100, Pagination.clampLimit(9999, 100));
        assertEquals(1, Pagination.clampLimit(-1, 100));
    }

    @Test
    void offsetToujoursPositif() {
        // offset = (page-1) * limit : jamais négatif quelles que soient les entrées
        for (int page : new int[]{-100, -1, 0, 1, 999999}) {
            for (int limit : new int[]{-50, 0, 1, 500000}) {
                int p = Pagination.clampPage(page);
                int l = Pagination.clampLimit(limit, 50);
                assertTrue((p - 1) * l >= 0, "offset négatif pour page=" + page + " limit=" + limit);
            }
        }
    }
}
