package com.sentinelvault.ui;

import com.sentinelvault.model.FileEntry;
import com.sentinelvault.model.Role;
import com.sentinelvault.model.VersionEntry;
import com.sentinelvault.security.PermissionService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * The history of one file: every version with its size, uploader and note.
 * You can download any version, add a new one, or make an older one current again.
 */
final class VersionsDialog {
    private VersionsDialog() {}

    static void open(Ctx ctx, Component parent, FileEntry file, Runnable onChange) {
        Tables.Model model = new Tables.Model("Ver", "Size", "Uploaded by", "Uploaded", "Note", "State");
        JTable table = Tables.create(model, 80, 90, 120, 170, 200, 110);
        table.setRowHeight(44);
        List<VersionEntry>[] rows = new List[]{new ArrayList<VersionEntry>()};

        // Only the uploader or an Owner may change versions (the server checks this again).
        boolean canChange = PermissionService.canPerform(ctx.user().getRole(), PermissionService.UPLOAD)
                && (file.getOwnerId() == ctx.user().getId() || ctx.user().getRole() == Role.OWNER);

        Widgets.Btn newBtn = new Widgets.Btn("Upload new version", Widgets.Kind.GOLD).icon("upload");
        Widgets.Btn downloadBtn = new Widgets.Btn("Download", Widgets.Kind.GHOST).icon("download");
        Widgets.Btn restoreBtn = new Widgets.Btn("Make current", Widgets.Kind.GHOST);
        newBtn.setVisible(canChange);
        restoreBtn.setVisible(canChange);

        Runnable[] reload = new Runnable[1];
        reload[0] = () -> Async.run(() -> ctx.api().versions(ctx.user(), file.getId()), list -> {
            rows[0] = list;
            List<Object[]> data = new ArrayList<>();
            for (VersionEntry v : list) {
                data.add(new Object[]{"v" + v.getVersionNo(), Fmt.size(v.getSizeBytes()), v.getUploadedBy(),
                        Fmt.fileDate(v.getUploadedAt()), v.getComment() == null ? "" : v.getComment(),
                        v.isCurrent() ? "Current" : (v.isAvailable() ? "Older" : "Key destroyed")});
            }
            model.setRows(data);
        }, t -> ctx.toast(Async.message(t), true));

        java.util.function.Supplier<VersionEntry> selected = () -> {
            int r = table.getSelectedRow();
            return r >= 0 && r < rows[0].size() ? rows[0].get(r) : null;
        };
        Runnable buttons = () -> {
            VersionEntry v = selected.get();
            downloadBtn.setEnabled(v != null && v.isAvailable());
            restoreBtn.setEnabled(v != null && v.isAvailable() && !v.isCurrent());
        };
        table.getSelectionModel().addListSelectionListener(e -> buttons.run());
        model.addTableModelListener(e -> buttons.run());
        buttons.run();

        newBtn.addActionListener(e -> {
            FileActions.uploadVersion(ctx, parent, file, () -> { reload[0].run(); onChange.run(); });
        });
        downloadBtn.addActionListener(e -> {
            VersionEntry v = selected.get();
            if (v != null) FileActions.downloadVersion(ctx, file, v.getVersionNo());
        });
        restoreBtn.addActionListener(e -> {
            VersionEntry v = selected.get();
            if (v == null) return;
            Async.run(() -> { ctx.api().restoreVersion(ctx.user(), file.getId(), v.getVersionNo()); return null; },
                    x -> { ctx.toast("Version " + v.getVersionNo() + " is now the current version.", false);
                           reload[0].run(); onChange.run(); },
                    t -> ctx.toast(Async.message(t), true));
        });

        JPanel bar = Widgets.transparent(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        bar.setBorder(new EmptyBorder(0, 0, 12, 0));
        bar.add(restoreBtn);
        bar.add(downloadBtn);
        bar.add(newBtn);

        JComponent card = Tables.card(table);
        card.setPreferredSize(new Dimension(780, 280));
        JPanel body = Widgets.transparent(new BorderLayout());
        body.add(bar, BorderLayout.NORTH);
        body.add(card, BorderLayout.CENTER);

        reload[0].run();
        Dialogs.show(parent, "Versions of " + file.getFilename(),
                "Each version is encrypted with its own key and has its own SHA-256 fingerprint.", body);
    }
}
