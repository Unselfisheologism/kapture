package dev.dect.scrnshoot.data;

import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;

import dev.dect.scrnshoot.model.Scrnshoot;

public class DB extends SQLiteOpenHelper {
    private static final String DB_NAME = "scrnshoot.db";

    private static final int DB_VERSION = 1;

    private static final String TABLE_KAPTURE = "scrnshoot",
                                KAPTURE_COL_ID = "k_id",
                                KAPTURE_COL_LOCATION = "k_location",
                                KAPTURE_COL_PROFILE_ID = "k_profile_id",
                                KAPTURE_COL_FROM = "k_from";

    private final Context CONTEXT;

    public DB(Context ctx) {
        super(ctx, DB_NAME, null, DB_VERSION);

        this.CONTEXT = ctx;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        final String q0 = "CREATE TABLE "
                           + TABLE_KAPTURE + " ("
                           + KAPTURE_COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                           + KAPTURE_COL_LOCATION + " TEXT, "
                           + KAPTURE_COL_PROFILE_ID + " TEXT, "
                           + KAPTURE_COL_FROM + " TEXT);";

        db.execSQL(q0);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {}

    public void insertScrnshoot(Scrnshoot scrnshoot) {
        final SQLiteDatabase db = this.getReadableDatabase();

        final ContentValues valuesScrnshoot = new ContentValues();

        valuesScrnshoot.put(KAPTURE_COL_LOCATION, scrnshoot.getLocation());
        valuesScrnshoot.put(KAPTURE_COL_PROFILE_ID, scrnshoot.getProfileId());
        valuesScrnshoot.put(KAPTURE_COL_FROM, scrnshoot.getFrom());

        final long idScrnshoot = db.insert(TABLE_KAPTURE, null, valuesScrnshoot);

        scrnshoot.setId(idScrnshoot);
    }

    public ArrayList<Scrnshoot> selectAllScrnshoots(boolean desc) {
        final ArrayList<Scrnshoot> scrnshoots = new ArrayList<>();

        final SQLiteDatabase db = this.getReadableDatabase();

        final Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_KAPTURE + " ORDER BY " + KAPTURE_COL_ID + (desc ? " DESC" : " ASC"), null);

        while(true) {
            assert cursor != null;
            if(!cursor.moveToNext()) {
                break;
            }

            final Scrnshoot scrnshoot = new Scrnshoot(CONTEXT);

            scrnshoot.setId(cursor.getLong(cursor.getColumnIndexOrThrow(KAPTURE_COL_ID)));
            scrnshoot.setLocation(cursor.getString(cursor.getColumnIndexOrThrow(KAPTURE_COL_LOCATION)));
            scrnshoot.setProfileId(cursor.getString(cursor.getColumnIndexOrThrow(KAPTURE_COL_PROFILE_ID)));
            scrnshoot.setFrom(cursor.getString(cursor.getColumnIndexOrThrow(KAPTURE_COL_FROM)));

            scrnshoots.add(scrnshoot);
        }

        cursor.close();

        return scrnshoots;
    }

    public void deleteScrnshoot(Scrnshoot scrnshoot) {
        final SQLiteDatabase db = this.getReadableDatabase();

        db.beginTransaction();

        db.execSQL("DELETE FROM " + TABLE_KAPTURE + " WHERE " + KAPTURE_COL_ID + " = " +  scrnshoot.getId());

        db.setTransactionSuccessful();

        db.endTransaction();

        try {
            ((NotificationManager) CONTEXT.getSystemService(Context.NOTIFICATION_SERVICE)).cancel((int) scrnshoot.getId());
        } catch (Exception ignore) {}
    }
}
