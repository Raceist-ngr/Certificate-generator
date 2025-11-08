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

public class App {

    // -------- PAGE GEOMETRY (A4 LANDSCAPE) --------
    private static final PDRectangle LANDSCAPE_A4 =
            new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()); // ~842 x 595

    private static final float PAGE_WIDTH = LANDSCAPE_A4.getWidth();
    private static final float CENTER_X = PAGE_WIDTH / 2f;

    // -------- YOUR FINAL ALIGNMENTS --------
    private static float NAME_X = CENTER_X;
    private static float NAME_Y = 250f;

    private static float COURSE_X = CENTER_X;
    private static float COURSE_Y = 160f;

    private static float DATE_X = 250f;
    private static float DATE_Y = 100f;

    private static float CERTID_X = 40f;
    private static float CERTID_Y = 40f;

    // -------- FONT SIZES --------
    private static float NAME_SIZE = 36f;
    private static float COURSE_SIZE = 22f;
    private static float DATE_SIZE = 16f;
    private static float ID_SIZE = 12f;

    // -------- RESOURCES (GUI CAN OVERRIDE THESE) --------
    private static String TEMPLATE_IMG_PATH = "/templates/certificate-template.png";
    private static String FONT_TTF_PATH = "/fonts/NotoSerif-Regular.ttf";

    public static void setTemplatePath(String path) { TEMPLATE_IMG_PATH = path; }
    public static void setFontPath(String path) { FONT_TTF_PATH = path; }

    public static void main(String[] args) {
        Map<String, String> cli = parseArgs(args);
        Path outDir = Paths.get(cli.getOrDefault("outDir", "out"));

        try {
            Files.createDirectories(outDir);

            if (cli.containsKey("calibrate")) {
                makeCalibrationSheet(outDir);
                System.out.println("Calibration sheet saved to " + outDir.resolve("calibrate.pdf"));
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
        }
        catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // -------- GENERATE ONE CERTIFICATE --------
    public static void generateCertificate(String name, String course, String date, String certId, Path outputPath) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(LANDSCAPE_A4);
            doc.addPage(page);

            PDType0Font font = PDType0Font.load(doc, openResourceOrFile(FONT_TTF_PATH), true);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {

                try (InputStream imgStream = openResourceOrFile(TEMPLATE_IMG_PATH)) {
                    PDImageXObject bg = PDImageXObject.createFromByteArray(doc, imgStream.readAllBytes(), "template");
                    cs.drawImage(bg, 0, 0, PAGE_WIDTH, LANDSCAPE_A4.getHeight());
                }

                drawText(cs, font, NAME_SIZE, NAME_X, NAME_Y, name, true);
                drawText(cs, font, COURSE_SIZE, COURSE_X, COURSE_Y, course, true);
                drawText(cs, font, DATE_SIZE, DATE_X, DATE_Y, date, false);
                drawText(cs, font, ID_SIZE, CERTID_X, CERTID_Y, certId, false);
            }

            Files.createDirectories(outputPath.getParent());
            doc.save(outputPath.toFile());
        }
    }

    // -------- BULK CSV --------
    private static void bulkFromCsv(String csvPath, Path outDir) throws Exception {
        try (CSVReader reader = openCsv(csvPath)) {
            String[] headers = reader.readNext();
            if (headers == null) throw new IllegalArgumentException("CSV is empty.");

            Map<String, Integer> idx = indexMap(headers, "name", "course", "date", "certId");

            String[] row;
            while ((row = reader.readNext()) != null) {
                String name = row[idx.get("name")].trim();
                String course = row[idx.get("course")].trim();
                String date = row[idx.get("date")].trim();
                String certId = row[idx.get("certId")].trim();

                Path out = outDir.resolve(safeFile("Certificate_" + name + ".pdf"));
                generateCertificate(name, course, date, certId, out);
                System.out.println("Saved: " + out.toAbsolutePath());
            }
        }
    }

    // -------- CALIBRATION --------
    private static void makeCalibrationSheet(Path outDir) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(LANDSCAPE_A4);
            doc.addPage(page);

            PDType0Font font = PDType0Font.load(doc, openResourceOrFile(FONT_TTF_PATH), true);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float w = PAGE_WIDTH;
                float h = LANDSCAPE_A4.getHeight();

                cs.setLineWidth(0.2f);
                for (float x = 0; x <= w; x += 50) { cs.moveTo(x, 0); cs.lineTo(x, h); }
                for (float y = 0; y <= h; y += 50) { cs.moveTo(0, y); cs.lineTo(w, y); }
                cs.stroke();

                marker(cs, font, "NAME", NAME_X, NAME_Y);
                marker(cs, font, "COURSE", COURSE_X, COURSE_Y);
                marker(cs, font, "DATE", DATE_X, DATE_Y);
                marker(cs, font, "ID", CERTID_X, CERTID_Y);
            }

            doc.save(outDir.resolve("calibrate.pdf").toFile());
        }
    }

    // -------- DRAW HELPERS --------
    private static void drawText(PDPageContentStream cs, PDType0Font font, float size,
                                 float x, float y, String text, boolean center) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        if (center) x -= (font.getStringWidth(text) / 1000f * size) / 2f;
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }

    private static void marker(PDPageContentStream cs, PDType0Font font, String label, float x, float y) throws IOException {
        cs.setLineWidth(0.7f);
        cs.moveTo(x-5, y-5); cs.lineTo(x+5, y+5);
        cs.moveTo(x-5, y+5); cs.lineTo(x+5, y-5);
        cs.stroke();

        cs.beginText();
        cs.setFont(font, 12);
        cs.newLineAtOffset(x+8, y+8);
        cs.showText(label);
        cs.endText();
    }

    // -------- FILE / CSV HELPERS --------
    private static InputStream openResourceOrFile(String path) throws IOException {
        InputStream in = App.class.getResourceAsStream(path);
        if (in != null) return in;
        return Files.newInputStream(Paths.get(path.replaceFirst("^/", "")));
    }

    private static CSVReader openCsv(String path) throws IOException {
        Path p = Paths.get(path);
        if (Files.exists(p)) return new CSVReader(Files.newBufferedReader(p));
        InputStream in = openResourceOrFile(path);
        return new CSVReader(new InputStreamReader(in));
    }

    private static Map<String, Integer> indexMap(String[] headers, String... required) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headers.length; i++)
            map.put(headers[i].trim().toLowerCase(Locale.ROOT), i);
        return map;
    }

    private static String safeFile(String s) { return s.replaceAll("[^a-zA-Z0-9._-]", "_"); }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (String a : args) {
            if (a.startsWith("--")) {
                String[] kv = a.substring(2).split("=", 2);
                map.put(kv[0], (kv.length == 2) ? kv[1] : "true");
            }
        }
        return map;
    }
}
