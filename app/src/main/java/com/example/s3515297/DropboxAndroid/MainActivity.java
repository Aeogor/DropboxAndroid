package com.example.s3515297.DropboxAndroid;


import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ScaleDrawable;
import android.os.AsyncTask;
import android.os.Bundle;

import android.os.Environment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.*;
import android.view.View.OnClickListener;

import com.dropbox.client2.*;
import com.dropbox.client2.DropboxAPI.*;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.exception.*;
import com.dropbox.client2.session.AppKeyPair;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;


public class MainActivity extends AppCompatActivity {
    final static private String APP_KEY = "y80sihff7o9wai7";
    final static private String APP_SECRET = "cqbt9r8y75am81m";
    protected static DropboxAPI<AndroidAuthSession> myDBApi;

    private GridView gridView;
    private String current_directory = File.separator;
    private String previous_directory;
    private Entry chosen_entry;
    private ArrayList<Entry> current_entries;
    private static boolean should_execute_onResume = true;
    static final int UPLOAD_FILE_MODE = 1;         // The codes are used when starting other activities for result
    static final int MOVE_TO_FOLDER_MODE = 2;
    static final int DOWNLOAD_MODE = 3;
    static final String PICK_FILE_MESSAGE = "file";
    static final String PICK_FOLDER_MESSAGE = "folder";
    static final String BROWSE_LOCAL = "local";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AppKeyPair appKeys = new AppKeyPair(APP_KEY, APP_SECRET);
        AndroidAuthSession session = new AndroidAuthSession(appKeys);
        myDBApi = new DropboxAPI<>(session);
        myDBApi.getSession().startOAuth2Authentication(MainActivity.this);

        gridView = (GridView) findViewById(R.id.gridView);

