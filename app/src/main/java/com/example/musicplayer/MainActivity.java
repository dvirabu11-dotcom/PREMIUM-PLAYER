package com.example.musicplayer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.util.Log;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

public class MainActivity extends AppCompatActivity {

    private ArrayList<FileItem> fileItems;
    private ArrayList<FileItem> originalFileItems;
    private android.widget.EditText searchBar;
    private File currentDir;
    private ListView listView;
    private MusicService musicService;
    private boolean isBound = false;
    private boolean isLocked = false;
    private java.util.Set<String> favorites = new java.util.HashSet<>();
    private String currentPlayingPath = null;
    private ArrayList<File> storageRoots = new ArrayList<>();

    // Progress UI
    private LinearLayout playerFooter;
    private android.widget.ViewFlipper viewFlipper;
    private TextView playerSongName;
    private TextView playerArtistName;
    private ListView upNextListView;
    private ArrayList<FileItem> upNextItems;
    private FileAdapter upNextAdapter;

    private TextView footerSongName;
    private TextView timeCurrent;
    private TextView timeTotal;
    private android.widget.SeekBar progressBar;
    private android.widget.ImageView albumArt;
    private android.os.Handler progressHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private android.animation.ObjectAnimator albumArtAnimator;
    private TextView lyricsView;
    private java.util.TreeMap<Integer, String> currentLyrics = new java.util.TreeMap<>();

    private android.widget.ImageButton btnPrevious, btnPlayPause, btnNext;

    private Runnable updateProgressAction = new Runnable() {
        @Override
        public void run() {
            if (isBound && musicService != null && musicService.isPlaying()) {
                int pos = musicService.getPosition();
                int dur = musicService.getDuration();
                
                Song currSong = musicService.getCurrentSong();
                if (currSong != null && !currSong.path.equals(currentPlayingPath)) {
                    currentPlayingPath = currSong.path;
                    footerSongName.setText(currSong.title);
                    if (playerSongName != null) playerSongName.setText(currSong.title);
                    if (playerArtistName != null) playerArtistName.setText(currSong.artist != null ? currSong.artist : "Unknown Artist");
                    
                    // Update Up Next list
                    if (upNextItems != null && upNextAdapter != null) {
                        upNextItems.clear();
                        ArrayList<Song> pl = musicService.getPlaylist();
                        int idx = musicService.getSongIndex();
                        if (pl != null && idx >= 0 && idx < pl.size() - 1) {
                            for (int i = idx + 1; i < pl.size(); i++) {
                                Song s = pl.get(i);
                                upNextItems.add(new FileItem(s.title, s.path, false));
                            }
                        }
                        upNextAdapter.notifyDataSetChanged();
                    }

                    loadLyrics(currSong.path);
                    
                    // Album Art Fade Transition
                    android.view.animation.Animation fadeOut = android.view.animation.AnimationUtils.loadAnimation(MainActivity.this, android.R.anim.fade_out);
                    fadeOut.setDuration(200);
                    final String path = currSong.path;
                    fadeOut.setAnimationListener(new android.view.animation.Animation.AnimationListener() {
                        public void onAnimationStart(android.view.animation.Animation a) {}
                        public void onAnimationRepeat(android.view.animation.Animation a) {}
                        public void onAnimationEnd(android.view.animation.Animation a) {
                            loadAlbumArt(path);
                            android.view.animation.Animation fadeIn = android.view.animation.AnimationUtils.loadAnimation(MainActivity.this, android.R.anim.fade_in);
                            fadeIn.setDuration(200);
                            albumArt.startAnimation(fadeIn);
                        }
                    });
                    albumArt.startAnimation(fadeOut);
                    Toast.makeText(MainActivity.this, "מנגן: " + currSong.title, Toast.LENGTH_SHORT).show();
                }

                if (dur > 0) {
                    if (playerFooter.getVisibility() != View.VISIBLE) {
                        playerFooter.setVisibility(View.VISIBLE);
                        playerFooter.startAnimation(android.view.animation.AnimationUtils.loadAnimation(MainActivity.this, R.anim.fade_in_200));
                    }
                    if (!albumArtAnimator.isRunning()) {
                        albumArtAnimator.start();
                    } else if (android.os.Build.VERSION.SDK_INT >= 19 && albumArtAnimator.isPaused()) {
                        albumArtAnimator.resume();
                    }

                    progressBar.setProgress((int) (((float) pos / dur) * 100));
                    timeCurrent.setText(formatTime(pos));
                    timeTotal.setText(formatTime(dur));
                    
                    if (btnPlayPause != null) {
                        btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
                    }

                    // Lyrics sync
                    if (!currentLyrics.isEmpty()) {
                        java.util.Map.Entry<Integer, String> entry = currentLyrics.floorEntry(pos);
                        if (entry != null) {
                            lyricsView.setText(entry.getValue());
                        }
                    }
                }
            } else {
                if (android.os.Build.VERSION.SDK_INT >= 19 && albumArtAnimator.isRunning()) {
                    albumArtAnimator.pause();
                }
                if (btnPlayPause != null) {
                    btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
                }
            }
            progressHandler.postDelayed(this, 1000);
        }
    };

