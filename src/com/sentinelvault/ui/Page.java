package com.sentinelvault.ui;

import com.sentinelvault.model.User;

import javax.swing.*;
import java.awt.*;

/** What a page can ask of the main window. */
interface Ctx {
    Api api();
    User user();
    void toast(String message, boolean error);
    void goTo(String page);
    void signOut(boolean lockVault);
    Window window();
}

/** Base class of every screen inside the main window. */
abstract class Page extends JPanel {
    protected final Ctx ctx;

    Page(Ctx ctx) {
        super(new BorderLayout());
        this.ctx = ctx;
        setOpaque(false);
    }

    abstract String title();
    abstract String subtitle();
    /** Reload the data. Called every time the page is shown. */
    abstract void refresh();

    /** Standard way to show a failure. */
    protected void fail(Throwable t) { ctx.toast(Async.message(t), true); }
}
