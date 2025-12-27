package dev.dect.scrnshoot.data;

import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;

import dev.dect.scrnshoot.model.Scrnshoot;

public class DB extends SQLiteOpenHelper {
    private final String TAG = DB.class.getSimpleName();

    private static final String DB_NAME = "scrnshoot.db";

    private static final int DB_VERSION = 4;

    private static final String TABLE_KAPTURE = "scrnshoot",
                                KAPTURE_COL_ID = "k_id",
                                KAPTURE_COL_LOCATION = "k_location",
                                KAPTURE_COL_PROFILE_ID = "k_profile_id",
                                KAPTURE_COL_FROM = "k_from",

                                TABLE_EXTRAS = "extras",
                                EXTRAS_COL_ID = "e_id",
                                EXTRAS_COL_ID_KAPTURE = "e_id_scrnshoot",
                                EXTRAS_COL_LOCATION = "e_location",
                                EXTRAS_COL_TYPE = "e_type",

                                TABLE_SCREENSHOTS = "screenshots",
                                SCREENSHOTS_COL_ID = "s_id",
                                SCREENSHOTS_COL_ID_KAPTURE = "s_id_scrnshoot",
                                SCREENSHOTS_COL_LOCATION = "s_location";

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

        final String q1 = "CREATE TABLE "
                          + TABLE_EXTRAS + " ("
                          + EXTRAS_COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                          + EXTRAS_COL_ID_KAPTURE + " INTEGER, "
                          + EXTRAS_COL_LOCATION + " TEXT, "
                          + EXTRAS_COL_TYPE + " INTEGER);";

        db.execSQL(q0);
        db.execSQL(q1);

        Update.createScreenshotsTableHelper(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        switch(oldVersion) {
            case 1:
                Update.createScreenshotsTableHelper(db);

            case 2:
                Update.updateScrnshootTableAddProfileCol(db);

            case 3:
                Update.updateScrnshootTableAddFromCol(db);
                break;
        }
    }

    private static class Update {
        public static void createScreenshotsTableHelper(SQLiteDatabase db) {
            final String q = "CREATE TABLE "
                + TABLE_SCREENSHOTS + " ("
                + SCREENSHOTS_COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + SCREENSHOTS_COL_ID_KAPTURE + " INTEGER, "
                + SCREENSHOTS_COL_LOCATION + " TEXT);";

            db.execSQL(q);
        }

        public static void updateScrnshootTableAddProfileCol(SQLiteDatabase db) {
            final String q = "ALTER TABLE "
                + TABLE_KAPTURE
                + " ADD COLUMN "
                + KAPTURE_COL_PROFILE_ID
                + " TEXT DEFAULT "
                + "\"" + Constants.NO_PROFILE + "\"";

            db.execSQL(q);
        }

        public static void updateScrnshootTableAddFromCol(SQLiteDatabase db) {
            final String q = "ALTER TABLE "
                    + TABLE_KAPTURE
                    + " ADD COLUMN "
                    + KAPTURE_COL_FROM
                    + " TEXT DEFAULT "
                    + "\"" + Scrnshoot.FROM_PHONE + "\"";

            db.execSQL(q);
        }
    }

    public void insertScrnshoot(Scrnshoot scrnshoot) {
        final SQLiteDatabase db = this.getWritableDatabase();

        final ContentValues valuesScrnshoot = new ContentValues();

        valuesScrnshoot.put(KAPTURE_COL_LOCATION, scrnshoot.getLocation());
        valuesScrnshoot.put(KAPTURE_COL_PROFILE_ID, scrnshoot.getProfileId());
        valuesScrnshoot.put(KAPTURE_COL_FROM, scrnshoot.getFrom());

        final long idScrnshoot = db.insert(TABLE_KAPTURE, null, valuesScrnshoot);

        scrnshoot.setId(idScrnshoot);

        for(Scrnshoot.Extra extra : scrnshoot.getExtras()) {
            final ContentValues valuesExtra = new ContentValues();

            valuesExtra.put(EXTRAS_COL_ID_KAPTURE, idScrnshoot);
            valuesExtra.put(EXTRAS_COL_LOCATION, extra.getLocation());
            valuesExtra.put(EXTRAS_COL_TYPE, extra.getType());

            final long idExtra = db.insert(TABLE_EXTRAS, null, valuesExtra);

            extra.setId(idExtra);
        }

        for(Scrnshoot.Screenshot screenshot : scrnshoot.getScreenshots()) {
            final ContentValues valuesScreenshot = new ContentValues();

            valuesScreenshot.put(SCREENSHOTS_COL_ID_KAPTURE, idScrnshoot);
            valuesScreenshot.put(SCREENSHOTS_COL_LOCATION, screenshot.getLocation());

            final long idScreenshot = db.insert(TABLE_SCREENSHOTS, null, valuesScreenshot);

            screenshot.setId(idScreenshot);
        }
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
            scrnshoot.setExtras(selectExtras(scrnshoot));
            scrnshoot.setScreenshots(selectScreenshots(scrnshoot));

            scrnshoots.add(scrnshoot);
        }

