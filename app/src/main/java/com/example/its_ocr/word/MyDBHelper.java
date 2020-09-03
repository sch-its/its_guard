package com.example.its_ocr.word;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;

public class MyDBHelper extends SQLiteOpenHelper {
    static final String DATABASE_NAME = "Today.db";

    public MyDBHelper(Context context, String name, CursorFactory factory, int version) {
        super(context, DATABASE_NAME, null, version);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // TODO Auto-generated method stub
        db.execSQL("CREATE TABLE word(_id INTEGER PRIMARY KEY AUTOINCREMENT, " + "memo TEXT);");
        db.execSQL("CREATE TABLE secret_memo(_id INTEGER PRIMARY KEY AUTOINCREMENT, " + "content TEXT);");
        db.execSQL("CREATE TABLE parsing_count(_id INTEGER PRIMARY KEY AUTOINCREMENT, " + "parsingname TEXT,"+"parsingnum TEXT);");
        // db.execSQL("CREATE TABLE word(_id INTEGER PRIMARY KEY AUTOINCREMENT, " + "date TEXT , "+ "memo TEXT );");
    }
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // TODO Auto-generated method stub
        db.execSQL("DROP TABLE IF EXISTS word;");
        db.execSQL("DROP TABLE IF EXISTS secret_memo;");
        db.execSQL("DROP TABLE IF EXISTS parsing_count;");
        onCreate(db);
    }

}