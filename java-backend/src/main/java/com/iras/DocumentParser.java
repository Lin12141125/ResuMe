package com.iras;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.springframework.stereotype.Component;

@Component
public class DocumentParser {
    public record Fragment(int page, int paragraph, String content) {}

    public List<Fragment> parse(String name, byte[] bytes) {
        var pages = new ArrayList<String>();
        try {
            String suffix = name.substring(name.lastIndexOf('.') + 1).toLowerCase(java.util.Locale.ROOT);
            switch (suffix) {
                case "txt", "text" -> pages.add(StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().replace("\uFEFF", ""));
                case "pdf" -> {
                    try (var document = Loader.loadPDF(bytes)) {
                        if (document.getNumberOfPages() > 100) throw new ApiException(400, "PDF exceeds 100 pages");
                        var stripper = new PDFTextStripper();
                        for (int page = 1; page <= document.getNumberOfPages(); page++) {
                            stripper.setStartPage(page);
                            stripper.setEndPage(page);
                            pages.add(stripper.getText(document));
                            if (pages.stream().mapToInt(String::length).sum() > 50000) throw new ApiException(400, "Text exceeds 50000 characters");
                        }
                    }
                }
                case "docx" -> {
                    try (var document = new XWPFDocument(new ByteArrayInputStream(bytes));
                         var extractor = new XWPFWordExtractor(document)) { pages.add(extractor.getText()); }
                }
                default -> throw new ApiException(400, "Only TXT, PDF and DOCX are supported");
            }
        } catch (ApiException error) { throw error; }
        catch (Exception error) { throw new ApiException(400, "Document cannot be read"); }
        if (pages.stream().mapToInt(String::length).sum() > 50000) throw new ApiException(400, "Text exceeds 50000 characters");
        var result = new ArrayList<Fragment>();
        for (int page = 0; page < pages.size(); page++) {
            String text = pages.get(page).strip();
            int paragraph = 1;
            for (int offset = 0; offset < text.length(); offset += 1600) {
                String content = text.substring(offset, Math.min(offset + 1600, text.length()));
                result.add(new Fragment(name.toLowerCase().endsWith(".docx") ? 0 : page + 1, paragraph++, content));
            }
        }
        if (result.isEmpty()) throw new ApiException(400, "No text found; scanned PDFs require OCR");
        return result;
    }
}