    public static class Song {
        public String path;
        public String title;
        public String artist;
        public String album;

        public Song(String path, String title, String artist, String album) {
            this.path = path;
            this.title = title;
            this.artist = artist;
            this.album = album;
        }
    }

    public static class FileItem {
        public String title;
        public String path;
        public boolean isDirectory;

        public FileItem(String title, String path, boolean isDirectory) {
            this.title = title;
            this.path = path;
            this.isDirectory = isDirectory;
        }
    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicService = binder.getService();
            isBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Pure Black UI
        getWindow().getDecorView().setBackgroundColor(android.graphics.Color.BLACK);
        
        listView = (ListView) findViewById(R.id.songListView);
        fileItems = new ArrayList<>();
        originalFileItems = new ArrayList<>();
        
        searchBar = (android.widget.EditText) findViewById(R.id.searchBar);
        searchBar.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterList(s.toString());
            }
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        currentDir = new File("/storage/emulated/0/");
        if (!currentDir.exists()) {
            currentDir = new File("/");
        }

        // Footer UI Init
        playerFooter = (LinearLayout) findViewById(R.id.playerFooter);
        viewFlipper = (android.widget.ViewFlipper) findViewById(R.id.viewFlipper);
        playerSongName = (TextView) findViewById(R.id.playerSongName);
        playerArtistName = (TextView) findViewById(R.id.playerArtistName);
        upNextListView = (ListView) findViewById(R.id.upNextListView);
        
