package com.example.certgen;

import com.opencsv.CSVReader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

/**
 * Certificate Generator (A4 Landscape)
 *
 * Usage:
 *   --calibrate                       Create out/calibrate.pdf with grid and markers
 *   --csv=pathOrClasspath             Bulk generate from CSV with headers: name,course,date,certId
 *   --name=... --course=... --date=... --id=...   Single certificate mode
 *   --outDir=outputFolder             Optional output folder (default: out)
 *
 * Resources required (classpath or filesystem):
 *   /templates/certificate-template.png
 *   /fonts/NotoSerif-Regular.ttf  (Unicode-safe font)
 *
 * Coordinates are in PDF points (A4 landscape ~ 842 x 595). Origin: bottom-left.
 */
public class App {

    // ---------- PAGE GEOMETRY (A4 LANDSCAPE) ----------
    // ---------- PAGE GEOMETRY (A4 LANDSCAPE) ----------
private static final PDRectangle LANDSCAPE_A4 =
        new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()); // 842 x 595

private static final float PAGE_WIDTH = LANDSCAPE_A4.getWidth();   // ~842
private static final float PAGE_HEIGHT = LANDSCAPE_A4.getHeight(); // ~595
private static final float CENTER_X = PAGE_WIDTH / 2f;

// ---------- COORDINATES tuned for your template ----------
private static float NAME_X = CENTER_X;  // centered
private static float NAME_Y = 250f;

private static float COURSE_X = CENTER_X; // centered
private static float COURSE_Y = 160f;

private static float DATE_X = 250f;  // near "Date awarded:"
private static float DATE_Y = 100f;

private static float CERTID_X = 40f;  // optional bottom-left
private static float CERTID_Y = 40f;

// ---------- FONT SIZES ----------
private static float NAME_SIZE = 36f;
private static float COURSE_SIZE = 22f;
private static float DATE_SIZE = 16f;
private static float ID_SIZE = 12f;

