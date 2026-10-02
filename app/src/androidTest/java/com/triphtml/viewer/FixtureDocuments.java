package com.triphtml.viewer;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/** This provider lives only in the separate test APK and is opened through the real system picker. */
public final class FixtureDocuments extends DocumentsProvider {
    private static final String[] DOCUMENT_COLUMNS = {"document_id", "_display_name", "mime_type", "flags", "_size"};
    @Override public boolean onCreate() { return true; }
    @Override public Cursor queryRoots(String[] projection) {
        MatrixCursor roots = new MatrixCursor(new String[]{"root_id", "document_id", "title", "flags", "mime_types"});
        roots.addRow(new Object[]{"fixture", "root", "Viewer test files", DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD, "text/html"});
        return roots;
    }
    @Override public Cursor queryDocument(String id, String[] projection) throws FileNotFoundException {
        MatrixCursor rows = new MatrixCursor(DOCUMENT_COLUMNS);
        addDocument(rows, id);
        return rows;
    }
    @Override public Cursor queryChildDocuments(String parent, String[] projection, String sortOrder) throws FileNotFoundException {
        MatrixCursor rows = new MatrixCursor(DOCUMENT_COLUMNS);
        addDocument(rows, "trip.html");
        addDocument(rows, "other.html");
        return rows;
    }
    private void addDocument(MatrixCursor rows, String id) throws FileNotFoundException {
        if (id.equals("root")) rows.addRow(new Object[]{id, "Viewer test files", DocumentsContract.Document.MIME_TYPE_DIR, 0, 0});
        else if (id.equals("trip.html") || id.equals("other.html")) rows.addRow(new Object[]{id, id, "text/html", 0, 2048});
        else throw new FileNotFoundException(id);
    }
    @Override public ParcelFileDescriptor openDocument(String id, String mode, CancellationSignal signal) throws FileNotFoundException {
        if (!mode.equals("r") || (!id.equals("trip.html") && !id.equals("other.html"))) throw new FileNotFoundException(id);
        File file = new File(getContext().getFilesDir(), id);
        File deleted = new File(getContext().getFilesDir(), "deleted");
        if (deleted.exists()) throw new FileNotFoundException("Synthetic deletion");
        if (!file.exists()) {
            try (InputStream input = getContext().getAssets().open("trip.html")) {
                Files.copy(input, file.toPath());
            } catch (IOException error) { throw new FileNotFoundException(error.toString()); }
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }
}