        cursor.close();

        return scrnshoots;
    }

    public Scrnshoot selectScrnshoot(long id) {
        return selectScrnshoot((int) id);
    }

    public Scrnshoot selectScrnshoot(int id) {
        final SQLiteDatabase db = this.getReadableDatabase();

        final Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_KAPTURE + " WHERE " + KAPTURE_COL_ID + " = " + id, null);

        if(cursor.moveToFirst()) {
            final Scrnshoot scrnshoot = new Scrnshoot(CONTEXT);

            scrnshoot.setId(cursor.getLong(cursor.getColumnIndexOrThrow(KAPTURE_COL_ID)));
            scrnshoot.setLocation(cursor.getString(cursor.getColumnIndexOrThrow(KAPTURE_COL_LOCATION)));
            scrnshoot.setProfileId(cursor.getString(cursor.getColumnIndexOrThrow(KAPTURE_COL_PROFILE_ID)));
            scrnshoot.setFrom(cursor.getString(cursor.getColumnIndexOrThrow(KAPTURE_COL_FROM)));
            scrnshoot.setExtras(selectExtras(scrnshoot));
            scrnshoot.setScreenshots(selectScreenshots(scrnshoot));

            cursor.close();

            return scrnshoot;
        }

        cursor.close();

        return null;
    }

    public ArrayList<Scrnshoot.Extra> selectExtras(Scrnshoot scrnshoot) {
        final ArrayList<Scrnshoot.Extra> extras = new ArrayList<>();

        final SQLiteDatabase db = this.getReadableDatabase();

        final Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_EXTRAS + " WHERE " + EXTRAS_COL_ID_KAPTURE + " = " + scrnshoot.getId(), null);

        while(true) {
            assert cursor != null;
            if(!cursor.moveToNext()) {
                break;
            }

            final Scrnshoot.Extra extra = new Scrnshoot.Extra();

            extra.setId(cursor.getLong(cursor.getColumnIndexOrThrow(EXTRAS_COL_ID)));
            extra.setLocation(cursor.getString(cursor.getColumnIndexOrThrow(EXTRAS_COL_LOCATION)));
            extra.setType(cursor.getInt(cursor.getColumnIndexOrThrow(EXTRAS_COL_TYPE)));
            extra.setIdScrnshoot(cursor.getLong(cursor.getColumnIndexOrThrow(EXTRAS_COL_ID_KAPTURE)));

            extras.add(extra);
        }

        cursor.close();

        return extras;
    }

    public ArrayList<Scrnshoot.Screenshot> selectScreenshots(Scrnshoot scrnshoot) {
        final ArrayList<Scrnshoot.Screenshot> screenshots = new ArrayList<>();

        final SQLiteDatabase db = this.getReadableDatabase();

        final Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_SCREENSHOTS + " WHERE " + SCREENSHOTS_COL_ID_KAPTURE + " = " + scrnshoot.getId(), null);

        while(true) {
            assert cursor != null;
            if(!cursor.moveToNext()) {
                break;
            }

            final Scrnshoot.Screenshot screenshot = new Scrnshoot.Screenshot();

            screenshot.setId(cursor.getLong(cursor.getColumnIndexOrThrow(SCREENSHOTS_COL_ID)));
            screenshot.setLocation(cursor.getString(cursor.getColumnIndexOrThrow(SCREENSHOTS_COL_LOCATION)));
            screenshot.setIdScrnshoot(cursor.getLong(cursor.getColumnIndexOrThrow(SCREENSHOTS_COL_ID_KAPTURE)));

            screenshots.add(screenshot);
        }

        cursor.close();

        return screenshots;
    }

    public void deleteScrnshoot(Scrnshoot scrnshoot) {
        final SQLiteDatabase db = this.getWritableDatabase();

        db.beginTransaction();

        try {
            db.execSQL("DELETE FROM " + TABLE_KAPTURE + " WHERE " + KAPTURE_COL_ID + " = " + scrnshoot.getId());
            db.execSQL("DELETE FROM " + TABLE_EXTRAS + " WHERE " + EXTRAS_COL_ID_KAPTURE + " = " + scrnshoot.getId());
            db.execSQL("DELETE FROM " + TABLE_SCREENSHOTS + " WHERE " + SCREENSHOTS_COL_ID_KAPTURE + " = " + scrnshoot.getId());

            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "deleteScrnshoot: " + e.getMessage());
        }

        db.endTransaction();

        try {
            ((NotificationManager) CONTEXT.getSystemService(Context.NOTIFICATION_SERVICE)).cancel((int) scrnshoot.getId());
        } catch (Exception e) {
            Log.e(TAG, "deleteScrnshoot: " + e.getMessage());
        }
    }

    public void deleteExtra(Scrnshoot.Extra extra) {
        final SQLiteDatabase db = this.getWritableDatabase();

        db.execSQL("DELETE FROM " + TABLE_EXTRAS + " WHERE " + EXTRAS_COL_ID + " = " + extra.getId());
    }

    public void deleteExtras(Scrnshoot scrnshoot) {
        final SQLiteDatabase db = this.getWritableDatabase();

        db.execSQL("DELETE FROM " + TABLE_EXTRAS + " WHERE " + EXTRAS_COL_ID_KAPTURE + " = " + scrnshoot.getId());
    }

    public void deleteScreenshot(Scrnshoot.Screenshot screenshot) {
        final SQLiteDatabase db = this.getWritableDatabase();

        db.execSQL("DELETE FROM " + TABLE_SCREENSHOTS + " WHERE " + SCREENSHOTS_COL_ID + " = " + screenshot.getId());
    }

    public void deleteScreenshots(Scrnshoot scrnshoot) {
        final SQLiteDatabase db = this.getWritableDatabase();

        db.execSQL("DELETE FROM " + TABLE_SCREENSHOTS + " WHERE " + SCREENSHOTS_COL_ID_KAPTURE + " = " + scrnshoot.getId());
    }

    public void updateScrnshoot(Scrnshoot scrnshoot) {
        final SQLiteDatabase db = this.getWritableDatabase();

        ContentValues valuesScrnshoot = new ContentValues();

        valuesScrnshoot.put(KAPTURE_COL_LOCATION, scrnshoot.getLocation());
        valuesScrnshoot.put(KAPTURE_COL_PROFILE_ID, scrnshoot.getProfileId());
        valuesScrnshoot.put(KAPTURE_COL_FROM, scrnshoot.getFrom());

        for(Scrnshoot.Extra extra : scrnshoot.getExtras()) {
            updateExtra(extra);
        }

        for(Scrnshoot.Screenshot screenshot : scrnshoot.getScreenshots()) {
            updateScreenshot(screenshot);
        }

        db.update(TABLE_KAPTURE, valuesScrnshoot, KAPTURE_COL_ID + " = " + scrnshoot.getId(), null);
    }

    public void updateExtra(Scrnshoot.Extra extra) {
        final SQLiteDatabase db = this.getWritableDatabase();

        final ContentValues valuesExtra = new ContentValues();

        valuesExtra.put(EXTRAS_COL_LOCATION, extra.getLocation());

        db.update(TABLE_EXTRAS, valuesExtra, EXTRAS_COL_ID + " = " + extra.getId(), null);
    }

    public void updateScreenshot(Scrnshoot.Screenshot screenshot) {
        final SQLiteDatabase db = this.getWritableDatabase();

        final ContentValues valuesScreenshot = new ContentValues();

        valuesScreenshot.put(SCREENSHOTS_COL_LOCATION, screenshot.getLocation());

        db.update(TABLE_SCREENSHOTS, valuesScreenshot, SCREENSHOTS_COL_ID + " = " + screenshot.getId(), null);
    }
}