// ---------- RESOURCES ----------
private static final String TEMPLATE_IMG = "/templates/certificate-template.png";
private static final String FONT_TTF   = "/fonts/NotoSerif-Regular.ttf";


    public static void main(String[] args) {
        Map<String, String> cli = parseArgs(args);
        Path outDir = Paths.get(cli.getOrDefault("outDir", "out"));

        try {
            Files.createDirectories(outDir);

            if (cli.containsKey("calibrate")) {
                makeCalibrationSheet(outDir);
                System.out.println("Calibration sheet written to " + outDir.resolve("calibrate.pdf").toAbsolutePath());
                return;
            }

            if (cli.containsKey("csv")) {
                bulkFromCsv(cli.get("csv"), outDir);
                System.out.println("Bulk generation complete.");
                return;
            }

            // Single certificate mode
            String name = cli.getOrDefault("name", "Student Name");
            String course = cli.getOrDefault("course", "Course Title");
            String date = cli.getOrDefault("date", LocalDate.now().toString());
            String certId = cli.getOrDefault("id", UUID.randomUUID().toString());

            Path out = outDir.resolve(safeFile("Certificate_" + name + ".pdf"));
            generateCertificate(name, course, date, certId, out);
            System.out.println("Saved: " + out.toAbsolutePath());

        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    // --------------------- CORE GENERATION ---------------------

    public static void generateCertificate(String name, String course, String date,
                                           String certId, Path outputPath) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(LANDSCAPE_A4);
            doc.addPage(page);

            // Font (Unicode-capable)
            PDType0Font font;
            try (InputStream fontStream = openResourceOrFile(FONT_TTF)) {
                if (fontStream == null) throw new FileNotFoundException("Font not found at " + FONT_TTF);
                font = PDType0Font.load(doc, fontStream, true);
            }

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                // Background template image
                try (InputStream imgStream = openResourceOrFile(TEMPLATE_IMG)) {
                    if (imgStream != null) {
                        PDImageXObject bg = PDImageXObject.createFromByteArray(doc, imgStream.readAllBytes(), "template");
                        cs.drawImage(bg, 0, 0, PAGE_WIDTH, PAGE_HEIGHT);
                    } else {
                        // Fallback border if template missing
                        drawFallbackBorder(cs, LANDSCAPE_A4);
                    }
                }

                // Draw text fields
                drawText(cs, font, NAME_SIZE, NAME_X, NAME_Y, name, true);
                drawText(cs, font, COURSE_SIZE, COURSE_X, COURSE_Y, course, true);
                drawText(cs, font, DATE_SIZE, DATE_X, DATE_Y, date, true);
                drawText(cs, font, ID_SIZE, CERTID_X, CERTID_Y, "ID: " + certId, false);
            }

            Files.createDirectories(outputPath.getParent());
            doc.save(outputPath.toFile());
        }
    }

    private static void bulkFromCsv(String csvResourceOrPath, Path outDir) throws Exception {
        try (CSVReader reader = openCsv(csvResourceOrPath)) {
            String[] headers = reader.readNext();
            if (headers == null) throw new IllegalArgumentException("Empty CSV.");
            Map<String, Integer> idx = indexMap(headers, "name", "course", "date", "certId");

            String[] row;
            while ((row = reader.readNext()) != null) {
                String name = cell(row, idx.get("name"), "Student");
                String course = cell(row, idx.get("course"), "Course");
                String date = cell(row, idx.get("date"), LocalDate.now().toString());
                String certId = cell(row, idx.get("certId"), UUID.randomUUID().toString());

                Path out = outDir.resolve(safeFile("Certificate_" + name + ".pdf"));
                generateCertificate(name, course, date, certId, out);
                System.out.println("Saved: " + out.toAbsolutePath());
            }
        }
    }

    // --------------------- DRAW HELPERS ---------------------

    private static void drawText(PDPageContentStream cs, PDType0Font font, float size,
                                 float x, float y, String text, boolean center) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        float tx = x;
        if (center) {
            float width = font.getStringWidth(text) / 1000f * size;
            tx = x - width / 2f;
        }
        cs.newLineAtOffset(tx, y);
        cs.showText(text);
        cs.endText();
    }

    private static void drawFallbackBorder(PDPageContentStream cs, PDRectangle box) throws IOException {
        cs.setLineWidth(2f);
        cs.moveTo(20, 20);
        cs.lineTo(box.getWidth() - 20, 20);
        cs.lineTo(box.getWidth() - 20, box.getHeight() - 20);
        cs.lineTo(20, box.getHeight() - 20);
        cs.closePath();
        cs.stroke();
    }

    private static void makeCalibrationSheet(Path outDir) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(LANDSCAPE_A4);
            doc.addPage(page);

            PDType0Font font;
            try (InputStream fontStream = openResourceOrFile(FONT_TTF)) {
                if (fontStream == null) throw new FileNotFoundException("Font not found at " + FONT_TTF);
                font = PDType0Font.load(doc, fontStream, true);
            }

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float w = PAGE_WIDTH, h = PAGE_HEIGHT;

                // Light grid every 50pt
                cs.setLineWidth(0.2f);
                for (float x = 0; x <= w; x += 50) { cs.moveTo(x, 0); cs.lineTo(x, h); }
                for (float y = 0; y <= h; y += 50) { cs.moveTo(0, y); cs.lineTo(w, y); }
                cs.stroke();

                marker(cs, font, "NAME", NAME_X, NAME_Y);
                marker(cs, font, "COURSE", COURSE_X, COURSE_Y);
                marker(cs, font, "DATE", DATE_X, DATE_Y);
                marker(cs, font, "ID", CERTID_X, CERTID_Y);
            }

            Path out = outDir.resolve("calibrate.pdf");
            Files.createDirectories(out.getParent());
            doc.save(out.toFile());
        }
    }

    private static void marker(PDPageContentStream cs, PDType0Font font, String label, float x, float y) throws IOException {
        cs.setLineWidth(1f);
        cs.moveTo(x - 5, y - 5); cs.lineTo(x + 5, y + 5);
        cs.moveTo(x - 5, y + 5); cs.lineTo(x + 5, y - 5);
        cs.stroke();

        cs.beginText();
        cs.setFont(font, 12);
        cs.newLineAtOffset(x + 8, y + 8);
        cs.showText(label + " @ (" + (int)x + "," + (int)y + ")");
        cs.endText();
    }

    // --------------------- IO HELPERS ---------------------

    private static InputStream openResourceOrFile(String pathOrResource) {
        // Try classpath first
        InputStream in = App.class.getResourceAsStream(pathOrResource);
        if (in != null) return in;

        // If not classpath, try filesystem
        try {
            Path p = Paths.get(pathOrResource.replaceFirst("^/", "")); // tolerate leading slash
            if (Files.exists(p)) return Files.newInputStream(p);
        } catch (Exception ignored) {
        }
        return null;
    }

    private static CSVReader openCsv(String resourceOrPath) throws IOException {
        // Try FS first (so you can pass an absolute or relative path easily)
        Path p = Paths.get(resourceOrPath);
        if (Files.exists(p)) {
            return new CSVReader(Files.newBufferedReader(p));
        }
        // Then classpath (e.g. /data/recipients.csv)
        InputStream in = openResourceOrFile(resourceOrPath);
        if (in == null) throw new FileNotFoundException("CSV not found: " + resourceOrPath);
        return new CSVReader(new InputStreamReader(in));
    }

    private static Map<String, Integer> indexMap(String[] headers, String... required) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            map.put(headers[i].trim().toLowerCase(Locale.ROOT), i);
        }
        for (String r : required) {
            if (!map.containsKey(r)) throw new IllegalArgumentException("CSV missing column: " + r);
        }
        return map;
    }

    private static String cell(String[] row, int idx, String def) {
        if (idx < 0 || idx >= row.length) return def;
        String v = row[idx];
        return (v == null || v.isBlank()) ? def : v.trim();
    }

    private static String safeFile(String s) {
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (String a : args) {
            if (a.startsWith("--")) {
                String[] kv = a.substring(2).split("=", 2);
                map.put(kv[0], kv.length == 2 ? kv[1] : "true");
            }
        }
        return map;
    }
}
