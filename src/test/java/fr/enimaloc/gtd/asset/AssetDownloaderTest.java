package fr.enimaloc.gtd.asset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AssetDownloaderTest {

    @Test
    void convertsPngToWebp(@TempDir Path dir) throws IOException {
        // Créer une petite image PNG source
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        Path pngFile = dir.resolve("source.png");
        ImageIO.write(img, "png", pngFile.toFile());

        Path dest = dir.resolve("out.webp");
        Path result = AssetDownloader.download(pngFile.toUri().toString(), dest);

        assertNotNull(result);
        assertEquals(dest, result); // must be the .webp destination, not fallback
        assertTrue(Files.exists(result));
        BufferedImage read = ImageIO.read(result.toFile());
        assertNotNull(read);
    }

    @Test
    void skipsIfDestinationExists(@TempDir Path dir) throws IOException {
        Path dest = dir.resolve("existing.webp");
        Files.writeString(dest, "already-there");

        // Ne doit pas faire de requête HTTP ni modifier le fichier
        Path result = AssetDownloader.download("http://192.0.2.0/unreachable.png", dest);

        assertEquals(dest, result);
        assertEquals("already-there", Files.readString(dest));
    }

    @Test
    void fallsBackToOriginalFormatOnFailure(@TempDir Path dir) throws IOException {
        // Fichier binaire non-image
        Path binFile = dir.resolve("data.bin");
        Files.write(binFile, new byte[]{0x00, 0x01, 0x02, 0x03});

        Path dest = dir.resolve("data.webp");
        Path result = AssetDownloader.download(binFile.toUri().toString(), dest);

        // Doit retourner un chemin valide (avec une extension différente)
        assertNotNull(result);
        assertTrue(Files.exists(result));
        // Le fichier .webp ne doit pas exister (on a utilisé l'extension originale)
        assertFalse(Files.exists(dest));
    }
}
