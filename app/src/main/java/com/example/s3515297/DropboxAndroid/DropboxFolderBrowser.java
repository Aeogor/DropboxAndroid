package com.example.s3515297.DropboxAndroid;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.LinearLayout.*;
import android.widget.ListView;
import android.widget.TextView;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.exception.DropboxException;

import java.io.File;
import java.util.ArrayList;

public class DropboxFolderBrowser extends ListActivity {
    private String navigation_path = File.separator;
    private String folder_chosen_path;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.local_file_browser);

        Button open_btn = (Button) findViewById(R.id.open_btn);
        // When user clicks Open button, folders inside the chosen folder is displayed
        open_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (folder_chosen_path == null)
                    simpleDialog(getString(R.string.folder_missing));
                else
                    new ListFiles(folder_chosen_path).execute();
            }
        });

        // When user clicks Move button, transfer the chosen folder to MainActivity to continue the moving process
        Button move_btn = (Button) findViewById(R.id.upload_btn2);
        move_btn.setText(getString(R.string.move_btn));
        move_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (folder_chosen_path == null) {
                    setResult(RESULT_OK, new Intent().putExtra(MainActivity.PICK_FOLDER_MESSAGE, navigation_path));
                    finish();
                }
                else {
                    setResult(RESULT_OK, new Intent().putExtra(MainActivity.PICK_FOLDER_MESSAGE, folder_chosen_path));
                    finish();
                }
            }
        });

        // Set up Cancel button
        Button cancel_btn = (Button) findViewById(R.id.cancel_btn);
        cancel_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(RESULT_CANCELED, null);
                finish();
            }
        });

        new ListFiles(navigation_path).execute();
    }

    // Back one directory if user presses Back button
    @Override
    public void onBackPressed() {
        if (!navigation_path.equals(File.separator)) {
            new ListFiles(navigation_path.substring(0, navigation_path.lastIndexOf('/',
                    navigation_path.length() - 2) + 1)).execute();
        }
    }

    // Fetch metadata and display all folders inside a folder
    private class ListFiles extends AsyncTask<Void, Void, ArrayList> {
        //private String path;                                            // The path of the folder that we fetch data
        private AlertDialog progress = new AlertDialog.Builder(DropboxFolderBrowser.this).setMessage("Loading data...")
                .setCancelable(false).create();                         // Dialog to inform user that data is being loaded

        public ListFiles(String dir_path) {
            progress.show();
            navigation_path = dir_path;
        }

        @Override
        protected ArrayList doInBackground(Void... params) {
            ArrayList<String> folders = new ArrayList<>();         // The list of entries in the given Dropbox's folder
            if (folder_chosen_path == null || folder_chosen_path.equals(File.separator))
                folders.add("Home");
            try {
                DropboxAPI.Entry directory = MainActivity.myDBApi.metadata(navigation_path, 100, null, true, null);
                for (DropboxAPI.Entry entry : directory.contents)
                    if (entry.isDir)
                        folders.add(entry.fileName());
            } catch (DropboxException e) {
                simpleDialog(e.getMessage());
            }
            return folders;
        }

        @Override
        protected void onPostExecute(ArrayList result) {
            // Display all files/folder in our list of entries
            setListAdapter(new ArrayAdapter<String>(DropboxFolderBrowser.this, android.R.layout.simple_list_item_1, result));
            progress.dismiss();
        }
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        String foldername = (String) getListAdapter().getItem(position);
        if (foldername.equals("Home"))
            folder_chosen_path = File.separator;
        else
            folder_chosen_path = navigation_path + foldername + File.separator;     // When user clicks a file, store the file path
    }

    public void simpleDialog(String message) {
        new AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK", null).create().show();
    }
}
