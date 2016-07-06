package com.example.s3515297.DropboxAndroid;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class LocalFileBrowser extends ListActivity {

    private String navigation_path = File.separator;
    private String chosen_path;
    private int mode;                               // The int passed from the MainActivity so
                                                    // this activity will display properly for
                                                    // browsing file to upload or folder to download into
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.local_file_browser);
        mode = getIntent().getExtras().getInt(MainActivity.BROWSE_LOCAL);

        // The activity is created for file uploading
        if (mode == MainActivity.UPLOAD_FILE_MODE) {                    // Remove the Open button as it is
            Button open_btn = (Button) findViewById(R.id.open_btn);     // unnecessary when we upload file
            ViewGroup layout = (ViewGroup) open_btn.getParent();        // as the folder will automatically open
            layout.removeView(open_btn);                                // when you click

            // Set up Upload button
            Button upload_btn = (Button) findViewById(R.id.upload_btn2);
            upload_btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (chosen_path != null) {
                        setResult(RESULT_OK, new Intent().putExtra(MainActivity.PICK_FILE_MESSAGE, chosen_path));
                        finish();
                    } else {
                        new AlertDialog.Builder(LocalFileBrowser.this).setMessage("Please select a file to upload")
                                .setPositiveButton("OK", null).create().show();
                    }
                }
            });
        }

        // The activity is created for file/folder downloading
        else {
            Button open_btn = (Button) findViewById(R.id.open_btn);
            open_btn.setOnClickListener(new View.OnClickListener() {    // , they can choose whether to
                @Override                                               // proceed to that folder or
                public void onClick(View v) {
                    processDirectory(chosen_path + File.separator);
                }
            });

            // Set up Download button
            Button download_btn = (Button) findViewById(R.id.upload_btn2);
            download_btn.setText(getString(R.string.download_btn2));
            download_btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (chosen_path != null)
                        setResult(RESULT_OK, new Intent().putExtra(MainActivity.PICK_FOLDER_MESSAGE, chosen_path));
                    else
                        setResult(RESULT_OK, new Intent().putExtra(MainActivity.PICK_FOLDER_MESSAGE, navigation_path));
                    finish();
                }
            });
        }

        // Set up Cancel button
        Button cancel_btn = (Button) findViewById(R.id.cancel_btn);
        cancel_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(RESULT_CANCELED, null);
                finish();
            }
        });

        processDirectory(navigation_path);
    }

    // Back one directory if user presses Back button
    @Override
    public void onBackPressed() {
        if (!navigation_path.equals(File.separator)) {
            processDirectory(navigation_path.substring(0, navigation_path.lastIndexOf('/',     // Get the string of the parent
                    navigation_path.length() - 2) + 1));                                       // folder

        }
    }

    // Display contents in a folder
    public void processDirectory(String dir_path) {
        navigation_path = dir_path;                                 // Store the current folder path
        String[] original_contents = new File(dir_path).list();     // The array contains all files/folders inside the dir_path directory
        ArrayList<String> contents  = new ArrayList<>();

        if (mode == MainActivity.DOWNLOAD_MODE) {                   // If the activity is created to choose
            for (String name : original_contents) {                 // a folder to download file into,
                if (new File(dir_path + name).isDirectory())        // we list folders only
                    contents.add(name);
            }
        }
        else
            if (original_contents != null)
                contents = new ArrayList<>(Arrays.asList(original_contents));

        if (contents.size() != 0) {
            // Put the data into the list and display it
            Collections.sort(contents);
            setListAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, contents));
        }
        else
            setListAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, android.R.id.text1));

        if (mode == MainActivity.UPLOAD_FILE_MODE)                  // User has to choose file before clicking upload
            chosen_path = null;
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        String filename = (String) getListAdapter().getItem(position);
        filename = navigation_path + filename;
        if (mode == MainActivity.UPLOAD_FILE_MODE)
            if (new File(filename).isDirectory())               // When user clicks on a folder, move inside that folder
                processDirectory(filename + File.separator);
            else
                chosen_path = filename;
        else
            chosen_path = filename;
    }



}