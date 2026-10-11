package com.sentinelvault.ui;

import com.sentinelvault.model.FileEntry;
import com.sentinelvault.model.User;
import com.sentinelvault.security.PermissionService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/** Every file the signed-in user is allowed to open, with upload / download / delete. */
class FilesPage extends Page {

    private final Tables.Model model = new Tables.Model("File", "Owner", "Open to", "Uploaded");
    private final JTable table = Tables.create(model, 380, 150, 140, 200);
    private final Widgets.Field search = new Widgets.Field(false).hint("Search files or owners");
    private final Widgets.Btn uploadBtn = new Widgets.Btn("Upload", Widgets.Kind.GOLD).icon("upload");
    private final Widgets.Btn downloadBtn = new Widgets.Btn("Download", Widgets.Kind.GHOST).icon("download");
    private final Widgets.Btn deleteBtn = new Widgets.Btn("Delete", Widgets.Kind.GHOST).icon("trash");
    private final JLabel count = Widgets.label(" ", Theme.sans(Font.PLAIN, 13f), Theme.MUTED);
    private final CardLayout switcher = new CardLayout();
    private final JPanel holder = new JPanel(switcher);
    private List<FileEntry> all = new ArrayList<>();
    private List<FileEntry> shown = new ArrayList<>();

    FilesPage(Ctx ctx) {
        super(ctx);
        Tables.fileColumn(table, 0);
        Tables.chipColumn(table, 2);

        JPanel bar = Widgets.transparent(new BorderLayout(14, 0));
        bar.setBorder(new EmptyBorder(0, 0, 16, 0));
        search.setPreferredSize(new Dimension(340, 42));
        JPanel left = Widgets.transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.add(search);
        JPanel buttons = Widgets.transparent(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.add(count);
        buttons.add(Box.createHorizontalStrut(6));
        buttons.add(downloadBtn);
        buttons.add(deleteBtn);
        buttons.add(uploadBtn);
        bar.add(left, BorderLayout.WEST);
        bar.add(buttons, BorderLayout.EAST);
        add(bar, BorderLayout.NORTH);

        holder.setOpaque(false);
        holder.add(Tables.card(table), "table");
        holder.add(emptyState(), "empty");
        add(holder, BorderLayout.CENTER);

        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
        });
        table.getSelectionModel().addListSelectionListener(e -> updateButtons());
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && selected() != null) FileActions.download(ctx, selected());
            }
        });
        uploadBtn.addActionListener(e -> FileActions.upload(ctx, this, this::refresh));
        downloadBtn.addActionListener(e -> { if (selected() != null) FileActions.download(ctx, selected()); });
        deleteBtn.addActionListener(e -> { if (selected() != null) FileActions.delete(ctx, this, selected(), this::refresh); });
    }

    @Override String title() { return "Vault"; }
    @Override String subtitle() { return "Every file here is encrypted with its own key. You only see files your role may open."; }

    @Override void refresh() {
        User me = ctx.user();
        boolean canUpload = PermissionService.canPerform(me.getRole(), PermissionService.UPLOAD);
        boolean canDelete = PermissionService.canPerform(me.getRole(), PermissionService.DELETE);
        uploadBtn.setVisible(canUpload);
        deleteBtn.setVisible(canDelete);
        Async.run(() -> ctx.api().files(me), list -> { all = list; applyFilter(); }, this::fail);
    }

    private void applyFilter() {
        String q = search.getText().trim().toLowerCase();
        shown = new ArrayList<>();
        List<Object[]> rows = new ArrayList<>();
        for (FileEntry f : all) {
            String owner = ctx.api().ownerName(f.getOwnerId());
            if (!q.isEmpty() && !f.getFilename().toLowerCase().contains(q) && !owner.toLowerCase().contains(q)) continue;
            shown.add(f);
            rows.add(new Object[]{f.getFilename(), owner, Fmt.level(f.getAccessLevel()), Fmt.fileDate(f.getUploadedAt())});
        }
        model.setRows(rows);
        count.setText(shown.size() + (shown.size() == 1 ? " file" : " files"));
        switcher.show(holder, shown.isEmpty() ? "empty" : "table");
        updateButtons();
    }

    private FileEntry selected() {
        int r = table.getSelectedRow();
        return r >= 0 && r < shown.size() ? shown.get(r) : null;
    }

    private void updateButtons() {
        boolean has = selected() != null;
        downloadBtn.setEnabled(has);
        deleteBtn.setEnabled(has);
    }

    private JComponent emptyState() {
        Widgets.Card card = new Widgets.Card(new GridBagLayout());
        JPanel box = Widgets.transparent(null);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        JLabel a = Widgets.label("Nothing here yet", Theme.serif(Font.BOLD, 22f), Theme.NAVY);
        JLabel b = Widgets.label("Files you may open will appear here, encrypted and ready.", Theme.sans(Font.PLAIN, 13.5f), Theme.MUTED);
        a.setAlignmentX(Component.CENTER_ALIGNMENT);
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        box.add(a);
        box.add(Box.createRigidArea(new Dimension(0, 6)));
        box.add(b);
        card.add(box);
        return card;
    }
}
