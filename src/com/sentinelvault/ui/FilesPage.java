package com.sentinelvault.ui;

import com.sentinelvault.model.FileEntry;
import com.sentinelvault.model.FolderEntry;
import com.sentinelvault.model.Role;
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

    private static final String ALL = "All files";
    private static final String NO_FOLDER = "Not in a folder";

    private final Tables.Model model = new Tables.Model("File", "Folder", "Owner", "Open to", "Ver", "Uploaded");
    private final JTable table = Tables.create(model, 290, 120, 120, 120, 50, 170);
    private final Widgets.Field search = new Widgets.Field(false).hint("Search files or owners");
    private final Widgets.Btn uploadBtn = new Widgets.Btn("Upload", Widgets.Kind.GOLD).icon("upload");
    private final Widgets.Btn downloadBtn = new Widgets.Btn("Download", Widgets.Kind.GHOST).icon("download");
    private final Widgets.Btn deleteBtn = new Widgets.Btn("Delete", Widgets.Kind.GHOST).icon("trash");
    private final Widgets.Btn versionsBtn = new Widgets.Btn("Versions", Widgets.Kind.GHOST);
    private final Widgets.Btn moveBtn = new Widgets.Btn("Move to folder", Widgets.Kind.GHOST);
    private final Widgets.Btn newFolderBtn = new Widgets.Btn("New folder", Widgets.Kind.GHOST);
    private final Widgets.Btn deleteFolderBtn = new Widgets.Btn("Delete folder", Widgets.Kind.GHOST);
    private final DefaultComboBoxModel<Object> folderModel = new DefaultComboBoxModel<>();
    private final Widgets.Combo<Object> folderCombo = new Widgets.Combo<>(new Object[]{ALL});
    private boolean loadingFolders = false;
    private List<FolderEntry> folders = new ArrayList<>();
    private final JLabel count = Widgets.label(" ", Theme.sans(Font.PLAIN, 13f), Theme.MUTED);
    private final CardLayout switcher = new CardLayout();
    private final JPanel holder = new JPanel(switcher);
    private List<FileEntry> all = new ArrayList<>();
    private List<FileEntry> shown = new ArrayList<>();

    FilesPage(Ctx ctx) {
        super(ctx);
        Tables.fileColumn(table, 0);
        Tables.chipColumn(table, 3);

        // Row 1: search, folder filter and folder buttons. Row 2: actions on the selected file.
        search.setPreferredSize(new Dimension(260, 42));
        folderCombo.setModel(folderModel);
        folderModel.addElement(ALL);
        folderCombo.setPreferredSize(new Dimension(210, 42));
        JPanel row1 = Widgets.transparent(new FlowLayout(FlowLayout.LEFT, 10, 0));
        row1.add(search);
        row1.add(folderCombo);
        row1.add(newFolderBtn);
        row1.add(deleteFolderBtn);

        JPanel row2 = Widgets.transparent(new BorderLayout(10, 0));
        JPanel countBox = Widgets.transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        countBox.add(count);
        JPanel buttons = Widgets.transparent(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.add(versionsBtn);
        buttons.add(moveBtn);
        buttons.add(downloadBtn);
        buttons.add(deleteBtn);
        buttons.add(uploadBtn);
        row2.add(countBox, BorderLayout.WEST);
        row2.add(buttons, BorderLayout.EAST);

        JPanel bar = Widgets.transparent(new GridLayout(2, 1, 0, 10));
        bar.setBorder(new EmptyBorder(0, 0, 16, 0));
        bar.add(row1);
        bar.add(row2);
        bar.setPreferredSize(new Dimension(10, 106));
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
        versionsBtn.addActionListener(e -> { if (selected() != null) VersionsDialog.open(ctx, this, selected(), this::refresh); });
        moveBtn.addActionListener(e -> moveSelected());
        newFolderBtn.addActionListener(e -> newFolder());
        deleteFolderBtn.addActionListener(e -> deleteFolder());
        folderCombo.addActionListener(e -> { if (!loadingFolders) applyFilter(); });
    }

    @Override String title() { return "Vault"; }
    @Override String subtitle() { return "Every file here is encrypted with its own key. You only see files your role may open."; }

    @Override void refresh() {
        User me = ctx.user();
        boolean canUpload = PermissionService.canPerform(me.getRole(), PermissionService.UPLOAD);
        boolean canDelete = PermissionService.canPerform(me.getRole(), PermissionService.DELETE);
        uploadBtn.setVisible(canUpload);
        deleteBtn.setVisible(canDelete);
        moveBtn.setVisible(canUpload);
        newFolderBtn.setVisible(canUpload);
        deleteFolderBtn.setVisible(canDelete);
        Async.run(() -> new Object[]{ctx.api().files(me), ctx.api().folders(me)}, data -> {
            @SuppressWarnings("unchecked") List<FileEntry> f = (List<FileEntry>) data[0];
            @SuppressWarnings("unchecked") List<FolderEntry> d = (List<FolderEntry>) data[1];
            all = f;
            folders = d;
            fillFolderCombo();
            applyFilter();
        }, this::fail);
    }

    /** Rebuilds the folder drop-down and keeps the current choice when it still exists. */
    private void fillFolderCombo() {
        Object keep = folderCombo.getSelectedItem();
        loadingFolders = true;
        folderModel.removeAllElements();
        folderModel.addElement(ALL);
        folderModel.addElement(NO_FOLDER);
        Object restore = ALL;
        for (FolderEntry f : folders) {
            folderModel.addElement(f);
            if (keep instanceof FolderEntry k && k.getId() == f.getId()) restore = f;
        }
        if (NO_FOLDER.equals(keep)) restore = NO_FOLDER;
        folderCombo.setSelectedItem(restore);
        loadingFolders = false;
    }

    private String folderName(Integer id) {
        if (id == null) return "-";
        for (FolderEntry f : folders) if (f.getId() == id) return f.getName();
        return "-";
    }

    private void newFolder() {
        Widgets.Field name = new Widgets.Field(false).hint("Folder name");
        if (!Dialogs.form(this, "New folder", "Folders only group files. Each file keeps its own access level.",
                Dialogs.labelled("Name", name), "Create folder", false)) return;
        String value = name.getText();
        Async.run(() -> ctx.api().createFolder(ctx.user(), value),
                id -> { ctx.toast("Folder created: " + value.trim(), false); refresh(); },
                t -> ctx.toast(Async.message(t), true));
    }

    private void deleteFolder() {
        Object choice = folderCombo.getSelectedItem();
        if (!(choice instanceof FolderEntry folder)) return;
        if (!Dialogs.confirm(this, "Delete this folder?",
                "“" + folder.getName() + "” will be removed. Only an empty folder can be deleted; its files are not affected.",
                "Delete folder", true)) return;
        Async.run(() -> { ctx.api().deleteFolder(ctx.user(), folder.getId()); return null; },
                v -> { ctx.toast("Folder deleted: " + folder.getName(), false); folderCombo.setSelectedItem(ALL); refresh(); },
                t -> ctx.toast(Async.message(t), true));
    }

    private void moveSelected() {
        FileEntry file = selected();
        if (file == null) return;
        List<Object> choices = new ArrayList<>();
        choices.add(NO_FOLDER);
        choices.addAll(folders);
        Widgets.Combo<Object> combo = new Widgets.Combo<>(choices.toArray());
        for (FolderEntry f : folders) if (file.getFolderId() != null && f.getId() == file.getFolderId()) combo.setSelectedItem(f);
        if (!Dialogs.form(this, "Move to folder", "Choose where “" + file.getFilename() + "” should be.",
                Dialogs.labelled("Folder", combo), "Move", false)) return;
        Object target = combo.getSelectedItem();
        Integer folderId = target instanceof FolderEntry f ? f.getId() : null;
        Async.run(() -> { ctx.api().moveFile(ctx.user(), file.getId(), folderId); return null; },
                v -> { ctx.toast("Moved " + file.getFilename(), false); refresh(); },
                t -> ctx.toast(Async.message(t), true));
    }

    private void applyFilter() {
        String q = search.getText().trim().toLowerCase();
        shown = new ArrayList<>();
        List<Object[]> rows = new ArrayList<>();
        Object choice = folderCombo.getSelectedItem();
        for (FileEntry f : all) {
            if (choice instanceof FolderEntry fe && (f.getFolderId() == null || f.getFolderId() != fe.getId())) continue;
            if (NO_FOLDER.equals(choice) && f.getFolderId() != null) continue;
            String owner = ctx.api().ownerName(f.getOwnerId());
            if (!q.isEmpty() && !f.getFilename().toLowerCase().contains(q) && !owner.toLowerCase().contains(q)) continue;
            shown.add(f);
            rows.add(new Object[]{f.getFilename(), folderName(f.getFolderId()), owner, Fmt.level(f.getAccessLevel()),
                    "v" + f.getVersion(), Fmt.fileDate(f.getUploadedAt())});
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
        versionsBtn.setEnabled(has);
        moveBtn.setEnabled(has);
        deleteFolderBtn.setEnabled(folderCombo.getSelectedItem() instanceof FolderEntry);
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
