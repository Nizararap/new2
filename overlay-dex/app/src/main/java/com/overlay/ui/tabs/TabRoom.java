package com.overlay.ui.tabs;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.LinearLayout;

import com.overlay.KeyAuthManager;
import com.overlay.RadarView;
import com.overlay.ui.UIFactory;

public class TabRoom {
    public static LinearLayout build(Context ctx, SharedPreferences prefs, KeyAuthManager authManager, RadarView radar, int realScreenW, int realScreenH, LinearLayout roomTableContainer) {
        LinearLayout t = new LinearLayout(ctx); 
        t.setOrientation(LinearLayout.VERTICAL);

        t.addView(UIFactory.card(ctx, l -> {
            l.addView(UIFactory.secTitle(ctx, "ROOM INFO & DRAFT PICK"));
            l.addView(UIFactory.toggleRow(ctx, prefs, authManager, radar, realScreenW, realScreenH, "Enable Room Info", "Show player data", "room_info_enable", false));
            l.addView(UIFactory.vgap(ctx, 10));

            if (roomTableContainer.getParent() != null) {
                ((LinearLayout) roomTableContainer.getParent()).removeView(roomTableContainer);
            }
            l.addView(roomTableContainer);
        }));

        return t;
    }
}
