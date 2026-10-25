package dev.muguet;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import java.io.File;

// The launcher has to hand the native side a real filesystem path, not a SAF URI.
// Rust opens game files with std::fs, which knows nothing about content:// URIs, so
// a tree the user picked in the system browser only works once it is resolved back
// to the /storage/emulated/0 path it stands for.
final class StoragePaths {
    private static final String PRIMARY_VOLUME = "primary";

    private StoragePaths() {}

    // Where imported game data is extracted. On Android 10+ an app may create and
    // write its own subdirectory under Android/data without any extra permission,
    // so this is the one path that both the user can see in a file manager and the
    // app can always write. Falls back to app-private storage when even that is
    // refused (adopted storage, a vendor build with a locked Android/data).
    static File gameDir(Context context) {
        File external = externalGameDir(context);
        if (external != null) return external;
        return new File(context.getFilesDir(), "game");
    }

    static File externalGameDir(Context context) {
        File root = context.getExternalFilesDir(null);
        if (root == null) return null;
        File game = new File(root, "game");
        if (game.isDirectory() || game.mkdirs()) return game;
        File parent = root;
        if (parent.isDirectory() || parent.mkdirs()) return new File(parent, "game");
        return null;
    }

    // True when the game data sits under Android/data, which is what the launcher
    // tells the user when they look for it in a file manager.
    static boolean isUnderAndroidData(File dir) {
        String path = dir.getAbsolutePath();
        return path.contains("/Android/data/") || path.contains("/Android/media/");
    }

    // Resolves a picked tree URI to a filesystem path, or null when the pick is not
    // an ordinary directory on shared storage (a Downloads provider entry, a cloud
    // provider such as Drive, a ZIP opened in place). Callers fall back to the SAF
    // copy path in that case rather than pretending the folder is usable.
    static String treeToPath(Context context, Uri uri) {
        if (uri == null) return null;
        try {
            if (DocumentsContract.isTreeUri(uri)) {
                String docId = DocumentsContract.getTreeDocumentId(uri);
                String fromDocId = documentIdToPath(docId);
                if (fromDocId != null) return fromDocId;
            }
            // Some file managers return a plain file authority rather than a
            // documents one; try the generic path decoding before giving up.
            if ("com.android.externalstorage.documents".equals(uri.getAuthority())) {
                String docId = DocumentsContract.getDocumentId(uri);
                return documentIdToPath(docId);
            }
        } catch (Exception ignored) {
            // Fall through to null: a pick we cannot resolve is handled by the
            // caller, which copies the tree instead.
        }
        return null;
    }

    // A document id from the external-storage provider is "primary:Games/GTA",
    // which maps to /storage/emulated/0/Games/GTA. A secondary volume keeps its
    // UUID as the prefix and lives at /storage/<uuid>/<rest>.
    private static String documentIdToPath(String docId) {
        if (docId == null || docId.isEmpty()) return null;
        String[] parts = docId.split(":", 2);
        String volume = parts[0];
        String rest = parts.length > 1 ? parts[1] : "";
        String base;
        if (PRIMARY_VOLUME.equalsIgnoreCase(volume)) {
            base = Environment.getExternalStorageDirectory().getAbsolutePath();
        } else {
            base = "/storage/" + volume;
        }
        File candidate = rest.isEmpty() ? new File(base) : new File(base, rest);
        return candidate.getAbsolutePath();
    }

    // Whether a plain filesystem path can actually be listed and read. Used to
    // confirm a resolved path is real before the launcher stores it.
    static boolean isReadableDir(String path) {
        if (path == null) return false;
        File dir = new File(path);
        return dir.isDirectory() && dir.canRead();
    }
}
