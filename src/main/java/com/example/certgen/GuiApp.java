package com.example.certgen;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.UUID;

public class GuiApp {
    public static void main(String[] args) { SwingUtilities.invokeLater(GuiApp::launch); }

    private static void launch() {
        JFrame f = new JFrame("Certificate Generator");
        f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        f.setSize(720, 460);
        f.setLocationRelativeTo(null);

        JPanel root = new JPanel(new GridBagLayout());
        root.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6,6,6,6); c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;

        JTextField tfName = new JTextField("Sumit Kumar");
        JTextField tfCourse = new JTextField("Computer Science");
        JTextField tfDate = new JTextField(LocalDate.now().toString());
        JTextField tfId = new JTextField("CERT-" + UUID.randomUUID().toString().substring(0,8).toUpperCase());
        JTextField tfTemplate = new JTextField("/templates/certificate-template.png");
        JTextField tfFont = new JTextField("/fonts/NotoSerif-Regular.ttf");

        JButton pickTpl = new JButton("Browse…");
        JButton pickFont = new JButton("Browse…");
        JButton btnGen = new JButton("Generate PDF");
        JButton btnCal = new JButton("Make calibrate.pdf");
        JLabel status = new JLabel("Ready.");

        int row = 0;
        addRow(root,c,row++, new JLabel("Recipient Name"), tfName, null);
        addRow(root,c,row++, new JLabel("Course"), tfCourse, null);
        addRow(root,c,row++, new JLabel("Date (YYYY-MM-DD)"), tfDate, null);
        addRow(root,c,row++, new JLabel("Certificate ID"), tfId, null);
        addRow(root,c,row++, new JLabel("Template (PNG)"), tfTemplate, pickTpl);
        addRow(root,c,row++, new JLabel("Font (TTF/OTF)"), tfFont, pickFont);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(btnCal); actions.add(btnGen);
        c.gridx=0; c.gridy=row++; c.gridwidth=3; root.add(actions,c);
        c.gridx=0; c.gridy=row;   c.gridwidth=3; root.add(status,c);

        pickTpl.addActionListener(e -> {
            JFileChooser fc = chooser("Choose template PNG", new FileNameExtensionFilter("PNG Images","png"));
            if (fc.showOpenDialog(f)==JFileChooser.APPROVE_OPTION) tfTemplate.setText(fc.getSelectedFile().getAbsolutePath());
        });
        pickFont.addActionListener(e -> {
            JFileChooser fc = chooser("Choose font (TTF/OTF)", new FileNameExtensionFilter("Fonts","ttf","otf"));
            if (fc.showOpenDialog(f)==JFileChooser.APPROVE_OPTION) tfFont.setText(fc.getSelectedFile().getAbsolutePath());
        });

        btnGen.addActionListener(e -> {
            setBusy(f,true);
            try {
                App.setTemplatePath(tfTemplate.getText().trim());
                App.setFontPath(tfFont.getText().trim());
                Path out = Paths.get("out").resolve(safe("Certificate_" + tfName.getText().trim() + ".pdf"));
                App.generateCertificate(
                        tfName.getText().trim(),
                        tfCourse.getText().trim(),
                        tfDate.getText().trim(),
                        tfId.getText().trim(),
                        out
                );
                ok(status, "Saved: " + out.toAbsolutePath());
            } catch (Throwable ex) { err(status, ex.getMessage()); ex.printStackTrace(); }
            finally { setBusy(f,false); }
        });

        btnCal.addActionListener(e -> {
            setBusy(f,true);
            try {
                App.setTemplatePath(tfTemplate.getText().trim());
                App.setFontPath(tfFont.getText().trim());
                App.main(new String[] {"--calibrate"});
                ok(status, "Calibration written to ./out/calibrate.pdf");
            } catch (Throwable ex) { err(status, ex.getMessage()); ex.printStackTrace(); }
            finally { setBusy(f,false); }
        });

        f.setContentPane(root);
        f.setVisible(true);
    }

    private static void addRow(JPanel root, GridBagConstraints c, int row, JComponent label, JComponent field, JButton browse) {
        c.gridx=0; c.gridy=row; c.gridwidth=1; c.weightx=0.25; root.add(label,c);
        c.gridx=1; c.gridy=row; c.gridwidth=(browse==null?2:1); c.weightx=1; root.add(field,c);
        if (browse!=null) { c.gridx=2; c.gridy=row; c.gridwidth=1; c.weightx=0; root.add(browse,c); }
    }
    private static JFileChooser chooser(String title, FileNameExtensionFilter f) {
        JFileChooser fc = new JFileChooser("."); fc.setDialogTitle(title); fc.setFileFilter(f); return fc;
    }
    private static void setBusy(JFrame f, boolean b){ f.setCursor(Cursor.getPredefinedCursor(b?Cursor.WAIT_CURSOR:Cursor.DEFAULT_CURSOR)); }
    private static void ok(JLabel l, String s){ l.setForeground(new Color(30,120,30)); l.setText(s); }
    private static void err(JLabel l, String s){ l.setForeground(new Color(170,40,40)); l.setText("ERROR: "+s); }
    private static String safe(String s){ return s.replaceAll("[^a-zA-Z0-9._-]", "_"); }
}
