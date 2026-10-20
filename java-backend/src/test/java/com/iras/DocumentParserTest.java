package com.iras;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DocumentParserTest {
    @Test
    void textHasEvidenceAndInvalidDocumentsFail() {
        var parser = new DocumentParser();
        var result = parser.parse("synthetic.txt", "Synthetic Java experience".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().page()).isEqualTo(1);
        assertThatThrownBy(() -> parser.parse("empty.txt", new byte[0])).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> parser.parse("bad.exe", new byte[]{1})).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> parser.parse("bad.pdf", new byte[]{1})).isInstanceOf(ApiException.class);
    }

    @Test
    void pdfAndDocxTextIsExtractedAndScannedPdfFailsClearly() throws Exception {
        var parser = new DocumentParser();
        var pdfBytes = new java.io.ByteArrayOutputStream();
        try (var document = new org.apache.pdfbox.pdmodel.PDDocument()) {
            var page = new org.apache.pdfbox.pdmodel.PDPage();
            document.addPage(page);
            try (var content = new org.apache.pdfbox.pdmodel.PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText("Synthetic Java PostgreSQL candidate");
                content.endText();
            }
            document.save(pdfBytes);
        }
        assertThat(parser.parse("synthetic.pdf", pdfBytes.toByteArray()).getFirst().content()).contains("Java PostgreSQL");
        var docxBytes = new java.io.ByteArrayOutputStream();
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument()) {
            document.createParagraph().createRun().setText("Synthetic Java DOCX candidate");
            document.write(docxBytes);
        }
        var fragment = parser.parse("synthetic.docx", docxBytes.toByteArray()).getFirst();
        assertThat(fragment.content()).contains("Java DOCX");
        assertThat(fragment.page()).isZero();
        var blankBytes = new java.io.ByteArrayOutputStream();
        try (var document = new org.apache.pdfbox.pdmodel.PDDocument()) {
            document.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            document.save(blankBytes);
        }
        assertThatThrownBy(() -> parser.parse("scanned.pdf", blankBytes.toByteArray())).isInstanceOf(ApiException.class).hasMessageContaining("OCR");
    }

    @Test
    void workIntervalsAreMerged() throws Exception {
        var input = new ObjectMapper().readTree("{\"工作经历\":[{\"时间\":\"2020-2023\"},{\"时间\":\"2022-2024\"}]}");
        assertThat(ResumeService.workYears(input)).isEqualTo(4);
    }
}