        // When user clicks upload, a new activity will be triggered
        Button upload_btn = (Button) findViewById(R.id.upload_Btn1);
        upload_btn.setBackground(new ColorDrawable(getResources().getColor(R.color.green)));
        upload_btn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, LocalFileBrowser.class);
                intent.putExtra(BROWSE_LOCAL, UPLOAD_FILE_MODE);
                startActivityForResult(intent, UPLOAD_FILE_MODE);
            }
        });
        setActionBar();
    }

    public void setActionBar() {
        android.support.v7.app.ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.green)));
            LayoutInflater inflater = LayoutInflater.from(this);
            View custom_view = inflater.inflate(R.layout.custom_actionbar, null);

            ImageView home_btn = (ImageView) custom_view.findViewById(R.id.home_btn);
            home_btn.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    current_directory = File.separator;
                    previous_directory = null;
                    new ListFiles(current_directory).execute();
                }
            });

            ImageView logout_btn = (ImageView) custom_view.findViewById(R.id.logout_btn);
            logout_btn.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    recreate();
                }
            });
            ImageView refresh_btn = (ImageView) custom_view.findViewById(R.id.refresh_btn);
            refresh_btn.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    new ListFiles(current_directory).execute();
                }
            });

            actionBar.setCustomView(custom_view);
            actionBar.setDisplayShowCustomEnabled(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.home_btn:
                current_directory = File.separator;
                previous_directory = null;
                new ListFiles(current_directory).execute();
                break;
            case R.id.logout_btn:
                recreate();
                break;
            case R.id.refresh_btn:
                new ListFiles(current_directory).execute();
        }
        return true;
    }

    // Resume the activity after logging in
    protected void onResume() {
        super.onResume();
        if (!should_execute_onResume) {
            should_execute_onResume = true;
            return;
        }
        if (myDBApi.getSession().authenticationSuccessful()) {
            try {
                myDBApi.getSession().finishAuthentication();
                // List files / folders in home directory
                new ListFiles(current_directory).execute();
            } catch (IllegalStateException e) {
                Log.i("DbAuthLog", "Error authenticating", e);
            }
        }
    }


    @Override
    // Handle result from another activity
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == UPLOAD_FILE_MODE) {
            if (resultCode == RESULT_OK) {
                String local_path = data.getStringExtra(PICK_FILE_MESSAGE);
                new Upload(local_path, "Uploading...").execute();
            }
            else
                should_execute_onResume = false;
        }
        else if (requestCode == MOVE_TO_FOLDER_MODE) {
            if (resultCode == RESULT_OK) {
                String destination = data.getStringExtra(PICK_FOLDER_MESSAGE);
                String source = chosen_entry.parentPath() + chosen_entry.fileName();
                new Move(source, destination + source.substring(
                        source.lastIndexOf(File.separator)), true).execute();
            }
            should_execute_onResume = false;
        }
        else {
            if (resultCode == RESULT_OK) {
                String destination = data.getStringExtra(PICK_FOLDER_MESSAGE);
                new Download(chosen_entry.parentPath() + chosen_entry.fileName(), destination, false).execute();
            }
            should_execute_onResume = false;
        }
    }

    @Override
    public void onBackPressed() {
        if (TextUtils.isEmpty(previous_directory))
            new ListFiles(File.separator).execute();
        else if (!previous_directory.equals(File.separator))
            new ListFiles(previous_directory).execute();
    }

    // Display items into a list
    private class ListOfItems extends BaseAdapter {
        private ArrayList<Entry> entries;

        public ListOfItems(ArrayList<Entry> entries) {
            this.entries = entries;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            TextView textView;
            if (convertView == null) {
                Entry entry = entries.get(position);
                textView = new TextView(MainActivity.this);
                textView.setText(entry.fileName());
                textView.setTextColor(Color.BLACK);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
                textView.setPadding(10, 10, 0, 10);

                // Set icon for file / folder
                Drawable img;
                String file_name = entry.fileName().toLowerCase();
                if (entry.isDir) {
                    img = ContextCompat.getDrawable(MainActivity.this, R.drawable.blank_icon);
                }
                else if (checkEndsWith(file_name, new String[]{".jpe?g", ".png", ".gif", ".tiff?"}))
                    img = ContextCompat.getDrawable(MainActivity.this, R.drawable.image_icon);
                else if (checkEndsWith(file_name, new String[]{".mp4", ".avi", ".mov", ".flv", ".swf"}))
                    img = ContextCompat.getDrawable(MainActivity.this, R.drawable.video_icon);
                else if (checkEndsWith(file_name, new String[]{".mp3", ".wav", ".wma"}))
                    img = ContextCompat.getDrawable(MainActivity.this, R.drawable.image_icon);
                else if (checkEndsWith(file_name, new String[]{".7z", ".zip", ".rar", ".tar"}))
                    img = ContextCompat.getDrawable(MainActivity.this, R.drawable.zip_icon);
                else
                    img = ContextCompat.getDrawable(MainActivity.this, R.drawable.document_icon);

                img = new ScaleDrawable(img, 0, 100, 100).getDrawable();
                if (img != null)
                    img.setBounds(0, 0, 120, 120);

                textView.setCompoundDrawables(img, null, null, null);
                textView.setCompoundDrawablePadding(30);
                textView.setOnClickListener(new ItemOnClick(entry));
            } else
                textView = (TextView) convertView;
            return textView;
        }

        @Override
        public int getCount() {
            return entries.size();
        }
        @Override
        public Objects getItem(int position) {
            return null;
        }
        @Override
        public long getItemId(int position) {
            return position;
        }

        // Open a dialog that contains a list of options for users to choose
        private class ItemOnClick implements OnClickListener {
            Entry entry;

            public ItemOnClick(Entry entry) {
                this.entry = entry;
            }

            // Open a menu when user clicks any folder or file
            public void onClick(View view) {
                final String[] choices_origin = {"Open", "Download", "Rename", "Delete", "Move", "Edit"};
                final String[] choices;
                if (entry.isDir)
                    choices = Arrays.copyOfRange(choices_origin, 0, choices_origin.length - 1);
                else
                    if (entry.fileName().endsWith(".txt"))
                        choices = Arrays.copyOfRange(choices_origin, 1, choices_origin.length);
                    else
                        choices = Arrays.copyOfRange(choices_origin, 1, choices_origin.length - 1);


                // Display the string array using
                ArrayAdapter<String> choices_array = new ArrayAdapter<String>(MainActivity.this,
                        android.R.layout.simple_list_item_1, choices) {
                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        View view = super.getView(position, convertView, parent);
                        TextView text = (TextView) view.findViewById(android.R.id.text1);
                        text.setTextColor(Color.BLACK);
                        return view;
                    }
                };

                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);

                // Set strings of choices to menu dialog that shows up when user clicks any file or folder
                builder.setAdapter(choices_array, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int item_id) {
                        switch (choices[item_id]) {
                            case "Open":
                                new ListFiles(entry.parentPath() + entry.fileName()).execute();
                                break;
                            case "Download":
                                chosen_entry = entry;
                                Intent intent_down = new Intent(MainActivity.this, LocalFileBrowser.class);
                                intent_down.putExtra(BROWSE_LOCAL, DOWNLOAD_MODE);
                                startActivityForResult(intent_down, DOWNLOAD_MODE);
                                break;
                            case "Delete":
                                new Delete(entry.parentPath() + entry.fileName()).execute();
                                break;
                            case "Move":
                                chosen_entry = entry;
                                Intent intent = new Intent(MainActivity.this, DropboxFolderBrowser.class);
                                startActivityForResult(intent, MOVE_TO_FOLDER_MODE);
                                break;
                            case "Rename":
                                showInputDialog(entry.parentPath() + entry.fileName());
                                break;
                            case "Edit":
                                new Download(entry.parentPath() + entry.fileName(),
                                        Environment.getExternalStorageDirectory().getAbsolutePath(), true).execute();
                        }
                    }
                });
                AlertDialog alert = builder.create();
                alert.show();
            }
        }
    }

    // Handle the background process of Fetching all files / folders in a given directory
    private class ListFiles extends AsyncTask<Void, Void, String> {
        private String path;                                            // The path of the folder that we fetch data
        private AlertDialog progress = new AlertDialog.Builder(MainActivity.this).setMessage("Loading data...")
                .setCancelable(false).create();                         // Dialog to inform user that data is being loaded

        public ListFiles(String path) {
            progress.show();                                            // Display that dialog
            this.path = path;
            previous_directory = path.substring(0, path.lastIndexOf(File.separator));
            current_directory = path;
            if (!current_directory.endsWith(File.separator))
                current_directory += File.separator;
        }

        @Override
        protected String doInBackground(Void... params) {
            current_entries = new ArrayList<>();         // The list of entries in the given Dropbox's folder
            try {
                Entry directory = myDBApi.metadata(path, 100, null, true, null);
                for (Entry entry : directory.contents)
                    current_entries.add(entry);
            } catch (DropboxException e) {
                return e.getMessage();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String message) {
            progress.dismiss();
            if (message == null) {                                  // No error occurs
                // Display all files/folder in our list of entries
                gridView.setAdapter(new ListOfItems(current_entries));
            }
            else                                                    // Error has occurred
                showSimpleDialog(message);
        }
    }

    // Handle the background process of Downloading
    private class Download extends AsyncTask<Void, Void, String> {
        private String dropbox_file_path;                            // The path of the file to download in Dropbox
        private String destination;                                  // Where the file will be downloaded into
        private boolean isFileEdit;
        private AlertDialog progress;

        public Download(String dropbox_file_path, String destination, boolean isFileEdit) {
            this.dropbox_file_path = dropbox_file_path;
            this.destination = destination;
            this.isFileEdit = isFileEdit;

            if (isFileEdit)
                progress = new AlertDialog.Builder(MainActivity.this).setMessage("Open file...").setCancelable(false).create();
            else
                progress = new AlertDialog.Builder(MainActivity.this).setMessage("Downloading...").setCancelable(false).create();
            progress.show();
        }

        @Override
        protected String doInBackground(Void... params) {
            File file = new File(destination, dropbox_file_path.substring(dropbox_file_path.lastIndexOf('/') + 1,
                    dropbox_file_path.length()));
            try (FileOutputStream outputStream = new FileOutputStream(file)) {
                myDBApi.getFile(dropbox_file_path, null, outputStream, null);
            } catch (IOException | DropboxException e) {
                return e.getMessage();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String message) {
            progress.dismiss();
            if (message == null)
                if (isFileEdit)
                    editFile(dropbox_file_path);
                else
                    Toast.makeText(getApplicationContext(), "Download finished", Toast.LENGTH_SHORT).show();
            else
                showSimpleDialog(message);
        }

    }

    // Handle the background process of Deleting
    private class Delete extends AsyncTask<Void, Void, String> {
        private String file_path;                                   // Path of the file we want to delete
        private AlertDialog progress = new AlertDialog.Builder(MainActivity.this).setMessage("Deleting...")
                .setCancelable(false).create();
        public Delete(String fileName) {
            progress.show();
            this.file_path = fileName;
        }

        @Override
        protected String doInBackground(Void... params) {
            try {
                myDBApi.delete(file_path);
            } catch (DropboxException e) {
                return e.getMessage();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String message) {
            progress.dismiss();
            if (message == null)
                new ListFiles(current_directory).execute();
            else
                showSimpleDialog(message);
        }
    }

    // Handle the background process of Uploading
    private class Upload extends AsyncTask<Void, Void, String> {
        private String local_path;
        private AlertDialog progress;
        private boolean isEditFile = false;
        public Upload(String local_path, String progress_message) {
            progress = new AlertDialog.Builder(MainActivity.this).setMessage(progress_message).setCancelable(false).create();
            progress.show();
            this.local_path = local_path;
            if (progress_message.equals("Saving..."))
                isEditFile = true;
        }

        @Override
        protected String doInBackground(Void... params) {
            String dropbox_path = current_directory + local_path.substring(local_path.lastIndexOf(File.separator) + 1);
            File file = new File(local_path);
            try (InputStream inputStream = new FileInputStream(file)) {
                myDBApi.putFile(dropbox_path, inputStream, file.length(), null, true, null);
            } catch (DropboxException | IOException e) {
                return e.getMessage();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String message) {
            progress.dismiss();
            if (message == null) {
                if (isEditFile)
                    Toast.makeText(getApplicationContext(), "Changes saved", Toast.LENGTH_LONG).show();
                else
                    Toast.makeText(getApplicationContext(), "Uploaded successfully", Toast.LENGTH_LONG).show();
                new ListFiles(current_directory).execute();
            }
            else
                showSimpleDialog(message);
        }
    }

    // Handle the background process of Moving and Renaming
    private class Move extends AsyncTask<Void, Void, String> {
        private String source_path;
        private String destination;
        private boolean is_move_file;                          // move file or rename
        private AlertDialog progress;

        public Move(String source_path, String destination, boolean is_move_file) {
            this.source_path = source_path;
            this.destination = destination;
            this.is_move_file = is_move_file;
            if (is_move_file)
                progress = new AlertDialog.Builder(MainActivity.this).setMessage("Moving...")
                        .setCancelable(false).create();
            else
                progress = new AlertDialog.Builder(MainActivity.this).setMessage("Renaming...")
                        .setCancelable(false).create();
            progress.show();
        }

        protected String doInBackground(Void... params) {
            try {
                myDBApi.move(source_path, destination);
            } catch(DropboxException e) {
                return e.getMessage();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String message) {
            progress.dismiss();
            if (message == null) {
                if (is_move_file)
                    Toast.makeText(getApplicationContext(), "Moved successfully", Toast.LENGTH_LONG).show();
                else
                    new ListFiles(current_directory).execute();
            }
            else
                showSimpleDialog(message);
        }
    }

    // Display a dialog that require user's new name for file / folder
    public void showInputDialog(final String source_path) {
        final String file_name = source_path.substring(source_path.lastIndexOf('/') + 1, source_path.length());
        final EditText editText = new EditText(this);
        editText.setText(file_name);
        editText.setSingleLine(true);

        AlertDialog myDialog = new AlertDialog.Builder(this).setMessage("Please enter a new name:").setView(editText)
                // When user enters new name and click rename, a new instance of Move class is created
                .setPositiveButton("Rename", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String input = editText.getText().toString().trim();
                        if (!TextUtils.isEmpty(input))
                            new Move(source_path, source_path.substring(0, source_path.lastIndexOf("/") + 1)
                                    + input, false).execute();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                }).create();
        myDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        myDialog.show();
    }

    // Open file in editText to edit
    public void editFile(final String dropbox_file_path) {
        final String external_storage_dir = Environment.getExternalStorageDirectory().getAbsolutePath();
        final EditText editText = new EditText(this);
        final String local_file_path = external_storage_dir + File.separator +
                dropbox_file_path.substring(dropbox_file_path.lastIndexOf('/') + 1, dropbox_file_path.length());

        String file_content = readFromFile(local_file_path);
        if (file_content == null)
            return;
        editText.setText(file_content);

        final AlertDialog myDialog = new AlertDialog.Builder(this).setView(editText)
                // When user enter new name and click rename, a new instance of Move class is created
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String input = editText.getText().toString().trim();
                        writeToFile(local_file_path, input);
                        new Delete(dropbox_file_path).execute();
                        new Upload(local_file_path, "Saving...").execute();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                }).create();
        myDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        myDialog.show();

        editText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    myDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                }
            }
        });

        myDialog.show();
        new File(local_file_path).delete();
    }

    public void writeToFile(String file_path, String text) {
        try (PrintWriter printWriter = new PrintWriter(new FileWriter(file_path, false))) {
            printWriter.print(text);
        }
        catch (IOException e) { showSimpleDialog(e.getMessage()) ;}
    }

    public String readFromFile(String file_path) {
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(file_path))) {
            String result = "";
            String line = bufferedReader.readLine();

            while (line != null) {
                result += line + "\n";
                line = bufferedReader.readLine();
            }
            return result;
        }
        catch (IOException e) { showSimpleDialog(e.getMessage()); }
        return null;
    }

    public void showSimpleDialog(String message) {
        new AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK", null).create().show();
    }

    public boolean checkEndsWith(String file_name, String[] file_extensions) {
        for (String file_extension : file_extensions) {
            if (file_name.endsWith(file_extension))
                return true;
        }
        return false;
    }
}
