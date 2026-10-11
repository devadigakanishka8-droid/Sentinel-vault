package com.sentinelvault.ui;

import javax.swing.SwingWorker;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/** Runs slow work (BCrypt, encryption, database) off the screen thread so the window never freezes. */
final class Async {
    private Async() {}

    static <T> void run(Callable<T> job, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception { return job.call(); }
            @Override protected void done() {
                try {
                    onSuccess.accept(get());
                } catch (ExecutionException e) {
                    onError.accept(e.getCause() == null ? e : e.getCause());
                } catch (Exception e) {
                    onError.accept(e);
                }
            }
        }.execute();
    }

    /** A message that is safe and useful to show the user. */
    static String message(Throwable t) {
        if (t instanceof SecurityException || t instanceof IllegalArgumentException || t instanceof IllegalStateException) {
            return t.getMessage();
        }
        String m = t.getMessage();
        return "Something went wrong" + (m == null ? "." : ": " + m);
    }
}
