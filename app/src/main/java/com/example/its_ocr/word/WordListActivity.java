/*
 * The MIT License (MIT)
 * 
 * Copyright (c) 2015 baoyongzhang <baoyz94@gmail.com>
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.example.its_ocr.word;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.baoyz.swipemenulistview.SwipeMenu;
import com.baoyz.swipemenulistview.SwipeMenuCreator;
import com.baoyz.swipemenulistview.SwipeMenuItem;
import com.baoyz.swipemenulistview.SwipeMenuListView;
import com.example.its_ocr.R;

import java.util.ArrayList;
import java.util.List;

/**
 * SwipeMenuListView
 * Created by baoyz on 15/6/29.
 */
public class WordListActivity extends Activity {

    View dialogView;
    View dialogViewEdit;
    SwipeMenuListView listView;
    String contents;
    int REQUEST_TEST;
    MyDBHelper mDBHelper;
    Cursor cursor;
    SQLiteDatabase db;
    Intent myintent;
    Button plus;
    private AppAdapter mAdapter;
    private List<AppAdapter> mAppList;
    EditText editMemo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);

        //리스트 뷰, 어댑터 설정
        listView = (SwipeMenuListView) findViewById(R.id.listView);
        mAppList = new ArrayList<>();
        mAdapter = new AppAdapter(this);
        listView.setAdapter(mAdapter);

        //추가버튼
        plus = (Button) findViewById(R.id.plus);

        //데이터베이스 읽기, 리스트뷰 출력
        mDBHelper = new MyDBHelper(getApplicationContext(), "Today.db", null, 1);
        db = mDBHelper.getWritableDatabase();
        cursor = db.rawQuery("SELECT * FROM word", null);
        while (cursor.moveToNext()) {
            contents = cursor.getString(1);

            mAdapter.addItem(contents);
            mAdapter.notifyDataSetChanged();
        }
        mDBHelper.close();
        mAdapter.notifyDataSetChanged();

        //추가버튼 클릭
        plus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                dialogView = (View) View.inflate(WordListActivity.this, R.layout.activity_word_add, null);
                AlertDialog.Builder dlg = new AlertDialog.Builder(WordListActivity.this);

                editMemo = (EditText) dialogView.findViewById(R.id.editmemo);

                dlg.setView(dialogView);

                dlg.setPositiveButton("저장", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        try {

                            mDBHelper = new MyDBHelper(getApplicationContext(), "Today.db", null, 1);

                            SQLiteDatabase db2 = mDBHelper.getReadableDatabase();
                            cursor = db2.rawQuery("SELECT * FROM word WHERE memo = '"+editMemo.getText().toString()+"'", null);

                            if(cursor.getCount() == 0){
                                SQLiteDatabase db = mDBHelper.getWritableDatabase();
                                db.execSQL("INSERT INTO word VALUES(null, '" + editMemo.getText().toString() + "');");
                                mDBHelper.close();

                                mAdapter.addItem(editMemo.getText().toString());
                                mAdapter.notifyDataSetChanged();
                                setResult(RESULT_OK);
                                Toast.makeText(getApplicationContext(), "저장되었습니다", Toast.LENGTH_SHORT).show();
                            }
                            else{
                                Toast.makeText(getApplicationContext(), "있는 단어입니다", Toast.LENGTH_SHORT).show();
                            }

                        } catch (Exception e) {
                            Log.e("DB에러", "" + e.getMessage());
                            Toast.makeText(getApplicationContext(), "" + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                mAdapter.notifyDataSetChanged();
                dlg.setNegativeButton("취소", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Toast.makeText(getApplicationContext(), "취소했습니다.", Toast.LENGTH_SHORT).show();
                        mAdapter.notifyDataSetChanged();
                    }
                });
                //adapter1.notifyDataSetChanged();
                dlg.show();
                mAdapter.notifyDataSetChanged();
            }
        });

        // create a MenuCreator
        SwipeMenuCreator creator = new SwipeMenuCreator() {
            @Override
            public void create(SwipeMenu menu) {
                createMenu1(menu);
            }

            private void createMenu1(SwipeMenu menu) {
                SwipeMenuItem item1 = new SwipeMenuItem(getApplicationContext());
                item1.setTitle("수정");
                item1.setTitleSize(15);
                item1.setTitleColor(Color.WHITE);
                item1.setBackground(new ColorDrawable(Color.rgb(142,179, 255)));
                  item1.setWidth(dp2px(80));
                 // item1.setIcon(R.drawable.ic_action_important);

                menu.addMenuItem(item1);
                SwipeMenuItem item2 = new SwipeMenuItem(getApplicationContext());
                item2.setTitle("삭제");
                item2.setTitleSize(15);
                item2.setTitleColor(Color.WHITE);
                item2.setBackground(new ColorDrawable(Color.rgb(239, 116,116)));
                item2.setWidth(dp2px(80));
                // item2.setIcon(R.drawable.ic_action_discard);
                menu.addMenuItem(item2);
            }
        };
        // set creator
        listView.setMenuCreator(creator);

        // 리스트 메뉴 아이템 클릭
        listView.setOnMenuItemClickListener(new SwipeMenuListView.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(final int position, SwipeMenu menu, int index) {
                switch (index) {
                    case 0:

                        dialogViewEdit = (View) View.inflate(WordListActivity.this, R.layout.activity_word_add, null);
                        AlertDialog.Builder dlg2 = new AlertDialog.Builder(WordListActivity.this);

                        editMemo = (EditText) dialogViewEdit.findViewById(R.id.editmemo);
                        editMemo.setText(mAppList.get(position).getContent());

                        dlg2.setView(dialogViewEdit);

                        dlg2.setPositiveButton("수정", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                try {
                                    mDBHelper = new MyDBHelper(getApplicationContext(), "Today.db", null, 1);

                                    SQLiteDatabase db2 = mDBHelper.getReadableDatabase();

                                    cursor = db2.rawQuery("SELECT * FROM word WHERE memo = '"+editMemo.getText().toString()+"'", null);
                                    if(cursor.getCount() == 0){
                                        SQLiteDatabase db = mDBHelper.getWritableDatabase();
                                        db.execSQL("UPDATE word SET memo = '"+editMemo.getText().toString()+"' WHERE memo='"+mAppList.get(position).getContent()+"';");
                                        setResult(RESULT_OK);
                                        mAdapter.changeItem(position, editMemo.getText().toString());
                                        Toast.makeText(getApplicationContext(), "수정 되었습니다", Toast.LENGTH_SHORT).show();
                                    }else{
                                        Toast.makeText(getApplicationContext(), "있는 단어입니다", Toast.LENGTH_SHORT).show();
                                    }
                                    mDBHelper.close();
                                    mAdapter.notifyDataSetChanged();

                                } catch (Exception e) {
                                    Log.e("DB에러", "" + e.getMessage());
                                    Toast.makeText(getApplicationContext(), "" + e.getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                        mAdapter.notifyDataSetChanged();
                        dlg2.setNegativeButton("취소", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                Toast.makeText(getApplicationContext(), "취소했습니다.", Toast.LENGTH_SHORT).show();
                                mAdapter.notifyDataSetChanged();
                            }
                        });
                        //adapter1.notifyDataSetChanged();
                        dlg2.show();
                        mAdapter.notifyDataSetChanged();
                        break;
                    case 1:
                        // delete
                        Toast.makeText(getApplicationContext(),"삭제되었습니다",Toast.LENGTH_SHORT).show();
                        SQLiteDatabase db = mDBHelper.getWritableDatabase();
                        db.execSQL("DELETE FROM word WHERE memo='"+mAppList.get(position).getContent()+"';");

                        mAppList.remove(position);
                        mAdapter.notifyDataSetChanged();
                        break;
                }
                return false;
            }
        });

        //리스트뷰 아이템 클릭
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

            }
        });

    }

    class AppAdapter extends BaseAdapter {

        String contentStr;
        private final Activity context;

        //생성자
        public AppAdapter(Activity context) {
            this.context = context;
        }

        public void setContent(String content) {
            contentStr = content;
        }

        public String getContent() {
            return this.contentStr;
        }

        // 아이템 데이터 추가를 위한 함수. 개발자가 원하는대로 작성 가능.
        public void addItem(String content) {
            AppAdapter item = new AppAdapter(WordListActivity.this);

            item.setContent(content);
            contentStr = content;

            mAppList.add(item);
            mAdapter.notifyDataSetChanged();
        }

        public void changeItem(int i,String content) {
            AppAdapter item = new AppAdapter(WordListActivity.this);

            item.setContent(content);
            contentStr = content;

            mAppList.set(i,item);
            mAdapter.notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mAppList.size();
        }

        @Override
        public AppAdapter getItem(int position) {
            return mAppList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }


        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = View.inflate(getApplicationContext(),
                        R.layout.item_listview, null);
                new ViewHolder(convertView);
            }

            ViewHolder holder = (ViewHolder) convertView.getTag();
            AppAdapter item = getItem(position);
           //holder.tv_name.setText(item.loadLabel(getPackageManager()));
           // holder.tv_name.setText(contents);

            // 아이템 내 각 위젯에 데이터 반영
            holder.tv_name.setText(mAppList.get(position).getContent());

            return convertView;
        }

        class ViewHolder {
            TextView tv_name;

            public ViewHolder(View view) {
                tv_name = (TextView) view.findViewById(R.id.tv_name);
                view.setTag(this);
            }
        }
    }

    private int dp2px(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                getResources().getDisplayMetrics());
    }
}