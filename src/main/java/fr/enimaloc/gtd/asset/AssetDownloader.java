package fr.enimaloc.gtd.asset;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

public class AssetDownloader {

    /**
     * Télécharge l'URL donnée et tente de convertir en WebP.
     * Si la conversion échoue (non-image ou format non supporté), sauvegarde l'original
     * avec l'extension d'origine à côté de webpDestination.
     * Si webpDestination existe déjà, retourne immédiatement sans télécharger.
     *
     * @return le chemin effectivement écrit, ou null en cas d'erreur réseau
     */
    public static Path download(String url, Path webpDestination) {
        if (Files.exists(webpDestination)) return webpDestination;

        byte[] bytes;
        try (var in = URI.create(url).toURL().openStream()) {
            bytes = in.readAllBytes();
        } catch (IOException e) {
            System.err.println("[AssetDownloader] Impossible de télécharger " + url + " : " + e.getMessage());
            return null;
        }

        Path parent = webpDestination.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                System.err.println("[AssetDownloader] Impossible de créer le dossier " + parent + " : " + e.getMessage());
                return null;
            }
        }

        // Tentative de conversion WebP
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img != null && ImageIO.write(img, "webp", webpDestination.toFile())) {
                return webpDestination;
            }
        } catch (IOException ignored) {}

        // Fallback : sauvegarder avec l'extension d'origine
        String originalExt = originalExtension(url);
        String name = webpDestination.getFileName().toString();
        String baseName = name.endsWith(".webp") ? name.substring(0, name.length() - 5) : name;
        Path fallback = webpDestination.resolveSibling(baseName + originalExt);
        try {
            Files.write(fallback, bytes);
            return fallback;
        } catch (IOException e) {
            System.err.println("[AssetDownloader] Impossible d'écrire " + fallback + " : " + e.getMessage());
            return null;
        }
    }

    private static String originalExtension(String url) {
        String path = url.contains("?") ? url.substring(0, url.indexOf('?')) : url;
        int dot = path.lastIndexOf('.');
        return (dot >= 0 && dot > path.lastIndexOf('/')) ? path.substring(dot) : ".bin";
    }
}
