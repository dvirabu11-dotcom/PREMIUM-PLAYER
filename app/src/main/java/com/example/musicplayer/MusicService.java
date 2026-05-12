package com.example.musicplayer;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.media.audiofx.Equalizer;
import android.os.Handler;
import android.content.SharedPreferences;

import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PowerManager;
import java.util.ArrayList;

public class MusicService extends Service {

    private SimpleExoPlayer player;
    private Equalizer equalizer;
    private ArrayList<MainActivity.Song> songList;
    private ArrayList<MainActivity.Song> queue = new ArrayList<>();
    private int songIndex = 0;
    private final IBinder musicBind = new MusicBinder();
    private static final int NOTIFICATION_ID = 1;
    private Handler sleepHandler = new Handler();
    private Runnable sleepRunnable;
    private PowerManager.WakeLock wakeLock;

    private BroadcastReceiver noisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                if (player != null && player.getPlayWhenReady()) {
                    player.setPlayWhenReady(false);
                    saveLastPosition();
                }
            }
        }
    };

    private static final String PREFS_NAME = "MusicPrefs";
    private static final String KEY_PATH = "lastPath";
    private static final String KEY_POS = "lastPos";

    private float currentSpeed = 1.0f;

    public class MusicBinder extends Binder {
        MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        player = ExoPlayerFactory.newSimpleInstance(this);
        player.addListener(new Player.EventListener() {
            @Override
            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                if (playbackState == Player.STATE_ENDED) {
                    playNext();
                }
            }
        });
        
        setupEqualizer();

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MusicPlayer:WakeLock");

        IntentFilter filter = new IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(noisyReceiver, filter);
    }

    public void addToQueue(MainActivity.Song song) {
        queue.add(song);
    }

    private void setupEqualizer() {
        try {
            equalizer = new Equalizer(0, player.getAudioSessionId());
            equalizer.setEnabled(true);
        } catch (Exception e) {
            Log.e("MusicService", "Equalizer Error", e);
        }
    }

    public void setEqualizerPreset(short preset) {
        if (equalizer != null && preset < equalizer.getNumberOfPresets()) {
            equalizer.usePreset(preset);
        }
    }

    public String[] getEqualizerPresets() {
        if (equalizer == null) return new String[0];
        int num = equalizer.getNumberOfPresets();
        String[] names = new String[num];
        for (short i = 0; i < num; i++) {
            names[i] = equalizer.getPresetName(i);
        }
        return names;
    }

    public void changeSpeed(float delta) {
        currentSpeed += delta;
        if (currentSpeed < 0.5f) currentSpeed = 0.5f;
        if (currentSpeed > 2.0f) currentSpeed = 2.0f;
        PlaybackParameters params = new PlaybackParameters(currentSpeed);
        player.setPlaybackParameters(params);
    }

    public void seekRelative(long ms) {
        if (player != null) {
            long newPos = player.getCurrentPosition() + ms;
            if (newPos < 0) newPos = 0;
            if (newPos > player.getDuration()) newPos = player.getDuration();
            player.seekTo(newPos);
        }
    }

    public float getSpeed() {
        return currentSpeed;
    }

    public void startSleepTimer(int minutes) {
        stopSleepTimer();
        if (minutes <= 0) return;
        
        sleepRunnable = new Runnable() {
            @Override
            public void run() {
                if (player != null) {
                    player.setPlayWhenReady(false);
                }
                stopSelf();
            }
        };
        sleepHandler.postDelayed(sleepRunnable, (long) minutes * 60 * 1000);
    }

    public void stopSleepTimer() {
        if (sleepRunnable != null) {
            sleepHandler.removeCallbacks(sleepRunnable);
            sleepRunnable = null;
        }
    }

    private void saveLastPosition() {
        if (songList != null && songIndex >= 0 && songIndex < songList.size()) {
            SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
            editor.putString(KEY_PATH, songList.get(songIndex).path);
            editor.putLong(KEY_POS, player.getCurrentPosition());
            editor.apply();
        }
    }

    public void loadLastPosition() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String path = prefs.getString(KEY_PATH, null);
        long pos = prefs.getLong(KEY_POS, 0);
        if (path != null) {
            playSongFromPath(path, pos);
        }
    }

    private void playSongFromPath(String path, long pos) {
        if (!wakeLock.isHeld()) wakeLock.acquire();
        DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(this, Util.getUserAgent(this, "MusicPlayer"));
        ProgressiveMediaSource mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(Uri.parse(path));
        
        player.prepare(mediaSource);
        if (pos > 0) player.seekTo(pos);
        player.setPlayWhenReady(true);
    }

    public void setPlaylist(ArrayList<MainActivity.Song> songs) {
        this.songList = songs;
    }

    public void setSongIndex(int index) {
        this.songIndex = index;
    }

    public void playSong() {
        if (songList == null || songIndex < 0 || songIndex >= songList.size()) return;
        
        MainActivity.Song song = songList.get(songIndex);
        playSongFromPath(song.path, 0);
        showNotification(song.title);
    }

    public void playSongAt(int index) {
        setSongIndex(index);
        playSong();
    }

    public void playNext() {
        if (!queue.isEmpty()) {
            MainActivity.Song next = queue.remove(0);
            playSongFromPath(next.path, 0);
            showNotification(next.title);
            return;
        }
        if (songList == null || songList.isEmpty()) return;
        songIndex = (songIndex + 1) % songList.size();
        playSong();
    }

    public void playPrevious() {
        if (songList == null || songList.isEmpty()) return;
        songIndex = (songIndex - 1 + songList.size()) % songList.size();
        playSong();
    }

    public void pauseResume() {
        if (player.getPlayWhenReady()) {
            player.setPlayWhenReady(false);
            saveLastPosition();
            if (wakeLock.isHeld()) wakeLock.release();
        } else {
            player.setPlayWhenReady(true);
            if (!wakeLock.isHeld()) wakeLock.acquire();
        }
    }

    public boolean isPlaying() {
        return player != null && player.getPlayWhenReady();
    }

    public int getDuration() {
        return player != null ? (int) player.getDuration() : 0;
    }

    public int getPosition() {
        return player != null ? (int) player.getCurrentPosition() : 0;
    }

    private void showNotification(String title) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

        Notification notification = new Notification.Builder(this)
                .setContentTitle("Playing Music")
                .setContentText(title)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            if ("ACTION_PLAY_PAUSE".equals(action)) {
                pauseResume();
            } else if ("ACTION_NEXT".equals(action)) {
                playNext();
            } else if ("ACTION_PREVIOUS".equals(action)) {
                playPrevious();
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return musicBind;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        unregisterReceiver(noisyReceiver);
        stopForeground(true);
    }
}
