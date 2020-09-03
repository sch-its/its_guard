package com.example.its_ocr.textDetection;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.os.Environment;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.example.its_ocr.MainActivity;
import com.example.its_ocr.R;
import com.example.its_ocr.word.MyDBHelper;
import com.example.its_ocr.word.WordListActivity;
import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;


public class Tess{
    private static final String TAG = "Tess";
    /**
     * 테서렉트 관련 선언
     */
    private static final String DATA_PATH = Environment.getExternalStorageDirectory().toString() + "/its-ocs/";
    public static String lang = "kor";

    private static Context mContext;
    private static MyDBHelper mDBHelper;
    private Cursor cursor;
    private Cursor cursor2;
    private SQLiteDatabase db;
    private List<String> wordList = new ArrayList<>();
    int count = 0;
    MainActivity mainActivity = new MainActivity();

    public Tess(Context context) throws IOException {
        mContext = context;
        checkFile();
    }

    public static void checkFile() throws IOException {


        String[] paths = new String[] { DATA_PATH, DATA_PATH + "tessdata/" };


        for (String path : paths) {
            File dir = new File(path);
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    Log.v(TAG, "ERROR: Creation of directory " + path + " on sdcard failed");
                    return;
                } else {
                    Log.v(TAG, "Created directory " + path + " on sdcard");
                }
            }
        }

        if (!(new File(DATA_PATH + "tessdata/" + lang + ".traineddata")).exists()) {
            try {

                AssetManager assetManager = mContext.getResources().getAssets();
                InputStream in = assetManager.open("tessdata/" + lang + ".traineddata");
                //GZIPInputStream gin = new GZIPInputStream(in);
                OutputStream out = new FileOutputStream(DATA_PATH
                        + "tessdata/" + lang + ".traineddata");
                // Transfer bytes from in to out
                byte[] buf = new byte[1024];
                int len;
                //while ((lenf = gin.read(buff)) > 0) {

                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }

                in.close();
                //gin.close();

                out.close();

                Log.v(TAG, "Copied " + lang + " traineddata");
            } catch (IOException e) {
                Log.e(TAG, "Was unable to copy " + lang + " traineddata " + e.toString());
            }
        }
    }

    public void textDetection(Bitmap bitmap) {
        String result = "No result";

        if(bitmap == null) {
            Log.d("textDetection", "이미지가 없읍니다");
            return;
        }

        try {
            TessBaseAPI tessBaseAPI = new TessBaseAPI();
            tessBaseAPI.init(DATA_PATH, lang);
            //tessBaseAPI.setVariable(TessBaseAPI.VAR_CHAR_BLACKLIST, "$#@%&*()_+=÷-[]}{;'\"\\|`," +
            //        ".:/<>" + "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz");
            bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
            //여기에 전처리한 후 이미지
            tessBaseAPI.setImage(bitmap);
            result = tessBaseAPI.getUTF8Text();
            tessBaseAPI.clear();
            tessBaseAPI.end();
            Log.e("textDetection 테서렉트 원문", result);

            mDBHelper = new MyDBHelper(mContext, "Today.db", null, 1);

            String[] array = result.split(" |,|'|&|#|%");

            SQLiteDatabase db = mDBHelper.getWritableDatabase();

            for(int j =0; j<array.length; j++){
                db.execSQL("INSERT INTO secret_memo VALUES(null, '"+array[j]+"');");
            }

            SQLiteDatabase db2 = mDBHelper.getReadableDatabase();
            cursor = db2.rawQuery("SELECT * FROM word;", null);
            while (cursor.moveToNext()) {
                wordList.add(cursor.getString(1));
            }

            count = 0;
            for (int i = 0; i < wordList.size(); i++) {
                cursor2 = db2.rawQuery("SELECT * FROM secret_memo WHERE content LIKE '%" + wordList.get(i) + "%';", null);
                //while (cursor2.moveToNext()) {
                if (cursor2.getCount() == 0) {
                    count = cursor2.getCount();
                } else {
                    count = cursor2.getCount();
                    db.execSQL("INSERT INTO parsing_count VALUES(null, '"+wordList.get(i)+"', '"+count+"');");
                }

            }

            wordList.clear();
            db.execSQL("DELETE FROM secret_memo;");
            mDBHelper.close();

            Log.e("textDetection 테서렉트 ", result);

        } catch (Exception e) {
            Log.d("textDetection", "catch 안에서 문제");
            e.printStackTrace();
        }
    }
 public String getContent(String name){
        return name;
 }

    public int getNum(int count){
        return count;
    }

}



