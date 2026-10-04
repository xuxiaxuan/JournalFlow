package com.journalflow.service;

import com.journalflow.config.JournalProperties;
import com.journalflow.entity.ManuscriptVersion;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManuscriptTextExtractorTest {
    @TempDir Path tempDir;

    @Test
    void extractsRealPdfText() throws Exception {
        Path pdf = tempDir.resolve("paper.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText("Real manuscript body and references");
                content.endText();
            }
            document.save(pdf.toFile());
        }

        String text = extractor().extract(version("paper.pdf", "paper.pdf"));
        assertTrue(text.contains("Real manuscript body and references"));
    }

    @Test
    void extractsDocxDocumentXmlText() throws Exception {
        Path docx = tempDir.resolve("paper.docx");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(docx))) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(("<?xml version=\"1.0\"?><w:document xmlns:w=\"urn:test\"><w:body>"
                    + "<w:p><w:r><w:t>First paragraph</w:t></w:r></w:p>"
                    + "<w:p><w:r><w:t>Second paragraph</w:t></w:r></w:p>"
                    + "</w:body></w:document>").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        String text = extractor().extract(version("paper.docx", "paper.docx"));
        assertTrue(text.contains("First paragraph"));
        assertTrue(text.contains("Second paragraph"));
    }

    @Test
    void unsupportedLegacyDocFallsBackWithoutInventingText() throws Exception {
        Files.write(tempDir.resolve("paper.doc"), new byte[]{1, 2, 3});
        assertEquals("", extractor().extract(version("paper.doc", "paper.doc")));
    }

    private ManuscriptTextExtractor extractor() {
        JournalProperties properties = new JournalProperties();
        properties.setUploadDir(tempDir.toString());
        return new ManuscriptTextExtractor(new FileStorageService(properties));
    }

    private ManuscriptVersion version(String path, String name) {
        ManuscriptVersion version = new ManuscriptVersion();
        version.setId(1L);
        version.setFilePath(path);
        version.setFileName(name);
        return version;
    }
}