        upNextItems = new ArrayList<>();
        upNextAdapter = new FileAdapter(this, upNextItems);
        upNextListView.setAdapter(upNextAdapter);
        upNextListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (isBound && musicService != null) {
                    musicService.playSongAt(position + musicService.getSongIndex() + 1);
                }
            }
        });

        playerFooter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewFlipper.setDisplayedChild(1); // open player
                if (btnPlayPause != null) btnPlayPause.requestFocus();
            }
        });

        footerSongName = (TextView) findViewById(R.id.footerSongName);
        footerSongName.setSelected(true);
        if (playerSongName != null) playerSongName.setSelected(true);
        if (playerArtistName != null) playerArtistName.setSelected(true);

        timeCurrent = (TextView) findViewById(R.id.timeCurrent);
        timeTotal = (TextView) findViewById(R.id.timeTotal);
        progressBar = (android.widget.SeekBar) findViewById(R.id.songProgressBar);
        progressBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && isBound && musicService != null) {
                    int dur = musicService.getDuration();
                    if (dur > 0) {
                        musicService.seekTo((int) (((float) progress / 100) * dur));
                    }
                }
            }
            @Override
            public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });
        lyricsView = (TextView) findViewById(R.id.lyricsView);
        albumArt = (android.widget.ImageView) findViewById(R.id.albumArt);
        
        btnPrevious = (android.widget.ImageButton) findViewById(R.id.btnPrevious);
        btnPlayPause = (android.widget.ImageButton) findViewById(R.id.btnPlayPause);
        btnNext = (android.widget.ImageButton) findViewById(R.id.btnNext);

        btnPrevious.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isBound && musicService != null) musicService.playPrevious();
            }
        });

        btnPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isBound && musicService != null) musicService.pauseResume();
            }
        });

        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isBound && musicService != null) musicService.playNext();
            }
        });

        View.OnFocusChangeListener scaleFocusListener = new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start();
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
                }
            }
        };

        btnPrevious.setOnFocusChangeListener(scaleFocusListener);
        btnPlayPause.setOnFocusChangeListener(scaleFocusListener);
        btnNext.setOnFocusChangeListener(scaleFocusListener);

        albumArtAnimator = android.animation.ObjectAnimator.ofFloat(albumArt, "rotation", 0f, 360f);
        albumArtAnimator.setDuration(20000);
        albumArtAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
        albumArtAnimator.setRepeatCount(android.animation.ObjectAnimator.INFINITE);
        albumArtAnimator.setInterpolator(new android.view.animation.LinearInterpolator());

        detectStorageRoots();
        currentDir = storageRoots.isEmpty() ? new File("/") : storageRoots.get(0);

        refreshList();

        FileAdapter adapter = new FileAdapter(this, fileItems);
        listView.setAdapter(adapter);

        // Ensure ListView can be focused for D-pad
        listView.setFocusable(true);
        listView.requestFocus();

        listView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (view != null) {
                    TextView title = (TextView) view.findViewById(R.id.songTitle);
                    if (title != null) title.setSelected(true); // activate marquee
                    
                    for (int i = 0; i < parent.getChildCount(); i++) {
                        View child = parent.getChildAt(i);
                        if (child != view) {
                            TextView t = (TextView) child.findViewById(R.id.songTitle);
                            if (t != null) t.setSelected(false);
                        }
                    }
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                handleItemSelection(position);
            }
        });

        // Bind to MusicService
        Intent intent = new Intent(this, MusicService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
        startService(intent);

        // Load Favorites
        android.content.SharedPreferences prefs = getSharedPreferences("MusicPrefs", MODE_PRIVATE);
        favorites = prefs.getStringSet("favorites", new java.util.HashSet<String>());
    }

    private void refreshList() {
        fileItems.clear();

        // If we are at root or parent of storage roots, show storage options
        boolean atRoot = currentDir.getPath().equals("/") || currentDir.getPath().equals("/storage");
        
        if (atRoot) {
            for (File root : storageRoots) {
                fileItems.add(new FileItem("💾 כונן: " + root.getName(), root.getAbsolutePath(), true));
            }
        } else {
            // Add ".." to go back
            if (currentDir.getParentFile() != null) {
                fileItems.add(new FileItem(".. [חזור]", currentDir.getParent(), true));
            }
        }

        File[] files = currentDir.listFiles();
        
        if (files != null) {
            // Trigger scan for the current folder to help MediaStore
            scanFolder(currentDir);
            
            ArrayList<File> folderList = new ArrayList<>();
            ArrayList<File> musicList = new ArrayList<>();
            
            for (File file : files) {
                if (file.isDirectory() && !file.getName().startsWith(".")) {
                    folderList.add(file);
                } else if (file.isFile() && isAudioFile(file.getName())) {
                    musicList.add(file);
                }
            }
            
            Collections.sort(folderList, new java.util.Comparator<File>() {
                @Override
                public int compare(File o1, File o2) {
                    return o1.getName().compareToIgnoreCase(o2.getName());
                }
            });
            Collections.sort(musicList, new java.util.Comparator<File>() {
                @Override
                public int compare(File o1, File o2) {
                    return o1.getName().compareToIgnoreCase(o2.getName());
                }
            });

            for (File f : folderList) {
                fileItems.add(new FileItem("📁 [" + f.getName() + "]", f.getAbsolutePath(), true));
            }
            for (File f : musicList) {
                fileItems.add(new FileItem("🎵 " + f.getName(), f.getAbsolutePath(), false));
            }
        }
        
        originalFileItems.clear();
        originalFileItems.addAll(fileItems);
        
        if (searchBar != null) {
            String currentSearch = searchBar.getText().toString();
            if (!currentSearch.isEmpty()) {
                filterList(currentSearch);
            }
        }
        
        if (listView != null && listView.getAdapter() != null) {
            ((BaseAdapter) listView.getAdapter()).notifyDataSetChanged();
            listView.setSelection(0);
        }
    }

    private boolean isAudioFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".m4a");
    }

    private void handleItemSelection(int position) {
        if (position < 0 || position >= fileItems.size()) return;
        FileItem item = fileItems.get(position);
        if (item.isDirectory) {
            currentDir = new File(item.path);
            refreshList();
        } else {
            if (isBound && musicService != null && item.path.equals(currentPlayingPath)) {
                musicService.pauseResume();
            } else {
                playFile(position);
            }
        }
    }

    private void playFile(int position) {
        if (isBound && musicService != null) {
            ArrayList<Song> playlist = new ArrayList<>();
            int songIdxInPlaylist = 0;
            int currentFileIdx = 0;
            
            for (FileItem item : fileItems) {
                if (!item.isDirectory) {
                    playlist.add(new Song(item.path, item.title, "Unknown", "Folder"));
                    if (currentFileIdx == position) {
                        songIdxInPlaylist = playlist.size() - 1;
                    }
                }
                currentFileIdx++;
            }
            
            if (!playlist.isEmpty()) {
                musicService.setPlaylist(playlist);
                musicService.playSongAt(songIdxInPlaylist);
                currentPlayingPath = playlist.get(songIdxInPlaylist).path;
                footerSongName.setText(playlist.get(songIdxInPlaylist).title);
                if (playerFooter.getVisibility() != View.VISIBLE) {
                    playerFooter.setVisibility(View.VISIBLE);
                    playerFooter.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this, R.anim.fade_in_200));
                }
                loadLyrics(playlist.get(songIdxInPlaylist).path);
                loadAlbumArt(playlist.get(songIdxInPlaylist).path);
                
                // Trigger media scan for this file
                triggerMediaScan(new File(playlist.get(songIdxInPlaylist).path));
            }
        }
    }

    private void triggerMediaScan(File file) {
        if (file == null || !file.exists()) return;
        try {
            sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)));
        } catch (Exception e) {
            Log.e("MainActivity", "Error triggering media scan", e);
        }
    }

    private void scanFolder(File folder) {
        if (folder == null || !folder.isDirectory()) return;
        File[] files = folder.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && isAudioFile(f.getName())) {
                    triggerMediaScan(f);
                }
            }
        }
    }

    private void detectStorageRoots() {
        storageRoots.clear();
        // Internal storage
        String internal = System.getenv("EXTERNAL_STORAGE");
        if (internal != null) storageRoots.add(new File(internal));
        else storageRoots.add(new File("/storage/emulated/0/"));

        // Secondary storage (SD card)
        String secondary = System.getenv("SECONDARY_STORAGE");
        if (secondary != null) {
            String[] parts = secondary.split(":");
            for (String part : parts) {
                if (!part.isEmpty()) storageRoots.add(new File(part));
            }
        }

        // Generic probe
        File storage = new File("/storage");
        if (storage.exists() && storage.isDirectory()) {
            File[] list = storage.listFiles();
            if (list != null) {
                for (File f : list) {
                    if (f.isDirectory() && !f.getName().equalsIgnoreCase("self") && !storageRoots.contains(f)) {
                        storageRoots.add(f);
                    }
                }
            }
        }
        
        // Debug logs
        for (File root : storageRoots) {
            Log.d("StorageDebug", "Found root: " + root.getAbsolutePath());
        }
        // Print all /storage subdirs to log for debugging
        File storageDir = new File("/storage");
        if (storageDir.exists()) {
            File[] subs = storageDir.listFiles();
            if (subs != null) {
                for (File s : subs) {
                    Log.d("StorageDebug", "Direct /storage child: " + s.getAbsolutePath() + " isDir:" + s.isDirectory());
                }
            }
        }
    }

    private void loadLyrics(String songPath) {
        currentLyrics.clear();
        lyricsView.setText("");
        String lrcPath = songPath.substring(0, songPath.lastIndexOf('.')) + ".lrc";
        File lrcFile = new File(lrcPath);
        if (lrcFile.exists()) {
            try {
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(lrcFile));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("[") && line.contains("]")) {
                        int end = line.indexOf("]");
                        String timeStr = line.substring(1, end);
                        String lyric = line.substring(end + 1);
                        
                        // Parse time [mm:ss.xx]
                        try {
                            String[] parts = timeStr.split(":");
                            int min = Integer.parseInt(parts[0]);
                            float sec = Float.parseFloat(parts[1]);
                            int totalMs = (int) ((min * 60 + sec) * 1000);
                            currentLyrics.put(totalMs, lyric);
                        } catch (Exception e) {}
                    }
                }
                reader.close();
            } catch (Exception e) {
                Log.e("MainActivity", "Error reading lrc", e);
            }
        }
    }

    private void loadAlbumArt(String path) {
        try {
            android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
            retriever.setDataSource(path);
            byte[] art = retriever.getEmbeddedPicture();
            if (art != null) {
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(art, 0, art.length);
                albumArt.setImageBitmap(getCircularBitmap(bitmap));
            } else {
                albumArt.setImageResource(android.R.drawable.ic_menu_report_image);
            }
            retriever.release();
        } catch (Exception e) {
            albumArt.setImageResource(android.R.drawable.ic_menu_report_image);
        }
    }

    private android.graphics.Bitmap getCircularBitmap(android.graphics.Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int minEdge = Math.min(width, height);
        
        android.graphics.Bitmap output = android.graphics.Bitmap.createBitmap(minEdge, minEdge, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(output);

        final int color = 0xff424242;
        final android.graphics.Paint paint = new android.graphics.Paint();
        final android.graphics.Rect rect = new android.graphics.Rect(0, 0, minEdge, minEdge);

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(color);
        
        // Crop center
        int dx = (width - minEdge) / 2;
        int dy = (height - minEdge) / 2;
        android.graphics.Rect srcRect = new android.graphics.Rect(dx, dy, dx + minEdge, dy + minEdge);

        canvas.drawCircle(minEdge / 2f, minEdge / 2f, minEdge / 2f, paint);
        paint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, srcRect, rect, paint);

        return output;
    }

    private void shuffleFolder() {
        ArrayList<Song> shuffleList = new ArrayList<>();
        File[] files = currentDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && isAudioFile(file.getName())) {
                    shuffleList.add(new Song(file.getAbsolutePath(), file.getName(), "Shuffle", currentDir.getName()));
                }
            }
        }

        if (!shuffleList.isEmpty()) {
            Collections.shuffle(shuffleList);
            if (isBound && musicService != null) {
                musicService.setPlaylist(shuffleList);
                musicService.playSongAt(0);
                footerSongName.setText(shuffleList.get(0).title);
                if (playerFooter.getVisibility() != View.VISIBLE) {
                    playerFooter.setVisibility(View.VISIBLE);
                    playerFooter.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this, R.anim.fade_in_200));
                }
                loadAlbumArt(shuffleList.get(0).path);
                Toast.makeText(this, "מערבב תיקייה...", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String formatTime(int ms) {
        int sec = ms / 1000;
        int min = sec / 60;
        sec %= 60;
        return String.format("%02d:%02d", min, sec);
    }

    private void filterList(String query) {
        if (query == null || query.isEmpty()) {
            refreshList();
            return;
        }
        ArrayList<FileItem> filtered = new ArrayList<>();
        for (FileItem item : fileItems) {
            if (item.title.toLowerCase().contains(query.toLowerCase())) {
                filtered.add(item);
            }
        }
        fileItems.clear();
        fileItems.addAll(filtered);
        ((BaseAdapter) listView.getAdapter()).notifyDataSetChanged();
    }

    private void showSleepTimerDialog() {
        final String[] options = {"ללא", "15 דקות", "30 דקות", "60 דקות"};
        final int[] times = {0, 15, 30, 60};
        
        android.support.v7.app.AlertDialog.Builder builder = new android.support.v7.app.AlertDialog.Builder(this);
        builder.setTitle("טיימר שינה");
        builder.setItems(options, new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                if (isBound && musicService != null) {
                    musicService.startSleepTimer(times[which]);
                    Toast.makeText(MainActivity.this, "טיימר הוגדר ל-" + options[which], Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.show();
    }

    private int presetIdx = 0;
    private void showEqualizerDialog() {
        if (!isBound || musicService == null) return;
        final String[] presets = musicService.getEqualizerPresets();
        if (presets.length == 0) return;

        android.support.v7.app.AlertDialog.Builder builder = new android.support.v7.app.AlertDialog.Builder(this);
        builder.setTitle("אקולייזר");
        builder.setItems(presets, new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                musicService.setEqualizerPreset((short) which);
                Toast.makeText(MainActivity.this, "מצב: " + presets[which], Toast.LENGTH_SHORT).show();
            }
        });
        builder.show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isLocked && keyCode != KeyEvent.KEYCODE_BACK) {
            Toast.makeText(this, "סמל המנעול מופעל. לחיצה ארוכה על 'חזור' לשחרור.", Toast.LENGTH_SHORT).show();
            return true;
        }

        if (event.getRepeatCount() == 0) {
            if (keyCode == KeyEvent.KEYCODE_STAR || keyCode == KeyEvent.KEYCODE_0 || 
                keyCode == KeyEvent.KEYCODE_5 || keyCode == KeyEvent.KEYCODE_DPAD_CENTER || 
                keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_BACK ||
                keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                keyCode == KeyEvent.KEYCODE_7 || keyCode == KeyEvent.KEYCODE_9) {
                event.startTracking();
                // do not return true here! let it fall through or we handle short press in onKeyUp
            }
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_2: filterByT9("אבג"); return true;
            case KeyEvent.KEYCODE_4: filterByT9("דהו"); return true;
            case KeyEvent.KEYCODE_6: filterByT9("יכל"); return true;
            case KeyEvent.KEYCODE_8: filterByT9("עפצ"); return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if ((event.getFlags() & KeyEvent.FLAG_CANCELED_LONG_PRESS) == 0 && !isLocked) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    handleItemSelection(listView.getSelectedItemPosition());
                    return true;
                case KeyEvent.KEYCODE_BACK:
                    if (viewFlipper.getDisplayedChild() == 1) {
                        viewFlipper.setDisplayedChild(0);
                        return true;
                    }
                    boolean isAtRoot = currentDir.getPath().equals("/") || currentDir.getPath().equals("/storage");
                    for (File root : storageRoots) {
                        if (currentDir.getPath().equals(root.getPath())) {
                            isAtRoot = true;
                            break;
                        }
                    }

                    if (!isAtRoot && currentDir.getParentFile() != null) {
                        currentDir = currentDir.getParentFile();
                        refreshList();
                        return true;
                    } else if (!currentDir.getPath().equals("/storage") && !currentDir.getPath().equals("/") && !storageRoots.isEmpty()) {
                        currentDir = new File("/storage");
                        refreshList();
                        return true;
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (getCurrentFocus() != progressBar && getCurrentFocus() != btnNext && getCurrentFocus() != btnPrevious && getCurrentFocus() != btnPlayPause && getCurrentFocus() != searchBar) {
                        if (isBound && musicService != null) {
                            musicService.playNext();
                            return true;
                        }
                    }
                    break;

                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (getCurrentFocus() != progressBar && getCurrentFocus() != btnNext && getCurrentFocus() != btnPrevious && getCurrentFocus() != btnPlayPause && getCurrentFocus() != searchBar) {
                        if (isBound && musicService != null) {
                            musicService.playPrevious();
                            return true;
                        }
                    }
                    break;
                case KeyEvent.KEYCODE_3: 
                    if (isBound && musicService != null) {
                        musicService.changeSpeed(0.25f);
                        Toast.makeText(this, "מהירות: x" + musicService.getSpeed(), Toast.LENGTH_SHORT).show();
                    }
                    return true;
                case KeyEvent.KEYCODE_1:
                    if (isBound && musicService != null) {
                        musicService.changeSpeed(-0.25f);
                        Toast.makeText(this, "מהירות: x" + musicService.getSpeed(), Toast.LENGTH_SHORT).show();
                    }
                    return true;
                case KeyEvent.KEYCODE_5: 
                    shuffleFolder();
                    return true;
                case KeyEvent.KEYCODE_7: filterByT9("מנס"); return true;
                case KeyEvent.KEYCODE_9: filterByT9("קרשת"); return true;
                
                case KeyEvent.KEYCODE_0:
                    refreshList();
                    return true;

                case KeyEvent.KEYCODE_POUND:
                    int pos = listView.getSelectedItemPosition();
                    if (pos >= 0 && pos < fileItems.size() && !fileItems.get(pos).isDirectory) {
                        toggleFavorite(pos);
                    } else if (currentPlayingPath != null) {
                        toggleFavoriteByPath(currentPlayingPath);
                    }
                    return true;
                case KeyEvent.KEYCODE_MENU:
                    showEqualizerDialog();
                    return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            isLocked = !isLocked;
            Toast.makeText(this, isLocked ? "מקשים נעולים 🔒" : "מקשים פתוחים 🔓", Toast.LENGTH_SHORT).show();
            return true;
        }
        if (isLocked) return true;

        if (keyCode == KeyEvent.KEYCODE_STAR) {
            showSleepTimerDialog();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_0) {
            resumeLastPosition();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_5) {
            shuffleFolder();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_7) {
            showRenameDialog(listView.getSelectedItemPosition());
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_9) {
            showVolumeDialog();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            int pos = listView.getSelectedItemPosition();
            if (pos >= 0 && pos < fileItems.size()) {
                FileItem item = fileItems.get(pos);
                if (!item.isDirectory && isBound && musicService != null) {
                    musicService.addToQueue(new Song(item.path, item.title, "Unknown", "Queue"));
                    Toast.makeText(this, "נוסף לתור: " + item.title, Toast.LENGTH_SHORT).show();
                }
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (getCurrentFocus() != progressBar && getCurrentFocus() != btnNext && getCurrentFocus() != btnPrevious && getCurrentFocus() != btnPlayPause && getCurrentFocus() != searchBar) {
                if (isBound && musicService != null) {
                    musicService.seekRelative(-10000);
                    Toast.makeText(this, "-10 שניות", Toast.LENGTH_SHORT).show();
                    return true;
                }
            }
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (getCurrentFocus() != progressBar && getCurrentFocus() != btnNext && getCurrentFocus() != btnPrevious && getCurrentFocus() != btnPlayPause && getCurrentFocus() != searchBar) {
                if (isBound && musicService != null) {
                    musicService.seekRelative(10000);
                    Toast.makeText(this, "+10 שניות", Toast.LENGTH_SHORT).show();
                    return true;
                }
            }
        }
        return super.onKeyLongPress(keyCode, event);
    }

    private void toggleFavorite(int position) {
        if (position < 0 || position >= fileItems.size()) return;
        FileItem item = fileItems.get(position);
        if (item.isDirectory) return;
        toggleFavoriteByPath(item.path);
    }

    private void toggleFavoriteByPath(String path) {
        if (path == null) return;
        if (favorites.contains(path)) {
            favorites.remove(path);
            Toast.makeText(this, "הוסר מהמועדפים", Toast.LENGTH_SHORT).show();
        } else {
            favorites.add(path);
            Toast.makeText(this, "נוסף למועדפים", Toast.LENGTH_SHORT).show();
        }
        
        getSharedPreferences("MusicPrefs", MODE_PRIVATE).edit().putStringSet("favorites", favorites).apply();
        if (listView != null && listView.getAdapter() != null) {
            ((BaseAdapter) listView.getAdapter()).notifyDataSetChanged();
        }
    }

    private void filterList(String query) {
        if (query == null || query.trim().isEmpty()) {
            fileItems.clear();
            fileItems.addAll(originalFileItems);
        } else {
            String lowerQuery = query.toLowerCase();
            fileItems.clear();
            for (FileItem item : originalFileItems) {
                // ".." usually goes back, keep it or remove it from search? Keep it maybe.
                if (item.title.equals(".. [חזור]")) {
                    fileItems.add(item);
                } else if (item.title.toLowerCase().contains(lowerQuery)) {
                    fileItems.add(item);
                }
            }
        }
        if (listView != null && listView.getAdapter() != null) {
            ((BaseAdapter) listView.getAdapter()).notifyDataSetChanged();
        }
    }

    private void filterByT9(String letters) {
        ArrayList<FileItem> filtered = new ArrayList<>();
        String normalizedLetters = letters.toLowerCase();
        for (FileItem item : originalFileItems) {
            if (item.isDirectory) continue; // Skip folders for search maybe?
            String title = item.title.toLowerCase();
            // Check if it starts with any of the letters
            for (char c : normalizedLetters.toCharArray()) {
                if (title.contains("🎵 " + c) || title.startsWith("" + c)) {
                    filtered.add(item);
                    break;
                }
            }
        }
        if (!filtered.isEmpty()) {
            fileItems.clear();
            fileItems.addAll(filtered);
            if (listView != null && listView.getAdapter() != null) {
                ((BaseAdapter) listView.getAdapter()).notifyDataSetChanged();
            }
            Toast.makeText(this, "מסונן לפי: " + letters, Toast.LENGTH_SHORT).show();
        }
    }

    private void showRenameDialog(final int position) {
        if (position < 0 || position >= fileItems.size()) return;
        final FileItem item = fileItems.get(position);
        if (item.isDirectory) return;

        android.support.v7.app.AlertDialog.Builder builder = new android.support.v7.app.AlertDialog.Builder(this);
        builder.setTitle("שינוי שם קובץ");
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setText(item.title.replace("🎵 ", ""));
        builder.setView(input);

        builder.setPositiveButton("שנה", new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                String newName = input.getText().toString();
                if (!newName.isEmpty()) {
                    File oldFile = new File(item.path);
                    File newFile = new File(oldFile.getParent(), newName);
                    if (oldFile.renameTo(newFile)) {
                        refreshList();
                        Toast.makeText(MainActivity.this, "שם שונה בהצלחה", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "נכשל בשינוי שם", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
        builder.setNegativeButton("ביטול", null);
        builder.show();
    }

    private void showVolumeDialog() {
        final android.media.AudioManager audioManager = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int maxVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
        int curVol = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);

        android.support.v7.app.AlertDialog.Builder builder = new android.support.v7.app.AlertDialog.Builder(this);
        builder.setTitle("עוצמת קול");
        final android.widget.SeekBar seekBar = new android.widget.SeekBar(this);
        seekBar.setMax(maxVol);
        seekBar.setProgress(curVol);
        builder.setView(seekBar);

        seekBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, progress, 0);
            }
            @Override
            public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });
        builder.show();
    }
    private void resumeLastPosition() {
        if (isBound && musicService != null) {
            musicService.loadLastPosition();
            musicService.pauseResume();
            footerSongName.setText("ממשיך מהנקודה האחרונה...");
            if (playerFooter.getVisibility() != View.VISIBLE) {
                playerFooter.setVisibility(View.VISIBLE);
                playerFooter.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this, R.anim.fade_in_200));
            }
            Toast.makeText(this, "ממשיך...", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        progressHandler.post(updateProgressAction);
    }

    @Override
    protected void onPause() {
        super.onPause();
        progressHandler.removeCallbacks(updateProgressAction);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
    }

    private class FileAdapter extends BaseAdapter {
        private Context context;
        private ArrayList<FileItem> items;

        public FileAdapter(Context context, ArrayList<FileItem> items) {
            this.context = context;
            this.items = items;
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        private class ViewHolder {
            TextView titleView;
            TextView metaView;
            View container;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.list_item_song, parent, false);
                holder = new ViewHolder();
                holder.titleView = (TextView) convertView.findViewById(R.id.songTitle);
                holder.metaView = (TextView) convertView.findViewById(R.id.songArtistAlbum);
                holder.container = convertView;
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            FileItem item = items.get(position);
            holder.titleView.setText(item.title);
            
            if (item.isDirectory) {
                holder.metaView.setText("תיקייה");
                holder.titleView.setTextColor(android.graphics.Color.parseColor("#00B0FF"));
            } else {
                holder.metaView.setText(favorites.contains(item.path) ? "★ מועדף | קובץ שמע" : "קובץ שמע");
                holder.titleView.setTextColor(android.graphics.Color.WHITE);
            }

            return convertView;
        }
    }
}
