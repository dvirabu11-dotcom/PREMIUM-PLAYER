package com.example.musicplayer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

public class MediaButtonReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
            KeyEvent event = (KeyEvent) intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            if (event == null || event.getAction() != KeyEvent.ACTION_DOWN) return;

            Intent serviceIntent = new Intent(context, MusicService.class);
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    serviceIntent.setAction("ACTION_PLAY_PAUSE");
                    break;
                case KeyEvent.KEYCODE_MEDIA_NEXT:
                    serviceIntent.setAction("ACTION_NEXT");
                    break;
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                    serviceIntent.setAction("ACTION_PREVIOUS");
                    break;
            }
            context.startService(serviceIntent);
        }
    }
}
