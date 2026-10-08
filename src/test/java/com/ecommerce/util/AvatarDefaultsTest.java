package com.ecommerce.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Photos de profil : visuels réservés + validation d'URL.
 * Aucune DB, aucun fichier requis.
 */
class AvatarDefaultsTest {

    @Test
    void defautsToujoursRenseignes() {
        assertNotNull(AvatarDefaults.DEFAULT_SUPPLIER_LOGO);
        assertNotNull(AvatarDefaults.DEFAULT_CLIENT_AVATAR);
        assertFalse(AvatarDefaults.DEFAULT_SUPPLIER_LOGO.trim().isEmpty());
        assertFalse(AvatarDefaults.DEFAULT_CLIENT_AVATAR.trim().isEmpty());
    }

    @Test
    void logoFournisseurOuReserve() {
        assertEquals(AvatarDefaults.DEFAULT_SUPPLIER_LOGO,
            AvatarDefaults.supplierLogoOrDefault(null));
        assertEquals(AvatarDefaults.DEFAULT_SUPPLIER_LOGO,
            AvatarDefaults.supplierLogoOrDefault("   "));
        assertEquals("uploads/logos/abc.jpg",
            AvatarDefaults.supplierLogoOrDefault("uploads/logos/abc.jpg"));
    }

    @Test
    void avatarClientOuReserve() {
        assertEquals(AvatarDefaults.DEFAULT_CLIENT_AVATAR,
            AvatarDefaults.customerAvatarOrDefault(null));
        assertEquals(AvatarDefaults.DEFAULT_CLIENT_AVATAR,
            AvatarDefaults.customerAvatarOrDefault(""));
        assertEquals("uploads/avatars/x.png",
            AvatarDefaults.customerAvatarOrDefault("uploads/avatars/x.png"));
    }

    @Test
    void urlsAcceptees() {
        assertTrue(AvatarDefaults.isAcceptableImageUrl("uploads/avatars/a.jpg"));
        assertTrue(AvatarDefaults.isAcceptableImageUrl("assets/img/default-client.svg"));
        assertTrue(AvatarDefaults.isAcceptableImageUrl("https://example.com/p.png"));
        assertTrue(AvatarDefaults.isAcceptableImageUrl("http://example.com/p.png"));
    }

    @Test
    void urlsDangereusesRefusees() {
        assertFalse(AvatarDefaults.isAcceptableImageUrl(null));
        assertFalse(AvatarDefaults.isAcceptableImageUrl(""));
        assertFalse(AvatarDefaults.isAcceptableImageUrl("javascript:alert(1)"));
        assertFalse(AvatarDefaults.isAcceptableImageUrl("data:image/png;base64,xx"));
        assertFalse(AvatarDefaults.isAcceptableImageUrl("vbscript:x"));
        assertFalse(AvatarDefaults.isAcceptableImageUrl("uploads/../../etc/passwd"));
        assertFalse(AvatarDefaults.isAcceptableImageUrl("uploads\\evil.jpg"));
        assertFalse(AvatarDefaults.isAcceptableImageUrl("<img src=x>"));
        StringBuilder longUrl = new StringBuilder("uploads/");
        for (int i = 0; i < 600; i++) longUrl.append('a');
        assertFalse(AvatarDefaults.isAcceptableImageUrl(longUrl.toString()));
    }
}
