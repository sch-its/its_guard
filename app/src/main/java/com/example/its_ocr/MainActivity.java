package com.example.its_ocr;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.example.its_ocr.camera.BitmapUtils;
import com.example.its_ocr.camera.GraphicOverlay;
import com.example.its_ocr.textDetection.Tess;
import com.example.its_ocr.word.MyDBHelper;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements OnRequestPermissionsResultCallback {
    static {
        System.loadLibrary("native-lib");
    }

    private static final String[] PERMISSIONS = {
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.CAMERA
    };

    MyDBHelper mDBHelper;
    Cursor cursor;
    SQLiteDatabase db;

    private static final int REQUEST_IMAGE_CAPTURE = 1001;
    private static final int REQUEST_CHOOSE_IMAGE = 1002;

    private static final String KEY_IMAGE_URI = "com.example.its.KEY_IMAGE_URI";

    private ImageView preview;
    private GraphicOverlay graphicOverlay;
    private Bitmap imageBitmap;
    private Bitmap imageBitmap2;

    private Tess tess;

    boolean isLandScape;

    private Uri imageUri;
    private int imageMaxWidth;
    private int imageMaxHeight;

    ImageButton settingsButton;

    View dialogView;
    //public listAdapter mAdapter;
    List<listAdapter> parsingList;
    //////////////////////////////// for JNI
    private int imgHeight;
    private int imgWidth;
    private Mat matInput;
    private Mat matResult;
    private Mat matPrepross;
    public native void detectRoI(long matAddrInput, long matAddrResult, long matAddrPreprocess);


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_still_image);

        //리스트 뷰, 어댑터 설정
        parsingList = new ArrayList<listAdapter>();




        findViewById(R.id.select_image_button)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                // Menu for selecting either: a) take new photo b) select from existing
                                PopupMenu popup = new PopupMenu(MainActivity.this, view);
                                popup.setOnMenuItemClickListener(
                                        new PopupMenu.OnMenuItemClickListener() {
                                            @Override
                                            public boolean onMenuItemClick(MenuItem menuItem) {
                                                int itemId = menuItem.getItemId();
                                                if (itemId == R.id.select_images_from_local) {
                                                    startChooseImageIntentForResult();
                                                    return true;
                                                } else if (itemId == R.id.take_photo_using_camera) {
                                                    startCameraIntentForResult();
                                                    return true;
                                                }
                                                return false;
                                            }
                                        });
                                MenuInflater inflater = popup.getMenuInflater();
                                inflater.inflate(R.menu.camera_button_menu, popup.getMenu());
                                popup.show();
                            }
                        });

        settingsButton = (ImageButton) findViewById(R.id.settings_button);

        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent listIntent = new Intent(view.getContext(), com.example.its_ocr.word.WordListActivity.class);
                startActivity(listIntent);
            }
        });

        preview = findViewById(R.id.preview);
        graphicOverlay = findViewById(R.id.graphic_overlay);

        isLandScape = (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);

        final View rootView = findViewById(R.id.root);
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @RequiresApi(api = Build.VERSION_CODES.N)
            @Override
            public void onGlobalLayout() {
                rootView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                imageMaxWidth = rootView.getWidth();
                imageMaxHeight = rootView.getHeight() - findViewById(R.id.control).getHeight();
                tryLoadAndDetectInImage();
            }
        });

        try {
            tess = new Tess(this);
        } catch (IOException e) {
            e.printStackTrace();
        }

        findViewById(R.id.detect_button).setOnClickListener(
                        new View.OnClickListener() {
                            @RequiresApi(api = Build.VERSION_CODES.N)
                            @Override
                            public void onClick(View view) {

                                if (imageBitmap2 == null) {
                                    Toast.makeText(getApplicationContext(), "이미지를 먼저 선택해주세요", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                tess.textDetection(imageBitmap2);
                                graphicOverlay.clear();

                                Pair<Integer, Integer> targetedSize = getTargetedWidthHeight();

                                float scaleFactor2 =
                                        Math.max(
                                                (float) imageBitmap2.getWidth() / (float) targetedSize.first,
                                                (float) imageBitmap2.getHeight() / (float) targetedSize.second);

                                Bitmap resizedBitmap2 =
                                        Bitmap.createScaledBitmap(
                                                imageBitmap2,
                                                (int) (imageBitmap2.getWidth() / scaleFactor2),
                                                (int) (imageBitmap2.getHeight() / scaleFactor2),
                                                true);


                                preview.setImageBitmap(resizedBitmap2);


                                //다이알로그
                                dialogView = (View) View.inflate(MainActivity.this, R.layout.activity_parsing_result, null);

                                AlertDialog.Builder dlg = new AlertDialog.Builder(MainActivity.this);

                                dlg.setView(dialogView);


                                final listAdapter mAdapter = new listAdapter(MainActivity.this);
                                ListView plistView = (ListView) dialogView.findViewById(R.id.parsingList);

                                plistView.setAdapter(mAdapter);

                                mDBHelper = new MyDBHelper(getApplicationContext(), "Today.db", null, 1);
                                db = mDBHelper.getWritableDatabase();
                                cursor = db.rawQuery("SELECT * FROM parsing_count", null);

                                if(cursor.getCount() == 0){

                                TextView lv = (TextView)dialogView.findViewById(R.id.emptyText);
                                plistView.setEmptyView(lv);
                                }
                                while (cursor.moveToNext()) {
                                    String contents = cursor.getString(1);
                                    int count = Integer.parseInt(cursor.getString(2));
                                    mAdapter.addItem(contents, count);
                                    mAdapter.notifyDataSetChanged();
                                }
                                mDBHelper.close();
                                mAdapter.notifyDataSetChanged();

                                dlg.setOnDismissListener(new DialogInterface.OnDismissListener() {
                                    @Override
                                    public void onDismiss(DialogInterface dialogInterface) {
                                        parsingList.clear();
                                        SQLiteDatabase db = mDBHelper.getWritableDatabase();
                                        db.execSQL("DELETE FROM parsing_count");
                                        mAdapter.notifyDataSetChanged();

                                    }
                                });

                                dlg.setPositiveButton("확인", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        try {
                                            parsingList.clear();
                                            SQLiteDatabase db = mDBHelper.getWritableDatabase();
                                            db.execSQL("DELETE FROM parsing_count");
                                            mAdapter.notifyDataSetChanged();

                                        } catch (Exception e) {
                                            Log.e("에러", "" + e.getMessage());
                                            Toast.makeText(getApplicationContext(), "" + e.getMessage(), Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                });
                            //adapter1.notifyDataSetChanged();
                                dlg.show();
                                mAdapter.notifyDataSetChanged();
                            }
                        });
        if(!checkPermissions()){
            requestPermission();
        }
         }

  public class listAdapter extends BaseAdapter{
        String nameStr;
        int countInt;
        private Activity context;

        //생성자
        public listAdapter(Activity context) {
            this.context = context;
        }
        public listAdapter(MainActivity mainActivity) {
        }

        public void setName(String name) {
            this.nameStr = name;
        }

        public String getName() {
            return this.nameStr;
        }

        public void setCount(int count) {
            this.countInt = count;
        }

        public String getCountnum() {
            return this.countInt+"";
        }

        // 아이템 데이터 추가를 위한 함수. 개발자가 원하는대로 작성 가능.
        public void addItem(String name, int count) {
            listAdapter item = new listAdapter(MainActivity.this);

            item.setName(name);
            nameStr = name;
            item.setCount(count);
            countInt = count;

            parsingList.add(item);
        }


        @Override
        public int getCount() {
            return parsingList.size();
        }

        @Override
        public listAdapter getItem(int position) {
            return parsingList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }


        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = View.inflate(getApplicationContext(), R.layout.item_parsing, null);
                new ViewHolder(convertView);
            }

            ViewHolder holder = (ViewHolder) convertView.getTag();
            listAdapter item = getItem(position);
            //holder.tv_name.setText(item.loadLabel(getPackageManager()));
            // holder.tv_name.setText(contents);

            // 아이템 내 각 위젯에 데이터 반영
            holder.parsingWord.setText(parsingList.get(position).getName());
            holder.parsingCount.setText(parsingList.get(position).getCountnum());

            return convertView;
        }

        class ViewHolder {
            TextView parsingWord;
            TextView parsingCount;

            public ViewHolder(View view) {
                parsingWord = (TextView) view.findViewById(R.id.parsingWord);
                parsingCount = (TextView) view.findViewById(R.id.parsingCount);
                view.setTag(this);
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_IMAGE_URI, imageUri);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.still_image_menu, menu);
        return true;
    }

    private void startCameraIntentForResult() {
        imageUri = null;
        preview.setImageBitmap(null);

        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.TITLE, "New Picture");
            values.put(MediaStore.Images.Media.DESCRIPTION, "From Camera");
            imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }

    private void startChooseImageIntentForResult() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_PICK);
        startActivityForResult(intent, REQUEST_CHOOSE_IMAGE);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            tryLoadAndDetectInImage();
        } else if (requestCode == REQUEST_CHOOSE_IMAGE && resultCode == RESULT_OK) {
            imageUri = data.getData();
            tryLoadAndDetectInImage();
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void tryLoadAndDetectInImage() {
        try {
            if (imageUri == null) {
                return;
            }

            if (imageMaxWidth == 0) {
                return;
            }


            imageBitmap = BitmapUtils.getBitmapFromContentUri(getContentResolver(), imageUri);
            imageBitmap2 = imageBitmap;

            if (imageBitmap == null) {
                return;
            }

            //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!     by injae
            //convert to Mat
            matInput = new Mat(imageBitmap.getHeight(), imageBitmap.getWidth(), CvType.CV_8UC4);
            matResult = new Mat(imageBitmap.getHeight(), imageBitmap.getWidth(), CvType.CV_8UC4);
            matPrepross = new Mat(imageBitmap.getHeight(), imageBitmap.getWidth(), CvType.CV_8UC4);
            Utils.bitmapToMat(imageBitmap, matInput);

           //네모 그려주기
            detectRoI(matInput.getNativeObjAddr(), matResult.getNativeObjAddr(), matPrepross.getNativeObjAddr());

            //네모 그린 것
            Utils.matToBitmap(matResult, imageBitmap);
            //자른 것
            Utils.matToBitmap(matPrepross, imageBitmap2);
            //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!    ---------

            graphicOverlay.clear();

            Pair<Integer, Integer> targetedSize = getTargetedWidthHeight();

            float scaleFactor =
                    Math.max(
                            (float) imageBitmap.getWidth() / (float) targetedSize.first,
                            (float) imageBitmap.getHeight() / (float) targetedSize.second);

            Bitmap resizedBitmap =
                    Bitmap.createScaledBitmap(
                            imageBitmap,
                            (int) (imageBitmap.getWidth() / scaleFactor),
                            (int) (imageBitmap.getHeight() / scaleFactor),
                            true);


            preview.setImageBitmap(resizedBitmap);
        } catch (IOException e) {
            imageUri = null;
        }
    }

    private Pair<Integer, Integer> getTargetedWidthHeight() {
        return new Pair<>(imageMaxWidth, imageMaxHeight);
    }


    // 권한 확인
    private boolean checkPermissions() {
        for(String permission : PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
                return true;
            } else {
            }
        }
        return false;
    }

    // 권한 요청
    private void requestPermission() {
        ActivityCompat.requestPermissions(this,
                PERMISSIONS, 0);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResult){
        super.onRequestPermissionsResult(requestCode, permissions, grantResult);
        if(requestCode == 0){
            if(grantResult[0] == 0){
                // 해당 권한이 승낙된 경우
            }else{ // 권한이 거절된 경우
                finish(); // 강종
            }
        }
    }

}
