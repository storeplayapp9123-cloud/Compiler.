package com.aalamstudio.compiler;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.Blob;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Har user ke projects: users/{uid}/projects/{id} (info) + .../blob/asc (file data). */
public class CloudSync {

    public static final int MAX_BYTES = 700 * 1024;

    public static class Item {
        public String id;
        public String name;
        public long updated;
        public long size;
    }

    public interface Result<T> {
        void ok(T value);
        void fail(String message);
    }

    private static CollectionReference col() {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        return FirebaseFirestore.getInstance()
                .collection("users").document(uid).collection("projects");
    }

    public static String idFor(String name) {
        String id = name.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        return id.isEmpty() ? "project" : id;
    }

    public static void upload(String name, byte[] asc, final Result<String> r) {
        if (asc.length > MAX_BYTES) {
            r.fail("Project bahut bada hai (" + asc.length / 1024 + " KB, limit 700 KB)");
            return;
        }
        String id = idFor(name);

        Map<String, Object> meta = new HashMap<>();
        meta.put("name", name);
        meta.put("updated", System.currentTimeMillis());
        meta.put("size", (long) asc.length);

        Map<String, Object> data = new HashMap<>();
        data.put("asc", Blob.fromBytes(asc));

        WriteBatch b = FirebaseFirestore.getInstance().batch();
        b.set(col().document(id), meta);
        b.set(col().document(id).collection("blob").document("asc"), data);
        b.commit()
                .addOnSuccessListener(v -> r.ok("Saved to cloud: " + name))
                .addOnFailureListener(e -> r.fail(String.valueOf(e.getMessage())));
    }

    public static void list(final Result<List<Item>> r) {
        col().orderBy("updated", Query.Direction.DESCENDING).get()
                .addOnSuccessListener(snap -> {
                    List<Item> out = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Item it = new Item();
                        it.id = d.getId();
                        it.name = d.getString("name") == null ? d.getId() : d.getString("name");
                        Long u = d.getLong("updated");
                        Long s = d.getLong("size");
                        it.updated = u == null ? 0 : u;
                        it.size = s == null ? 0 : s;
                        out.add(it);
                    }
                    r.ok(out);
                })
                .addOnFailureListener(e -> r.fail(String.valueOf(e.getMessage())));
    }

    public static void download(String id, final Result<byte[]> r) {
        col().document(id).collection("blob").document("asc").get()
                .addOnSuccessListener(d -> {
                    Blob b = d.getBlob("asc");
                    if (b == null) r.fail("Project ka data nahi mila");
                    else r.ok(b.toBytes());
                })
                .addOnFailureListener(e -> r.fail(String.valueOf(e.getMessage())));
    }

    public static void delete(String id, final Result<String> r) {
        WriteBatch b = FirebaseFirestore.getInstance().batch();
        b.delete(col().document(id).collection("blob").document("asc"));
        b.delete(col().document(id));
        b.commit()
                .addOnSuccessListener(v -> r.ok("Deleted from cloud"))
                .addOnFailureListener(e -> r.fail(String.valueOf(e.getMessage())));
    }
}
