package com.sentinelvault.ui;

import com.sentinelvault.model.FileEntry;
import com.sentinelvault.model.Role;
import com.sentinelvault.model.User;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Sharing in SentinelVault is role-based: a file is open to every user whose role meets its access level.
 *  - Shared with me: files other people uploaded that my role may open.
 *  - Shared by me:   my own files that other roles may open.
 */
class SharedPage extends Page {

    private static final class Section {
        final Tables.Model model;
        final JTable table;
        final Widgets.Card card;
        List<FileEntry> files = new ArrayList<>();

        Section(Tables.Model model, int... widths) {
            this.model = model;
            this.table = Tables.create(model, widths);
            Tables.fileColumn(table, 0);
            Tables.chipColumn(table, 2);
            this.card = Tables.card(table);
        }
        FileEntry selected() {
            int r = table.getSelectedRow();
            return r >= 0 && r < files.size() ? files.get(r) : null;
        }
    }

    private final Section withMe = new Section(new Tables.Model("File", "Shared by", "Open to", "Shared on"), 380, 150, 140, 200);
    private final Section byMe = new Section(new Tables.Model("File", "Uploaded by", "Open to", "Shared on"), 380, 150, 140, 200);
    private final CardLayout switcher = new CardLayout();
    private final JPanel holder = new JPanel(switcher);
    private final Widgets.Btn downloadBtn = new Widgets.Btn("Download", Widgets.Kind.GHOST).icon("download");
    private final JLabel note = Widgets.label(" ", Theme.sans(Font.PLAIN, 13f), Theme.MUTED);
    private int mode = 0;

    SharedPage(Ctx ctx) {
        super(ctx);
        JPanel bar = Widgets.transparent(new BorderLayout(14, 0));
        bar.setBorder(new EmptyBorder(0, 0, 16, 0));
        Widgets.Segmented tabs = new Widgets.Segmented(new String[]{"Shared with me", "Shared by me"}, i -> {
            mode = i;
            switcher.show(holder, i == 0 ? "with" : "by");
            updateNote();
            updateButtons();
        });
        JPanel left = Widgets.transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.add(tabs);
        JPanel right = Widgets.transparent(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.add(note);
        right.add(Box.createHorizontalStrut(6));
        right.add(downloadBtn);
        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        add(bar, BorderLayout.NORTH);

        holder.setOpaque(false);
        holder.add(withMe.card, "with");
        holder.add(byMe.card, "by");
        add(holder, BorderLayout.CENTER);

        downloadBtn.addActionListener(e -> {
            FileEntry f = (mode == 0 ? withMe : byMe).selected();
            if (f != null) FileActions.download(ctx, f);
        });
        javax.swing.event.ListSelectionListener l = e -> updateButtons();
        withMe.table.getSelectionModel().addListSelectionListener(l);
        byMe.table.getSelectionModel().addListSelectionListener(l);
        updateButtons();
    }

    @Override String title() { return "Shared files"; }
    @Override String subtitle() { return "A file is open to every role at or above its access level."; }

    @Override void refresh() {
        User me = ctx.user();
        Async.run(() -> ctx.api().files(me), list -> {
            withMe.files = new ArrayList<>();
            byMe.files = new ArrayList<>();
            List<Object[]> in = new ArrayList<>(), out = new ArrayList<>();
            for (FileEntry f : list) {
                if (f.getOwnerId() != me.getId()) {
                    withMe.files.add(f);
                    in.add(new Object[]{f.getFilename(), ctx.api().ownerName(f.getOwnerId()),
                            Fmt.level(f.getAccessLevel()), Fmt.fileDate(f.getUploadedAt())});
                } else if (f.getAccessLevel() != Role.OWNER) {
                    byMe.files.add(f);
                    out.add(new Object[]{f.getFilename(), "You", Fmt.level(f.getAccessLevel()), Fmt.fileDate(f.getUploadedAt())});
                }
            }
            withMe.model.setRows(in);
            byMe.model.setRows(out);
            updateNote();
            updateButtons();
        }, this::fail);
    }

    private void updateNote() {
        int n = (mode == 0 ? withMe : byMe).files.size();
        note.setText(n + (n == 1 ? " file" : " files"));
    }

    private void updateButtons() {
        downloadBtn.setEnabled((mode == 0 ? withMe : byMe).selected() != null);
    }
}
