package com.ecommerce.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 15 — Uploads : seules les signatures bitmap réelles passent.
 */
class UploadSecurityTest {

    private static byte[] padded(byte... head) {
        byte[] b = new byte[16];
        System.arraycopy(head, 0, b, 0, head.length);
        return b;
    }

    @Test
    void signaturesValidesAcceptees() {
        assertTrue(UploadUtil.contentMatches(
            padded((byte) 0xFF, (byte) 0xD8, (byte) 0xFF), ".jpg"));
        assertTrue(UploadUtil.contentMatches(
            padded((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G'), ".png"));
        assertTrue(UploadUtil.contentMatches(
            padded((byte) 'G', (byte) 'I', (byte) 'F', (byte) '8'), ".gif"));
        assertTrue(UploadUtil.contentMatches(new byte[]{
            'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 0, 0, 0, 0}, ".webp"));
    }

    @Test
    void fauxJpgScriptOuHtmlRefuse() {
        assertFalse(UploadUtil.contentMatches(
            padded((byte) '<', (byte) 's', (byte) 'c'), ".jpg"));
        assertFalse(UploadUtil.contentMatches(
            padded((byte) '#', (byte) '!', (byte) '/'), ".png"));
        // PNG servi comme .jpg : refusé (extension incohérente).
        assertFalse(UploadUtil.contentMatches(
            padded((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G'), ".jpg"));
    }

    @Test
    void entreesLimitesEtSvgRefuse() {
        assertFalse(UploadUtil.contentMatches(null, ".jpg"));
        assertFalse(UploadUtil.contentMatches(new byte[]{1, 2, 3}, ".jpg"));
        assertFalse(UploadUtil.contentMatches(
            padded((byte) '<', (byte) 's', (byte) 'v'), ".svg"));
        assertFalse(UploadUtil.contentMatches(
            padded((byte) 0xFF, (byte) 0xD8, (byte) 0xFF), ".bmp"));
    }
}
