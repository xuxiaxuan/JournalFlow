package com.journalflow.service;

import com.journalflow.entity.ManuscriptVersion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** 从已上传稿件中提取供大模型使用的真实文本，解析失败时由调用方降级。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManuscriptTextExtractor {
    private static final int MAX_EXTRACTED_CHARS = 30_000;

    private final FileStorageService fileStorage;

    public String extract(ManuscriptVersion version) {
        if (version == null || version.getFilePath() == null) return "";
        Path path = fileStorage.resolve(version.getFilePath());
        if (!Files.isRegularFile(path)) return "";
        String name = version.getFileName() == null ? path.getFileName().toString() : version.getFileName();
        try {
            String text = switch (extension(name)) {
                case "pdf" -> extractPdf(path);
                case "docx" -> extractDocx(path);
                case "tex" -> Files.readString(path, StandardCharsets.UTF_8);
                default -> "";
            };
            return limit(normalize(text));
        } catch (Exception e) {
            log.warn("稿件版本 {} 文本提取失败，降级使用表单元数据: {}", version.getId(), e.getMessage());
            return "";
        }
    }

    private String extractPdf(Path path) throws Exception {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    private String extractDocx(Path path) throws Exception {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            ZipEntry documentXml = zip.getEntry("word/document.xml");
            if (documentXml == null) return "";
            XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
            StringBuilder text = new StringBuilder();
            try (InputStream input = zip.getInputStream(documentXml)) {
                XMLStreamReader reader = factory.createXMLStreamReader(input);
                while (reader.hasNext() && text.length() < MAX_EXTRACTED_CHARS) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                        text.append(reader.getText());
                    } else if (event == XMLStreamConstants.END_ELEMENT && "p".equals(reader.getLocalName())) {
                        text.append('\n');
                    }
                }
                reader.close();
            }
            return text.toString();
        }
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value.replace('\u0000', ' ')
                .replaceAll("[\\t\\x0B\\f\\r ]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String limit(String value) {
        return value.length() <= MAX_EXTRACTED_CHARS ? value : value.substring(0, MAX_EXTRACTED_CHARS);
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
